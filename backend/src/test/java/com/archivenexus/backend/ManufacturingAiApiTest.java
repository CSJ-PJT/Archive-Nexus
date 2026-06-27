package com.archivenexus.backend;

import com.archivenexus.backend.ai.persistence.AiQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_ai;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false"
})
@AutoConfigureMockMvc
class ManufacturingAiApiTest {
    @Autowired MockMvc mvc;
    @Autowired AiQueryRepository repository;

    @Test
    void executesCompositeQueryPersistsHistoryAndCreatesArchiveOsEventsAndRpa() throws Exception {
        String response = mvc.perform(post("/api/ai/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "3공장의 생산량 감소 원인과 설비 진동 이상 여부를 분석해줘",
                                  "factoryId": "FACTORY-C",
                                  "requestedBy": "operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId", notNullValue()))
                .andExpect(jsonPath("$.selectedFactoryId").value("FAC-C"))
                .andExpect(jsonPath("$.routedIntents", hasItem("PRODUCTION")))
                .andExpect(jsonPath("$.routedIntents", hasItem("MAINTENANCE")))
                .andExpect(jsonPath("$.invokedAgents", hasItem("ProductionAgent")))
                .andExpect(jsonPath("$.invokedAgents", hasItem("MaintenanceAgent")))
                .andExpect(jsonPath("$.answer", containsString("통합 판단")))
                .andReturn().getResponse().getContentAsString();

        String queryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("queryId").asText();
        mvc.perform(get("/api/ai/queries/{id}", queryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value(queryId));
        mvc.perform(get("/api/ai/queries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].queryId", hasItem(queryId)));
        mvc.perform(get("/api/archiveos/interactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("AGENT_QUERY_RECEIVED")))
                .andExpect(jsonPath("$[*].type", hasItem("AGENT_RESPONSE_COMPOSED")));

        org.assertj.core.api.Assertions.assertThat(repository.existsById(queryId)).isTrue();
    }

    @Test
    void unknownQuestionDoesNotCreateRpaTask() throws Exception {
        mvc.perform(post("/api/ai/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"안녕하세요", "requestedBy":"viewer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routedIntents[0]").value("UNKNOWN"))
                .andExpect(jsonPath("$.rpaTaskId").doesNotExist())
                .andExpect(jsonPath("$.executionStatus").value("INSUFFICIENT_DATA"));
    }

    @Test
    void createsMultiAgentRpaTaskWhenMaintenanceThresholdIsExceeded() throws Exception {
        mvc.perform(post("/api/ai/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"설비 진동과 온도 위험을 분석해줘", "factoryId":"FAC-B", "requestedBy":"operator"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpaTaskId", notNullValue()))
                .andExpect(jsonPath("$.approvalRequired").value(true));

        mvc.perform(get("/api/rpa/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].source", hasItem("MULTI_AGENT")))
                .andExpect(jsonPath("$[*].sourceQueryId", hasItem(notNullValue())));
    }
}
