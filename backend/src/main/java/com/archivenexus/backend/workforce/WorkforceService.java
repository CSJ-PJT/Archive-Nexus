package com.archivenexus.backend.workforce;

import com.archivenexus.backend.outbox.OutboxModels.EventType;
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
    private final WorkdayResultRepository workdayResults;
    private final NexusStateService nexus;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final int baselineCapacity;
    private final int summaryDemandMultiplier;
    private final int maxSummaryDemandPerRole;

    public WorkforceService(WorkforceAllocationRepository allocations,
                            WorkdayResultRepository workdayResults,
                            NexusStateService nexus,
                            ObjectMapper mapper,
                            @Value("${archive.workforce.enabled:false}") boolean enabled,
                            @Value("${archive.workforce.baseline-capacity:120}") int baselineCapacity,
                            @Value("${archive.workforce.summary-demand-multiplier:2}") int summaryDemandMultiplier,
                            @Value("${archive.workforce.max-summary-demand-per-role:1000}") int maxSummaryDemandPerRole) {
        this.allocations = allocations;
        this.workdayResults = workdayResults;
        this.nexus = nexus;
        this.mapper = mapper;
        this.enabled = enabled;
        this.baselineCapacity = Math.max(1, baselineCapacity);
        this.summaryDemandMultiplier = Math.max(1, summaryDemandMultiplier);
        this.maxSummaryDemandPerRole = Math.max(1, maxSummaryDemandPerRole);
    }

    @Transactional
    public WorkforceAllocationResponse assign(WorkforceAllocationRequest request) {
        WorkforceAllocationRequest safe = normalize(request);
        WorkforceAllocationEntity existing = allocations.findByIdempotencyKey(safe.idempotencyKey())
                .orElseGet(() -> allocations.findByEventId(safe.eventId()).orElse(null));
        if (existing != null) {
            return allocationResponse(existing, true);
        }
        WorkforceAllocationEntity sameRole = safe.workdayId() == null ? null
                : allocations.findByWorkdayIdAndRole(safe.workdayId(), safe.role()).orElse(null);
        if (sameRole != null) {
            return allocationResponse(sameRole, true);
        }

        int headcount = Math.max(0, safe.allocatedHeadcount() == null ? safe.assignedUnits() : safe.allocatedHeadcount());
        int capacityPerPerson = Math.max(0, safe.capacityPerPersonPerDay() == null
                ? defaultCapacityPerPerson(safe.role())
                : safe.capacityPerPersonPerDay());
        BigDecimal productivity = positiveDecimal(safe.productivityScore(), positiveDecimal(safe.skillLevel(), BigDecimal.ONE));
        BigDecimal wage = positiveDecimal(safe.wagePerDay(), positiveDecimal(safe.costPerUnitKrw(), BigDecimal.ZERO));
        int effectiveCapacity = effectiveCapacity(headcount, capacityPerPerson, productivity);
        WorkforceAllocationStatus status = safe.hopCount() > safe.maxHop()
                ? WorkforceAllocationStatus.REJECTED
                : WorkforceAllocationStatus.ACTIVE;
        String reason = status == WorkforceAllocationStatus.REJECTED ? "hopCount exceeds maxHop" : safe.reason();
        Instant now = Instant.now();

        WorkforceAllocationEntity saved = allocations.save(new WorkforceAllocationEntity(
                safe.eventId(),
                text(safe.payload() == null ? null : asString(safe.payload().get("allocationId")), safe.eventId()),
                safe.idempotencyKey(),
                safe.sourceService(),
                SERVICE_NAME,
                safe.role(),
                headcount,
                headcount,
                capacityPerPerson,
                productivity,
                productivity,
                wage,
                wage,
                effectiveCapacity,
                0,
                effectiveCapacity,
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
                now,
                now,
                now
        ));
        return allocationResponse(saved, false);
    }

    @Transactional
    public ProductionCapacityDecision processProductionRequest(Map<String, Object> payload) {
        int requested = positiveInt(payload.get("quantity"), 1);
        String orderId = text(asString(payload.get("orderId")), "ORDER-" + UUID.randomUUID());
        String workdayId = text(asString(payload.get("workdayId")), "NEXUS-WORKDAY-" + LocalDate.now());
        CapacityUse use = consume(WorkforceRole.PRODUCTION_OPERATOR, requested);
        int completed = use.used();
        int backlog = Math.max(0, requested - completed);
        String bottleneck = backlog > 0 ? WorkforceRole.PRODUCTION_OPERATOR.name() : bottleneckRole();
        BigDecimal productivity = requested == 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(completed).divide(BigDecimal.valueOf(requested), 4, RoundingMode.HALF_UP);

        Map<String, Object> workforcePayload = new LinkedHashMap<>();
        workforcePayload.put("workdayId", workdayId);
        workforcePayload.put("workforceAllocationId", use.allocationId());
        workforcePayload.put("productivityScore", productivity);
        workforcePayload.put("usedCapacity", completed);
        workforcePayload.put("remainingCapacity", use.remaining());
        workforcePayload.put("backlogCount", backlog);
        workforcePayload.put("bottleneckRole", bottleneck);
        workforcePayload.put("productionRequested", requested);
        workforcePayload.put("productionCompleted", completed);
        workforcePayload.put("payrollCost", payrollCost());
        workforcePayload.put("qualityRiskIncreased", roleRemaining(WorkforceRole.QUALITY_INSPECTOR) < completed);
        workforcePayload.put("maintenanceRiskIncreased", roleRemaining(WorkforceRole.MAINTENANCE_ENGINEER) <= 0);

        EventType eventType = backlog > 0 ? EventType.BACKLOG_INCREASED : EventType.PRODUCTION_COMPLETED;
        return new ProductionCapacityDecision(eventType, orderId, requested, completed, backlog, workforcePayload);
    }

    public WorkforceSummary workforceSummary() {
        CapacitySnapshot capacity = capacitySnapshot();
        ProductivitySummary productivity = productivitySummary();
        return new WorkforceSummary(
                enabled,
                SERVICE_NAME,
                baselineCapacity,
                activeWorkers(WorkforceRole.PRODUCTION_OPERATOR),
                activeWorkers(WorkforceRole.QUALITY_INSPECTOR),
                activeWorkers(WorkforceRole.MAINTENANCE_ENGINEER),
                activeWorkers(WorkforceRole.MATERIAL_HANDLER),
                activeWorkers(WorkforceRole.FACTORY_MANAGER),
                activeAllocations().stream().mapToInt(WorkforceAllocationEntity::allocatedHeadcount).sum(),
                payrollCost(),
                payrollCost(),
                capacity.totalCapacity(),
                capacity.usedCapacity(),
                Math.max(0, capacity.totalCapacity() - capacity.usedCapacity()),
                capacity.backlogUnits(),
                capacity.bottleneckRole(),
                productivity.lastProductivityRate(),
                allocations.count(),
                workdayResults.count(),
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
                snapshot.materialCapacity(),
                snapshot.managementCapacity(),
                snapshot.totalCapacity(),
                snapshot.demandUnits(),
                snapshot.usedCapacity(),
                Math.max(0, snapshot.totalCapacity() - snapshot.usedCapacity()),
                snapshot.backlogUnits(),
                snapshot.bottleneckRole(),
                Instant.now()
        );
    }

    public ProductivitySummary productivitySummary() {
        List<WorkdayResultEntity> recent = workdayResults.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50));
        if (recent.isEmpty()) {
            CapacitySnapshot snapshot = capacitySnapshot();
            return new ProductivitySummary(enabled, 0, snapshot.demandUnits(), snapshot.backlogUnits(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, snapshot.bottleneckRole(), 0, Instant.now());
        }
        WorkdayResultEntity last = recent.getFirst();
        BigDecimal average = recent.stream()
                .map(WorkdayResultEntity::productivityScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), 4, RoundingMode.HALF_UP);
        return new ProductivitySummary(enabled, last.usedCapacity(), last.productionRequested(), last.productionBacklog(),
                last.productivityScore(), average, last.payrollCost(), last.payrollCost(), last.bottleneckRole(),
                workdayResults.count(), Instant.now());
    }

    @Transactional
    public WorkdayRunResponse runWorkday(LocalDate requestedDate) {
        LocalDate workDate = requestedDate == null ? LocalDate.now() : requestedDate;
        String workdayId = "NEXUS-WORKDAY-" + workDate;
        WorkdayResultEntity existing = workdayResults.findByWorkdayId(workdayId).orElse(null);
        if (existing != null) {
            return workdayResponse(existing);
        }

        CapacitySnapshot capacity = capacitySnapshot();
        int productionRequested = capacity.productionDemand();
        int productionCompleted = Math.min(productionRequested, capacity.productionCapacity());
        int productionBacklog = Math.max(0, productionRequested - productionCompleted);
        int qualityChecked = Math.min(capacity.qualityDemand(), capacity.qualityCapacity());
        int qualityDefects = Math.max(0, capacity.qualityDemand() - capacity.qualityCapacity());
        int maintenanceCompleted = Math.min(capacity.maintenanceDemand(), capacity.maintenanceCapacity());
        int used = productionCompleted + qualityChecked + maintenanceCompleted;
        int total = capacity.totalCapacity();
        BigDecimal productivity = productionRequested == 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(productionCompleted).divide(BigDecimal.valueOf(productionRequested), 4, RoundingMode.HALF_UP);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("enabled", enabled);
        evidence.put("baselineCapacity", baselineCapacity);
        evidence.put("productionRequested", productionRequested);
        evidence.put("qualityDemand", capacity.qualityDemand());
        evidence.put("maintenanceDemand", capacity.maintenanceDemand());
        evidence.put("rawProductionDemand", nexus.productionOrders().size());
        evidence.put("rawQualityDemand", nexus.inspections().size());
        evidence.put("rawMaintenanceDemand", nexus.maintenanceEvents().size());
        evidence.put("summaryDemandMultiplier", summaryDemandMultiplier);
        evidence.put("maxSummaryDemandPerRole", maxSummaryDemandPerRole);
        evidence.put("bottleneckRole", capacity.bottleneckRole());

        WorkdayResultEntity saved = workdayResults.save(new WorkdayResultEntity(
                workdayId,
                workDate,
                total,
                used,
                Math.max(0, total - used),
                productionRequested,
                productionCompleted,
                productionBacklog,
                qualityChecked,
                qualityDefects,
                maintenanceCompleted,
                payrollCost(),
                productivity,
                capacity.bottleneckRole(),
                productionBacklog > 0 ? "BACKLOG_INCREASED" : "WORKDAY_COMPLETED",
                write(evidence),
                Instant.now()
        ));
        return workdayResponse(saved);
    }

    private WorkforceAllocationRequest normalize(WorkforceAllocationRequest request) {
        WorkforceAllocationRequest safe = request == null
                ? new WorkforceAllocationRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null)
                : request;
        String eventId = text(safe.eventId(), "WF-" + UUID.randomUUID());
        String idempotencyKey = text(safe.idempotencyKey(), "WF-IDEMP-" + UUID.randomUUID());
        String source = text(safe.sourceService(), "ArchiveOS");
        if (!ALLOWED_SOURCES.contains(source)) {
            source = "ArchiveOS";
        }
        WorkforceRole role = safe.role() == null ? WorkforceRole.PRODUCTION_OPERATOR : safe.role();
        return new WorkforceAllocationRequest(
                eventId,
                idempotencyKey,
                source,
                safe.eventType() == null ? WorkforceEventType.WORKFORCE_ALLOCATION_ASSIGNED : safe.eventType(),
                role,
                safe.allocatedHeadcount(),
                safe.assignedUnits() == null ? safe.allocatedHeadcount() : safe.assignedUnits(),
                safe.capacityPerPersonPerDay(),
                safe.productivityScore(),
                safe.skillLevel(),
                safe.wagePerDay(),
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
                entity.role(), entity.allocationId(), entity.targetService(), entity.allocatedHeadcount(),
                entity.assignedUnits(), entity.capacityPerPersonPerDay(), entity.productivityScore(),
                entity.skillLevel(), entity.wagePerDay(), entity.costPerUnitKrw(), entity.effectiveCapacity(),
                entity.usedCapacity(), entity.remainingCapacity(), entity.status(), duplicate, entity.reason(),
                entity.correlationId(), entity.createdAt());
    }

    private WorkdayRunResponse workdayResponse(WorkdayResultEntity entity) {
        return new WorkdayRunResponse(entity.workdayId(), entity.workDate(), entity.totalCapacity(),
                entity.usedCapacity(), entity.productionRequested(), entity.productionBacklog(), entity.payrollCost(),
                entity.payrollCost(), entity.productivityScore(), entity.productionRequested(), entity.productionCompleted(),
                entity.qualityChecked(), entity.qualityDefects(), entity.maintenanceCompleted(), entity.bottleneckRole(),
                entity.status(), read(entity.evidenceJson()), entity.createdAt());
    }

    private CapacityUse consume(WorkforceRole role, int requested) {
        if (!enabled) {
            int used = Math.min(requested, baselineCapacity);
            return new CapacityUse("BASELINE", used, Math.max(0, baselineCapacity - used));
        }
        int remaining = requested;
        String allocationId = null;
        int used = 0;
        for (WorkforceAllocationEntity allocation : allocations.findAllByRoleAndStatusOrderByCreatedAtAsc(role, WorkforceAllocationStatus.ACTIVE)) {
            if (remaining <= 0) {
                break;
            }
            int before = allocation.remainingCapacity();
            allocation.consumeCapacity(remaining, Instant.now());
            int consumed = before - allocation.remainingCapacity();
            if (consumed > 0 && allocationId == null) {
                allocationId = allocation.allocationId();
            }
            used += consumed;
            remaining -= consumed;
        }
        return new CapacityUse(allocationId == null ? "NONE" : allocationId, used, roleRemaining(role));
    }

    private CapacitySnapshot capacitySnapshot() {
        int production = enabled ? roleCapacity(WorkforceRole.PRODUCTION_OPERATOR) : baselineCapacity;
        int quality = enabled ? roleCapacity(WorkforceRole.QUALITY_INSPECTOR) : Math.max(1, baselineCapacity / 3);
        int maintenance = enabled ? roleCapacity(WorkforceRole.MAINTENANCE_ENGINEER) : Math.max(1, baselineCapacity / 4);
        int material = enabled ? roleCapacity(WorkforceRole.MATERIAL_HANDLER) : Math.max(1, baselineCapacity / 5);
        int management = enabled ? roleCapacity(WorkforceRole.FACTORY_MANAGER) : Math.max(1, baselineCapacity / 10);
        int used = activeAllocations().stream().mapToInt(WorkforceAllocationEntity::usedCapacity).sum();
        int total = production + quality + maintenance + material + management;
        int productionDemand = boundedOperationalDemand(nexus.productionOrders().size(), production, baselineCapacity);
        int qualityDemand = boundedOperationalDemand(nexus.inspections().size(), quality, Math.max(1, baselineCapacity / 3));
        int maintenanceDemand = boundedOperationalDemand(nexus.maintenanceEvents().size(), maintenance, Math.max(1, baselineCapacity / 4));
        int demand = productionDemand + qualityDemand + maintenanceDemand;
        int productionGap = Math.max(0, productionDemand - production);
        int qualityGap = Math.max(0, qualityDemand - quality);
        int maintenanceGap = Math.max(0, maintenanceDemand - maintenance);
        int backlog = productionGap + qualityGap + maintenanceGap;
        String bottleneck = bottleneckRole(productionGap, qualityGap, maintenanceGap);
        return new CapacitySnapshot(production, quality, maintenance, material, management, total, used,
                productionDemand, qualityDemand, maintenanceDemand, demand, backlog, bottleneck);
    }

    private int roleCapacity(WorkforceRole role) {
        int capacity = activeAllocations().stream()
                .filter(allocation -> allocation.role() == role)
                .mapToInt(WorkforceAllocationEntity::effectiveCapacity)
                .sum();
        return Math.max(0, capacity);
    }

    private int roleRemaining(WorkforceRole role) {
        if (!enabled) {
            return baselineCapacity;
        }
        return activeAllocations().stream()
                .filter(allocation -> allocation.role() == role)
                .mapToInt(WorkforceAllocationEntity::remainingCapacity)
                .sum();
    }

    private int defaultCapacityPerPerson(WorkforceRole role) {
        return switch (role) {
            case PRODUCTION_OPERATOR -> 20;
            case QUALITY_INSPECTOR -> 30;
            case MAINTENANCE_ENGINEER -> 5;
            case MATERIAL_HANDLER -> 25;
            case FACTORY_MANAGER -> 80;
        };
    }

    private int effectiveCapacity(int headcount, int capacityPerPerson, BigDecimal productivity) {
        return productivity.multiply(BigDecimal.valueOf(headcount)).multiply(BigDecimal.valueOf(capacityPerPerson))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int boundedOperationalDemand(int rawDemand, int roleCapacity, int fallbackCapacity) {
        int capacityBasis = Math.max(1, roleCapacity > 0 ? roleCapacity : fallbackCapacity);
        int window = Math.min(maxSummaryDemandPerRole, capacityBasis * summaryDemandMultiplier);
        return Math.min(Math.max(0, rawDemand), window);
    }

    private String bottleneckRole() {
        return capacitySnapshot().bottleneckRole();
    }

    private String bottleneckRole(int productionGap, int qualityGap, int maintenanceGap) {
        if (productionGap >= qualityGap && productionGap >= maintenanceGap && productionGap > 0) {
            return WorkforceRole.PRODUCTION_OPERATOR.name();
        }
        if (qualityGap >= maintenanceGap && qualityGap > 0) {
            return WorkforceRole.QUALITY_INSPECTOR.name();
        }
        if (maintenanceGap > 0) {
            return WorkforceRole.MAINTENANCE_ENGINEER.name();
        }
        return "NONE";
    }

    private int activeWorkers(WorkforceRole role) {
        return activeAllocations().stream()
                .filter(allocation -> allocation.role() == role)
                .mapToInt(WorkforceAllocationEntity::allocatedHeadcount)
                .sum();
    }

    private BigDecimal payrollCost() {
        return activeAllocations().stream()
                .map(allocation -> allocation.wagePerDay().multiply(BigDecimal.valueOf(allocation.allocatedHeadcount())))
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

    private int positiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return value == null ? fallback : Math.max(0, Integer.parseInt(value.toString()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public record ProductionCapacityDecision(
            EventType eventType,
            String aggregateId,
            int requestedQuantity,
            int completedQuantity,
            int backlogQuantity,
            Map<String, Object> workforcePayload
    ) {
    }

    private record CapacityUse(String allocationId, int used, int remaining) {
    }

    private record CapacitySnapshot(
            int productionCapacity,
            int qualityCapacity,
            int maintenanceCapacity,
            int materialCapacity,
            int managementCapacity,
            int totalCapacity,
            int usedCapacity,
            int productionDemand,
            int qualityDemand,
            int maintenanceDemand,
            int demandUnits,
            int backlogUnits,
            String bottleneckRole
    ) {
    }
}
