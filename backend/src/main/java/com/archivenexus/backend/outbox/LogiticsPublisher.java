package com.archivenexus.backend.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final String token;

    public LogiticsPublisher(ObjectMapper mapper,
                             @Value("${archive.integrations.logitics.base-url:http://localhost:8092}") String baseUrl,
                             @Value("${archive.integrations.logitics.bulk-endpoint:/api/events/nexus/bulk}") String bulkEndpoint,
                             @Value("${archive.integrations.logitics.enabled:false}") boolean enabled,
                             @Value("${archive.integrations.logitics.timeout-ms:3000}") long timeoutMs,
                             @Value("${archive.tokens.nexus-to-logistics:}") String token) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.bulkEndpoint = normalizeEndpoint(bulkEndpoint);
        this.enabled = enabled;
        this.timeout = Duration.ofMillis(Math.max(250, timeoutMs));
        this.token = token;
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

    public OutboxModels.PublishAcknowledgement publish(List<OutboxEventEntity> events) {
        if (!enabled) {
            throw new IllegalStateException("Archive-Logitics publishing is disabled.");
        }
        try {
            Map<String, Object> body = Map.of("events", events.stream().map(this::payload).toList());
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Archive-Source-System", "archive-nexus")
                    .header("X-Archive-Service-Scope", "logistics:ingest")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Archive-Logitics returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.has("data") ? root.path("data") : root;
            int accepted = data.path("successCount").asInt(0) + data.path("duplicateCount").asInt(0);
            if (accepted != events.size() || data.path("failedCount").asInt(0) > 0) {
                throw new IllegalStateException("Archive-Logitics bulk acknowledgement is incomplete");
            }
            return new OutboxModels.PublishAcknowledgement(events.stream().map(OutboxEventEntity::eventId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)), Map.of());
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
        Map<String, Object> domainPayload = new LinkedHashMap<>(readPayload(event.payload()));
        domainPayload.put("riskLevel", logisticsRiskLevel(domainPayload.get("riskLevel")));
        domainPayload.put("sourceSystem", "archive-nexus");
        domainPayload.put("targetSystem", "archive-logistics");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("idempotencyKey", event.idempotencyKey());
        // The transport boundary is Nexus even when the payload originated in Market.
        envelope.put("source", "archive-nexus");
        envelope.put("eventType", event.eventType().name());
        envelope.put("schemaVersion", event.schemaVersion());
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("payload", domainPayload);
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String payload) {
        try {
            return mapper.readValue(payload, Map.class);
        } catch (Exception error) {
            return Map.of("rawPayload", payload);
        }
    }

    private int logisticsRiskLevel(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null) {
            return 0;
        }
        return switch (value.toString().trim().toUpperCase(java.util.Locale.ROOT)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 4;
            case "CRITICAL" -> 5;
            default -> 0;
        };
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
