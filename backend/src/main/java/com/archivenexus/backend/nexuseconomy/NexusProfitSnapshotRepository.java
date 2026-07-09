package com.archivenexus.backend.nexuseconomy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NexusProfitSnapshotRepository extends JpaRepository<NexusProfitSnapshotEntity, Long> {
    List<NexusProfitSnapshotEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
