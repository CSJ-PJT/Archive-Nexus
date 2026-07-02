package com.archivenexus.backend;

import com.archivenexus.backend.archiveos.MockArchiveOsClient;
import com.archivenexus.backend.persistence.SimulatorStateEntity;
import com.archivenexus.backend.persistence.SimulatorControlStateEntity;
import com.archivenexus.backend.persistence.SimulatorControlStateRepository;
import com.archivenexus.backend.persistence.SimulatorStateRepository;
import com.archivenexus.backend.persistence.SimulatorStateStore;
import com.archivenexus.backend.service.NexusStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulatorControlLatencyTest {
    @TempDir
    Path tempDir;

    @Test
    void startAndStopPersistOnlyBoundedControlState() {
        SimulatorStateRepository repository = mock(SimulatorStateRepository.class);
        SimulatorControlStateRepository controlRepository = mock(SimulatorControlStateRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(SimulatorStateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(controlRepository.saveAndFlush(any(SimulatorControlStateEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SimulatorStateStore store = new SimulatorStateStore(repository, controlRepository, objectMapper);
        NexusStateService service = new NexusStateService(
                new MockArchiveOsClient(), store, objectMapper, 20260626, true,
                tempDir.resolve("archive-nexus-state.json")
        );
        clearInvocations(repository);
        clearInvocations(controlRepository);

        assertThat(service.start().running()).isTrue();
        assertThat(service.start().running()).isTrue();
        assertThat(service.stop().running()).isFalse();
        assertThat(service.stop().running()).isFalse();

        verify(repository, never()).save(any(SimulatorStateEntity.class));
        verify(controlRepository, org.mockito.Mockito.times(4))
                .saveAndFlush(any(SimulatorControlStateEntity.class));
        service.shutdown();
    }
}
