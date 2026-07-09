package com.archivenexus.backend.nexuseconomy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class NexusEconomyModels {
    private NexusEconomyModels() {}

    public enum RevenueType {
        PRODUCTION_REVENUE_RECOGNIZED,
        SHIPMENT_REVENUE_RECOGNIZED,
        QUALITY_RECOVERY_REVENUE_RECOGNIZED,
        INVENTORY_CLEARANCE_REVENUE_RECOGNIZED
    }

    public enum CostType {
        MATERIAL_COST_INCURRED,
        MAINTENANCE_COST_INCURRED,
        QUALITY_LOSS_INCURRED,
        INVENTORY_HOLDING_COST_INCURRED,
        LOGISTICS_SERVICE_FEE_PAID,
        LOGISTICS_DAILY_SETTLEMENT_FEE_PAID,
        LEDGER_SETTLEMENT_AGENCY_FEE_PAID,
        LEDGER_RECONCILIATION_FEE_PAID,
        NEXUS_OPERATION_COST_INCURRED
    }

    public enum BankruptcyRisk {
        LOW,
        WARNING,
        CRITICAL
    }

    public record ExternalCostEventRequest(
            String eventId,
            String idempotencyKey,
            String simulationRunId,
            String settlementCycleId,
            String correlationId,
            String causationId,
            Integer hopCount,
            Integer maxHop,
            String sourceService,
            CostType costType,
            BigDecimal costAmount,
            String currency,
            String reason
    ) {}

    public record ExternalCostEventBulkRequest(List<ExternalCostEventRequest> events) {}

    public record EconomyEventResponse(
            String eventId,
            String idempotencyKey,
            String simulationRunId,
            String settlementCycleId,
            String correlationId,
            String causationId,
            int hopCount,
            int maxHop,
            boolean duplicate,
            String type,
            BigDecimal amount,
            String currency,
            String sourceService,
            String reason,
            Instant createdAt
    ) {}

    public record ExternalCostEventBulkResponse(int requested, int accepted, int duplicate, int rejected, List<EconomyEventResponse> results) {}

    public record RevenueEventView(String eventId, String idempotencyKey, String simulationRunId, String settlementCycleId,
                                   String correlationId, String causationId, int hopCount, int maxHop,
                                   RevenueType revenueType, BigDecimal revenueAmount, String currency, String reason, Instant createdAt) {}

    public record CostEventView(String eventId, String idempotencyKey, String simulationRunId, String settlementCycleId,
                                String correlationId, String causationId, int hopCount, int maxHop,
                                String sourceService, CostType costType, BigDecimal costAmount, String currency, String reason, Instant createdAt) {}

    public record ProfitSnapshotView(String snapshotId, LocalDate settlementDate, BigDecimal revenueAmount,
                                     BigDecimal costAmount, BigDecimal profitAmount, BigDecimal cashBalance,
                                     BankruptcyRisk bankruptcyRisk, Instant createdAt) {}

    public record NexusEconomySummary(BigDecimal totalRevenue, BigDecimal totalCost, BigDecimal profit,
                                      BigDecimal cashBalance, BankruptcyRisk bankruptcyRisk,
                                      long revenueEventCount, long costEventCount,
                                      Map<String, BigDecimal> revenueByType,
                                      Map<String, BigDecimal> costByType,
                                      String dailyClosePath) {}
}
