package com.archivenexus.backend.web;

import com.archivenexus.backend.dashboard.DashboardService;
import com.archivenexus.backend.dashboard.DashboardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    DashboardSummary summary() {
        return dashboardService.summary();
    }
}
