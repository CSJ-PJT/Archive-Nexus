package com.archivenexus.backend.web;

import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.IntegrationSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private final OutboxEventService outbox;

    public IntegrationController(OutboxEventService outbox) {
        this.outbox = outbox;
    }

    @GetMapping("/summary")
    IntegrationSummary summary() {
        return outbox.integrationSummary();
    }
}
