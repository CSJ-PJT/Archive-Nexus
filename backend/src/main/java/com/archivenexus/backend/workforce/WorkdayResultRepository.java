package com.archivenexus.backend.workforce;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkdayResultRepository extends JpaRepository<WorkdayResultEntity, Long> {
    Optional<WorkdayResultEntity> findByWorkdayId(String workdayId);
    List<WorkdayResultEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
