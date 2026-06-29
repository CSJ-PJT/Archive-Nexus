package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentRouterTest {
    @Test
    void routesProductionQuestion() {
        assertThat(routerWithoutModel().route("3공장의 생산량이 감소했는지 확인해줘"))
                .containsExactly(Intent.PRODUCTION);
    }

    @Test
    void routesQualityQuestion() {
        assertThat(routerWithoutModel().route("최근 Lot 불량률과 품질 상태를 알려줘"))
                .containsExactly(Intent.QUALITY);
    }

    @Test
    void routesMaintenanceQuestion() {
        assertThat(routerWithoutModel().route("설비 진동과 온도 이상을 점검해줘"))
                .containsExactly(Intent.MAINTENANCE);
    }

    @Test
    void routesCompositeQuestionToMultipleAgents() {
        assertThat(routerWithoutModel().route("생산량 감소 원인과 설비 이상 여부를 확인해줘"))
                .containsExactly(Intent.PRODUCTION, Intent.MAINTENANCE);
    }

    @Test
    void returnsUnknownForUnclassifiedQuestion() {
        assertThat(routerWithoutModel().route("오늘 운영 보고서를 예쁘게 정리해줘"))
                .containsExactly(Intent.UNKNOWN);
    }

    @Test
    void fallsBackToRulesWhenChatModelFails() {
        ObjectProvider<ChatModel> provider = provider();
        ChatModel model = mock(ChatModel.class);
        when(provider.getIfAvailable()).thenReturn(model);
        when(model.call(anyString())).thenThrow(new IllegalStateException("model unavailable"));
        IntentRouter router = new IntentRouter(provider);

        assertThat(router.route("생산 실적을 분석해줘")).containsExactly(Intent.PRODUCTION);
    }

    private IntentRouter routerWithoutModel() {
        ObjectProvider<ChatModel> provider = provider();
        when(provider.getIfAvailable()).thenReturn(null);
        return new IntentRouter(provider);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatModel> provider() {
        return mock(ObjectProvider.class);
    }
}
