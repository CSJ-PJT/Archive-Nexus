package com.archivenexus.backend.archiveos.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class ArchiveOsRuntimeProperties {
    public final boolean enabled; public final String baseUrl; public final String token; public final Duration timeout; public final int batchSize; public final int maxRetry; public final Duration retryDelay; public final Duration stalePublishing;
    public ArchiveOsRuntimeProperties(@Value("${archiveos.runtime-ingest.enabled:false}") boolean enabled,
                                      @Value("${archive-nexus.archiveos.base-url:http://host.docker.internal:4000}") String baseUrl,
                                      @Value("${archive.tokens.nexus-to-os:}") String token,
                                      @Value("${archiveos.runtime-ingest.timeout-ms:2000}") long timeoutMs,
                                      @Value("${archiveos.runtime-ingest.batch-size:10}") int batchSize,
                                      @Value("${archiveos.runtime-ingest.max-retry:5}") int maxRetry,
                                      @Value("${archiveos.runtime-ingest.retry-delay-ms:5000}") long retryDelayMs,
                                      @Value("${archiveos.runtime-ingest.stale-publishing-ms:60000}") long staleMs) {
        this.enabled=enabled; this.baseUrl=baseUrl.replaceAll("/+$",""); this.token=token==null?"":token.trim(); this.timeout=Duration.ofMillis(Math.max(250, timeoutMs)); this.batchSize=Math.max(1,Math.min(batchSize,100)); this.maxRetry=Math.max(1,maxRetry); this.retryDelay=Duration.ofMillis(Math.max(500,retryDelayMs)); this.stalePublishing=Duration.ofMillis(Math.max(5000,staleMs));
    }
}
