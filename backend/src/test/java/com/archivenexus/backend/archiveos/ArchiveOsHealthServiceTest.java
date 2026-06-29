package com.archivenexus.backend.archiveos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveOsHealthServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsAvailableWhenArchiveOsAndAllServicesAreOk() throws IOException {
        ArchiveOsHealthService service = serviceWith("""
                {"status":"ok","services":{"ax":true,"runtime":true,"knowledge":true,"mesh":true}}
                """);

        ArchiveOsHealthService.ArchiveOsStatus status = service.status();

        assertThat(status.status()).isEqualTo("AVAILABLE");
        assertThat(status.httpStatus()).isEqualTo(200);
    }

    @Test
    void reportsDegradedWhenArchiveOsIsReachableButSomeServicesAreDown() throws IOException {
        ArchiveOsHealthService service = serviceWith("""
                {"status":"ok","services":{"ax":true,"runtime":true,"knowledge":false,"mesh":true}}
                """);

        ArchiveOsHealthService.ArchiveOsStatus status = service.status();

        assertThat(status.status()).isEqualTo("DEGRADED");
        assertThat(status.httpStatus()).isEqualTo(200);
    }

    @Test
    void reportsUnavailableWhenArchiveOsCannotBeReached() {
        ArchiveOsHealthService service = new ArchiveOsHealthService(
                "http://127.0.0.1:1", 100, new ObjectMapper());

        ArchiveOsHealthService.ArchiveOsStatus status = service.status();

        assertThat(status.status()).isEqualTo("UNAVAILABLE");
        assertThat(status.httpStatus()).isNull();
    }

    @Test
    void refreshesStatusAfterArchiveOsRecovers() throws IOException {
        AtomicReference<String> body = new AtomicReference<>("""
                {"status":"ok","services":{"ax":true,"runtime":false}}
                """);
        ArchiveOsHealthService service = serviceWith(body);

        assertThat(service.status().status()).isEqualTo("DEGRADED");

        body.set("""
                {"status":"ok","services":{"ax":true,"runtime":true}}
                """);

        assertThat(service.status().status()).isEqualTo("AVAILABLE");
    }

    private ArchiveOsHealthService serviceWith(String body) throws IOException {
        return serviceWith(new AtomicReference<>(body));
    }

    private ArchiveOsHealthService serviceWith(AtomicReference<String> body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health", exchange -> {
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return new ArchiveOsHealthService("http://127.0.0.1:" + server.getAddress().getPort(),
                1000, new ObjectMapper());
    }
}
