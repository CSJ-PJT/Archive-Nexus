package com.archivenexus.backend.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DomainModels {
    private DomainModels() {
    }

    public enum FactoryKind {
        AUTOMOTIVE_PARTS,
        BATTERY_MODULE,
        ELECTRONICS
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    public enum RpaTaskStatus {
        DETECTED,
        ANALYZING,
        RECOMMENDATION_READY,
        APPROVAL_REQUIRED,
        APPROVED,
        REJECTED,
        EXECUTING,
        COMPLETED,
        FAILED
    }

    public record Factory(String id, String name, FactoryKind kind, String scenario, List<ProductionLine> lines) {
    }

    public record ProductionLine(String id, String name, String product, List<Machine> machines) {
    }

    public record Machine(String id, String name, double vibrationThreshold, double temperatureThreshold, double currentThreshold) {
    }

    public record SensorMetric(String id, String factoryId, String machineId, long tick, Instant measuredAt, double vibration, double temperatureCelsius, double currentAmpere) {
    }

    public record ProductionOrder(String id, String factoryId, String product, int targetQuantity, int producedQuantity, String status) {
    }

    public record WorkOrder(String id, String productionOrderId, String lineId, String status) {
    }

    public record Lot(String id, String factoryId, String product, int quantity, boolean shipmentHold) {
    }

    public record QualityInspection(String id, String lotId, String factoryId, double defectRate, String result) {
    }

    public record DefectEvent(String id, String factoryId, String lotId, AlertSeverity severity, String reason) {
    }

    public record InventoryItem(String id, String name, String type, int quantity, int safetyStock) {
    }

    public record InventoryTransaction(String id, String itemId, String factoryId, String type, int quantity, Instant occurredAt) {
    }

    public record Warehouse(String id, String name, String location) {
    }

    public record LogisticsShipment(String id, String factoryId, String destination, String status, int priority) {
    }

    public record MaintenanceEvent(String id, String factoryId, String machineId, AlertSeverity severity, String cause, String status) {
    }

    public record FactoryAlert(String id, String factoryId, AlertSeverity severity, String category, String message, Instant occurredAt) {
    }

    public record RpaTask(String id, String factoryId, RpaTaskStatus status, String trigger, String recommendation, boolean approvalRequired, Instant createdAt) {
    }

    public record RpaDecision(String id, String rpaTaskId, String decision, String actor, Instant decidedAt) {
    }

    public record RpaExecutionLog(String id, String rpaTaskId, String message, Instant loggedAt) {
    }

    public record ArchiveOsInteraction(String id, String type, String factoryId, String payload, Instant occurredAt) {
    }

    public record BatchSnapshot(long tick, int factoryCount, int productionOrderCount, int totalProducedQuantity, double averageDefectRate, int alertCount, int pendingApprovalCount, Instant createdAt) {
    }

    public record NexusSnapshot(
            boolean running,
            long tick,
            int lastParallelWorkerCount,
            List<Factory> factories,
            List<SensorMetric> sensorMetrics,
            List<ProductionOrder> productionOrders,
            List<Lot> lots,
            List<QualityInspection> inspections,
            List<InventoryItem> inventoryItems,
            List<InventoryTransaction> inventoryTransactions,
            List<LogisticsShipment> shipments,
            List<MaintenanceEvent> maintenanceEvents,
            List<FactoryAlert> alerts,
            List<RpaTask> rpaTasks,
            List<BatchSnapshot> batchSnapshots,
            List<ArchiveOsInteraction> archiveOsInteractions,
            Instant persistedAt
    ) {
    }

    public record SimulatorPersistenceStatus(boolean enabled, String stateFile, boolean snapshotExists, Instant lastPersistedAt) {
    }

    public record SimulatorStatus(boolean running, long tick, int factoryCount, int alertCount, int rpaTaskCount, int parallelWorkerCount, Instant updatedAt) {
    }

    public record Overview(SimulatorStatus simulator, List<Factory> factories, List<FactoryAlert> recentAlerts, List<RpaTask> pendingRpaTasks, Map<String, Object> kpis) {
    }
}
