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
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LedgerPublisher {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String bulkEndpoint;
    private final boolean enabled;
    private final Duration timeout;
    private final String token;

    public LedgerPublisher(ObjectMapper mapper,
                           @Value("${archive.integrations.ledger.base-url:${archive-nexus.ledger.base-url:http://localhost:18080}}") String baseUrl,
                           @Value("${archive.integrations.ledger.bulk-endpoint:/api/events/nexus/bulk}") String bulkEndpoint,
                           @Value("${archive.integrations.ledger.enabled:${archive-nexus.ledger.enabled:false}}") boolean enabled,
                           @Value("${archive.integrations.ledger.timeout-ms:${archive-nexus.ledger.timeout-ms:3000}}") long timeoutMs,
                           @Value("${archive.tokens.nexus-to-ledger:}") String token) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.bulkEndpoint = normalizeEndpoint(bulkEndpoint);
        this.enabled = enabled;
        this.timeout = Duration.ofMillis(Math.max(250, timeoutMs));
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.token = token;
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
            throw new IllegalStateException("Archive-Ledger publishing is disabled.");
        }
        try {
            List<Map<String, Object>> body = events.stream().map(this::payload).toList();
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Archive-Source-System", "archive-nexus")
                    .header("X-Archive-Service-Scope", "ledger:ingest")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Archive-Ledger returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.has("data") ? root.path("data") : root;
            LinkedHashSet<String> accepted = new LinkedHashSet<>();
            java.util.LinkedHashMap<String, String> rejected = new java.util.LinkedHashMap<>();
            for (JsonNode result : data.path("results")) {
                String eventId = result.path("eventId").asText();
                boolean duplicate = result.path("duplicate").asBoolean(false);
                String status = result.path("status").asText("");
                if (duplicate || "ACCEPTED".equalsIgnoreCase(status) || "PROCESSED".equalsIgnoreCase(status)) accepted.add(eventId);
                else rejected.put(eventId, result.path("message").asText("Ledger rejected event"));
            }
            if (accepted.size() != events.size()) throw new IllegalStateException("Archive-Ledger acknowledgement is incomplete: " + rejected);
            return new OutboxModels.PublishAcknowledgement(accepted, rejected);
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Archive-Ledger publish failed", error);
        }
    }

    public String health() {
        if (!enabled) {
            return "DISABLED";
        }
        return get(baseUrl + "/actuator/health");
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

    private Map<String, Object> payload(OutboxEventEntity event) {
        Map<String, Object> domainPayload = new LinkedHashMap<>(readPayload(event.payload()));
        domainPayload.put("sourceSystem", "archive-nexus");
        domainPayload.put("targetSystem", "archive-ledger");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("idempotencyKey", event.idempotencyKey());
        envelope.put("eventType", event.eventType().name());
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("aggregateId", event.aggregateId());
        envelope.put("source", "archive-nexus");
        envelope.put("schemaVersion", event.schemaVersion());
        envelope.put("payload", domainPayload);
        envelope.put("occurredAt", event.occurredAt().toString());
        copyTrace(domainPayload, envelope, "simulationRunId");
        copyTrace(domainPayload, envelope, "settlementCycleId");
        copyTrace(domainPayload, envelope, "correlationId");
        copyTrace(domainPayload, envelope, "causationId");
        copyTrace(domainPayload, envelope, "hopCount");
        copyTrace(domainPayload, envelope, "maxHop");
        return envelope;
    }

    private void copyTrace(Map<String, Object> payload, Map<String, Object> envelope, String field) {
        Object value = payload.get(field);
        if (value != null) envelope.put(field, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String payload) {
        try {
            return mapper.readValue(payload, Map.class);
        } catch (Exception error) {
            return Map.of("rawPayload", payload);
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "/api/events/nexus/bulk";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }
}
