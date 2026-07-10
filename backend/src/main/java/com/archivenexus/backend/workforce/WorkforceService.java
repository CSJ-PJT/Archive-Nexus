package com.archivenexus.backend.workforce;

import com.archivenexus.backend.service.NexusStateService;
import com.archivenexus.backend.workforce.WorkforceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkforceService {
    private static final String SERVICE_NAME = "Archive-Nexus";
    private static final List<String> ALLOWED_SOURCES = List.of("Archive-Market", "ArchiveOS");

    private final WorkforceAllocationRepository allocations;
    private final WorkdayProductivityRepository workdays;
    private final NexusStateService nexus;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final int baselineCapacity;

    public WorkforceService(WorkforceAllocationRepository allocations,
                            WorkdayProductivityRepository workdays,
                            NexusStateService nexus,
                            ObjectMapper mapper,
                            @Value("${archive.workforce.enabled:false}") boolean enabled,
                            @Value("${archive.workforce.baseline-capacity:120}") int baselineCapacity) {
        this.allocations = allocations;
        this.workdays = workdays;
        this.nexus = nexus;
        this.mapper = mapper;
        this.enabled = enabled;
        this.baselineCapacity = Math.max(1, baselineCapacity);
    }

    @Transactional
    public WorkforceAllocationResponse assign(WorkforceAllocationRequest request) {
        WorkforceAllocationRequest safe = normalize(request);
        WorkforceAllocationEntity existing = allocations.findByIdempotencyKey(safe.idempotencyKey())
                .orElseGet(() -> allocations.findByEventId(safe.eventId()).orElse(null));
        if (existing != null) {
            return allocationResponse(existing, true);
        }

        WorkforceAllocationStatus status = safe.hopCount() > safe.maxHop()
                ? WorkforceAllocationStatus.REJECTED
                : WorkforceAllocationStatus.ACTIVE;
        String reason = status == WorkforceAllocationStatus.REJECTED
                ? "hopCount exceeds maxHop"
                : safe.reason();
        WorkforceAllocationEntity saved = allocations.save(new WorkforceAllocationEntity(
                safe.eventId(),
                safe.idempotencyKey(),
                safe.sourceService(),
                safe.role(),
                Math.max(0, safe.assignedUnits()),
                positiveDecimal(safe.skillLevel(), BigDecimal.ONE),
                positiveDecimal(safe.costPerUnitKrw(), BigDecimal.ZERO),
                safe.workdayId(),
                safe.simulationRunId(),
                safe.settlementCycleId(),
                safe.correlationId(),
                safe.causationId(),
                safe.hopCount(),
                safe.maxHop(),
                status,
                reason,
                write(safe.payload()),
                Instant.now(),
                Instant.now()
        ));
        return allocationResponse(saved, false);
    }

    public WorkforceSummary workforceSummary() {
        CapacitySnapshot capacity = capacitySnapshot();
        ProductivitySummary productivity = productivitySummary();
        return new WorkforceSummary(
                enabled,
                SERVICE_NAME,
                baselineCapacity,
                activeWorkers(WorkforceRole.PRODUCTION),
                activeWorkers(WorkforceRole.QUALITY),
                activeWorkers(WorkforceRole.MAINTENANCE),
                activeAllocations().stream().mapToInt(WorkforceAllocationEntity::assignedUnits).sum(),
                dailyLaborCost(),
                capacity.totalCapacity(),
                capacity.demandUnits(),
                capacity.bottleneckRole(),
                productivity.lastProductivityRate(),
                allocations.count(),
                workdays.count(),
                Instant.now()
        );
    }

    public CapacitySummary capacitySummary() {
        CapacitySnapshot snapshot = capacitySnapshot();
        return new CapacitySummary(
                enabled,
                baselineCapacity,
                snapshot.productionCapacity(),
                snapshot.qualityCapacity(),
                snapshot.maintenanceCapacity(),
                snapshot.totalCapacity(),
                snapshot.demandUnits(),
                Math.max(0, snapshot.totalCapacity() - snapshot.demandUnits()),
                Math.max(0, snapshot.demandUnits() - snapshot.totalCapacity()),
                snapshot.bottleneckRole(),
                Instant.now()
        );
    }

    public ProductivitySummary productivitySummary() {
        List<WorkdayProductivityEntity> recent = workdays.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50));
        if (recent.isEmpty()) {
            return new ProductivitySummary(enabled, 0, demandUnits(), demandUnits(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, bottleneckRole(), 0, Instant.now());
        }
        WorkdayProductivityEntity last = recent.getFirst();
        BigDecimal average = recent.stream()
                .map(WorkdayProductivityEntity::productivityRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), 4, RoundingMode.HALF_UP);
        return new ProductivitySummary(enabled, last.processedUnits(), last.backlogBefore(), last.backlogAfter(),
                last.productivityRate(), average, last.laborCostKrw(), last.bottleneckRole(), workdays.count(), Instant.now());
    }

    @Transactional
    public WorkdayRunResponse runWorkday(LocalDate requestedDate) {
        LocalDate workDate = requestedDate == null ? LocalDate.now() : requestedDate;
        String workdayId = "NEXUS-WORKDAY-" + workDate;
        WorkdayProductivityEntity existing = workdays.findByWorkdayId(workdayId).orElse(null);
        if (existing != null) {
            return workdayResponse(existing);
        }

        CapacitySnapshot capacity = capacitySnapshot();
        int backlogBefore = capacity.demandUnits();
        int processed = Math.min(backlogBefore, capacity.totalCapacity());
        int backlogAfter = Math.max(0, backlogBefore - processed);
        BigDecimal productivityRate = backlogBefore == 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(processed).divide(BigDecimal.valueOf(backlogBefore), 4, RoundingMode.HALF_UP);
        String status = backlogAfter > 0 ? "BACKLOG_INCREASED" : "WORKDAY_COMPLETED";
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("enabled", enabled);
        evidence.put("baselineCapacity", baselineCapacity);
        evidence.put("productionDemand", nexus.productionOrders().size());
        evidence.put("qualityDemand", nexus.inspections().size());
        evidence.put("maintenanceDemand", nexus.maintenanceEvents().size());
        evidence.put("bottleneckRole", capacity.bottleneckRole());

        WorkdayProductivityEntity saved = workdays.save(new WorkdayProductivityEntity(
                workdayId,
                workDate,
                "SIM-" + workDate,
                "CYCLE-" + workDate,
                "CORR-" + UUID.randomUUID(),
                "CAUSE-WORKDAY-RUN",
                capacity.totalCapacity(),
                processed,
                backlogBefore,
                backlogAfter,
                dailyLaborCost(),
                productivityRate,
                capacity.bottleneckRole(),
                status,
                write(evidence),
                Instant.now()
        ));
        return workdayResponse(saved);
    }

    private WorkforceAllocationRequest normalize(WorkforceAllocationRequest request) {
        WorkforceAllocationRequest safe = request == null
                ? new WorkforceAllocationRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null)
                : request;
        String eventId = text(safe.eventId(), "WF-" + UUID.randomUUID());
        String idempotencyKey = text(safe.idempotencyKey(), "WF-IDEMP-" + UUID.randomUUID());
        String source = text(safe.sourceService(), "ArchiveOS");
        if (!ALLOWED_SOURCES.contains(source)) {
            source = "ArchiveOS";
        }
        return new WorkforceAllocationRequest(
                eventId,
                idempotencyKey,
                source,
                safe.eventType() == null ? WorkforceEventType.WORKFORCE_ALLOCATION_ASSIGNED : safe.eventType(),
                safe.role() == null ? WorkforceRole.PRODUCTION : safe.role(),
                safe.assignedUnits() == null ? 0 : safe.assignedUnits(),
                safe.skillLevel(),
                safe.costPerUnitKrw(),
                safe.workdayId(),
                safe.simulationRunId(),
                safe.settlementCycleId(),
                safe.correlationId(),
                safe.causationId(),
                safe.hopCount() == null ? 0 : safe.hopCount(),
                safe.maxHop() == null ? 8 : safe.maxHop(),
                safe.reason(),
                safe.payload()
        );
    }

    private WorkforceAllocationResponse allocationResponse(WorkforceAllocationEntity entity, boolean duplicate) {
        return new WorkforceAllocationResponse(entity.eventId(), entity.idempotencyKey(), entity.sourceService(),
                entity.role(), entity.assignedUnits(), entity.skillLevel(), entity.costPerUnitKrw(),
                entity.status(), duplicate, entity.reason(), entity.correlationId(), entity.createdAt());
    }

    private WorkdayRunResponse workdayResponse(WorkdayProductivityEntity entity) {
        return new WorkdayRunResponse(entity.workdayId(), entity.workDate(), entity.totalCapacity(),
                entity.processedUnits(), entity.backlogBefore(), entity.backlogAfter(), entity.laborCostKrw(),
                entity.productivityRate(), entity.bottleneckRole(), entity.status(), read(entity.evidenceJson()),
                entity.createdAt());
    }

    private CapacitySnapshot capacitySnapshot() {
        int production = enabled ? roleCapacity(WorkforceRole.PRODUCTION) : baselineCapacity;
        int quality = enabled ? roleCapacity(WorkforceRole.QUALITY) : Math.max(1, baselineCapacity / 3);
        int maintenance = enabled ? roleCapacity(WorkforceRole.MAINTENANCE) : Math.max(1, baselineCapacity / 4);
        int total = production + quality + maintenance;
        int demand = demandUnits();
        return new CapacitySnapshot(production, quality, maintenance, total, demand, bottleneckRole());
    }

    private int roleCapacity(WorkforceRole role) {
        int capacity = activeAllocations().stream()
                .filter(allocation -> allocation.role() == role)
                .mapToInt(allocation -> allocation.skillLevel()
                        .multiply(BigDecimal.valueOf(allocation.assignedUnits()))
                        .multiply(BigDecimal.valueOf(roleMultiplier(role)))
                        .setScale(0, RoundingMode.HALF_UP)
                        .intValue())
                .sum();
        return Math.max(1, capacity);
    }

    private double roleMultiplier(WorkforceRole role) {
        return switch (role) {
            case PRODUCTION -> 12.0;
            case QUALITY -> 8.0;
            case MAINTENANCE -> 6.0;
        };
    }

    private int demandUnits() {
        return nexus.productionOrders().size() + nexus.inspections().size() + nexus.maintenanceEvents().size();
    }

    private String bottleneckRole() {
        int productionGap = nexus.productionOrders().size() - (enabled ? roleCapacity(WorkforceRole.PRODUCTION) : baselineCapacity);
        int qualityGap = nexus.inspections().size() - (enabled ? roleCapacity(WorkforceRole.QUALITY) : baselineCapacity / 3);
        int maintenanceGap = nexus.maintenanceEvents().size() - (enabled ? roleCapacity(WorkforceRole.MAINTENANCE) : baselineCapacity / 4);
        if (productionGap >= qualityGap && productionGap >= maintenanceGap && productionGap > 0) {
            return WorkforceRole.PRODUCTION.name();
        }
        if (qualityGap >= maintenanceGap && qualityGap > 0) {
            return WorkforceRole.QUALITY.name();
        }
        if (maintenanceGap > 0) {
            return WorkforceRole.MAINTENANCE.name();
        }
        return "NONE";
    }

    private int activeWorkers(WorkforceRole role) {
        return activeAllocations().stream()
                .filter(allocation -> allocation.role() == role)
                .mapToInt(WorkforceAllocationEntity::assignedUnits)
                .sum();
    }

    private BigDecimal dailyLaborCost() {
        return activeAllocations().stream()
                .map(allocation -> allocation.costPerUnitKrw().multiply(BigDecimal.valueOf(allocation.assignedUnits())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<WorkforceAllocationEntity> activeAllocations() {
        return allocations.findAllByStatus(WorkforceAllocationStatus.ACTIVE);
    }

    private BigDecimal positiveDecimal(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return fallback;
        }
        return value;
    }

    private String write(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json == null ? "{}" : json, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CapacitySnapshot(
            int productionCapacity,
            int qualityCapacity,
            int maintenanceCapacity,
            int totalCapacity,
            int demandUnits,
            String bottleneckRole
    ) {
    }
}
