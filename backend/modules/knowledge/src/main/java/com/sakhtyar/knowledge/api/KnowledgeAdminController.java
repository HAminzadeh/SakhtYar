package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeAdminService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin")
public class KnowledgeAdminController {
    private final KnowledgeAdminService service;

    public KnowledgeAdminController(KnowledgeAdminService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    @GetMapping("/profile")
    public Map<String, Object> profile() {
        return service.profile();
    }

    @PutMapping("/profile")
    public Map<String, Object> update(@RequestBody ProfileRequest r) {
        return service.updateProfile(r.repository(), r.branch(), r.sourcePath(), r.runbookVersion(), r.runbookPath(), r.defaultMode());
    }

    @PostMapping("/commands/generate")
    public Map<String, Object> generate(@RequestBody(required = false) GenerateCommandRequest r, Authentication a) {
        return service.generateCommand(r == null ? null : r.mode(), r == null ? null : r.baselineCommit(), a);
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs(@RequestParam(defaultValue = "20") int limit) {
        return service.recentRuns(limit);
    }

    @GetMapping("/runs/{runCode}/command")
    public Map<String, Object> command(@PathVariable String runCode) {
        return service.command(runCode);
    }

    public record GenerateCommandRequest(String mode, String baselineCommit) {
    }

    public record ProfileRequest(String repository, String branch, String sourcePath, String runbookVersion,
                                 String runbookPath, String defaultMode) {
    }
}
