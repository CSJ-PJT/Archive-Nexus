package com.archivenexus.backend.workforce;

import com.archivenexus.backend.workforce.WorkforceModels.WorkforceAllocationStatus;
import com.archivenexus.backend.workforce.WorkforceModels.WorkforceRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkforceAllocationRepository extends JpaRepository<WorkforceAllocationEntity, Long> {
    Optional<WorkforceAllocationEntity> findByEventId(String eventId);
    Optional<WorkforceAllocationEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<WorkforceAllocationEntity> findByWorkdayIdAndRole(String workdayId, WorkforceRole role);
    List<WorkforceAllocationEntity> findAllByStatus(WorkforceAllocationStatus status);
    List<WorkforceAllocationEntity> findAllByRoleAndStatusOrderByCreatedAtAsc(WorkforceRole role, WorkforceAllocationStatus status);
    long countByStatus(WorkforceAllocationStatus status);
    long countByRoleAndStatus(WorkforceRole role, WorkforceAllocationStatus status);
}
