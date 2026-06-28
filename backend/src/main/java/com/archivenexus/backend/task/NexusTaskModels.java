package com.archivenexus.backend.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class NexusTaskModels {
    private NexusTaskModels() {}

    public enum TaskType { MANUFACTURING_QUERY, SIMULATOR_TICK }
    public enum TaskStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }

    public record CreateTaskRequest(@NotBlank String title, @NotNull TaskType type, String factoryId,
                                    String question, String requestedBy, @Min(1) @Max(5) Integer maxAttempts) {}
    public record TaskResponse(String id, String title, TaskType type, String factoryId, String question,
                               String requestedBy, TaskStatus status, int attemptCount, int maxAttempts,
                               String resultSummary, String errorMessage, Instant createdAt, Instant startedAt,
                               Instant completedAt, Instant updatedAt) {}
    public record TaskLogResponse(long id, String taskId, String level, String message, Instant createdAt) {}
    public record TaskDetailResponse(TaskResponse task, List<TaskLogResponse> logs) {}
}
