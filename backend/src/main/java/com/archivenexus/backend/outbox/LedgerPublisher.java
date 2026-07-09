package com.archivenexus.backend.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class LedgerPublisher {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final boolean enabled;
    private final Duration timeout;

    public LedgerPublisher(ObjectMapper mapper,
                           @Value("${archive-nexus.ledger.base-url:http://host.docker.internal:18080}") String baseUrl,
                           @Value("${archive-nexus.ledger.enabled:false}") boolean enabled,
                           @Value("${archive-nexus.ledger.timeout-ms:2000}") long timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.enabled = enabled;
        this.timeout = Duration.ofMillis(Math.max(250, timeoutMs));
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public void publish(List<OutboxEventEntity> events) {
        if (!enabled) {
            throw new IllegalStateException("Archive-Ledger publishing is disabled.");
        }
        try {
            List<Map<String, Object>> body = events.stream().map(this::payload).toList();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/events/nexus/bulk"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Archive-Ledger returned HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Archive-Ledger publish failed", error);
        }
    }

    private Map<String, Object> payload(OutboxEventEntity event) {
        return Map.of(
                "eventId", event.eventId(),
                "idempotencyKey", event.idempotencyKey(),
                "eventType", event.eventType().name(),
                "aggregateType", event.aggregateType(),
                "aggregateId", event.aggregateId(),
                "source", event.source(),
                "schemaVersion", event.schemaVersion(),
                "payload", readPayload(event.payload()),
                "occurredAt", event.occurredAt().toString()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String payload) {
        try {
            return mapper.readValue(payload, Map.class);
        } catch (Exception error) {
            return Map.of("rawPayload", payload);
        }
    }
}
