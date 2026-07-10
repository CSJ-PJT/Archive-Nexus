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
            String sourceService,
            String domain,
            String eventType,
            String entityType,
            String entityId,
            String correlationId,
            String causationId,
            String status,
            String severity,
            String displayLabel,
            Instant occurredAt,
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
            WorkforceOperationsSummary workforce,
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
            String status
    ) {
    }

    public record WorkforceOperationsSummary(
            Integer totalHeadcount,
            Integer effectiveCapacity,
            Integer usedCapacity,
            Integer backlog,
            BigDecimal productivityRate,
            String bottleneckRole
    ) {
    }
}
