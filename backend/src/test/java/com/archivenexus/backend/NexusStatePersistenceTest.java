package com.archivenexus.backend;

import com.archivenexus.backend.archiveos.MockArchiveOsClient;
import com.archivenexus.backend.service.NexusStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NexusStatePersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void restoresSimulatorSnapshotFromStateFile() {
        Path stateFile = tempDir.resolve("archive-nexus-state.json");
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        NexusStateService firstService = new NexusStateService(new MockArchiveOsClient(), null, objectMapper, 20260626, true, stateFile);
        firstService.generateTick();
        long persistedTick = firstService.status().tick();
        firstService.shutdown();

        NexusStateService restoredService = new NexusStateService(new MockArchiveOsClient(), null, objectMapper, 20260626, true, stateFile);

        assertThat(restoredService.status().tick()).isEqualTo(persistedTick);
        assertThat(restoredService.factories()).hasSize(3);
        assertThat(restoredService.persistenceStatus().snapshotExists()).isTrue();

        restoredService.shutdown();
    }
}
