package com.sakhtyar.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_source")
public class KnowledgeSourceEntity {

    @Id
    private UUID id;

    @Column(name = "source_code", nullable = false, unique = true, length = 100)
    private String sourceCode;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "base_url", length = 2000)
    private String baseUrl;

    @Column(name = "jurisdiction_country", length = 2)
    private String jurisdictionCountry;

    @Column(name = "jurisdiction_province", length = 100)
    private String jurisdictionProvince;

    @Column(name = "jurisdiction_city", length = 100)
    private String jurisdictionCity;

    @Column(name = "trust_level", nullable = false, length = 40)
    private String trustLevel;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeSourceEntity() {}

    public UUID getId() { return id; }
    public String getSourceCode() { return sourceCode; }
    public String getName() { return name; }
    public String getSourceType() { return sourceType; }
    public String getBaseUrl() { return baseUrl; }
    public String getJurisdictionCountry() { return jurisdictionCountry; }
    public String getJurisdictionProvince() { return jurisdictionProvince; }
    public String getJurisdictionCity() { return jurisdictionCity; }
    public String getTrustLevel() { return trustLevel; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}