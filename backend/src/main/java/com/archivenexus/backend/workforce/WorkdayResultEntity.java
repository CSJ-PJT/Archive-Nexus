package com.archivenexus.backend.workforce;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nexus_workday_result",
        uniqueConstraints = @UniqueConstraint(name = "nexus_workday_result_workday_id_key", columnNames = "workday_id"),
        indexes = @Index(name = "nexus_workday_result_date_idx", columnList = "work_date,created_at"))
public class WorkdayResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workday_id", nullable = false, length = 120)
    private String workdayId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "used_capacity", nullable = false)
    private int usedCapacity;

    @Column(name = "remaining_capacity", nullable = false)
    private int remainingCapacity;

    @Column(name = "production_requested", nullable = false)
    private int productionRequested;

    @Column(name = "production_completed", nullable = false)
    private int productionCompleted;

    @Column(name = "production_backlog", nullable = false)
    private int productionBacklog;

    @Column(name = "quality_checked", nullable = false)
    private int qualityChecked;

    @Column(name = "quality_defects", nullable = false)
    private int qualityDefects;

    @Column(name = "maintenance_completed", nullable = false)
    private int maintenanceCompleted;

    @Column(name = "payroll_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal payrollCost;

    @Column(name = "productivity_score", nullable = false, precision = 8, scale = 4)
    private BigDecimal productivityScore;

    @Column(name = "bottleneck_role", length = 60)
    private String bottleneckRole;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "text")
    private String evidenceJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkdayResultEntity() {
    }

    public WorkdayResultEntity(String workdayId, LocalDate workDate, int totalCapacity, int usedCapacity,
                               int remainingCapacity, int productionRequested, int productionCompleted,
                               int productionBacklog, int qualityChecked, int qualityDefects,
                               int maintenanceCompleted, BigDecimal payrollCost, BigDecimal productivityScore,
                               String bottleneckRole, String status, String evidenceJson, Instant createdAt) {
        this.workdayId = workdayId;
        this.workDate = workDate;
        this.totalCapacity = totalCapacity;
        this.usedCapacity = usedCapacity;
        this.remainingCapacity = remainingCapacity;
        this.productionRequested = productionRequested;
        this.productionCompleted = productionCompleted;
        this.productionBacklog = productionBacklog;
        this.qualityChecked = qualityChecked;
        this.qualityDefects = qualityDefects;
        this.maintenanceCompleted = maintenanceCompleted;
        this.payrollCost = payrollCost;
        this.productivityScore = productivityScore;
        this.bottleneckRole = bottleneckRole;
        this.status = status;
        this.evidenceJson = evidenceJson == null ? "{}" : evidenceJson;
        this.createdAt = createdAt;
    }

    public String workdayId() { return workdayId; }
    public LocalDate workDate() { return workDate; }
    public int totalCapacity() { return totalCapacity; }
    public int usedCapacity() { return usedCapacity; }
    public int remainingCapacity() { return remainingCapacity; }
    public int productionRequested() { return productionRequested; }
    public int productionCompleted() { return productionCompleted; }
    public int productionBacklog() { return productionBacklog; }
    public int qualityChecked() { return qualityChecked; }
    public int qualityDefects() { return qualityDefects; }
    public int maintenanceCompleted() { return maintenanceCompleted; }
    public BigDecimal payrollCost() { return payrollCost; }
    public BigDecimal productivityScore() { return productivityScore; }
    public String bottleneckRole() { return bottleneckRole; }
    public String status() { return status; }
    public String evidenceJson() { return evidenceJson; }
    public Instant createdAt() { return createdAt; }
}
