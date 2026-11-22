package com.sakhtyar.casefile.api;

import com.sakhtyar.casefile.api.CaseDtos.CaseResponse;
import com.sakhtyar.casefile.api.CaseDtos.UpsertCaseRequest;
import com.sakhtyar.casefile.application.CaseService;
import com.sakhtyar.casefile.application.CaseRevision;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public CaseResponse get(@PathVariable UUID id, HttpServletResponse response) {
        CaseResponse item = service.get(id);
        response.setHeader(HttpHeaders.ETAG, CaseRevision.etag(item.updatedAt()));
        return item;
    }

    // Preserve the Java method signature for existing internal callers.
    public CaseResponse get(UUID id) {
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
            @Valid @RequestBody UpsertCaseRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            HttpServletResponse response
    ) {
        CaseResponse item = service.update(id, request, ifMatch);
        response.setHeader(HttpHeaders.ETAG, CaseRevision.etag(item.updatedAt()));
        return item;
    }

    // Keep the historical Java signature available to existing callers.
    public CaseResponse update(UUID id, UpsertCaseRequest request) {
        return service.update(id, request);
    }
}
