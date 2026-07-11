package com.archivenexus.backend.runtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RuntimeEventModels {
    private RuntimeEventModels() {
    }

    public record RuntimeEventResponse(
            String eventId,
            String idempotencyKey,
            String sourceService,
            String targetService,
            String domain,
            String eventType,
            String entityType,
            String entityId,
            String orderId,
            String correlationId,
            String causationId,
            String simulationRunId,
            String settlementCycleId,
            String workdayId,
            String status,
            String severity,
            String displayLabel,
            Instant occurredAt,
            int hopCount,
            int maxHop,
            Map<String, Object> metadata
    ) {
    }

    public record OperationsSummaryResponse(
            String serviceName,
            String serviceRole,
            String status,
            Instant latestEventAt,
            int productionRequested,
            int productionCompleted,
            int productionBacklog,
            int qualityDefects,
            long marketOriginEvents,
            OutboxOperationsSummary outbox,
            EconomyOperationsSummary economy,
            ProductionOperationsSummary production,
            WorkforceOperationsSummary workforce,
            RuntimeStatusResponse runtime,
            String degradedReason,
            boolean liveFlowAvailable,
            List<String> readOnlyApis,
            Instant generatedAt
    ) {
    }

    public record OutboxOperationsSummary(
            long pending,
            long published,
            long failed,
            long retry
    ) {
    }

    public record EconomyOperationsSummary(
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal profit,
            String status,
            BigDecimal manufacturingRevenue,
            BigDecimal materialCost,
            BigDecimal maintenanceCost,
            BigDecimal qualityLossCost,
            BigDecimal logisticsFee,
            BigDecimal workforceCost,
            BigDecimal operatingProfit,
            BigDecimal operatingMargin,
            BigDecimal cashBalance,
            BigDecimal qualityDefectRate,
            BigDecimal downtimeRate,
            int negativeProfitStreak,
            boolean available,
            String reason,
            BigDecimal totalCost
    ) {
    }

    public record ProductionOperationsSummary(
            boolean available,
            String reason,
            Integer requested,
            Integer completed,
            Integer backlog,
            BigDecimal capacityUtilization,
            String bottleneckRole,
            BigDecimal qualityDefectRate,
            BigDecimal downtimeRate
    ) {
    }

    public record WorkforceOperationsSummary(
            Integer totalHeadcount,
            Integer effectiveCapacity,
            Integer usedCapacity,
            Integer backlog,
            BigDecimal productivityRate,
            BigDecimal capacityUtilization,
            String bottleneckRole
    ) {
    }

    public record RuntimeStatusResponse(
            String service,
            boolean runtimeActive,
            boolean autoRunEnabled,
            String schedulerStatus,
            Instant lastWorkAt,
            Instant lastEventAt,
            int eventsProducedLastTick,
            int eventsConsumedLastTick,
            int backlogCount,
            Long oldestBacklogAgeSeconds,
            String latestCursor,
            String degradedReason,
            String pipelineStatus,
            Instant generatedAt
    ) {
    }
}
