package com.sakhtyar.crawler.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.crawler.api.CrawlerDtos.JobResponse;
import com.sakhtyar.crawler.domain.*;
import com.sakhtyar.crawler.infrastructure.*;
import com.sakhtyar.knowledge.domain.*;
import com.sakhtyar.globalization.application.JurisdictionService;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class IngestionPipeline {

    private static final int MAX_CANDIDATE_TEXT = 20000;

    private final CrawlSourceService sourceService;
    private final CrawlJobRepository jobRepository;
    private final CrawlUrlRepository urlRepository;
    private final CrawledDocumentRepository documentRepository;
    private final KnowledgeCandidateRepository candidateRepository;
    private final PageFetcher pageFetcher;
    private final ContentExtractor contentExtractor;
    private final UrlDiscovery urlDiscovery;
    private final ChangeDetector changeDetector;
    private final AuditService auditService;
    private final JurisdictionService jurisdictionService;

    public IngestionPipeline(
            CrawlSourceService sourceService,
            CrawlJobRepository jobRepository,
            CrawlUrlRepository urlRepository,
            CrawledDocumentRepository documentRepository,
            KnowledgeCandidateRepository candidateRepository,
            PageFetcher pageFetcher,
            ContentExtractor contentExtractor,
            UrlDiscovery urlDiscovery,
            ChangeDetector changeDetector,
            AuditService auditService,
            JurisdictionService jurisdictionService
    ) {
        this.sourceService = sourceService;
        this.jobRepository = jobRepository;
        this.urlRepository = urlRepository;
        this.documentRepository = documentRepository;
        this.candidateRepository = candidateRepository;
        this.pageFetcher = pageFetcher;
        this.contentExtractor = contentExtractor;
        this.urlDiscovery = urlDiscovery;
        this.changeDetector = changeDetector;
        this.auditService = auditService;
        this.jurisdictionService = jurisdictionService;
    }

    public JobResponse run(UUID sourceId, Integer maxPagesOverride, Authentication authentication) {
        CrawlSourceEntity source = sourceService.requireSource(sourceId);
        if (!source.isEnabled()) {
            throw new IllegalStateException("Crawler source is disabled.");
        }

        int maxPages = maxPagesOverride == null
                ? source.getMaxPages()
                : Math.min(maxPagesOverride, source.getMaxPages());

        String actor = authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();

        Instant now = Instant.now();
        CrawlJobEntity job = new CrawlJobEntity(UUID.randomUUID(), sourceId, actor, now);
        jobRepository.save(job);

        recordJobEvent(job, "CRAWL_JOB_STARTED");

        Deque<QueueItem> queue = new ArrayDeque<>();
        queue.add(new QueueItem(source.getSeedUrl(), 0));

        try {
            while (!queue.isEmpty() && job.getPagesFetched() + job.getPagesFailed() < maxPages) {
                QueueItem item = queue.removeFirst();
                String canonical = UrlCanonicalizer.canonicalize(item.url());
                String urlHash = Hashing.sha256(canonical);

                if (urlRepository.existsByJobIdAndUrlHash(job.getId(), urlHash)) {
                    continue;
                }

                CrawlUrlEntity crawlUrl = new CrawlUrlEntity(
                        UUID.randomUUID(), job.getId(), canonical, urlHash, item.depth(), Instant.now()
                );
                urlRepository.save(crawlUrl);
                job.discovered();
                jobRepository.save(job);

                try {
                    if (source.getRequestDelayMs() > 0 && job.getPagesFetched() > 0) {
                        Thread.sleep(source.getRequestDelayMs());
                    }

                    FetchedPage fetched = pageFetcher.fetch(
                            canonical, source.getTimeoutMs(), source.getUserAgent()
                    );
                    crawlUrl.fetched(fetched.statusCode(), Instant.now());
                    urlRepository.save(crawlUrl);
                    job.fetched();

                    if (fetched.statusCode() < 200 || fetched.statusCode() >= 400) {
                        jobRepository.save(job);
                        continue;
                    }

                    ExtractedContent extracted = contentExtractor.extract(fetched);
                    String finalCanonical = UrlCanonicalizer.canonicalize(fetched.finalUri().toString());
                    String canonicalHash = Hashing.sha256(finalCanonical);
                    String contentHash = Hashing.sha256(fetched.body());

                    if (changeDetector.isNewVersion(sourceId, canonicalHash, contentHash)) {
                        CrawledDocumentEntity document = new CrawledDocumentEntity(
                                UUID.randomUUID(), sourceId, job.getId(), canonical, finalCanonical,
                                canonicalHash, fetched.contentType(), extracted.kind(),
                                IngestionDocumentState.EXTRACTED, fetched.statusCode(),
                                trimTo(extracted.title(), 1000), contentHash,
                                trimTo(extracted.text(), 2_000_000),
                                fetched.headers().get("etag"), fetched.headers().get("last-modified"),
                                Map.of("depth", item.depth()), Instant.now()
                        );
                        documentRepository.save(document);
                        job.changed();

                        if (extracted.text() != null && !extracted.text().isBlank()) {
                            KnowledgeCandidateEntity candidate = createCandidate(
                                    source, document, extracted, actor
                            );
                            candidateRepository.save(candidate);
                            jurisdictionService.stampCandidateFromCrawler(candidate.getId(), source.getId());
                            document.linkCandidate(candidate.getId());
                            documentRepository.save(document);
                            job.candidateCreated();
                        }
                    }

                    if (item.depth() < source.getMaxDepth() && extracted.kind() == DocumentKind.HTML) {
                        List<String> links = urlDiscovery.sameHostLinks(
                                extracted.discoveredLinks(), source.getAllowedHost()
                        );
                        for (String link : links) {
                            if (queue.size() >= maxPages * 5) {
                                break;
                            }
                            queue.addLast(new QueueItem(link, item.depth() + 1));
                        }
                    }

                    jobRepository.save(job);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Crawler execution interrupted.", e);
                } catch (Exception pageError) {
                    crawlUrl.failed(trimTo(pageError.getMessage(), 5000), Instant.now());
                    urlRepository.save(crawlUrl);
                    job.failedPage();
                    jobRepository.save(job);
                }
            }

            job.complete(Instant.now());
            jobRepository.save(job);
            recordJobEvent(job, "CRAWL_JOB_COMPLETED");
            return JobResponse.from(job);
        } catch (Exception fatal) {
            job.fail(trimTo(fatal.getMessage(), 10000), Instant.now());
            jobRepository.save(job);
            recordJobEvent(job, "CRAWL_JOB_FAILED");
            return JobResponse.from(job);
        }
    }

    private KnowledgeCandidateEntity createCandidate(
            CrawlSourceEntity source,
            CrawledDocumentEntity document,
            ExtractedContent extracted,
            String actor
    ) {
        String rawInput = trimTo(extracted.text(), MAX_CANDIDATE_TEXT);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("crawlerSourceId", source.getId().toString());
        payload.put("crawledDocumentId", document.getId().toString());
        payload.put("url", document.getCanonicalUrl());
        payload.put("contentHash", document.getContentHash());
        payload.put("documentKind", document.getDocumentKind().name());
        if (document.getTitle() != null) {
            payload.put("title", document.getTitle());
        }

        return new KnowledgeCandidateEntity(
                UUID.randomUUID(),
                KnowledgeCandidateType.DEFINITION,
                KnowledgeCandidateOrigin.CRAWLER,
                rawInput,
                payload,
                null,
                trimTo(document.getTitle(), 300),
                null,
                source.getKnowledgeSourceId(),
                document.getCanonicalUrl(),
                null,
                "Automatically extracted by crawler; human review required.",
                actor,
                Instant.now()
        );
    }

    private void recordJobEvent(CrawlJobEntity job, String action) {
        auditService.record(
                "CRAWL_JOB",
                job.getId(),
                action,
                Map.of(
                        "sourceId", job.getSourceId().toString(),
                        "status", job.getStatus().name(),
                        "pagesFetched", job.getPagesFetched(),
                        "pagesChanged", job.getPagesChanged(),
                        "candidatesCreated", job.getCandidatesCreated()
                )
        );
    }

    private static String trimTo(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record QueueItem(String url, int depth) {}
}