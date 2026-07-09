package com.archivenexus.backend;

import com.archivenexus.backend.outbox.OutboxEventRepository;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_outbox_failure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.integrations.logitics.enabled=true",
        "archive.integrations.logitics.base-url=http://127.0.0.1:9",
        "archive.integrations.logitics.timeout-ms=250",
        "archive.integrations.ledger.enabled=true",
        "archive.integrations.ledger.base-url=http://127.0.0.1:9",
        "archive.integrations.ledger.timeout-ms=250",
        "archive.integrations.routing.max-retry-count=5"
})
@AutoConfigureMockMvc
class OutboxPublishFailureTest {
    @Autowired MockMvc mvc;
    @Autowired OutboxEventRepository repository;
    @MockBean DomainAggregateProjectionService projections;

    @Test
    void logiticsConnectionRefusedIsRecordedWithoutBreakingApi() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=3&type=logistics"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/outbox/events/publish?target=logitics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(3));

        assertThat(repository.findAll()).allSatisfy(event -> {
            assertThat(event.status()).isEqualTo(OutboxStatus.PENDING_RETRY);
            assertThat(event.retryCount()).isEqualTo(1);
            assertThat(event.lastError()).isNotBlank();
        });
    }

    @Test
    void ledgerConnectionRefusedIsRecordedWithoutBreakingApi() throws Exception {
        repository.deleteAll();
        mvc.perform(post("/api/outbox/events/generate?count=3&type=ledger"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/outbox/events/publish?target=ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(3));

        assertThat(repository.findAll()).allSatisfy(event -> {
            assertThat(event.status()).isEqualTo(OutboxStatus.PENDING_RETRY);
            assertThat(event.retryCount()).isEqualTo(1);
            assertThat(event.lastError()).isNotBlank();
        });
    }
}
