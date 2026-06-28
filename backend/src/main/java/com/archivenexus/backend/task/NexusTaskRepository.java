package com.archivenexus.backend.task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface NexusTaskRepository extends JpaRepository<NexusTaskEntity,String>{List<NexusTaskEntity> findAllByOrderByCreatedAtDesc();}
