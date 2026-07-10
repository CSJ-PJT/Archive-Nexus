package com.archivenexus.backend.market;

import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.market.MarketEventModels.MarketEventType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "nexus_market_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "nexus_market_event_event_id_key", columnNames = "event_id"),
                @UniqueConstraint(name = "nexus_market_event_idempotency_key_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "nexus_market_event_status_idx", columnList = "processing_status, created_at"),
                @Index(name = "nexus_market_event_type_idx", columnList = "event_type, occurred_at"),
                @Index(name = "nexus_market_event_source_idx", columnList = "source, created_at")
        })
public class MarketInboundEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(nullable = false, length = 120)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private MarketEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 40)
    private MarketEventStatus processingStatus = MarketEventStatus.RECEIVED;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "simulation_run_id", length = 120)
    private String simulationRunId;

    @Column(name = "settlement_cycle_id", length = 120)
    private String settlementCycleId;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "causation_id", length = 120)
    private String causationId;

    @Column(name = "hop_count", nullable = false)
    private int hopCount;

    @Column(name = "max_hop", nullable = false)
    private int maxHop;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson = "{}";

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "outbox_event_count", nullable = false)
    private int outboxEventCount;

    @Column(name = "outbox_event_ids", columnDefinition = "text")
    private String outboxEventIds = "[]";

    protected MarketInboundEventEntity() {}

    public MarketInboundEventEntity(String eventId, String idempotencyKey, String source,
                                   MarketEventType eventType, int schemaVersion,
                                   Instant occurredAt, Instant receivedAt,
                                   String simulationRunId, String settlementCycleId,
                                   String correlationId, String causationId,
                                   int hopCount, int maxHop, String payloadJson) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.source = source == null || source.isBlank() ? "Archive-Market" : source;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.simulationRunId = simulationRunId;
        this.settlementCycleId = settlementCycleId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.hopCount = hopCount;
        this.maxHop = maxHop;
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
    }

    public void markProcessed(int outboxCount, String outboxIds) {
        this.processingStatus = MarketEventStatus.PROCESSED;
        this.outboxEventCount = outboxCount;
        this.outboxEventIds = outboxIds == null ? "[]" : outboxIds;
    }

    public void markDuplicate(String reason) {
        this.processingStatus = MarketEventStatus.DUPLICATE;
        this.reason = reason;
    }

    public void markRejected(String reason) {
        this.processingStatus = MarketEventStatus.REJECTED;
        this.reason = reason;
    }

    public void markFailed(String reason) {
        this.processingStatus = MarketEventStatus.FAILED;
        this.reason = reason;
    }

    public Long id() { return id; }
    public String eventId() { return eventId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String source() { return source; }
    public MarketEventType eventType() { return eventType; }
    public MarketEventStatus processingStatus() { return processingStatus; }
    public int schemaVersion() { return schemaVersion; }
    public Instant occurredAt() { return occurredAt; }
    public Instant receivedAt() { return receivedAt; }
    public String simulationRunId() { return simulationRunId; }
    public String settlementCycleId() { return settlementCycleId; }
    public String correlationId() { return correlationId; }
    public String causationId() { return causationId; }
    public int hopCount() { return hopCount; }
    public int maxHop() { return maxHop; }
    public String payloadJson() { return payloadJson; }
    public String reason() { return reason; }
    public int outboxEventCount() { return outboxEventCount; }
    public String outboxEventIds() { return outboxEventIds; }
}
