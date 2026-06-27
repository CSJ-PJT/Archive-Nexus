package com.archivenexus.backend.ai.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_query_history")
public class AiQueryEntity {
    @Id
    @Column(name = "query_id", length = 80)
    private String queryId;

    @Lob @Column(name = "original_question", nullable = false)
    private String originalQuestion;
    @Column(name = "requested_by", nullable = false, length = 120)
    private String requestedBy;
    @Column(name = "selected_factory_id", length = 80)
    private String selectedFactoryId;
    @Lob @Column(name = "routed_intents_json", nullable = false)
    private String routedIntentsJson;
    @Lob @Column(name = "invoked_agents_json", nullable = false)
    private String invokedAgentsJson;
    @Lob @Column(name = "agent_results_json", nullable = false)
    private String agentResultsJson;
    @Lob @Column(name = "final_answer", nullable = false)
    private String finalAnswer;
    @Lob @Column(name = "evidence_json", nullable = false)
    private String evidenceJson;
    @Lob @Column(name = "recommended_actions_json", nullable = false)
    private String recommendedActionsJson;
    @Column(nullable = false)
    private double confidence;
    @Column(name = "execution_status", nullable = false, length = 40)
    private String executionStatus;
    @Column(name = "execution_time_ms", nullable = false)
    private long executionTimeMs;
    @Lob @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "rpa_task_id", length = 80)
    private String rpaTaskId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiQueryEntity() {
    }

    public AiQueryEntity(
            String queryId, String originalQuestion, String requestedBy, String selectedFactoryId,
            String routedIntentsJson, String invokedAgentsJson, String agentResultsJson, String finalAnswer,
            String evidenceJson, String recommendedActionsJson, double confidence, String executionStatus,
            long executionTimeMs, String errorMessage, String rpaTaskId, Instant createdAt
    ) {
        this.queryId = queryId;
        this.originalQuestion = originalQuestion;
        this.requestedBy = requestedBy;
        this.selectedFactoryId = selectedFactoryId;
        this.routedIntentsJson = routedIntentsJson;
        this.invokedAgentsJson = invokedAgentsJson;
        this.agentResultsJson = agentResultsJson;
        this.finalAnswer = finalAnswer;
        this.evidenceJson = evidenceJson;
        this.recommendedActionsJson = recommendedActionsJson;
        this.confidence = confidence;
        this.executionStatus = executionStatus;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.rpaTaskId = rpaTaskId;
        this.createdAt = createdAt;
    }

    public String queryId() { return queryId; }
    public String originalQuestion() { return originalQuestion; }
    public String requestedBy() { return requestedBy; }
    public String selectedFactoryId() { return selectedFactoryId; }
    public String routedIntentsJson() { return routedIntentsJson; }
    public String invokedAgentsJson() { return invokedAgentsJson; }
    public String agentResultsJson() { return agentResultsJson; }
    public String finalAnswer() { return finalAnswer; }
    public String evidenceJson() { return evidenceJson; }
    public String recommendedActionsJson() { return recommendedActionsJson; }
    public double confidence() { return confidence; }
    public String executionStatus() { return executionStatus; }
    public long executionTimeMs() { return executionTimeMs; }
    public String errorMessage() { return errorMessage; }
    public String rpaTaskId() { return rpaTaskId; }
    public Instant createdAt() { return createdAt; }
}
