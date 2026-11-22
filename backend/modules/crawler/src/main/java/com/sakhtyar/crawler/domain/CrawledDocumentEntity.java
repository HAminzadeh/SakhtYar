package com.sakhtyar.crawler.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "crawled_document")
public class CrawledDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "source_url", nullable = false, length = 3000)
    private String sourceUrl;

    @Column(name = "canonical_url", nullable = false, length = 3000)
    private String canonicalUrl;

    @Column(name = "canonical_url_hash", nullable = false, length = 64)
    private String canonicalUrlHash;

    @Column(name = "content_type", length = 300)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_kind", nullable = false, length = 40)
    private DocumentKind documentKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IngestionDocumentState state;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(length = 1000)
    private String title;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @Column(length = 1000)
    private String etag;

    @Column(name = "last_modified", length = 1000)
    private String lastModified;

    @Column(name = "knowledge_candidate_id")
    private UUID knowledgeCandidateId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CrawledDocumentEntity() {}

    public CrawledDocumentEntity(
            UUID id, UUID sourceId, UUID jobId, String sourceUrl, String canonicalUrl,
            String canonicalUrlHash, String contentType, DocumentKind documentKind,
            IngestionDocumentState state, Integer httpStatus, String title, String contentHash,
            String extractedText, String etag, String lastModified, Map<String, Object> metadata,
            Instant now
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.jobId = jobId;
        this.sourceUrl = sourceUrl;
        this.canonicalUrl = canonicalUrl;
        this.canonicalUrlHash = canonicalUrlHash;
        this.contentType = contentType;
        this.documentKind = documentKind;
        this.state = state;
        this.httpStatus = httpStatus;
        this.title = title;
        this.contentHash = contentHash;
        this.extractedText = extractedText;
        this.etag = etag;
        this.lastModified = lastModified;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.observedAt = now;
        this.createdAt = now;
    }

    public void linkCandidate(UUID candidateId) {
        this.knowledgeCandidateId = candidateId;
        this.state = IngestionDocumentState.PENDING_REVIEW;
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public UUID getJobId() { return jobId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getCanonicalUrlHash() { return canonicalUrlHash; }
    public String getContentType() { return contentType; }
    public DocumentKind getDocumentKind() { return documentKind; }
    public IngestionDocumentState getState() { return state; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getTitle() { return title; }
    public String getContentHash() { return contentHash; }
    public String getExtractedText() { return extractedText; }
    public String getEtag() { return etag; }
    public String getLastModified() { return lastModified; }
    public UUID getKnowledgeCandidateId() { return knowledgeCandidateId; }
    public Instant getObservedAt() { return observedAt; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public Instant getCreatedAt() { return createdAt; }
}