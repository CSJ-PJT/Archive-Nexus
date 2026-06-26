package com.archivenexus.backend.web;

import com.archivenexus.backend.domain.DomainModels.Factory;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
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
import com.archivenexus.backend.domain.DomainModels.SimulatorStatus;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NexusController {
    private final NexusStateService nexus;

    public NexusController(NexusStateService nexus) {
        this.nexus = nexus;
    }

    @GetMapping("/overview")
    Overview overview() {
        return nexus.overview();
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
    List<ProductionOrder> productionOrders() {
        return nexus.productionOrders();
    }

    @GetMapping("/quality/inspections")
    List<QualityInspection> inspections() {
        return nexus.inspections();
    }

    @GetMapping("/inventory/items")
    List<InventoryItem> inventoryItems() {
        return nexus.inventoryItems();
    }

    @GetMapping("/inventory/transactions")
    List<InventoryTransaction> inventoryTransactions() {
        return nexus.inventoryTransactions();
    }

    @GetMapping("/logistics/shipments")
    List<LogisticsShipment> shipments() {
        return nexus.shipments();
    }

    @GetMapping("/maintenance/events")
    List<MaintenanceEvent> maintenanceEvents() {
        return nexus.maintenanceEvents();
    }

    @GetMapping("/rpa/tasks")
    List<RpaTask> rpaTasks() {
        return nexus.rpaTasks();
    }

    @GetMapping("/rpa/tasks/{id}")
    ResponseEntity<RpaTask> rpaTask(@PathVariable String id) {
        return ResponseEntity.of(nexus.rpaTask(id));
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
        return nexus.start();
    }

    @PostMapping("/simulator/stop")
    SimulatorStatus stop() {
        return nexus.stop();
    }

    @GetMapping("/simulator/status")
    SimulatorStatus simulatorStatus() {
        return nexus.status();
    }
}
