package com.sakhtyar.knowledge.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_candidate")
public class KnowledgeCandidateEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 60)
    private KnowledgeCandidateType candidateType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KnowledgeCandidateOrigin origin;

    @Column(name = "raw_input", nullable = false, columnDefinition = "text")
    private String rawInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> normalizedPayload;

    @Column(name = "proposed_term_code", length = 120)
    private String proposedTermCode;

    @Column(name = "proposed_name_fa", length = 300)
    private String proposedNameFa;

    @Column(name = "proposed_name_en", length = 300)
    private String proposedNameEn;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_url", length = 3000)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KnowledgeReviewStatus status;

    @Column(precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeCandidateEntity() {}

    public KnowledgeCandidateEntity(
            UUID id, KnowledgeCandidateType candidateType, KnowledgeCandidateOrigin origin,
            String rawInput, Map<String, Object> normalizedPayload,
            String proposedTermCode, String proposedNameFa, String proposedNameEn,
            UUID sourceId, String sourceUrl, BigDecimal confidence, String reason,
            String createdBy, Instant now
    ) {
        this.id = id;
        this.candidateType = candidateType;
        this.origin = origin;
        this.rawInput = rawInput;
        this.normalizedPayload = normalizedPayload == null ? Map.of() : Map.copyOf(normalizedPayload);
        this.proposedTermCode = proposedTermCode;
        this.proposedNameFa = proposedNameFa;
        this.proposedNameEn = proposedNameEn;
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
        this.status = KnowledgeReviewStatus.PENDING_REVIEW;
        this.confidence = confidence;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void review(KnowledgeReviewStatus status, String note, String reviewer, Instant now) {
        if (status != KnowledgeReviewStatus.APPROVED && status != KnowledgeReviewStatus.REJECTED) {
            throw new IllegalArgumentException("Candidate review status must be APPROVED or REJECTED.");
        }
        this.status = status;
        this.reviewNote = note;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public KnowledgeCandidateType getCandidateType() { return candidateType; }
    public KnowledgeCandidateOrigin getOrigin() { return origin; }
    public String getRawInput() { return rawInput; }
    public Map<String, Object> getNormalizedPayload() { return normalizedPayload == null ? Map.of() : Map.copyOf(normalizedPayload); }
    public String getProposedTermCode() { return proposedTermCode; }
    public String getProposedNameFa() { return proposedNameFa; }
    public String getProposedNameEn() { return proposedNameEn; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceUrl() { return sourceUrl; }
    public KnowledgeReviewStatus getStatus() { return status; }
    public BigDecimal getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public String getReviewNote() { return reviewNote; }
    public String getCreatedBy() { return createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}