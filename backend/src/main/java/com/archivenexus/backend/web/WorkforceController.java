package com.archivenexus.backend.web;

import com.archivenexus.backend.workforce.WorkforceModels.CapacitySummary;
import com.archivenexus.backend.workforce.WorkforceModels.ProductivitySummary;
import com.archivenexus.backend.workforce.WorkforceModels.WorkdayRunResponse;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceAllocationRequest;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceAllocationResponse;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceSummary;
import com.archivenexus.backend.workforce.WorkforceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
public class WorkforceController {
    private final WorkforceService workforce;

    public WorkforceController(WorkforceService workforce) {
        this.workforce = workforce;
    }

    @GetMapping("/api/workforce/summary")
    WorkforceSummary workforceSummary() {
        return workforce.workforceSummary();
    }

    @GetMapping("/api/productivity/summary")
    ProductivitySummary productivitySummary() {
        return workforce.productivitySummary();
    }

    @GetMapping("/api/capacity/summary")
    CapacitySummary capacitySummary() {
        return workforce.capacitySummary();
    }

    @PostMapping("/api/workforce/allocations")
    WorkforceAllocationResponse assign(@RequestBody(required = false) WorkforceAllocationRequest request) {
        return workforce.assign(request);
    }

    @PostMapping("/api/workforce/workday/run")
    WorkdayRunResponse runWorkday(@RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return workforce.runWorkday(date);
    }
}
