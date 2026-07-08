package com.archivenexus.backend.platform;

import com.archivenexus.backend.archiveos.ArchiveOsHealthService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformManifestServiceTest {
    @Test
    void exposesIndustryAppContractAndSisterSystems() {
        ArchiveOsHealthService health = mock(ArchiveOsHealthService.class);
        when(health.status()).thenReturn(new ArchiveOsHealthService.ArchiveOsStatus(
                "DEGRADED", 200, "ArchiveOS optional service unavailable", Instant.parse("2026-07-08T00:00:00Z")));
        PlatformManifestService service = new PlatformManifestService(health, Optional.empty(), "test",
                "https://github.com/CSJ-PJT/Archive-Nexus", "http://archiveos.local:4000",
                "https://github.com/CSJ-PJT/Backend-Atlas");

        var manifest = service.manifest();

        assertThat(manifest.product()).isEqualTo("archive-nexus");
        assertThat(manifest.contractVersion()).isEqualTo("industry-app-contract/v1");
        assertThat(manifest.capabilities()).extracting("id")
                .contains("multi-agent-orchestrator", "workflow-contract", "observability");
        assertThat(manifest.contractEndpoints()).extracting("path")
                .contains("/api/platform/manifest", "/api/ai/query", "/api/tasks/{id}/run");
        assertThat(manifest.dependencies()).extracting("name")
                .contains("ArchiveOS", "Backend Atlas");
        assertThat(manifest.archiveOsStatus().status()).isEqualTo("DEGRADED");
    }
}
