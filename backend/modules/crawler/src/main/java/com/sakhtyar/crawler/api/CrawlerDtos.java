package com.sakhtyar.crawler.api;

import com.sakhtyar.crawler.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class CrawlerDtos {

    private CrawlerDtos() {}

    public record UpsertSourceRequest(
            @NotNull UUID knowledgeSourceId,
            @NotBlank @Size(max = 300) String name,
            @NotBlank @Size(max = 3000) String seedUrl,
            @Size(max = 500) String allowedHost,
            boolean enabled,
            @Min(0) @Max(10) int maxDepth,
            @Min(1) @Max(1000) int maxPages,
            @Min(0) int requestDelayMs,
            @Min(1000) int timeoutMs,
            boolean respectRobots,
            @NotBlank @Size(max = 500) String userAgent,
            Map<String, Object> metadata
    ) {}

    public record RunSourceRequest(
            @Min(1) @Max(1000) Integer maxPagesOverride
    ) {}

    public record SourceResponse(
            UUID id, UUID knowledgeSourceId, String name, String seedUrl, String allowedHost,
            boolean enabled, int maxDepth, int maxPages, int requestDelayMs, int timeoutMs,
            boolean respectRobots, String userAgent, Map<String, Object> metadata,
            String createdBy, Instant createdAt, Instant updatedAt
    ) {
        public static SourceResponse from(CrawlSourceEntity e) {
            return new SourceResponse(
                    e.getId(), e.getKnowledgeSourceId(), e.getName(), e.getSeedUrl(), e.getAllowedHost(),
                    e.isEnabled(), e.getMaxDepth(), e.getMaxPages(), e.getRequestDelayMs(), e.getTimeoutMs(),
                    e.isRespectRobots(), e.getUserAgent(), e.getMetadata(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record JobResponse(
            UUID id, UUID sourceId, CrawlJobStatus status, String startedBy,
            int pagesDiscovered, int pagesFetched, int pagesChanged, int pagesFailed,
            int candidatesCreated, String errorMessage, Instant startedAt, Instant finishedAt,
            Instant createdAt, Instant updatedAt
    ) {
        public static JobResponse from(CrawlJobEntity e) {
            return new JobResponse(
                    e.getId(), e.getSourceId(), e.getStatus(), e.getStartedBy(),
                    e.getPagesDiscovered(), e.getPagesFetched(), e.getPagesChanged(),
                    e.getPagesFailed(), e.getCandidatesCreated(), e.getErrorMessage(),
                    e.getStartedAt(), e.getFinishedAt(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record DocumentResponse(
            UUID id, UUID sourceId, UUID jobId, String sourceUrl, String canonicalUrl,
            String contentType, DocumentKind documentKind, IngestionDocumentState state,
            Integer httpStatus, String title, String contentHash, UUID knowledgeCandidateId,
            Instant observedAt
    ) {
        public static DocumentResponse from(CrawledDocumentEntity e) {
            return new DocumentResponse(
                    e.getId(), e.getSourceId(), e.getJobId(), e.getSourceUrl(), e.getCanonicalUrl(),
                    e.getContentType(), e.getDocumentKind(), e.getState(), e.getHttpStatus(),
                    e.getTitle(), e.getContentHash(), e.getKnowledgeCandidateId(), e.getObservedAt()
            );
        }
    }
}