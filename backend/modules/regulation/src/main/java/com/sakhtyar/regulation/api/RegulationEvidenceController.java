package com.sakhtyar.regulation.api;

import com.sakhtyar.regulation.application.RegulationEvidenceService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/regulations")
public class RegulationEvidenceController {
    private final RegulationEvidenceService service;
    public RegulationEvidenceController(RegulationEvidenceService service){this.service=service;}
    @GetMapping("/cases/{caseId}/verified-evidence")
    public Object evidence(@PathVariable UUID caseId){return service.evidence(caseId);}
    public record BindRequest(UUID knowledgeRuleId){}
    @PostMapping("/admin/rules/{id}/bind-evidence")
    public Object bind(@PathVariable UUID id,@RequestBody BindRequest request,Authentication auth){
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("USER_MANAGE")))
            throw new AccessDeniedException("USER_MANAGE required");
        if(request==null||request.knowledgeRuleId()==null)throw new IllegalArgumentException("knowledgeRuleId required");
        return service.bind(id,request.knowledgeRuleId(),auth.getName());
    }
}
