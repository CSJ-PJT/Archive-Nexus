package com.archivenexus.backend.web;

import com.archivenexus.backend.market.MarketEventModels.MarketBulkEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketBulkEventResponse;
import com.archivenexus.backend.market.MarketEventModels.MarketEventResponse;
import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.market.MarketEventService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/market")
public class MarketEventController {
    private final MarketEventService marketEventService;

    public MarketEventController(MarketEventService marketEventService) {
        this.marketEventService = marketEventService;
    }

    @PostMapping
    MarketEventResponse receive(@RequestBody MarketEventRequest request) {
        return marketEventService.receive(request);
    }

    @PostMapping("/bulk")
    MarketBulkEventResponse receiveBulk(@RequestBody MarketBulkEventRequest request) {
        return marketEventService.receiveBulk(request);
    }

    @GetMapping
    List<MarketEventResponse> list(@RequestParam(defaultValue = "100") int limit,
                                   @RequestParam(required = false) MarketEventStatus status) {
        return marketEventService.list(limit, status);
    }
}
