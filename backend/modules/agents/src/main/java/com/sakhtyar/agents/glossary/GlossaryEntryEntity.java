package com.sakhtyar.agents.glossary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "persian_glossary_entry")
public class GlossaryEntryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String term;

    @Column(name = "normalized_term", nullable = false, unique = true, length = 200)
    private String normalizedTerm;

    @Column(nullable = false, columnDefinition = "text")
    private String meaning;

    @Column(name = "aliases_text", columnDefinition = "text")
    private String aliasesText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GlossaryStatus status;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GlossaryEntryEntity() {
    }

    public GlossaryEntryEntity(
            UUID id,
            String term,
            String normalizedTerm,
            String meaning,
            String aliasesText,
            GlossaryStatus status,
            String createdBy,
            String approvedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.term = term;
        this.normalizedTerm = normalizedTerm;
        this.meaning = meaning;
        this.aliasesText = aliasesText;
        this.status = status;
        this.createdBy = createdBy;
        this.approvedBy = approvedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getTerm() { return term; }
    public String getNormalizedTerm() { return normalizedTerm; }
    public String getMeaning() { return meaning; }
    public String getAliasesText() { return aliasesText; }
    public GlossaryStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateDraft(String term, String meaning, String aliasesText) {
        if (status == GlossaryStatus.APPROVED) {
            throw new IllegalStateException("Approved glossary entry cannot be overwritten as draft.");
        }
        this.term = term;
        this.meaning = meaning;
        this.aliasesText = aliasesText;
        this.status = GlossaryStatus.DRAFT;
        this.approvedBy = null;
        this.updatedAt = Instant.now();
    }

    public void approve(String actor) {
        this.status = GlossaryStatus.APPROVED;
        this.approvedBy = actor;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        this.status = GlossaryStatus.REJECTED;
        this.approvedBy = null;
        this.updatedAt = Instant.now();
    }
}
