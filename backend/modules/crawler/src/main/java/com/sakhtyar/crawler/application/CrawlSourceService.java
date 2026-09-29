package com.sakhtyar.crawler.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.crawler.api.CrawlerDtos.*;
import com.sakhtyar.crawler.domain.*;
import com.sakhtyar.knowledge.domain.KnowledgeSourceRepository;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrawlSourceService {

    private final CrawlSourceRepository sourceRepository;
    private final CrawlJobRepository jobRepository;
    private final CrawledDocumentRepository documentRepository;
    private final KnowledgeSourceRepository knowledgeSourceRepository;
    private final AuditService auditService;

    public CrawlSourceService(
            CrawlSourceRepository sourceRepository,
            CrawlJobRepository jobRepository,
            CrawledDocumentRepository documentRepository,
            KnowledgeSourceRepository knowledgeSourceRepository,
            AuditService auditService
    ) {
        this.sourceRepository = sourceRepository;
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.knowledgeSourceRepository = knowledgeSourceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> listSources() {
        return sourceRepository.findAllByOrderByUpdatedAtDesc().stream().map(SourceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SourceResponse getSource(UUID id) {
        return SourceResponse.from(requireSource(id));
    }

    @Transactional
    public SourceResponse create(UpsertSourceRequest request, Authentication authentication) {
        validate(request);
        Instant now = Instant.now();
        String actor = actor(authentication);

        CrawlSourceEntity entity = new CrawlSourceEntity(
                UUID.randomUUID(), request.knowledgeSourceId(), request.name().trim(),
                request.seedUrl().trim(), normalizedHost(request), request.enabled(),
                request.maxDepth(), request.maxPages(), request.requestDelayMs(), request.timeoutMs(),
                request.respectRobots(), request.userAgent().trim(), request.metadata(), actor, now
        );
        sourceRepository.save(entity);
        auditService.record("CRAWL_SOURCE", entity.getId(), "CRAWL_SOURCE_CREATED",
                Map.of("name", entity.getName(), "seedUrl", entity.getSeedUrl()));
        return SourceResponse.from(entity);
    }

    @Transactional
    public SourceResponse update(UUID id, UpsertSourceRequest request) {
        validate(request);
        CrawlSourceEntity entity = requireSource(id);
        if (!entity.getKnowledgeSourceId().equals(request.knowledgeSourceId())) {
            throw new IllegalArgumentException("knowledgeSourceId is immutable after crawler source creation.");
        }
        entity.update(
                request.name().trim(), request.seedUrl().trim(), normalizedHost(request),
                request.enabled(), request.maxDepth(), request.maxPages(), request.requestDelayMs(),
                request.timeoutMs(), request.respectRobots(), request.userAgent().trim(),
                request.metadata(), Instant.now()
        );
        auditService.record("CRAWL_SOURCE", entity.getId(), "CRAWL_SOURCE_UPDATED",
                Map.of("name", entity.getName(), "enabled", entity.isEnabled()));
        return SourceResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> jobs(UUID sourceId) {
        requireSource(sourceId);
        return jobRepository.findBySourceIdOrderByCreatedAtDesc(sourceId).stream().map(JobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(UUID jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new IllegalArgumentException("Crawl job not found.");
        }
        return documentRepository.findByJobIdOrderByObservedAtDesc(jobId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    CrawlSourceEntity requireSource(UUID id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Crawler source not found."));
    }

    private void validate(UpsertSourceRequest request) {
        if (!knowledgeSourceRepository.existsById(request.knowledgeSourceId())) {
            throw new IllegalArgumentException("Knowledge source not found.");
        }
        URI uri = URI.create(request.seedUrl().trim());
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Crawler seed URL must use HTTP or HTTPS.");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Crawler seed URL must contain a host.");
        }
    }

    private static String normalizedHost(UpsertSourceRequest request) {
        if (request.allowedHost() != null && !request.allowedHost().isBlank()) {
            return request.allowedHost().trim().toLowerCase(Locale.ROOT);
        }
        return URI.create(request.seedUrl().trim()).getHost().toLowerCase(Locale.ROOT);
    }

    private static String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }
}