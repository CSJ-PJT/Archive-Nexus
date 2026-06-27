package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import com.archivenexus.backend.domain.DomainModels.Factory;
import com.archivenexus.backend.domain.DomainModels.Machine;
import com.archivenexus.backend.domain.DomainModels.MaintenanceEvent;
import com.archivenexus.backend.domain.DomainModels.SensorMetric;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MaintenanceAgent implements ManufacturingAgent {
    private final NexusStateService nexus;

    public MaintenanceAgent(NexusStateService nexus) {
        this.nexus = nexus;
    }

    @Override
    public String getName() {
        return "MaintenanceAgent";
    }

    @Override
    public boolean supports(Intent intent) {
        return intent == Intent.MAINTENANCE;
    }

    @Override
    public AgentResult analyze(AgentContext context) {
        long startedAt = System.nanoTime();
        List<SensorMetric> metrics = nexus.factories().stream()
                .filter(factory -> context.selectedFactoryId() == null || factory.id().equals(context.selectedFactoryId()))
                .flatMap(factory -> nexus.metrics(factory.id()).stream())
                .toList();
        if (metrics.isEmpty()) {
            return new AgentResult(getName(), Intent.MAINTENANCE, "판단할 설비 센서 데이터가 부족함", List.of(), List.of(), 0,
                    elapsed(startedAt), AgentExecutionStatus.INSUFFICIENT_DATA, null, false);
        }

        Map<String, Machine> machines = nexus.factories().stream()
                .flatMap(factory -> factory.lines().stream())
                .flatMap(line -> line.machines().stream())
                .collect(Collectors.toMap(Machine::id, Function.identity()));
        List<SensorMetric> latest = metrics.stream()
                .collect(Collectors.groupingBy(SensorMetric::machineId))
                .values().stream()
                .map(values -> values.stream().max(Comparator.comparingLong(SensorMetric::tick)).orElseThrow())
                .toList();
        long thresholdViolations = latest.stream().filter(metric -> exceeds(metric, machines.get(metric.machineId()))).count();
        List<MaintenanceEvent> events = nexus.maintenanceEvents().stream()
                .filter(event -> context.selectedFactoryId() == null || event.factoryId().equals(context.selectedFactoryId()))
                .toList();
        long criticalEvents = events.stream().filter(event -> "CRITICAL".equals(event.severity().name()) && "OPEN".equals(event.status())).count();
        SensorMetric peak = latest.stream().max(Comparator.comparingDouble(SensorMetric::vibration)).orElseThrow();
        boolean actionRequired = thresholdViolations > 0 || criticalEvents > 0;
        List<String> actions = actionRequired
                ? List.of("임계치 초과 설비를 점검하고 운영자 승인 후 예방 정비 작업을 생성합니다.")
                : List.of();
        String summary = "최근 센서 기준 임계치 초과 설비는 %d대, 미처리 critical 정비 이벤트는 %d건입니다.%s".formatted(
                thresholdViolations, criticalEvents,
                actionRequired ? " 설비 점검이 필요합니다." : " 즉시 정비가 필요한 설비는 없습니다."
        );
        return new AgentResult(
                getName(), Intent.MAINTENANCE, summary,
                List.of(
                        new AgentEvidence("threshold_violations", "센서 임계치 초과 설비", String.valueOf(thresholdViolations), "sensorMetrics"),
                        new AgentEvidence("critical_maintenance", "미처리 critical 정비", String.valueOf(criticalEvents), "maintenanceEvents"),
                        new AgentEvidence("peak_vibration", "최근 최대 진동", String.valueOf(peak.vibration()), peak.machineId())
                ),
                actions, 0.92, elapsed(startedAt), AgentExecutionStatus.COMPLETED, null, actionRequired
        );
    }

    @Override
    public int getPriority() {
        return 30;
    }

    private boolean exceeds(SensorMetric metric, Machine machine) {
        return machine != null && (metric.vibration() >= machine.vibrationThreshold()
                || metric.temperatureCelsius() >= machine.temperatureThreshold()
                || metric.currentAmpere() >= machine.currentThreshold());
    }

    private long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
