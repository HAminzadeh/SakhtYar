package com.sakhtyar.crawler.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crawl_url")
public class CrawlUrlEntity {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false, length = 3000)
    private String url;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(nullable = false)
    private int depth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CrawlUrlStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    protected CrawlUrlEntity() {}

    public CrawlUrlEntity(UUID id, UUID jobId, String url, String urlHash, int depth, Instant now) {
        this.id = id;
        this.jobId = jobId;
        this.url = url;
        this.urlHash = urlHash;
        this.depth = depth;
        this.status = CrawlUrlStatus.DISCOVERED;
        this.discoveredAt = now;
    }

    public void fetched(int statusCode, Instant now) {
        status = CrawlUrlStatus.FETCHED;
        httpStatus = statusCode;
        fetchedAt = now;
    }

    public void failed(String error, Instant now) {
        status = CrawlUrlStatus.FAILED;
        errorMessage = error;
        fetchedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getUrl() { return url; }
    public String getUrlHash() { return urlHash; }
    public int getDepth() { return depth; }
    public CrawlUrlStatus getStatus() { return status; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getFetchedAt() { return fetchedAt; }
}