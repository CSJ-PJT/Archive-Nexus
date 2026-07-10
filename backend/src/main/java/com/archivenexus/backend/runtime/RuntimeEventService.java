package com.archivenexus.backend.runtime;

import com.archivenexus.backend.market.MarketInboundEventEntity;
import com.archivenexus.backend.market.MarketInboundEventRepository;
import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxSummary;
import com.archivenexus.backend.runtime.RuntimeEventModels.*;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceSummary;
import com.archivenexus.backend.workforce.WorkforceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RuntimeEventService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SERVICE_NAME = "Archive-Nexus";
    private static final String SERVICE_ROLE = "Manufacturing AX runtime, market inbound, workforce capacity, and outbox routing";

    private final OutboxEventService outbox;
    private final MarketInboundEventRepository marketEvents;
    private final WorkforceService workforce;
    private final ObjectMapper mapper;

    public RuntimeEventService(OutboxEventService outbox,
                               MarketInboundEventRepository marketEvents,
                               WorkforceService workforce,
                               ObjectMapper mapper) {
        this.outbox = outbox;
        this.marketEvents = marketEvents;
        this.workforce = workforce;
        this.mapper = mapper;
    }

    public List<RuntimeEventResponse> recent(int limit) {
        int safeLimit = safeLimit(limit);
        List<RuntimeEventResponse> events = new ArrayList<>();
        outbox.events(Math.min(1000, Math.max(safeLimit, 100))).stream()
                .map(this::fromOutbox)
                .forEach(events::add);
        marketEvents.findAllByOrderByReceivedAtDesc(PageRequest.of(0, Math.min(1000, Math.max(safeLimit, 100)))).stream()
                .map(this::fromMarket)
                .forEach(events::add);
        return events.stream()
                .sorted(Comparator.comparing(RuntimeEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(safeLimit)
                .toList();
    }

    public List<RuntimeEventResponse> byCorrelation(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        String safeCorrelationId = correlationId.trim();
        List<RuntimeEventResponse> events = new ArrayList<>();
        marketEvents.findAllByCorrelationIdOrderByReceivedAtDesc(safeCorrelationId, PageRequest.of(0, 500)).stream()
                .map(this::fromMarket)
                .forEach(events::add);
        outbox.events(1000).stream()
                .filter(event -> safeCorrelationId.equals(text(event.payload().get("correlationId"))))
                .map(this::fromOutbox)
                .forEach(events::add);
        return events.stream()
                .sorted(Comparator.comparing(RuntimeEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public List<RuntimeEventResponse> byEntity(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return List.of();
        }
        String safeEntityId = entityId.trim();
        List<RuntimeEventResponse> events = new ArrayList<>();
        outbox.events(1000).stream()
                .filter(event -> safeEntityId.equals(event.aggregateId()) || safeEntityId.equals(entityIdFromPayload(event.payload())))
                .map(this::fromOutbox)
                .forEach(events::add);
        marketEvents.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 1000)).stream()
                .filter(event -> safeEntityId.equals(entityIdFromPayload(read(event.payloadJson()))))
                .map(this::fromMarket)
                .forEach(events::add);
        return events.stream()
                .sorted(Comparator.comparing(RuntimeEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public OperationsSummaryResponse operationsSummary() {
        OutboxSummary outboxSummary = outbox.summary();
        WorkforceSummary workforceSummary = workforce.workforceSummary();
        Instant latestEventAt = recent(1).stream()
                .map(RuntimeEventResponse::occurredAt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        long failed = outboxSummary.failed();
        long retry = outboxSummary.pendingRetry();
        String status = failed > 0 || retry > 0 ? "DEGRADED" : "HEALTHY";
        String degradedReason = failed > 0
                ? "Outbox has failed events"
                : retry > 0 ? "Outbox has retrying events" : null;
        return new OperationsSummaryResponse(
                SERVICE_NAME,
                SERVICE_ROLE,
                status,
                latestEventAt,
                new OutboxOperationsSummary(outboxSummary.pending(), outboxSummary.published(), failed, retry),
                new EconomyOperationsSummary(BigDecimal.ZERO, workforceSummary.payrollCost(), BigDecimal.ZERO.subtract(workforceSummary.payrollCost()), "SYNTHETIC_WORKFORCE_COST_ONLY"),
                new WorkforceOperationsSummary(
                        workforceSummary.totalActiveWorkers(),
                        workforceSummary.estimatedDailyCapacity(),
                        workforceSummary.usedCapacity(),
                        workforceSummary.backlog(),
                        workforceSummary.productivityRate(),
                        workforceSummary.bottleneckRole()
                ),
                degradedReason,
                true,
                List.of(
                        "/api/runtime-events/recent",
                        "/api/runtime-events/correlation/{correlationId}",
                        "/api/runtime-events/entity/{entityId}",
                        "/api/workforce/summary",
                        "/api/productivity/summary",
                        "/api/capacity/summary",
                        "/api/outbox/summary",
                        "/api/integrations/summary",
                        "/api/operations/summary"
                ),
                Instant.now()
        );
    }

    private RuntimeEventResponse fromOutbox(OutboxEventResponse event) {
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        String entityId = text(event.aggregateId(), entityIdFromPayload(payload));
        return new RuntimeEventResponse(
                event.eventId(),
                text(event.source(), SERVICE_NAME),
                domainForOutbox(event),
                event.eventType().name(),
                entityTypeForAggregate(event.aggregateType()),
                entityId,
                text(payload.get("correlationId")),
                text(payload.get("causationId")),
                statusForOutbox(event.status()),
                severityForOutbox(event, payload),
                labelForOutbox(event),
                event.occurredAt(),
                metadataForOutbox(event, payload)
        );
    }

    private RuntimeEventResponse fromMarket(MarketInboundEventEntity event) {
        Map<String, Object> payload = read(event.payloadJson());
        String entityId = entityIdFromPayload(payload);
        return new RuntimeEventResponse(
                event.eventId(),
                text(event.source(), "Archive-Market"),
                "market",
                event.eventType().name(),
                entityTypeForMarket(event.eventType().name()),
                entityId,
                event.correlationId(),
                event.causationId(),
                statusForMarket(event.processingStatus()),
                severityForMarket(event.processingStatus(), payload),
                "Market event " + event.eventType().name() + " was " + event.processingStatus().name().toLowerCase(),
                event.occurredAt(),
                metadataForMarket(event, payload)
        );
    }

    private Map<String, Object> metadataForOutbox(OutboxEventResponse event, Map<String, Object> payload) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "aggregateType", event.aggregateType());
        put(metadata, "aggregateId", event.aggregateId());
        put(metadata, "targetService", event.targetService() == null ? null : event.targetService().name());
        put(metadata, "retryCount", event.retryCount());
        put(metadata, "lastError", event.lastError());
        put(metadata, "workdayId", payload.get("workdayId"));
        put(metadata, "workforceAllocationId", payload.get("workforceAllocationId"));
        put(metadata, "productivityScore", payload.get("productivityScore"));
        put(metadata, "usedCapacity", payload.get("usedCapacity"));
        put(metadata, "backlogCount", payload.get("backlogCount"));
        put(metadata, "factoryId", payload.get("factoryId"));
        put(metadata, "orderId", payload.get("orderId"));
        put(metadata, "shipmentId", payload.get("shipmentId"));
        put(metadata, "priority", payload.get("priority"));
        put(metadata, "simulationRunId", payload.get("simulationRunId"));
        put(metadata, "settlementCycleId", payload.get("settlementCycleId"));
        return metadata;
    }

    private Map<String, Object> metadataForMarket(MarketInboundEventEntity event, Map<String, Object> payload) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "idempotencyKey", event.idempotencyKey());
        put(metadata, "processingStatus", event.processingStatus().name());
        put(metadata, "outboxEventCount", event.outboxEventCount());
        put(metadata, "orderId", payload.get("orderId"));
        put(metadata, "shipmentId", payload.get("shipmentId"));
        put(metadata, "returnId", payload.get("returnId"));
        put(metadata, "claimId", payload.get("claimId"));
        put(metadata, "customerType", payload.get("customerType"));
        put(metadata, "riskLevel", payload.get("riskLevel"));
        put(metadata, "productType", payload.get("productType"));
        put(metadata, "quantity", payload.get("quantity"));
        put(metadata, "priority", payload.get("priority"));
        put(metadata, "simulationRunId", event.simulationRunId());
        put(metadata, "settlementCycleId", event.settlementCycleId());
        put(metadata, "reason", event.reason());
        return metadata;
    }

    private String domainForOutbox(OutboxEventResponse event) {
        if (event.eventType().name().contains("LOGISTICS") || event.eventType().name().contains("SHIPMENT")) {
            return "logistics";
        }
        if (event.eventType().name().contains("QUALITY")) {
            return "quality";
        }
        if (event.eventType().name().contains("MAINTENANCE")) {
            return "maintenance";
        }
        if ("Archive-Market".equals(event.source())) {
            return "market-to-manufacturing";
        }
        return "manufacturing";
    }

    private String entityTypeForAggregate(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return "manufacturing-event";
        }
        return aggregateType.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String entityTypeForMarket(String eventType) {
        if (eventType == null) {
            return "market-event";
        }
        if (eventType.contains("ORDER")) {
            return "order";
        }
        if (eventType.contains("SHIPMENT")) {
            return "shipment";
        }
        if (eventType.contains("RETURN")) {
            return "return";
        }
        if (eventType.contains("CLAIM")) {
            return "claim";
        }
        return "market-event";
    }

    private String statusForOutbox(OutboxStatus status) {
        if (status == null) {
            return "waiting";
        }
        return switch (status) {
            case PENDING -> "waiting";
            case PENDING_RETRY -> "delayed";
            case FAILED -> "failed";
            case SKIPPED -> "completed";
            case PUBLISHED -> "completed";
        };
    }

    private String statusForMarket(MarketEventStatus status) {
        if (status == null) {
            return "waiting";
        }
        return switch (status) {
            case RECEIVED -> "created";
            case PROCESSED -> "completed";
            case DUPLICATE -> "completed";
            case REJECTED, FAILED -> "failed";
        };
    }

    private String severityForOutbox(OutboxEventResponse event, Map<String, Object> payload) {
        if (event.status() == OutboxStatus.FAILED) {
            return "critical";
        }
        if (event.status() == OutboxStatus.PENDING_RETRY) {
            return "warning";
        }
        Object severity = payload.get("severity");
        if (severity != null && List.of("HIGH", "CRITICAL").contains(severity.toString().toUpperCase())) {
            return "warning";
        }
        return "info";
    }

    private String severityForMarket(MarketEventStatus status, Map<String, Object> payload) {
        if (status == MarketEventStatus.FAILED || status == MarketEventStatus.REJECTED) {
            return "warning";
        }
        Object risk = payload.get("riskLevel");
        if (risk != null && List.of("HIGH", "CRITICAL").contains(risk.toString().toUpperCase())) {
            return "warning";
        }
        return "info";
    }

    private String labelForOutbox(OutboxEventResponse event) {
        return event.eventType().name() + " routed to " + (event.targetService() == null ? "UNKNOWN" : event.targetService().name());
    }

    private String entityIdFromPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : List.of("orderId", "shipmentId", "returnId", "claimId", "factoryId", "equipmentId", "vendorId", "workdayId")) {
            Object value = payload.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String text(Object value, String fallback) {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !value.toString().isBlank() && !looksSensitive(key)) {
            metadata.put(key, value);
        }
    }

    private static boolean looksSensitive(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("webhook")
                || lower.contains("private")
                || lower.contains("phone")
                || lower.contains("address")
                || lower.contains("cardnumber")
                || lower.contains("accountnumber");
    }
}
