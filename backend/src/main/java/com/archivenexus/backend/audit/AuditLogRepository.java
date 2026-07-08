package com.archivenexus.backend.audit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AuditLogRepository extends JpaRepository<AuditLogEntity,Long>{List<AuditLogEntity> findTop200ByOrderByOccurredAtDesc();}
