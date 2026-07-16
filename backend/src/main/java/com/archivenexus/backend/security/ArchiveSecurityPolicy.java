package com.archivenexus.backend.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

/** Canonical Archive service identity and scope contract for Nexus. */
@Component
public class ArchiveSecurityPolicy {
    public static final String SOURCE_HEADER = "X-Archive-Source-System";
    public static final String SCOPE_HEADER = "X-Archive-Service-Scope";
    public static final String PRODUCTION_INGEST = "production:ingest";
    public static final String LOGISTICS_INGEST = "logistics:ingest";
    public static final String LEDGER_INGEST = "ledger:ingest";
    public static final String RUNTIME_INGEST = "runtime:ingest";
    public static final String LEDGER_APPROVAL_CALLBACK = "ledger:approval-callback";
    public static final String AUTHENTICATED_READ = "authenticated:read";
    public static final String ADMIN_OPERATE = "admin:operate";
    public static final Map<String, String> LEGACY_SCOPE_ALIASES = Map.of("ledger:read", AUTHENTICATED_READ, "runtime:read", AUTHENTICATED_READ);

    private final boolean enabled; private final long maxPayloadBytes; private final int maxBatchItems; private final int maxWritesPerMinute;
    private final String marketToken; private final String operatorToken; private final String readerToken;
    public ArchiveSecurityPolicy(@Value("${archive.security.enabled:false}") boolean enabled,
                                 @Value("${archive.security.max-payload-bytes:1048576}") long maxPayloadBytes,
                                 @Value("${archive.security.max-batch-items:100}") int maxBatchItems,
                                 @Value("${archive.security.max-writes-per-minute:60}") int maxWritesPerMinute,
                                 @Value("${archive.security.market-token:}") String marketToken,
                                 @Value("${archive.security.operator-token:}") String operatorToken,
                                 @Value("${archive.security.reader-token:}") String readerToken) {
        this.enabled=enabled; this.maxPayloadBytes=Math.max(1024,maxPayloadBytes); this.maxBatchItems=Math.max(1,Math.min(maxBatchItems,500)); this.maxWritesPerMinute=Math.max(1,Math.min(maxWritesPerMinute,10000)); this.marketToken=marketToken; this.operatorToken=operatorToken; this.readerToken=readerToken;
    }
    @PostConstruct void validateRcConfiguration() { if(enabled && (blank(marketToken)||blank(operatorToken)||blank(readerToken))) throw new IllegalStateException("Nexus RC security is enabled but required token variables are missing"); }
    public boolean enabled(){return enabled;} public long maxPayloadBytes(){return maxPayloadBytes;} public int maxBatchItems(){return maxBatchItems;} public int maxWritesPerMinute(){return maxWritesPerMinute;}
    public void authorize(String source,String scope,String bearerToken,RequiredAccess required){ if(!enabled)return; String canonicalSource=canonicalSource(source); String canonicalScope=canonicalScope(scope); if(blank(bearerToken))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Missing service credential"); if(!required.source.equals(canonicalSource)||!required.scope.equals(canonicalScope))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Service identity or scope is not permitted"); if(!constantTimeEquals(expectedToken(required),bearerToken))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid service credential"); }
    public void assertBodySource(String headerSource,String bodySource){ if(enabled && (blank(bodySource)||!canonicalSource(headerSource).equals(canonicalSource(bodySource))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Header and payload source do not match"); }
    private String expectedToken(RequiredAccess access){ return switch(access){case MARKET_INGEST->marketToken;case ADMIN->operatorToken;case SENSITIVE_READ->readerToken;}; }
    private static String canonicalSource(String source){return source==null?"":source.trim().toLowerCase(Locale.ROOT);} private static String canonicalScope(String scope){if(scope==null)return "";String s=scope.trim();return LEGACY_SCOPE_ALIASES.getOrDefault(s,s);} private static boolean constantTimeEquals(String expected,String supplied){return !blank(expected)&&MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));} private static boolean blank(String value){return value==null||value.isBlank();}
    public enum RequiredAccess { MARKET_INGEST("archive-market",PRODUCTION_INGEST), ADMIN("archive-os",ADMIN_OPERATE), SENSITIVE_READ("archive-os",AUTHENTICATED_READ); private final String source; private final String scope; RequiredAccess(String source,String scope){this.source=source;this.scope=scope;} }
}
