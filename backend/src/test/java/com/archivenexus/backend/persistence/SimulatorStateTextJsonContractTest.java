package com.archivenexus.backend.persistence;

import com.archivenexus.backend.domain.DomainModels.NexusSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulatorStateTextJsonContractTest {
    private static final List<String> JSON_FIELDS = List.of(
            "factoriesJson", "sensorMetricsJson", "productionOrdersJson", "lotsJson",
            "qualityInspectionsJson", "inventoryItemsJson", "inventoryTransactionsJson",
            "logisticsShipmentsJson", "maintenanceEventsJson", "alertsJson", "rpaTasksJson",
            "batchSnapshotsJson", "archiveOsInteractionsJson"
    );

    @Test
    void storesEverySnapshotFieldAsTextWithoutLargeObjectMapping() throws Exception {
        for (String fieldName : JSON_FIELDS) {
            Field field = SimulatorStateEntity.class.getDeclaredField(fieldName);
            Column column = field.getAnnotation(Column.class);

            assertThat(field.getType()).isEqualTo(String.class);
            assertThat(field.getAnnotation(Lob.class)).isNull();
            assertThat(column).isNotNull();
            assertThat(column.columnDefinition()).isEqualTo("text");
        }
    }

    @Test
    void savesAndRestoresJsonSnapshotThroughStringFields() {
        SimulatorStateRepository repository = mock(SimulatorStateRepository.class);
        AtomicReference<SimulatorStateEntity> saved = new AtomicReference<>();
        when(repository.save(any(SimulatorStateEntity.class))).thenAnswer(invocation -> {
            SimulatorStateEntity entity = invocation.getArgument(0);
            saved.set(entity);
            return entity;
        });
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));

        SimulatorStateStore store = new SimulatorStateStore(repository,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        Instant persistedAt = Instant.parse("2026-08-12T00:00:00Z");
        NexusSnapshot snapshot = new NexusSnapshot(false, 12293L, 3,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), persistedAt);

        assertThat(store.save(snapshot)).isTrue();
        assertThat(saved.get().factoriesJson()).isEqualTo("[]");
        assertThat(saved.get().factoriesJson()).doesNotMatch("^\\d+$");

        NexusSnapshot restored = store.restore().orElseThrow();
        assertThat(restored.tick()).isEqualTo(12293L);
        assertThat(restored.running()).isFalse();
        assertThat(restored.factories()).isEmpty();
    }
}
