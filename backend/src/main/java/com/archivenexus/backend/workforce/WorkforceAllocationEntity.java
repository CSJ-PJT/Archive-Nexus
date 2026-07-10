package com.archivenexus.backend.workforce;

import com.archivenexus.backend.workforce.WorkforceModels.WorkforceAllocationStatus;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceRole;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "nexus_workforce_allocation",
        uniqueConstraints = {
                @UniqueConstraint(name = "nexus_workforce_allocation_event_id_key", columnNames = "event_id"),
                @UniqueConstraint(name = "nexus_workforce_allocation_idempotency_key_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "nexus_workforce_allocation_role_idx", columnList = "workforce_role,status,created_at"),
                @Index(name = "nexus_workforce_allocation_workday_idx", columnList = "workday_id,created_at")
        })
public class WorkforceAllocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;

    @Enumerated(EnumType.STRING)
    @Column(name = "workforce_role", nullable = false, length = 40)
    private WorkforceRole role;

    @Column(name = "assigned_units", nullable = false)
    private int assignedUnits;

    @Column(name = "skill_level", nullable = false, precision = 8, scale = 4)
    private BigDecimal skillLevel;

    @Column(name = "cost_per_unit_krw", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPerUnitKrw;

    @Column(name = "workday_id", length = 120)
    private String workdayId;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkforceAllocationStatus status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkforceAllocationEntity() {
    }

    public WorkforceAllocationEntity(String eventId, String idempotencyKey, String sourceService, WorkforceRole role,
                                     int assignedUnits, BigDecimal skillLevel, BigDecimal costPerUnitKrw,
                                     String workdayId, String simulationRunId, String settlementCycleId,
                                     String correlationId, String causationId, int hopCount, int maxHop,
                                     WorkforceAllocationStatus status, String reason, String payloadJson,
                                     Instant assignedAt, Instant createdAt) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.sourceService = sourceService;
        this.role = role;
        this.assignedUnits = assignedUnits;
        this.skillLevel = skillLevel;
        this.costPerUnitKrw = costPerUnitKrw;
        this.workdayId = workdayId;
        this.simulationRunId = simulationRunId;
        this.settlementCycleId = settlementCycleId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.hopCount = hopCount;
        this.maxHop = maxHop;
        this.status = status;
        this.reason = reason;
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
        this.assignedAt = assignedAt;
        this.createdAt = createdAt;
    }

    public String eventId() { return eventId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String sourceService() { return sourceService; }
    public WorkforceRole role() { return role; }
    public int assignedUnits() { return assignedUnits; }
    public BigDecimal skillLevel() { return skillLevel; }
    public BigDecimal costPerUnitKrw() { return costPerUnitKrw; }
    public String workdayId() { return workdayId; }
    public String simulationRunId() { return simulationRunId; }
    public String settlementCycleId() { return settlementCycleId; }
    public String correlationId() { return correlationId; }
    public String causationId() { return causationId; }
    public int hopCount() { return hopCount; }
    public int maxHop() { return maxHop; }
    public WorkforceAllocationStatus status() { return status; }
    public String reason() { return reason; }
    public Instant createdAt() { return createdAt; }
}
