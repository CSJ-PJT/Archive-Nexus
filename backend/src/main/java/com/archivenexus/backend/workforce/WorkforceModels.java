package com.archivenexus.backend.workforce;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public final class WorkforceModels {
    private WorkforceModels() {
    }

    public enum WorkforceRole {
        PRODUCTION,
        QUALITY,
        MAINTENANCE
    }

    public enum WorkforceAllocationStatus {
        ACTIVE,
        REJECTED
    }

    public enum WorkforceEventType {
        WORKFORCE_ALLOCATION_ASSIGNED,
        WORKDAY_STARTED,
        WORKDAY_COMPLETED,
        PRODUCTIVITY_REPORTED,
        CAPACITY_SHORTAGE_DETECTED,
        BACKLOG_INCREASED,
        BOTTLENECK_DETECTED
    }

    public record WorkforceAllocationRequest(
            String eventId,
            String idempotencyKey,
            String sourceService,
            WorkforceEventType eventType,
            WorkforceRole role,
            Integer assignedUnits,
            BigDecimal skillLevel,
            BigDecimal costPerUnitKrw,
            String workdayId,
            String simulationRunId,
            String settlementCycleId,
            String correlationId,
            String causationId,
            Integer hopCount,
            Integer maxHop,
            String reason,
            Map<String, Object> payload
    ) {
    }

    public record WorkforceAllocationResponse(
            String eventId,
            String idempotencyKey,
            String sourceService,
            WorkforceRole role,
            int assignedUnits,
            BigDecimal skillLevel,
            BigDecimal costPerUnitKrw,
            WorkforceAllocationStatus status,
            boolean duplicate,
            String reason,
            String correlationId,
            Instant createdAt
    ) {
    }

    public record WorkforceSummary(
            boolean enabled,
            String service,
            int baselineCapacity,
            int activeProductionWorkers,
            int activeQualityWorkers,
            int activeMaintenanceWorkers,
            int totalActiveWorkers,
            BigDecimal dailyLaborCostKrw,
            int estimatedDailyCapacity,
            int backlog,
            String bottleneckRole,
            BigDecimal productivityRate,
            long allocationEvents,
            long completedWorkdays,
            Instant generatedAt
    ) {
    }

    public record CapacitySummary(
            boolean enabled,
            int baselineCapacity,
            int productionCapacity,
            int qualityCapacity,
            int maintenanceCapacity,
            int estimatedDailyCapacity,
            int demandUnits,
            int availableCapacity,
            int capacityGap,
            String bottleneckRole,
            Instant generatedAt
    ) {
    }

    public record ProductivitySummary(
            boolean enabled,
            int lastProcessedUnits,
            int lastBacklogBefore,
            int lastBacklogAfter,
            BigDecimal lastProductivityRate,
            BigDecimal averageProductivityRate,
            BigDecimal lastLaborCostKrw,
            String lastBottleneckRole,
            long completedWorkdays,
            Instant generatedAt
    ) {
    }

    public record WorkdayRunResponse(
            String workdayId,
            LocalDate workDate,
            int totalCapacity,
            int processedUnits,
            int backlogBefore,
            int backlogAfter,
            BigDecimal laborCostKrw,
            BigDecimal productivityRate,
            String bottleneckRole,
            String status,
            Map<String, Object> evidence,
            Instant createdAt
    ) {
    }
}
