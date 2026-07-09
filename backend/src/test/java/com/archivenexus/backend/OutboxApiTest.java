package com.archivenexus.backend;

import com.archivenexus.backend.outbox.OutboxEventRepository;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive-nexus.ledger.enabled=false"
})
@AutoConfigureMockMvc
class OutboxApiTest {
    @Autowired MockMvc mvc;
    @Autowired OutboxEventRepository repository;
    @MockBean DomainAggregateProjectionService projections;

    @Test
    void generatesSyntheticEventsWithUniqueIdempotencyKeys() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(100))
                .andExpect(jsonPath("$.generated").value(100));

        assertThat(repository.count()).isGreaterThanOrEqualTo(100);
        assertThat(repository.findAll().stream().map(event -> event.idempotencyKey()).distinct().count())
                .isEqualTo(repository.count());
        mvc.perform(get("/api/outbox/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(repository.count()));
    }

    @Test
    void ledgerPublishFailureIsIsolatedAndRecorded() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=5"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/outbox/events/publish").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempted").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.failed").value(org.hamcrest.Matchers.greaterThan(0)));

        assertThat(repository.findAll().stream().filter(event -> event.retryCount() > 0))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.lastError()).contains("disabled"));
    }
}
