package com.archivenexus.backend.archiveos.runtime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ArchiveOsRuntimeDeliveryRepository extends JpaRepository<ArchiveOsRuntimeDeliveryEntity, Long> {
    boolean existsByEventId(String eventId);
    @Query("select d from ArchiveOsRuntimeDeliveryEntity d where d.status = com.archivenexus.backend.archiveos.runtime.ArchiveOsRuntimeDeliveryStatus.PENDING or (d.status = com.archivenexus.backend.archiveos.runtime.ArchiveOsRuntimeDeliveryStatus.RETRY_WAIT and (d.nextRetryAt is null or d.nextRetryAt <= :now)) order by d.createdAt asc")
    List<ArchiveOsRuntimeDeliveryEntity> findDeliverable(Instant now, Pageable pageable);
    List<ArchiveOsRuntimeDeliveryEntity> findAllByStatusOrderByCreatedAtDesc(ArchiveOsRuntimeDeliveryStatus status, Pageable pageable);
    List<ArchiveOsRuntimeDeliveryEntity> findAllByCorrelationIdOrderByCreatedAtAsc(String correlationId, Pageable pageable);
    List<ArchiveOsRuntimeDeliveryEntity> findAllByStatusAndPublishingStartedAtBefore(ArchiveOsRuntimeDeliveryStatus status, Instant before);
    long countByStatus(ArchiveOsRuntimeDeliveryStatus status);
}
