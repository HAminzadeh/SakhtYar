package com.sakhtyar.geo.api;

import com.sakhtyar.geo.application.GeoService;
import com.sakhtyar.geo.domain.GeoSearchResult;
import com.sakhtyar.geo.domain.NearbyProperty;
import com.sakhtyar.geo.domain.ReverseGeocodeResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/geo")
public class GeoController {
    private final GeoService service;
    public GeoController(GeoService service) { this.service = service; }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("providerAvailable", service.providerAvailable(),
                "spatialDatabase", "PostGIS", "coordinateReferenceSystem", "EPSG:4326");
    }

    @GetMapping("/geocode")
    public List<GeoSearchResult> geocode(@RequestParam @NotBlank @Size(max = 300) String address) {
        return service.geocode(address);
    }

    /** Retained for existing clients; optional lat/lng never influence address geocoding. */
    @Deprecated
    @GetMapping("/search")
    public List<GeoSearchResult> legacySearch(
            @RequestParam @NotBlank @Size(max = 300) String term,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng) {
        return service.geocode(term);
    }

    @GetMapping("/reverse")
    public ReverseGeocodeResult reverse(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng) {
        return service.reverseGeocode(lat, lng);
    }

    @GetMapping("/nearby-properties")
    public List<NearbyProperty> nearbyProperties(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(20000) int radiusMeters) {
        return service.nearbyProperties(lat, lng, radiusMeters);
    }
}
