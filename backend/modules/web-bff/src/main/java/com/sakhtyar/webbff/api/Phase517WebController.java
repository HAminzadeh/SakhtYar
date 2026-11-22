package com.sakhtyar.webbff.api;

import com.sakhtyar.market.application.MarketIntelligenceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only integration API. Comparable listings require CASE_READ. */
@RestController
@RequestMapping("/api/web/v1")
public class Phase517WebController {
  private final MarketIntelligenceService market;
  public Phase517WebController(MarketIntelligenceService market) { this.market = market; }

  @GetMapping("/public/capabilities")
  public Map<String,Object> capabilities() {
    return Map.of("apiVersion", "1", "phase", "5.17", "service", "sakhtyar-web-bff",
       "features", List.of("market-summary", "market-trend", "market-comparables", "case-summary-authenticated"),
       "generatedAt", Instant.now().toString());
  }

  @GetMapping("/market/comparables")
  public Map<String,Object> comparables(
      @RequestParam String city,
      @RequestParam(required=false) String district,
      @RequestParam(required=false) String neighborhood,
      @RequestParam(required=false) String propertyType,
      @RequestParam(required=false) BigDecimal areaM2,
      @RequestParam(defaultValue="20") int limit) {
    if (city == null || city.isBlank() || city.length() > 120) {
      throw new IllegalArgumentException("city must be a non-empty string of at most 120 characters");
    }
    if (limit < 1 || limit > 50) {
      throw new IllegalArgumentException("limit must be between 1 and 50");
    }
    var samples = market.comparables(city, district, neighborhood, propertyType, areaM2, limit);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("generatedAt", Instant.now());
    out.put("sampleCount", samples.size());
    out.put("comparables", samples);
    out.put("disclaimer", "Observed listing data is not a verified transaction price.");
    return out;
  }
}