param(
    [string]$Branch = "v2",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 94) -ForegroundColor DarkCyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 94) -ForegroundColor DarkCyan
}

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "WROTE  $($Path.Replace($RepoRoot + '\',''))" -ForegroundColor Green
}

function Backup-IfExists([string]$RelativePath) {
    $src = Join-Path $RepoRoot $RelativePath
    if (Test-Path $src) {
        $dest = Join-Path $BackupDir $RelativePath
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
        Copy-Item $src $dest -Force
    }
}

function Replace-Once([string]$Path, [string]$Old, [string]$New, [string]$AlreadyMarker) {
    $content = [System.IO.File]::ReadAllText($Path)

    if ($AlreadyMarker -and $content.Contains($AlreadyMarker)) {
        Write-Host "SKIP   $($Path.Replace($RepoRoot + '\','')) (already updated)" -ForegroundColor Yellow
        return
    }

    $normalizedContent = $content.Replace("`r`n", "`n")
    $normalizedOld = $Old.Replace("`r`n", "`n")
    $normalizedNew = $New.Replace("`r`n", "`n")

    if (-not $normalizedContent.Contains($normalizedOld)) {
        throw "Expected text was not found in $Path even after line-ending normalization. Stopping instead of guessing."
    }

    $normalizedContent = $normalizedContent.Replace($normalizedOld, $normalizedNew)
    [System.IO.File]::WriteAllText($Path, $normalizedContent, [System.Text.UTF8Encoding]::new($false))
    Write-Host "PATCH  $($Path.Replace($RepoRoot + '\',''))" -ForegroundColor Green
}

Write-Step "SakhtYar Phase 5.7 - Urban & Regulation Engine (directly on v2)"

$RepoRoot = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($RepoRoot)) {
    throw "Run this script from inside the SakhtYar repository."
}
$RepoRoot = $RepoRoot.Trim()
Set-Location $RepoRoot

$remoteUrl = (& git remote get-url origin 2>$null)
if ($LASTEXITCODE -ne 0 -or $remoteUrl -notmatch "HAminzadeh/SakhtYar") {
    throw "Unexpected Git origin. Expected HAminzadeh/SakhtYar."
}

$currentBranch = (& git branch --show-current).Trim()
if ($currentBranch -ne $Branch) {
    throw "Current branch is '$currentBranch'. This script intentionally works only on '$Branch'."
}

$selfPath = $MyInvocation.MyCommand.Path
$selfRel = $null
if ($selfPath) {
    try {
        $selfRel = [System.IO.Path]::GetRelativePath($RepoRoot, $selfPath).Replace('\','/')
    } catch {}
}

$staged = @(& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw "Could not inspect staged files." }
$staged = @($staged | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

if ($staged.Count -gt 0) {
    if ($selfRel -and $staged.Count -eq 1 -and $staged[0].Replace('\','/') -eq $selfRel) {
        Write-Host "Only this helper script is staged; unstaging it automatically." -ForegroundColor Yellow
        Invoke-Git restore --staged -- $selfRel
    } else {
        throw "Staged changes exist. Commit or stash them before Phase 5.7.`n$($staged -join "`n")"
    }
}

& git diff --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Tracked working-tree changes exist. Commit or stash them before Phase 5.7."
}

Write-Step "1/8 - Updating v2"
Invoke-Git fetch origin $Branch
Invoke-Git pull --ff-only origin $Branch
$baseSha = (& git rev-parse HEAD).Trim()
Write-Host "Working directly on $Branch at $baseSha" -ForegroundColor Green

Write-Step "2/8 - Creating backup"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $RepoRoot ".local\patch-backups\phase-5.7-$stamp"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

@(
    "backend/pom.xml",
    "backend/app/pom.xml",
    "backend/app/src/main/resources/db/migration/V15__phase5_urban_regulation_engine.sql",
    "backend/modules/regulation/pom.xml",
    "docs/phase-5/phase-5.7-urban-regulation-engine.md",
    "docs/phase-5/phase-5.7-regulation-api.md"
) | ForEach-Object { Backup-IfExists $_ }

Write-Host "Backup: $BackupDir" -ForegroundColor Green

Write-Step "3/8 - Registering regulation module"

$parentPom = Join-Path $RepoRoot "backend/pom.xml"
$appPom = Join-Path $RepoRoot "backend/app/pom.xml"

Replace-Once $parentPom @'
    <module>modules/assembly</module>
    <module>modules/scenario</module>
    <module>modules/analysis</module>
'@ @'
    <module>modules/assembly</module>
    <module>modules/scenario</module>
    <module>modules/regulation</module>
    <module>modules/analysis</module>
'@ "<module>modules/regulation</module>"

Replace-Once $parentPom @'
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-scenario</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-analysis</artifactId>
        <version>${project.version}</version>
      </dependency>
'@ @'
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-scenario</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-regulation</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-analysis</artifactId>
        <version>${project.version}</version>
      </dependency>
'@ "<artifactId>sakhtyar-regulation</artifactId>"

Replace-Once $appPom @'
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-scenario</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-analysis</artifactId></dependency>
'@ @'
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-scenario</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-regulation</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-analysis</artifactId></dependency>
'@ "<artifactId>sakhtyar-regulation</artifactId>"

$modulePom = @'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.sakhtyar</groupId>
    <artifactId>sakhtyar-backend-parent</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>sakhtyar-regulation</artifactId>

  <dependencies>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-shared-kernel</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-audit</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-property</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-knowledge</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-scenario</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/regulation/pom.xml") $modulePom

Write-Step "4/8 - Writing V15 urban regulation migration"

$migration = @'
-- SakhtYar Phase 5.7 - Urban & Regulation Engine

CREATE TABLE urban_rule (
    id UUID PRIMARY KEY,
    code VARCHAR(140) NOT NULL UNIQUE,
    name_fa VARCHAR(500) NOT NULL,
    name_en VARCHAR(500),
    rule_type VARCHAR(50) NOT NULL,
    jurisdiction_country VARCHAR(2),
    jurisdiction_province VARCHAR(100),
    jurisdiction_city VARCHAR(100),
    jurisdiction_district VARCHAR(100),
    property_type VARCHAR(80),
    source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE RESTRICT,
    source_url VARCHAR(2000),
    valid_from DATE,
    valid_to DATE,
    priority INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_urban_rule_params_object CHECK (jsonb_typeof(parameters) = 'object'),
    CONSTRAINT chk_urban_rule_validity CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_urban_rule_scope
    ON urban_rule(jurisdiction_province, jurisdiction_city, jurisdiction_district, property_type, active);

CREATE INDEX idx_urban_rule_type
    ON urban_rule(rule_type, priority);

CREATE TABLE urban_evaluation (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES property(id) ON DELETE RESTRICT,
    scenario_id UUID REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL,
    rule_count INTEGER NOT NULL,
    passed_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    review_count INTEGER NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluated_by VARCHAR(150) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_urban_eval_counts CHECK (
        rule_count >= 0 AND passed_count >= 0 AND failed_count >= 0 AND review_count >= 0
        AND passed_count + failed_count + review_count = rule_count
    ),
    CONSTRAINT chk_urban_eval_snapshot_object CHECK (jsonb_typeof(input_snapshot) = 'object')
);

CREATE INDEX idx_urban_evaluation_property
    ON urban_evaluation(property_id, evaluated_at DESC);

CREATE INDEX idx_urban_evaluation_scenario
    ON urban_evaluation(scenario_id, evaluated_at DESC);

CREATE TABLE urban_evaluation_result (
    id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES urban_evaluation(id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES urban_rule(id) ON DELETE RESTRICT,
    outcome VARCHAR(30) NOT NULL,
    actual_value VARCHAR(500),
    expected_value VARCHAR(500),
    message VARCHAR(2000) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_urban_result_details_object CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_urban_evaluation_result_eval
    ON urban_evaluation_result(evaluation_id);

CREATE INDEX idx_urban_evaluation_result_rule
    ON urban_evaluation_result(rule_id, outcome);
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/app/src/main/resources/db/migration/V15__phase5_urban_regulation_engine.sql") $migration

Write-Step "5/8 - Writing regulation domain"

$files = @{}

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanRuleType.java"] = @'
package com.sakhtyar.regulation.domain;

public enum UrbanRuleType {
    MAX_FLOORS,
    MAX_FAR,
    MAX_COVERAGE_PERCENT,
    MIN_PASSAGE_WIDTH,
    MIN_FRONTAGE,
    MAX_HEIGHT
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/EvaluationOutcome.java"] = @'
package com.sakhtyar.regulation.domain;

public enum EvaluationOutcome {
    PASS,
    FAIL,
    REVIEW
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/EvaluationStatus.java"] = @'
package com.sakhtyar.regulation.domain;

public enum EvaluationStatus {
    COMPLIANT,
    NON_COMPLIANT,
    REVIEW_REQUIRED
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanRuleEntity.java"] = @'
package com.sakhtyar.regulation.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "urban_rule")
public class UrbanRuleEntity {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=140) private String code;
    @Column(name="name_fa", nullable=false, length=500) private String nameFa;
    @Column(name="name_en", length=500) private String nameEn;
    @Enumerated(EnumType.STRING)
    @Column(name="rule_type", nullable=false, length=50) private UrbanRuleType ruleType;
    @Column(name="jurisdiction_country", length=2) private String jurisdictionCountry;
    @Column(name="jurisdiction_province", length=100) private String jurisdictionProvince;
    @Column(name="jurisdiction_city", length=100) private String jurisdictionCity;
    @Column(name="jurisdiction_district", length=100) private String jurisdictionDistrict;
    @Column(name="property_type", length=80) private String propertyType;
    @Column(name="source_id", nullable=false) private UUID sourceId;
    @Column(name="source_url", length=2000) private String sourceUrl;
    @Column(name="valid_from") private LocalDate validFrom;
    @Column(name="valid_to") private LocalDate validTo;
    @Column(nullable=false) private int priority;
    @Column(nullable=false) private boolean active;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable=false, columnDefinition="jsonb") private Map<String,Object> parameters;
    @Column(name="created_by", nullable=false, length=150) private String createdBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;

    protected UrbanRuleEntity(){}

    public UrbanRuleEntity(UUID id,String code,String nameFa,String nameEn,UrbanRuleType ruleType,
            String country,String province,String city,String district,String propertyType,
            UUID sourceId,String sourceUrl,LocalDate validFrom,LocalDate validTo,int priority,
            boolean active,Map<String,Object> parameters,String createdBy,Instant now) {
        this.id=id; this.code=code; this.nameFa=nameFa; this.nameEn=nameEn; this.ruleType=ruleType;
        this.jurisdictionCountry=country; this.jurisdictionProvince=province; this.jurisdictionCity=city;
        this.jurisdictionDistrict=district; this.propertyType=propertyType; this.sourceId=sourceId;
        this.sourceUrl=sourceUrl; this.validFrom=validFrom; this.validTo=validTo; this.priority=priority;
        this.active=active; this.parameters=parameters==null?Map.of():Map.copyOf(parameters);
        this.createdBy=createdBy; this.createdAt=now; this.updatedAt=now;
    }

    public void update(String nameFa,String nameEn,UrbanRuleType ruleType,String country,
            String province,String city,String district,String propertyType,UUID sourceId,
            String sourceUrl,LocalDate validFrom,LocalDate validTo,int priority,boolean active,
            Map<String,Object> parameters,Instant now) {
        this.nameFa=nameFa; this.nameEn=nameEn; this.ruleType=ruleType; this.jurisdictionCountry=country;
        this.jurisdictionProvince=province; this.jurisdictionCity=city; this.jurisdictionDistrict=district;
        this.propertyType=propertyType; this.sourceId=sourceId; this.sourceUrl=sourceUrl;
        this.validFrom=validFrom; this.validTo=validTo; this.priority=priority; this.active=active;
        this.parameters=parameters==null?Map.of():Map.copyOf(parameters); this.updatedAt=now;
    }

    public UUID getId(){return id;} public String getCode(){return code;}
    public String getNameFa(){return nameFa;} public String getNameEn(){return nameEn;}
    public UrbanRuleType getRuleType(){return ruleType;} public String getJurisdictionCountry(){return jurisdictionCountry;}
    public String getJurisdictionProvince(){return jurisdictionProvince;} public String getJurisdictionCity(){return jurisdictionCity;}
    public String getJurisdictionDistrict(){return jurisdictionDistrict;} public String getPropertyType(){return propertyType;}
    public UUID getSourceId(){return sourceId;} public String getSourceUrl(){return sourceUrl;}
    public LocalDate getValidFrom(){return validFrom;} public LocalDate getValidTo(){return validTo;}
    public int getPriority(){return priority;} public boolean isActive(){return active;}
    public Map<String,Object> getParameters(){return parameters==null?Map.of():Map.copyOf(parameters);}
    public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanEvaluationEntity.java"] = @'
package com.sakhtyar.regulation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="urban_evaluation")
public class UrbanEvaluationEntity {
    @Id private UUID id;
    @Column(name="property_id",nullable=false) private UUID propertyId;
    @Column(name="scenario_id") private UUID scenarioId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private EvaluationStatus status;
    @Column(name="rule_count",nullable=false) private int ruleCount;
    @Column(name="passed_count",nullable=false) private int passedCount;
    @Column(name="failed_count",nullable=false) private int failedCount;
    @Column(name="review_count",nullable=false) private int reviewCount;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @Column(name="evaluated_by",nullable=false,length=150) private String evaluatedBy;
    @Column(name="evaluated_at",nullable=false) private Instant evaluatedAt;

    protected UrbanEvaluationEntity(){}

    public UrbanEvaluationEntity(UUID id,UUID propertyId,UUID scenarioId,EvaluationStatus status,
            int ruleCount,int passedCount,int failedCount,int reviewCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,String evaluatedBy,Instant evaluatedAt) {
        this.id=id; this.propertyId=propertyId; this.scenarioId=scenarioId; this.status=status;
        this.ruleCount=ruleCount; this.passedCount=passedCount; this.failedCount=failedCount;
        this.reviewCount=reviewCount; this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.evaluatedBy=evaluatedBy; this.evaluatedAt=evaluatedAt;
    }

    public UUID getId(){return id;} public UUID getPropertyId(){return propertyId;}
    public UUID getScenarioId(){return scenarioId;} public EvaluationStatus getStatus(){return status;}
    public int getRuleCount(){return ruleCount;} public int getPassedCount(){return passedCount;}
    public int getFailedCount(){return failedCount;} public int getReviewCount(){return reviewCount;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public String getEvaluatedBy(){return evaluatedBy;} public Instant getEvaluatedAt(){return evaluatedAt;}
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanEvaluationResultEntity.java"] = @'
package com.sakhtyar.regulation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="urban_evaluation_result")
public class UrbanEvaluationResultEntity {
    @Id private UUID id;
    @Column(name="evaluation_id",nullable=false) private UUID evaluationId;
    @Column(name="rule_id",nullable=false) private UUID ruleId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EvaluationOutcome outcome;
    @Column(name="actual_value",length=500) private String actualValue;
    @Column(name="expected_value",length=500) private String expectedValue;
    @Column(nullable=false,length=2000) private String message;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> details;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected UrbanEvaluationResultEntity(){}

    public UrbanEvaluationResultEntity(UUID id,UUID evaluationId,UUID ruleId,EvaluationOutcome outcome,
            String actualValue,String expectedValue,String message,Map<String,Object> details,Instant createdAt) {
        this.id=id; this.evaluationId=evaluationId; this.ruleId=ruleId; this.outcome=outcome;
        this.actualValue=actualValue; this.expectedValue=expectedValue; this.message=message;
        this.details=details==null?Map.of():Map.copyOf(details); this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getEvaluationId(){return evaluationId;}
    public UUID getRuleId(){return ruleId;} public EvaluationOutcome getOutcome(){return outcome;}
    public String getActualValue(){return actualValue;} public String getExpectedValue(){return expectedValue;}
    public String getMessage(){return message;} public Map<String,Object> getDetails(){return details==null?Map.of():Map.copyOf(details);}
    public Instant getCreatedAt(){return createdAt;}
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanRuleRepository.java"] = @'
package com.sakhtyar.regulation.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UrbanRuleRepository extends JpaRepository<UrbanRuleEntity,UUID>{
    Optional<UrbanRuleEntity> findByCodeIgnoreCase(String code);
    List<UrbanRuleEntity> findAllByOrderByPriorityAscCodeAsc();
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanEvaluationRepository.java"] = @'
package com.sakhtyar.regulation.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UrbanEvaluationRepository extends JpaRepository<UrbanEvaluationEntity,UUID>{
    List<UrbanEvaluationEntity> findByPropertyIdOrderByEvaluatedAtDesc(UUID propertyId);
}
'@

$files["backend/modules/regulation/src/main/java/com/sakhtyar/regulation/domain/UrbanEvaluationResultRepository.java"] = @'
package com.sakhtyar.regulation.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UrbanEvaluationResultRepository extends JpaRepository<UrbanEvaluationResultEntity,UUID>{
    List<UrbanEvaluationResultEntity> findByEvaluationIdOrderByIdAsc(UUID evaluationId);
}
'@

foreach ($relative in $files.Keys) {
    Write-Utf8NoBom (Join-Path $RepoRoot $relative) $files[$relative]
}

Write-Step "6/8 - Writing regulation evaluator, service, API and tests"

$evaluator = @'
package com.sakhtyar.regulation.application;

import com.sakhtyar.regulation.domain.*;
import java.math.*;
import java.util.Map;

public final class UrbanRuleEvaluator {

    public record EvaluationInput(
            BigDecimal landAreaM2,
            BigDecimal frontageM,
            BigDecimal passageWidthM,
            Integer proposedFloors,
            BigDecimal totalBuiltAreaM2,
            BigDecimal footprintAreaM2,
            BigDecimal proposedHeightM
    ) {}

    public record Result(
            EvaluationOutcome outcome,
            String actualValue,
            String expectedValue,
            String message
    ) {}

    public Result evaluate(UrbanRuleType type, Map<String,Object> parameters, EvaluationInput input) {
        return switch (type) {
            case MAX_FLOORS -> maxFloors(parameters, input);
            case MAX_FAR -> maxFar(parameters, input);
            case MAX_COVERAGE_PERCENT -> maxCoverage(parameters, input);
            case MIN_PASSAGE_WIDTH -> minPassage(parameters, input);
            case MIN_FRONTAGE -> minFrontage(parameters, input);
            case MAX_HEIGHT -> maxHeight(parameters, input);
        };
    }

    private Result maxFloors(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxFloors");
        if (limit == null) return review("Missing parameter maxFloors.");
        if (i.proposedFloors() == null) return review("Proposed floor count is missing.");
        BigDecimal actual = BigDecimal.valueOf(i.proposedFloors());
        return compareMax(actual, limit, "floors");
    }

    private Result maxFar(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxFar");
        if (limit == null) return review("Missing parameter maxFar.");
        if (i.landAreaM2() == null || i.landAreaM2().signum() <= 0 || i.totalBuiltAreaM2() == null) {
            return review("Land area or total built area is missing for FAR evaluation.");
        }
        BigDecimal actual = i.totalBuiltAreaM2().divide(i.landAreaM2(), 6, RoundingMode.HALF_UP);
        return compareMax(actual, limit, "FAR");
    }

    private Result maxCoverage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxCoveragePercent");
        if (limit == null) return review("Missing parameter maxCoveragePercent.");
        if (i.landAreaM2() == null || i.landAreaM2().signum() <= 0 || i.footprintAreaM2() == null) {
            return review("Land area or footprint area is missing for coverage evaluation.");
        }
        BigDecimal actual = i.footprintAreaM2()
                .multiply(new BigDecimal("100"))
                .divide(i.landAreaM2(), 4, RoundingMode.HALF_UP);
        return compareMax(actual, limit, "coveragePercent");
    }

    private Result minPassage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "minPassageWidthM");
        if (limit == null) return review("Missing parameter minPassageWidthM.");
        if (i.passageWidthM() == null) return review("Passage width is missing.");
        return compareMin(i.passageWidthM(), limit, "passageWidthM");
    }

    private Result minFrontage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "minFrontageM");
        if (limit == null) return review("Missing parameter minFrontageM.");
        if (i.frontageM() == null) return review("Frontage is missing.");
        return compareMin(i.frontageM(), limit, "frontageM");
    }

    private Result maxHeight(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxHeightM");
        if (limit == null) return review("Missing parameter maxHeightM.");
        if (i.proposedHeightM() == null) return review("Proposed height is missing.");
        return compareMax(i.proposedHeightM(), limit, "heightM");
    }

    private Result compareMax(BigDecimal actual, BigDecimal expected, String label) {
        boolean pass = actual.compareTo(expected) <= 0;
        return new Result(
                pass ? EvaluationOutcome.PASS : EvaluationOutcome.FAIL,
                actual.stripTrailingZeros().toPlainString(),
                "<= " + expected.stripTrailingZeros().toPlainString(),
                pass ? label + " is within maximum allowed value." : label + " exceeds maximum allowed value."
        );
    }

    private Result compareMin(BigDecimal actual, BigDecimal expected, String label) {
        boolean pass = actual.compareTo(expected) >= 0;
        return new Result(
                pass ? EvaluationOutcome.PASS : EvaluationOutcome.FAIL,
                actual.stripTrailingZeros().toPlainString(),
                ">= " + expected.stripTrailingZeros().toPlainString(),
                pass ? label + " satisfies minimum required value." : label + " is below minimum required value."
        );
    }

    private Result review(String message) {
        return new Result(EvaluationOutcome.REVIEW, null, null, message);
    }

    private BigDecimal number(Map<String,Object> p, String key) {
        if (p == null) return null;
        Object value = p.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException ex) { return null; }
    }
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/regulation/src/main/java/com/sakhtyar/regulation/application/UrbanRuleEvaluator.java") $evaluator

$dtos = @'
package com.sakhtyar.regulation.api;

import com.sakhtyar.regulation.domain.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class RegulationDtos {
    private RegulationDtos(){}

    public record UpsertRuleRequest(
            @NotBlank @Size(max=140) String code,
            @NotBlank @Size(max=500) String nameFa,
            @Size(max=500) String nameEn,
            @NotNull UrbanRuleType ruleType,
            @Size(max=2) String jurisdictionCountry,
            @Size(max=100) String jurisdictionProvince,
            @Size(max=100) String jurisdictionCity,
            @Size(max=100) String jurisdictionDistrict,
            @Size(max=80) String propertyType,
            @NotNull UUID sourceId,
            @Size(max=2000) String sourceUrl,
            LocalDate validFrom,
            LocalDate validTo,
            @Min(0) int priority,
            boolean active,
            @NotNull Map<String,Object> parameters
    ) {}

    public record EvaluateRequest(@NotNull UUID propertyId, UUID scenarioId) {}

    public record RuleResponse(
            UUID id,String code,String nameFa,String nameEn,UrbanRuleType ruleType,
            String jurisdictionCountry,String jurisdictionProvince,String jurisdictionCity,
            String jurisdictionDistrict,String propertyType,UUID sourceId,String sourceUrl,
            LocalDate validFrom,LocalDate validTo,int priority,boolean active,
            Map<String,Object> parameters,String createdBy,Instant createdAt,Instant updatedAt
    ) {
        public static RuleResponse from(UrbanRuleEntity e) {
            return new RuleResponse(e.getId(),e.getCode(),e.getNameFa(),e.getNameEn(),e.getRuleType(),
                e.getJurisdictionCountry(),e.getJurisdictionProvince(),e.getJurisdictionCity(),
                e.getJurisdictionDistrict(),e.getPropertyType(),e.getSourceId(),e.getSourceUrl(),
                e.getValidFrom(),e.getValidTo(),e.getPriority(),e.isActive(),e.getParameters(),
                e.getCreatedBy(),e.getCreatedAt(),e.getUpdatedAt());
        }
    }

    public record ResultResponse(
            UUID id,UUID ruleId,EvaluationOutcome outcome,String actualValue,String expectedValue,
            String message,Map<String,Object> details,Instant createdAt
    ) {
        public static ResultResponse from(UrbanEvaluationResultEntity e) {
            return new ResultResponse(e.getId(),e.getRuleId(),e.getOutcome(),e.getActualValue(),
                e.getExpectedValue(),e.getMessage(),e.getDetails(),e.getCreatedAt());
        }
    }

    public record EvaluationResponse(
            UUID id,UUID propertyId,UUID scenarioId,EvaluationStatus status,int ruleCount,
            int passedCount,int failedCount,int reviewCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,String evaluatedBy,Instant evaluatedAt,
            List<ResultResponse> results
    ) {}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/regulation/src/main/java/com/sakhtyar/regulation/api/RegulationDtos.java") $dtos

$service = @'
package com.sakhtyar.regulation.application;

import static com.sakhtyar.regulation.api.RegulationDtos.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.domain.KnowledgeSourceRepository;
import com.sakhtyar.property.domain.*;
import com.sakhtyar.regulation.domain.*;
import com.sakhtyar.scenario.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegulationService {
    private static final String ALGORITHM_VERSION="1.0";

    private final UrbanRuleRepository ruleRepo;
    private final UrbanEvaluationRepository evaluationRepo;
    private final UrbanEvaluationResultRepository resultRepo;
    private final PropertyRepository propertyRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final KnowledgeSourceRepository sourceRepo;
    private final AuditService auditService;
    private final UrbanRuleEvaluator evaluator = new UrbanRuleEvaluator();

    public RegulationService(UrbanRuleRepository ruleRepo,UrbanEvaluationRepository evaluationRepo,
            UrbanEvaluationResultRepository resultRepo,PropertyRepository propertyRepo,
            ConstructionScenarioRepository scenarioRepo,KnowledgeSourceRepository sourceRepo,
            AuditService auditService) {
        this.ruleRepo=ruleRepo; this.evaluationRepo=evaluationRepo; this.resultRepo=resultRepo;
        this.propertyRepo=propertyRepo; this.scenarioRepo=scenarioRepo; this.sourceRepo=sourceRepo;
        this.auditService=auditService;
    }

    @Transactional(readOnly=true)
    public List<RuleResponse> rules() {
        return ruleRepo.findAllByOrderByPriorityAscCodeAsc().stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse createRule(UpsertRuleRequest r,Authentication auth) {
        String code=r.code().trim().toUpperCase(Locale.ROOT);
        ruleRepo.findByCodeIgnoreCase(code).ifPresent(x->{throw new IllegalArgumentException("Urban rule code already exists.");});
        validateRuleRequest(r);
        Instant now=Instant.now();
        var e=new UrbanRuleEntity(UUID.randomUUID(),code,r.nameFa().trim(),trim(r.nameEn()),r.ruleType(),
            upper(r.jurisdictionCountry()),trim(r.jurisdictionProvince()),trim(r.jurisdictionCity()),
            trim(r.jurisdictionDistrict()),trim(r.propertyType()),r.sourceId(),trim(r.sourceUrl()),
            r.validFrom(),r.validTo(),r.priority(),r.active(),r.parameters(),actor(auth),now);
        ruleRepo.save(e);
        auditService.record("URBAN_RULE",e.getId(),"URBAN_RULE_CREATED",Map.of("code",code,"ruleType",r.ruleType().name()));
        return RuleResponse.from(e);
    }

    @Transactional
    public RuleResponse updateRule(UUID id,UpsertRuleRequest r) {
        var e=requireRule(id);
        if(!e.getCode().equalsIgnoreCase(r.code().trim())) throw new IllegalArgumentException("Urban rule code is immutable.");
        validateRuleRequest(r);
        e.update(r.nameFa().trim(),trim(r.nameEn()),r.ruleType(),upper(r.jurisdictionCountry()),
            trim(r.jurisdictionProvince()),trim(r.jurisdictionCity()),trim(r.jurisdictionDistrict()),
            trim(r.propertyType()),r.sourceId(),trim(r.sourceUrl()),r.validFrom(),r.validTo(),
            r.priority(),r.active(),r.parameters(),Instant.now());
        auditService.record("URBAN_RULE",id,"URBAN_RULE_UPDATED",Map.of("code",e.getCode(),"active",e.isActive()));
        return RuleResponse.from(e);
    }

    @Transactional
    public EvaluationResponse evaluate(EvaluateRequest r,Authentication auth) {
        PropertyEntity property=propertyRepo.findById(r.propertyId())
            .orElseThrow(()->new IllegalArgumentException("Property not found."));
        ConstructionScenarioEntity scenario=null;
        if(r.scenarioId()!=null) {
            scenario=scenarioRepo.findById(r.scenarioId())
                .orElseThrow(()->new IllegalArgumentException("Scenario not found."));
        }

        LocalDate today=LocalDate.now();
        List<UrbanRuleEntity> applicable=ruleRepo.findAllByOrderByPriorityAscCodeAsc().stream()
            .filter(UrbanRuleEntity::isActive)
            .filter(x->x.getValidFrom()==null||!today.isBefore(x.getValidFrom()))
            .filter(x->x.getValidTo()==null||!today.isAfter(x.getValidTo()))
            .filter(x->matches(x.getJurisdictionProvince(),property.getProvince()))
            .filter(x->matches(x.getJurisdictionCity(),property.getCity()))
            .filter(x->matches(x.getJurisdictionDistrict(),property.getDistrict()))
            .filter(x->matches(x.getPropertyType(),property.getPropertyType()))
            .toList();

        Map<String,Object> assumptions=scenario==null?Map.of():scenario.getAssumptions();
        Integer proposedFloors=integer(assumptions.get("proposedFloors"));
        BigDecimal totalBuiltArea=decimal(assumptions.get("totalBuiltAreaM2"));
        BigDecimal footprintArea=decimal(assumptions.get("footprintAreaM2"));
        BigDecimal proposedHeight=decimal(assumptions.get("proposedHeightM"));

        if(proposedFloors==null) proposedFloors=property.getExistingFloors();
        if(totalBuiltArea==null) totalBuiltArea=property.getBuildingAreaM2();

        var input=new UrbanRuleEvaluator.EvaluationInput(
            property.getLandAreaM2(),property.getFrontageM(),property.getPassageWidthM(),
            proposedFloors,totalBuiltArea,footprintArea,proposedHeight
        );

        List<PreparedResult> prepared=new ArrayList<>();
        int pass=0,fail=0,review=0;

        for(var rule:applicable) {
            var result=evaluator.evaluate(rule.getRuleType(),rule.getParameters(),input);
            switch(result.outcome()) {
                case PASS -> pass++;
                case FAIL -> fail++;
                case REVIEW -> review++;
            }
            prepared.add(new PreparedResult(rule,result));
        }

        EvaluationStatus status=fail>0?EvaluationStatus.NON_COMPLIANT:
            (review>0?EvaluationStatus.REVIEW_REQUIRED:EvaluationStatus.COMPLIANT);

        Map<String,Object> snapshot=new LinkedHashMap<>();
        put(snapshot,"propertyId",property.getId().toString());
        put(snapshot,"caseId",property.getCaseId().toString());
        put(snapshot,"scenarioId",scenario==null?null:scenario.getId().toString());
        put(snapshot,"province",property.getProvince()); put(snapshot,"city",property.getCity());
        put(snapshot,"district",property.getDistrict()); put(snapshot,"propertyType",property.getPropertyType());
        put(snapshot,"landAreaM2",text(property.getLandAreaM2())); put(snapshot,"frontageM",text(property.getFrontageM()));
        put(snapshot,"passageWidthM",text(property.getPassageWidthM()));
        put(snapshot,"proposedFloors",proposedFloors); put(snapshot,"totalBuiltAreaM2",text(totalBuiltArea));
        put(snapshot,"footprintAreaM2",text(footprintArea)); put(snapshot,"proposedHeightM",text(proposedHeight));

        Instant now=Instant.now();
        var evaluation=new UrbanEvaluationEntity(UUID.randomUUID(),property.getId(),r.scenarioId(),status,
            applicable.size(),pass,fail,review,ALGORITHM_VERSION,snapshot,actor(auth),now);
        evaluationRepo.save(evaluation);

        List<UrbanEvaluationResultEntity> resultEntities=new ArrayList<>();
        for(var p:prepared) {
            Map<String,Object> details=Map.of(
                "ruleCode",p.rule().getCode(),
                "ruleType",p.rule().getRuleType().name(),
                "sourceId",p.rule().getSourceId().toString()
            );
            var entity=new UrbanEvaluationResultEntity(UUID.randomUUID(),evaluation.getId(),p.rule().getId(),
                p.result().outcome(),p.result().actualValue(),p.result().expectedValue(),
                p.result().message(),details,now);
            resultEntities.add(resultRepo.save(entity));
        }

        auditService.record("URBAN_EVALUATION",evaluation.getId(),"URBAN_REGULATION_EVALUATED",
            Map.of("propertyId",property.getId().toString(),"status",status.name(),"ruleCount",applicable.size()));

        return toResponse(evaluation,resultEntities);
    }

    @Transactional(readOnly=true)
    public List<EvaluationResponse> evaluations(UUID propertyId) {
        return evaluationRepo.findByPropertyIdOrderByEvaluatedAtDesc(propertyId).stream()
            .map(e->toResponse(e,resultRepo.findByEvaluationIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public EvaluationResponse evaluation(UUID id) {
        var e=evaluationRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Urban evaluation not found."));
        return toResponse(e,resultRepo.findByEvaluationIdOrderByIdAsc(id));
    }

    private EvaluationResponse toResponse(UrbanEvaluationEntity e,List<UrbanEvaluationResultEntity> results) {
        return new EvaluationResponse(e.getId(),e.getPropertyId(),e.getScenarioId(),e.getStatus(),
            e.getRuleCount(),e.getPassedCount(),e.getFailedCount(),e.getReviewCount(),e.getAlgorithmVersion(),
            e.getInputSnapshot(),e.getEvaluatedBy(),e.getEvaluatedAt(),
            results.stream().map(ResultResponse::from).toList());
    }

    private void validateRuleRequest(UpsertRuleRequest r) {
        if(!sourceRepo.existsById(r.sourceId())) throw new IllegalArgumentException("Knowledge source not found.");
        if(r.validFrom()!=null&&r.validTo()!=null&&r.validTo().isBefore(r.validFrom()))
            throw new IllegalArgumentException("validTo cannot be before validFrom.");
        if(r.parameters()==null||r.parameters().isEmpty())
            throw new IllegalArgumentException("Rule parameters are required.");
    }

    private UrbanRuleEntity requireRule(UUID id) {
        return ruleRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Urban rule not found."));
    }

    private static boolean matches(String ruleValue,String propertyValue) {
        return ruleValue==null||ruleValue.isBlank()||(propertyValue!=null&&ruleValue.equalsIgnoreCase(propertyValue));
    }

    private static Integer integer(Object v) {
        if(v==null) return null;
        if(v instanceof Number n) return n.intValue();
        try{return Integer.valueOf(v.toString());}catch(Exception ex){return null;}
    }

    private static BigDecimal decimal(Object v) {
        if(v==null) return null;
        if(v instanceof BigDecimal b) return b;
        if(v instanceof Number n) return new BigDecimal(n.toString());
        try{return new BigDecimal(v.toString());}catch(Exception ex){return null;}
    }

    private static String text(BigDecimal v){return v==null?null:v.stripTrailingZeros().toPlainString();}
    private static void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private static String trim(String s){return s==null?null:s.trim();}
    private static String upper(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT);}
    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
    private record PreparedResult(UrbanRuleEntity rule,UrbanRuleEvaluator.Result result){}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/regulation/src/main/java/com/sakhtyar/regulation/application/RegulationService.java") $service

$controller = @'
package com.sakhtyar.regulation.api;

import static com.sakhtyar.regulation.api.RegulationDtos.*;
import com.sakhtyar.regulation.application.RegulationService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/regulations")
public class RegulationController {
    private final RegulationService service;
    public RegulationController(RegulationService service){this.service=service;}

    @GetMapping("/rules")
    public List<RuleResponse> rules(){return service.rules();}

    @PostMapping("/rules")
    public RuleResponse createRule(@Valid @RequestBody UpsertRuleRequest request,Authentication auth){
        return service.createRule(request,auth);
    }

    @PutMapping("/rules/{id}")
    public RuleResponse updateRule(@PathVariable UUID id,@Valid @RequestBody UpsertRuleRequest request){
        return service.updateRule(id,request);
    }

    @PostMapping("/evaluate")
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluateRequest request,Authentication auth){
        return service.evaluate(request,auth);
    }

    @GetMapping("/properties/{propertyId}/evaluations")
    public List<EvaluationResponse> evaluations(@PathVariable UUID propertyId){
        return service.evaluations(propertyId);
    }

    @GetMapping("/evaluations/{id}")
    public EvaluationResponse evaluation(@PathVariable UUID id){
        return service.evaluation(id);
    }
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/regulation/src/main/java/com/sakhtyar/regulation/api/RegulationController.java") $controller

$test = @'
package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.regulation.domain.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UrbanRuleEvaluatorTest {
    private final UrbanRuleEvaluator evaluator=new UrbanRuleEvaluator();

    @Test
    void maxFloorsPassesAndFailsDeterministically(){
        var pass=evaluator.evaluate(UrbanRuleType.MAX_FLOORS,Map.of("maxFloors",5),
            new UrbanRuleEvaluator.EvaluationInput(null,null,null,4,null,null,null));
        var fail=evaluator.evaluate(UrbanRuleType.MAX_FLOORS,Map.of("maxFloors",5),
            new UrbanRuleEvaluator.EvaluationInput(null,null,null,6,null,null,null));
        assertEquals(EvaluationOutcome.PASS,pass.outcome());
        assertEquals(EvaluationOutcome.FAIL,fail.outcome());
    }

    @Test
    void farUsesBuiltAreaDividedByLandArea(){
        var result=evaluator.evaluate(UrbanRuleType.MAX_FAR,Map.of("maxFar","2.50"),
            new UrbanRuleEvaluator.EvaluationInput(
                new BigDecimal("200"),null,null,null,new BigDecimal("450"),null,null));
        assertEquals(EvaluationOutcome.PASS,result.outcome());
        assertEquals("2.25",result.actualValue());
    }

    @Test
    void missingInputRequiresReviewInsteadOfGuessing(){
        var result=evaluator.evaluate(UrbanRuleType.MIN_FRONTAGE,Map.of("minFrontageM","10"),
            new UrbanRuleEvaluator.EvaluationInput(null,null,null,null,null,null,null));
        assertEquals(EvaluationOutcome.REVIEW,result.outcome());
    }

    @Test
    void coverageIsEvaluatedAsPercentage(){
        var result=evaluator.evaluate(UrbanRuleType.MAX_COVERAGE_PERCENT,Map.of("maxCoveragePercent","60"),
            new UrbanRuleEvaluator.EvaluationInput(
                new BigDecimal("250"),null,null,null,null,new BigDecimal("150"),null));
        assertEquals(EvaluationOutcome.PASS,result.outcome());
        assertEquals("60",result.actualValue());
    }
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/regulation/src/test/java/com/sakhtyar/regulation/application/UrbanRuleEvaluatorTest.java") $test

Write-Step "7/8 - Writing Phase 5.7 documentation"

$doc = @'
# Phase 5.7 — Urban & Regulation Engine

## Purpose
Phase 5.7 introduces a deterministic, source-traceable rule engine for urban and municipal constraints.

Rules are explicit data records. They are not invented by AI and must reference a `knowledge_source`.

## Supported initial rule types
- `MAX_FLOORS`
- `MAX_FAR`
- `MAX_COVERAGE_PERCENT`
- `MIN_PASSAGE_WIDTH`
- `MIN_FRONTAGE`
- `MAX_HEIGHT`

Each rule has:
- immutable code
- Persian/English name
- jurisdiction scope
- optional property type
- source/provenance
- validity dates
- priority
- JSON parameters
- active state

## Jurisdiction matching
A blank rule scope behaves as a wildcard.

Rules can be scoped by:
- province
- city
- district
- property type

## Evaluation inputs
Property facts come from the Property module.

When a construction scenario is supplied, the following optional scenario assumptions are recognized:
- `proposedFloors`
- `totalBuiltAreaM2`
- `footprintAreaM2`
- `proposedHeightM`

For backward compatibility, if `proposedFloors` or `totalBuiltAreaM2` is absent, the engine can use existing property floors/building area.

The engine never guesses missing frontage, passage width, footprint, height, or other required inputs. Missing facts produce `REVIEW`.

## Outcomes
Rule outcomes:
- `PASS`
- `FAIL`
- `REVIEW`

Evaluation status:
- any FAIL -> `NON_COMPLIANT`
- no FAIL but at least one REVIEW -> `REVIEW_REQUIRED`
- all PASS -> `COMPLIANT`

## Lineage
Every evaluation stores:
- property/scenario IDs
- deterministic input snapshot
- rule results
- source IDs through the rule
- algorithm version
- actor/time

This creates a reproducible regulation layer for Phase 5.8 feasibility analysis.

## Important boundary
This engine is a decision-support layer, not an official municipal permit determination.
Only reviewed rule data from authoritative or appropriately trusted sources should be treated as operationally reliable.

## Deferred
- automatic GIS/zoning polygon lookup
- municipality-specific expression language
- parking rules
- setbacks
- density incentives
- heritage/fire/environmental constraints
- automatic permit workflows
- regulation UI
'@
Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.7-urban-regulation-engine.md") $doc

$apiDoc = @'
# Phase 5.7 — Regulation API

Base path: `/api/v1/regulations`

## Rules
- `GET /api/v1/regulations/rules`
- `POST /api/v1/regulations/rules`
- `PUT /api/v1/regulations/rules/{id}`

Example MAX_FLOORS parameters:

```json
{
  "maxFloors": 5
}
```

Example MAX_FAR parameters:

```json
{
  "maxFar": 2.5
}
```

## Evaluation
- `POST /api/v1/regulations/evaluate`

Example:

```json
{
  "propertyId": "00000000-0000-0000-0000-000000000000",
  "scenarioId": null
}
```

## History
- `GET /api/v1/regulations/properties/{propertyId}/evaluations`
- `GET /api/v1/regulations/evaluations/{id}`

Missing required inputs are returned as REVIEW outcomes rather than inferred values.
'@
Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.7-regulation-api.md") $apiDoc

Write-Step "8/8 - Running Maven tests"

if (-not $SkipTests) {
    Push-Location (Join-Path $RepoRoot "backend")
    try {
        & mvn test
        if ($LASTEXITCODE -ne 0) {
            throw "Maven tests failed."
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Tests skipped by -SkipTests." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Current branch:" -ForegroundColor Cyan
& git branch --show-current

Write-Host ""
Write-Host "Changed files:" -ForegroundColor Cyan
& git status --short

Write-Host ""
Write-Host "Phase 5.7 applied on v2 successfully." -ForegroundColor Green
Write-Host "Base SHA used: $baseSha"
Write-Host ""
Write-Host "Next validation:" -ForegroundColor Yellow
Write-Host "1) Run backend from IntelliJ and confirm Flyway V15 applies."
Write-Host "2) Confirm the application stays running."
Write-Host "3) This phase is backend-only; no frontend files should change."
Write-Host "4) After runtime validation, commit and push directly to v2."
