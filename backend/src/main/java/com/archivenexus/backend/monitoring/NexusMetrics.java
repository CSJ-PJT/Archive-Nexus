package com.archivenexus.backend.monitoring;

import com.archivenexus.backend.persistence.SimulatorStateStore;
import com.archivenexus.backend.service.NexusStateService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class NexusMetrics implements MeterBinder {
    private final NexusStateService nexus;
    private final SimulatorStateStore stateStore;

    public NexusMetrics(NexusStateService nexus, SimulatorStateStore stateStore) {
        this.nexus = nexus;
        this.stateStore = stateStore;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("archive.nexus.simulator.running", nexus, value -> value.status().running() ? 1 : 0)
                .description("Whether the factory simulator is running")
                .register(registry);
        Gauge.builder("archive.nexus.simulator.tick", nexus, value -> value.status().tick())
                .description("Current simulator tick")
                .register(registry);
        Gauge.builder("archive.nexus.factory.count", nexus, value -> value.factories().size())
                .description("Configured virtual factory count")
                .register(registry);
        Gauge.builder("archive.nexus.anomaly.count", nexus, NexusStateService::anomalyCount)
                .description("Detected manufacturing anomaly count")
                .register(registry);
        Gauge.builder("archive.nexus.rpa.task.count", nexus, value -> value.rpaTasks().size())
                .description("Generated RPA task count")
                .register(registry);
        Gauge.builder("archive.nexus.batch.snapshot.count", nexus, value -> value.batchSnapshots().size())
                .description("Created batch snapshot count")
                .register(registry);

        FunctionCounter.builder("archive.nexus.persistence.save", stateStore, SimulatorStateStore::successfulSaveCount)
                .description("Successful PostgreSQL simulator state saves")
                .register(registry);
        registerRestoreCounter(registry, "postgresql");
        registerRestoreCounter(registry, "file");
        registerRestoreCounter(registry, "seed");
    }

    private void registerRestoreCounter(MeterRegistry registry, String source) {
        FunctionCounter.builder("archive.nexus.restore.source", nexus, value -> value.restoreSourceCount(source))
                .tag("source", source)
                .description("Simulator state restores grouped by source")
                .register(registry);
    }
}
