package com.archivenexus.backend.task;

import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryRequest;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryResponse;
import com.archivenexus.backend.ai.ManufacturingOrchestrator;
import com.archivenexus.backend.archiveos.ArchiveOsWorkflowClient;
import com.archivenexus.backend.notification.DiscordCriticalNotifier;
import com.archivenexus.backend.service.NexusStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NexusTaskRunner {
    private final NexusTaskRepository tasks;
    private final TaskStateStore state;
    private final ManufacturingOrchestrator orchestrator;
    private final NexusStateService nexus;
    private final DiscordCriticalNotifier notifier;
    private final ArchiveOsWorkflowClient workflows;
    private final ObjectMapper mapper;

    public NexusTaskRunner(NexusTaskRepository tasks, TaskStateStore state, ManufacturingOrchestrator orchestrator,
                           NexusStateService nexus, DiscordCriticalNotifier notifier,
                           ArchiveOsWorkflowClient workflows, ObjectMapper mapper) {
        this.tasks = tasks;
        this.state = state;
        this.orchestrator = orchestrator;
        this.nexus = nexus;
        this.notifier = notifier;
        this.workflows = workflows;
        this.mapper = mapper;
    }

    public void execute(String id) {
        NexusTaskEntity task = tasks.findById(id).orElse(null);
        if (task == null || task.status() != NexusTaskModels.TaskStatus.ANALYZING) return;
        try {
            if (task.type() == NexusTaskModels.TaskType.SIMULATOR_TICK) {
                executeTick(task);
                complete(task, "Simulator tick " + nexus.status().tick() + " 생성 완료");
                return;
            }
            AiQueryResponse result = executeQuery(task);
            List<Map<String, Object>> evidence = result.evidence().stream()
                    .map(item -> Map.<String, Object>of("type", item.type(), "description", item.description(),
                            "value", item.value(), "source", item.source()))
                    .toList();
            state.analysis(id, result.answer(), evidence, result.recommendedActions(), result.confidence(), result.rpaTaskId());
            if (result.approvalRequired()) {
                ArchiveOsWorkflowClient.WorkflowRef workflow = workflows.requestApproval(task.workflowId());
                state.waitApproval(id);
                state.log(id, "INFO", "ArchiveOS approval workflow " + workflow.id() + "로 전달했습니다.");
                return;
            }
            if (!state.startRunning(id)) return;
            complete(tasks.findById(id).orElseThrow(), result.answer());
        } catch (RuntimeException error) {
            fail(task, error);
        }
    }

    public void resumeApproved(String id) {
        NexusTaskEntity task = tasks.findById(id).orElse(null);
        if (task == null || task.status() != NexusTaskModels.TaskStatus.APPROVED) return;
        try {
            if (!state.startRunning(id)) return;
            if (task.rpaTaskId() != null) nexus.approve(task.rpaTaskId());
            complete(tasks.findById(id).orElseThrow(), task.resultSummary() == null
                    ? "Approved Nexus action completed" : task.resultSummary());
        } catch (RuntimeException error) {
            fail(task, error);
        }
    }

    private AiQueryResponse executeQuery(NexusTaskEntity task) {
        if (task.question() == null || task.question().isBlank()) {
            throw new IllegalArgumentException("MANUFACTURING_QUERY 작업에는 question이 필요합니다.");
        }
        state.log(task.id(), "INFO", "Manufacturing Orchestrator에 분석을 요청합니다.");
        AiQueryResponse result = orchestrator.execute(new AiQueryRequest(task.question(), task.factoryId(), null,
                task.requestedBy(), Map.of("sourceTaskId", task.id(), "workflowId", task.workflowId())));
        if ("INSUFFICIENT_DATA".equals(result.executionStatus())) {
            throw new IllegalStateException("판단할 데이터가 부족합니다.");
        }
        return result;
    }

    private void executeTick(NexusTaskEntity task) {
        state.log(task.id(), "INFO", "Simulator tick 생성을 요청합니다.");
        nexus.generateTick();
        state.analysis(task.id(), "Simulator tick generated", List.of(), List.of(), 1.0, null);
        if (!state.startRunning(task.id())) return;
    }

    private void complete(NexusTaskEntity task, String summary) {
        state.verifying(task.id());
        state.succeed(task.id(), summary);
        NexusTaskEntity completed = tasks.findById(task.id()).orElseThrow();
        callback(completed, "success", summary);
    }

    private void fail(NexusTaskEntity task, RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        state.fail(task.id(), message);
        callback(tasks.findById(task.id()).orElse(task), "failed", message);
        notifier.notifyCritical("Archive Nexus 작업 실패", task.title() + " (" + task.id() + "): " + message);
    }

    private void callback(NexusTaskEntity task, String status, String summary) {
        if (task.workflowId() == null) return;
        try {
            workflows.callback(task.workflowId(), new ArchiveOsWorkflowClient.WorkflowCallback(status, summary,
                    readEvidence(task.evidenceJson()), readRecommendation(task.recommendationJson()), task.confidence(),
                    task.correlationId(), task.id(), task.approvalId()));
            state.log(task.id(), "INFO", "결과 callback이 ArchiveOS History에 기록되었습니다.");
        } catch (RuntimeException error) {
            state.log(task.id(), "WARN", "ArchiveOS callback 재시도 필요: " + error.getMessage());
        }
    }

    private List<Map<String, Object>> readEvidence(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception error) {
            return List.of();
        }
    }

    private List<String> readRecommendation(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception error) {
            return List.of();
        }
    }
}
