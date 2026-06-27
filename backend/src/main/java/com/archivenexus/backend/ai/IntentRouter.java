package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class IntentRouter {
    private final ChatModel chatModel;

    public IntentRouter(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModel = chatModelProvider.getIfAvailable();
    }

    public List<Intent> route(String question) {
        if (chatModel != null) {
            try {
                String answer = chatModel.call("""
                        Classify this manufacturing question into one or more intents.
                        Allowed values: PRODUCTION, QUALITY, MAINTENANCE, UNKNOWN.
                        Return only comma-separated values.
                        Question: %s
                        """.formatted(question));
                List<Intent> routed = parseModelResponse(answer);
                if (!routed.isEmpty() && !routed.equals(List.of(Intent.UNKNOWN))) {
                    return routed;
                }
            } catch (RuntimeException ignored) {
                // Local and test environments must continue through deterministic rules.
            }
        }
        return routeByRule(question);
    }

    List<Intent> routeByRule(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Set<Intent> intents = new LinkedHashSet<>();
        if (containsAny(normalized, "생산", "생산량", "실적", "목표", "병목", "production", "output", "throughput")) {
            intents.add(Intent.PRODUCTION);
        }
        if (containsAny(normalized, "품질", "불량", "검사", "lot", "quality", "defect", "yield")) {
            intents.add(Intent.QUALITY);
        }
        if (containsAny(normalized, "설비", "정비", "진동", "온도", "전류", "고장", "maintenance", "vibration", "temperature", "machine")) {
            intents.add(Intent.MAINTENANCE);
        }
        return intents.isEmpty() ? List.of(Intent.UNKNOWN) : List.copyOf(intents);
    }

    private List<Intent> parseModelResponse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        Set<Intent> parsed = new LinkedHashSet<>();
        String normalized = response.toUpperCase(Locale.ROOT);
        for (Intent intent : Intent.values()) {
            if (normalized.contains(intent.name())) {
                parsed.add(intent);
            }
        }
        if (parsed.size() > 1) {
            parsed.remove(Intent.UNKNOWN);
        }
        return new ArrayList<>(parsed);
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
