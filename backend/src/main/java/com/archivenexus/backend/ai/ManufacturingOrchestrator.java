package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiDashboardSummary;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryRequest;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryResponse;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import com.archivenexus.backend.ai.ResponseComposer.ComposedResponse;
import com.archivenexus.backend.ai.persistence.AiQueryHistoryService;
import com.archivenexus.backend.domain.DomainModels.Factory;
import com.archivenexus.backend.domain.DomainModels.RpaTask;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ManufacturingOrchestrator {
    private static final Pattern FACTORY_NUMBER_PATTERN = Pattern.compile("([123])\\s*공장");

    private final IntentRouter intentRouter;
    private final List<ManufacturingAgent> agents;
    private final ResponseComposer responseComposer;
    private final AiQueryHistoryService history;
    private final NexusStateService nexus;
    private final TaskExecutor executor;
    private final AiMetrics metrics;

    public ManufacturingOrchestrator(
            IntentRouter intentRouter,
            List<ManufacturingAgent> agents,
            ResponseComposer responseComposer,
            AiQueryHistoryService history,
            NexusStateService nexus,
            @Qualifier("manufacturingAgentExecutor") TaskExecutor executor,
            AiMetrics metrics
    ) {
        this.intentRouter = intentRouter;
        this.agents = agents.stream().sorted(Comparator.comparingInt(ManufacturingAgent::getPriority)).toList();
        this.responseComposer = responseComposer;
        this.history = history;
        this.nexus = nexus;
        this.executor = executor;
        this.metrics = metrics;
    }

    public AiQueryResponse execute(AiQueryRequest request) {
        long startedAt = System.nanoTime();
        String queryId = "AIQ-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String requestedBy = valueOrDefault(request.requestedBy(), "operator");
        String factoryId = resolveFactoryId(request.factoryId(), request.question());
        List<Intent> intents = intentRouter.route(request.question());
        AgentContext context = new AgentContext(
                request.question(), factoryId, valueOrDefault(request.timeRange(), "RECENT_20_TICKS"),
                nexus.status().tick(), intents, queryId, requestedBy,
                request.metadata() == null ? Map.of() : Map.copyOf(request.metadata())
        );

        metrics.queryReceived();
        intents.forEach(metrics::intentRouted);
        nexus.recordAgentInteraction("AGENT_QUERY_RECEIVED", factoryId, queryId + ":" + request.question());
        nexus.recordAgentInteraction("AGENT_ROUTED", factoryId, queryId + ":" + intents);

        List<ManufacturingAgent> selectedAgents = agents.stream()
                .filter(agent -> intents.stream().anyMatch(agent::supports))
                .toList();
        List<AgentResult> results = executeAgents(context, selectedAgents);
        ComposedResponse composed = responseComposer.compose(context, results);
        String rpaTaskId = createRpaTaskIfRequired(queryId, factoryId, composed);
        long executionTimeMs = elapsed(startedAt);
        String status = executionStatus(results, composed);
        String errorMessage = results.stream()
                .filter(result -> result.status() == AgentExecutionStatus.FAILED)
                .map(result -> result.agentName() + ": " + result.errorMessage())
                .reduce((left, right) -> left + "; " + right)
                .orElse(null);

        AiQueryResponse response = new AiQueryResponse(
                queryId, request.question(), requestedBy, factoryId, intents,
                selectedAgents.stream().map(ManufacturingAgent::getName).toList(), results,
                composed.answer(), composed.evidence(), composed.recommendedActions(), composed.confidence(),
                composed.partialFailure(), composed.approvalRequired(), rpaTaskId, status,
                executionTimeMs, errorMessage, Instant.now()
        );
        history.save(response);
        nexus.recordAgentInteraction("AGENT_RESPONSE_COMPOSED", factoryId, queryId + ":" + status);
        nexus.flushAgentState();
        return response;
    }

    public List<AiQueryResponse> history() {
        return history.findAll();
    }

    public java.util.Optional<AiQueryResponse> findById(String id) {
        return history.findById(id);
    }

    public AiDashboardSummary dashboardSummary() {
        return new AiDashboardSummary(
                history.count(), metrics.runningAgents(), history.agentFailureCount(),
                nexus.rpaTasks().stream().filter(task -> "MULTI_AGENT".equals(task.source())).count(),
                history.recentRecommendation()
        );
    }

    private List<AgentResult> executeAgents(AgentContext context, List<ManufacturingAgent> selectedAgents) {
        List<CompletableFuture<AgentResult>> futures = selectedAgents.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> executeAgent(agent, context), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private AgentResult executeAgent(ManufacturingAgent agent, AgentContext context) {
        long startedAt = System.nanoTime();
        metrics.agentStarted();
        nexus.recordAgentInteraction("AGENT_EXECUTION_STARTED", context.selectedFactoryId(), context.correlationId() + ":" + agent.getName());
        try {
            AgentResult result = agent.analyze(context);
            metrics.agentCompleted(Duration.ofNanos(System.nanoTime() - startedAt), false);
            nexus.recordAgentInteraction("AGENT_EXECUTION_COMPLETED", context.selectedFactoryId(), context.correlationId() + ":" + agent.getName());
            return result;
        } catch (RuntimeException error) {
            metrics.agentCompleted(Duration.ofNanos(System.nanoTime() - startedAt), true);
            nexus.recordAgentInteraction("AGENT_EXECUTION_FAILED", context.selectedFactoryId(), context.correlationId() + ":" + agent.getName() + ":" + error.getMessage());
            return AgentResult.failed(agent.getName(), supportedIntent(agent, context.detectedIntents()), error, elapsed(startedAt));
        }
    }

    private String createRpaTaskIfRequired(String queryId, String factoryId, ComposedResponse composed) {
        if (!composed.approvalRequired() || composed.recommendedActions().isEmpty()) {
            return null;
        }
        List<String> evidence = composed.evidence().stream()
                .map(value -> value.description() + "=" + value.value())
                .toList();
        RpaTask task = nexus.createAgentRpaTask(
                factoryId, queryId, "Multi-Agent 분석에서 운영 조치 필요",
                composed.recommendedActions().get(0), evidence, true
        );
        metrics.rpaTaskCreated();
        return task.id();
    }

    private Intent supportedIntent(ManufacturingAgent agent, List<Intent> intents) {
        return intents.stream().filter(agent::supports).findFirst().orElse(Intent.UNKNOWN);
    }

    private String executionStatus(List<AgentResult> results, ComposedResponse composed) {
        if (results.isEmpty() || results.stream().allMatch(result -> result.status() == AgentExecutionStatus.INSUFFICIENT_DATA)) {
            return "INSUFFICIENT_DATA";
        }
        return composed.partialFailure() ? "PARTIAL_SUCCESS" : "COMPLETED";
    }

    private String resolveFactoryId(String requestedFactoryId, String question) {
        String candidate = requestedFactoryId;
        if (candidate == null || candidate.isBlank()) {
            Matcher matcher = FACTORY_NUMBER_PATTERN.matcher(question);
            if (matcher.find()) {
                candidate = "FAC-" + switch (matcher.group(1)) {
                    case "1" -> "A";
                    case "2" -> "B";
                    default -> "C";
                };
            }
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String normalized = candidate.trim().toUpperCase().replace("FACTORY-", "FAC-");
        return nexus.factories().stream()
                .map(Factory::id)
                .filter(id -> id.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
