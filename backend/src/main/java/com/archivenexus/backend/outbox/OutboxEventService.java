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

@Service
public class OutboxEventService {
    private static final List<String> FACTORIES = List.of("FAC-A", "FAC-B", "FAC-C");
    private static final List<EventType> GENERATABLE_TYPES = Arrays.stream(EventType.values()).toList();

    private final OutboxEventRepository repository;
    private final LedgerPublisher publisher;
    private final ObjectMapper mapper;
    private final int publishBatchSize;

    public OutboxEventService(OutboxEventRepository repository, LedgerPublisher publisher, ObjectMapper mapper,
                              @Value("${archive-nexus.ledger.publish-batch-size:100}") int publishBatchSize) {
        this.repository = repository;
        this.publisher = publisher;
        this.mapper = mapper;
        this.publishBatchSize = Math.max(1, Math.min(publishBatchSize, 500));
    }

    @Transactional
    public Optional<OutboxEventResponse> emit(EventType type, String aggregateType, String aggregateId,
                                             String idempotencyKey, Map<String, Object> payload, Instant occurredAt) {
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            return Optional.empty();
        }
        try {
            OutboxEventEntity saved = repository.save(new OutboxEventEntity(
                    "NX-EVT-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(),
                    idempotencyKey,
                    type,
                    aggregateType,
                    aggregateId,
                    write(payload),
                    occurredAt,
                    Instant.now()
            ));
            return Optional.of(response(saved));
        } catch (DataIntegrityViolationException duplicate) {
            return Optional.empty();
        }
    }

    @Transactional
    public GenerateResult generateSynthetic(int requestedCount) {
        int count = Math.max(1, Math.min(requestedCount, 10_000));
        List<String> sample = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            EventType type = GENERATABLE_TYPES.get(index % GENERATABLE_TYPES.size());
            String factoryId = FACTORIES.get(index % FACTORIES.size());
            String aggregateId = type.name() + "-" + (System.currentTimeMillis() % 1_000_000) + "-" + index;
            String idempotency = "synthetic:" + type + ":" + aggregateId;
            Optional<OutboxEventResponse> created = emit(type, aggregateType(type), aggregateId, idempotency,
                    syntheticPayload(type, factoryId, index), Instant.now());
            created.ifPresent(event -> {
                if (sample.size() < 10) {
                    sample.add(event.eventId());
                }
            });
        }
        return new GenerateResult(count, count, sample);
    }

    public List<OutboxEventResponse> events(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream().map(this::response).toList();
    }

    public Optional<OutboxEventResponse> event(String eventId) {
        return repository.findByEventId(eventId).map(this::response);
    }

    public OutboxSummary summary() {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (EventType type : EventType.values()) {
            byType.put(type.name(), repository.countByEventType(type));
        }
        return new OutboxSummary(
                repository.count(),
                repository.countByStatus(OutboxStatus.PENDING),
                repository.countByStatus(OutboxStatus.PUBLISHED),
                repository.countByStatus(OutboxStatus.PENDING_RETRY),
                repository.countByStatus(OutboxStatus.FAILED),
                byType
        );
    }

    @Scheduled(fixedDelayString = "${archive-nexus.ledger.publish-interval-ms:15000}")
    public void scheduledPublish() {
        if (publisher.enabled()) {
            publishPending();
        }
    }

    @Transactional
    public PublishResult publishPending() {
        List<OutboxEventEntity> events = repository.findAllByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.PENDING_RETRY), PageRequest.of(0, publishBatchSize));
        if (events.isEmpty()) {
            return new PublishResult(0, 0, 0);
        }
        try {
            publisher.publish(events);
            Instant now = Instant.now();
            events.forEach(event -> event.markPublished(now));
            return new PublishResult(events.size(), events.size(), 0);
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            events.forEach(event -> event.markFailure(message));
            return new PublishResult(events.size(), 0, events.size());
        }
    }

    public OutboxEventResponse response(OutboxEventEntity event) {
        return new OutboxEventResponse(event.id(), event.eventId(), event.idempotencyKey(), event.eventType(),
                event.aggregateType(), event.aggregateId(), event.source(), event.schemaVersion(), read(event.payload()),
                event.status(), event.retryCount(), event.lastError(), event.occurredAt(), event.createdAt(), event.publishedAt());
    }

    private Map<String, Object> syntheticPayload(EventType type, String factoryId, int index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal amount = BigDecimal.valueOf(switch (type) {
            case PRODUCTION_COMPLETED -> random.nextLong(800_000, 2_500_000);
            case MATERIAL_CONSUMED -> random.nextLong(300_000, 1_800_000);
            case LOGISTICS_DISPATCHED -> random.nextLong(250_000, 1_200_000);
            case MAINTENANCE_COMPLETED -> random.nextLong(1_000_000, 6_500_000);
            case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> random.nextLong(900_000, 8_000_000);
            case EMERGENCY_PURCHASE_REQUESTED, CORPORATE_CARD_USED, VENDOR_PAYMENT_REQUESTED -> random.nextLong(500_000, 7_000_000);
            case SHIPMENT_HOLD_CREATED -> random.nextLong(200_000, 2_000_000);
        });
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
            case LOGISTICS_DISPATCHED, SHIPMENT_HOLD_CREATED -> "LOGI";
            case MATERIAL_CONSUMED, EMERGENCY_PURCHASE_REQUESTED, VENDOR_PAYMENT_REQUESTED -> "SUPPLY";
            case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> "QUALITY";
            default -> "OPS";
        };
    }

    private String syntheticReason(EventType type) {
        return switch (type) {
            case PRODUCTION_COMPLETED -> "synthetic production completion cost aggregation";
            case MATERIAL_CONSUMED -> "synthetic raw material consumption";
            case LOGISTICS_DISPATCHED -> "synthetic outbound logistics settlement";
            case MAINTENANCE_COMPLETED -> "synthetic maintenance expense";
            case QUALITY_DEFECT_DETECTED -> "synthetic quality defect loss";
            case EMERGENCY_PURCHASE_REQUESTED -> "synthetic emergency spare part purchase";
            case QUALITY_CLAIM_CHARGED -> "synthetic quality claim chargeback";
            case SHIPMENT_HOLD_CREATED -> "synthetic shipment hold cost impact";
            case CORPORATE_CARD_USED -> "synthetic corporate card-like operational expense";
            case VENDOR_PAYMENT_REQUESTED -> "synthetic vendor payment request";
        };
    }

    private String aggregateType(EventType type) {
        return switch (type) {
            case PRODUCTION_COMPLETED -> "ProductionOrder";
            case MATERIAL_CONSUMED -> "InventoryTransaction";
            case LOGISTICS_DISPATCHED, SHIPMENT_HOLD_CREATED -> "LogisticsShipment";
            case MAINTENANCE_COMPLETED -> "MaintenanceEvent";
            case QUALITY_DEFECT_DETECTED, QUALITY_CLAIM_CHARGED -> "QualityInspection";
            default -> "SyntheticFinancialOperation";
        };
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
