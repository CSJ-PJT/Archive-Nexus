package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
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
                @Index(name = "nexus_outbox_event_type_idx", columnList = "event_type, occurred_at")
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
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
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
    }

    public void markFailure(String message) {
        this.retryCount += 1;
        this.lastError = message;
        this.status = retryCount >= 5 ? OutboxStatus.FAILED : OutboxStatus.PENDING_RETRY;
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
    public Instant occurredAt() { return occurredAt; }
    public Instant createdAt() { return createdAt; }
    public Instant publishedAt() { return publishedAt; }
}
