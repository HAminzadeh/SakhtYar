package com.sakhtyar.casefile.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "construction_case")
public class CaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 250)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CaseStatus status;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "land_area_m2", precision = 12, scale = 2)
    private BigDecimal landAreaM2;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaseEntity() {
    }

    public CaseEntity(
            UUID id,
            String title,
            CaseStatus status,
            String description,
            String city,
            String district,
            String address,
            BigDecimal landAreaM2,
            String createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.description = description;
        this.city = city;
        this.district = district;
        this.address = address;
        this.landAreaM2 = landAreaM2;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            String title,
            CaseStatus status,
            String description,
            String city,
            String district,
            String address,
            BigDecimal landAreaM2
    ) {
        this.title = title;
        this.status = status;
        this.description = description;
        this.city = city;
        this.district = district;
        this.address = address;
        this.landAreaM2 = landAreaM2;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public CaseStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getAddress() { return address; }
    public BigDecimal getLandAreaM2() { return landAreaM2; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
