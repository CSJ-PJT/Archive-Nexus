package com.archivenexus.backend.web;

import com.archivenexus.backend.logisticssettlement.LogisticsSettlementModels.*;
import com.archivenexus.backend.logisticssettlement.LogisticsSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logistics/settlements")
public class LogisticsSettlementController {
    private final LogisticsSettlementService settlements;
    public LogisticsSettlementController(LogisticsSettlementService settlements){this.settlements=settlements;}

    @PostMapping("/daily")
    DailySettlementResponse receive(@RequestBody DailySettlementRequest request){return settlements.receive(request);}

    @PostMapping("/daily/bulk")
    BulkDailySettlementResponse receiveBulk(@RequestBody BulkDailySettlementRequest request){return settlements.receiveBulk(request);}

    @GetMapping("/daily")
    List<DailySettlementResponse> recent(@RequestParam(defaultValue = "100") int limit,@RequestParam(required = false) String factoryId){return settlements.recent(limit,factoryId);}

    @GetMapping("/daily/{settlementId}")
    ResponseEntity<DailySettlementResponse> find(@PathVariable String settlementId){return ResponseEntity.of(settlements.find(settlementId));}

    @GetMapping("/summary")
    SettlementSummary summary(){return settlements.summary();}
}
