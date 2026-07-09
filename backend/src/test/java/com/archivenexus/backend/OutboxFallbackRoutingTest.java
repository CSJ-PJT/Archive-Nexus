package com.archivenexus.backend;

import com.archivenexus.backend.outbox.OutboxEventRepository;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_outbox_fallback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.integrations.logitics.enabled=false",
        "archive.integrations.ledger.enabled=false",
        "archive.integrations.routing.allow-ledger-direct-fallback-for-logistics=true"
})
@AutoConfigureMockMvc
class OutboxFallbackRoutingTest {
    @Autowired MockMvc mvc;
    @Autowired OutboxEventRepository repository;
    @MockBean DomainAggregateProjectionService projections;

    @Test
    void ledgerDirectFallbackCanSelectLogisticsEventsWhenExplicitlyEnabled() throws Exception {
        repository.deleteAll();
        mvc.perform(post("/api/outbox/events/generate?count=5&type=logistics"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/outbox/events/publish?target=ledger&dryRun=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCandidates").value(5))
                .andExpect(jsonPath("$.targets.LOGITICS.skipped").value(5));
    }
}
