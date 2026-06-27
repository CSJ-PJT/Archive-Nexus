package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AiMetrics {
    private final MeterRegistry registry;
    private final Counter queryCounter;
    private final Counter agentExecutionCounter;
    private final Counter agentFailureCounter;
    private final Counter agentRpaCounter;
    private final Timer executionTimer;
    private final AtomicInteger runningAgents = new AtomicInteger();
    private final ConcurrentHashMap<Intent, Counter> routedIntentCounters = new ConcurrentHashMap<>();

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.queryCounter = registry.counter("archive.nexus.ai.query");
        this.agentExecutionCounter = registry.counter("archive.nexus.agent.execution");
        this.agentFailureCounter = registry.counter("archive.nexus.agent.failure");
        this.agentRpaCounter = registry.counter("archive.nexus.agent.rpa.task");
        this.executionTimer = registry.timer("archive.nexus.agent.execution.duration");
        for (Intent intent : Intent.values()) {
            routedIntentCounters.put(intent, registry.counter("archive.nexus.routed.intent", "intent", intent.name()));
        }
    }

    public void queryReceived() { queryCounter.increment(); }
    public void intentRouted(Intent intent) {
        routedIntentCounters.get(intent).increment();
    }
    public void agentStarted() { runningAgents.incrementAndGet(); }
    public void agentCompleted(Duration duration, boolean failed) {
        runningAgents.decrementAndGet();
        agentExecutionCounter.increment();
        executionTimer.record(duration);
        if (failed) agentFailureCounter.increment();
    }
    public void rpaTaskCreated() { agentRpaCounter.increment(); }
    public int runningAgents() { return runningAgents.get(); }
    public long agentFailures() { return (long) agentFailureCounter.count(); }
    public long agentRpaTasks() { return (long) agentRpaCounter.count(); }
}
