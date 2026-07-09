package com.archivenexus.backend.nexuseconomy;

import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.RevenueType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "nexus_revenue_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "nexus_revenue_event_event_id_key", columnNames = "event_id"),
                @UniqueConstraint(name = "nexus_revenue_event_idempotency_key_key", columnNames = "idempotency_key")
        },
        indexes = @Index(name = "nexus_revenue_event_type_created_idx", columnList = "revenue_type, created_at"))
public class NexusRevenueEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, length = 100) private String eventId;
    @Column(name = "idempotency_key", nullable = false, length = 180) private String idempotencyKey;
    @Column(name = "simulation_run_id", length = 100) private String simulationRunId;
    @Column(name = "settlement_cycle_id", length = 100) private String settlementCycleId;
    @Column(name = "correlation_id", length = 120) private String correlationId;
    @Column(name = "causation_id", length = 120) private String causationId;
    @Column(name = "hop_count", nullable = false) private int hopCount;
    @Column(name = "max_hop", nullable = false) private int maxHop = 8;
    @Enumerated(EnumType.STRING) @Column(name = "revenue_type", nullable = false, length = 80) private RevenueType revenueType;
    @Column(name = "revenue_amount", nullable = false, precision = 18, scale = 2) private BigDecimal revenueAmount;
    @Column(nullable = false, length = 12) private String currency = "KRW";
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected NexusRevenueEventEntity() {}
    public NexusRevenueEventEntity(String eventId, String idempotencyKey, String simulationRunId, String settlementCycleId,
                                   String correlationId, String causationId, int hopCount, int maxHop,
                                   RevenueType revenueType, BigDecimal revenueAmount, String currency, String reason, Instant createdAt) {
        this.eventId = eventId; this.idempotencyKey = idempotencyKey; this.simulationRunId = simulationRunId;
        this.settlementCycleId = settlementCycleId; this.correlationId = correlationId; this.causationId = causationId;
        this.hopCount = hopCount; this.maxHop = maxHop; this.revenueType = revenueType; this.revenueAmount = revenueAmount;
        this.currency = currency; this.reason = reason; this.createdAt = createdAt;
    }
    public String eventId(){return eventId;} public String idempotencyKey(){return idempotencyKey;}
    public String simulationRunId(){return simulationRunId;} public String settlementCycleId(){return settlementCycleId;}
    public String correlationId(){return correlationId;} public String causationId(){return causationId;}
    public int hopCount(){return hopCount;} public int maxHop(){return maxHop;}
    public RevenueType revenueType(){return revenueType;} public BigDecimal revenueAmount(){return revenueAmount;}
    public String currency(){return currency;} public String reason(){return reason;} public Instant createdAt(){return createdAt;}
}
