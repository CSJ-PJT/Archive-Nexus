package com.archivenexus.backend.persistence;

import com.archivenexus.backend.domain.DomainModels.ArchiveOsInteraction;
import com.archivenexus.backend.domain.DomainModels.BatchSnapshot;
import com.archivenexus.backend.domain.DomainModels.Factory;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.InventoryItem;
import com.archivenexus.backend.domain.DomainModels.InventoryTransaction;
import com.archivenexus.backend.domain.DomainModels.LogisticsShipment;
import com.archivenexus.backend.domain.DomainModels.Lot;
import com.archivenexus.backend.domain.DomainModels.MaintenanceEvent;
import com.archivenexus.backend.domain.DomainModels.NexusSnapshot;
import com.archivenexus.backend.domain.DomainModels.ProductionOrder;
import com.archivenexus.backend.domain.DomainModels.QualityInspection;
import com.archivenexus.backend.domain.DomainModels.RpaTask;
import com.archivenexus.backend.domain.DomainModels.SensorMetric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SimulatorStateStore {
    public static final String CURRENT_STATE_ID = "archive-nexus-runtime";

    private static final Logger log = LoggerFactory.getLogger(SimulatorStateStore.class);

    private final SimulatorStateRepository repository;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean dbAvailable = new AtomicBoolean(true);
    private final AtomicReference<Instant> lastSavedAt = new AtomicReference<>();

    public SimulatorStateStore(SimulatorStateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<NexusSnapshot> restore() {
        try {
            return repository.findById(CURRENT_STATE_ID).map(this::toSnapshot);
        } catch (DataAccessException | IllegalStateException cause) {
            dbAvailable.set(false);
            log.warn("PostgreSQL simulator state restore failed", cause);
            return Optional.empty();
        }
    }

    public boolean save(NexusSnapshot snapshot) {
        try {
            repository.save(toEntity(snapshot));
            dbAvailable.set(true);
            lastSavedAt.set(snapshot.persistedAt());
            return true;
        } catch (DataAccessException | JsonProcessingException cause) {
            dbAvailable.set(false);
            log.warn("PostgreSQL simulator state save failed", cause);
            return false;
        }
    }

    public boolean dbAvailable() {
        return dbAvailable.get();
    }

    public Instant lastSavedAt() {
        return lastSavedAt.get();
    }

    private SimulatorStateEntity toEntity(NexusSnapshot snapshot) throws JsonProcessingException {
        SimulatorStateEntity entity = new SimulatorStateEntity(CURRENT_STATE_ID);
        entity.running(snapshot.running());
        entity.tick(snapshot.tick());
        entity.lastParallelWorkerCount(snapshot.lastParallelWorkerCount());
        entity.factoriesJson(write(snapshot.factories()));
        entity.sensorMetricsJson(write(snapshot.sensorMetrics()));
        entity.productionOrdersJson(write(snapshot.productionOrders()));
        entity.lotsJson(write(snapshot.lots()));
        entity.qualityInspectionsJson(write(snapshot.inspections()));
        entity.inventoryItemsJson(write(snapshot.inventoryItems()));
        entity.inventoryTransactionsJson(write(snapshot.inventoryTransactions()));
        entity.logisticsShipmentsJson(write(snapshot.shipments()));
        entity.maintenanceEventsJson(write(snapshot.maintenanceEvents()));
        entity.alertsJson(write(snapshot.alerts()));
        entity.rpaTasksJson(write(snapshot.rpaTasks()));
        entity.batchSnapshotsJson(write(snapshot.batchSnapshots()));
        entity.archiveOsInteractionsJson(write(snapshot.archiveOsInteractions()));
        entity.savedAt(snapshot.persistedAt());
        return entity;
    }

    private NexusSnapshot toSnapshot(SimulatorStateEntity entity) {
        try {
            Instant savedAt = entity.savedAt();
            lastSavedAt.set(savedAt);
            dbAvailable.set(true);
            return new NexusSnapshot(
                    entity.running(),
                    entity.tick(),
                    entity.lastParallelWorkerCount(),
                    read(entity.factoriesJson(), new TypeReference<>() {}),
                    read(entity.sensorMetricsJson(), new TypeReference<>() {}),
                    read(entity.productionOrdersJson(), new TypeReference<>() {}),
                    read(entity.lotsJson(), new TypeReference<>() {}),
                    read(entity.qualityInspectionsJson(), new TypeReference<>() {}),
                    read(entity.inventoryItemsJson(), new TypeReference<>() {}),
                    read(entity.inventoryTransactionsJson(), new TypeReference<>() {}),
                    read(entity.logisticsShipmentsJson(), new TypeReference<>() {}),
                    read(entity.maintenanceEventsJson(), new TypeReference<>() {}),
                    read(entity.alertsJson(), new TypeReference<>() {}),
                    read(entity.rpaTasksJson(), new TypeReference<>() {}),
                    read(entity.batchSnapshotsJson(), new TypeReference<>() {}),
                    read(entity.archiveOsInteractionsJson(), new TypeReference<>() {}),
                    savedAt
            );
        } catch (IOException cause) {
            dbAvailable.set(false);
            throw new IllegalStateException("Stored simulator state JSON is not readable", cause);
        }
    }

    private String write(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value == null ? List.of() : value);
    }

    private <T> T read(String json, TypeReference<T> type) throws IOException {
        String value = json == null || json.isBlank() ? "[]" : json;
        return objectMapper.readValue(value, type);
    }
}
