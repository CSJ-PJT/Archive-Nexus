package com.archivenexus.backend.archiveos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

@Service
public class ArchiveOsHealthService {
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ArchiveOsHealthService(
            @Value("${archive-nexus.archiveos.base-url}") String baseUrl,
            @Value("${archive-nexus.archiveos.timeout-ms:2000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public ArchiveOsStatus status() {
        Instant checkedAt = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/health"))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ArchiveOsStatus("UNAVAILABLE", response.statusCode(),
                        "ArchiveOS health API returned HTTP " + response.statusCode(), checkedAt);
            }

            JsonNode body = objectMapper.readTree(response.body());
            boolean platformOk = "ok".equalsIgnoreCase(body.path("status").asText());
            boolean degraded = hasUnavailableService(body.path("services"));
            if (!platformOk) {
                return new ArchiveOsStatus("UNAVAILABLE", response.statusCode(),
                        "ArchiveOS platform health is not OK", checkedAt);
            }
            if (degraded) {
                return new ArchiveOsStatus("DEGRADED", response.statusCode(),
                        "ArchiveOS is reachable, but one or more optional services are unavailable", checkedAt);
            }
            return new ArchiveOsStatus("AVAILABLE", response.statusCode(), "ArchiveOS is available", checkedAt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ArchiveOsStatus("UNAVAILABLE", null, "ArchiveOS health check was interrupted", checkedAt);
        } catch (Exception exception) {
            return new ArchiveOsStatus("UNAVAILABLE", null,
                    "ArchiveOS is unreachable: " + exception.getClass().getSimpleName(), checkedAt);
        }
    }

    private boolean hasUnavailableService(JsonNode services) {
        if (!services.isObject()) {
            return true;
        }
        Iterator<Map.Entry<String, JsonNode>> properties = services.properties().iterator();
        while (properties.hasNext()) {
            if (!properties.next().getValue().asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("ArchiveOS base URL must not be blank");
        }
        return normalized;
    }

    public record ArchiveOsStatus(String status, Integer httpStatus, String message, Instant checkedAt) {
    }
}
