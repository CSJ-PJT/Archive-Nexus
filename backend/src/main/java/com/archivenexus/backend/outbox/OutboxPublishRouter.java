package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.OutboxTarget;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxModels.PublishTarget;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPublishRouter {
    private final OutboxRoutingPolicy routingPolicy;
    private final LogiticsPublisher logiticsPublisher;
    private final LedgerPublisher ledgerPublisher;

    public OutboxPublishRouter(OutboxRoutingPolicy routingPolicy, LogiticsPublisher logiticsPublisher, LedgerPublisher ledgerPublisher) {
        this.routingPolicy = routingPolicy;
        this.logiticsPublisher = logiticsPublisher;
        this.ledgerPublisher = ledgerPublisher;
    }

    public OutboxTarget resolve(OutboxEventEntity event) {
        return routingPolicy.resolveTarget(event.eventType(), null);
    }

    public boolean matchesTarget(OutboxEventEntity event, PublishTarget requestedTarget) {
        OutboxTargetService service = resolve(event).service();
        if (requestedTarget == PublishTarget.AUTO) {
            return service == OutboxTargetService.LOGITICS || service == OutboxTargetService.LEDGER
                    || service == OutboxTargetService.NONE || service == OutboxTargetService.UNKNOWN;
        }
        if (requestedTarget == PublishTarget.LOGITICS) {
            return service == OutboxTargetService.LOGITICS;
        }
        if (requestedTarget == PublishTarget.LEDGER) {
            return service == OutboxTargetService.LEDGER
                    || (service == OutboxTargetService.LOGITICS && routingPolicy.allowLedgerDirectFallbackForLogistics());
        }
        return false;
    }

    public boolean enabled(OutboxTargetService target) {
        return switch (target) {
            case LOGITICS -> logiticsPublisher.enabled();
            case LEDGER -> ledgerPublisher.enabled();
            default -> false;
        };
    }

    public String targetUrl(OutboxTargetService target) {
        return switch (target) {
            case LOGITICS -> logiticsPublisher.targetUrl();
            case LEDGER -> ledgerPublisher.targetUrl();
            default -> null;
        };
    }

    public String baseUrl(OutboxTargetService target) {
        return switch (target) {
            case LOGITICS -> logiticsPublisher.baseUrl();
            case LEDGER -> ledgerPublisher.baseUrl();
            default -> null;
        };
    }

    public OutboxModels.PublishAcknowledgement publish(OutboxTargetService target, List<OutboxEventEntity> events) {
        return switch (target) {
            case LOGITICS -> logiticsPublisher.publish(events);
            case LEDGER -> ledgerPublisher.publish(events);
            default -> throw new IllegalStateException("Unsupported outbox publish target: " + target);
        };
    }

    public String health(OutboxTargetService target) {
        return switch (target) {
            case LOGITICS -> logiticsPublisher.health();
            case LEDGER -> ledgerPublisher.health();
            default -> "DISABLED";
        };
    }

    public boolean allowLedgerDirectFallbackForLogistics() {
        return routingPolicy.allowLedgerDirectFallbackForLogistics();
    }
}
