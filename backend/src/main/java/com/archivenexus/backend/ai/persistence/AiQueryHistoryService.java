package com.archivenexus.backend.ai.persistence;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.AiQueryResponse;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AiQueryHistoryService {
    private final AiQueryRepository repository;
    private final ObjectMapper objectMapper;

    public AiQueryHistoryService(AiQueryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiQueryResponse save(AiQueryResponse response) {
        try {
            repository.save(new AiQueryEntity(
                    response.queryId(), response.question(), response.requestedBy(), response.selectedFactoryId(),
                    write(response.routedIntents()), write(response.invokedAgents()), write(response.agentResults()),
                    response.answer(), write(response.evidence()), write(response.recommendedActions()),
                    response.confidence(), response.executionStatus(), response.executionTimeMs(),
                    response.errorMessage(), response.rpaTaskId(), response.createdAt()
            ));
            return response;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("AI query history serialization failed", error);
        }
    }

    @Transactional(readOnly = true)
    public List<AiQueryResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AiQueryResponse> findById(String id) {
        return repository.findById(id).map(this::toResponse);
    }

    public long count() {
        return repository.count();
    }

    public String recentRecommendation() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(this::toResponse)
                .flatMap(response -> response.recommendedActions().stream().findFirst())
                .orElse("최근 권장 조치 없음");
    }

    public long agentFailureCount() {
        return findAll().stream()
                .flatMap(response -> response.agentResults().stream())
                .filter(result -> result.status() == com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus.FAILED)
                .count();
    }

    private AiQueryResponse toResponse(AiQueryEntity entity) {
        try {
            return new AiQueryResponse(
                    entity.queryId(), entity.originalQuestion(), entity.requestedBy(), entity.selectedFactoryId(),
                    read(entity.routedIntentsJson(), new TypeReference<List<Intent>>() {}),
                    read(entity.invokedAgentsJson(), new TypeReference<List<String>>() {}),
                    read(entity.agentResultsJson(), new TypeReference<List<AgentResult>>() {}),
                    entity.finalAnswer(),
                    read(entity.evidenceJson(), new TypeReference<List<AgentEvidence>>() {}),
                    read(entity.recommendedActionsJson(), new TypeReference<List<String>>() {}),
                    entity.confidence(),
                    entity.executionStatus().equals("PARTIAL_SUCCESS"),
                    entity.rpaTaskId() != null,
                    entity.rpaTaskId(), entity.executionStatus(), entity.executionTimeMs(), entity.errorMessage(), entity.createdAt()
            );
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored AI query history is not readable", error);
        }
    }

    private String write(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T read(String value, TypeReference<T> type) throws JsonProcessingException {
        return objectMapper.readValue(value, type);
    }
}
