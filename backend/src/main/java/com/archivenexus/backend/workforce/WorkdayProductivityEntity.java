package com.archivenexus.backend.workforce;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nexus_workday_productivity",
        uniqueConstraints = @UniqueConstraint(name = "nexus_workday_productivity_workday_id_key", columnNames = "workday_id"),
        indexes = @Index(name = "nexus_workday_productivity_date_idx", columnList = "work_date,created_at"))
public class WorkdayProductivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workday_id", nullable = false, length = 120)
    private String workdayId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "simulation_run_id", length = 120)
    private String simulationRunId;

    @Column(name = "settlement_cycle_id", length = 120)
    private String settlementCycleId;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "causation_id", length = 120)
    private String causationId;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "processed_units", nullable = false)
    private int processedUnits;

    @Column(name = "backlog_before", nullable = false)
    private int backlogBefore;

    @Column(name = "backlog_after", nullable = false)
    private int backlogAfter;

    @Column(name = "labor_cost_krw", nullable = false, precision = 18, scale = 2)
    private BigDecimal laborCostKrw;

    @Column(name = "productivity_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal productivityRate;

    @Column(name = "bottleneck_role", length = 40)
    private String bottleneckRole;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "text")
    private String evidenceJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkdayProductivityEntity() {
    }

    public WorkdayProductivityEntity(String workdayId, LocalDate workDate, String simulationRunId,
                                     String settlementCycleId, String correlationId, String causationId,
                                     int totalCapacity, int processedUnits, int backlogBefore, int backlogAfter,
                                     BigDecimal laborCostKrw, BigDecimal productivityRate, String bottleneckRole,
                                     String status, String evidenceJson, Instant createdAt) {
        this.workdayId = workdayId;
        this.workDate = workDate;
        this.simulationRunId = simulationRunId;
        this.settlementCycleId = settlementCycleId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.totalCapacity = totalCapacity;
        this.processedUnits = processedUnits;
        this.backlogBefore = backlogBefore;
        this.backlogAfter = backlogAfter;
        this.laborCostKrw = laborCostKrw;
        this.productivityRate = productivityRate;
        this.bottleneckRole = bottleneckRole;
        this.status = status;
        this.evidenceJson = evidenceJson == null ? "{}" : evidenceJson;
        this.createdAt = createdAt;
    }

    public String workdayId() { return workdayId; }
    public LocalDate workDate() { return workDate; }
    public String simulationRunId() { return simulationRunId; }
    public String settlementCycleId() { return settlementCycleId; }
    public String correlationId() { return correlationId; }
    public String causationId() { return causationId; }
    public int totalCapacity() { return totalCapacity; }
    public int processedUnits() { return processedUnits; }
    public int backlogBefore() { return backlogBefore; }
    public int backlogAfter() { return backlogAfter; }
    public BigDecimal laborCostKrw() { return laborCostKrw; }
    public BigDecimal productivityRate() { return productivityRate; }
    public String bottleneckRole() { return bottleneckRole; }
    public String status() { return status; }
    public String evidenceJson() { return evidenceJson; }
    public Instant createdAt() { return createdAt; }
}
