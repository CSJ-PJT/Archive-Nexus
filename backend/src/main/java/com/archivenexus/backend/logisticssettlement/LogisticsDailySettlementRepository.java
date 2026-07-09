package com.archivenexus.backend.logisticssettlement;

import com.archivenexus.backend.logisticssettlement.LogisticsSettlementModels.SettlementProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LogisticsDailySettlementRepository extends JpaRepository<LogisticsDailySettlementEntity, Long> {
    Optional<LogisticsDailySettlementEntity> findBySettlementId(String settlementId);
    Optional<LogisticsDailySettlementEntity> findByIdempotencyKey(String idempotencyKey);
    List<LogisticsDailySettlementEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
    List<LogisticsDailySettlementEntity> findAllByFactoryIdOrderBySettlementDateDescReceivedAtDesc(String factoryId, Pageable pageable);
    long countByProcessingStatus(SettlementProcessingStatus status);
    long countByFactoryId(String factoryId);
    @Query("select coalesce(sum(s.totalLogisticsCost), 0) from LogisticsDailySettlementEntity s")
    BigDecimal sumTotalLogisticsCost();
    @Query("select coalesce(sum(s.manufacturingImpactCost), 0) from LogisticsDailySettlementEntity s")
    BigDecimal sumManufacturingImpactCost();
    @Query("select max(s.receivedAt) from LogisticsDailySettlementEntity s")
    Instant maxReceivedAt();
    @Query("select distinct s.factoryId from LogisticsDailySettlementEntity s order by s.factoryId")
    List<String> distinctFactoryIds();
}
