package com.sakhtyar.material.api;

import static com.sakhtyar.material.api.MaterialDtos.*;

import com.sakhtyar.material.application.MaterialService;
import com.sakhtyar.material.domain.PriceAnomalyStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping
    public List<MaterialResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category
    ) {
        return service.listMaterials(query, category);
    }

    @GetMapping("/{id}")
    public MaterialResponse get(@PathVariable UUID id) {
        return service.getMaterial(id);
    }

    @PostMapping
    public MaterialResponse create(
            @Valid @RequestBody UpsertMaterialRequest request,
            Authentication authentication
    ) {
        return service.createMaterial(request, authentication);
    }

    @PutMapping("/{id}")
    public MaterialResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertMaterialRequest request
    ) {
        return service.updateMaterial(id, request);
    }

    @GetMapping("/{id}/variants")
    public List<VariantResponse> variants(@PathVariable UUID id) {
        return service.listVariants(id);
    }

    @PostMapping("/{id}/variants")
    public VariantResponse createVariant(
            @PathVariable UUID id,
            @Valid @RequestBody CreateVariantRequest request,
            Authentication authentication
    ) {
        return service.createVariant(id, request, authentication);
    }

    @GetMapping("/{id}/prices")
    public List<PriceObservationResponse> prices(@PathVariable UUID id) {
        return service.observations(id);
    }

    @PostMapping("/{id}/prices")
    public PriceObservationResponse createPrice(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePriceObservationRequest request,
            Authentication authentication
    ) {
        return service.createObservation(id, request, authentication);
    }

    @PostMapping("/prices/{id}/review")
    public PriceObservationResponse reviewPrice(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewPriceObservationRequest request,
            Authentication authentication
    ) {
        return service.reviewObservation(id, request, authentication);
    }

    @GetMapping("/{id}/aggregates")
    public List<PriceAggregateResponse> aggregates(@PathVariable UUID id) {
        return service.aggregates(id);
    }

    @PostMapping("/{id}/aggregates/rebuild")
    public List<PriceAggregateResponse> rebuildAggregates(@PathVariable UUID id) {
        return service.rebuildAggregates(id);
    }

    @GetMapping("/anomalies")
    public List<PriceAnomalyResponse> anomalies(
            @RequestParam(required = false) PriceAnomalyStatus status
    ) {
        return service.anomalies(status);
    }

    @PostMapping("/anomalies/{id}/review")
    public PriceAnomalyResponse reviewAnomaly(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewAnomalyRequest request,
            Authentication authentication
    ) {
        return service.reviewAnomaly(id, request, authentication);
    }
}