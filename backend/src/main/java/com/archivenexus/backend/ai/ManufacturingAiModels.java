package com.archivenexus.backend.ai;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ManufacturingAiModels {
    private ManufacturingAiModels() {
    }

    public enum Intent {
        PRODUCTION,
        QUALITY,
        MAINTENANCE,
        INVENTORY,
        LOGISTICS,
        CROSS_DOMAIN,
        UNKNOWN
    }

    public enum AgentExecutionStatus {
        COMPLETED,
        INSUFFICIENT_DATA,
        FAILED
    }

    public record AgentContext(
            String originalQuestion,
            String selectedFactoryId,
            String selectedTimeRange,
            long currentTick,
            List<Intent> detectedIntents,
            String correlationId,
            String requestedBy,
            Map<String, String> metadata
    ) {
    }

    public record AgentEvidence(String type, String description, String value, String source) {
    }

    public record AgentResult(
            String agentName,
            Intent intent,
            String summary,
            List<AgentEvidence> evidence,
            List<String> recommendedActions,
            double confidence,
            long executionTimeMs,
            AgentExecutionStatus status,
            String errorMessage,
            boolean actionRequired
    ) {
        public static AgentResult failed(String agentName, Intent intent, Throwable error, long executionTimeMs) {
            return new AgentResult(
                    agentName,
                    intent,
                    "Agent 분석에 실패했습니다.",
                    List.of(),
                    List.of(),
                    0,
                    executionTimeMs,
                    AgentExecutionStatus.FAILED,
                    error.getMessage(),
                    false
            );
        }
    }

    public record AiQueryRequest(
            @NotBlank String question,
            String factoryId,
            String timeRange,
            String requestedBy,
            Map<String, String> metadata
    ) {
    }

    public record AiQueryResponse(
            String queryId,
            String question,
            String requestedBy,
            String selectedFactoryId,
            List<Intent> routedIntents,
            List<String> invokedAgents,
            List<AgentResult> agentResults,
            String answer,
            List<AgentEvidence> evidence,
            List<String> recommendedActions,
            double confidence,
            boolean partialFailure,
            boolean approvalRequired,
            String rpaTaskId,
            String executionStatus,
            long executionTimeMs,
            String errorMessage,
            Instant createdAt
    ) {
    }

    public record AiDashboardSummary(
            long totalQueries,
            int runningAgents,
            long agentFailures,
            long agentRpaTasks,
            String recentRecommendation
    ) {
    }
}
