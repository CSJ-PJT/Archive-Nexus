package com.archivenexus.backend;

import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxModels.RoutingStatus;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import com.archivenexus.backend.market.MarketInboundEventRepository;
import com.archivenexus.backend.workforce.WorkdayResultRepository;
import com.archivenexus.backend.workforce.WorkforceAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_workforce_market;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.workforce.enabled=true"
})
@AutoConfigureMockMvc
class WorkforceDrivenMarketEventTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    WorkforceAllocationRepository allocations;

    @Autowired
    WorkdayResultRepository workdayResults;

    @Autowired
    MarketInboundEventRepository marketEvents;

    @MockBean
    OutboxEventService outbox;

    @MockBean
    DomainAggregateProjectionService projections;

    @BeforeEach
    void setup() {
        workdayResults.deleteAll();
        allocations.deleteAll();
        marketEvents.deleteAll();
        reset(outbox);
        when(outbox.emit(any(), anyString(), anyString(), anyString(), any(), any(Instant.class), anyString()))
                .thenAnswer(invocation -> Optional.of(new OutboxEventResponse(
                        1L,
                        "NX-EVT-WF",
                        invocation.getArgument(3),
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(6),
                        1,
                        invocation.getArgument(4),
                        OutboxStatus.PENDING,
                        0,
                        null,
                        OutboxTargetService.NONE,
                        null,
                        RoutingStatus.ROUTE_SKIPPED,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now(),
                        null
                )));
    }

    @Test
    void marketProductionRequestConsumesWorkforceCapacityAndCreatesBacklog() throws Exception {
        mvc.perform(post("/api/workforce/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "WF-MARKET-001",
                                  "idempotencyKey": "WF-MARKET-IDEMP-001",
                                  "sourceService": "ArchiveOS",
                                  "role": "PRODUCTION_OPERATOR",
                                  "allocatedHeadcount": 1,
                                  "capacityPerPersonPerDay": 20,
                                  "productivityScore": 1.0,
                                  "wagePerDay": 100000,
                                  "workdayId": "WD-MARKET-001",
                                  "hopCount": 0,
                                  "maxHop": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveCapacity").value(20));

        allocate("WF-MARKET-MATERIAL", "MATERIAL_HANDLER", 1, 25);
        allocate("WF-MARKET-QUALITY", "QUALITY_INSPECTOR", 1, 30);

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "MK-WF-PROD-001",
                                  "idempotencyKey": "MK-WF-PROD-IDEMP-001",
                                  "source": "Archive-Market",
                                  "eventType": "PRODUCTION_REQUESTED",
                                  "schemaVersion": 1,
                                  "occurredAt": "2026-07-10T00:00:00Z",
                                  "simulationRunId": "SIM-WF-001",
                                  "settlementCycleId": "CYCLE-WF-001",
                                  "correlationId": "CORR-WF-001",
                                  "causationId": "CAUSE-WF-001",
                                  "hopCount": 0,
                                  "maxHop": 8,
                                  "payload": {
                                    "orderId": "ORD-WF-001",
                                    "quantity": 25,
                                    "orderAmount": 2500000,
                                    "workdayId": "WD-MARKET-001"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass((Class) Map.class);
        verify(outbox).emit(eq(EventType.BACKLOG_INCREASED), eq("ProductionOrder"), eq("ORD-WF-001"),
                anyString(), payloadCaptor.capture(), any(Instant.class), eq("Archive-Market"));
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("workdayId")).isEqualTo("WD-MARKET-001");
        assertThat(payload.get("usedCapacity")).isEqualTo(20);
        assertThat(payload.get("backlogCount")).isEqualTo(5);
        assertThat(payload.get("bottleneckRole")).isEqualTo("PRODUCTION_OPERATOR");
    }

    @Test
    void qualityCapacityShortagePreventsLogisticsDispatch() throws Exception {
        allocate("WF-Q-PROD", "PRODUCTION_OPERATOR", 1, 20);
        allocate("WF-Q-MATERIAL", "MATERIAL_HANDLER", 1, 25);

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "MK-Q-001",
                                  "idempotencyKey": "MK-Q-IDEMP-001",
                                  "source": "Archive-Market",
                                  "eventType": "PRODUCTION_REQUESTED",
                                  "schemaVersion": 1,
                                  "occurredAt": "2026-07-10T00:00:00Z",
                                  "correlationId": "CORR-Q-001",
                                  "hopCount": 0,
                                  "maxHop": 8,
                                  "payload": {"orderId": "ORD-Q-001", "shipmentId": "SHIP-Q-001", "quantity": 12, "requiresShipment": true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        verify(outbox).emit(eq(EventType.PRODUCTION_DELAYED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        verify(outbox).emit(eq(EventType.QUALITY_DEFECT_DETECTED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        verify(outbox, never()).emit(eq(EventType.LOGISTICS_DISPATCHED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
    }

    @Test
    void materialCapacityShortageDelaysProductionWithoutCompletion() throws Exception {
        allocate("WF-M-PROD", "PRODUCTION_OPERATOR", 1, 20);
        allocate("WF-M-QUALITY", "QUALITY_INSPECTOR", 1, 30);
        postProduction("MK-M-001", "ORD-M-001", "SHIP-M-001", 12, false);

        verify(outbox).emit(eq(EventType.PRODUCTION_DELAYED), anyString(), eq("ORD-M-001"), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        verify(outbox, never()).emit(eq(EventType.PRODUCTION_COMPLETED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        verify(outbox, never()).emit(eq(EventType.LOGISTICS_DISPATCHED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
    }

    @Test
    void criticalMaintenanceUsesEngineerCapacityBeforeProductionCanDispatch() throws Exception {
        allocate("WF-D-PROD", "PRODUCTION_OPERATOR", 1, 20);
        allocate("WF-D-MATERIAL", "MATERIAL_HANDLER", 1, 25);
        allocate("WF-D-QUALITY", "QUALITY_INSPECTOR", 1, 30);
        allocate("WF-D-MAINT", "MAINTENANCE_ENGINEER", 1, 5);
        postProduction("MK-D-001", "ORD-D-001", "SHIP-D-001", 12, true);

        verify(outbox).emit(eq(EventType.MAINTENANCE_COMPLETED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        verify(outbox).emit(eq(EventType.PRODUCTION_COMPLETED), anyString(), eq("ORD-D-001"), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        verify(outbox).emit(eq(EventType.LOGISTICS_DISPATCHED), anyString(), eq("SHIP-D-001"), anyString(), any(), any(Instant.class), eq("Archive-Market"));
    }

    private void postProduction(String eventId, String orderId, String shipmentId, int quantity, boolean maintenanceRequired) throws Exception {
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"%s","idempotencyKey":"%s","source":"Archive-Market","eventType":"PRODUCTION_REQUESTED","schemaVersion":1,"occurredAt":"2026-07-10T00:00:00Z","correlationId":"CORR-%s","hopCount":0,"maxHop":8,"payload":{"orderId":"%s","shipmentId":"%s","quantity":%d,"requiresShipment":true,"maintenanceRequired":%s}}
                                """.formatted(eventId, eventId, eventId, orderId, shipmentId, quantity, maintenanceRequired)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    private void allocate(String eventId, String role, int headcount, int capacity) throws Exception {
        mvc.perform(post("/api/workforce/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"%s","idempotencyKey":"%s","sourceService":"ArchiveOS","role":"%s","allocatedHeadcount":%d,"capacityPerPersonPerDay":%d,"productivityScore":1.0,"wagePerDay":100000,"workdayId":"WD-Q-001","hopCount":0,"maxHop":8}
                                """.formatted(eventId, eventId, role, headcount, capacity)))
                .andExpect(status().isOk());
    }
}
