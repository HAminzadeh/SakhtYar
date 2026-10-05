package com.sakhtyar.knowledge.preparation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge/preparation")
public class KnowledgePreparationController {
    private final KnowledgePreparationService service;

    public KnowledgePreparationController(KnowledgePreparationService service) {
        this.service = service;
    }

    public record PrepareRequest(String inputRoot, String outputRoot) {}

    @PostMapping("/runs")
    public ResponseEntity<KnowledgePreparationResult> prepare(@RequestBody PrepareRequest request) {
        return ResponseEntity.accepted().body(service.prepare(
            Path.of(request.inputRoot()), Path.of(request.outputRoot())));
    }

    @GetMapping("/runs/{runId}")
    public Map<String,Object> status(@PathVariable UUID runId) {
        return service.status(runId);
    }
}
