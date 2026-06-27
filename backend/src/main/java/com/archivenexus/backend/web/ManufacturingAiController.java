package com.archivenexus.backend.web;

import com.archivenexus.backend.ai.ManufacturingAiModels.AiDashboardSummary;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryRequest;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryResponse;
import com.archivenexus.backend.ai.ManufacturingOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class ManufacturingAiController {
    private final ManufacturingOrchestrator orchestrator;

    public ManufacturingAiController(ManufacturingOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/query")
    AiQueryResponse query(@Valid @RequestBody AiQueryRequest request) {
        return orchestrator.execute(request);
    }

    @GetMapping("/queries")
    List<AiQueryResponse> queries() {
        return orchestrator.history();
    }

    @GetMapping("/queries/{id}")
    ResponseEntity<AiQueryResponse> query(@PathVariable String id) {
        return ResponseEntity.of(orchestrator.findById(id));
    }

    @GetMapping("/summary")
    AiDashboardSummary summary() {
        return orchestrator.dashboardSummary();
    }
}
