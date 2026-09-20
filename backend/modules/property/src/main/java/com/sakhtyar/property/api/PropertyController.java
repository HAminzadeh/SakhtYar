package com.sakhtyar.property.api;

import com.sakhtyar.property.api.PropertyDtos.PropertyResponse;
import com.sakhtyar.property.api.PropertyDtos.UpsertPropertyRequest;
import com.sakhtyar.property.application.PropertyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/property")
public class PropertyController {

    private final PropertyService service;

    public PropertyController(PropertyService service) {
        this.service = service;
    }

    @GetMapping
    public PropertyResponse get(@PathVariable UUID caseId) {
        return service.get(caseId);
    }

    @PutMapping
    public PropertyResponse upsert(
            @PathVariable UUID caseId,
            @Valid @RequestBody UpsertPropertyRequest request
    ) {
        return service.upsert(caseId, request);
    }
}
