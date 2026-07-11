package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
    List<OutboxEventEntity> findAllByTargetServiceOrderByCreatedAtDesc(OutboxTargetService targetService, Pageable pageable);
    List<OutboxEventEntity> findAllByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);
    List<OutboxEventEntity> findAllByTargetServiceAndStatusOrderByCreatedAtDesc(OutboxTargetService targetService, OutboxStatus status, Pageable pageable);
    long countByStatus(OutboxStatus status);
    long countByEventType(OutboxModels.EventType eventType);
    long countByTargetService(OutboxTargetService targetService);
    long countByTargetServiceAndStatus(OutboxTargetService targetService, OutboxStatus status);
    long countBySource(String source);
    List<OutboxEventEntity> findTop1ByTargetServiceAndStatusInAndLastErrorIsNotNullOrderByLastPublishAttemptAtDesc(OutboxTargetService targetService, Collection<OutboxStatus> statuses);
}
