package com.sakhtyar.owner.api;

import com.sakhtyar.owner.api.OwnerDtos.OwnerResponse;
import com.sakhtyar.owner.api.OwnerDtos.UpsertOwnerRequest;
import com.sakhtyar.owner.application.OwnerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/owners")
public class OwnerController {

    private final OwnerService service;

    public OwnerController(OwnerService service) {
        this.service = service;
    }

    @GetMapping
    public List<OwnerResponse> list(@PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerResponse create(
            @PathVariable UUID caseId,
            @Valid @RequestBody UpsertOwnerRequest request
    ) {
        return service.create(caseId, request);
    }

    @PutMapping("/{ownerId}")
    public OwnerResponse update(
            @PathVariable UUID caseId,
            @PathVariable UUID ownerId,
            @Valid @RequestBody UpsertOwnerRequest request
    ) {
        return service.update(caseId, ownerId, request);
    }

    @DeleteMapping("/{ownerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID caseId,
            @PathVariable UUID ownerId
    ) {
        service.delete(caseId, ownerId);
    }
}
