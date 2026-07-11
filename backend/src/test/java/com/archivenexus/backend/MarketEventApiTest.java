package com.archivenexus.backend;

import com.archivenexus.backend.market.MarketEventModels.MarketBulkEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.market.MarketEventModels.MarketEventType;
import com.archivenexus.backend.market.MarketInboundEventRepository;
import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxModels.RoutingStatus;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_market;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.integrations.logitics.enabled=false",
        "archive.integrations.ledger.enabled=false",
        "archive.integrations.market.enabled=false"
})
@AutoConfigureMockMvc
class MarketEventApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    MarketInboundEventRepository repository;

    @Autowired
    ObjectMapper mapper;

    @MockBean
    OutboxEventService outbox;

    @MockBean
    DomainAggregateProjectionService projections;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        reset(outbox);
        when(outbox.emit(any(), anyString(), anyString(), anyString(), any(), any(Instant.class), anyString()))
                .thenAnswer(invocation -> {
                    EventType eventType = invocation.getArgument(0);
                    String aggregateType = invocation.getArgument(1);
                    String aggregateId = invocation.getArgument(2);
                    String eventId = "EVT-" + System.nanoTime();
                    String idempotency = invocation.getArgument(3);
                    return Optional.of(new OutboxEventResponse(
                            1L,
                            eventId,
                            idempotency,
                            eventType,
                            aggregateType,
                            aggregateId,
                            "Archive-Market",
                            1,
                            Map.of("mock", true, "aggregateType", aggregateType, "aggregateId", aggregateId),
                            OutboxStatus.PENDING,
                            0,
                            null,
                            OutboxTargetService.LOGITICS,
                            null,
                            RoutingStatus.ROUTED,
                            null,
                            null,
                            null,
                            Instant.now(),
                            Instant.now(),
                            null
                    ));
        });
    }

    @Test
    void marketOrderPlacedIsStoredWithoutEmittingOutbox() throws Exception {
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(orderPlacedRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.outboxEventsGenerated").value(0));

        verifyNoInteractions(outbox);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void productionRequestedRunsMaterialQualityProductionAndDispatchChain() throws Exception {
        MarketEventRequest request = productionRequestedRequest();
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("PRODUCTION_REQUESTED"))
                .andExpect(jsonPath("$.outboxEventsGenerated").value(4))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.emittedOutboxEventIds").isArray())
                .andExpect(jsonPath("$.emittedOutboxEventIds[0]").isNotEmpty());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass((Class) Map.class);
        org.mockito.Mockito.verify(outbox).emit(
                eq(EventType.PRODUCTION_COMPLETED),
                eq("ProductionOrder"),
                eq("ORD-1001"),
                anyString(),
                payloadCaptor.capture(),
                any(Instant.class),
                eq("Archive-Market")
        );
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("orderId")).isEqualTo("ORD-1001");
        assertThat(payload.get("customerType")).isEqualTo("CONSUMER");
        assertThat(payload.get("productType")).isEqualTo("BATTERY_PACK");
        assertThat(payload.get("totalAmount")).isEqualTo(1200000);
        assertThat(payload.get("marketPayload")).isInstanceOf(Map.class);
        assertThat(((Map<String, Object>) payload.get("marketPayload")).get("orderId")).isEqualTo("ORD-1001");
        org.mockito.Mockito.verify(outbox).emit(eq(EventType.MATERIAL_CONSUMED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        org.mockito.Mockito.verify(outbox).emit(eq(EventType.QUALITY_INSPECTION_COMPLETED), anyString(), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
        org.mockito.Mockito.verify(outbox).emit(eq(EventType.LOGISTICS_DISPATCHED), eq("MarketShipment"), anyString(), anyString(), any(), any(Instant.class), eq("Archive-Market"));
    }

    @Test
    void shipmentRequestedMapsToLogisticsDispatchedWhenRequiresShipment() throws Exception {
        MarketEventRequest request = shipmentRequestedRequest();
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outboxEventsGenerated").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        org.mockito.Mockito.verify(outbox).emit(
                eq(EventType.LOGISTICS_DISPATCHED),
                eq("MarketShipment"),
                eq("SHIP-9001"),
                anyString(),
                any(),
                any(Instant.class),
                eq("Archive-Market")
        );
    }

    @Test
    void orderCancelledMapsToShipmentHoldCreatedOutboxEvent() throws Exception {
        MarketEventRequest request = orderCancelledRequest();
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outboxEventsGenerated").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        org.mockito.Mockito.verify(outbox).emit(
                eq(EventType.SHIPMENT_HOLD_CREATED),
                eq("MarketShipmentHold"),
                eq("ORD-2002"),
                anyString(),
                any(),
                any(Instant.class),
                eq("Archive-Market")
        );
    }

    @Test
    void returnRequestedMapsToQualityDefectDetectedOutboxEvent() throws Exception {
        MarketEventRequest request = returnRequestedRequest();
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outboxEventsGenerated").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        org.mockito.Mockito.verify(outbox).emit(
                eq(EventType.QUALITY_DEFECT_DETECTED),
                eq("MarketReturn"),
                eq("RET-3003"),
                anyString(),
                any(),
                any(Instant.class),
                eq("Archive-Market")
        );
    }

    @Test
    void qualityClaimCreatedMapsToQualityClaimChargedOutboxEvent() throws Exception {
        MarketEventRequest request = qualityClaimRequest();
        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outboxEventsGenerated").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        org.mockito.Mockito.verify(outbox).emit(
                eq(EventType.QUALITY_CLAIM_CHARGED),
                eq("MarketQualityClaim"),
                eq("CLM-4004"),
                anyString(),
                any(),
                any(Instant.class),
                eq("Archive-Market")
        );
    }

    @Test
    void duplicateMarketEventIsMarkedDuplicateWithoutReEmitting() throws Exception {
        MarketEventRequest request = productionRequestedRequest();
        String body = eventJson(request);

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.outboxEventsGenerated").value(0));

        org.mockito.Mockito.verify(outbox, atMost(1)).emit(
                eq(EventType.PRODUCTION_COMPLETED),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(Instant.class),
                eq("Archive-Market")
        );
    }

    @Test
    void hopOverflowIsRejectedAndNotPersistedToOutbox() throws Exception {
        MarketEventRequest request = productionRequestedRequest();
        request = new MarketEventRequest(
                request.eventId(),
                request.idempotencyKey(),
                request.source(),
                request.eventType(),
                request.schemaVersion(),
                request.occurredAt(),
                request.simulationRunId(),
                request.settlementCycleId(),
                request.correlationId(),
                request.causationId(),
                20,
                3,
                request.payload()
        );

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.outboxEventsGenerated").value(0));
    }

    @Test
    void marketBulkReceivesAndListsByStatus() throws Exception {
        MarketBulkEventRequest bulk = new MarketBulkEventRequest(List.of(
                productionRequestedRequest(),
                returnRequestedRequest(),
                qualityClaimRequest()
        ));
        mvc.perform(post("/api/events/market/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bulk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.received").value(3))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results", hasSize(3)));

        mvc.perform(get("/api/events/market")
                        .param("status", MarketEventStatus.PROCESSED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void unknownEventTypeIsHandledAsFailedInsteadOf500() throws Exception {
        MarketEventRequest request = new MarketEventRequest(
                "evt-unknown",
                "mk-unknown",
                "Archive-Market",
                MarketEventType.UNKNOWN,
                1,
                Instant.now(),
                "sim-1",
                "cycle-1",
                "corr-1",
                "cause-1",
                0,
                10,
                Map.of("orderId", "ORD-UNK", "customerType", "RETAIL")
        );

        mvc.perform(post("/api/events/market")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    private static MarketEventRequest orderPlacedRequest() {
        return new MarketEventRequest(
                "evt-order-1",
                "mk-order-1",
                "Archive-Market",
                MarketEventType.MARKET_ORDER_PLACED,
                1,
                Instant.now(),
                "sim-1",
                "cycle-1",
                "corr-1",
                "cause-1",
                0,
                5,
                Map.of(
                        "orderId", "ORD-1001",
                        "customerId", "CUST-001",
                        "customerType", "CONSUMER",
                        "productType", "BATTERY_PACK",
                        "quantity", 12,
                        "orderAmount", 1200000L,
                        "priority", "NORMAL",
                        "requiresShipment", true,
                        "riskLevel", "LOW"
                )
        );
    }

    private static MarketEventRequest productionRequestedRequest() {
        return new MarketEventRequest(
                "evt-prod-1",
                "mk-prod-1",
                "Archive-Market",
                MarketEventType.PRODUCTION_REQUESTED,
                1,
                Instant.now(),
                "sim-1",
                "cycle-1",
                "corr-1",
                "cause-1",
                0,
                5,
                Map.of(
                        "orderId", "ORD-1001",
                        "customerId", "CUST-001",
                        "customerType", "CONSUMER",
                        "riskLevel", "LOW",
                        "productType", "BATTERY_PACK",
                        "quantity", 12,
                        "orderAmount", 1200000L,
                        "priority", "NORMAL",
                        "requiresShipment", true
                )
        );
    }

    private static MarketEventRequest shipmentRequestedRequest() {
        return new MarketEventRequest(
                "evt-ship-1",
                "mk-ship-1",
                "Archive-Market",
                MarketEventType.SHIPMENT_REQUESTED,
                1,
                Instant.now(),
                "sim-2",
                "cycle-2",
                "corr-2",
                "cause-2",
                0,
                6,
                Map.of(
                        "shipmentId", "SHIP-9001",
                        "orderId", "ORD-1001",
                        "originCode", "FAC-A",
                        "destinationCode", "DC-SEOUL-01",
                        "requiresShipment", true,
                        "priority", "HIGH",
                        "itemType", "battery-module",
                        "quantity", 4
                )
        );
    }

    private static MarketEventRequest orderCancelledRequest() {
        return new MarketEventRequest(
                "evt-cancel-1",
                "mk-cancel-1",
                "Archive-Market",
                MarketEventType.ORDER_CANCELLED,
                1,
                Instant.now(),
                "sim-3",
                "cycle-3",
                "corr-3",
                "cause-3",
                0,
                7,
                Map.of(
                        "orderId", "ORD-2002",
                        "customerId", "CUST-002",
                        "customerType", "ENTERPRISE",
                        "riskLevel", "MEDIUM",
                        "requiresShipment", false,
                        "priority", "LOW"
                )
        );
    }

    private static MarketEventRequest returnRequestedRequest() {
        return new MarketEventRequest(
                "evt-return-1",
                "mk-return-1",
                "Archive-Market",
                MarketEventType.RETURN_REQUESTED,
                1,
                Instant.now(),
                "sim-4",
                "cycle-4",
                "corr-4",
                "cause-4",
                0,
                4,
                Map.of(
                        "returnId", "RET-3003",
                        "orderId", "ORD-1001",
                        "customerId", "CUST-001",
                        "customerType", "CONSUMER",
                        "riskLevel", "HIGH",
                        "productType", "BATTERY_PACK",
                        "priority", "CRITICAL"
                )
        );
    }

    private static MarketEventRequest qualityClaimRequest() {
        return new MarketEventRequest(
                "evt-claim-1",
                "mk-claim-1",
                "Archive-Market",
                MarketEventType.QUALITY_CLAIM_CREATED,
                1,
                Instant.now(),
                "sim-5",
                "cycle-5",
                "corr-5",
                "cause-5",
                0,
                4,
                Map.of(
                        "claimId", "CLM-4004",
                        "orderId", "ORD-5005",
                        "customerId", "CUST-005",
                        "customerType", "ENTERPRISE",
                        "riskLevel", "CRITICAL",
                        "productType", "BATTERY_PACK",
                        "priority", "HIGH"
                )
        );
    }

    private String eventJson(MarketEventRequest request) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("eventId", request.eventId());
            body.put("idempotencyKey", request.idempotencyKey());
            body.put("source", request.source());
            body.put("eventType", request.eventType().name());
            body.put("schemaVersion", request.schemaVersion());
            body.put("occurredAt", request.occurredAt().toString());
            body.put("simulationRunId", request.simulationRunId());
            body.put("settlementCycleId", request.settlementCycleId());
            body.put("correlationId", request.correlationId());
            body.put("causationId", request.causationId());
            body.put("hopCount", request.hopCount());
            body.put("maxHop", request.maxHop());
            body.put("payload", request.payload());
            return mapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
