package com.archivenexus.backend.web;

import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.*;
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
    List<OutboxEventResponse> events(@RequestParam(defaultValue = "100") int limit,
                                     @RequestParam(required = false) OutboxTargetService targetService,
                                     @RequestParam(required = false) OutboxStatus status) {
        return outbox.events(limit, targetService, status);
    }

    @GetMapping("/events/{eventId}")
    ResponseEntity<OutboxEventResponse> event(@PathVariable String eventId) {
        return ResponseEntity.of(outbox.event(eventId));
    }

    @PostMapping("/events/generate")
    GenerateResult generate(@RequestParam(defaultValue = "100") int count,
                            @RequestParam(defaultValue = "mixed") String type) {
        return outbox.generateSynthetic(count, generateType(type));
    }

    @PostMapping("/events/publish")
    PublishResult publish(@RequestParam(defaultValue = "auto") String target,
                          @RequestParam(defaultValue = "false") boolean dryRun) {
        return outbox.publishPending(publishTarget(target), dryRun);
    }

    @PostMapping("/events/{eventId}/publish")
    OutboxEventResponse publishSingle(@PathVariable String eventId) {
        return outbox.publishEvent(eventId);
    }

    @GetMapping("/summary")
    OutboxSummary summary() {
        return outbox.summary();
    }

    private GenerateType generateType(String value) {
        return switch (value == null ? "mixed" : value.trim().toLowerCase()) {
            case "logistics", "logitics" -> GenerateType.LOGISTICS;
            case "ledger" -> GenerateType.LEDGER;
            case "approval-risk", "approval_risk", "approvalrisk" -> GenerateType.APPROVAL_RISK;
            default -> GenerateType.MIXED;
        };
    }

    private PublishTarget publishTarget(String value) {
        return switch (value == null ? "auto" : value.trim().toLowerCase()) {
            case "logistics", "logitics" -> PublishTarget.LOGITICS;
            case "ledger" -> PublishTarget.LEDGER;
            default -> PublishTarget.AUTO;
        };
    }
}
