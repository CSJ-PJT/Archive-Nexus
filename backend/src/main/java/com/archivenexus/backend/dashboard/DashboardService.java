package com.archivenexus.backend.dashboard;

import com.archivenexus.backend.ai.ManufacturingOrchestrator;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {
    private final NexusStateService nexus;
    private final ManufacturingOrchestrator orchestrator;

    public DashboardService(NexusStateService nexus, ManufacturingOrchestrator orchestrator) {
        this.nexus = nexus;
        this.orchestrator = orchestrator;
    }

    public DashboardSummary summary() {
        List<FactoryAlert> alerts = nexus.factories().stream().flatMap(factory -> nexus.alerts(factory.id()).stream()).toList();
        List<FactoryAlert> latest = alerts.stream()
                .sorted(Comparator.comparing(FactoryAlert::occurredAt).reversed())
                .limit(8)
                .toList();
        return new DashboardSummary(
                nexus.status(), nexus.persistenceStatus(), nexus.status().tick(), nexus.factories().size(),
                nexus.factories().stream().mapToInt(factory -> nexus.metrics(factory.id()).size()).sum(),
                countCategory(alerts, "INVENTORY"), countCategory(alerts, "QUALITY"),
                nexus.maintenanceEvents().stream().filter(event -> "OPEN".equals(event.status())).count(),
                nexus.rpaTasks().size(), nexus.batchSnapshots().size(), nexus.archiveOsInteractions().size(),
                nexus.anomalyCount(), latest, orchestrator.dashboardSummary(), Instant.now()
        );
    }

    private long countCategory(List<FactoryAlert> alerts, String category) {
        return alerts.stream().filter(alert -> category.equals(alert.category())).count();
    }
}
