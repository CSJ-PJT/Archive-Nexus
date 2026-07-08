package com.archivenexus.backend.task;

import com.archivenexus.backend.task.NexusTaskModels.TaskStatus;
import com.archivenexus.backend.task.NexusTaskModels.TaskType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "nexus_tasks")
public class NexusTaskEntity {
    @Id @Column(length = 48) private String id;
    @Column(nullable = false, length = 180) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "task_type", nullable = false, length = 40) private TaskType type;
    @Column(name = "factory_id", length = 40) private String factoryId;
    @Column(columnDefinition = "text") private String question;
    @Column(name = "requested_by", nullable = false, length = 120) private String requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TaskStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "result_summary", columnDefinition = "text") private String resultSummary;
    @Column(name = "evidence_json", columnDefinition = "text") private String evidenceJson;
    @Column(name = "recommendation_json", columnDefinition = "text") private String recommendationJson;
    private Double confidence;
    @Column(name = "correlation_id", nullable = false, length = 80) private String correlationId;
    @Column(name = "workflow_id", length = 80) private String workflowId;
    @Column(name = "approval_id", length = 80) private String approvalId;
    @Column(name = "rpa_task_id", length = 80) private String rpaTaskId;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected NexusTaskEntity() {}
    public NexusTaskEntity(String id, String title, TaskType type, String factoryId, String question,
                           String requestedBy, int maxAttempts, Instant now) {
        this.id=id; this.title=title; this.type=type; this.factoryId=factoryId; this.question=question;
        this.requestedBy=requestedBy; this.status=TaskStatus.DRAFT; this.maxAttempts=maxAttempts;
        this.correlationId="CORR-"+java.util.UUID.randomUUID().toString();
        this.createdAt=now; this.updatedAt=now;
    }
    public void transition(TaskStatus next, Instant now) { status=next; updatedAt=now; }
    public void startAnalysis(Instant now) { status=TaskStatus.ANALYZING; attemptCount++; startedAt=now; completedAt=null; errorMessage=null; updatedAt=now; }
    public void attachWorkflow(String value, Instant now) { workflowId=value; status=TaskStatus.PENDING; updatedAt=now; }
    public void analysis(String summary,String evidence,String recommendation,Double confidence,String rpaTaskId,Instant now){this.resultSummary=summary;this.evidenceJson=evidence;this.recommendationJson=recommendation;this.confidence=confidence;this.rpaTaskId=rpaTaskId;updatedAt=now;}
    public void approval(String approvalId, boolean approved, Instant now){this.approvalId=approvalId;status=approved?TaskStatus.APPROVED:TaskStatus.REJECTED;if(!approved)completedAt=now;updatedAt=now;}
    public void start(Instant now) { status=TaskStatus.RUNNING; if(startedAt==null)startedAt=now; completedAt=null; errorMessage=null; updatedAt=now; }
    public void verify(Instant now){status=TaskStatus.VERIFYING;updatedAt=now;}
    public void succeed(String summary, Instant now) { status=TaskStatus.SUCCESS; resultSummary=summary; errorMessage=null; completedAt=now; updatedAt=now; }
    public void fail(String message, Instant now) { status=TaskStatus.FAILED; errorMessage=message; completedAt=now; updatedAt=now; }
    public void cancel(Instant now) { status=TaskStatus.CANCELLED; completedAt=now; updatedAt=now; }
    public void resetForRetry(Instant now) { status=TaskStatus.RETRY_REQUESTED; errorMessage=null; completedAt=null; updatedAt=now; }
    public String id(){return id;} public String title(){return title;} public TaskType type(){return type;}
    public String factoryId(){return factoryId;} public String question(){return question;} public String requestedBy(){return requestedBy;}
    public TaskStatus status(){return status;} public int attemptCount(){return attemptCount;} public int maxAttempts(){return maxAttempts;}
    public String resultSummary(){return resultSummary;} public String errorMessage(){return errorMessage;}
    public String evidenceJson(){return evidenceJson;} public String recommendationJson(){return recommendationJson;}
    public Double confidence(){return confidence;} public String correlationId(){return correlationId;}
    public String workflowId(){return workflowId;} public String approvalId(){return approvalId;} public String rpaTaskId(){return rpaTaskId;}
    public Instant createdAt(){return createdAt;} public Instant startedAt(){return startedAt;}
    public Instant completedAt(){return completedAt;} public Instant updatedAt(){return updatedAt;}
}
