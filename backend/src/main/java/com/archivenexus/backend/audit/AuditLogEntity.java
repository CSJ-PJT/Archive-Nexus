package com.archivenexus.backend.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="nexus_audit_logs")
public class AuditLogEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String actor;
    @Column(nullable=false,length=100) private String action;
    @Column(columnDefinition="text") private String reason;
    @Column(name="task_id",length=48) private String taskId;
    @Column(name="correlation_id",length=80) private String correlationId;
    @Column(name="workflow_id",length=80) private String workflowId;
    @Column(name="details_json",nullable=false,columnDefinition="text") private String detailsJson;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected AuditLogEntity(){}
    public AuditLogEntity(String actor,String action,String reason,String taskId,String correlationId,String workflowId,String detailsJson,Instant occurredAt){this.actor=actor;this.action=action;this.reason=reason;this.taskId=taskId;this.correlationId=correlationId;this.workflowId=workflowId;this.detailsJson=detailsJson;this.occurredAt=occurredAt;}
    public Long id(){return id;} public String actor(){return actor;} public String action(){return action;} public String reason(){return reason;} public String taskId(){return taskId;} public String correlationId(){return correlationId;} public String workflowId(){return workflowId;} public String detailsJson(){return detailsJson;} public Instant occurredAt(){return occurredAt;}
}
