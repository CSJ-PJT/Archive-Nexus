package com.archivenexus.backend.web;

import com.archivenexus.backend.logisticssettlement.LogisticsSettlementModels.*;
import com.archivenexus.backend.logisticssettlement.LogisticsSettlementService;
import com.archivenexus.backend.security.ArchiveSecurityPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logistics/settlements")
public class LogisticsSettlementController {
    private final LogisticsSettlementService settlements;
    private final ArchiveSecurityPolicy security;
    public LogisticsSettlementController(LogisticsSettlementService settlements, ArchiveSecurityPolicy security){this.settlements=settlements;this.security=security;}

    @PostMapping("/daily")
    DailySettlementResponse receive(@RequestBody DailySettlementRequest request, HttpServletRequest servletRequest){security.assertBodySource(servletRequest.getHeader(ArchiveSecurityPolicy.SOURCE_HEADER), request.source());return settlements.receive(request);}

    @PostMapping("/daily/bulk")
    BulkDailySettlementResponse receiveBulk(@RequestBody BulkDailySettlementRequest request, HttpServletRequest servletRequest){if(request.settlements()==null||request.settlements().size()>security.maxBatchItems())throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Settlement batch exceeds RC limit");for(DailySettlementRequest settlement:request.settlements())security.assertBodySource(servletRequest.getHeader(ArchiveSecurityPolicy.SOURCE_HEADER),settlement.source());return settlements.receiveBulk(request);}

    @GetMapping("/daily")
    List<DailySettlementResponse> recent(@RequestParam(defaultValue = "100") int limit,@RequestParam(required = false) String factoryId){return settlements.recent(limit,factoryId);}

    @GetMapping("/daily/{settlementId}")
    ResponseEntity<DailySettlementResponse> find(@PathVariable String settlementId){return ResponseEntity.of(settlements.find(settlementId));}

    @GetMapping("/summary")
    SettlementSummary summary(){return settlements.summary();}
}
