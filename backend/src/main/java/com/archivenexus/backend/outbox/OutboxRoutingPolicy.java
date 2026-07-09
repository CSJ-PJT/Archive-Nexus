package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTarget;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxModels.RoutingStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OutboxRoutingPolicy {
    private static final Set<EventType> LOGITICS_EVENTS = EnumSet.of(
            EventType.LOGISTICS_DISPATCHED,
            EventType.URGENT_DELIVERY_REQUESTED,
            EventType.SHIPMENT_HOLD_RELEASED,
            EventType.MATERIAL_TRANSFER_REQUESTED,
            EventType.QUALITY_REPLACEMENT_SHIPMENT
    );

    private static final Set<EventType> LEDGER_EVENTS = EnumSet.of(
            EventType.PRODUCTION_COMPLETED,
            EventType.MATERIAL_CONSUMED,
            EventType.MAINTENANCE_COMPLETED,
            EventType.QUALITY_DEFECT_DETECTED,
            EventType.EMERGENCY_PURCHASE_REQUESTED,
            EventType.QUALITY_CLAIM_CHARGED,
            EventType.CORPORATE_CARD_USED,
            EventType.VENDOR_PAYMENT_REQUESTED
    );

    private final boolean allowLedgerDirectFallbackForLogistics;

    public OutboxRoutingPolicy(@Value("${archive.integrations.routing.allow-ledger-direct-fallback-for-logistics:${archive-nexus.ledger.allow-direct-fallback-for-logistics:false}}") boolean allowLedgerDirectFallbackForLogistics) {
        this.allowLedgerDirectFallbackForLogistics = allowLedgerDirectFallbackForLogistics;
    }

    public OutboxTarget resolveTarget(EventType eventType, Map<String, Object> payload) {
        if (LOGITICS_EVENTS.contains(eventType)) {
            return new OutboxTarget(OutboxTargetService.LOGITICS, RoutingStatus.ROUTED,
                    "logistics event must be priced by Archive-Logitics before financial settlement");
        }
        if (LEDGER_EVENTS.contains(eventType)) {
            return new OutboxTarget(OutboxTargetService.LEDGER, RoutingStatus.ROUTED,
                    "cost/settlement event can be normalized by Archive-Ledger directly");
        }
        if (eventType == EventType.SHIPMENT_HOLD_CREATED) {
            return new OutboxTarget(OutboxTargetService.NONE, RoutingStatus.ROUTE_SKIPPED,
                    "shipment hold creation is operational state, not a confirmed logistics cost event");
        }
        return new OutboxTarget(OutboxTargetService.UNKNOWN, RoutingStatus.ROUTE_FAILED,
                "unknown outbox routing target for eventType=" + eventType);
    }

    public boolean isLogiticsEvent(EventType eventType) {
        return LOGITICS_EVENTS.contains(eventType);
    }

    public boolean isLedgerEvent(EventType eventType) {
        return LEDGER_EVENTS.contains(eventType);
    }

    public boolean allowLedgerDirectFallbackForLogistics() {
        return allowLedgerDirectFallbackForLogistics;
    }
}
