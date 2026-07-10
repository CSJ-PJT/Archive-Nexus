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
        URGENT_DELIVERY_REQUESTED,
        SHIPMENT_HOLD_RELEASED,
        MATERIAL_TRANSFER_REQUESTED,
        QUALITY_REPLACEMENT_SHIPMENT,
        MAINTENANCE_COMPLETED,
        QUALITY_DEFECT_DETECTED,
        EMERGENCY_PURCHASE_REQUESTED,
        QUALITY_CLAIM_CHARGED,
        SHIPMENT_HOLD_CREATED,
        CORPORATE_CARD_USED,
        VENDOR_PAYMENT_REQUESTED,
        UNKNOWN
    }

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        PENDING_RETRY,
        FAILED,
        SKIPPED
    }

    public enum OutboxTargetService {
        LOGITICS,
        LEDGER,
        NONE,
        UNKNOWN
    }

    public enum RoutingStatus {
        ROUTED,
        ROUTE_SKIPPED,
        ROUTE_FAILED,
        DRY_RUN,
        LEGACY
    }

    public enum PublishTarget {
        AUTO,
        LOGITICS,
        LEDGER
    }

    public enum GenerateType {
        MIXED,
        LOGISTICS,
        LEDGER,
        APPROVAL_RISK
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
            OutboxTargetService targetService,
            String targetUrl,
            RoutingStatus routingStatus,
            OutboxTargetService lastPublishTarget,
            Instant lastPublishAttemptAt,
            String publishSkippedReason,
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
            Map<String, Long> byType,
            Map<String, Long> status,
            Map<String, TargetSummary> targets,
            Map<String, IntegrationState> integrations
    ) {
    }

    public record TargetSummary(long candidates, long pending, long published, long skipped, long failed, String lastError) {
    }

    public record IntegrationState(boolean enabled, String baseUrl, String status) {
    }

    public record GenerateResult(int requested, int generated, GenerateType type, Map<String, Long> targets, List<String> sampleEventIds) {
    }

    public record TargetPublishSummary(int candidates, int published, int skipped, int failed) {
    }

    public record PublishResult(
            String traceId,
            PublishTarget target,
            boolean dryRun,
            int totalCandidates,
            int attempted,
            int published,
            int skipped,
            int failed,
            Map<String, TargetPublishSummary> targets
    ) {
    }

    public record OutboxTarget(OutboxTargetService service, RoutingStatus routingStatus, String reason) {
    }

    public record IntegrationSummary(
            String traceId,
            String service,
            String status,
            Map<String, IntegrationState> integrations,
            RoutingConfig routing,
            boolean marketInboundEnabled,
            long marketEventsReceived,
            long marketEventsProcessed,
            long marketEventsFailed,
            long marketOriginOutboxEvents
    ) {
    }

    public record RoutingConfig(String mode, boolean allowLedgerDirectFallbackForLogistics) {
    }
}
