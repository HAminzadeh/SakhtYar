package com.sakhtyar.builder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "builder_profile")
public class BuilderProfileEntity {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 250)
    private String legalName;

    @Column(name = "trade_name", length = 250)
    private String tradeName;

    @Column(name = "national_identifier", length = 30)
    private String nationalIdentifier;

    @Column(name = "registration_no", length = 60)
    private String registrationNo;

    @Column(length = 30)
    private String mobile;

    @Column(length = 30)
    private String phone;

    @Column(length = 250)
    private String email;

    @Column(length = 500)
    private String website;

    @Column(length = 100)
    private String province;

    @Column(length = 100)
    private String city;

    @Column(columnDefinition = "text")
    private String address;

    @Column(nullable = false)
    private boolean verified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuilderProfileEntity() {}

    public BuilderProfileEntity(
            UUID id,
            String legalName,
            String tradeName,
            String nationalIdentifier,
            String registrationNo,
            String mobile,
            String phone,
            String email,
            String website,
            String province,
            String city,
            String address,
            boolean verified,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.nationalIdentifier = nationalIdentifier;
        this.registrationNo = registrationNo;
        this.mobile = mobile;
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.province = province;
        this.city = city;
        this.address = address;
        this.verified = verified;
        this.metadata = mutable(metadata);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private Map<String, Object> mutable(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    public UUID getId() { return id; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getNationalIdentifier() { return nationalIdentifier; }
    public String getRegistrationNo() { return registrationNo; }
    public String getMobile() { return mobile; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public boolean isVerified() { return verified; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}