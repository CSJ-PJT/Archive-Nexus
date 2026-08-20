package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    Optional<OutboxEventEntity> findByEventId(String eventId);
    boolean existsByIdempotencyKey(String idempotencyKey);
    boolean existsByEventTypeAndAggregateId(OutboxModels.EventType eventType, String aggregateId);
    List<OutboxEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<OutboxEventEntity> findAllByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses, Pageable pageable);
    List<OutboxEventEntity> findAllByStatusInAndTargetServiceOrderByCreatedAtAsc(Collection<OutboxStatus> statuses, OutboxTargetService targetService, Pageable pageable);
    List<OutboxEventEntity> findAllByStatusInAndTargetServiceAndSourceOrderByCreatedAtDesc(Collection<OutboxStatus> statuses, OutboxTargetService targetService, String source, Pageable pageable);
    List<OutboxEventEntity> findAllByStatusInAndSourceOrderByCreatedAtDesc(Collection<OutboxStatus> statuses, String source, Pageable pageable);
    List<OutboxEventEntity> findAllByTargetServiceOrderByCreatedAtDesc(OutboxTargetService targetService, Pageable pageable);
    List<OutboxEventEntity> findAllByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);
    List<OutboxEventEntity> findAllByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            OutboxStatus status, Instant periodStart, Instant periodEnd, Pageable pageable);
    List<OutboxEventEntity> findAllByTargetServiceAndStatusOrderByCreatedAtDesc(OutboxTargetService targetService, OutboxStatus status, Pageable pageable);
    long countByStatus(OutboxStatus status);
    long countByEventTypeAndStatusAndCreatedAtBetween(
            OutboxModels.EventType eventType, OutboxStatus status, Instant periodStart, Instant periodEnd);
    long countByEventType(OutboxModels.EventType eventType);
    long countByTargetService(OutboxTargetService targetService);
    long countByTargetServiceAndStatus(OutboxTargetService targetService, OutboxStatus status);
    long countBySource(String source);
    List<OutboxEventEntity> findTop1ByTargetServiceAndStatusInAndLastErrorIsNotNullOrderByLastPublishAttemptAtDesc(OutboxTargetService targetService, Collection<OutboxStatus> statuses);

    @Query(value = """
            select
              count(*) filter (where
                event_type in ('MATERIAL_CONSUMED', 'MAINTENANCE_COMPLETED',
                               'QUALITY_DEFECT_DETECTED', 'QUALITY_CLAIM_CHARGED', 'LOGISTICS_DISPATCHED')
                or (event_type = 'PRODUCTION_COMPLETED'
                    and nullif(trim(payload::jsonb ->> 'totalAmount'), '') is not null)
              ) as "publishedEvents",
              count(*) filter (where event_type = 'PRODUCTION_COMPLETED') as "productionEvents",
              count(*) filter (where event_type = 'MAINTENANCE_REQUIRED') as "maintenanceRequired",
              count(*) filter (where event_type in ('QUALITY_DEFECT_DETECTED', 'QUALITY_CLAIM_CHARGED')) as "qualityDefects",
              max(created_at) filter (where
                event_type in ('MATERIAL_CONSUMED', 'MAINTENANCE_COMPLETED',
                               'QUALITY_DEFECT_DETECTED', 'QUALITY_CLAIM_CHARGED', 'LOGISTICS_DISPATCHED')
                or (event_type = 'PRODUCTION_COMPLETED'
                    and nullif(trim(payload::jsonb ->> 'totalAmount'), '') is not null)
              ) as "sourceLatestEventAt",
              coalesce(sum(case when event_type = 'PRODUCTION_COMPLETED' then greatest(coalesce(
                nullif(payload::jsonb ->> 'totalAmount', '')::numeric, 0), 0) else 0 end), 0) as "manufacturingRevenue",
              coalesce(sum(case when event_type = 'MATERIAL_CONSUMED' then greatest(coalesce(
                nullif(payload::jsonb ->> 'estimatedCost', '')::numeric,
                coalesce(nullif(payload::jsonb ->> 'materialConsumed', '')::numeric,
                         nullif(payload::jsonb ->> 'quantity', '')::numeric, 0) * 950), 0) else 0 end), 0) as "materialCost",
              coalesce(sum(case when event_type = 'MAINTENANCE_COMPLETED' then greatest(coalesce(
                nullif(payload::jsonb ->> 'estimatedCost', '')::numeric, 350000), 0) else 0 end), 0) as "maintenanceCost",
              coalesce(sum(case when event_type in ('QUALITY_DEFECT_DETECTED', 'QUALITY_CLAIM_CHARGED') then greatest(coalesce(
                nullif(payload::jsonb ->> 'estimatedCost', '')::numeric,
                coalesce(nullif(payload::jsonb ->> 'qualityDefects', '')::numeric, 1) * 25000), 0) else 0 end), 0) as "qualityLossCost",
              coalesce(sum(case when event_type = 'LOGISTICS_DISPATCHED' then greatest(coalesce(
                nullif(payload::jsonb ->> 'estimatedCost', '')::numeric,
                coalesce(nullif(payload::jsonb ->> 'quantity', '')::numeric, 0) * 2500), 0) else 0 end), 0) as "logisticsFee"
            from nexus_outbox_event
            where status = 'PUBLISHED'
              and created_at >= :since
              and created_at <= :until
            """, nativeQuery = true)
    EconomyAggregateProjection aggregateEconomy(@Param("since") Instant since, @Param("until") Instant until);

    interface EconomyAggregateProjection {
        long getPublishedEvents();
        long getProductionEvents();
        long getMaintenanceRequired();
        long getQualityDefects();
        Instant getSourceLatestEventAt();
        BigDecimal getManufacturingRevenue();
        BigDecimal getMaterialCost();
        BigDecimal getMaintenanceCost();
        BigDecimal getQualityLossCost();
        BigDecimal getLogisticsFee();
    }
}
