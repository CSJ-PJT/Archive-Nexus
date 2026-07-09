package com.archivenexus.backend.logisticssettlement;

import com.archivenexus.backend.logisticssettlement.LogisticsSettlementModels.SettlementProcessingStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nexus_logistics_daily_settlement",
        uniqueConstraints = {
                @UniqueConstraint(name = "nexus_logistics_daily_settlement_settlement_id_key", columnNames = "settlement_id"),
                @UniqueConstraint(name = "nexus_logistics_daily_settlement_idempotency_key_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "nexus_logistics_daily_settlement_date_idx", columnList = "settlement_date, factory_id"),
                @Index(name = "nexus_logistics_daily_settlement_status_idx", columnList = "processing_status, received_at"),
                @Index(name = "nexus_logistics_daily_settlement_factory_idx", columnList = "factory_id, settlement_date")
        })
public class LogisticsDailySettlementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "settlement_id", nullable = false, length = 100) private String settlementId;
    @Column(name = "idempotency_key", nullable = false, length = 180) private String idempotencyKey;
    @Column(nullable = false, length = 80) private String source = "Archive-Logistics";
    @Column(name = "schema_version", nullable = false) private int schemaVersion = 1;
    @Column(name = "settlement_date", nullable = false) private LocalDate settlementDate;
    @Column(name = "factory_id", nullable = false, length = 80) private String factoryId;
    @Enumerated(EnumType.STRING) @Column(name = "processing_status", nullable = false, length = 40) private SettlementProcessingStatus processingStatus = SettlementProcessingStatus.RECEIVED;
    @Column(nullable = false, length = 12) private String currency = "KRW";
    @Column(name = "total_shipments", nullable = false) private int totalShipments;
    @Column(name = "delayed_shipments", nullable = false) private int delayedShipments;
    @Column(name = "held_shipments", nullable = false) private int heldShipments;
    @Column(name = "total_quantity", nullable = false) private int totalQuantity;
    @Column(name = "total_logistics_cost", nullable = false, precision = 18, scale = 2) private BigDecimal totalLogisticsCost = BigDecimal.ZERO;
    @Column(name = "manufacturing_impact_cost", nullable = false, precision = 18, scale = 2) private BigDecimal manufacturingImpactCost = BigDecimal.ZERO;
    @Column(name = "on_time_rate", nullable = false, precision = 8, scale = 4) private BigDecimal onTimeRate = BigDecimal.ZERO;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "text") private String evidenceJson = "{}";
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") private String payloadJson = "{}";
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "last_duplicate_received_at") private Instant lastDuplicateReceivedAt;
    @Column(name = "duplicate_count", nullable = false) private int duplicateCount;

    protected LogisticsDailySettlementEntity() {}

    public LogisticsDailySettlementEntity(String settlementId, String idempotencyKey, String source, int schemaVersion,
                                          LocalDate settlementDate, String factoryId, String currency, int totalShipments,
                                          int delayedShipments, int heldShipments, int totalQuantity,
                                          BigDecimal totalLogisticsCost, BigDecimal manufacturingImpactCost,
                                          BigDecimal onTimeRate, String evidenceJson, String payloadJson,
                                          Instant occurredAt, Instant receivedAt) {
        this.settlementId = settlementId; this.idempotencyKey = idempotencyKey; this.source = source;
        this.schemaVersion = schemaVersion; this.settlementDate = settlementDate; this.factoryId = factoryId;
        this.currency = currency; this.totalShipments = totalShipments; this.delayedShipments = delayedShipments;
        this.heldShipments = heldShipments; this.totalQuantity = totalQuantity;
        this.totalLogisticsCost = totalLogisticsCost; this.manufacturingImpactCost = manufacturingImpactCost;
        this.onTimeRate = onTimeRate; this.evidenceJson = evidenceJson; this.payloadJson = payloadJson;
        this.occurredAt = occurredAt; this.receivedAt = receivedAt; this.processedAt = receivedAt;
    }

    public void recordDuplicate(Instant now) { this.duplicateCount++; this.lastDuplicateReceivedAt = now; }
    public Long id() { return id; }
    public String settlementId() { return settlementId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String source() { return source; }
    public int schemaVersion() { return schemaVersion; }
    public LocalDate settlementDate() { return settlementDate; }
    public String factoryId() { return factoryId; }
    public SettlementProcessingStatus processingStatus() { return processingStatus; }
    public String currency() { return currency; }
    public int totalShipments() { return totalShipments; }
    public int delayedShipments() { return delayedShipments; }
    public int heldShipments() { return heldShipments; }
    public int totalQuantity() { return totalQuantity; }
    public BigDecimal totalLogisticsCost() { return totalLogisticsCost; }
    public BigDecimal manufacturingImpactCost() { return manufacturingImpactCost; }
    public BigDecimal onTimeRate() { return onTimeRate; }
    public String evidenceJson() { return evidenceJson; }
    public String payloadJson() { return payloadJson; }
    public Instant occurredAt() { return occurredAt; }
    public Instant receivedAt() { return receivedAt; }
    public Instant processedAt() { return processedAt; }
    public int duplicateCount() { return duplicateCount; }
}
