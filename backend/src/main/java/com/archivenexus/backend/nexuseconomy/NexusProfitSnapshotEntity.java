package com.archivenexus.backend.nexuseconomy;

import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.BankruptcyRisk;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nexus_profit_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "nexus_profit_snapshot_snapshot_id_key", columnNames = "snapshot_id"),
        indexes = @Index(name = "nexus_profit_snapshot_date_idx", columnList = "settlement_date, created_at"))
public class NexusProfitSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "snapshot_id", nullable = false, length = 120) private String snapshotId;
    @Column(name = "settlement_date", nullable = false) private LocalDate settlementDate;
    @Column(name = "revenue_amount", nullable = false, precision = 18, scale = 2) private BigDecimal revenueAmount;
    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 2) private BigDecimal costAmount;
    @Column(name = "profit_amount", nullable = false, precision = 18, scale = 2) private BigDecimal profitAmount;
    @Column(name = "cash_balance", nullable = false, precision = 18, scale = 2) private BigDecimal cashBalance;
    @Enumerated(EnumType.STRING) @Column(name = "bankruptcy_risk", nullable = false, length = 40) private BankruptcyRisk bankruptcyRisk;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected NexusProfitSnapshotEntity() {}
    public NexusProfitSnapshotEntity(String snapshotId, LocalDate settlementDate, BigDecimal revenueAmount, BigDecimal costAmount,
                                     BigDecimal profitAmount, BigDecimal cashBalance, BankruptcyRisk bankruptcyRisk, Instant createdAt) {
        this.snapshotId = snapshotId; this.settlementDate = settlementDate; this.revenueAmount = revenueAmount;
        this.costAmount = costAmount; this.profitAmount = profitAmount; this.cashBalance = cashBalance;
        this.bankruptcyRisk = bankruptcyRisk; this.createdAt = createdAt;
    }
    public String snapshotId(){return snapshotId;} public LocalDate settlementDate(){return settlementDate;}
    public BigDecimal revenueAmount(){return revenueAmount;} public BigDecimal costAmount(){return costAmount;}
    public BigDecimal profitAmount(){return profitAmount;} public BigDecimal cashBalance(){return cashBalance;}
    public BankruptcyRisk bankruptcyRisk(){return bankruptcyRisk;} public Instant createdAt(){return createdAt;}
}
