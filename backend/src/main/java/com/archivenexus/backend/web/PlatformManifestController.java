package com.archivenexus.backend.web;

import com.archivenexus.backend.platform.PlatformManifestModels.PlatformManifest;
import com.archivenexus.backend.platform.PlatformManifestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformManifestController {
    private final PlatformManifestService manifestService;

    public PlatformManifestController(PlatformManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @GetMapping("/manifest")
    PlatformManifest manifest() {
        return manifestService.manifest();
    }
}
