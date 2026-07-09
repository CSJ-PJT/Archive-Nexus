package com.archivenexus.backend.web;

import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.GenerateResult;
import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.OutboxSummary;
import com.archivenexus.backend.outbox.OutboxModels.PublishResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outbox")
public class OutboxController {
    private final OutboxEventService outbox;

    public OutboxController(OutboxEventService outbox) {
        this.outbox = outbox;
    }

    @GetMapping("/events")
    List<OutboxEventResponse> events(@RequestParam(defaultValue = "100") int limit) {
        return outbox.events(limit);
    }

    @GetMapping("/events/{eventId}")
    ResponseEntity<OutboxEventResponse> event(@PathVariable String eventId) {
        return ResponseEntity.of(outbox.event(eventId));
    }

    @PostMapping("/events/generate")
    GenerateResult generate(@RequestParam(defaultValue = "100") int count) {
        return outbox.generateSynthetic(count);
    }

    @PostMapping("/events/publish")
    PublishResult publish() {
        return outbox.publishPending();
    }

    @GetMapping("/summary")
    OutboxSummary summary() {
        return outbox.summary();
    }
}
