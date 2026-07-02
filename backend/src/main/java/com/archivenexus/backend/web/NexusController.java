package com.archivenexus.backend.web;

import com.archivenexus.backend.domain.DomainModels.Factory;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.ArchiveOsInteraction;
import com.archivenexus.backend.domain.DomainModels.BatchSnapshot;
import com.archivenexus.backend.domain.DomainModels.InventoryItem;
import com.archivenexus.backend.domain.DomainModels.InventoryTransaction;
import com.archivenexus.backend.domain.DomainModels.LogisticsShipment;
import com.archivenexus.backend.domain.DomainModels.MaintenanceEvent;
import com.archivenexus.backend.domain.DomainModels.Overview;
import com.archivenexus.backend.domain.DomainModels.ProductionLine;
import com.archivenexus.backend.domain.DomainModels.ProductionOrder;
import com.archivenexus.backend.domain.DomainModels.QualityInspection;
import com.archivenexus.backend.domain.DomainModels.RpaTask;
import com.archivenexus.backend.domain.DomainModels.SensorMetric;
import com.archivenexus.backend.domain.DomainModels.SimulatorPersistenceStatus;
import com.archivenexus.backend.domain.DomainModels.SimulatorStatus;
import com.archivenexus.backend.service.NexusStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NexusController {
    private static final Logger log = LoggerFactory.getLogger(NexusController.class);

    private final NexusStateService nexus;

    public NexusController(NexusStateService nexus) {
        this.nexus = nexus;
    }

    @GetMapping("/overview")
    Overview overview(@RequestParam(required = false) Integer pendingLimit) {
        Overview overview = nexus.overview();
        return new Overview(overview.simulator(), overview.factories(), overview.recentAlerts(),
                tail(overview.pendingRpaTasks(), pendingLimit), overview.kpis());
    }

    @GetMapping("/factories")
    List<Factory> factories() {
        return nexus.factories();
    }

    @GetMapping("/factories/{factoryId}")
    ResponseEntity<Factory> factory(@PathVariable String factoryId) {
        return ResponseEntity.of(nexus.factory(factoryId));
    }

    @GetMapping("/factories/{factoryId}/lines")
    List<ProductionLine> lines(@PathVariable String factoryId) {
        return nexus.lines(factoryId);
    }

    @GetMapping("/factories/{factoryId}/metrics")
    List<SensorMetric> metrics(@PathVariable String factoryId) {
        return nexus.metrics(factoryId);
    }

    @GetMapping("/factories/{factoryId}/alerts")
    List<FactoryAlert> alerts(@PathVariable String factoryId) {
        return nexus.alerts(factoryId);
    }

    @GetMapping("/production/orders")
    List<ProductionOrder> productionOrders(@RequestParam(required = false) Integer limit) {
        return tail(nexus.productionOrders(), limit);
    }

    @GetMapping("/quality/inspections")
    List<QualityInspection> inspections(@RequestParam(required = false) Integer limit) {
        return tail(nexus.inspections(), limit);
    }

    @GetMapping("/inventory/items")
    List<InventoryItem> inventoryItems() {
        return nexus.inventoryItems();
    }

    @GetMapping("/inventory/transactions")
    List<InventoryTransaction> inventoryTransactions(@RequestParam(required = false) Integer limit) {
        return tail(nexus.inventoryTransactions(), limit);
    }

    @GetMapping("/logistics/shipments")
    List<LogisticsShipment> shipments(@RequestParam(required = false) Integer limit) {
        return tail(nexus.shipments(), limit);
    }

    @GetMapping("/maintenance/events")
    List<MaintenanceEvent> maintenanceEvents(@RequestParam(required = false) Integer limit) {
        return tail(nexus.maintenanceEvents(), limit);
    }

    @GetMapping("/rpa/tasks")
    List<RpaTask> rpaTasks(@RequestParam(required = false) Integer limit) {
        return tail(nexus.rpaTasks(), limit);
    }

    @GetMapping("/rpa/tasks/{id}")
    ResponseEntity<RpaTask> rpaTask(@PathVariable String id) {
        return ResponseEntity.of(nexus.rpaTask(id));
    }

    @GetMapping("/batch/snapshots")
    List<BatchSnapshot> batchSnapshots(@RequestParam(required = false) Integer limit) {
        return tail(nexus.batchSnapshots(), limit);
    }

    @GetMapping("/archiveos/interactions")
    List<ArchiveOsInteraction> archiveOsInteractions(@RequestParam(required = false) Integer limit) {
        return tail(nexus.archiveOsInteractions(), limit);
    }

    @PostMapping("/rpa/tasks/{id}/approve")
    ResponseEntity<RpaTask> approve(@PathVariable String id) {
        return ResponseEntity.of(nexus.approve(id));
    }

    @PostMapping("/rpa/tasks/{id}/reject")
    ResponseEntity<RpaTask> reject(@PathVariable String id) {
        return ResponseEntity.of(nexus.reject(id));
    }

    @PostMapping("/simulator/start")
    SimulatorStatus start() {
        return timedControl("start", nexus::start);
    }

    @PostMapping("/simulator/stop")
    SimulatorStatus stop() {
        return timedControl("stop", nexus::stop);
    }

    @GetMapping("/simulator/status")
    SimulatorStatus simulatorStatus() {
        return nexus.status();
    }

    @GetMapping("/simulator/persistence")
    SimulatorPersistenceStatus simulatorPersistenceStatus() {
        return nexus.persistenceStatus();
    }

    private SimulatorStatus timedControl(String operation, java.util.function.Supplier<SimulatorStatus> action) {
        long startedAt = System.nanoTime();
        SimulatorStatus result = action.get();
        long totalMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Simulator HTTP control operation={} running={} totalMs={}", operation, result.running(), totalMs);
        return result;
    }

    private static <T> List<T> tail(List<T> values, Integer limit) {
        if (limit == null) {
            return values;
        }
        int safeLimit = Math.max(0, Math.min(limit, 1000));
        if (safeLimit == 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, values.size() - safeLimit);
        return List.copyOf(values.subList(fromIndex, values.size()));
    }
}
