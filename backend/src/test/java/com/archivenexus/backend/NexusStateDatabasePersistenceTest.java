package com.archivenexus.backend;

import com.archivenexus.backend.archiveos.MockArchiveOsClient;
import com.archivenexus.backend.persistence.SimulatorStateRepository;
import com.archivenexus.backend.persistence.SimulatorStateStore;
import com.archivenexus.backend.service.NexusStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_db_persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false"
})
class NexusStateDatabasePersistenceTest {
    @Autowired
    SimulatorStateRepository repository;

    @Autowired
    ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @Test
    void restoresSimulatorSnapshotFromDatabaseBeforeFileSnapshot() {
        Path stateFile = tempDir.resolve("archive-nexus-state.json");
        SimulatorStateStore store = new SimulatorStateStore(repository, objectMapper);

        NexusStateService firstService = new NexusStateService(new MockArchiveOsClient(), store, objectMapper, 20260626, true, stateFile);
        firstService.generateTick();
        long databaseTick = firstService.status().tick();
        firstService.shutdown();

        NexusStateService restoredService = new NexusStateService(new MockArchiveOsClient(), store, objectMapper, 20260626, true, stateFile);

        assertThat(restoredService.status().tick()).isEqualTo(databaseTick);
        assertThat(restoredService.persistenceStatus().storageMode()).isEqualTo("postgresql");
        assertThat(restoredService.persistenceStatus().dbAvailable()).isTrue();
        assertThat(restoredService.persistenceStatus().restoredFrom()).isEqualTo("postgresql");
        assertThat(restoredService.persistenceStatus().fileSnapshotAvailable()).isTrue();

        restoredService.shutdown();
    }
}
