package com.archivenexus.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ArchiveServiceAuthenticationFilter extends OncePerRequestFilter {
    private final ArchiveSecurityPolicy policy;
    private final ConcurrentHashMap<String, RateWindow> writeWindows = new ConcurrentHashMap<>();

    public ArchiveServiceAuthenticationFilter(ArchiveSecurityPolicy policy) { this.policy = policy; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!policy.enabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > policy.maxPayloadBytes()) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request payload exceeds RC limit");
            return;
        }
        try {
            ArchiveSecurityPolicy.RequiredAccess required = requiredAccess(request);
            if (required != null) {
                String authorization = request.getHeader("Authorization");
                String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
                String source = request.getHeader(ArchiveSecurityPolicy.SOURCE_HEADER);
                String scope = request.getHeader(ArchiveSecurityPolicy.SCOPE_HEADER);
                policy.authorize(source, scope, token, required);
                if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
                    enforceWriteRate(source);
                }
                request.setAttribute("archive.auth.source", source);
            }
            chain.doFilter(request, response);
        } catch (ResponseStatusException exception) {
            response.sendError(exception.getStatusCode().value(), exception.getReason());
        }
    }

    private ArchiveSecurityPolicy.RequiredAccess requiredAccess(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/actuator/health".equals(path) || "/actuator/health/readiness".equals(path) || "/actuator/health/liveness".equals(path)) return null;
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())) {
            return isSensitiveRead(path) ? ArchiveSecurityPolicy.RequiredAccess.SENSITIVE_READ : null;
        }
        if (path.startsWith("/api/events/market")) return ArchiveSecurityPolicy.RequiredAccess.MARKET_INGEST;
        if (path.startsWith("/api/")) return ArchiveSecurityPolicy.RequiredAccess.ADMIN;
        return ArchiveSecurityPolicy.RequiredAccess.ADMIN;
    }

    private boolean isSensitiveRead(String path) {
        return path.startsWith("/api/outbox/events")
                || path.startsWith("/api/runtime-events/")
                || path.startsWith("/api/runtime-outbound")
                || path.startsWith("/api/audit")
                || path.startsWith("/api/logistics/settlements/daily")
                || path.startsWith("/api/tasks/")
                || path.startsWith("/actuator/prometheus");
    }

    private void enforceWriteRate(String source) {
        long minute = Instant.now().getEpochSecond() / 60;
        RateWindow window = writeWindows.compute(source, (ignored, current) -> {
            if (current == null || current.minute != minute) return new RateWindow(minute, new AtomicInteger(1));
            current.count.incrementAndGet();
            return current;
        });
        if (window.count.get() > policy.maxWritesPerMinute()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Write rate limit exceeded");
        }
    }

    private record RateWindow(long minute, AtomicInteger count) { }
}
