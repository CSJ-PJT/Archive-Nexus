package com.archivenexus.backend.nexuseconomy;

import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.RevenueType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NexusRevenueEventRepository extends JpaRepository<NexusRevenueEventEntity, Long> {
    Optional<NexusRevenueEventEntity> findByEventId(String eventId);
    Optional<NexusRevenueEventEntity> findByIdempotencyKey(String idempotencyKey);
    List<NexusRevenueEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByRevenueType(RevenueType type);
    @Query("select coalesce(sum(e.revenueAmount), 0) from NexusRevenueEventEntity e")
    BigDecimal sumRevenue();
    @Query("select coalesce(sum(e.revenueAmount), 0) from NexusRevenueEventEntity e where e.createdAt >= ?1 and e.createdAt < ?2")
    BigDecimal sumRevenueBetween(Instant from, Instant to);
    @Query("select coalesce(sum(e.revenueAmount), 0) from NexusRevenueEventEntity e where e.revenueType = ?1")
    BigDecimal sumRevenueByType(RevenueType type);
}
