package com.archivenexus.backend;

import com.archivenexus.backend.archiveos.ArchiveOsWorkflowClient;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import com.archivenexus.backend.task.NexusTaskModels.TaskStatus;
import com.archivenexus.backend.task.NexusTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_tasks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false"
})
@AutoConfigureMockMvc
class NexusTaskApiTest {
    @Autowired MockMvc mvc;
    @Autowired NexusTaskRepository repository;
    @MockBean ArchiveOsWorkflowClient workflows;
    @MockBean DomainAggregateProjectionService projections;

    @BeforeEach
    void stubWorkflow() {
        when(workflows.create(any())).thenAnswer(invocation ->
                new ArchiveOsWorkflowClient.WorkflowRef("WF-" + invocation.getArgument(0, com.archivenexus.backend.task.NexusTaskEntity.class).id(), "in_progress", null));
    }

    @Test
    void executesAndLogs() throws Exception {
        String id = create("SIMULATOR_TICK", null);
        mvc.perform(post("/api/tasks/{id}/run", id)).andExpect(status().isOk());
        await(id, TaskStatus.SUCCESS);
        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("SUCCESS"))
                .andExpect(jsonPath("$.task.resultSummary", notNullValue()))
                .andExpect(jsonPath("$.logs.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void cancelsAndRetries() throws Exception {
        String id = create("MANUFACTURING_QUERY", "");
        mvc.perform(post("/api/tasks/{id}/cancel", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/tasks/{id}/retry", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETRY_REQUESTED"));
    }

    @Test
    void recordsFailure() throws Exception {
        String id = create("MANUFACTURING_QUERY", null);
        mvc.perform(post("/api/tasks/{id}/run", id)).andExpect(status().isOk());
        await(id, TaskStatus.FAILED);
        mvc.perform(get("/api/tasks/{id}/logs", id)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.level == 'ERROR')]", hasSize(1)));
        mvc.perform(post("/api/tasks/{id}/retry", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETRY_REQUESTED"));
    }

    private String create(String type, String question) throws Exception {
        String q = question == null ? "" : ",\"question\":\"" + question + "\"";
        String body = mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"운영 작업\",\"type\":\"" + type + "\"" + q + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("id").asText();
    }

    private void await(String id, TaskStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
        while (Instant.now().isBefore(deadline)) {
            if (repository.findById(id).map(task -> task.status() == expected).orElse(false)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Task did not reach " + expected);
    }
}
