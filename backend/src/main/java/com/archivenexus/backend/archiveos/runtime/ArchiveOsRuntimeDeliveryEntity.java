package com.archivenexus.backend.archiveos.runtime;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "archiveos_runtime_delivery", indexes = {
        @Index(name = "archiveos_runtime_delivery_status_idx", columnList = "status,next_retry_at,created_at"),
        @Index(name = "archiveos_runtime_delivery_correlation_idx", columnList = "correlation_id,created_at")
})
public class ArchiveOsRuntimeDeliveryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, unique = true, length = 120) private String eventId;
    @Column(name = "idempotency_key", nullable = false, length = 180) private String idempotencyKey;
    @Column(name = "correlation_id", length = 160) private String correlationId;
    @Column(name = "causation_id", length = 160) private String causationId;
    @Column(name = "order_id", length = 160) private String orderId;
    @Column(name = "simulation_run_id", length = 160) private String simulationRunId;
    @Column(name = "entity_id", nullable = false, length = 160) private String entityId;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") private String payloadJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ArchiveOsRuntimeDeliveryStatus status = ArchiveOsRuntimeDeliveryStatus.PENDING;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "next_retry_at") private Instant nextRetryAt;
    @Column(name = "publishing_started_at") private Instant publishingStartedAt;
    @Column(name = "last_error_code", length = 80) private String lastErrorCode;
    @Column(name = "last_error_message", columnDefinition = "text") private String lastErrorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "published_at") private Instant publishedAt;
    protected ArchiveOsRuntimeDeliveryEntity() {}
    public ArchiveOsRuntimeDeliveryEntity(String eventId, String idempotencyKey, String correlationId, String causationId, String orderId, String simulationRunId, String entityId, String eventType, String payloadJson, Instant now) {
        this.eventId=eventId; this.idempotencyKey=idempotencyKey; this.correlationId=correlationId; this.causationId=causationId; this.orderId=orderId; this.simulationRunId=simulationRunId; this.entityId=entityId; this.eventType=eventType; this.payloadJson=payloadJson; this.createdAt=now; this.updatedAt=now;
    }
    public void publishing(Instant now){ status=ArchiveOsRuntimeDeliveryStatus.PUBLISHING; publishingStartedAt=now; updatedAt=now; }
    public void published(Instant now){ status=ArchiveOsRuntimeDeliveryStatus.PUBLISHED; publishedAt=now; nextRetryAt=null; lastErrorCode=null; lastErrorMessage=null; updatedAt=now; }
    public void retry(String code,String message,Instant next,Instant now){ retryCount++; status=ArchiveOsRuntimeDeliveryStatus.RETRY_WAIT; nextRetryAt=next; publishingStartedAt=null; lastErrorCode=code; lastErrorMessage=message; updatedAt=now; }
    public void terminal(ArchiveOsRuntimeDeliveryStatus terminal,String code,String message,Instant now){ status=terminal; publishingStartedAt=null; nextRetryAt=null; lastErrorCode=code; lastErrorMessage=message; updatedAt=now; }
    public void recoverStale(Instant now){ if(status==ArchiveOsRuntimeDeliveryStatus.PUBLISHING){status=ArchiveOsRuntimeDeliveryStatus.RETRY_WAIT; nextRetryAt=now; publishingStartedAt=null; lastErrorCode="STALE_PUBLISHING"; lastErrorMessage="Recovered stale in-flight runtime delivery"; updatedAt=now;} }
    public Long id(){return id;} public String eventId(){return eventId;} public String idempotencyKey(){return idempotencyKey;} public String correlationId(){return correlationId;} public String causationId(){return causationId;} public String orderId(){return orderId;} public String simulationRunId(){return simulationRunId;} public String entityId(){return entityId;} public String eventType(){return eventType;} public String payloadJson(){return payloadJson;} public ArchiveOsRuntimeDeliveryStatus status(){return status;} public int retryCount(){return retryCount;} public Instant nextRetryAt(){return nextRetryAt;} public Instant publishingStartedAt(){return publishingStartedAt;} public String lastErrorCode(){return lastErrorCode;} public String lastErrorMessage(){return lastErrorMessage;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public Instant publishedAt(){return publishedAt;}
}
