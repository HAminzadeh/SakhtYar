package com.sakhtyar.crawler.api;

import com.sakhtyar.crawler.api.CrawlerDtos.*;
import com.sakhtyar.crawler.application.CrawlSourceService;
import com.sakhtyar.crawler.application.IngestionPipeline;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crawler")
public class CrawlerController {

    private final CrawlSourceService sourceService;
    private final IngestionPipeline pipeline;

    public CrawlerController(CrawlSourceService sourceService, IngestionPipeline pipeline) {
        this.sourceService = sourceService;
        this.pipeline = pipeline;
    }

    @GetMapping("/sources")
    public List<SourceResponse> listSources() {
        return sourceService.listSources();
    }

    @GetMapping("/sources/{id}")
    public SourceResponse getSource(@PathVariable UUID id) {
        return sourceService.getSource(id);
    }

    @PostMapping("/sources")
    public SourceResponse createSource(
            @Valid @RequestBody UpsertSourceRequest request,
            Authentication authentication
    ) {
        return sourceService.create(request, authentication);
    }

    @PutMapping("/sources/{id}")
    public SourceResponse updateSource(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertSourceRequest request
    ) {
        return sourceService.update(id, request);
    }

    @PostMapping("/sources/{id}/run")
    public JobResponse run(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RunSourceRequest request,
            Authentication authentication
    ) {
        return pipeline.run(
                id,
                request == null ? null : request.maxPagesOverride(),
                authentication
        );
    }

    @GetMapping("/sources/{id}/jobs")
    public List<JobResponse> jobs(@PathVariable UUID id) {
        return sourceService.jobs(id);
    }

    @GetMapping("/jobs/{id}/documents")
    public List<DocumentResponse> documents(@PathVariable UUID id) {
        return sourceService.documents(id);
    }
}