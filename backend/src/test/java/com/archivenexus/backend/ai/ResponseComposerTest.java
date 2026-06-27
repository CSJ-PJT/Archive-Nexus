package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseComposerTest {
    @Test
    void composesPartialResponseWhenOneAgentFails() {
        AgentContext context = new AgentContext("생산과 정비 분석", null, "RECENT", 10,
                List.of(Intent.PRODUCTION, Intent.MAINTENANCE), "query", "operator", Map.of());
        AgentResult success = new AgentResult(
                "ProductionAgent", Intent.PRODUCTION, "생산은 정상입니다.",
                List.of(new AgentEvidence("output", "생산량", "100", "order")), List.of(), 0.9, 2,
                AgentExecutionStatus.COMPLETED, null, false
        );
        AgentResult failure = AgentResult.failed("MaintenanceAgent", Intent.MAINTENANCE,
                new IllegalStateException("sensor unavailable"), 3);

        ResponseComposer.ComposedResponse response = new ResponseComposer().compose(context, List.of(success, failure));

        assertThat(response.partialFailure()).isTrue();
        assertThat(response.answer()).contains("생산은 정상입니다", "일부 Agent가 실패");
        assertThat(response.evidence()).hasSize(1);
    }
}
