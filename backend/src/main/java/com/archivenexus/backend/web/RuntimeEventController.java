package com.archivenexus.backend.web;

import com.archivenexus.backend.runtime.RuntimeEventModels.OperationsSummaryResponse;
import com.archivenexus.backend.runtime.RuntimeEventModels.RuntimeEventResponse;
import com.archivenexus.backend.runtime.RuntimeEventModels.RuntimeStatusResponse;
import com.archivenexus.backend.runtime.RuntimeEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RuntimeEventController {
    private final RuntimeEventService runtimeEvents;

    public RuntimeEventController(RuntimeEventService runtimeEvents) {
        this.runtimeEvents = runtimeEvents;
    }

    @GetMapping("/api/runtime-events/recent")
    List<RuntimeEventResponse> recent(@RequestParam(defaultValue = "100") int limit,
                                      @RequestParam(required = false) String after) {
        return runtimeEvents.recent(limit, after);
    }

    @GetMapping("/api/runtime-events/correlation/{correlationId}")
    List<RuntimeEventResponse> correlation(@PathVariable String correlationId) {
        return runtimeEvents.byCorrelation(correlationId);
    }

    @GetMapping("/api/runtime-events/entity/{entityId}")
    List<RuntimeEventResponse> entity(@PathVariable String entityId) {
        return runtimeEvents.byEntity(entityId);
    }

    @GetMapping("/api/operations/summary")
    OperationsSummaryResponse operationsSummary() {
        return runtimeEvents.operationsSummary();
    }

    @GetMapping("/api/runtime/status")
    RuntimeStatusResponse runtimeStatus() {
        return runtimeEvents.runtimeStatus();
    }
}
