package com.archivenexus.backend.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OutboxModels {
    private OutboxModels() {
    }

    public enum EventType {
        PRODUCTION_COMPLETED,
        MATERIAL_CONSUMED,
        LOGISTICS_DISPATCHED,
        MAINTENANCE_COMPLETED,
        QUALITY_DEFECT_DETECTED,
        EMERGENCY_PURCHASE_REQUESTED,
        QUALITY_CLAIM_CHARGED,
        SHIPMENT_HOLD_CREATED,
        CORPORATE_CARD_USED,
        VENDOR_PAYMENT_REQUESTED
    }

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        PENDING_RETRY,
        FAILED
    }

    public record OutboxEventResponse(
            long id,
            String eventId,
            String idempotencyKey,
            EventType eventType,
            String aggregateType,
            String aggregateId,
            String source,
            int schemaVersion,
            Map<String, Object> payload,
            OutboxStatus status,
            int retryCount,
            String lastError,
            Instant occurredAt,
            Instant createdAt,
            Instant publishedAt
    ) {
    }

    public record OutboxSummary(
            long total,
            long pending,
            long published,
            long pendingRetry,
            long failed,
            Map<String, Long> byType
    ) {
    }

    public record GenerateResult(int requested, int generated, List<String> sampleEventIds) {
    }

    public record PublishResult(int attempted, int published, int failed) {
    }
}
