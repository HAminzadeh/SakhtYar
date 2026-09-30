package com.sakhtyar.sensitivity.api;

import static com.sakhtyar.sensitivity.api.SensitivityDtos.*;
import com.sakhtyar.sensitivity.application.SensitivityService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sensitivity")
public class SensitivityController {
    private final SensitivityService service;
    public SensitivityController(SensitivityService service){this.service=service;}

    @PostMapping("/analyses")
    public SensitivityResponse create(@Valid @RequestBody CreateSensitivityRequest request,Authentication auth) {
        return service.create(request,auth);
    }

    @GetMapping("/analyses/{id}")
    public SensitivityResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/financial-analyses/{financialAnalysisId}")
    public List<SensitivityResponse> byFinancial(@PathVariable UUID financialAnalysisId) {
        return service.byFinancialAnalysis(financialAnalysisId);
    }

    @GetMapping("/cases/{caseId}")
    public List<SensitivityResponse> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }
}