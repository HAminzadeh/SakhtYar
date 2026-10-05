package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeSelectedExecutionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin/executions")
public class KnowledgeExecutionController {
    private final KnowledgeSelectedExecutionService service;
    public KnowledgeExecutionController(KnowledgeSelectedExecutionService service){this.service=service;}

    @PostMapping
    public Map<String,Object> create(@RequestBody Request r, Authentication a){
        return service.createSelectionAndQueue(r.selectedDocumentIds(),a==null?"system":a.getName());
    }
    @GetMapping("/{id}")
    public Map<String,Object> status(@PathVariable UUID id){return service.status(id);}
    public record Request(List<String> selectedDocumentIds){}
}