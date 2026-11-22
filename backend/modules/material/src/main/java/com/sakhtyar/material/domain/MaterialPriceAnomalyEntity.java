package com.sakhtyar.material.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_price_anomaly")
public class MaterialPriceAnomalyEntity {

    @Id
    private UUID id;

    @Column(name = "observation_id", nullable = false, unique = true)
    private UUID observationId;

    @Column(name = "anomaly_type", nullable = false, length = 60)
    private String anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceAnomalySeverity severity;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PriceAnomalyStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MaterialPriceAnomalyEntity() {}

    public MaterialPriceAnomalyEntity(
            UUID id, UUID observationId, String anomalyType, PriceAnomalySeverity severity,
            BigDecimal score, String reason, Instant createdAt
    ) {
        this.id = id;
        this.observationId = observationId;
        this.anomalyType = anomalyType;
        this.severity = severity;
        this.score = score;
        this.status = PriceAnomalyStatus.OPEN;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void review(PriceAnomalyStatus status, String reviewer, String note, Instant now) {
        if (status == PriceAnomalyStatus.OPEN) {
            throw new IllegalArgumentException("Anomaly review must resolve to ACCEPTED or DISMISSED.");
        }
        this.status = status;
        this.reviewedBy = reviewer;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getObservationId() { return observationId; }
    public String getAnomalyType() { return anomalyType; }
    public PriceAnomalySeverity getSeverity() { return severity; }
    public BigDecimal getScore() { return score; }
    public PriceAnomalyStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
}