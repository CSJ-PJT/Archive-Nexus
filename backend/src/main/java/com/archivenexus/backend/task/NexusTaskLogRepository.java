package com.archivenexus.backend.task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface NexusTaskLogRepository extends JpaRepository<NexusTaskLogEntity,Long>{List<NexusTaskLogEntity> findAllByTaskIdOrderByCreatedAtAsc(String taskId);}
