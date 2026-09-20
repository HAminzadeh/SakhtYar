package com.sakhtyar.casefile.api;

import com.sakhtyar.casefile.api.CaseDtos.CaseResponse;
import com.sakhtyar.casefile.api.CaseDtos.UpsertCaseRequest;
import com.sakhtyar.casefile.application.CaseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<CaseResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CaseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public CaseResponse create(
            @Valid @RequestBody UpsertCaseRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    public CaseResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertCaseRequest request
    ) {
        return service.update(id, request);
    }
}
