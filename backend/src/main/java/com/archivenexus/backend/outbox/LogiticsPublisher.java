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
public class LogiticsPublisher {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String bulkEndpoint;
    private final boolean enabled;
    private final Duration timeout;

    public LogiticsPublisher(ObjectMapper mapper,
                             @Value("${archive.integrations.logitics.base-url:http://localhost:8092}") String baseUrl,
                             @Value("${archive.integrations.logitics.bulk-endpoint:/api/events/nexus/bulk}") String bulkEndpoint,
                             @Value("${archive.integrations.logitics.enabled:false}") boolean enabled,
                             @Value("${archive.integrations.logitics.timeout-ms:3000}") long timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.bulkEndpoint = normalizeEndpoint(bulkEndpoint);
        this.enabled = enabled;
        this.timeout = Duration.ofMillis(Math.max(250, timeoutMs));
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String targetUrl() {
        return baseUrl + bulkEndpoint;
    }

    public void publish(List<OutboxEventEntity> events) {
        if (!enabled) {
            throw new IllegalStateException("Archive-Logitics publishing is disabled.");
        }
        try {
            Map<String, Object> body = Map.of("events", events.stream().map(this::payload).toList());
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Archive-Logitics returned HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Archive-Logitics publish failed", error);
        }
    }

    public String health() {
        if (!enabled) {
            return "DISABLED";
        }
        String actuator = get(baseUrl + "/actuator/health");
        return "UNAVAILABLE".equals(actuator) ? get(baseUrl + "/api/operations/summary") : actuator;
    }

    private Map<String, Object> payload(OutboxEventEntity event) {
        return Map.of(
                "eventId", event.eventId(),
                "idempotencyKey", event.idempotencyKey(),
                "source", event.source(),
                "eventType", event.eventType().name(),
                "schemaVersion", event.schemaVersion(),
                "occurredAt", event.occurredAt().toString(),
                "payload", readPayload(event.payload())
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

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? "AVAILABLE" : "DEGRADED";
        } catch (Exception error) {
            return "UNAVAILABLE";
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "/api/events/nexus/bulk";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }
}
