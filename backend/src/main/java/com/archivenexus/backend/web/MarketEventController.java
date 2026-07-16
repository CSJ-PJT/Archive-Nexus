package com.archivenexus.backend.web;

import com.archivenexus.backend.market.MarketEventModels.MarketBulkEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketEventRequest;
import com.archivenexus.backend.market.MarketEventModels.MarketBulkEventResponse;
import com.archivenexus.backend.market.MarketEventModels.MarketEventResponse;
import com.archivenexus.backend.market.MarketEventModels.MarketEventStatus;
import com.archivenexus.backend.market.MarketEventService;
import com.archivenexus.backend.security.ArchiveSecurityPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/market")
public class MarketEventController {
    private final MarketEventService marketEventService;
    private final ArchiveSecurityPolicy security;

    public MarketEventController(MarketEventService marketEventService, ArchiveSecurityPolicy security) {
        this.marketEventService = marketEventService;
        this.security = security;
    }

    @PostMapping
    MarketEventResponse receive(@RequestBody MarketEventRequest request, HttpServletRequest servletRequest) {
        security.assertBodySource(servletRequest.getHeader(ArchiveSecurityPolicy.SOURCE_HEADER), request.source());
        return marketEventService.receive(request);
    }

    @PostMapping("/bulk")
    MarketBulkEventResponse receiveBulk(@RequestBody MarketBulkEventRequest request, HttpServletRequest servletRequest) {
        if (request.events() == null || request.events().size() > security.maxBatchItems()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Market event batch exceeds RC limit");
        }
        for (MarketEventRequest event : request.events()) {
            security.assertBodySource(servletRequest.getHeader(ArchiveSecurityPolicy.SOURCE_HEADER), event.source());
        }
        return marketEventService.receiveBulk(request);
    }

    @GetMapping
    List<MarketEventResponse> list(@RequestParam(defaultValue = "100") int limit,
                                   @RequestParam(required = false) MarketEventStatus status) {
        return marketEventService.list(limit, status);
    }
}
