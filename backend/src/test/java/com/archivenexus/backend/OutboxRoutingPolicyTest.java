package com.archivenexus.backend;

import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxRoutingPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRoutingPolicyTest {
    private final OutboxRoutingPolicy policy = new OutboxRoutingPolicy(false);

    @Test
    void resolvesEventTypesToExpectedTargets() {
        assertThat(policy.resolveTarget(EventType.LOGISTICS_DISPATCHED, Map.of()).service()).isEqualTo(OutboxTargetService.LOGITICS);
        assertThat(policy.resolveTarget(EventType.URGENT_DELIVERY_REQUESTED, Map.of()).service()).isEqualTo(OutboxTargetService.LOGITICS);
        assertThat(policy.resolveTarget(EventType.MAINTENANCE_COMPLETED, Map.of()).service()).isEqualTo(OutboxTargetService.LEDGER);
        assertThat(policy.resolveTarget(EventType.CORPORATE_CARD_USED, Map.of()).service()).isEqualTo(OutboxTargetService.LEDGER);
        assertThat(policy.resolveTarget(EventType.SHIPMENT_HOLD_CREATED, Map.of()).service()).isEqualTo(OutboxTargetService.NONE);
        assertThat(policy.resolveTarget(EventType.UNKNOWN, Map.of()).service()).isEqualTo(OutboxTargetService.UNKNOWN);
    }
}
