package com.archivenexus.backend.workforce;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkdayProductivityRepository extends JpaRepository<WorkdayProductivityEntity, Long> {
    Optional<WorkdayProductivityEntity> findByWorkdayId(String workdayId);
    List<WorkdayProductivityEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
