package com.archivenexus.backend.runtime;

import com.archivenexus.backend.market.MarketEventModels.MarketEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketEventResponse;
import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.market.MarketEventModels.MarketEventType;
import com.archivenexus.backend.market.MarketEventService;
import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxModels.OutboxSummary;
import com.archivenexus.backend.runtime.RuntimeEventModels.RuntimeStatusResponse;
import com.archivenexus.backend.workforce.WorkforceModels.WorkdayRunResponse;
import com.archivenexus.backend.workforce.WorkforceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RuntimeWorkLoopService {
    private static final String SERVICE_NAME = "Archive-Nexus";
    private static final int MAX_HOP = 8;

    private final MarketEventService marketEvents;
    private final WorkforceService workforce;
    private final OutboxEventService outbox;
    private final boolean autoRunEnabled;
    private final Duration tickInterval;
    private final int maxEventsPerTick;
    private final int maxBacklogPerTick;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Instant lastWorkAt;
    private volatile Instant lastEventAt;
    private volatile int eventsProducedLastTick;
    private volatile int eventsConsumedLastTick;
    private volatile int backlogCount;
    private volatile String schedulerStatus;

    public RuntimeWorkLoopService(MarketEventService marketEvents,
                                  WorkforceService workforce,
                                  OutboxEventService outbox,
                                  @Value("${archive.runtime.autorun.enabled:false}") boolean autoRunEnabled,
                                  @Value("${archive.runtime.tick-interval:30s}") Duration tickInterval,
                                  @Value("${archive.runtime.max-events-per-tick:10}") int maxEventsPerTick,
                                  @Value("${archive.runtime.max-backlog-per-tick:50}") int maxBacklogPerTick) {
        this.marketEvents = marketEvents;
        this.workforce = workforce;
        this.outbox = outbox;
        this.autoRunEnabled = autoRunEnabled;
        this.tickInterval = tickInterval == null || tickInterval.isNegative() || tickInterval.isZero()
                ? Duration.ofSeconds(30)
                : tickInterval;
        this.maxEventsPerTick = Math.max(1, Math.min(maxEventsPerTick, 50));
        this.maxBacklogPerTick = Math.max(1, Math.min(maxBacklogPerTick, 500));
        this.schedulerStatus = autoRunEnabled ? "IDLE" : "DISABLED";
    }

    @Scheduled(fixedDelayString = "${archive.runtime.tick-interval:30s}",
            initialDelayString = "${archive.runtime.tick-interval:30s}")
    public void scheduledTick() {
        runOnce();
    }

    public RuntimeStatusResponse runOnce() {
        if (!autoRunEnabled) {
            schedulerStatus = "DISABLED";
            return status();
        }
        if (!running.compareAndSet(false, true)) {
            schedulerStatus = "LOCKED";
            return status();
        }
        try {
            schedulerStatus = "RUNNING";
            RuntimeTickResult result = executeTick();
            lastWorkAt = result.workedAt();
            lastEventAt = result.lastEventAt();
            eventsProducedLastTick = result.produced();
            eventsConsumedLastTick = result.consumed();
            backlogCount = result.backlog();
            schedulerStatus = "IDLE";
            return status();
        } catch (RuntimeException ex) {
            schedulerStatus = "FAILED";
            throw ex;
        } finally {
            running.set(false);
        }
    }

    public RuntimeStatusResponse status() {
        int currentBacklog = Math.max(backlogCount, workforce.workforceSummary().backlog());
        String pipelineStatus = !autoRunEnabled
                ? "DISABLED"
                : "FAILED".equals(schedulerStatus) ? "DEGRADED" : "LIVE";
        return new RuntimeStatusResponse(
                SERVICE_NAME,
                autoRunEnabled && !"FAILED".equals(schedulerStatus),
                autoRunEnabled,
                schedulerStatus,
                lastWorkAt,
                lastEventAt,
                eventsProducedLastTick,
                eventsConsumedLastTick,
                currentBacklog,
                pipelineStatus,
                Instant.now()
        );
    }

    private RuntimeTickResult executeTick() {
        Instant now = Instant.now();
        long bucket = Math.floorDiv(now.getEpochSecond(), Math.max(1, tickInterval.toSeconds()));
        String tickId = "NEXUS-AUTORUN-" + bucket;
        String correlationId = "CORR-" + tickId;
        String simulationRunId = "SIM-RUNTIME-" + LocalDate.now();
        String settlementCycleId = "CYCLE-RUNTIME-" + LocalDate.now();
        int quantity = Math.max(1, Math.min(maxBacklogPerTick, 6 + (int) (bucket % 9)));

        List<MarketEventRequest> requests = new ArrayList<>();
        addIfAllowed(requests, orderPlaced(tickId, correlationId, simulationRunId, settlementCycleId, now, quantity));
        addIfAllowed(requests, productionRequested(tickId, correlationId, simulationRunId, settlementCycleId, now, quantity));
        addIfAllowed(requests, shipmentRequested(tickId, correlationId, simulationRunId, settlementCycleId, now, quantity));

        int produced = 0;
        int consumed = 0;
        Instant latestEvent = null;
        for (MarketEventRequest request : requests) {
            MarketEventResponse response = marketEvents.receive(request);
            if (!response.duplicate()) {
                produced++;
            }
            if (response.status() == MarketEventStatus.PROCESSED) {
                consumed++;
                latestEvent = request.occurredAt();
            }
        }

        WorkdayRunResponse workday = workforce.runWorkday(LocalDate.now());
        OutboxSummary summary = outbox.summary();
        int backlog = Math.max(workday.backlogAfter(), Math.toIntExact(Math.min(Integer.MAX_VALUE, summary.pending() + summary.pendingRetry())));
        return new RuntimeTickResult(now, latestEvent == null ? now : latestEvent, produced, consumed, backlog);
    }

    private void addIfAllowed(List<MarketEventRequest> requests, MarketEventRequest request) {
        if (requests.size() < maxEventsPerTick) {
            requests.add(request);
        }
    }

    private MarketEventRequest orderPlaced(String tickId, String correlationId, String simulationRunId,
                                           String settlementCycleId, Instant now, int quantity) {
        String orderId = tickId + "-ORDER";
        return request(tickId + "-ORDER-PLACED", MarketEventType.MARKET_ORDER_PLACED, now, simulationRunId,
                settlementCycleId, correlationId, tickId, payload(orderId, tickId + "-SHIP", quantity, "NORMAL"));
    }

    private MarketEventRequest productionRequested(String tickId, String correlationId, String simulationRunId,
                                                   String settlementCycleId, Instant now, int quantity) {
        String orderId = tickId + "-ORDER";
        return request(tickId + "-PRODUCTION-REQUESTED", MarketEventType.PRODUCTION_REQUESTED, now.plusMillis(1),
                simulationRunId, settlementCycleId, correlationId, tickId + "-ORDER-PLACED",
                payload(orderId, tickId + "-SHIP", quantity, "HIGH"));
    }

    private MarketEventRequest shipmentRequested(String tickId, String correlationId, String simulationRunId,
                                                 String settlementCycleId, Instant now, int quantity) {
        String orderId = tickId + "-ORDER";
        return request(tickId + "-SHIPMENT-REQUESTED", MarketEventType.SHIPMENT_REQUESTED, now.plusMillis(2),
                simulationRunId, settlementCycleId, correlationId, tickId + "-PRODUCTION-REQUESTED",
                payload(orderId, tickId + "-SHIP", quantity, "HIGH"));
    }

    private MarketEventRequest request(String eventId, MarketEventType eventType, Instant occurredAt,
                                       String simulationRunId, String settlementCycleId, String correlationId,
                                       String causationId, Map<String, Object> payload) {
        return new MarketEventRequest(
                eventId,
                "IDEMP-" + eventId,
                "Archive-Market",
                eventType,
                1,
                occurredAt,
                simulationRunId,
                settlementCycleId,
                correlationId,
                causationId,
                0,
                MAX_HOP,
                payload
        );
    }

    private Map<String, Object> payload(String orderId, String shipmentId, int quantity, String priority) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("customerId", "SYNTHETIC-CUSTOMER-B2B");
        payload.put("customerType", "B2B_SYNTHETIC");
        payload.put("riskLevel", "LOW");
        payload.put("productType", "BATTERY_MODULE");
        payload.put("quantity", quantity);
        payload.put("orderAmount", quantity * 120_000L);
        payload.put("totalAmount", quantity * 120_000L);
        payload.put("priority", priority);
        payload.put("requiresShipment", true);
        payload.put("shipmentId", shipmentId);
        payload.put("originCode", "FAC-A");
        payload.put("destinationCode", "DC-SEOUL-01");
        payload.put("itemType", "battery-module");
        payload.put("workdayId", "NEXUS-WORKDAY-" + LocalDate.now());
        return payload;
    }

    private record RuntimeTickResult(
            Instant workedAt,
            Instant lastEventAt,
            int produced,
            int consumed,
            int backlog
    ) {
    }
}
