package com.archivenexus.backend;

import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
                .andExpect(jsonPath("$[0].idempotencyKey").exists())
                .andExpect(jsonPath("$[0].sourceService").exists())
                .andExpect(jsonPath("$[0].targetService").exists())
                .andExpect(jsonPath("$[0].eventType").exists())
                .andExpect(jsonPath("$[0].hopCount").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].metadata").exists())
                .andExpect(jsonPath("$[?(@.simulationRunId=='SIM-RUNTIME')]").exists());

        mvc.perform(get("/api/runtime-events/recent")
                        .param("after", "0|bootstrap")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").exists());

        mvc.perform(get("/api/runtime-events/correlation/CORR-RUNTIME-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correlationId").value("CORR-RUNTIME-001"));

        mvc.perform(get("/api/runtime-events/entity/SHIP-RUNTIME-001"))
                .andExpect(status().isOk())
                // Entity queries may include related outbox projections before the inbound
                // event, so assert membership rather than an implementation-specific order.
                .andExpect(jsonPath("$[?(@.entityId=='SHIP-RUNTIME-001')]").exists());
    }

    @Test
    void operationsSummaryIncludesLiveFlowContract() throws Exception {
        mvc.perform(get("/api/operations/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("Archive-Nexus"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.productionRequested").exists())
                .andExpect(jsonPath("$.productionCompleted").exists())
                .andExpect(jsonPath("$.productionBacklog").exists())
                .andExpect(jsonPath("$.qualityDefects").exists())
                .andExpect(jsonPath("$.marketOriginEvents").exists())
                .andExpect(jsonPath("$.outbox.pending").exists())
                .andExpect(jsonPath("$.outbox.published").exists())
                .andExpect(jsonPath("$.outbox.failed").exists())
                .andExpect(jsonPath("$.outbox.retry").exists())
                .andExpect(jsonPath("$.economy.manufacturingRevenue").exists())
                .andExpect(jsonPath("$.economy.materialCost").exists())
                .andExpect(jsonPath("$.economy.operatingMargin").exists())
                .andExpect(jsonPath("$.economy.cashBalance").exists())
                .andExpect(jsonPath("$.economy.negativeProfitStreak").exists())
                .andExpect(jsonPath("$.economy.calculationScope").exists())
                .andExpect(jsonPath("$.economy.calculatedAt").exists())
                .andExpect(jsonPath("$.production.productionRequested").value(nullValue()))
                .andExpect(jsonPath("$.production.productionCompleted").value(nullValue()))
                .andExpect(jsonPath("$.production.productionBacklog").value(nullValue()))
                .andExpect(jsonPath("$.workforce.totalHeadcount").exists())
                .andExpect(jsonPath("$.workforce.effectiveCapacity").exists())
                .andExpect(jsonPath("$.workforce.usedCapacity").exists())
                .andExpect(jsonPath("$.workforce.capacityUtilization").exists())
                .andExpect(jsonPath("$.workforce.bottleneckRole").exists())
                .andExpect(jsonPath("$.liveFlowAvailable").value(true));
    }

    @Test
    void projectsMarketOrderProductionWorkforceAndWorkdayEvents() throws Exception {
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "runtime-order-001",
                                  "idempotencyKey": "runtime-order-001",
                                  "source": "Archive-Market",
                                  "eventType": "MARKET_ORDER_PLACED",
                                  "schemaVersion": 1,
                                  "occurredAt": "2026-07-10T01:00:00Z",
                                  "simulationRunId": "SIM-RUNTIME",
                                  "settlementCycleId": "CYCLE-RUNTIME",
                                  "correlationId": "CORR-RUNTIME-ORDER",
                                  "causationId": "CAUSE-RUNTIME-ORDER",
                                  "hopCount": 0,
                                  "maxHop": 8,
                                  "payload": {
                                    "orderId": "ORD-RUNTIME-ORDER",
                                    "customerType": "B2B",
                                    "productType": "BATTERY_PACK",
                                    "quantity": 12,
                                    "orderAmount": 1200000,
                                    "priority": "NORMAL",
                                    "requiresShipment": true
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "runtime-prod-001",
                                  "idempotencyKey": "runtime-prod-001",
                                  "source": "Archive-Market",
                                  "eventType": "PRODUCTION_REQUESTED",
                                  "schemaVersion": 1,
                                  "occurredAt": "2026-07-10T01:01:00Z",
                                  "simulationRunId": "SIM-RUNTIME",
                                  "settlementCycleId": "CYCLE-RUNTIME",
                                  "correlationId": "CORR-RUNTIME-PROD",
                                  "causationId": "CAUSE-RUNTIME-PROD",
                                  "hopCount": 0,
                                  "maxHop": 8,
                                  "payload": {
                                    "orderId": "ORD-RUNTIME-PROD",
                                    "customerType": "B2B",
                                    "productType": "BATTERY_PACK",
                                    "quantity": 12,
                                    "orderAmount": 1200000,
                                    "priority": "HIGH",
                                    "requiresShipment": true
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/workforce/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "runtime-workforce-001",
                                  "idempotencyKey": "runtime-workforce-001",
                                  "sourceService": "ArchiveOS",
                                  "eventType": "WORKFORCE_ALLOCATION_ASSIGNED",
                                  "role": "PRODUCTION_OPERATOR",
                                  "allocatedHeadcount": 2,
                                  "capacityPerPersonPerDay": 10,
                                  "productivityScore": 1.0,
                                  "wagePerDay": 100000,
                                  "workdayId": "NEXUS-WORKDAY-2026-07-10",
                                  "correlationId": "CORR-RUNTIME-WF",
                                  "causationId": "CAUSE-RUNTIME-WF",
                                  "hopCount": 0,
                                  "maxHop": 8
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/workforce/workday/run").param("date", "2026-07-10"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/runtime-events/recent").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType=='MARKET_ORDER_RECEIVED')]", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.eventType=='PRODUCTION_REQUESTED')]", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.eventType=='PRODUCTION_STARTED')]", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.eventType=='WORKFORCE_ALLOCATION_ASSIGNED')]", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.eventType=='WORKDAY_COMPLETED')]", hasSize(greaterThanOrEqualTo(1))));
    }
}
