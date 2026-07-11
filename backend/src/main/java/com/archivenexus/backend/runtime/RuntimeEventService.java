package com.archivenexus.backend.runtime;

import com.archivenexus.backend.market.MarketInboundEventEntity;
import com.archivenexus.backend.market.MarketInboundEventRepository;
import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxSummary;
import com.archivenexus.backend.runtime.RuntimeEventModels.*;
import com.archivenexus.backend.workforce.WorkdayResultEntity;
import com.archivenexus.backend.workforce.WorkdayResultRepository;
import com.archivenexus.backend.workforce.WorkforceAllocationEntity;
import com.archivenexus.backend.workforce.WorkforceAllocationRepository;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceSummary;
import com.archivenexus.backend.workforce.WorkforceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RuntimeEventService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SERVICE_NAME = "Archive-Nexus";
    private static final String SERVICE_ROLE = "Manufacturing AX runtime, market inbound, workforce capacity, and outbox routing";

    private final OutboxEventService outbox;
    private final MarketInboundEventRepository marketEvents;
    private final WorkforceAllocationRepository workforceAllocations;
    private final WorkdayResultRepository workdayResults;
    private final WorkforceService workforce;
    private final RuntimeWorkLoopService runtimeWorkLoop;
    private final ObjectMapper mapper;

    public RuntimeEventService(OutboxEventService outbox,
                               MarketInboundEventRepository marketEvents,
                               WorkforceAllocationRepository workforceAllocations,
                               WorkdayResultRepository workdayResults,
                               WorkforceService workforce,
                               RuntimeWorkLoopService runtimeWorkLoop,
                               ObjectMapper mapper) {
        this.outbox = outbox;
        this.marketEvents = marketEvents;
        this.workforceAllocations = workforceAllocations;
        this.workdayResults = workdayResults;
        this.workforce = workforce;
        this.runtimeWorkLoop = runtimeWorkLoop;
        this.mapper = mapper;
    }

    public List<RuntimeEventResponse> recent(int limit) {
        return recent(limit, null);
    }

    /**
     * Cursor polling is pull-only. A cursor is the last event's
     * {@code occurredAtEpochMillis|eventId}; it never creates runtime work.
     */
    public List<RuntimeEventResponse> recent(int limit, String after) {
        int safeLimit = safeLimit(limit);
        List<RuntimeEventResponse> events = new ArrayList<>();
        outbox.events(Math.min(1000, Math.max(safeLimit, 100))).stream()
                .map(this::fromOutbox)
                .forEach(events::add);
        marketEvents.findAllByOrderByReceivedAtDesc(PageRequest.of(0, Math.min(1000, Math.max(safeLimit, 100)))).stream()
                .flatMap(event -> fromMarket(event).stream())
                .forEach(events::add);
        workforceAllocations.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(500, Math.max(safeLimit, 100)))).stream()
                .map(this::fromWorkforceAllocation)
                .forEach(events::add);
        workdayResults.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(500, Math.max(safeLimit, 100)))).stream()
                .flatMap(result -> fromWorkday(result).stream())
                .forEach(events::add);
        RuntimeCursor afterCursor = runtimeCursor(after);
        HashSet<String> seenEventIds = new HashSet<>();
        return events.stream()
                .sorted(Comparator.comparing(RuntimeEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .filter(event -> isAfterCursor(event, afterCursor))
                .filter(event -> seenEventIds.add(event.eventId()))
                .limit(safeLimit)
                .toList();
    }

    public List<RuntimeEventResponse> byCorrelation(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        String safeCorrelationId = correlationId.trim();
        List<RuntimeEventResponse> events = new ArrayList<>();
        marketEvents.findAllByCorrelationIdOrderByReceivedAtDesc(safeCorrelationId, PageRequest.of(0, 500)).stream()
                .flatMap(event -> fromMarket(event).stream())
                .forEach(events::add);
        outbox.events(1000).stream()
                .filter(event -> safeCorrelationId.equals(text(event.payload().get("correlationId"))))
                .map(this::fromOutbox)
                .forEach(events::add);
        workforceAllocations.findAllByCorrelationIdOrderByCreatedAtDesc(safeCorrelationId, PageRequest.of(0, 500)).stream()
                .map(this::fromWorkforceAllocation)
                .forEach(events::add);
        return events.stream()
                .sorted(Comparator.comparing(RuntimeEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public List<RuntimeEventResponse> byEntity(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return List.of();
        }
        String safeEntityId = entityId.trim();
        List<RuntimeEventResponse> events = new ArrayList<>();
        outbox.events(1000).stream()
                .filter(event -> safeEntityId.equals(event.aggregateId()) || safeEntityId.equals(entityIdFromPayload(event.payload())))
                .map(this::fromOutbox)
                .forEach(events::add);
        marketEvents.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 1000)).stream()
                .filter(event -> safeEntityId.equals(entityIdFromPayload(read(event.payloadJson()))))
                .flatMap(event -> fromMarket(event).stream())
                .forEach(events::add);
        workforceAllocations.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 1000)).stream()
                .filter(event -> safeEntityId.equals(event.allocationId()) || safeEntityId.equals(event.workdayId()))
                .map(this::fromWorkforceAllocation)
                .forEach(events::add);
        workdayResults.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 1000)).stream()
                .filter(result -> safeEntityId.equals(result.workdayId()))
                .flatMap(result -> fromWorkday(result).stream())
                .forEach(events::add);
        return events.stream()
                .sorted(Comparator.comparing(RuntimeEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public OperationsSummaryResponse operationsSummary() {
        OutboxSummary outboxSummary = outbox.summary();
        WorkforceSummary workforceSummary = workforce.workforceSummary();
        WorkdayResultEntity latestWorkday = workdayResults.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        Instant latestEventAt = recent(1).stream()
                .map(RuntimeEventResponse::occurredAt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        long failed = outboxSummary.failed();
        long retry = outboxSummary.pendingRetry();
        String status = failed > 0 || retry > 0 ? "DEGRADED" : "HEALTHY";
        String degradedReason = failed > 0
                ? "Outbox has failed events"
                : retry > 0 ? "Outbox has retrying events" : null;
        EconomyOperationsSummary economy = economySummary(workforceSummary, latestWorkday);
        return new OperationsSummaryResponse(
                SERVICE_NAME,
                SERVICE_ROLE,
                status,
                latestEventAt,
                latestWorkday == null ? 0 : latestWorkday.productionRequested(),
                latestWorkday == null ? 0 : latestWorkday.productionCompleted(),
                latestWorkday == null ? workforceSummary.backlog() : latestWorkday.productionBacklog(),
                latestWorkday == null ? 0 : latestWorkday.qualityDefects(),
                marketEvents.count(),
                new OutboxOperationsSummary(outboxSummary.pending(), outboxSummary.published(), failed, retry),
                economy,
                productionSummary(latestWorkday, workforceSummary, economy),
                new WorkforceOperationsSummary(
                        workforceSummary.totalActiveWorkers(),
                        workforceSummary.estimatedDailyCapacity(),
                        workforceSummary.usedCapacity(),
                        workforceSummary.backlog(),
                        workforceSummary.productivityRate(),
                        percent(workforceSummary.usedCapacity(), workforceSummary.estimatedDailyCapacity()),
                        workforceSummary.bottleneckRole()
                ),
                runtimeStatus(),
                degradedReason,
                true,
                List.of(
                        "/api/runtime-events/recent",
                        "/api/runtime-events/correlation/{correlationId}",
                        "/api/runtime-events/entity/{entityId}",
                        "/api/workforce/summary",
                        "/api/productivity/summary",
                        "/api/capacity/summary",
                        "/api/runtime/status",
                        "/api/outbox/summary",
                        "/api/integrations/summary",
                        "/api/operations/summary"
                ),
                Instant.now()
        );
    }

    public RuntimeStatusResponse runtimeStatus() {
        RuntimeStatusResponse current = runtimeWorkLoop.status();
        RuntimeEventResponse latest = recent(1).stream().findFirst().orElse(null);
        if (latest == null || latest.occurredAt() == null) {
            return current;
        }
        return new RuntimeStatusResponse(
                current.service(), current.runtimeActive(), current.autoRunEnabled(), current.schedulerStatus(),
                current.lastWorkAt(), latest.occurredAt(), current.eventsProducedLastTick(), current.eventsConsumedLastTick(),
                current.backlogCount(), current.oldestBacklogAgeSeconds(), cursorFor(latest), current.degradedReason(),
                current.pipelineStatus(), current.generatedAt());
    }

    /**
     * Synthetic operating balance derived only from persisted Nexus outbox and workforce data.
     * It is a runtime estimate, never a real financial statement or customer transaction record.
     */
    private EconomyOperationsSummary economySummary(WorkforceSummary workforceSummary, WorkdayResultEntity latestWorkday) {
        BigDecimal manufacturingRevenue = BigDecimal.ZERO;
        BigDecimal materialCost = BigDecimal.ZERO;
        BigDecimal maintenanceCost = BigDecimal.ZERO;
        BigDecimal qualityLossCost = BigDecimal.ZERO;
        BigDecimal logisticsFee = BigDecimal.ZERO;
        long productionEvents = 0;
        long maintenanceRequired = 0;
        long qualityDefects = 0;

        for (OutboxEventResponse event : outbox.events(1000)) {
            Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
            switch (event.eventType()) {
                case PRODUCTION_COMPLETED -> {
                    productionEvents++;
                    manufacturingRevenue = manufacturingRevenue.add(money(payload.get("totalAmount"),
                            BigDecimal.valueOf(number(payload.get("productionCompleted"), number(payload.get("quantity"), 0)))
                                    .multiply(BigDecimal.valueOf(120_000))));
                }
                case MATERIAL_CONSUMED -> materialCost = materialCost.add(money(payload.get("estimatedCost"),
                        BigDecimal.valueOf(number(payload.get("materialConsumed"), number(payload.get("quantity"), 0)))
                                .multiply(BigDecimal.valueOf(950))));
                case MAINTENANCE_COMPLETED -> maintenanceCost = maintenanceCost.add(money(payload.get("estimatedCost"), BigDecimal.valueOf(350_000)));
                case MAINTENANCE_REQUIRED -> maintenanceRequired++;
                case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> {
                    qualityDefects++;
                    qualityLossCost = qualityLossCost.add(money(payload.get("estimatedCost"),
                            BigDecimal.valueOf(number(payload.get("qualityDefects"), 1)).multiply(BigDecimal.valueOf(25_000))));
                }
                case LOGISTICS_DISPATCHED -> logisticsFee = logisticsFee.add(money(payload.get("estimatedCost"),
                        BigDecimal.valueOf(number(payload.get("quantity"), 0)).multiply(BigDecimal.valueOf(2_500))));
                default -> { }
            }
        }
        boolean available = productionEvents > 0 || latestWorkday != null;
        if (!available) {
            return new EconomyOperationsSummary(null, null, null, "NO_DATA",
                    null, null, null, null, null, null, null, null, null, null, null, 0,
                    false, "No persisted synthetic manufacturing work has been completed yet", null);
        }
        BigDecimal workforceCost = workforceSummary.payrollCost();
        BigDecimal totalCost = materialCost.add(maintenanceCost).add(qualityLossCost).add(logisticsFee).add(workforceCost);
        BigDecimal operatingProfit = manufacturingRevenue.subtract(totalCost);
        int requested = latestWorkday == null ? 0 : latestWorkday.productionRequested();
        int defects = latestWorkday == null ? (int) qualityDefects : latestWorkday.qualityDefects();
        BigDecimal qualityDefectRate = percent(defects, Math.max(1, requested));
        BigDecimal downtimeRate = percent(maintenanceRequired, Math.max(1L, productionEvents + maintenanceRequired));
        return new EconomyOperationsSummary(
                manufacturingRevenue, totalCost, operatingProfit,
                "SYNTHETIC_RUNTIME_ESTIMATE",
                manufacturingRevenue, materialCost, maintenanceCost, qualityLossCost, logisticsFee, workforceCost,
                operatingProfit, percent(operatingProfit, manufacturingRevenue),
                operatingProfit, qualityDefectRate, downtimeRate,
                operatingProfit.signum() < 0 ? 1 : 0,
                true, null, totalCost
        );
    }

    private ProductionOperationsSummary productionSummary(WorkdayResultEntity latestWorkday,
                                                          WorkforceSummary workforceSummary,
                                                          EconomyOperationsSummary economy) {
        if (latestWorkday == null) {
            return new ProductionOperationsSummary(false, "No persisted synthetic workday result is available", null,
                    null, null, null, workforceSummary.bottleneckRole(), null, null);
        }
        return new ProductionOperationsSummary(true, null,
                latestWorkday.productionRequested(), latestWorkday.productionCompleted(), latestWorkday.productionBacklog(),
                percent(latestWorkday.usedCapacity(), latestWorkday.totalCapacity()), latestWorkday.bottleneckRole(),
                economy.qualityDefectRate(), economy.downtimeRate());
    }

    private RuntimeEventResponse fromOutbox(OutboxEventResponse event) {
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        String entityId = text(event.aggregateId(), entityIdFromPayload(payload));
        return new RuntimeEventResponse(
                event.eventId(),
                event.idempotencyKey(),
                SERVICE_NAME,
                event.targetService() == null ? null : event.targetService().name(),
                domainForOutbox(event),
                event.eventType().name(),
                entityTypeForAggregate(event.aggregateType()),
                entityId,
                text(payload.get("orderId")),
                text(payload.get("correlationId")),
                text(payload.get("causationId")),
                text(payload.get("simulationRunId")),
                text(payload.get("settlementCycleId")),
                text(payload.get("workdayId")),
                statusForOutbox(event.status()),
                severityForOutbox(event, payload),
                labelForOutbox(event),
                event.occurredAt(),
                number(payload.get("hopCount"), 0),
                number(payload.get("maxHop"), 5),
                metadataForOutbox(event, payload)
        );
    }

    private List<RuntimeEventResponse> fromMarket(MarketInboundEventEntity event) {
        Map<String, Object> payload = read(event.payloadJson());
        String entityId = entityIdFromPayload(payload);
        String projectedEventType = switch (event.eventType()) {
            case MARKET_ORDER_PLACED -> "MARKET_ORDER_RECEIVED";
            default -> event.eventType().name();
        };
        List<RuntimeEventResponse> projections = new ArrayList<>();
        projections.add(new RuntimeEventResponse(
                event.eventId(),
                event.idempotencyKey(),
                text(event.source(), "Archive-Market"),
                SERVICE_NAME,
                "market",
                projectedEventType,
                entityTypeForMarket(event.eventType().name()),
                entityId,
                text(payload.get("orderId")),
                event.correlationId(),
                event.causationId(),
                event.simulationRunId(),
                event.settlementCycleId(),
                text(payload.get("workdayId")),
                statusForMarket(event.processingStatus()),
                severityForMarket(event.processingStatus(), payload),
                projectedEventType + " was " + event.processingStatus().name().toLowerCase(),
                event.occurredAt(),
                event.hopCount(),
                event.maxHop(),
                metadataForMarket(event, payload)
        ));
        if ("PRODUCTION_REQUESTED".equals(event.eventType().name()) && event.processingStatus() == MarketEventStatus.PROCESSED) {
            projections.add(new RuntimeEventResponse(
                    event.eventId() + ":PRODUCTION_STARTED",
                    event.idempotencyKey() + ":PRODUCTION_STARTED",
                    SERVICE_NAME,
                    SERVICE_NAME,
                    "production",
                    "PRODUCTION_STARTED",
                    "production-order",
                    entityId,
                    text(payload.get("orderId")),
                    event.correlationId(),
                    event.causationId(),
                    event.simulationRunId(),
                    event.settlementCycleId(),
                    text(payload.get("workdayId")),
                    "PROCESSING",
                    "INFO",
                    "Production started from Market request",
                    event.occurredAt() == null ? event.receivedAt() : event.occurredAt().plusMillis(1),
                    event.hopCount(),
                    event.maxHop(),
                    metadataForMarket(event, payload)
            ));
        }
        return projections;
    }

    private RuntimeEventResponse fromWorkforceAllocation(WorkforceAllocationEntity event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "allocationId", event.allocationId());
        put(metadata, "role", event.roleType());
        put(metadata, "allocatedHeadcount", event.allocatedHeadcount());
        put(metadata, "effectiveCapacity", event.effectiveCapacity());
        put(metadata, "usedCapacity", event.usedCapacity());
        put(metadata, "remainingCapacity", event.remainingCapacity());
        put(metadata, "workdayId", event.workdayId());
        put(metadata, "simulationRunId", event.simulationRunId());
        put(metadata, "settlementCycleId", event.settlementCycleId());
        put(metadata, "reason", event.reason());
        boolean active = "ACTIVE".equals(event.status().name());
        return new RuntimeEventResponse(
                event.eventId(),
                event.idempotencyKey(),
                text(event.sourceService(), SERVICE_NAME),
                text(event.targetService(), SERVICE_NAME),
                "workforce",
                "WORKFORCE_ALLOCATION_ASSIGNED",
                "workforce-allocation",
                event.allocationId(),
                null,
                event.correlationId(),
                event.causationId(),
                event.simulationRunId(),
                event.settlementCycleId(),
                event.workdayId(),
                active ? "COMPLETED" : "FAILED",
                active ? "INFO" : "WARNING",
                "Workforce allocation assigned to " + event.roleType(),
                event.createdAt(),
                event.hopCount(),
                event.maxHop(),
                metadata
        );
    }

    private List<RuntimeEventResponse> fromWorkday(WorkdayResultEntity result) {
        Map<String, Object> evidence = read(result.evidenceJson());
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "workDate", result.workDate());
        put(metadata, "totalCapacity", result.totalCapacity());
        put(metadata, "usedCapacity", result.usedCapacity());
        put(metadata, "remainingCapacity", result.remainingCapacity());
        put(metadata, "productionRequested", result.productionRequested());
        put(metadata, "productionCompleted", result.productionCompleted());
        put(metadata, "productionBacklog", result.productionBacklog());
        put(metadata, "qualityDefects", result.qualityDefects());
        put(metadata, "maintenanceCompleted", result.maintenanceCompleted());
        put(metadata, "productivityScore", result.productivityScore());
        put(metadata, "bottleneckRole", result.bottleneckRole());
        List<RuntimeEventResponse> projections = new ArrayList<>();
        projections.add(new RuntimeEventResponse(
                result.workdayId() + ":WORKDAY_COMPLETED",
                result.workdayId() + ":WORKDAY_COMPLETED",
                SERVICE_NAME,
                SERVICE_NAME,
                "workforce",
                "WORKDAY_COMPLETED",
                "workday",
                result.workdayId(),
                null,
                text(evidence.get("correlationId")),
                text(evidence.get("causationId")),
                text(evidence.get("simulationRunId")),
                text(evidence.get("settlementCycleId")),
                result.workdayId(),
                "COMPLETED",
                result.productionBacklog() > 0 ? "WARNING" : "INFO",
                "Workday completed with " + result.productionCompleted() + " production units",
                result.createdAt(),
                number(evidence.get("hopCount"), 0),
                number(evidence.get("maxHop"), 5),
                metadata
        ));
        if (result.productionBacklog() > 0) {
            projections.add(new RuntimeEventResponse(
                    result.workdayId() + ":CAPACITY_SHORTAGE_DETECTED",
                    result.workdayId() + ":CAPACITY_SHORTAGE_DETECTED",
                    SERVICE_NAME,
                    SERVICE_NAME,
                    "workforce",
                    "CAPACITY_SHORTAGE_DETECTED",
                    "workday",
                    result.workdayId(),
                    null,
                    text(evidence.get("correlationId")),
                    text(evidence.get("causationId")),
                    text(evidence.get("simulationRunId")),
                    text(evidence.get("settlementCycleId")),
                    result.workdayId(),
                    "DELAYED",
                    "WARNING",
                    "Capacity shortage detected at " + result.bottleneckRole(),
                    result.createdAt().plusMillis(1),
                    number(evidence.get("hopCount"), 0),
                    number(evidence.get("maxHop"), 5),
                    metadata
            ));
        }
        return projections;
    }

    private Map<String, Object> metadataForOutbox(OutboxEventResponse event, Map<String, Object> payload) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "aggregateType", event.aggregateType());
        put(metadata, "aggregateId", event.aggregateId());
        put(metadata, "originSourceService", event.source());
        put(metadata, "targetService", event.targetService() == null ? null : event.targetService().name());
        put(metadata, "retryCount", event.retryCount());
        put(metadata, "lastError", event.lastError());
        put(metadata, "workdayId", payload.get("workdayId"));
        put(metadata, "workforceAllocationId", payload.get("workforceAllocationId"));
        put(metadata, "productivityScore", payload.get("productivityScore"));
        put(metadata, "usedCapacity", payload.get("usedCapacity"));
        put(metadata, "backlogCount", payload.get("backlogCount"));
        put(metadata, "factoryId", payload.get("factoryId"));
        put(metadata, "orderId", payload.get("orderId"));
        put(metadata, "shipmentId", payload.get("shipmentId"));
        put(metadata, "priority", payload.get("priority"));
        put(metadata, "simulationRunId", payload.get("simulationRunId"));
        put(metadata, "settlementCycleId", payload.get("settlementCycleId"));
        return metadata;
    }

    private Map<String, Object> metadataForMarket(MarketInboundEventEntity event, Map<String, Object> payload) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "idempotencyKey", event.idempotencyKey());
        put(metadata, "processingStatus", event.processingStatus().name());
        put(metadata, "outboxEventCount", event.outboxEventCount());
        put(metadata, "orderId", payload.get("orderId"));
        put(metadata, "shipmentId", payload.get("shipmentId"));
        put(metadata, "returnId", payload.get("returnId"));
        put(metadata, "claimId", payload.get("claimId"));
        put(metadata, "customerType", payload.get("customerType"));
        put(metadata, "riskLevel", payload.get("riskLevel"));
        put(metadata, "productType", payload.get("productType"));
        put(metadata, "quantity", payload.get("quantity"));
        put(metadata, "priority", payload.get("priority"));
        put(metadata, "simulationRunId", event.simulationRunId());
        put(metadata, "settlementCycleId", event.settlementCycleId());
        put(metadata, "reason", event.reason());
        return metadata;
    }

    private String domainForOutbox(OutboxEventResponse event) {
        if (event.eventType().name().contains("LOGISTICS") || event.eventType().name().contains("SHIPMENT")) {
            return "logistics";
        }
        if (event.eventType().name().contains("QUALITY")) {
            return "quality";
        }
        if (event.eventType().name().contains("MAINTENANCE")) {
            return "maintenance";
        }
        if ("Archive-Market".equals(event.source())) {
            return "market-to-manufacturing";
        }
        return "manufacturing";
    }

    private String entityTypeForAggregate(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return "manufacturing-event";
        }
        return aggregateType.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String entityTypeForMarket(String eventType) {
        if (eventType == null) {
            return "market-event";
        }
        if (eventType.contains("ORDER")) {
            return "order";
        }
        if (eventType.contains("SHIPMENT")) {
            return "shipment";
        }
        if (eventType.contains("RETURN")) {
            return "return";
        }
        if (eventType.contains("CLAIM")) {
            return "claim";
        }
        return "market-event";
    }

    private String statusForOutbox(OutboxStatus status) {
        if (status == null) {
            return "WAITING";
        }
        return switch (status) {
            case PENDING -> "WAITING";
            case PENDING_RETRY -> "DELAYED";
            case FAILED -> "FAILED";
            case SKIPPED -> "COMPLETED";
            case PUBLISHED -> "COMPLETED";
        };
    }

    private String statusForMarket(MarketEventStatus status) {
        if (status == null) {
            return "WAITING";
        }
        return switch (status) {
            case RECEIVED -> "CREATED";
            case PROCESSED, DUPLICATE -> "COMPLETED";
            case REJECTED, FAILED -> "FAILED";
        };
    }

    private String severityForOutbox(OutboxEventResponse event, Map<String, Object> payload) {
        if (event.status() == OutboxStatus.FAILED) {
            return "CRITICAL";
        }
        if (event.status() == OutboxStatus.PENDING_RETRY) {
            return "WARNING";
        }
        Object severity = payload.get("severity");
        if (severity != null && List.of("HIGH", "CRITICAL").contains(severity.toString().toUpperCase())) {
            return "WARNING";
        }
        return "INFO";
    }

    private String severityForMarket(MarketEventStatus status, Map<String, Object> payload) {
        if (status == MarketEventStatus.FAILED || status == MarketEventStatus.REJECTED) {
            return "WARNING";
        }
        Object risk = payload.get("riskLevel");
        if (risk != null && List.of("HIGH", "CRITICAL").contains(risk.toString().toUpperCase())) {
            return "WARNING";
        }
        return "INFO";
    }

    private String labelForOutbox(OutboxEventResponse event) {
        return event.eventType().name() + " routed to " + (event.targetService() == null ? "UNKNOWN" : event.targetService().name());
    }

    private String entityIdFromPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : List.of("orderId", "shipmentId", "returnId", "claimId", "factoryId", "equipmentId", "vendorId", "workdayId")) {
            Object value = payload.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BigDecimal money(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal amount) {
            return amount.max(BigDecimal.ZERO);
        }
        try {
            return value == null ? fallback : new BigDecimal(value.toString()).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return (numerator == null ? BigDecimal.ZERO : numerator)
                .divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(long numerator, long denominator) {
        return ratio(numerator, denominator).multiply(BigDecimal.valueOf(100));
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return ratio(numerator, denominator).multiply(BigDecimal.valueOf(100));
    }

    private static String cursorFor(RuntimeEventResponse event) {
        return event.occurredAt().toEpochMilli() + "|" + event.eventId();
    }

    private static boolean isAfterCursor(RuntimeEventResponse event, RuntimeCursor cursor) {
        if (cursor == null) {
            return true;
        }
        if (event.occurredAt() == null) {
            return false;
        }
        int timeComparison = event.occurredAt().compareTo(cursor.occurredAt());
        return timeComparison > 0 || (timeComparison == 0 && event.eventId().compareTo(cursor.eventId()) > 0);
    }

    private static RuntimeCursor runtimeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = cursor.trim().split("\\|", 2);
            return new RuntimeCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), parts.length == 2 ? parts[1] : "");
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record RuntimeCursor(Instant occurredAt, String eventId) {
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String text(Object value, String fallback) {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !value.toString().isBlank() && !looksSensitive(key)) {
            metadata.put(key, value);
        }
    }

    private static boolean looksSensitive(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("webhook")
                || lower.contains("private")
                || lower.contains("phone")
                || lower.contains("address")
                || lower.contains("cardnumber")
                || lower.contains("accountnumber");
    }
}
