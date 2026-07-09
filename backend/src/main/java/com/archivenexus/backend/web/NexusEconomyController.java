package com.archivenexus.backend.web;

import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.*;
import com.archivenexus.backend.nexuseconomy.NexusEconomyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
public class NexusEconomyController {
    private final NexusEconomyService economy;
    public NexusEconomyController(NexusEconomyService economy) { this.economy = economy; }

    @PostMapping("/api/economy/events/external")
    EconomyEventResponse receiveExternal(@RequestBody ExternalCostEventRequest request) {
        return economy.receiveExternalCost(request);
    }

    @PostMapping("/api/economy/events/external/bulk")
    ExternalCostEventBulkResponse receiveExternalBulk(@RequestBody ExternalCostEventBulkRequest request) {
        return economy.receiveExternalCostBulk(request);
    }

    @GetMapping("/api/nexus-economy/summary")
    NexusEconomySummary summary() { return economy.summary(); }

    @GetMapping("/api/nexus-economy/revenue-events")
    List<RevenueEventView> revenueEvents(@RequestParam(defaultValue = "100") int limit) {
        return economy.revenueEvents(limit);
    }

    @GetMapping("/api/nexus-economy/cost-events")
    List<CostEventView> costEvents(@RequestParam(defaultValue = "100") int limit) {
        return economy.costEvents(limit);
    }

    @GetMapping("/api/nexus-economy/profit-snapshots")
    List<ProfitSnapshotView> profitSnapshots(@RequestParam(defaultValue = "100") int limit) {
        return economy.profitSnapshots(limit);
    }

    @PostMapping("/api/nexus-economy/daily-close")
    ProfitSnapshotView dailyClose(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return economy.dailyClose(date);
    }
}
