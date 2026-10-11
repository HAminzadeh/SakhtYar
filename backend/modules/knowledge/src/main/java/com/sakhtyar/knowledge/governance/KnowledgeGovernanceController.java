package com.sakhtyar.knowledge.governance;

import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin/governance")
public class KnowledgeGovernanceController {
    private final KnowledgeGovernanceService service;
    public KnowledgeGovernanceController(KnowledgeGovernanceService service) {this.service=service;}

    private static String reviewer(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
        return auth.getName();
    }
    @GetMapping("/pending")
    public Object pending(@RequestParam(defaultValue="100") int limit,Authentication auth){reviewer(auth);return service.pending(limit);}
    @GetMapping("/releases")
    public Object releases(Authentication auth){reviewer(auth);return service.releases();}
    @PostMapping("/rules/{id}/review")
    public Object review(@PathVariable UUID id,@RequestBody KnowledgeGovernanceService.ReviewRequest req,Authentication auth){return service.review(id,req,reviewer(auth));}
    @PostMapping("/releases/{id}/publish")
    public Object publish(@PathVariable UUID id,Authentication auth){return service.publish(id,reviewer(auth));}
}
