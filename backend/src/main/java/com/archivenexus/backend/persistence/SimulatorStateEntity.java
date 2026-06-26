package com.archivenexus.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "simulator_state")
public class SimulatorStateEntity {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false)
    private boolean running;

    @Column(nullable = false)
    private long tick;

    @Column(name = "last_parallel_worker_count", nullable = false)
    private int lastParallelWorkerCount;

    @Lob
    @Column(name = "factories_json", nullable = false)
    private String factoriesJson;

    @Lob
    @Column(name = "sensor_metrics_json", nullable = false)
    private String sensorMetricsJson;

    @Lob
    @Column(name = "production_orders_json", nullable = false)
    private String productionOrdersJson;

    @Lob
    @Column(name = "lots_json", nullable = false)
    private String lotsJson;

    @Lob
    @Column(name = "quality_inspections_json", nullable = false)
    private String qualityInspectionsJson;

    @Lob
    @Column(name = "inventory_items_json", nullable = false)
    private String inventoryItemsJson;

    @Lob
    @Column(name = "inventory_transactions_json", nullable = false)
    private String inventoryTransactionsJson;

    @Lob
    @Column(name = "logistics_shipments_json", nullable = false)
    private String logisticsShipmentsJson;

    @Lob
    @Column(name = "maintenance_events_json", nullable = false)
    private String maintenanceEventsJson;

    @Lob
    @Column(name = "alerts_json", nullable = false)
    private String alertsJson;

    @Lob
    @Column(name = "rpa_tasks_json", nullable = false)
    private String rpaTasksJson;

    @Lob
    @Column(name = "batch_snapshots_json", nullable = false)
    private String batchSnapshotsJson;

    @Lob
    @Column(name = "archiveos_interactions_json", nullable = false)
    private String archiveOsInteractionsJson;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected SimulatorStateEntity() {
    }

    public SimulatorStateEntity(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean running() {
        return running;
    }

    public void running(boolean running) {
        this.running = running;
    }

    public long tick() {
        return tick;
    }

    public void tick(long tick) {
        this.tick = tick;
    }

    public int lastParallelWorkerCount() {
        return lastParallelWorkerCount;
    }

    public void lastParallelWorkerCount(int lastParallelWorkerCount) {
        this.lastParallelWorkerCount = lastParallelWorkerCount;
    }

    public String factoriesJson() {
        return factoriesJson;
    }

    public void factoriesJson(String factoriesJson) {
        this.factoriesJson = factoriesJson;
    }

    public String sensorMetricsJson() {
        return sensorMetricsJson;
    }

    public void sensorMetricsJson(String sensorMetricsJson) {
        this.sensorMetricsJson = sensorMetricsJson;
    }

    public String productionOrdersJson() {
        return productionOrdersJson;
    }

    public void productionOrdersJson(String productionOrdersJson) {
        this.productionOrdersJson = productionOrdersJson;
    }

    public String lotsJson() {
        return lotsJson;
    }

    public void lotsJson(String lotsJson) {
        this.lotsJson = lotsJson;
    }

    public String qualityInspectionsJson() {
        return qualityInspectionsJson;
    }

    public void qualityInspectionsJson(String qualityInspectionsJson) {
        this.qualityInspectionsJson = qualityInspectionsJson;
    }

    public String inventoryItemsJson() {
        return inventoryItemsJson;
    }

    public void inventoryItemsJson(String inventoryItemsJson) {
        this.inventoryItemsJson = inventoryItemsJson;
    }

    public String inventoryTransactionsJson() {
        return inventoryTransactionsJson;
    }

    public void inventoryTransactionsJson(String inventoryTransactionsJson) {
        this.inventoryTransactionsJson = inventoryTransactionsJson;
    }

    public String logisticsShipmentsJson() {
        return logisticsShipmentsJson;
    }

    public void logisticsShipmentsJson(String logisticsShipmentsJson) {
        this.logisticsShipmentsJson = logisticsShipmentsJson;
    }

    public String maintenanceEventsJson() {
        return maintenanceEventsJson;
    }

    public void maintenanceEventsJson(String maintenanceEventsJson) {
        this.maintenanceEventsJson = maintenanceEventsJson;
    }

    public String alertsJson() {
        return alertsJson;
    }

    public void alertsJson(String alertsJson) {
        this.alertsJson = alertsJson;
    }

    public String rpaTasksJson() {
        return rpaTasksJson;
    }

    public void rpaTasksJson(String rpaTasksJson) {
        this.rpaTasksJson = rpaTasksJson;
    }

    public String batchSnapshotsJson() {
        return batchSnapshotsJson;
    }

    public void batchSnapshotsJson(String batchSnapshotsJson) {
        this.batchSnapshotsJson = batchSnapshotsJson;
    }

    public String archiveOsInteractionsJson() {
        return archiveOsInteractionsJson;
    }

    public void archiveOsInteractionsJson(String archiveOsInteractionsJson) {
        this.archiveOsInteractionsJson = archiveOsInteractionsJson;
    }

    public Instant savedAt() {
        return savedAt;
    }

    public void savedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }
}
