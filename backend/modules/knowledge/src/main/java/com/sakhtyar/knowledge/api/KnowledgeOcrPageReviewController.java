package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeOcrPageReviewService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin/ocr-review")
public class KnowledgeOcrPageReviewController {
    private final KnowledgeOcrPageReviewService review;
    public KnowledgeOcrPageReviewController(KnowledgeOcrPageReviewService review) {this.review=review;}

    private String reviewer(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream()
            .noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE authority required");
        return auth.getName();
    }
    public record DecisionRequest(String decision,String text) {}

    @GetMapping("/pages")
    public Map<String,Object> pages(@RequestParam(required=false) UUID executionId,
                                    @RequestParam(defaultValue="PENDING") String status,
                                    @RequestParam(defaultValue="1") int page,
                                    @RequestParam(defaultValue="15") int pageSize,
                                    Authentication auth) {
        reviewer(auth);
        return review.pages(executionId,status,page,pageSize);
    }
    @GetMapping("/pages/{executionId}/{documentId}/{pageNo}/image")
    public Map<String,String> image(@PathVariable UUID executionId,@PathVariable UUID documentId,
                                    @PathVariable int pageNo,Authentication auth) throws Exception {
        reviewer(auth);
        return review.pageImage(executionId,documentId,pageNo);
    }
    @PostMapping("/pages/{executionId}/{documentId}/{pageNo}/decision")
    public Map<String,Object> decide(@PathVariable UUID executionId,@PathVariable UUID documentId,
                                     @PathVariable int pageNo,@RequestBody DecisionRequest body,
                                     Authentication auth) {
        String by=reviewer(auth);
        if(body==null) throw new IllegalArgumentException("Review decision is required");
        return review.decide(executionId,documentId,pageNo,body.decision(),body.text(),by);
    }
}
