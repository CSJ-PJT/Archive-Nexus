package com.archivenexus.backend.platform;

import com.archivenexus.backend.archiveos.ArchiveOsHealthService.ArchiveOsStatus;

import java.time.Instant;
import java.util.List;

public final class PlatformManifestModels {
    private PlatformManifestModels() {
    }

    public record PlatformManifest(
            String product,
            String displayName,
            String productLine,
            String role,
            String version,
            String contractVersion,
            String environment,
            String repository,
            String summary,
            List<Capability> capabilities,
            List<ContractEndpoint> contractEndpoints,
            List<Dependency> dependencies,
            List<String> ownedDomains,
            List<String> operationalGuarantees,
            ArchiveOsStatus archiveOsStatus,
            Instant generatedAt
    ) {
    }

    public record Capability(String id, String name, String description, String status) {
    }

    public record ContractEndpoint(String method, String path, String purpose, String owner) {
    }

    public record Dependency(String name, String role, String url, String status) {
    }
}
