package com.archivenexus.backend.logisticssettlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class LogisticsSettlementModels {
    private LogisticsSettlementModels() {}

    public enum SettlementProcessingStatus {
        RECEIVED,
        INVALID
    }

    public record DailySettlementRequest(
            String settlementId,
            String idempotencyKey,
            String source,
            Integer schemaVersion,
            LocalDate settlementDate,
            String factoryId,
            String currency,
            Integer totalShipments,
            Integer delayedShipments,
            Integer heldShipments,
            Integer totalQuantity,
            BigDecimal totalLogisticsCost,
            BigDecimal manufacturingImpactCost,
            BigDecimal onTimeRate,
            Map<String, Object> evidence,
            Map<String, Object> payload,
            Instant occurredAt
    ) {}

    public record BulkDailySettlementRequest(List<DailySettlementRequest> settlements) {}

    public record DailySettlementResponse(
            String settlementId,
            String idempotencyKey,
            boolean duplicate,
            String source,
            int schemaVersion,
            LocalDate settlementDate,
            String factoryId,
            SettlementProcessingStatus processingStatus,
            String currency,
            int totalShipments,
            int delayedShipments,
            int heldShipments,
            int totalQuantity,
            BigDecimal totalLogisticsCost,
            BigDecimal manufacturingImpactCost,
            BigDecimal onTimeRate,
            Map<String, Object> evidence,
            Map<String, Object> payload,
            Instant occurredAt,
            Instant receivedAt,
            Instant processedAt,
            int duplicateCount
    ) {}

    public record BulkDailySettlementResponse(int requested, int received, int duplicates, int failed, List<DailySettlementResponse> results) {}

    public record SettlementSummary(
            long total,
            long received,
            long invalid,
            BigDecimal totalLogisticsCost,
            BigDecimal manufacturingImpactCost,
            Instant lastReceivedAt,
            List<FactorySettlementSummary> factories
    ) {}

    public record FactorySettlementSummary(String factoryId, long count) {}
}
