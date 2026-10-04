package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeAdminService;
import com.sakhtyar.knowledge.application.KnowledgeDatasetPackageService;

import java.util.List;
import java.util.UUID;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/knowledge/admin")
public class KnowledgeAdminController {
    private final KnowledgeAdminService service;
    private final KnowledgeDatasetPackageService packageService;

    public KnowledgeAdminController(
            KnowledgeAdminService service,
            KnowledgeDatasetPackageService packageService
    ) {
        this.service = service;
        this.packageService = packageService;
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

    @GetMapping("/export-kit")
    public Map<String, Object> exportKit() {
        return service.exportKit();
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

    @PostMapping(value = "/dataset-packages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> uploadDatasetPackage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return packageService.upload(file, authentication == null ? "system" : authentication.getName());
    }

    @GetMapping("/dataset-packages")
    public List<Map<String,Object>> datasetPackages() {
        return packageService.list();
    }

    @PostMapping("/dataset-packages/{packageId}/validate")
    public Map<String,Object> validateDatasetPackage(
            @PathVariable UUID packageId,
            Authentication authentication
    ) {
        return packageService.validate(packageId, authentication == null ? "system" : authentication.getName());
    }

    @PostMapping("/dataset-packages/{packageId}/dry-run")
    public Map<String,Object> dryRunDatasetPackage(
            @PathVariable UUID packageId,
            Authentication authentication
    ) {
        return packageService.dryRun(packageId, authentication == null ? "system" : authentication.getName());
    }

    @PostMapping("/dataset-packages/{packageId}/import")
    public Map<String,Object> importDatasetPackage(
            @PathVariable UUID packageId,
            Authentication authentication
    ) {
        return packageService.importAsync(packageId, authentication == null ? "system" : authentication.getName());
    }

    @GetMapping("/dataset-packages/{packageId}/events")
    public List<Map<String,Object>> datasetPackageEvents(@PathVariable UUID packageId) {
        return packageService.events(packageId);
    }

    @GetMapping("/dataset-packages/{packageId}/content")
    public ResponseEntity<?> downloadDatasetPackage(@PathVariable UUID packageId) {
        var p = packageService.download(packageId);
        var disposition = ContentDisposition.attachment()
                .filename(p.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.contentType()))
                .contentLength(p.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(p.resource());
    }
}
