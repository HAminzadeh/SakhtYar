param(
    [string]$Branch = "v2",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 96) -ForegroundColor DarkCyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 96) -ForegroundColor DarkCyan
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

Write-Step "SakhtYar Phase 5.8 - Feasibility Engine (directly on v2)"

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
    try { $selfRel = [System.IO.Path]::GetRelativePath($RepoRoot, $selfPath).Replace('\','/') } catch {}
}

$staged = @(& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw "Could not inspect staged files." }
$staged = @($staged | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

if ($staged.Count -gt 0) {
    if ($selfRel -and $staged.Count -eq 1 -and $staged[0].Replace('\','/') -eq $selfRel) {
        Write-Host "Only this helper script is staged; unstaging it automatically." -ForegroundColor Yellow
        Invoke-Git restore --staged -- $selfRel
    } else {
        throw "Staged changes exist. Commit or stash them before Phase 5.8.`n$($staged -join "`n")"
    }
}

& git diff --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Tracked working-tree changes exist. Commit or stash them before Phase 5.8."
}

Write-Step "1/8 - Updating v2"
Invoke-Git fetch origin $Branch
Invoke-Git pull --ff-only origin $Branch
$baseSha = (& git rev-parse HEAD).Trim()
Write-Host "Working directly on $Branch at $baseSha" -ForegroundColor Green

Write-Step "2/8 - Creating backup"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $RepoRoot ".local\patch-backups\phase-5.8-$stamp"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

@(
    "backend/modules/analysis/pom.xml",
    "backend/app/src/main/resources/db/migration/V16__phase5_feasibility_engine.sql",
    "docs/phase-5/phase-5.8-feasibility-engine.md",
    "docs/phase-5/phase-5.8-feasibility-api.md"
) | ForEach-Object { Backup-IfExists $_ }

Write-Host "Backup: $BackupDir" -ForegroundColor Green

Write-Step "3/8 - Extending existing analysis module for feasibility"

$analysisPom = Join-Path $RepoRoot "backend/modules/analysis/pom.xml"

Replace-Once $analysisPom @'
  <dependencies>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-shared-kernel</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
  </dependencies>
'@ @'
  <dependencies>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-shared-kernel</artifactId>
    </dependency>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-audit</artifactId>
    </dependency>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-casefile</artifactId>
    </dependency>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-property</artifactId>
    </dependency>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-scenario</artifactId>
    </dependency>
    <dependency>
      <groupId>com.sakhtyar</groupId>
      <artifactId>sakhtyar-regulation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
'@ "<artifactId>sakhtyar-regulation</artifactId>"

Write-Step "4/8 - Writing V16 feasibility migration"

$migration = @'
-- SakhtYar Phase 5.8 - Feasibility Engine

CREATE TABLE feasibility_assessment (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE RESTRICT,
    property_id UUID NOT NULL REFERENCES property(id) ON DELETE RESTRICT,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    scenario_cost_snapshot_id UUID NOT NULL REFERENCES scenario_cost_snapshot(id) ON DELETE RESTRICT,
    urban_evaluation_id UUID NOT NULL REFERENCES urban_evaluation(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL,
    readiness_score NUMERIC(5,2) NOT NULL,
    scenario_cost NUMERIC(20,2) NOT NULL,
    cost_currency_code VARCHAR(3) NOT NULL,
    land_area_m2 NUMERIC(20,6),
    total_built_area_m2 NUMERIC(20,6),
    cost_per_land_m2 NUMERIC(20,2),
    cost_per_built_m2 NUMERIC(20,2),
    proposed_floors INTEGER,
    calculated_far NUMERIC(20,6),
    regulation_status VARCHAR(40) NOT NULL,
    blocking_reason_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    assessed_by VARCHAR(150) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_feasibility_readiness_score CHECK (readiness_score >= 0 AND readiness_score <= 100),
    CONSTRAINT chk_feasibility_cost CHECK (scenario_cost >= 0),
    CONSTRAINT chk_feasibility_counts CHECK (blocking_reason_count >= 0 AND warning_count >= 0),
    CONSTRAINT chk_feasibility_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT chk_feasibility_result_object CHECK (jsonb_typeof(result_snapshot) = 'object')
);

CREATE INDEX idx_feasibility_case
    ON feasibility_assessment(case_id, assessed_at DESC);

CREATE INDEX idx_feasibility_scenario
    ON feasibility_assessment(scenario_id, assessed_at DESC);

CREATE TABLE feasibility_reason (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES feasibility_assessment(id) ON DELETE CASCADE,
    reason_type VARCHAR(40) NOT NULL,
    reason_code VARCHAR(120) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    source_entity_type VARCHAR(120),
    source_entity_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_feasibility_reason_details_object CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_feasibility_reason_assessment
    ON feasibility_reason(assessment_id, reason_type);
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/app/src/main/resources/db/migration/V16__phase5_feasibility_engine.sql") $migration

Write-Step "5/8 - Writing feasibility domain"

$files = @{}

$files["backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/domain/FeasibilityStatus.java"] = @'
package com.sakhtyar.analysis.feasibility.domain;

public enum FeasibilityStatus {
    FEASIBLE,
    CONDITIONAL,
    NOT_FEASIBLE,
    INSUFFICIENT_DATA
}
'@

$files["backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/domain/FeasibilityReasonType.java"] = @'
package com.sakhtyar.analysis.feasibility.domain;

public enum FeasibilityReasonType {
    BLOCKER,
    WARNING,
    INFO
}
'@

$files["backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/domain/FeasibilityAssessmentEntity.java"] = @'
package com.sakhtyar.analysis.feasibility.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="feasibility_assessment")
public class FeasibilityAssessmentEntity {
    @Id private UUID id;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="property_id",nullable=false) private UUID propertyId;
    @Column(name="scenario_id",nullable=false) private UUID scenarioId;
    @Column(name="scenario_cost_snapshot_id",nullable=false) private UUID scenarioCostSnapshotId;
    @Column(name="urban_evaluation_id",nullable=false) private UUID urbanEvaluationId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private FeasibilityStatus status;
    @Column(name="readiness_score",nullable=false,precision=5,scale=2) private BigDecimal readinessScore;
    @Column(name="scenario_cost",nullable=false,precision=20,scale=2) private BigDecimal scenarioCost;
    @Column(name="cost_currency_code",nullable=false,length=3) private String costCurrencyCode;
    @Column(name="land_area_m2",precision=20,scale=6) private BigDecimal landAreaM2;
    @Column(name="total_built_area_m2",precision=20,scale=6) private BigDecimal totalBuiltAreaM2;
    @Column(name="cost_per_land_m2",precision=20,scale=2) private BigDecimal costPerLandM2;
    @Column(name="cost_per_built_m2",precision=20,scale=2) private BigDecimal costPerBuiltM2;
    @Column(name="proposed_floors") private Integer proposedFloors;
    @Column(name="calculated_far",precision=20,scale=6) private BigDecimal calculatedFar;
    @Column(name="regulation_status",nullable=false,length=40) private String regulationStatus;
    @Column(name="blocking_reason_count",nullable=false) private int blockingReasonCount;
    @Column(name="warning_count",nullable=false) private int warningCount;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> resultSnapshot;
    @Column(name="assessed_by",nullable=false,length=150) private String assessedBy;
    @Column(name="assessed_at",nullable=false) private Instant assessedAt;

    protected FeasibilityAssessmentEntity(){}

    public FeasibilityAssessmentEntity(UUID id,UUID caseId,UUID propertyId,UUID scenarioId,
            UUID scenarioCostSnapshotId,UUID urbanEvaluationId,FeasibilityStatus status,
            BigDecimal readinessScore,BigDecimal scenarioCost,String costCurrencyCode,
            BigDecimal landAreaM2,BigDecimal totalBuiltAreaM2,BigDecimal costPerLandM2,
            BigDecimal costPerBuiltM2,Integer proposedFloors,BigDecimal calculatedFar,
            String regulationStatus,int blockingReasonCount,int warningCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,Map<String,Object> resultSnapshot,
            String assessedBy,Instant assessedAt) {
        this.id=id; this.caseId=caseId; this.propertyId=propertyId; this.scenarioId=scenarioId;
        this.scenarioCostSnapshotId=scenarioCostSnapshotId; this.urbanEvaluationId=urbanEvaluationId;
        this.status=status; this.readinessScore=readinessScore; this.scenarioCost=scenarioCost;
        this.costCurrencyCode=costCurrencyCode; this.landAreaM2=landAreaM2;
        this.totalBuiltAreaM2=totalBuiltAreaM2; this.costPerLandM2=costPerLandM2;
        this.costPerBuiltM2=costPerBuiltM2; this.proposedFloors=proposedFloors;
        this.calculatedFar=calculatedFar; this.regulationStatus=regulationStatus;
        this.blockingReasonCount=blockingReasonCount; this.warningCount=warningCount;
        this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.resultSnapshot=resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);
        this.assessedBy=assessedBy; this.assessedAt=assessedAt;
    }

    public UUID getId(){return id;} public UUID getCaseId(){return caseId;}
    public UUID getPropertyId(){return propertyId;} public UUID getScenarioId(){return scenarioId;}
    public UUID getScenarioCostSnapshotId(){return scenarioCostSnapshotId;}
    public UUID getUrbanEvaluationId(){return urbanEvaluationId;} public FeasibilityStatus getStatus(){return status;}
    public BigDecimal getReadinessScore(){return readinessScore;} public BigDecimal getScenarioCost(){return scenarioCost;}
    public String getCostCurrencyCode(){return costCurrencyCode;} public BigDecimal getLandAreaM2(){return landAreaM2;}
    public BigDecimal getTotalBuiltAreaM2(){return totalBuiltAreaM2;} public BigDecimal getCostPerLandM2(){return costPerLandM2;}
    public BigDecimal getCostPerBuiltM2(){return costPerBuiltM2;} public Integer getProposedFloors(){return proposedFloors;}
    public BigDecimal getCalculatedFar(){return calculatedFar;} public String getRegulationStatus(){return regulationStatus;}
    public int getBlockingReasonCount(){return blockingReasonCount;} public int getWarningCount(){return warningCount;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public Map<String,Object> getResultSnapshot(){return resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);}
    public String getAssessedBy(){return assessedBy;} public Instant getAssessedAt(){return assessedAt;}
}
'@

$files["backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/domain/FeasibilityReasonEntity.java"] = @'
package com.sakhtyar.analysis.feasibility.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="feasibility_reason")
public class FeasibilityReasonEntity {
    @Id private UUID id;
    @Column(name="assessment_id",nullable=false) private UUID assessmentId;
    @Enumerated(EnumType.STRING) @Column(name="reason_type",nullable=false,length=40) private FeasibilityReasonType reasonType;
    @Column(name="reason_code",nullable=false,length=120) private String reasonCode;
    @Column(nullable=false,length=2000) private String message;
    @Column(name="source_entity_type",length=120) private String sourceEntityType;
    @Column(name="source_entity_id") private UUID sourceEntityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> details;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected FeasibilityReasonEntity(){}

    public FeasibilityReasonEntity(UUID id,UUID assessmentId,FeasibilityReasonType reasonType,
            String reasonCode,String message,String sourceEntityType,UUID sourceEntityId,
            Map<String,Object> details,Instant createdAt) {
        this.id=id; this.assessmentId=assessmentId; this.reasonType=reasonType;
        this.reasonCode=reasonCode; this.message=message; this.sourceEntityType=sourceEntityType;
        this.sourceEntityId=sourceEntityId; this.details=details==null?Map.of():Map.copyOf(details);
        this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getAssessmentId(){return assessmentId;}
    public FeasibilityReasonType getReasonType(){return reasonType;} public String getReasonCode(){return reasonCode;}
    public String getMessage(){return message;} public String getSourceEntityType(){return sourceEntityType;}
    public UUID getSourceEntityId(){return sourceEntityId;} public Map<String,Object> getDetails(){return details==null?Map.of():Map.copyOf(details);}
    public Instant getCreatedAt(){return createdAt;}
}
'@

$files["backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/domain/FeasibilityAssessmentRepository.java"] = @'
package com.sakhtyar.analysis.feasibility.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FeasibilityAssessmentRepository extends JpaRepository<FeasibilityAssessmentEntity,UUID>{
    List<FeasibilityAssessmentEntity> findByCaseIdOrderByAssessedAtDesc(UUID caseId);
    List<FeasibilityAssessmentEntity> findByScenarioIdOrderByAssessedAtDesc(UUID scenarioId);
}
'@

$files["backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/domain/FeasibilityReasonRepository.java"] = @'
package com.sakhtyar.analysis.feasibility.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FeasibilityReasonRepository extends JpaRepository<FeasibilityReasonEntity,UUID>{
    List<FeasibilityReasonEntity> findByAssessmentIdOrderByIdAsc(UUID assessmentId);
}
'@

foreach ($relative in $files.Keys) {
    Write-Utf8NoBom (Join-Path $RepoRoot $relative) $files[$relative]
}

Write-Step "6/8 - Writing feasibility calculator, service, API and tests"

$calculator = @'
package com.sakhtyar.analysis.feasibility.application;

import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.regulation.domain.EvaluationStatus;
import com.sakhtyar.scenario.domain.ScenarioStatus;
import java.math.*;
import java.util.*;

public final class FeasibilityCalculator {

    public record Input(
            EvaluationStatus regulationStatus,
            ScenarioStatus scenarioStatus,
            BigDecimal landAreaM2,
            BigDecimal totalBuiltAreaM2,
            BigDecimal scenarioCost,
            Integer proposedFloors
    ) {}

    public record Result(
            FeasibilityStatus status,
            BigDecimal readinessScore,
            BigDecimal costPerLandM2,
            BigDecimal costPerBuiltM2,
            BigDecimal calculatedFar,
            List<Reason> reasons
    ) {}

    public record Reason(
            FeasibilityReasonType type,
            String code,
            String message
    ) {}

    public Result calculate(Input input) {
        List<Reason> reasons=new ArrayList<>();
        int score=100;

        if(input.regulationStatus()==EvaluationStatus.NON_COMPLIANT) {
            reasons.add(new Reason(FeasibilityReasonType.BLOCKER,"REGULATION_NON_COMPLIANT",
                    "Urban regulation evaluation contains one or more failed rules."));
            score-=60;
        } else if(input.regulationStatus()==EvaluationStatus.REVIEW_REQUIRED) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"REGULATION_REVIEW_REQUIRED",
                    "Urban regulation evaluation contains unresolved review items."));
            score-=25;
        }

        if(input.scenarioStatus()!=ScenarioStatus.READY) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"SCENARIO_NOT_READY",
                    "Construction scenario is not in READY status."));
            score-=15;
        }

        if(input.landAreaM2()==null || input.landAreaM2().signum()<=0) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"LAND_AREA_MISSING",
                    "Valid land area is required for normalized feasibility metrics."));
            score-=15;
        }

        if(input.totalBuiltAreaM2()==null || input.totalBuiltAreaM2().signum()<=0) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"BUILT_AREA_MISSING",
                    "Total built area is missing; FAR and cost-per-built-area cannot be calculated."));
            score-=15;
        }

        if(input.scenarioCost()==null || input.scenarioCost().signum()<0) {
            reasons.add(new Reason(FeasibilityReasonType.BLOCKER,"SCENARIO_COST_INVALID",
                    "Scenario cost snapshot is missing or invalid."));
            score-=50;
        }

        BigDecimal costPerLand=divide(input.scenarioCost(),input.landAreaM2(),2);
        BigDecimal costPerBuilt=divide(input.scenarioCost(),input.totalBuiltAreaM2(),2);
        BigDecimal far=divide(input.totalBuiltAreaM2(),input.landAreaM2(),6);

        score=Math.max(0,Math.min(100,score));

        long blockers=reasons.stream().filter(r->r.type()==FeasibilityReasonType.BLOCKER).count();
        boolean missingCritical=input.landAreaM2()==null || input.totalBuiltAreaM2()==null || input.scenarioCost()==null;

        FeasibilityStatus status;
        if(blockers>0) status=FeasibilityStatus.NOT_FEASIBLE;
        else if(missingCritical) status=FeasibilityStatus.INSUFFICIENT_DATA;
        else if(reasons.stream().anyMatch(r->r.type()==FeasibilityReasonType.WARNING)) status=FeasibilityStatus.CONDITIONAL;
        else status=FeasibilityStatus.FEASIBLE;

        return new Result(status,new BigDecimal(score).setScale(2),costPerLand,costPerBuilt,far,List.copyOf(reasons));
    }

    private BigDecimal divide(BigDecimal numerator,BigDecimal denominator,int scale) {
        if(numerator==null || denominator==null || denominator.signum()<=0) return null;
        return numerator.divide(denominator,scale,RoundingMode.HALF_UP);
    }
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/application/FeasibilityCalculator.java") $calculator

$dtos = @'
package com.sakhtyar.analysis.feasibility.api;

import com.sakhtyar.analysis.feasibility.domain.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class FeasibilityDtos {
    private FeasibilityDtos(){}

    public record CreateAssessmentRequest(
            @NotNull UUID caseId,
            @NotNull UUID scenarioId,
            @NotNull UUID scenarioCostSnapshotId,
            @NotNull UUID urbanEvaluationId
    ) {}

    public record ReasonResponse(
            UUID id,FeasibilityReasonType reasonType,String reasonCode,String message,
            String sourceEntityType,UUID sourceEntityId,Map<String,Object> details,Instant createdAt
    ) {
        public static ReasonResponse from(FeasibilityReasonEntity e) {
            return new ReasonResponse(e.getId(),e.getReasonType(),e.getReasonCode(),e.getMessage(),
                    e.getSourceEntityType(),e.getSourceEntityId(),e.getDetails(),e.getCreatedAt());
        }
    }

    public record AssessmentResponse(
            UUID id,UUID caseId,UUID propertyId,UUID scenarioId,UUID scenarioCostSnapshotId,
            UUID urbanEvaluationId,FeasibilityStatus status,BigDecimal readinessScore,
            BigDecimal scenarioCost,String costCurrencyCode,BigDecimal landAreaM2,
            BigDecimal totalBuiltAreaM2,BigDecimal costPerLandM2,BigDecimal costPerBuiltM2,
            Integer proposedFloors,BigDecimal calculatedFar,String regulationStatus,
            int blockingReasonCount,int warningCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,Map<String,Object> resultSnapshot,
            String assessedBy,Instant assessedAt,List<ReasonResponse> reasons
    ) {}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/api/FeasibilityDtos.java") $dtos

$service = @'
package com.sakhtyar.analysis.feasibility.application;

import static com.sakhtyar.analysis.feasibility.api.FeasibilityDtos.*;

import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.property.domain.*;
import com.sakhtyar.regulation.domain.*;
import com.sakhtyar.scenario.domain.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeasibilityService {
    private static final String ALGORITHM_VERSION="1.0";

    private final FeasibilityAssessmentRepository assessmentRepo;
    private final FeasibilityReasonRepository reasonRepo;
    private final CaseRepository caseRepo;
    private final PropertyRepository propertyRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final ScenarioCostSnapshotRepository costSnapshotRepo;
    private final UrbanEvaluationRepository urbanEvaluationRepo;
    private final AuditService auditService;
    private final FeasibilityCalculator calculator=new FeasibilityCalculator();

    public FeasibilityService(FeasibilityAssessmentRepository assessmentRepo,FeasibilityReasonRepository reasonRepo,
            CaseRepository caseRepo,PropertyRepository propertyRepo,ConstructionScenarioRepository scenarioRepo,
            ScenarioCostSnapshotRepository costSnapshotRepo,UrbanEvaluationRepository urbanEvaluationRepo,
            AuditService auditService) {
        this.assessmentRepo=assessmentRepo; this.reasonRepo=reasonRepo; this.caseRepo=caseRepo;
        this.propertyRepo=propertyRepo; this.scenarioRepo=scenarioRepo; this.costSnapshotRepo=costSnapshotRepo;
        this.urbanEvaluationRepo=urbanEvaluationRepo; this.auditService=auditService;
    }

    @Transactional
    public AssessmentResponse create(CreateAssessmentRequest r,Authentication auth) {
        if(!caseRepo.existsById(r.caseId())) throw new IllegalArgumentException("Construction case not found.");

        PropertyEntity property=propertyRepo.findByCaseId(r.caseId())
                .orElseThrow(()->new IllegalArgumentException("Property for case not found."));

        ConstructionScenarioEntity scenario=scenarioRepo.findById(r.scenarioId())
                .orElseThrow(()->new IllegalArgumentException("Scenario not found."));

        ScenarioCostSnapshotEntity costSnapshot=costSnapshotRepo.findById(r.scenarioCostSnapshotId())
                .orElseThrow(()->new IllegalArgumentException("Scenario cost snapshot not found."));

        UrbanEvaluationEntity urban=urbanEvaluationRepo.findById(r.urbanEvaluationId())
                .orElseThrow(()->new IllegalArgumentException("Urban evaluation not found."));

        if(!costSnapshot.getScenarioId().equals(scenario.getId()))
            throw new IllegalArgumentException("Cost snapshot does not belong to selected scenario.");

        if(!urban.getPropertyId().equals(property.getId()))
            throw new IllegalArgumentException("Urban evaluation does not belong to case property.");

        if(urban.getScenarioId()!=null && !urban.getScenarioId().equals(scenario.getId()))
            throw new IllegalArgumentException("Urban evaluation belongs to a different scenario.");

        Map<String,Object> assumptions=scenario.getAssumptions();
        Integer proposedFloors=integer(assumptions.get("proposedFloors"));
        BigDecimal totalBuiltArea=decimal(assumptions.get("totalBuiltAreaM2"));

        if(proposedFloors==null) proposedFloors=property.getExistingFloors();
        if(totalBuiltArea==null) totalBuiltArea=property.getBuildingAreaM2();

        var calc=calculator.calculate(new FeasibilityCalculator.Input(
                urban.getStatus(),scenario.getStatus(),property.getLandAreaM2(),
                totalBuiltArea,costSnapshot.getTotalCost(),proposedFloors));

        int blockers=(int)calc.reasons().stream().filter(x->x.type()==FeasibilityReasonType.BLOCKER).count();
        int warnings=(int)calc.reasons().stream().filter(x->x.type()==FeasibilityReasonType.WARNING).count();

        Map<String,Object> inputSnapshot=new LinkedHashMap<>();
        inputSnapshot.put("caseId",r.caseId().toString());
        inputSnapshot.put("propertyId",property.getId().toString());
        inputSnapshot.put("scenarioId",scenario.getId().toString());
        inputSnapshot.put("scenarioCostSnapshotId",costSnapshot.getId().toString());
        inputSnapshot.put("urbanEvaluationId",urban.getId().toString());
        put(inputSnapshot,"landAreaM2",text(property.getLandAreaM2()));
        put(inputSnapshot,"totalBuiltAreaM2",text(totalBuiltArea));
        put(inputSnapshot,"proposedFloors",proposedFloors);
        inputSnapshot.put("scenarioStatus",scenario.getStatus().name());
        inputSnapshot.put("regulationStatus",urban.getStatus().name());
        inputSnapshot.put("scenarioCost",costSnapshot.getTotalCost().toPlainString());
        inputSnapshot.put("currencyCode",costSnapshot.getCurrencyCode());

        Map<String,Object> resultSnapshot=new LinkedHashMap<>();
        resultSnapshot.put("status",calc.status().name());
        resultSnapshot.put("readinessScore",calc.readinessScore().toPlainString());
        put(resultSnapshot,"costPerLandM2",text(calc.costPerLandM2()));
        put(resultSnapshot,"costPerBuiltM2",text(calc.costPerBuiltM2()));
        put(resultSnapshot,"calculatedFar",text(calc.calculatedFar()));
        resultSnapshot.put("blockingReasonCount",blockers);
        resultSnapshot.put("warningCount",warnings);

        Instant now=Instant.now();
        var assessment=new FeasibilityAssessmentEntity(
                UUID.randomUUID(),r.caseId(),property.getId(),scenario.getId(),costSnapshot.getId(),
                urban.getId(),calc.status(),calc.readinessScore(),costSnapshot.getTotalCost(),
                costSnapshot.getCurrencyCode(),property.getLandAreaM2(),totalBuiltArea,
                calc.costPerLandM2(),calc.costPerBuiltM2(),proposedFloors,calc.calculatedFar(),
                urban.getStatus().name(),blockers,warnings,ALGORITHM_VERSION,inputSnapshot,resultSnapshot,
                actor(auth),now
        );
        assessmentRepo.save(assessment);

        List<FeasibilityReasonEntity> reasons=new ArrayList<>();
        for(var reason:calc.reasons()) {
            String sourceType = reason.code().startsWith("REGULATION") ? "URBAN_EVALUATION" : "FEASIBILITY_INPUT";
            UUID sourceId = reason.code().startsWith("REGULATION") ? urban.getId() : null;
            Map<String,Object> details=Map.of("scenarioId",scenario.getId().toString());
            reasons.add(reasonRepo.save(new FeasibilityReasonEntity(
                    UUID.randomUUID(),assessment.getId(),reason.type(),reason.code(),reason.message(),
                    sourceType,sourceId,details,now
            )));
        }

        auditService.record("FEASIBILITY_ASSESSMENT",assessment.getId(),"FEASIBILITY_ASSESSED",
                Map.of("caseId",r.caseId().toString(),"scenarioId",scenario.getId().toString(),
                        "status",calc.status().name(),"readinessScore",calc.readinessScore().toPlainString()));

        return toResponse(assessment,reasons);
    }

    @Transactional(readOnly=true)
    public List<AssessmentResponse> byCase(UUID caseId) {
        return assessmentRepo.findByCaseIdOrderByAssessedAtDesc(caseId).stream()
                .map(e->toResponse(e,reasonRepo.findByAssessmentIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public List<AssessmentResponse> byScenario(UUID scenarioId) {
        return assessmentRepo.findByScenarioIdOrderByAssessedAtDesc(scenarioId).stream()
                .map(e->toResponse(e,reasonRepo.findByAssessmentIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public AssessmentResponse get(UUID id) {
        var e=assessmentRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Feasibility assessment not found."));
        return toResponse(e,reasonRepo.findByAssessmentIdOrderByIdAsc(id));
    }

    private AssessmentResponse toResponse(FeasibilityAssessmentEntity e,List<FeasibilityReasonEntity> reasons) {
        return new AssessmentResponse(e.getId(),e.getCaseId(),e.getPropertyId(),e.getScenarioId(),
                e.getScenarioCostSnapshotId(),e.getUrbanEvaluationId(),e.getStatus(),e.getReadinessScore(),
                e.getScenarioCost(),e.getCostCurrencyCode(),e.getLandAreaM2(),e.getTotalBuiltAreaM2(),
                e.getCostPerLandM2(),e.getCostPerBuiltM2(),e.getProposedFloors(),e.getCalculatedFar(),
                e.getRegulationStatus(),e.getBlockingReasonCount(),e.getWarningCount(),e.getAlgorithmVersion(),
                e.getInputSnapshot(),e.getResultSnapshot(),e.getAssessedBy(),e.getAssessedAt(),
                reasons.stream().map(ReasonResponse::from).toList());
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
    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/application/FeasibilityService.java") $service

$controller = @'
package com.sakhtyar.analysis.feasibility.api;

import static com.sakhtyar.analysis.feasibility.api.FeasibilityDtos.*;
import com.sakhtyar.analysis.feasibility.application.FeasibilityService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feasibility")
public class FeasibilityController {
    private final FeasibilityService service;
    public FeasibilityController(FeasibilityService service){this.service=service;}

    @PostMapping("/assessments")
    public AssessmentResponse create(@Valid @RequestBody CreateAssessmentRequest request,Authentication auth) {
        return service.create(request,auth);
    }

    @GetMapping("/assessments/{id}")
    public AssessmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/cases/{caseId}/assessments")
    public List<AssessmentResponse> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }

    @GetMapping("/scenarios/{scenarioId}/assessments")
    public List<AssessmentResponse> byScenario(@PathVariable UUID scenarioId) {
        return service.byScenario(scenarioId);
    }
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/api/FeasibilityController.java") $controller

$test = @'
package com.sakhtyar.analysis.feasibility.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.regulation.domain.EvaluationStatus;
import com.sakhtyar.scenario.domain.ScenarioStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeasibilityCalculatorTest {
    private final FeasibilityCalculator calculator=new FeasibilityCalculator();

    @Test
    void compliantReadyScenarioIsFeasible(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.COMPLIANT,ScenarioStatus.READY,
                new BigDecimal("200"),new BigDecimal("600"),new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.FEASIBLE,result.status());
        assertEquals(new BigDecimal("100.00"),result.readinessScore());
        assertEquals(new BigDecimal("3.000000"),result.calculatedFar());
    }

    @Test
    void regulationFailureIsBlocking(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.NON_COMPLIANT,ScenarioStatus.READY,
                new BigDecimal("200"),new BigDecimal("600"),new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.NOT_FEASIBLE,result.status());
        assertTrue(result.reasons().stream().anyMatch(r->r.type()==FeasibilityReasonType.BLOCKER));
    }

    @Test
    void regulationReviewProducesConditionalOutcome(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.REVIEW_REQUIRED,ScenarioStatus.READY,
                new BigDecimal("200"),new BigDecimal("600"),new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.CONDITIONAL,result.status());
    }

    @Test
    void missingBuiltAreaProducesInsufficientData(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.COMPLIANT,ScenarioStatus.READY,
                new BigDecimal("200"),null,new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.INSUFFICIENT_DATA,result.status());
        assertNull(result.costPerBuiltM2());
    }
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/analysis/src/test/java/com/sakhtyar/analysis/feasibility/application/FeasibilityCalculatorTest.java") $test

Write-Step "7/8 - Writing Phase 5.8 documentation"

$doc = @'
# Phase 5.8 — Feasibility Engine

## Purpose
Phase 5.8 composes reviewed outputs from Property, Construction Scenario, Scenario Cost and Urban Regulation into one deterministic feasibility assessment.

This phase deliberately does not calculate revenue, profit, owner/builder shares or investment returns. Those belong to Phase 5.9 Financial & Participation Engine.

## Exact input references
A feasibility request must select:
- construction case
- construction scenario
- exact scenario cost snapshot
- exact urban regulation evaluation

The engine validates that all references belong to the same property/scenario context.

## Status
- `FEASIBLE`
- `CONDITIONAL`
- `NOT_FEASIBLE`
- `INSUFFICIENT_DATA`

Initial deterministic logic:
- regulation `NON_COMPLIANT` creates a blocking reason
- regulation `REVIEW_REQUIRED` creates a warning
- scenario not READY creates a warning
- missing land/built area reduces readiness
- invalid scenario cost is blocking

## Derived metrics
Where input data exists:
- cost per land square metre
- cost per built square metre
- FAR = total built area / land area
- proposed floor count
- readiness score from 0 to 100

The readiness score is an operational completeness/readiness indicator. It is not a financial return score and does not replace human engineering judgment.

## Input sources
Property:
- land area
- existing floors
- existing building area

Scenario assumptions may override/fill:
- `proposedFloors`
- `totalBuiltAreaM2`

Cost comes only from the selected Phase 5.6 `scenario_cost_snapshot`.

Regulatory status comes only from the selected Phase 5.7 `urban_evaluation`.

## Persistence
`feasibility_assessment` stores:
- exact source IDs
- status
- readiness score
- deterministic derived metrics
- input snapshot
- result snapshot
- engine version
- actor/time

`feasibility_reason` stores blockers, warnings and informational reasons with optional source-entity references.

## Lineage
case -> property
case/scenario -> feasibility assessment
scenario -> scenario cost snapshot
property/scenario -> urban evaluation

This phase prepares Phase 5.9 financial analysis and Phase 5.11 full analysis lineage/snapshot hardening.

## Deferred
- market sale value/revenue
- profit and ROI
- owner/builder participation shares
- financing/cash-flow schedule
- NPV/IRR
- sensitivity analysis
- AI recommendations
- frontend feasibility UI
'@
Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.8-feasibility-engine.md") $doc

$apiDoc = @'
# Phase 5.8 — Feasibility API

Base path: `/api/v1/feasibility`

## Create assessment
`POST /api/v1/feasibility/assessments`

Example:
```json
{
  "caseId": "00000000-0000-0000-0000-000000000000",
  "scenarioId": "00000000-0000-0000-0000-000000000000",
  "scenarioCostSnapshotId": "00000000-0000-0000-0000-000000000000",
  "urbanEvaluationId": "00000000-0000-0000-0000-000000000000"
}
```

## Read
- `GET /api/v1/feasibility/assessments/{id}`
- `GET /api/v1/feasibility/cases/{caseId}/assessments`
- `GET /api/v1/feasibility/scenarios/{scenarioId}/assessments`

The API intentionally requires exact cost/regulation snapshot IDs so an assessment remains reproducible.
'@
Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.8-feasibility-api.md") $apiDoc

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
Write-Host "Phase 5.8 applied on v2 successfully." -ForegroundColor Green
Write-Host "Base SHA used: $baseSha"
Write-Host ""
Write-Host "Next validation:" -ForegroundColor Yellow
Write-Host "1) Run backend from IntelliJ and confirm Flyway V16 applies."
Write-Host "2) Confirm the application stays running."
Write-Host "3) This phase is backend-only; no frontend files should change."
Write-Host "4) After runtime validation, commit and push directly to v2."
