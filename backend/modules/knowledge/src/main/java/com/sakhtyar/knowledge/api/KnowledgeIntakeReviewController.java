package com.sakhtyar.knowledge.api;
import com.sakhtyar.knowledge.application.KnowledgeIntakeReviewService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge/admin/intake")
public class KnowledgeIntakeReviewController {
    private final KnowledgeIntakeReviewService service;
    public KnowledgeIntakeReviewController(KnowledgeIntakeReviewService service){this.service=service;}
    @GetMapping("/review") public Map<String,Object> review(){return service.review();}
}