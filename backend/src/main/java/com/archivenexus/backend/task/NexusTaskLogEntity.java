package com.archivenexus.backend.task;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "nexus_task_logs")
public class NexusTaskLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "task_id", nullable = false, length = 48) private String taskId;
    @Column(nullable = false, length = 16) private String level;
    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected NexusTaskLogEntity() {}
    public NexusTaskLogEntity(String taskId,String level,String message,Instant createdAt){this.taskId=taskId;this.level=level;this.message=message;this.createdAt=createdAt;}
    public Long id(){return id;} public String taskId(){return taskId;} public String level(){return level;}
    public String message(){return message;} public Instant createdAt(){return createdAt;}
}
