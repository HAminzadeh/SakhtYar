package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeAiImportService;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin/ai-import")
public class KnowledgeAiImportController {
    private final KnowledgeAiImportService service;
    public KnowledgeAiImportController(KnowledgeAiImportService service) { this.service=service; }
    private String admin(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() ||
            auth.getAuthorities().stream().noneMatch(a -> "USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
        return auth.getName();
    }
    @GetMapping("/instructions")
    public Map<String,Object> instructions(@RequestParam(defaultValue="ChatGPT") String provider, Authentication auth) {
        admin(auth); return service.instructions(provider);
    }
    @PostMapping(value="/packages",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> upload(@RequestParam("file") MultipartFile file,Authentication auth) {
        return service.upload(file,admin(auth));
    }
    @GetMapping("/packages")
    public List<Map<String,Object>> packages(Authentication auth) { admin(auth);return service.batches(); }
    @GetMapping("/packages/{id}/documents")
    public List<Map<String,Object>> documents(@PathVariable UUID id,Authentication auth) {
        admin(auth);return service.documents(id);
    }
    @GetMapping("/packages/{id}/items")
    public List<Map<String,Object>> items(@PathVariable UUID id,@RequestParam(defaultValue="RULE") String type,Authentication auth) {
        admin(auth);return service.items(id,type);
    }
    public record ReviewRequest(String decision,String notes) {}
    @PostMapping("/packages/{id}/items/{itemId}/review")
    public Map<String,Object> review(@PathVariable UUID id,@PathVariable UUID itemId,
                                     @RequestBody ReviewRequest request,Authentication auth) {
        return service.review(id,itemId,request.decision(),request.notes(),admin(auth));
    }
    @GetMapping("/packages/{id}/items/{itemId}/history")
    public List<Map<String,Object>> history(@PathVariable UUID id,@PathVariable UUID itemId,Authentication auth) {
        admin(auth);return service.reviewEvents(id,itemId);
    }

}
