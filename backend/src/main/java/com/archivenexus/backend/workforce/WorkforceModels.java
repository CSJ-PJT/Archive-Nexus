package com.archivenexus.backend.workforce;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public final class WorkforceModels {
    private WorkforceModels() {
    }

    public enum WorkforceRole {
        PRODUCTION_OPERATOR,
        QUALITY_INSPECTOR,
        MAINTENANCE_ENGINEER,
        MATERIAL_HANDLER,
        FACTORY_MANAGER
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
            Integer allocatedHeadcount,
            Integer assignedUnits,
            Integer capacityPerPersonPerDay,
            BigDecimal productivityScore,
            BigDecimal skillLevel,
            BigDecimal wagePerDay,
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
            String allocationId,
            String targetService,
            int allocatedHeadcount,
            int assignedUnits,
            int capacityPerPersonPerDay,
            BigDecimal productivityScore,
            BigDecimal skillLevel,
            BigDecimal wagePerDay,
            BigDecimal costPerUnitKrw,
            int effectiveCapacity,
            int usedCapacity,
            int remainingCapacity,
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
            int activeProductionOperators,
            int activeQualityInspectors,
            int activeMaintenanceEngineers,
            int activeMaterialHandlers,
            int activeFactoryManagers,
            int totalActiveWorkers,
            BigDecimal dailyLaborCostKrw,
            BigDecimal payrollCost,
            int estimatedDailyCapacity,
            int usedCapacity,
            int remainingCapacity,
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
            int materialCapacity,
            int managementCapacity,
            int estimatedDailyCapacity,
            int demandUnits,
            int usedCapacity,
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
            BigDecimal lastPayrollCost,
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
            BigDecimal payrollCost,
            BigDecimal productivityRate,
            int productionRequested,
            int productionCompleted,
            int qualityChecked,
            int qualityDefects,
            int maintenanceCompleted,
            String bottleneckRole,
            String status,
            Map<String, Object> evidence,
            Instant createdAt
    ) {
    }
}
