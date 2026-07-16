package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxModels.RoutingStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "nexus_outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "nexus_outbox_event_event_id_key", columnNames = "event_id"),
                @UniqueConstraint(name = "nexus_outbox_event_idempotency_key_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "nexus_outbox_event_status_idx", columnList = "status, created_at"),
                @Index(name = "nexus_outbox_event_type_idx", columnList = "event_type, occurred_at"),
                @Index(name = "nexus_outbox_event_target_idx", columnList = "target_service, status, created_at")
        })
public class OutboxEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 80)
    private String eventId;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private EventType eventType;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(nullable = false, length = 80)
    private String source = "Archive-Nexus";

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_service", length = 50)
    private OutboxTargetService targetService;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_status", length = 50)
    private RoutingStatus routingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_publish_target", length = 50)
    private OutboxTargetService lastPublishTarget;

    @Column(name = "last_publish_attempt_at")
    private Instant lastPublishAttemptAt;

    @Column(name = "publish_skipped_reason", columnDefinition = "text")
    private String publishSkippedReason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(String eventId, String idempotencyKey, EventType eventType, String aggregateType,
                             String aggregateId, String payload, Instant occurredAt, Instant createdAt) {
        this(eventId, idempotencyKey, "Archive-Nexus", eventType, aggregateType, aggregateId, payload, occurredAt, createdAt);
    }

    public OutboxEventEntity(String eventId, String idempotencyKey, String source, EventType eventType, String aggregateType,
                             String aggregateId, String payload, Instant occurredAt, Instant createdAt) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.source = source == null || source.isBlank() ? "Archive-Nexus" : source;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
        this.lastError = null;
        this.publishSkippedReason = null;
        this.routingStatus = RoutingStatus.ROUTED;
    }

    public void markPublishing(OutboxTargetService target, String url, Instant now) {
        recordPublishAttempt(target, url, now);
        this.status = OutboxStatus.PUBLISHING;
    }

    public void markFailure(String message, int maxRetryCount) {
        this.retryCount += 1;
        this.lastError = message;
        this.routingStatus = RoutingStatus.ROUTE_FAILED;
        this.status = retryCount >= Math.max(1, maxRetryCount) ? OutboxStatus.FAILED : OutboxStatus.PENDING_RETRY;
    }

    /** Authentication/authorization failures are configuration errors, not retryable transport failures. */
    public void markTerminalFailure(String message) {
        this.retryCount += 1;
        this.lastError = message;
        this.routingStatus = RoutingStatus.ROUTE_FAILED;
        this.status = OutboxStatus.FAILED;
    }

    public void markSkipped(String reason, RoutingStatus routingStatus, Instant now) {
        this.status = OutboxStatus.SKIPPED;
        this.publishSkippedReason = reason;
        this.routingStatus = routingStatus;
        this.lastPublishAttemptAt = now;
        this.lastError = null;
    }

    public void recordDryRun(String reason, Instant now) {
        this.publishSkippedReason = reason;
        this.routingStatus = RoutingStatus.DRY_RUN;
        this.lastPublishAttemptAt = now;
        this.lastError = null;
    }

    public void route(OutboxTargetService targetService, String targetUrl, RoutingStatus routingStatus, String skippedReason) {
        this.targetService = targetService;
        this.targetUrl = targetUrl;
        this.routingStatus = routingStatus;
        this.publishSkippedReason = skippedReason;
    }

    public void recordPublishAttempt(OutboxTargetService target, String targetUrl, Instant now) {
        this.lastPublishTarget = target;
        this.targetService = target;
        this.targetUrl = targetUrl;
        this.lastPublishAttemptAt = now;
    }

    public Long id() { return id; }
    public String eventId() { return eventId; }
    public String idempotencyKey() { return idempotencyKey; }
    public EventType eventType() { return eventType; }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public String source() { return source; }
    public int schemaVersion() { return schemaVersion; }
    public String payload() { return payload; }
    public OutboxStatus status() { return status; }
    public int retryCount() { return retryCount; }
    public String lastError() { return lastError; }
    public OutboxTargetService targetService() { return targetService; }
    public String targetUrl() { return targetUrl; }
    public RoutingStatus routingStatus() { return routingStatus; }
    public OutboxTargetService lastPublishTarget() { return lastPublishTarget; }
    public Instant lastPublishAttemptAt() { return lastPublishAttemptAt; }
    public String publishSkippedReason() { return publishSkippedReason; }
    public Instant occurredAt() { return occurredAt; }
    public Instant createdAt() { return createdAt; }
    public Instant publishedAt() { return publishedAt; }
}
