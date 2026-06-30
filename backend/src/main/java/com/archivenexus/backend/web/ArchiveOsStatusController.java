package com.archivenexus.backend.web;

import com.archivenexus.backend.archiveos.ArchiveOsHealthService;
import com.archivenexus.backend.archiveos.ArchiveOsHealthService.ArchiveOsStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/archiveos")
public class ArchiveOsStatusController {
    private final ArchiveOsHealthService healthService;

    public ArchiveOsStatusController(ArchiveOsHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/status")
    ArchiveOsStatus status() {
        return healthService.status();
    }
}
