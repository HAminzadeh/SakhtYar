package com.sakhtyar.regulation.api;

import static com.sakhtyar.regulation.api.RegulationDtos.*;
import com.sakhtyar.regulation.application.RegulationService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/regulations")
public class RegulationController {
    private final RegulationService service;
    public RegulationController(RegulationService service){this.service=service;}

    @GetMapping("/rules")
    public List<RuleResponse> rules(){return service.rules();}

    @PostMapping("/rules")
    public RuleResponse createRule(@Valid @RequestBody UpsertRuleRequest request,Authentication auth){
        return service.createRule(request,auth);
    }

    @PutMapping("/rules/{id}")
    public RuleResponse updateRule(@PathVariable UUID id,@Valid @RequestBody UpsertRuleRequest request){
        return service.updateRule(id,request);
    }

    @PostMapping("/evaluate")
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluateRequest request,Authentication auth){
        return service.evaluate(request,auth);
    }

    @GetMapping("/properties/{propertyId}/evaluations")
    public List<EvaluationResponse> evaluations(@PathVariable UUID propertyId){
        return service.evaluations(propertyId);
    }

    @GetMapping("/evaluations/{id}")
    public EvaluationResponse evaluation(@PathVariable UUID id){
        return service.evaluation(id);
    }
}