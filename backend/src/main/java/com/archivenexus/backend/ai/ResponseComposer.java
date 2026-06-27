package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResponseComposer {
    public ComposedResponse compose(AgentContext context, List<AgentResult> results) {
        List<AgentResult> successful = results.stream()
                .filter(result -> result.status() != AgentExecutionStatus.FAILED)
                .toList();
        List<AgentEvidence> evidence = successful.stream().flatMap(result -> result.evidence().stream()).distinct().toList();
        List<String> actions = successful.stream().flatMap(result -> result.recommendedActions().stream()).distinct().toList();
        double confidence = successful.stream().mapToDouble(AgentResult::confidence).average().orElse(0);
        boolean partialFailure = results.stream().anyMatch(result -> result.status() == AgentExecutionStatus.FAILED);
        boolean insufficient = successful.isEmpty() || successful.stream().allMatch(result -> result.status() == AgentExecutionStatus.INSUFFICIENT_DATA);
        boolean approvalRequired = successful.stream().anyMatch(AgentResult::actionRequired);

        String answer;
        if (insufficient) {
            answer = "질문을 분류했지만 판단할 데이터가 부족함";
        } else {
            String findings = successful.stream().map(AgentResult::summary).distinct().reduce((left, right) -> left + " " + right).orElse("");
            answer = "요청 '%s'에 대한 통합 판단입니다. %s".formatted(context.originalQuestion(), findings);
            if (partialFailure) {
                answer += " 일부 Agent가 실패해 성공한 분석만 반영했습니다.";
            }
        }
        return new ComposedResponse(answer, evidence, actions, confidence, partialFailure, approvalRequired);
    }

    public record ComposedResponse(
            String answer,
            List<AgentEvidence> evidence,
            List<String> recommendedActions,
            double confidence,
            boolean partialFailure,
            boolean approvalRequired
    ) {
    }
}
