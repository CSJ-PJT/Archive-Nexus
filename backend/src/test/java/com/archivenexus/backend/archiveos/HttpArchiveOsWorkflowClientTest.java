package com.archivenexus.backend.archiveos;

import com.archivenexus.backend.task.NexusTaskEntity;
import com.archivenexus.backend.task.NexusTaskModels.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpArchiveOsWorkflowClientTest {
    @Test
    void sendsIntegrationTokenHeaderWhenCreatingWorkflow() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> source = new AtomicReference<>();
        AtomicReference<String> scope = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/tasks", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            source.set(exchange.getRequestHeaders().getFirst("X-Archive-Source-System"));
            scope.set(exchange.getRequestHeaders().getFirst("X-Archive-Service-Scope"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"data":{"id":"WF-1","status":"queued","latest_pm_decision_id":null}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpArchiveOsWorkflowClient client = new HttpArchiveOsWorkflowClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "shared-token", 1, 1000);
            NexusTaskEntity task = new NexusTaskEntity("TASK-1", "Check inventory", TaskType.MANUFACTURING_QUERY,
                    "factory-a", "Why is inventory low?", "operator", 3, Instant.now());

            ArchiveOsWorkflowClient.WorkflowRef ref = client.create(task);

            assertThat(ref.id()).isEqualTo("WF-1");
            assertThat(authorization.get()).isEqualTo("Bearer shared-token");
            assertThat(source.get()).isEqualTo("archive-nexus");
            assertThat(scope.get()).isEqualTo("runtime:ingest");
            assertThat(body.get()).contains("\"source\":\"archive-nexus\"", "\"source_task_id\":\"TASK-1\"");
        } finally {
            server.stop(0);
        }
    }
}
