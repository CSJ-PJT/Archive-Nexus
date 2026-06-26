package com.archivenexus.backend.service;

import com.archivenexus.backend.archiveos.MockArchiveOsClient;
import com.archivenexus.backend.domain.DomainModels.AlertSeverity;
import com.archivenexus.backend.domain.DomainModels.ArchiveOsInteraction;
import com.archivenexus.backend.domain.DomainModels.BatchSnapshot;
import com.archivenexus.backend.domain.DomainModels.Factory;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.FactoryKind;
import com.archivenexus.backend.domain.DomainModels.InventoryItem;
import com.archivenexus.backend.domain.DomainModels.InventoryTransaction;
import com.archivenexus.backend.domain.DomainModels.LogisticsShipment;
import com.archivenexus.backend.domain.DomainModels.Lot;
import com.archivenexus.backend.domain.DomainModels.Machine;
import com.archivenexus.backend.domain.DomainModels.MaintenanceEvent;
import com.archivenexus.backend.domain.DomainModels.Overview;
import com.archivenexus.backend.domain.DomainModels.ProductionLine;
import com.archivenexus.backend.domain.DomainModels.ProductionOrder;
import com.archivenexus.backend.domain.DomainModels.QualityInspection;
import com.archivenexus.backend.domain.DomainModels.RpaTask;
import com.archivenexus.backend.domain.DomainModels.RpaTaskStatus;
import com.archivenexus.backend.domain.DomainModels.SensorMetric;
import com.archivenexus.backend.domain.DomainModels.SimulatorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class NexusStateService {
    private final MockArchiveOsClient archiveOsClient;
    private final Random random;
    private final ExecutorService factoryExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tick = new AtomicLong(0);
    private final AtomicInteger lastParallelWorkerCount = new AtomicInteger(0);

    private final List<Factory> factories = new ArrayList<>();
    private final List<SensorMetric> sensorMetrics = new CopyOnWriteArrayList<>();
    private final List<ProductionOrder> productionOrders = new CopyOnWriteArrayList<>();
    private final List<Lot> lots = new CopyOnWriteArrayList<>();
    private final List<QualityInspection> inspections = new CopyOnWriteArrayList<>();
    private final List<InventoryItem> inventoryItems = new CopyOnWriteArrayList<>();
    private final List<InventoryTransaction> inventoryTransactions = new CopyOnWriteArrayList<>();
    private final List<LogisticsShipment> shipments = new CopyOnWriteArrayList<>();
    private final List<MaintenanceEvent> maintenanceEvents = new CopyOnWriteArrayList<>();
    private final List<FactoryAlert> alerts = new CopyOnWriteArrayList<>();
    private final List<RpaTask> rpaTasks = new CopyOnWriteArrayList<>();
    private final List<BatchSnapshot> batchSnapshots = new CopyOnWriteArrayList<>();

    public NexusStateService(MockArchiveOsClient archiveOsClient, @Value("${archive-nexus.simulator.seed}") long seed) {
        this.archiveOsClient = archiveOsClient;
        this.random = new Random(seed);
        this.factoryExecutor = Executors.newFixedThreadPool(Math.max(3, Runtime.getRuntime().availableProcessors()));
        seedFactories();
        seedInventory();
        generateTick();
    }

    public SimulatorStatus start() {
        running.set(true);
        return status();
    }

    public SimulatorStatus stop() {
        running.set(false);
        return status();
    }

    public SimulatorStatus status() {
        return new SimulatorStatus(running.get(), tick.get(), factories.size(), alerts.size(), rpaTasks.size(), lastParallelWorkerCount.get(), Instant.now());
    }

    @PreDestroy
    public void shutdown() {
        factoryExecutor.shutdownNow();
    }

    @Scheduled(fixedDelayString = "${archive-nexus.simulator.tick-delay-ms}")
    public void scheduledTick() {
        if (running.get()) {
            generateTick();
        }
    }

    public void generateTick() {
        long currentTick = tick.incrementAndGet();
        List<CompletableFuture<Void>> futures = factories.stream()
                .map(factory -> CompletableFuture.runAsync(() -> generateFactoryTick(factory, currentTick), factoryExecutor))
                .toList();
        futures.forEach(CompletableFuture::join);
        lastParallelWorkerCount.set(futures.size());
        if (currentTick % 5 == 0) {
            runBatchSnapshot(currentTick);
        }
    }

    public Overview overview() {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("productionOrders", productionOrders.size());
        kpis.put("qualityInspections", inspections.size());
        kpis.put("inventoryTransactions", inventoryTransactions.size());
        kpis.put("logisticsShipments", shipments.size());
        kpis.put("maintenanceEvents", maintenanceEvents.size());
        kpis.put("parallelWorkerCount", lastParallelWorkerCount.get());
        return new Overview(status(), factories, recentAlerts(), pendingRpaTasks(), kpis);
    }

    public List<Factory> factories() {
        return factories;
    }

    public Optional<Factory> factory(String id) {
        return factories.stream().filter(factory -> factory.id().equals(id)).findFirst();
    }

    public List<ProductionLine> lines(String factoryId) {
        return factory(factoryId).map(Factory::lines).orElse(List.of());
    }

    public List<SensorMetric> metrics(String factoryId) {
        return sensorMetrics.stream().filter(metric -> metric.factoryId().equals(factoryId)).toList();
    }

    public List<FactoryAlert> alerts(String factoryId) {
        return alerts.stream().filter(alert -> alert.factoryId().equals(factoryId)).toList();
    }

    public List<ProductionOrder> productionOrders() {
        return productionOrders;
    }

    public List<QualityInspection> inspections() {
        return inspections;
    }

    public List<InventoryItem> inventoryItems() {
        return inventoryItems;
    }

    public List<InventoryTransaction> inventoryTransactions() {
        return inventoryTransactions;
    }

    public List<LogisticsShipment> shipments() {
        return shipments;
    }

    public List<MaintenanceEvent> maintenanceEvents() {
        return maintenanceEvents;
    }

    public List<RpaTask> rpaTasks() {
        return rpaTasks;
    }

    public List<BatchSnapshot> batchSnapshots() {
        return batchSnapshots;
    }

    public List<ArchiveOsInteraction> archiveOsInteractions() {
        return archiveOsClient.interactions();
    }

    public Optional<RpaTask> rpaTask(String id) {
        return rpaTasks.stream().filter(task -> task.id().equals(id)).findFirst();
    }

    public Optional<RpaTask> approve(String id) {
        return transitionRpa(id, RpaTaskStatus.APPROVED);
    }

    public Optional<RpaTask> reject(String id) {
        return transitionRpa(id, RpaTaskStatus.REJECTED);
    }

    private Optional<RpaTask> transitionRpa(String id, RpaTaskStatus status) {
        Optional<RpaTask> current = rpaTask(id);
        current.ifPresent(task -> {
            rpaTasks.remove(task);
            rpaTasks.add(new RpaTask(task.id(), task.factoryId(), status, task.trigger(), task.recommendation(), task.approvalRequired(), task.createdAt()));
        });
        return rpaTask(id);
    }

    private void generateFactoryTick(Factory factory, long currentTick) {
        double scenarioPressure = switch (factory.kind()) {
            case AUTOMOTIVE_PARTS -> 0.8;
            case BATTERY_MODULE -> 1.55;
            case ELECTRONICS -> 1.2;
        };
        ProductionLine line = factory.lines().get(0);
        Machine machine = line.machines().get(0);

        double vibration = round(randomRange(0.12, 0.42) * scenarioPressure + anomalySpike(0.18));
        double temperature = round(randomRange(61, 76) * scenarioPressure + anomalySpike(8));
        double current = round(randomRange(8, 12) * scenarioPressure + anomalySpike(3));
        int target = switch (factory.kind()) {
            case AUTOMOTIVE_PARTS -> 520;
            case BATTERY_MODULE -> 360;
            case ELECTRONICS -> 430;
        };
        int produced = Math.max(0, (int) (target * randomRange(0.78, 1.05) - Math.max(0, temperature - 85) * 3));
        double defectRate = round(randomRange(0.003, 0.012) + Math.max(0, vibration - 0.7) * 0.045 + (factory.kind() == FactoryKind.ELECTRONICS ? randomRange(0, 0.018) : 0));

        SensorMetric metric = new SensorMetric(id("MET"), factory.id(), machine.id(), currentTick, Instant.now(), vibration, temperature, current);
        sensorMetrics.add(metric);
        productionOrders.add(new ProductionOrder(id("PO"), factory.id(), line.product(), target, produced, produced < target * 0.8 ? "DELAYED" : "RUNNING"));

        Lot lot = new Lot(id("LOT"), factory.id(), line.product(), produced, defectRate >= 0.03);
        lots.add(lot);
        inspections.add(new QualityInspection(id("QI"), lot.id(), factory.id(), defectRate, defectRate >= 0.03 ? "FAILED" : "PASSED"));

        InventoryItem item = inventoryItems.stream().filter(inventory -> inventory.id().startsWith(factory.id())).findFirst().orElseThrow();
        int consumed = Math.max(20, produced / 3);
        inventoryItems.remove(item);
        InventoryItem updatedItem = new InventoryItem(item.id(), item.name(), item.type(), Math.max(0, item.quantity() - consumed), item.safetyStock());
        inventoryItems.add(updatedItem);
        inventoryTransactions.add(new InventoryTransaction(id("ITX"), updatedItem.id(), factory.id(), "OUTBOUND", consumed, Instant.now()));

        String shipmentStatus = produced < target * 0.82 || randomChance(0.08) ? "DELAYED" : "IN_TRANSIT";
        shipments.add(new LogisticsShipment(id("SHP"), factory.id(), "Central Warehouse", shipmentStatus, shipmentStatus.equals("DELAYED") ? 1 : 3));

        if (vibration >= machine.vibrationThreshold() || temperature >= machine.temperatureThreshold() || current >= machine.currentThreshold()) {
            maintenanceEvents.add(new MaintenanceEvent(id("MNT"), factory.id(), machine.id(), AlertSeverity.CRITICAL, "설비 센서 임계치 초과", "OPEN"));
            raiseAlert(factory.id(), AlertSeverity.CRITICAL, "MAINTENANCE", "설비 진동/온도/전류 임계치 초과");
        }
        if (defectRate >= 0.03) {
            raiseAlert(factory.id(), AlertSeverity.CRITICAL, "QUALITY", "Lot 품질 검사 실패 및 출하 보류");
        }
        if (updatedItem.quantity() <= updatedItem.safetyStock()) {
            raiseAlert(factory.id(), AlertSeverity.WARNING, "INVENTORY", "안전재고 이하로 재고 감소");
        }
        if (shipmentStatus.equals("DELAYED")) {
            raiseAlert(factory.id(), AlertSeverity.WARNING, "LOGISTICS", "납기 지연 위험 발생");
        }
    }

    private void raiseAlert(String factoryId, AlertSeverity severity, String category, String message) {
        FactoryAlert alert = new FactoryAlert(id("ALT"), factoryId, severity, category, message, Instant.now());
        alerts.add(alert);
        archiveOsClient.sendEvent(alert);
        archiveOsClient.publishAlert(alert);
        List<String> rag = archiveOsClient.requestRagAnalysis(alert);
        String recommendation = recommendation(category, rag);
        boolean approvalRequired = archiveOsClient.requiresApproval(alert);
        RpaTask task = archiveOsClient.createRpaTask(alert, recommendation, approvalRequired);
        rpaTasks.add(task);
        if (approvalRequired) {
            archiveOsClient.requestApproval(task);
        }
    }

    private void runBatchSnapshot(long currentTick) {
        int totalProduced = productionOrders.stream().mapToInt(ProductionOrder::producedQuantity).sum();
        double averageDefectRate = inspections.stream()
                .mapToDouble(QualityInspection::defectRate)
                .average()
                .orElse(0);
        int pendingApprovalCount = pendingRpaTasks().size();
        batchSnapshots.add(new BatchSnapshot(
                currentTick,
                factories.size(),
                productionOrders.size(),
                totalProduced,
                round(averageDefectRate),
                alerts.size(),
                pendingApprovalCount,
                Instant.now()
        ));
    }

    private List<RpaTask> pendingRpaTasks() {
        return rpaTasks.stream().filter(task -> task.status() == RpaTaskStatus.APPROVAL_REQUIRED).toList();
    }

    private String recommendation(String category, List<String> rag) {
        String source = rag.isEmpty() ? "운영 기준" : rag.get(0);
        return switch (category) {
            case "QUALITY" -> "출하 보류 후 재검사와 재작업 지시를 생성한다. 근거: " + source;
            case "MAINTENANCE" -> "라인 속도를 제한하고 긴급 정비 작업을 생성한다. 근거: " + source;
            case "INVENTORY" -> "긴급 보충 발주와 공장 간 재고 이동을 요청한다. 근거: " + source;
            case "LOGISTICS" -> "출하 우선순위를 재조정하고 대체 배송 경로를 요청한다. 근거: " + source;
            default -> "운영자 검토 작업을 생성한다. 근거: " + source;
        };
    }

    private List<FactoryAlert> recentAlerts() {
        return alerts.stream().sorted(Comparator.comparing(FactoryAlert::occurredAt).reversed()).limit(12).toList();
    }

    private void seedFactories() {
        factories.add(new Factory("FAC-A", "Factory A", FactoryKind.AUTOMOTIVE_PARTS, "자동차 부품 정상 생산 중심", List.of(
                new ProductionLine("LINE-A1", "A 조립 라인", "자동차 브라켓", List.of(new Machine("M-A1", "프레스 설비", 0.72, 86, 15)))
        )));
        factories.add(new Factory("FAC-B", "Factory B", FactoryKind.BATTERY_MODULE, "배터리 모듈 설비 이상 중심", List.of(
                new ProductionLine("LINE-B1", "B 모듈 라인", "배터리 모듈", List.of(new Machine("M-B1", "셀 용접 설비", 0.7, 85, 14.5)))
        )));
        factories.add(new Factory("FAC-C", "Factory C", FactoryKind.ELECTRONICS, "전장 부품 Lot 품질 추적 중심", List.of(
                new ProductionLine("LINE-C1", "C 검사 라인", "전장 제어 보드", List.of(new Machine("M-C1", "광학 검사 설비", 0.74, 84, 14)))
        )));
    }

    private void seedInventory() {
        inventoryItems.add(new InventoryItem("FAC-A-RAW", "자동차 부품 원자재", "RAW", 2600, 900));
        inventoryItems.add(new InventoryItem("FAC-B-RAW", "배터리 셀 원자재", "RAW", 1800, 850));
        inventoryItems.add(new InventoryItem("FAC-C-RAW", "전장 부품 원자재", "RAW", 2100, 820));
    }

    private double anomalySpike(double max) {
        return randomChance(0.12) ? randomRange(max * 0.35, max) : 0;
    }

    private boolean randomChance(double probability) {
        synchronized (random) {
            return random.nextDouble() < probability;
        }
    }

    private double randomRange(double min, double max) {
        synchronized (random) {
            return min + (max - min) * random.nextDouble();
        }
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
