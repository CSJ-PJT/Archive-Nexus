package com.archivenexus.backend.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NexusTaskModels {
    private NexusTaskModels() {}

    public enum TaskType { MANUFACTURING_QUERY, SIMULATOR_TICK, SCENARIO_RECOVERY }
    public enum TaskStatus {
        DRAFT, PENDING, ANALYZING, WAITING_APPROVAL, APPROVED, RUNNING, VERIFYING,
        SUCCESS, FAILED, REJECTED, CANCELLED, RETRY_REQUESTED
    }

    public record CreateTaskRequest(@NotBlank String title, @NotNull TaskType type, String factoryId,
                                    String question, String requestedBy, @Min(1) @Max(5) Integer maxAttempts) {}
    public record TaskResponse(String id, String title, TaskType type, String factoryId, String question,
                               String requestedBy, TaskStatus status, int attemptCount, int maxAttempts,
                               String resultSummary, List<Map<String, Object>> evidence,
                               List<String> recommendation, Double confidence, String correlationId,
                               String workflowId, String approvalId, String rpaTaskId, String errorMessage,
                               Instant createdAt, Instant startedAt, Instant completedAt, Instant updatedAt) {}
    public record TaskLogResponse(long id, String taskId, String level, String message, Instant createdAt) {}
    public record TaskDetailResponse(TaskResponse task, List<TaskLogResponse> logs) {}
}
