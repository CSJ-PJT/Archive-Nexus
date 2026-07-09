package com.archivenexus.backend.nexuseconomy;

import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.CostType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NexusCostEventRepository extends JpaRepository<NexusCostEventEntity, Long> {
    Optional<NexusCostEventEntity> findByEventId(String eventId);
    Optional<NexusCostEventEntity> findByIdempotencyKey(String idempotencyKey);
    List<NexusCostEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByCostType(CostType type);
    @Query("select coalesce(sum(e.costAmount), 0) from NexusCostEventEntity e")
    BigDecimal sumCost();
    @Query("select coalesce(sum(e.costAmount), 0) from NexusCostEventEntity e where e.createdAt >= ?1 and e.createdAt < ?2")
    BigDecimal sumCostBetween(Instant from, Instant to);
    @Query("select coalesce(sum(e.costAmount), 0) from NexusCostEventEntity e where e.costType = ?1")
    BigDecimal sumCostByType(CostType type);
}
