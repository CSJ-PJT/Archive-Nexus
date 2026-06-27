package com.archivenexus.backend.dashboard;

import com.archivenexus.backend.ai.ManufacturingAiModels.AiDashboardSummary;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.SimulatorPersistenceStatus;
import com.archivenexus.backend.domain.DomainModels.SimulatorStatus;

import java.time.Instant;
import java.util.List;

public record DashboardSummary(
        SimulatorStatus simulatorStatus,
        SimulatorPersistenceStatus persistence,
        long currentTick,
        int factoryCount,
        int sensorMetricCount,
        long inventoryAlertCount,
        long qualityIssueCount,
        long maintenanceRiskCount,
        int rpaTaskCount,
        int batchSnapshotCount,
        int archiveOsInteractionCount,
        int anomalyCount,
        List<FactoryAlert> latestAnomalies,
        AiDashboardSummary ai,
        Instant generatedAt
) {
}
