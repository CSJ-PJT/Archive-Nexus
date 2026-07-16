package com.archivenexus.backend.web;

import com.archivenexus.backend.archiveos.runtime.ArchiveOsRuntimeDeliveryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class RuntimeOutboundController {
    private final ArchiveOsRuntimeDeliveryService deliveries;
    public RuntimeOutboundController(ArchiveOsRuntimeDeliveryService deliveries){this.deliveries=deliveries;}
    @GetMapping("/api/runtime-outbound/summary") ArchiveOsRuntimeDeliveryService.Summary summary(){return deliveries.summary();}
    @GetMapping("/api/runtime-outbound/events") List<ArchiveOsRuntimeDeliveryService.View> events(@RequestParam(required=false) String status,@RequestParam(required=false) String correlationId,@RequestParam(defaultValue="100") int limit){return deliveries.events(status,correlationId,limit);}
    @GetMapping("/api/runtime-outbound/correlation/{correlationId}/preview") ArchiveOsRuntimeDeliveryService.Preview preview(@PathVariable String correlationId){return deliveries.preview(correlationId);}
}
