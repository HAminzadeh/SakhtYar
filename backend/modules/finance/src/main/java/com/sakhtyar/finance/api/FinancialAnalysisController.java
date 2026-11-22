package com.sakhtyar.finance.api;

import static com.sakhtyar.finance.api.FinanceDtos.*;
import com.sakhtyar.finance.application.FinancialAnalysisService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class FinancialAnalysisController {
    private final FinancialAnalysisService service;
    public FinancialAnalysisController(FinancialAnalysisService service){this.service=service;}

    @PostMapping("/analyses")
    public FinancialAnalysisResponse create(@Valid @RequestBody CreateFinancialAnalysisRequest request,Authentication auth) {
        return service.create(request,auth);
    }

    @GetMapping("/analyses/{id}")
    public FinancialAnalysisResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/cases/{caseId}/analyses")
    public List<FinancialAnalysisResponse> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }

    @GetMapping("/scenarios/{scenarioId}/analyses")
    public List<FinancialAnalysisResponse> byScenario(@PathVariable UUID scenarioId) {
        return service.byScenario(scenarioId);
    }
}