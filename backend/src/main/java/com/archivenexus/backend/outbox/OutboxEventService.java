package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OutboxEventService {
    private static final List<String> FACTORIES = List.of("FAC-A", "FAC-B", "FAC-C");
    private static final List<EventType> LOGISTICS_GENERATABLE_TYPES = List.of(
            EventType.LOGISTICS_DISPATCHED,
            EventType.URGENT_DELIVERY_REQUESTED,
            EventType.SHIPMENT_HOLD_RELEASED,
            EventType.MATERIAL_TRANSFER_REQUESTED,
            EventType.QUALITY_REPLACEMENT_SHIPMENT
    );
    private static final List<EventType> LEDGER_GENERATABLE_TYPES = List.of(
            EventType.PRODUCTION_COMPLETED,
            EventType.MATERIAL_CONSUMED,
            EventType.MAINTENANCE_COMPLETED,
            EventType.QUALITY_DEFECT_DETECTED,
            EventType.EMERGENCY_PURCHASE_REQUESTED,
            EventType.QUALITY_CLAIM_CHARGED,
            EventType.CORPORATE_CARD_USED,
            EventType.VENDOR_PAYMENT_REQUESTED
    );
    private static final List<EventType> MIXED_GENERATABLE_TYPES = List.of(
            EventType.PRODUCTION_COMPLETED,
            EventType.MATERIAL_CONSUMED,
            EventType.LOGISTICS_DISPATCHED,
            EventType.MAINTENANCE_COMPLETED,
            EventType.QUALITY_DEFECT_DETECTED,
            EventType.SHIPMENT_HOLD_CREATED,
            EventType.EMERGENCY_PURCHASE_REQUESTED,
            EventType.QUALITY_CLAIM_CHARGED,
            EventType.CORPORATE_CARD_USED,
            EventType.VENDOR_PAYMENT_REQUESTED
    );

    private final OutboxEventRepository repository;
    private final OutboxPublishRouter router;
    private final OutboxRoutingPolicy routingPolicy;
    private final ObjectMapper mapper;
    private final int publishBatchSize;
    private final int maxRetryCount;

    public OutboxEventService(OutboxEventRepository repository,
                              OutboxPublishRouter router,
                              OutboxRoutingPolicy routingPolicy,
                              ObjectMapper mapper,
                              @Value("${archive.integrations.routing.chunk-size:${archive-nexus.ledger.publish-batch-size:50}}") int publishBatchSize,
                              @Value("${archive.integrations.routing.max-retry-count:5}") int maxRetryCount) {
        this.repository = repository;
        this.router = router;
        this.routingPolicy = routingPolicy;
        this.mapper = mapper;
        this.publishBatchSize = Math.max(1, Math.min(publishBatchSize, 500));
        this.maxRetryCount = Math.max(1, maxRetryCount);
    }

    @Transactional
    public Optional<OutboxEventResponse> emit(EventType type, String aggregateType, String aggregateId,
                                             String idempotencyKey, Map<String, Object> payload, Instant occurredAt) {
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            return Optional.empty();
        }
        try {
            OutboxTarget target = routingPolicy.resolveTarget(type, payload);
            OutboxEventEntity event = new OutboxEventEntity(
                    "NX-EVT-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(),
                    idempotencyKey,
                    type,
                    aggregateType,
                    aggregateId,
                    write(payload),
                    occurredAt,
                    Instant.now()
            );
            event.route(target.service(), router.targetUrl(target.service()), target.routingStatus(), skippedReason(target));
            OutboxEventEntity saved = repository.save(event);
            return Optional.of(response(saved));
        } catch (DataIntegrityViolationException duplicate) {
            return Optional.empty();
        }
    }

    @Transactional
    public GenerateResult generateSynthetic(int requestedCount) {
        return generateSynthetic(requestedCount, GenerateType.MIXED);
    }

    @Transactional
    public GenerateResult generateSynthetic(int requestedCount, GenerateType generateType) {
        int count = Math.max(1, Math.min(requestedCount, 10_000));
        GenerateType safeType = generateType == null ? GenerateType.MIXED : generateType;
        List<EventType> types = generationTypes(safeType);
        List<String> sample = new ArrayList<>();
        Map<String, Long> targets = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            EventType type = types.get(index % types.size());
            String factoryId = FACTORIES.get(index % FACTORIES.size());
            String aggregateId = type.name() + "-" + (System.currentTimeMillis() % 1_000_000) + "-" + index;
            String idempotency = "synthetic:" + safeType + ":" + type + ":" + aggregateId;
            Optional<OutboxEventResponse> created = emit(type, aggregateType(type), aggregateId, idempotency,
                    syntheticPayload(type, factoryId, index, safeType), Instant.now());
            created.ifPresent(event -> {
                targets.merge(event.targetService().name(), 1L, Long::sum);
                if (sample.size() < 10) {
                    sample.add(event.eventId());
                }
            });
        }
        return new GenerateResult(count, count, safeType, targets, sample);
    }

    public List<OutboxEventResponse> events(int limit, OutboxTargetService targetService, OutboxStatus status) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        PageRequest page = PageRequest.of(0, safeLimit);
        if (targetService != null && status != null) {
            return repository.findAllByTargetServiceAndStatusOrderByCreatedAtDesc(targetService, status, page).stream().map(this::response).toList();
        }
        if (targetService != null) {
            return repository.findAllByTargetServiceOrderByCreatedAtDesc(targetService, page).stream().map(this::response).toList();
        }
        if (status != null) {
            return repository.findAllByStatusOrderByCreatedAtDesc(status, page).stream().map(this::response).toList();
        }
        return repository.findAllByOrderByCreatedAtDesc(page).stream().map(this::response).toList();
    }

    public List<OutboxEventResponse> events(int limit) {
        return events(limit, null, null);
    }

    public Optional<OutboxEventResponse> event(String eventId) {
        return repository.findByEventId(eventId).map(this::response);
    }

    public OutboxSummary summary() {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (EventType type : EventType.values()) {
            byType.put(type.name(), repository.countByEventType(type));
        }
        Map<String, Long> status = new LinkedHashMap<>();
        for (OutboxStatus outboxStatus : OutboxStatus.values()) {
            status.put(outboxStatus.name(), repository.countByStatus(outboxStatus));
        }
        return new OutboxSummary(
                repository.count(),
                repository.countByStatus(OutboxStatus.PENDING),
                repository.countByStatus(OutboxStatus.PUBLISHED),
                repository.countByStatus(OutboxStatus.PENDING_RETRY),
                repository.countByStatus(OutboxStatus.FAILED),
                byType,
                status,
                targetSummaries(),
                integrationStates()
        );
    }

    public IntegrationSummary integrationSummary() {
        return new IntegrationSummary(
                UUID.randomUUID().toString(),
                "Archive-Nexus",
                "HEALTHY",
                integrationStatesWithHealth(),
                new RoutingConfig("AUTO", router.allowLedgerDirectFallbackForLogistics())
        );
    }

    @Scheduled(fixedDelayString = "${archive.integrations.routing.publish-interval-ms:${archive-nexus.ledger.publish-interval-ms:15000}}")
    public void scheduledPublish() {
        if (router.enabled(OutboxTargetService.LOGITICS) || router.enabled(OutboxTargetService.LEDGER)) {
            publishPending(PublishTarget.AUTO, false);
        }
    }

    @Transactional
    public PublishResult publishPending() {
        return publishPending(PublishTarget.AUTO, false);
    }

    @Transactional
    public PublishResult publishPending(PublishTarget requestedTarget, boolean dryRun) {
        PublishTarget safeTarget = requestedTarget == null ? PublishTarget.AUTO : requestedTarget;
        List<OutboxEventEntity> candidates = repository.findAllByStatusInOrderByCreatedAtAsc(
                        List.of(OutboxStatus.PENDING, OutboxStatus.PENDING_RETRY), PageRequest.of(0, publishBatchSize))
                .stream()
                .filter(event -> router.matchesTarget(event, safeTarget))
                .toList();
        if (candidates.isEmpty()) {
            return emptyResult(safeTarget, dryRun);
        }
        Map<OutboxTargetService, List<OutboxEventEntity>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(event -> router.resolve(event).service(), LinkedHashMap::new, Collectors.toList()));

        Map<String, TargetPublishSummary> targetResults = new LinkedHashMap<>();
        int published = 0;
        int skipped = 0;
        int failed = 0;
        Instant now = Instant.now();

        for (Map.Entry<OutboxTargetService, List<OutboxEventEntity>> entry : grouped.entrySet()) {
            OutboxTargetService target = entry.getKey();
            List<OutboxEventEntity> events = entry.getValue();
            int targetPublished = 0;
            int targetSkipped = 0;
            int targetFailed = 0;

            if (target == OutboxTargetService.NONE || target == OutboxTargetService.UNKNOWN) {
                for (OutboxEventEntity event : events) {
                    OutboxTarget resolved = router.resolve(event);
                    event.route(resolved.service(), null, resolved.routingStatus(), resolved.reason());
                    event.markSkipped(resolved.reason(), resolved.routingStatus(), now);
                }
                targetSkipped = events.size();
            } else if (dryRun || !router.enabled(target)) {
                String reason = dryRun ? "dry-run: external publish was not executed" : target + " integration is disabled";
                for (OutboxEventEntity event : events) {
                    event.route(target, router.targetUrl(target), RoutingStatus.DRY_RUN, reason);
                    event.recordDryRun(reason, now);
                }
                targetSkipped = events.size();
            } else {
                try {
                    for (OutboxEventEntity event : events) {
                        event.recordPublishAttempt(target, router.targetUrl(target), now);
                    }
                    router.publish(target, events);
                    events.forEach(event -> event.markPublished(now));
                    targetPublished = events.size();
                } catch (RuntimeException error) {
                    String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    events.forEach(event -> event.markFailure(message, maxRetryCount));
                    targetFailed = events.size();
                }
            }
            published += targetPublished;
            skipped += targetSkipped;
            failed += targetFailed;
            targetResults.put(target.name(), new TargetPublishSummary(events.size(), targetPublished, targetSkipped, targetFailed));
        }

        return new PublishResult(UUID.randomUUID().toString(), safeTarget, dryRun, candidates.size(), candidates.size(),
                published, skipped, failed, targetResults);
    }

    public OutboxEventResponse response(OutboxEventEntity event) {
        return new OutboxEventResponse(event.id(), event.eventId(), event.idempotencyKey(), event.eventType(),
                event.aggregateType(), event.aggregateId(), event.source(), event.schemaVersion(), read(event.payload()),
                event.status(), event.retryCount(), event.lastError(), event.targetService(), event.targetUrl(),
                event.routingStatus(), event.lastPublishTarget(), event.lastPublishAttemptAt(), event.publishSkippedReason(),
                event.occurredAt(), event.createdAt(), event.publishedAt());
    }

    private PublishResult emptyResult(PublishTarget target, boolean dryRun) {
        return new PublishResult(UUID.randomUUID().toString(), target, dryRun, 0, 0, 0, 0, 0, Map.of());
    }

    private Map<String, TargetSummary> targetSummaries() {
        Map<String, TargetSummary> summaries = new LinkedHashMap<>();
        for (OutboxTargetService target : OutboxTargetService.values()) {
            long pending = repository.countByTargetServiceAndStatus(target, OutboxStatus.PENDING);
            long pendingRetry = repository.countByTargetServiceAndStatus(target, OutboxStatus.PENDING_RETRY);
            long published = repository.countByTargetServiceAndStatus(target, OutboxStatus.PUBLISHED);
            long skipped = repository.countByTargetServiceAndStatus(target, OutboxStatus.SKIPPED);
            long failed = repository.countByTargetServiceAndStatus(target, OutboxStatus.FAILED);
            String lastError = repository.findTop1ByTargetServiceAndStatusInAndLastErrorIsNotNullOrderByLastPublishAttemptAtDesc(
                    target, List.of(OutboxStatus.PENDING_RETRY, OutboxStatus.FAILED)).stream()
                    .map(OutboxEventEntity::lastError)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            summaries.put(target.name(), new TargetSummary(pending + pendingRetry + published + skipped + failed,
                    pending + pendingRetry, published, skipped, failed, lastError));
        }
        return summaries;
    }

    private Map<String, IntegrationState> integrationStates() {
        return Map.of(
                "logitics", new IntegrationState(router.enabled(OutboxTargetService.LOGITICS), router.baseUrl(OutboxTargetService.LOGITICS),
                        router.enabled(OutboxTargetService.LOGITICS) ? "ENABLED" : "DISABLED"),
                "ledger", new IntegrationState(router.enabled(OutboxTargetService.LEDGER), router.baseUrl(OutboxTargetService.LEDGER),
                        router.enabled(OutboxTargetService.LEDGER) ? "ENABLED" : "DISABLED")
        );
    }

    private Map<String, IntegrationState> integrationStatesWithHealth() {
        return Map.of(
                "logitics", new IntegrationState(router.enabled(OutboxTargetService.LOGITICS), router.baseUrl(OutboxTargetService.LOGITICS),
                        router.health(OutboxTargetService.LOGITICS)),
                "ledger", new IntegrationState(router.enabled(OutboxTargetService.LEDGER), router.baseUrl(OutboxTargetService.LEDGER),
                        router.health(OutboxTargetService.LEDGER))
        );
    }

    private List<EventType> generationTypes(GenerateType type) {
        return switch (type) {
            case LOGISTICS -> LOGISTICS_GENERATABLE_TYPES;
            case LEDGER -> LEDGER_GENERATABLE_TYPES;
            case APPROVAL_RISK -> List.of(
                    EventType.MAINTENANCE_COMPLETED,
                    EventType.EMERGENCY_PURCHASE_REQUESTED,
                    EventType.QUALITY_CLAIM_CHARGED,
                    EventType.CORPORATE_CARD_USED,
                    EventType.VENDOR_PAYMENT_REQUESTED
            );
            case MIXED -> MIXED_GENERATABLE_TYPES;
        };
    }

    private Map<String, Object> syntheticPayload(EventType type, String factoryId, int index, GenerateType generateType) {
        if (routingPolicy.isLogiticsEvent(type)) {
            return logisticsPayload(type, factoryId, index);
        }
        return ledgerPayload(type, factoryId, index, generateType);
    }

    private Map<String, Object> logisticsPayload(EventType type, String factoryId, int index) {
        String shipmentId = "SHIP-20260709-" + String.format("%06d", index);
        String priority = switch (index % 8) {
            case 0 -> "CRITICAL";
            case 1, 2 -> "HIGH";
            default -> "NORMAL";
        };
        String destination = switch (index % 3) {
            case 0 -> "DC-SEOUL-01";
            case 1 -> "DC-DAEJEON-01";
            default -> "DC-BUSAN-01";
        };
        return new LinkedHashMap<>(Map.ofEntries(
                Map.entry("synthetic", true),
                Map.entry("factoryId", factoryId),
                Map.entry("shipmentId", shipmentId),
                Map.entry("originCode", factoryId),
                Map.entry("destinationCode", destination),
                Map.entry("priority", priority),
                Map.entry("itemType", index % 2 == 0 ? "battery-module" : "vehicle-control-unit"),
                Map.entry("quantity", 40 + (index % 180)),
                Map.entry("requiresColdChain", index % 5 == 0),
                Map.entry("eventMeaning", logisticsReason(type))
        ));
    }

    private Map<String, Object> ledgerPayload(EventType type, String factoryId, int index, GenerateType generateType) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal amount = BigDecimal.valueOf(switch (type) {
            case PRODUCTION_COMPLETED -> random.nextLong(800_000, 2_500_000);
            case MATERIAL_CONSUMED -> random.nextLong(300_000, 1_800_000);
            case MAINTENANCE_COMPLETED -> random.nextLong(1_000_000, 6_500_000);
            case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> random.nextLong(900_000, 8_000_000);
            case EMERGENCY_PURCHASE_REQUESTED, CORPORATE_CARD_USED, VENDOR_PAYMENT_REQUESTED -> random.nextLong(500_000, 7_000_000);
            case SHIPMENT_HOLD_CREATED -> random.nextLong(200_000, 2_000_000);
            default -> random.nextLong(300_000, 1_500_000);
        });
        if (generateType == GenerateType.APPROVAL_RISK && amount.longValue() < 3_000_000) {
            amount = BigDecimal.valueOf(3_500_000 + index * 10_000L);
        }
        String severity = amount.longValue() >= 3_000_000 ? "HIGH" : "MEDIUM";
        return new LinkedHashMap<>(Map.ofEntries(
                Map.entry("synthetic", true),
                Map.entry("factoryId", factoryId),
                Map.entry("equipmentId", "EQ-" + factoryId.substring(factoryId.length() - 1) + "-" + String.format("%03d", index % 200)),
                Map.entry("vendorId", "VENDOR-" + vendorCategory(type) + "-" + String.format("%02d", index % 30)),
                Map.entry("syntheticAccountId", "SYN-ACCT-" + factoryId + "-" + String.format("%04d", index % 500)),
                Map.entry("corporateCardToken", "SYN-CARD-TOKEN-" + String.format("%05d", index)),
                Map.entry("severity", severity),
                Map.entry("estimatedCost", amount),
                Map.entry("currency", "KRW"),
                Map.entry("vendorRisk", index % 97 == 0 ? "WARNING" : "NORMAL"),
                Map.entry("requiresApproval", amount.longValue() >= 3_000_000 || type == EventType.EMERGENCY_PURCHASE_REQUESTED),
                Map.entry("reason", syntheticReason(type))
        ));
    }

    private String vendorCategory(EventType type) {
        return switch (type) {
            case MAINTENANCE_COMPLETED -> "MAINT";
            case MATERIAL_CONSUMED, EMERGENCY_PURCHASE_REQUESTED, VENDOR_PAYMENT_REQUESTED -> "SUPPLY";
            case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> "QUALITY";
            default -> "OPS";
        };
    }

    private String logisticsReason(EventType type) {
        return switch (type) {
            case LOGISTICS_DISPATCHED -> "synthetic outbound shipment routing request";
            case URGENT_DELIVERY_REQUESTED -> "synthetic urgent delivery routing request";
            case SHIPMENT_HOLD_RELEASED -> "synthetic shipment hold release requiring delivery plan";
            case MATERIAL_TRANSFER_REQUESTED -> "synthetic inter-factory material transfer";
            case QUALITY_REPLACEMENT_SHIPMENT -> "synthetic replacement shipment for quality issue";
            default -> "synthetic logistics operation";
        };
    }

    private String syntheticReason(EventType type) {
        return switch (type) {
            case PRODUCTION_COMPLETED -> "synthetic production completion cost aggregation";
            case MATERIAL_CONSUMED -> "synthetic raw material consumption";
            case MAINTENANCE_COMPLETED -> "synthetic maintenance expense";
            case QUALITY_DEFECT_DETECTED -> "synthetic quality defect loss";
            case EMERGENCY_PURCHASE_REQUESTED -> "synthetic emergency spare part purchase";
            case QUALITY_CLAIM_CHARGED -> "synthetic quality claim chargeback";
            case SHIPMENT_HOLD_CREATED -> "synthetic shipment hold created without confirmed delivery cost";
            case CORPORATE_CARD_USED -> "synthetic corporate card-like operational expense";
            case VENDOR_PAYMENT_REQUESTED -> "synthetic vendor payment request";
            default -> "synthetic operational event";
        };
    }

    private String aggregateType(EventType type) {
        return switch (type) {
            case PRODUCTION_COMPLETED -> "ProductionOrder";
            case MATERIAL_CONSUMED -> "InventoryTransaction";
            case LOGISTICS_DISPATCHED, URGENT_DELIVERY_REQUESTED, SHIPMENT_HOLD_RELEASED,
                 MATERIAL_TRANSFER_REQUESTED, QUALITY_REPLACEMENT_SHIPMENT, SHIPMENT_HOLD_CREATED -> "LogisticsShipment";
            case MAINTENANCE_COMPLETED -> "MaintenanceEvent";
            case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> "QualityInspection";
            default -> "SyntheticFinancialOperation";
        };
    }

    private String skippedReason(OutboxTarget target) {
        return target.service() == OutboxTargetService.NONE || target.service() == OutboxTargetService.UNKNOWN ? target.reason() : null;
    }

    private String write(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid synthetic outbox payload", error);
        }
    }

    private Map<String, Object> read(String payload) {
        try {
            return mapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception error) {
            return Map.of("rawPayload", payload);
        }
    }
}
