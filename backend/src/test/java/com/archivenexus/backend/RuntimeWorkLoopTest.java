package com.archivenexus.backend;

import com.archivenexus.backend.market.MarketInboundEventRepository;
import com.archivenexus.backend.outbox.OutboxEventRepository;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import com.archivenexus.backend.runtime.RuntimeEventModels.RuntimeStatusResponse;
import com.archivenexus.backend.runtime.RuntimeWorkLoopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_runtime_work_loop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.integrations.logitics.enabled=false",
        "archive.integrations.ledger.enabled=false",
        "archive.integrations.market.enabled=false",
        "archive.runtime.autorun.enabled=true",
        "archive.runtime.tick-interval=1h",
        "archive.runtime.max-events-per-tick=2",
        "archive.runtime.max-backlog-per-tick=10"
})
@AutoConfigureMockMvc
class RuntimeWorkLoopTest {
    @Autowired
    RuntimeWorkLoopService runtimeWorkLoop;

    @Autowired
    MarketInboundEventRepository marketEvents;

    @Autowired
    OutboxEventRepository outboxEvents;

    @Autowired
    MockMvc mvc;

    @MockBean
    DomainAggregateProjectionService projections;

    @Test
    void autoRunTickCreatesBoundedSyntheticRuntimeWorkAndIsIdempotentPerTick() {
        long initialMarketCount = marketEvents.count();
        long initialOutboxCount = outboxEvents.count();
        RuntimeStatusResponse first = runtimeWorkLoop.runOnce();

        assertThat(first.autoRunEnabled()).isTrue();
        assertThat(first.pipelineStatus()).isEqualTo("LIVE");
        assertThat(first.lastWorkAt()).isNotNull();
        assertThat(first.lastEventAt()).isNotNull();
        assertThat(first.eventsProducedLastTick()).isBetween(1, 2);
        assertThat(first.eventsConsumedLastTick()).isBetween(1, 2);
        assertThat(marketEvents.count() - initialMarketCount).isEqualTo(first.eventsProducedLastTick());
        assertThat(outboxEvents.count() - initialOutboxCount).isLessThanOrEqualTo(2);

        long marketCount = marketEvents.count();
        long outboxCount = outboxEvents.count();
        RuntimeStatusResponse duplicate = runtimeWorkLoop.runOnce();

        assertThat(duplicate.eventsProducedLastTick()).isZero();
        assertThat(marketEvents.count()).isEqualTo(marketCount);
        assertThat(outboxEvents.count()).isEqualTo(outboxCount);
    }

    @Test
    void runtimeStatusAndOperationsSummaryAreReadOnly() throws Exception {
        runtimeWorkLoop.runOnce();
        long marketCount = marketEvents.count();
        long outboxCount = outboxEvents.count();

        mvc.perform(get("/api/runtime/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("Archive-Nexus"))
                .andExpect(jsonPath("$.runtimeActive").value(true))
                .andExpect(jsonPath("$.autoRunEnabled").value(true))
                .andExpect(jsonPath("$.lastWorkAt").exists())
                .andExpect(jsonPath("$.lastEventAt").exists())
                .andExpect(jsonPath("$.pipelineStatus").value("LIVE"));

        mvc.perform(get("/api/operations/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtime.autoRunEnabled").value(true))
                .andExpect(jsonPath("$.runtime.lastWorkAt").exists())
                .andExpect(jsonPath("$.runtime.pipelineStatus").value("LIVE"));

        assertThat(marketEvents.count()).isEqualTo(marketCount);
        assertThat(outboxEvents.count()).isEqualTo(outboxCount);
    }
}
