package com.archivenexus.backend.outbox;

import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    Optional<OutboxEventEntity> findByEventId(String eventId);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<OutboxEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<OutboxEventEntity> findAllByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses, Pageable pageable);
    long countByStatus(OutboxStatus status);
    long countByEventType(OutboxModels.EventType eventType);
}
