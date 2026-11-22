package com.sakhtyar.crawler.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crawl_job")
public class CrawlJobEntity {

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CrawlJobStatus status;

    @Column(name = "started_by", nullable = false, length = 150)
    private String startedBy;

    @Column(name = "pages_discovered", nullable = false)
    private int pagesDiscovered;

    @Column(name = "pages_fetched", nullable = false)
    private int pagesFetched;

    @Column(name = "pages_changed", nullable = false)
    private int pagesChanged;

    @Column(name = "pages_failed", nullable = false)
    private int pagesFailed;

    @Column(name = "candidates_created", nullable = false)
    private int candidatesCreated;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CrawlJobEntity() {}

    public CrawlJobEntity(UUID id, UUID sourceId, String startedBy, Instant now) {
        this.id = id;
        this.sourceId = sourceId;
        this.status = CrawlJobStatus.RUNNING;
        this.startedBy = startedBy;
        this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void discovered() { pagesDiscovered++; updatedAt = Instant.now(); }
    public void fetched() { pagesFetched++; updatedAt = Instant.now(); }
    public void changed() { pagesChanged++; updatedAt = Instant.now(); }
    public void failedPage() { pagesFailed++; updatedAt = Instant.now(); }
    public void candidateCreated() { candidatesCreated++; updatedAt = Instant.now(); }

    public void complete(Instant now) {
        status = CrawlJobStatus.COMPLETED;
        finishedAt = now;
        updatedAt = now;
    }

    public void fail(String error, Instant now) {
        status = CrawlJobStatus.FAILED;
        errorMessage = error;
        finishedAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public CrawlJobStatus getStatus() { return status; }
    public String getStartedBy() { return startedBy; }
    public int getPagesDiscovered() { return pagesDiscovered; }
    public int getPagesFetched() { return pagesFetched; }
    public int getPagesChanged() { return pagesChanged; }
    public int getPagesFailed() { return pagesFailed; }
    public int getCandidatesCreated() { return candidatesCreated; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}