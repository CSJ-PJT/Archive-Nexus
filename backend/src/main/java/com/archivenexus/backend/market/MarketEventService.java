package com.archivenexus.backend.market;

import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.workforce.WorkforceService;
import com.archivenexus.backend.archiveos.runtime.ArchiveOsRuntimeDeliveryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.archivenexus.backend.market.MarketEventModels.*;

@Service
public class MarketEventService {
    private static final Logger log = LoggerFactory.getLogger(MarketEventService.class);
    private static final int DEFAULT_MAX_HOP = 8;
    private static final String SOURCE_MARKET = "Archive-Market";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MarketInboundEventRepository repository;
    private final OutboxEventService outbox;
    private final WorkforceService workforce;
    private final ObjectMapper mapper;
    private final ObjectProvider<ArchiveOsRuntimeDeliveryService> runtimeDeliveries;

    public MarketEventService(MarketInboundEventRepository repository, OutboxEventService outbox,
                              WorkforceService workforce, ObjectMapper mapper, ObjectProvider<ArchiveOsRuntimeDeliveryService> runtimeDeliveries) {
        this.repository = repository;
        this.outbox = outbox;
        this.workforce = workforce;
        this.mapper = mapper;
        this.runtimeDeliveries = runtimeDeliveries;
    }

    @Transactional
    public MarketEventResponse receive(MarketEventRequest request) {
        MarketInboundEventRequest header = toHeader(request);

        if (header.hopCount() > header.maxHop()) {
            MarketInboundEventEntity rejected = new MarketInboundEventEntity(
                    header.eventId(),
                    header.idempotencyKey(),
                    header.source(),
                    header.eventType(),
                    header.schemaVersion(),
                    header.occurredAt(),
                    Instant.now(),
                    header.simulationRunId(),
                    header.settlementCycleId(),
                    header.correlationId(),
                    header.causationId(),
                    header.hopCount(),
                    header.maxHop(),
                    write(request.payload())
            );
            rejected.markRejected("hopCount exceeds maxHop");
            MarketInboundEventEntity saved = repository.save(rejected);
            return response(saved, List.of(), Map.of(), false);
        }

        MarketInboundEventEntity existing = repository.findByIdempotencyKey(header.idempotencyKey())
                .orElseGet(() -> repository.findByEventId(header.eventId()).orElse(null));
        if (existing != null) {
            return response(existing, List.of(), Map.of(), true);
        }

        MarketInboundEventEntity entity = new MarketInboundEventEntity(
                header.eventId(),
                header.idempotencyKey(),
                header.source(),
                header.eventType(),
                header.schemaVersion(),
                header.occurredAt(),
                Instant.now(),
                header.simulationRunId(),
                header.settlementCycleId(),
                header.correlationId(),
                header.causationId(),
                header.hopCount(),
                header.maxHop(),
                write(request.payload())
        );

        Map<String, Object> rawPayload = request.payload() == null ? Map.of() : request.payload();
        Map<String, Object> payload = preserveMetadata(header, rawPayload);
        try {
            List<OutboxEventResponse> emitted = emitOutboxEvents(header, payload);
            entity.markProcessed(emitted.size(), extractedOutboxIds(emitted));
            MarketInboundEventEntity saved = repository.save(entity);
            snapshotMarketAfterCommit(saved, payload);
            return response(saved, emitted, rawPayload, false);
        } catch (IllegalArgumentException ex) {
            entity.markFailed(ex.getMessage());
            MarketInboundEventEntity saved = repository.save(entity);
            return response(saved, List.of(), rawPayload, false);
        }
    }

    private void snapshotMarketAfterCommit(MarketInboundEventEntity event, Map<String,Object> payload) {
        if (event.eventType() != MarketEventType.PRODUCTION_REQUESTED) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    ArchiveOsRuntimeDeliveryService delivery = runtimeDeliveries.getIfAvailable();
                    if (delivery == null) return;
                    String orderId = asText(payload.get("orderId"));
                    String entityId = asText(payload.get("productionRequestId"));
                    if (entityId == null) entityId = event.eventId();
                    delivery.snapshotMarket(event.eventId(), event.idempotencyKey(), event.correlationId(), event.causationId(), orderId, event.simulationRunId(), entityId, "PRODUCTION_REQUESTED", event.occurredAt(), payload);
                    delivery.snapshotMarket(event.eventId()+":PRODUCTION_STARTED", event.idempotencyKey()+":PRODUCTION_STARTED", event.correlationId(), event.eventId(), orderId, event.simulationRunId(), entityId, "PRODUCTION_STARTED", event.occurredAt(), payload);
                } catch (RuntimeException ignored) { }
            }
        });
    }

    @Transactional
    public MarketBulkEventResponse receiveBulk(MarketBulkEventRequest request) {
        List<MarketEventRequest> events = request == null || request.events() == null ? List.of() : request.events();
        List<MarketEventResponse> responses = new ArrayList<>();

        int requested = events.size();
        int processed = 0;
        int duplicates = 0;
        int rejected = 0;
        int failed = 0;

        for (MarketEventRequest event : events) {
            try {
                MarketEventResponse result = receive(event);
                responses.add(result);
                switch (result.status()) {
                    case DUPLICATE -> {
                        duplicates++;
                    }
                    case REJECTED -> {
                        rejected++;
                    }
                    case FAILED -> {
                        failed++;
                    }
                    default -> {
                        processed++;
                    }
                }
            } catch (RuntimeException ex) {
                failed++;
                responses.add(new MarketEventResponse(
                        event == null ? null : event.eventId(),
                        event == null ? null : event.idempotencyKey(),
                        event == null ? MarketEventType.UNKNOWN : event.eventType(),
                        MarketEventStatus.FAILED,
                        false,
                        0,
                        List.of(),
                        ex.getMessage(),
                        null,
                        null
                ));
            }
        }
        return new MarketBulkEventResponse(requested, processed, duplicates, rejected, failed, responses);
    }

    public List<MarketEventResponse> list(Integer limit, MarketEventStatus status) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        var page = org.springframework.data.domain.PageRequest.of(0, safeLimit);
        if (status == null) {
            return repository.findAllByOrderByReceivedAtDesc(page).stream()
                    .map(event -> {
                        List<String> outboxIds = extractOutboxIds(event.outboxEventIds());
                        return new MarketEventResponse(
                                event.eventId(),
                                event.idempotencyKey(),
                                event.eventType(),
                                event.processingStatus(),
                                event.processingStatus() == MarketEventStatus.DUPLICATE,
                                outboxIds.size(),
                                outboxIds,
                                event.reason(),
                                event.correlationId(),
                                event.causationId()
                        );
                    }).toList();
        }
        return repository.findAllByProcessingStatusOrderByReceivedAtDesc(status, page).stream()
                .map(event -> {
                    List<String> outboxIds = extractOutboxIds(event.outboxEventIds());
                    return new MarketEventResponse(
                            event.eventId(),
                            event.idempotencyKey(),
                            event.eventType(),
                            event.processingStatus(),
                            event.processingStatus() == MarketEventStatus.DUPLICATE,
                            outboxIds.size(),
                            outboxIds,
                            event.reason(),
                            event.correlationId(),
                            event.causationId()
                    );
                }).toList();
    }

    public long marketEventsReceived() {
        return repository.countByProcessingStatus(MarketEventStatus.RECEIVED);
    }

    public long marketEventsProcessed() {
        return repository.countByProcessingStatus(MarketEventStatus.PROCESSED);
    }

    public long marketEventsFailed() {
        return repository.countByProcessingStatus(MarketEventStatus.FAILED)
                + repository.countByProcessingStatus(MarketEventStatus.REJECTED);
    }

    public long marketOriginOutboxEvents() {
        return outbox.events(500).stream()
                .filter(event -> SOURCE_MARKET.equals(event.source()))
                .count();
    }

    private MarketInboundEventRequest toHeader(MarketEventRequest request) {
        String eventId = textOrDefault(request == null ? null : request.eventId(), "EVT-" + UUID.randomUUID());
        String inboundCorrelationId = request == null ? null : request.correlationId();
        String correlationId = inboundCorrelationId;
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "LEGACY-NEXUS-CORR-" + eventId;
            log.warn("Market event {} has no correlationId; using legacy fallback {}", eventId, correlationId);
        }
        return new MarketInboundEventRequest(
                eventId,
                textOrDefault(request == null ? null : request.idempotencyKey(), "MK-" + UUID.randomUUID()),
                textOrDefault(request == null ? null : request.source(), SOURCE_MARKET),
                request == null || request.eventType() == null ? MarketEventType.UNKNOWN : request.eventType(),
                request == null || request.schemaVersion() == null ? 1 : request.schemaVersion(),
                request == null || request.occurredAt() == null ? Instant.now() : request.occurredAt(),
                request == null ? null : request.simulationRunId(),
                request == null ? null : request.settlementCycleId(),
                correlationId,
                request == null ? null : request.causationId(),
                request == null || request.hopCount() == null ? 0 : request.hopCount(),
                request == null || request.maxHop() == null ? DEFAULT_MAX_HOP : request.maxHop()
        );
    }

    private List<OutboxEventResponse> emitOutboxEvents(MarketInboundEventRequest header, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        return switch (header.eventType()) {
            case PRODUCTION_REQUESTED -> {
                WorkforceService.ProductionCapacityDecision decision = workforce.processProductionRequest(safePayload);
                Map<String, Object> mappedPayload = new LinkedHashMap<>(safePayload);
                mappedPayload.putAll(decision.workforcePayload());
                // The Market request is the direct cause of every manufacturing child event.
                // Keep the inbound correlation unchanged while exposing a stable request/batch entity id.
                mappedPayload.put("entityId", header.eventId());
                mappedPayload.put("productionRequestId", header.eventId());
                mappedPayload.put("causationId", header.eventId());
                yield manufacturingOutbox(header, mappedPayload, decision);
            }
            case SHIPMENT_REQUESTED -> shipmentOutbox(header, safePayload);
            case ORDER_CANCELLED -> List.of(
                    emit(EventType.SHIPMENT_HOLD_CREATED,
                            header,
                            payload,
                            eventAggregateId(header, payload, "orderId"),
                            "MarketShipmentHold"
                    )
            );
            case RETURN_REQUESTED -> List.of(
                    emit(EventType.QUALITY_DEFECT_DETECTED,
                            header,
                            payload,
                            eventAggregateId(header, payload, "returnId"),
                            "MarketReturn"
                    )
            );
            case QUALITY_CLAIM_CREATED -> List.of(
                    emit(EventType.QUALITY_CLAIM_CHARGED,
                            header,
                            payload,
                            eventAggregateId(header, payload, "claimId"),
                            "MarketQualityClaim"
                    )
            );
            case MARKET_ORDER_PLACED -> List.of();
            case UNKNOWN -> throw new IllegalArgumentException("Unknown market eventType: " + header.eventType());
        };
    }

    /**
     * Converts one accepted Market production request into a bounded manufacturing chain.
     * A shipment is created only after the requested quantity was materially consumed and
     * inspected; a capacity shortage remains a local delayed/backlog signal instead.
     */
    private List<OutboxEventResponse> manufacturingOutbox(MarketInboundEventRequest header,
                                                           Map<String, Object> payload,
                                                           WorkforceService.ProductionCapacityDecision decision) {
        List<OutboxEventResponse> events = new ArrayList<>();
        String orderId = decision.aggregateId();
        if (decision.materialConsumed() > 0) {
            events.add(emit(EventType.MATERIAL_CONSUMED, header, payload, orderId + ":material", "InventoryTransaction"));
        }
        if (decision.qualityInspected() > 0) {
            events.add(emit(EventType.QUALITY_INSPECTION_COMPLETED, header, payload, orderId + ":quality", "QualityInspection"));
        }
        if (decision.maintenanceRequired()) {
            events.add(emit(decision.maintenanceBlocked() ? EventType.MAINTENANCE_REQUIRED : EventType.MAINTENANCE_COMPLETED,
                    header, payload, orderId + ":maintenance", "MaintenanceEvent"));
        }
        if (decision.qualityDefects() > 0) {
            events.add(emit(EventType.QUALITY_DEFECT_DETECTED, header, payload, orderId + ":quality-defect", "QualityInspection"));
        }
        if (decision.completedQuantity() > 0 && !outbox.hasEventForAggregate(EventType.PRODUCTION_COMPLETED, orderId)) {
            events.add(emit(EventType.PRODUCTION_COMPLETED, header, payload, orderId, "ProductionOrder"));
        }
        if (decision.backlogQuantity() > 0) {
            events.add(emit(EventType.PRODUCTION_DELAYED, header, payload, orderId, "ProductionOrder"));
            events.add(emit(EventType.BACKLOG_INCREASED, header, payload, orderId, "ProductionOrder"));
        }
        // Dispatch is a terminal order-level action. A later production batch must not create a second shipment.
        if (decision.dispatchAllowed() && parseBoolean(payload.get("requiresShipment"), true)
                && !outbox.hasEventForAggregate(EventType.LOGISTICS_DISPATCHED, orderId)) {
            events.add(emit(EventType.LOGISTICS_DISPATCHED, header, payload,
                    orderId, "MarketShipment"));
        }
        return events;
    }

    private List<OutboxEventResponse> shipmentOutbox(MarketInboundEventRequest header, Map<String, Object> payload) {
        boolean requiresShipment = parseBoolean(payload.get("requiresShipment"), true);
        String orderId = eventAggregateId(header, payload, "orderId");
        if (!requiresShipment) {
            return List.of(emit(
                    EventType.SHIPMENT_HOLD_CREATED,
                    header,
                    payload,
                    eventAggregateId(header, payload, "shipmentId"),
                    "MarketShipment"
            ));
        }
        // SHIPMENT_REQUESTED is intent, not proof that the production/quality gate completed.
        // Keep it local until PRODUCTION_COMPLETED emits the one canonical dispatch for the order.
        if (!outbox.hasEventForAggregate(EventType.PRODUCTION_COMPLETED, orderId)) {
            return List.of(emit(
                    EventType.SHIPMENT_HOLD_CREATED,
                    header,
                    payload,
                    orderId,
                    "MarketShipmentHold"
            ));
        }
        if (outbox.hasEventForAggregate(EventType.LOGISTICS_DISPATCHED, orderId)) {
            return List.of();
        }
        return List.of(emit(
                EventType.LOGISTICS_DISPATCHED,
                header,
                payload,
                orderId,
                "MarketShipment"
        ));
    }

    private OutboxEventResponse emit(EventType eventType,
                                     MarketInboundEventRequest header,
                                     Map<String, Object> payload,
                                     String aggregateId,
                                     String aggregateType) {
        try {
            String idempotency = buildOutboxIdempotency(eventType, header, aggregateId);
            Map<String, Object> mapped = withMarketMetadata(eventType, header, payload);
            return outbox.emit(eventType, aggregateType, aggregateId, idempotency, mapped,
                    header.occurredAt(), SOURCE_MARKET).orElseThrow();
        } catch (RuntimeException duplicate) {
            throw duplicate;
        }
    }

    private MarketEventResponse response(MarketInboundEventEntity event,
                                        List<OutboxEventResponse> outboxResponses,
                                        Map<String, Object> rawPayload,
                                        boolean duplicateRequested) {
        MarketEventStatus status = duplicateRequested ? MarketEventStatus.DUPLICATE : event.processingStatus();
        boolean duplicate = duplicateRequested || status == MarketEventStatus.DUPLICATE;
        if (rawPayload == null) {
            rawPayload = Map.of();
        }
        List<String> outboxIds = outboxResponses == null || outboxResponses.isEmpty()
                ? extractOutboxIds(event.outboxEventIds())
                : outboxResponses.stream().map(OutboxEventResponse::eventId).toList();
        return new MarketEventResponse(
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                status,
                duplicate,
                status == MarketEventStatus.PROCESSED ? outboxIds.size() : 0,
                outboxIds,
                event.reason() == null ? "" : event.reason(),
                event.correlationId(),
                event.causationId()
        );
    }

    private String buildOutboxIdempotency(EventType eventType, MarketInboundEventRequest header, String aggregateId) {
        String safeAggregateId = aggregateId == null || aggregateId.isBlank()
                ? header.eventId()
                : aggregateId;
        return "market:" + header.source() + ":" + header.eventId() + ":" + eventType + ":" + safeAggregateId;
    }

    private Map<String, Object> preserveMetadata(MarketInboundEventRequest header, Map<String, Object> payload) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("source", header.source());
        merged.put("eventId", header.eventId());
        merged.put("idempotencyKey", header.idempotencyKey());
        merged.put("eventType", header.eventType().name());
        merged.put("schemaVersion", header.schemaVersion());
        merged.put("occurredAt", header.occurredAt());
        merged.put("simulationRunId", header.simulationRunId());
        merged.put("settlementCycleId", header.settlementCycleId());
        merged.put("correlationId", header.correlationId());
        merged.put("causationId", payload != null && payload.get("causationId") != null
                ? payload.get("causationId")
                : header.causationId());
        merged.put("hopCount", header.hopCount());
        merged.put("maxHop", header.maxHop());

        if (payload != null) {
            merged.put("shipmentId", payload.get("shipmentId"));
            merged.put("factoryId", payload.get("factoryId"));
            merged.put("originCode", payload.get("originCode"));
            merged.put("destinationCode", payload.get("destinationCode"));
            merged.put("itemType", payload.get("itemType"));
            merged.put("requiresColdChain", payload.get("requiresColdChain"));
            merged.put("orderId", payload.get("orderId"));
            merged.put("customerId", payload.get("customerId"));
            merged.put("customerType", payload.get("customerType"));
            merged.put("riskLevel", payload.get("riskLevel"));
            merged.put("productType", payload.get("productType"));
            merged.put("quantity", payload.get("quantity"));
            merged.put("totalAmount", payload.get("totalAmount") == null ? payload.get("orderAmount") : payload.get("totalAmount"));
            merged.put("orderAmount", payload.get("orderAmount"));
            merged.put("priority", payload.get("priority"));
            merged.put("requiresShipment", payload.get("requiresShipment"));
            merged.put("returnId", payload.get("returnId"));
            merged.put("claimId", payload.get("claimId"));
            merged.put("workdayId", payload.get("workdayId"));
            merged.put("entityId", payload.get("entityId"));
            merged.put("productionRequestId", payload.get("productionRequestId"));
            merged.put("workforceAllocationId", payload.get("workforceAllocationId"));
            merged.put("productivityScore", payload.get("productivityScore"));
            merged.put("usedCapacity", payload.get("usedCapacity"));
            merged.put("remainingCapacity", payload.get("remainingCapacity"));
            merged.put("backlogCount", payload.get("backlogCount"));
            merged.put("bottleneckRole", payload.get("bottleneckRole"));
            merged.put("productionRequested", payload.get("productionRequested"));
            merged.put("productionCompleted", payload.get("productionCompleted"));
            merged.put("payrollCost", payload.get("payrollCost"));
            merged.put("qualityRiskIncreased", payload.get("qualityRiskIncreased"));
            merged.put("maintenanceRiskIncreased", payload.get("maintenanceRiskIncreased"));
            merged.put("maintenanceRequired", payload.get("maintenanceRequired"));
        }

        merged.put("marketPayload", payload == null ? Map.of() : payload);
        return merged;
    }

    private Map<String, Object> withMarketMetadata(EventType eventType, MarketInboundEventRequest header, Map<String, Object> payload) {
        Map<String, Object> mapped = new LinkedHashMap<>(preserveMetadata(header, payload));
        if (eventType == EventType.LOGISTICS_DISPATCHED) {
            String factoryId = textOrDefault(asText(mapped.get("factoryId")), "FAC-A");
            mapped.put("factoryId", factoryId);
            mapped.put("shipmentId", textOrDefault(asText(mapped.get("shipmentId")), header.eventId() + "-shipment"));
            mapped.put("originCode", textOrDefault(asText(mapped.get("originCode")), factoryId));
            mapped.put("destinationCode", textOrDefault(asText(mapped.get("destinationCode")), "DC-SEOUL-01"));
            mapped.put("priority", textOrDefault(asText(mapped.get("priority")), "NORMAL"));
            mapped.put("itemType", textOrDefault(asText(mapped.get("itemType")), textOrDefault(asText(mapped.get("productType")), "synthetic-component")));
            mapped.put("requiresColdChain", parseBoolean(mapped.get("requiresColdChain"), false));
            mapped.put("riskLevel", logisticsRiskLevel(mapped.get("riskLevel")));
            mapped.put("sourceSystem", "Archive-Nexus");
            mapped.put("targetSystem", "Archive-Logistics");
        }
        if (eventType == EventType.MATERIAL_CONSUMED || eventType == EventType.PRODUCTION_COMPLETED
                || eventType == EventType.MAINTENANCE_COMPLETED || eventType == EventType.QUALITY_DEFECT_DETECTED
                || eventType == EventType.QUALITY_CLAIM_CHARGED) {
            long quantity = mapped.get("quantity") instanceof Number number ? Math.max(1L, number.longValue()) : 1L;
            Object amount = mapped.get("amount");
            if (!(amount instanceof Number)) {
                Object orderAmount = mapped.get("orderAmount");
                mapped.put("amount", orderAmount instanceof Number number ? number.longValue() : quantity * 120_000L);
            }
            mapped.putIfAbsent("currency", "KRW");
            mapped.putIfAbsent("factoryId", "FAC-A");
            mapped.put("sourceSystem", "archive-nexus");
            mapped.put("targetSystem", "archive-ledger");
        }
        mapped.put("eventMeaning", "mapped from " + header.eventType() + " -> " + eventType);
        mapped.put("requiresApproval", eventType == EventType.QUALITY_CLAIM_CHARGED);
        return mapped;
    }

    private int logisticsRiskLevel(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null) {
            return 0;
        }
        return switch (value.toString().trim().toUpperCase(java.util.Locale.ROOT)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 4;
            case "CRITICAL" -> 5;
            default -> 0;
        };
    }

    private Map<String, Object> read(Map<String, Object> payload) {
        try {
            return mapper.convertValue(payload, MAP_TYPE);
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private String write(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(read(payload));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String eventAggregateId(MarketInboundEventRequest header, Map<String, Object> payload, String key) {
        if (payload != null && payload.get(key) != null && !payload.get(key).toString().isBlank()) {
            return payload.get(key).toString();
        }
        return header.eventId() + "-" + header.eventType().name().toLowerCase();
    }

    private static String textOrDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static boolean parseBoolean(Object rawValue, boolean fallback) {
        if (rawValue == null) {
            return fallback;
        }
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        String value = rawValue.toString().trim().toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("y");
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private List<String> extractOutboxIds(String outboxIdsJson) {
        String normalized = outboxIdsJson == null ? "[]" : outboxIdsJson.trim();
        if (normalized.length() < 2 || !normalized.startsWith("[") || !normalized.endsWith("]")) {
            return List.of();
        }
        String body = normalized.substring(1, normalized.length() - 1).trim();
        if (body.isBlank()) {
            return List.of();
        }
        return List.of(body.split(","))
                .stream()
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .map(item -> item.replace("\"", ""))
                .toList();
    }

    private String extractedOutboxIds(List<OutboxEventResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "[]";
        }
        return responses.stream()
                .map(OutboxEventResponse::eventId)
                .toList()
                .toString();
    }
}
