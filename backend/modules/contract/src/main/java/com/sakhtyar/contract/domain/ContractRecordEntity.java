package com.sakhtyar.contract.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract_record")
public class ContractRecordEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "contract_code", nullable = false, length = 80)
    private String contractCode;

    @Column(name = "contract_type", nullable = false, length = 60)
    private String contractType;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> terms;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContractRecordEntity() {}

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getContractCode() { return contractCode; }
    public String getContractType() { return contractType; }
    public String getStatus() { return status; }
    public String getTitle() { return title; }
    public Instant getSignedAt() { return signedAt; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getSchemaVersion() { return schemaVersion; }
    public Map<String, Object> getTerms() { return terms == null ? Map.of() : Map.copyOf(terms); }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}