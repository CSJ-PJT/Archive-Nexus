package com.archivenexus.backend.nexuseconomy;

import com.archivenexus.backend.audit.AuditService;
import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class NexusEconomyService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal INITIAL_SYNTHETIC_CASH = BigDecimal.valueOf(10_000_000);
    private static final Set<String> EXTERNAL_SOURCES = Set.of("Archive-Logistics", "Archive-Ledger");
    private static final Set<CostType> EXTERNAL_COST_TYPES = EnumSet.of(
            CostType.LOGISTICS_SERVICE_FEE_PAID,
            CostType.LOGISTICS_DAILY_SETTLEMENT_FEE_PAID,
            CostType.LEDGER_SETTLEMENT_AGENCY_FEE_PAID,
            CostType.LEDGER_RECONCILIATION_FEE_PAID
    );

    private final NexusRevenueEventRepository revenues;
    private final NexusCostEventRepository costs;
    private final NexusProfitSnapshotRepository snapshots;
    private final AuditService audit;

    public NexusEconomyService(NexusRevenueEventRepository revenues, NexusCostEventRepository costs,
                               NexusProfitSnapshotRepository snapshots, AuditService audit) {
        this.revenues = revenues; this.costs = costs; this.snapshots = snapshots; this.audit = audit;
    }

    @Transactional
    public void recordProductionRevenue(String productionOrderId, String factoryId, String product, long tick, int producedQuantity) {
        BigDecimal revenue = BigDecimal.valueOf(Math.max(0, producedQuantity)).multiply(BigDecimal.valueOf(12_000));
        recordRevenue("NEXUS-REV-PROD-" + productionOrderId, "NEXUS:PRODUCTION_REVENUE:" + productionOrderId,
                null, "CYCLE-TICK-" + tick, "tick-" + tick, productionOrderId, 0, 8,
                RevenueType.PRODUCTION_REVENUE_RECOGNIZED, revenue, "KRW",
                "Synthetic production revenue recognized for " + product + " at " + factoryId);
        BigDecimal operationCost = BigDecimal.valueOf(150_000L + Math.max(0, producedQuantity) * 100L);
        recordCost("NEXUS-COST-OPS-" + productionOrderId, "NEXUS:OPERATION_COST:" + productionOrderId,
                null, "CYCLE-TICK-" + tick, "tick-" + tick, productionOrderId, 0, 8,
                "Archive-Nexus", CostType.NEXUS_OPERATION_COST_INCURRED, operationCost, "KRW",
                "Synthetic factory operation cost for production tick");
    }

    @Transactional
    public void recordMaterialCost(String inventoryTransactionId, long tick, BigDecimal amount) {
        recordCost("NEXUS-COST-MATERIAL-" + inventoryTransactionId, "NEXUS:MATERIAL_COST:" + inventoryTransactionId,
                null, "CYCLE-TICK-" + tick, "tick-" + tick, inventoryTransactionId, 0, 8,
                "Archive-Nexus", CostType.MATERIAL_COST_INCURRED, amount, "KRW",
                "Synthetic raw material cost incurred");
    }

    @Transactional
    public void recordShipmentRevenue(String shipmentId, long tick, int quantity, boolean delayed) {
        BigDecimal revenue = BigDecimal.valueOf(Math.max(1, quantity)).multiply(BigDecimal.valueOf(delayed ? 1_400 : 2_000));
        recordRevenue("NEXUS-REV-SHIP-" + shipmentId, "NEXUS:SHIPMENT_REVENUE:" + shipmentId,
                null, "CYCLE-TICK-" + tick, "tick-" + tick, shipmentId, 0, 8,
                RevenueType.SHIPMENT_REVENUE_RECOGNIZED, revenue, "KRW",
                delayed ? "Synthetic shipment revenue recognized with delay discount" : "Synthetic shipment revenue recognized");
    }

    @Transactional
    public void recordMaintenanceCost(String maintenanceEventId, long tick, BigDecimal amount) {
        recordCost("NEXUS-COST-MAINT-" + maintenanceEventId, "NEXUS:MAINTENANCE_COST:" + maintenanceEventId,
                null, "CYCLE-TICK-" + tick, "tick-" + tick, maintenanceEventId, 0, 8,
                "Archive-Nexus", CostType.MAINTENANCE_COST_INCURRED, amount, "KRW",
                "Synthetic maintenance cost incurred");
    }

    @Transactional
    public void recordQualityLoss(String inspectionId, long tick, BigDecimal amount) {
        recordCost("NEXUS-COST-QUALITY-" + inspectionId, "NEXUS:QUALITY_LOSS:" + inspectionId,
                null, "CYCLE-TICK-" + tick, "tick-" + tick, inspectionId, 0, 8,
                "Archive-Nexus", CostType.QUALITY_LOSS_INCURRED, amount, "KRW",
                "Synthetic quality loss incurred");
    }

    @Transactional
    public EconomyEventResponse receiveExternalCost(ExternalCostEventRequest request) {
        validateExternal(request);
        Optional<NexusCostEventEntity> duplicate = costs.findByEventId(request.eventId());
        if (duplicate.isEmpty()) duplicate = costs.findByIdempotencyKey(request.idempotencyKey());
        if (duplicate.isPresent()) return costResponse(duplicate.get(), true);
        NexusCostEventEntity saved = recordCost(request.eventId(), request.idempotencyKey(),
                request.simulationRunId(), request.settlementCycleId(), request.correlationId(), request.causationId(),
                safe(request.hopCount()), safeMaxHop(request.maxHop()), request.sourceService(), request.costType(),
                money(request.costAmount()), value(request.currency(), "KRW"), request.reason());
        audit.record(request.sourceService(), "NEXUS_EXTERNAL_FEE_RECEIVED", "Synthetic external fee event received by Nexus",
                null, request.correlationId(), null, Map.of("eventId", request.eventId(), "costType", request.costType().name(), "amount", money(request.costAmount())));
        return costResponse(saved, false);
    }

    @Transactional
    public ExternalCostEventBulkResponse receiveExternalCostBulk(ExternalCostEventBulkRequest request) {
        List<ExternalCostEventRequest> events = request == null || request.events() == null ? List.of() : request.events();
        List<EconomyEventResponse> results = new ArrayList<>(); int accepted = 0, duplicate = 0, rejected = 0;
        for (ExternalCostEventRequest event : events) {
            try {
                EconomyEventResponse result = receiveExternalCost(event); results.add(result);
                if (result.duplicate()) duplicate++; else accepted++;
            } catch (RuntimeException error) { rejected++; }
        }
        return new ExternalCostEventBulkResponse(events.size(), accepted, duplicate, rejected, results);
    }

    @Transactional(readOnly = true)
    public NexusEconomySummary summary() {
        BigDecimal totalRevenue = zero(revenues.sumRevenue());
        BigDecimal totalCost = zero(costs.sumCost());
        BigDecimal profit = totalRevenue.subtract(totalCost);
        BigDecimal cash = INITIAL_SYNTHETIC_CASH.add(profit);
        return new NexusEconomySummary(totalRevenue, totalCost, profit, cash, risk(cash, profit),
                revenues.count(), costs.count(), revenueByType(), costByType(), "/api/nexus-economy/daily-close?date=YYYY-MM-DD");
    }

    @Transactional(readOnly = true)
    public List<RevenueEventView> revenueEvents(int limit) {
        return revenues.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit(limit))).stream().map(this::revenueView).toList();
    }

    @Transactional(readOnly = true)
    public List<CostEventView> costEvents(int limit) {
        return costs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit(limit))).stream().map(this::costView).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfitSnapshotView> profitSnapshots(int limit) {
        return snapshots.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit(limit))).stream().map(this::snapshotView).toList();
    }

    @Transactional
    public ProfitSnapshotView dailyClose(LocalDate date) {
        LocalDate safeDate = date == null ? LocalDate.now() : date;
        Instant from = safeDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = safeDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal revenue = zero(revenues.sumRevenueBetween(from, to));
        BigDecimal cost = zero(costs.sumCostBetween(from, to));
        BigDecimal profit = revenue.subtract(cost);
        BigDecimal cash = INITIAL_SYNTHETIC_CASH.add(summary().profit());
        NexusProfitSnapshotEntity saved = snapshots.save(new NexusProfitSnapshotEntity(
                "NEXUS-PROFIT-" + safeDate + "-" + UUID.randomUUID(), safeDate, revenue, cost, profit, cash, risk(cash, profit), Instant.now()
        ));
        return snapshotView(saved);
    }

    private NexusRevenueEventEntity recordRevenue(String eventId, String idempotencyKey, String simulationRunId, String settlementCycleId,
                                                  String correlationId, String causationId, int hopCount, int maxHop, RevenueType type,
                                                  BigDecimal amount, String currency, String reason) {
        if (revenues.findByEventId(eventId).isPresent() || revenues.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return revenues.findByEventId(eventId).orElseGet(() -> revenues.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        return revenues.save(new NexusRevenueEventEntity(eventId, idempotencyKey, simulationRunId, settlementCycleId,
                correlationId, causationId, hopCount, maxHop, type, money(amount), value(currency, "KRW"), reason, Instant.now()));
    }

    private NexusCostEventEntity recordCost(String eventId, String idempotencyKey, String simulationRunId, String settlementCycleId,
                                            String correlationId, String causationId, int hopCount, int maxHop, String sourceService,
                                            CostType type, BigDecimal amount, String currency, String reason) {
        if (costs.findByEventId(eventId).isPresent() || costs.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return costs.findByEventId(eventId).orElseGet(() -> costs.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        return costs.save(new NexusCostEventEntity(eventId, idempotencyKey, simulationRunId, settlementCycleId,
                correlationId, causationId, hopCount, maxHop, value(sourceService, "Archive-Nexus"), type, money(amount),
                value(currency, "KRW"), reason, Instant.now()));
    }

    private void validateExternal(ExternalCostEventRequest request) {
        if (request == null) throw bad("request body is required");
        if (blank(request.eventId())) throw bad("eventId is required");
        if (blank(request.idempotencyKey())) throw bad("idempotencyKey is required");
        if (!EXTERNAL_SOURCES.contains(request.sourceService())) throw bad("sourceService must be Archive-Logistics or Archive-Ledger");
        if (request.costType() == null || !EXTERNAL_COST_TYPES.contains(request.costType())) throw bad("unsupported external costType");
        if (request.sourceService().equals("Archive-Logistics") && !request.costType().name().startsWith("LOGISTICS_")) {
            throw bad("Archive-Logistics can only bill logistics fee cost types");
        }
        if (request.sourceService().equals("Archive-Ledger") && !request.costType().name().startsWith("LEDGER_")) {
            throw bad("Archive-Ledger can only bill ledger fee cost types");
        }
        if (money(request.costAmount()).signum() <= 0) throw bad("costAmount must be positive");
        if (safe(request.hopCount()) > safeMaxHop(request.maxHop())) throw bad("hopCount exceeds maxHop");
    }

    private Map<String, BigDecimal> revenueByType() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (RevenueType type : RevenueType.values()) values.put(type.name(), zero(revenues.sumRevenueByType(type)));
        return values;
    }
    private Map<String, BigDecimal> costByType() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (CostType type : CostType.values()) values.put(type.name(), zero(costs.sumCostByType(type)));
        return values;
    }
    private EconomyEventResponse costResponse(NexusCostEventEntity e, boolean duplicate) {
        return new EconomyEventResponse(e.eventId(), e.idempotencyKey(), e.simulationRunId(), e.settlementCycleId(),
                e.correlationId(), e.causationId(), e.hopCount(), e.maxHop(), duplicate, e.costType().name(),
                e.costAmount(), e.currency(), e.sourceService(), e.reason(), e.createdAt());
    }
    private RevenueEventView revenueView(NexusRevenueEventEntity e) {
        return new RevenueEventView(e.eventId(), e.idempotencyKey(), e.simulationRunId(), e.settlementCycleId(),
                e.correlationId(), e.causationId(), e.hopCount(), e.maxHop(), e.revenueType(), e.revenueAmount(),
                e.currency(), e.reason(), e.createdAt());
    }
    private CostEventView costView(NexusCostEventEntity e) {
        return new CostEventView(e.eventId(), e.idempotencyKey(), e.simulationRunId(), e.settlementCycleId(),
                e.correlationId(), e.causationId(), e.hopCount(), e.maxHop(), e.sourceService(), e.costType(),
                e.costAmount(), e.currency(), e.reason(), e.createdAt());
    }
    private ProfitSnapshotView snapshotView(NexusProfitSnapshotEntity e) {
        return new ProfitSnapshotView(e.snapshotId(), e.settlementDate(), e.revenueAmount(), e.costAmount(), e.profitAmount(), e.cashBalance(), e.bankruptcyRisk(), e.createdAt());
    }
    private BankruptcyRisk risk(BigDecimal cash, BigDecimal profit) {
        if (cash.signum() <= 0) return BankruptcyRisk.CRITICAL;
        if (profit.signum() >= 0) return BankruptcyRisk.LOW;
        BigDecimal runway = cash.divide(profit.abs().max(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        if (runway.compareTo(BigDecimal.valueOf(2)) <= 0) return BankruptcyRisk.CRITICAL;
        if (runway.compareTo(BigDecimal.valueOf(7)) <= 0) return BankruptcyRisk.WARNING;
        return BankruptcyRisk.LOW;
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String value(String value, String fallback) { return blank(value) ? fallback : value; }
    private int safe(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private int safeMaxHop(Integer value) { return value == null ? 8 : Math.max(1, value); }
    private int safeLimit(int limit) { return Math.max(1, Math.min(limit, 1000)); }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.max(ZERO); }
    private BigDecimal zero(BigDecimal value) { return value == null ? ZERO : value; }
}
