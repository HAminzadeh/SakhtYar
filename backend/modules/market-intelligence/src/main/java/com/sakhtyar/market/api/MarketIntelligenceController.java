package com.sakhtyar.market.api;

import static com.sakhtyar.market.api.MarketDtos.*;
import com.sakhtyar.market.application.MarketIntelligenceService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/market")
public class MarketIntelligenceController {
    private final MarketIntelligenceService service;
    public MarketIntelligenceController(MarketIntelligenceService service){this.service=service;}

    @PostMapping("/observations")
    public ObservationResponse ingest(@Valid @RequestBody ObservationRequest request,Authentication auth){
        return service.ingest(request,auth);
    }

    @GetMapping("/summary")
    public MarketSummary summary(
            @RequestParam String city,@RequestParam(required=false) String district,
            @RequestParam(required=false) String neighborhood,@RequestParam(required=false) String propertyType,
            @RequestParam(defaultValue="30") int days
    ){return service.summary(city,district,neighborhood,propertyType,days);}

    @GetMapping("/trend")
    public List<TrendPoint> trend(
            @RequestParam String city,@RequestParam(required=false) String district,
            @RequestParam(required=false) String neighborhood,@RequestParam(required=false) String propertyType,
            @RequestParam(defaultValue="30") int days
    ){return service.trend(city,district,neighborhood,propertyType,days);}

    @GetMapping("/comparables")
    public List<ComparableResponse> comparables(
            @RequestParam String city,@RequestParam(required=false) String district,
            @RequestParam(required=false) String neighborhood,@RequestParam(required=false) String propertyType,
            @RequestParam(required=false) BigDecimal areaM2,@RequestParam(defaultValue="20") int limit
    ){return service.comparables(city,district,neighborhood,propertyType,areaM2,limit);}

    @PostMapping("/snapshots")
    public SnapshotResponse publishSnapshot(
            @RequestParam String city,@RequestParam(required=false) String district,
            @RequestParam(required=false) String neighborhood,@RequestParam(required=false) String propertyType,
            @RequestParam(defaultValue="30") int days,Authentication auth
    ){return service.publishSnapshot(city,district,neighborhood,propertyType,days,auth);}

    @GetMapping("/snapshots/latest")
    public SnapshotResponse latestSnapshot(
            @RequestParam String city,@RequestParam(required=false) String district,
            @RequestParam(required=false) String neighborhood,@RequestParam(required=false) String propertyType,
            @RequestParam(defaultValue="30") int days
    ){return service.latestSnapshot(city,district,neighborhood,propertyType,days).orElse(null);}
}
