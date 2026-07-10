package com.archivenexus.backend.market;

import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.market.MarketEventModels.MarketEventType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketInboundEventRepository extends JpaRepository<MarketInboundEventEntity, Long> {
    Optional<MarketInboundEventEntity> findByEventId(String eventId);
    Optional<MarketInboundEventEntity> findByIdempotencyKey(String idempotencyKey);
    long countByProcessingStatus(MarketEventStatus status);
    long countBySource(String source);
    long countBySourceAndProcessingStatus(String source, MarketEventStatus status);
    List<MarketInboundEventEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
    List<MarketInboundEventEntity> findAllByProcessingStatusOrderByReceivedAtDesc(MarketEventStatus status, Pageable pageable);
}

