package com.archivenexus.backend.task;

import com.archivenexus.backend.audit.AuditService;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import com.archivenexus.backend.task.NexusTaskModels.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class TaskStateStore {
    private final NexusTaskRepository tasks;
    private final NexusTaskLogRepository logs;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final DomainAggregateProjectionService projections;

    public TaskStateStore(NexusTaskRepository tasks, NexusTaskLogRepository logs, ObjectMapper mapper,
                          AuditService audit, DomainAggregateProjectionService projections) {
        this.tasks = tasks;
        this.logs = logs;
        this.mapper = mapper;
        this.audit = audit;
        this.projections = projections;
    }

    @Transactional
    public Optional<NexusTaskEntity> attachWorkflow(String id, String workflowId) {
        return tasks.findById(id).filter(task -> Set.of(TaskStatus.DRAFT, TaskStatus.RETRY_REQUESTED).contains(task.status()))
                .map(task -> {
                    task.attachWorkflow(workflowId, Instant.now());
                    event(task, "INFO", "ArchiveOS Workflow가 연결되었습니다.", "WORKFLOW_ROUTED",
                            "ArchiveOS workflow dispatch", Map.of("workflowId", workflowId));
                    return task;
                });
    }

    @Transactional
    public Optional<NexusTaskEntity> startAnalysis(String id) {
        return tasks.findById(id).filter(task -> task.status() == TaskStatus.PENDING).map(task -> {
            task.startAnalysis(Instant.now());
            event(task, "INFO", "Manufacturing 분석을 시작했습니다.", "TASK_ANALYZING", "Task analysis started", Map.of());
            return task;
        });
    }

    @Transactional
    public void analysis(String id, String summary, Object evidence, Object recommendation, Double confidence, String rpaTaskId) {
        tasks.findById(id).filter(task -> task.status() == TaskStatus.ANALYZING).ifPresent(task -> {
            task.analysis(summary, json(evidence), json(recommendation), confidence, rpaTaskId, Instant.now());
            event(task, "INFO", "분석 근거와 권장 조치를 저장했습니다.", "ANALYSIS_COMPLETED",
                    "Evidence and recommendation recorded", Map.of("confidence", confidence == null ? 0 : confidence));
        });
    }

    @Transactional public void waitApproval(String id) { tasks.findById(id).filter(task -> task.status() == TaskStatus.ANALYZING).ifPresent(task -> { task.transition(TaskStatus.WAITING_APPROVAL, Instant.now()); event(task, "WARN", "PM 승인을 기다립니다.", "APPROVAL_REQUIRED", "PM approval required", Map.of()); }); }
    @Transactional public void approve(String id, String approvalId) { tasks.findById(id).filter(task -> task.status() == TaskStatus.WAITING_APPROVAL).ifPresent(task -> { task.approval(approvalId, true, Instant.now()); event(task, "INFO", "ArchiveOS 승인이 확인되었습니다.", "TASK_APPROVED", "ArchiveOS approval synchronized", Map.of("approvalId", approvalId == null ? "" : approvalId)); }); }
    @Transactional public void reject(String id, String approvalId) { tasks.findById(id).filter(task -> task.status() == TaskStatus.WAITING_APPROVAL).ifPresent(task -> { task.approval(approvalId, false, Instant.now()); event(task, "WARN", "ArchiveOS에서 작업이 반려되었습니다.", "TASK_REJECTED", "ArchiveOS rejection synchronized", Map.of("approvalId", approvalId == null ? "" : approvalId)); }); }
    @Transactional public boolean startRunning(String id) {
        return tasks.findById(id).filter(task -> Set.of(TaskStatus.ANALYZING, TaskStatus.APPROVED).contains(task.status())).map(task -> {
            task.start(Instant.now());
            event(task, "INFO", "승인된 Nexus Action을 실행합니다.", "TASK_RUNNING", "Nexus action started", Map.of());
            return true;
        }).orElse(false);
    }
    @Transactional public void verifying(String id) { tasks.findById(id).filter(task -> task.status() == TaskStatus.RUNNING).ifPresent(task -> { task.verify(Instant.now()); event(task, "INFO", "실행 결과를 검증합니다.", "TASK_VERIFYING", "Action result verification", Map.of()); }); }
    @Transactional public void succeed(String id, String summary) { tasks.findById(id).filter(task -> task.status() == TaskStatus.VERIFYING).ifPresent(task -> { task.succeed(summary, Instant.now()); event(task, "INFO", "작업이 성공적으로 완료됐습니다.", "TASK_SUCCESS", "Task completed", Map.of()); }); }
    @Transactional public void fail(String id, String message) { tasks.findById(id).filter(task -> !Set.of(TaskStatus.SUCCESS, TaskStatus.REJECTED, TaskStatus.CANCELLED).contains(task.status())).ifPresent(task -> { task.fail(message, Instant.now()); event(task, "ERROR", "작업 실패: " + message, "TASK_FAILED", message, Map.of()); }); }
    @Transactional public void retryRequested(String id, String reason) { tasks.findById(id).ifPresent(task -> { task.resetForRetry(Instant.now()); event(task, "WARN", "재시도가 요청되었습니다.", "RETRY_REQUESTED", reason, Map.of()); }); }
    @Transactional public void log(String id, String level, String message) { logs.save(new NexusTaskLogEntity(id, level, message, Instant.now())); }

    private void event(NexusTaskEntity task, String level, String message, String action, String reason, Map<String, ?> details) {
        logs.save(new NexusTaskLogEntity(task.id(), level, message, Instant.now()));
        audit.record(task.requestedBy(), action, reason, task.id(), task.correlationId(), task.workflowId(), details);
        projections.projectTask(task);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { return "[]"; }
    }
}
