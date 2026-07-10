package com.archivenexus.backend;

import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_runtime_events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
        "archive.workforce.enabled=true"
})
@AutoConfigureMockMvc
class RuntimeEventApiTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    DomainAggregateProjectionService projections;

    @Test
    void exposesRecentCorrelationAndEntityRuntimeEvents() throws Exception {
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "runtime-market-001",
                                  "idempotencyKey": "runtime-market-001",
                                  "source": "Archive-Market",
                                  "eventType": "SHIPMENT_REQUESTED",
                                  "schemaVersion": 1,
                                  "occurredAt": "2026-07-10T00:00:00Z",
                                  "simulationRunId": "SIM-RUNTIME",
                                  "settlementCycleId": "CYCLE-RUNTIME",
                                  "correlationId": "CORR-RUNTIME-001",
                                  "causationId": "CAUSE-RUNTIME-001",
                                  "hopCount": 0,
                                  "maxHop": 8,
                                  "payload": {
                                    "shipmentId": "SHIP-RUNTIME-001",
                                    "orderId": "ORD-RUNTIME-001",
                                    "originCode": "FAC-A",
                                    "destinationCode": "DC-SEOUL-01",
                                    "requiresShipment": true,
                                    "priority": "HIGH",
                                    "itemType": "battery-module",
                                    "quantity": 4
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mvc.perform(get("/api/runtime-events/recent").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").exists())
                .andExpect(jsonPath("$[0].sourceService").exists())
                .andExpect(jsonPath("$[0].eventType").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].metadata").exists());

        mvc.perform(get("/api/runtime-events/correlation/CORR-RUNTIME-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correlationId").value("CORR-RUNTIME-001"));

        mvc.perform(get("/api/runtime-events/entity/SHIP-RUNTIME-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entityId").value("SHIP-RUNTIME-001"));
    }

    @Test
    void operationsSummaryIncludesLiveFlowContract() throws Exception {
        mvc.perform(get("/api/operations/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("Archive-Nexus"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.outbox.pending").exists())
                .andExpect(jsonPath("$.outbox.published").exists())
                .andExpect(jsonPath("$.outbox.failed").exists())
                .andExpect(jsonPath("$.outbox.retry").exists())
                .andExpect(jsonPath("$.workforce.totalHeadcount").exists())
                .andExpect(jsonPath("$.liveFlowAvailable").value(true));
    }
}
