package com.sakhtyar.analysis.feasibility.api;

import static com.sakhtyar.analysis.feasibility.api.FeasibilityDtos.*;
import com.sakhtyar.analysis.feasibility.application.FeasibilityService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feasibility")
public class FeasibilityController {
    private final FeasibilityService service;
    public FeasibilityController(FeasibilityService service){this.service=service;}

    @PostMapping("/assessments")
    public AssessmentResponse create(@Valid @RequestBody CreateAssessmentRequest request,Authentication auth) {
        return service.create(request,auth);
    }

    @GetMapping("/assessments/{id}")
    public AssessmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/cases/{caseId}/assessments")
    public List<AssessmentResponse> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }

    @GetMapping("/scenarios/{scenarioId}/assessments")
    public List<AssessmentResponse> byScenario(@PathVariable UUID scenarioId) {
        return service.byScenario(scenarioId);
    }
}