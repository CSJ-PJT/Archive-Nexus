package com.archivenexus.backend.market;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MarketEventModels {
    private MarketEventModels() {}

    public enum MarketEventType {
        MARKET_ORDER_PLACED,
        PRODUCTION_REQUESTED,
        SHIPMENT_REQUESTED,
        ORDER_CANCELLED,
        RETURN_REQUESTED,
        QUALITY_CLAIM_CREATED,
        UNKNOWN
    }

    public enum MarketEventStatus {
        RECEIVED,
        PROCESSED,
        DUPLICATE,
        REJECTED,
        FAILED
    }

    public record MarketInboundEventRequest(
            String eventId,
            String idempotencyKey,
            String source,
            MarketEventType eventType,
            Integer schemaVersion,
            Instant occurredAt,
            String simulationRunId,
            String settlementCycleId,
            String correlationId,
            String causationId,
            Integer hopCount,
            Integer maxHop
    ) {}

    public record MarketEventPayload(
            String orderId,
            String customerId,
            String customerType,
            String riskLevel,
            String productType,
            Integer quantity,
            Long totalAmount,
            Long orderAmount,
            String priority,
            Boolean requiresShipment,
            String returnId,
            String claimId,
            Map<String, Object> extra
    ) {}

    public record MarketEventBody(
            MarketInboundEventRequest header,
            MarketEventPayload payload
    ) {}

    public record MarketEventRequest(
            String eventId,
            String idempotencyKey,
            String source,
            MarketEventType eventType,
            Integer schemaVersion,
            Instant occurredAt,
            String simulationRunId,
            String settlementCycleId,
            String correlationId,
            String causationId,
            Integer hopCount,
            Integer maxHop,
            Map<String, Object> payload
    ) {}

    public record MarketBulkEventRequest(
            List<MarketEventRequest> events
    ) {}

    public record MarketEventResponse(
            String eventId,
            String idempotencyKey,
            MarketEventType eventType,
            MarketEventStatus status,
            boolean duplicate,
            int outboxEventsGenerated,
            List<String> emittedOutboxEventIds,
            String reason,
            String correlationId,
            String causationId
    ) {}

    public record MarketBulkEventResponse(
            int requested,
            int received,
            int duplicates,
            int rejected,
            int failed,
            List<MarketEventResponse> results
    ) {}
}
