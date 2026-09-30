param(
    [string]$Branch = "v2",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 100) -ForegroundColor DarkCyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 100) -ForegroundColor DarkCyan
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
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
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

Write-Step "SakhtYar Phase 5.10 - Sensitivity Analysis Engine (directly on v2)"

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
$selfName = Split-Path -Leaf $selfPath
$selfRel = $null
if ($selfPath) {
    try { $selfRel = [System.IO.Path]::GetRelativePath($RepoRoot, $selfPath).Replace('\','/') } catch {}
}

$staged = @(& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw "Could not inspect staged files." }
$staged = @($staged | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

if ($staged.Count -gt 0) {
    $onlySelf = $staged.Count -eq 1 -and (
        ($selfRel -and $staged[0].Replace('\','/') -eq $selfRel) -or
        ([System.IO.Path]::GetFileName($staged[0]) -eq $selfName)
    )
    if ($onlySelf) {
        Write-Host "Only this helper script is staged; unstaging it automatically." -ForegroundColor Yellow
        Invoke-Git restore --staged -- $staged[0]
    } else {
        throw "Staged changes exist. Commit or stash them before Phase 5.10.`n$($staged -join "`n")"
    }
}

& git diff --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Tracked working-tree changes exist. Commit or stash them before Phase 5.10."
}

Write-Step "1/8 - Updating v2"
Invoke-Git fetch origin $Branch
Invoke-Git pull --ff-only origin $Branch
$baseSha = (& git rev-parse HEAD).Trim()
Write-Host "Working directly on $Branch at $baseSha" -ForegroundColor Green

Write-Step "2/8 - Creating backup"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $RepoRoot ".local\patch-backups\phase-5.10-$stamp"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

@(
    "backend/pom.xml",
    "backend/app/pom.xml",
    "backend/app/src/main/resources/db/migration/V18__phase5_sensitivity_analysis.sql",
    "backend/modules/sensitivity/pom.xml",
    "docs/phase-5/phase-5.10-sensitivity-analysis.md",
    "docs/phase-5/phase-5.10-sensitivity-api.md"
) | ForEach-Object { Backup-IfExists $_ }

Write-Host "Backup: $BackupDir" -ForegroundColor Green

Write-Step "3/8 - Registering sensitivity module"

$parentPom = Join-Path $RepoRoot "backend/pom.xml"
$appPom = Join-Path $RepoRoot "backend/app/pom.xml"

Replace-Once $parentPom @'
    <module>modules/regulation</module>
    <module>modules/finance</module>
    <module>modules/analysis</module>
    <module>app</module>
'@ @'
    <module>modules/regulation</module>
    <module>modules/finance</module>
    <module>modules/sensitivity</module>
    <module>modules/analysis</module>
    <module>app</module>
'@ "<module>modules/sensitivity</module>"

Replace-Once $parentPom @'
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-finance</artifactId>
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
        <artifactId>sakhtyar-finance</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-sensitivity</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-analysis</artifactId>
        <version>${project.version}</version>
      </dependency>
'@ "<artifactId>sakhtyar-sensitivity</artifactId>"

Replace-Once $appPom @'
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-finance</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-analysis</artifactId></dependency>
'@ @'
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-finance</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-sensitivity</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-analysis</artifactId></dependency>
'@ "<artifactId>sakhtyar-sensitivity</artifactId>"

Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/sensitivity/pom.xml") @'
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

  <artifactId>sakhtyar-sensitivity</artifactId>

  <dependencies>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-shared-kernel</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-audit</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-finance</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
'@

Write-Step "4/8 - Writing V18 sensitivity migration"

Write-Utf8NoBom (Join-Path $RepoRoot "backend/app/src/main/resources/db/migration/V18__phase5_sensitivity_analysis.sql") @'
-- SakhtYar Phase 5.10 - Sensitivity Analysis

CREATE TABLE sensitivity_analysis (
    id UUID PRIMARY KEY,
    financial_analysis_id UUID NOT NULL REFERENCES financial_analysis(id) ON DELETE RESTRICT,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE RESTRICT,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    currency_code VARCHAR(3) NOT NULL,
    point_count INTEGER NOT NULL,
    best_profit NUMERIC(20,2),
    worst_profit NUMERIC(20,2),
    best_roi_percent NUMERIC(20,6),
    worst_roi_percent NUMERIC(20,6),
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculated_by VARCHAR(150) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sensitivity_point_count CHECK (point_count > 0),
    CONSTRAINT chk_sensitivity_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT chk_sensitivity_summary_object CHECK (jsonb_typeof(summary_snapshot) = 'object')
);

CREATE INDEX idx_sensitivity_analysis_financial
    ON sensitivity_analysis(financial_analysis_id, calculated_at DESC);

CREATE INDEX idx_sensitivity_analysis_case
    ON sensitivity_analysis(case_id, calculated_at DESC);

CREATE TABLE sensitivity_point (
    id UUID PRIMARY KEY,
    sensitivity_analysis_id UUID NOT NULL REFERENCES sensitivity_analysis(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    sale_price_change_percent NUMERIC(12,6) NOT NULL,
    sellable_area_change_percent NUMERIC(12,6) NOT NULL,
    construction_cost_change_percent NUMERIC(12,6) NOT NULL,
    adjusted_sellable_area_m2 NUMERIC(20,6) NOT NULL,
    adjusted_sale_price_per_m2 NUMERIC(20,2) NOT NULL,
    adjusted_base_construction_cost NUMERIC(20,2) NOT NULL,
    gross_revenue NUMERIC(20,2) NOT NULL,
    total_project_cost NUMERIC(20,2) NOT NULL,
    projected_profit NUMERIC(20,2) NOT NULL,
    roi_percent NUMERIC(20,6),
    profit_margin_percent NUMERIC(20,6),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sensitivity_sequence UNIQUE(sensitivity_analysis_id, sequence_no),
    CONSTRAINT chk_sensitivity_adjusted_area CHECK (adjusted_sellable_area_m2 > 0),
    CONSTRAINT chk_sensitivity_adjusted_values CHECK (
        adjusted_sale_price_per_m2 >= 0 AND adjusted_base_construction_cost >= 0
        AND gross_revenue >= 0 AND total_project_cost >= 0
    )
);

CREATE INDEX idx_sensitivity_point_analysis
    ON sensitivity_point(sensitivity_analysis_id, sequence_no);
'@

Write-Step "5/8 - Writing sensitivity domain"

$domain = @{}

$domain["backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/domain/SensitivityAnalysisEntity.java"] = @'
package com.sakhtyar.sensitivity.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="sensitivity_analysis")
public class SensitivityAnalysisEntity {
    @Id private UUID id;
    @Column(name="financial_analysis_id",nullable=false) private UUID financialAnalysisId;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="scenario_id",nullable=false) private UUID scenarioId;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="point_count",nullable=false) private int pointCount;
    @Column(name="best_profit",precision=20,scale=2) private BigDecimal bestProfit;
    @Column(name="worst_profit",precision=20,scale=2) private BigDecimal worstProfit;
    @Column(name="best_roi_percent",precision=20,scale=6) private BigDecimal bestRoiPercent;
    @Column(name="worst_roi_percent",precision=20,scale=6) private BigDecimal worstRoiPercent;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="summary_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> summarySnapshot;
    @Column(name="calculated_by",nullable=false,length=150) private String calculatedBy;
    @Column(name="calculated_at",nullable=false) private Instant calculatedAt;

    protected SensitivityAnalysisEntity(){}

    public SensitivityAnalysisEntity(UUID id,UUID financialAnalysisId,UUID caseId,UUID scenarioId,
            String currencyCode,int pointCount,BigDecimal bestProfit,BigDecimal worstProfit,
            BigDecimal bestRoiPercent,BigDecimal worstRoiPercent,String algorithmVersion,
            Map<String,Object> inputSnapshot,Map<String,Object> summarySnapshot,
            String calculatedBy,Instant calculatedAt) {
        this.id=id; this.financialAnalysisId=financialAnalysisId; this.caseId=caseId;
        this.scenarioId=scenarioId; this.currencyCode=currencyCode; this.pointCount=pointCount;
        this.bestProfit=bestProfit; this.worstProfit=worstProfit; this.bestRoiPercent=bestRoiPercent;
        this.worstRoiPercent=worstRoiPercent; this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.summarySnapshot=summarySnapshot==null?Map.of():Map.copyOf(summarySnapshot);
        this.calculatedBy=calculatedBy; this.calculatedAt=calculatedAt;
    }

    public UUID getId(){return id;} public UUID getFinancialAnalysisId(){return financialAnalysisId;}
    public UUID getCaseId(){return caseId;} public UUID getScenarioId(){return scenarioId;}
    public String getCurrencyCode(){return currencyCode;} public int getPointCount(){return pointCount;}
    public BigDecimal getBestProfit(){return bestProfit;} public BigDecimal getWorstProfit(){return worstProfit;}
    public BigDecimal getBestRoiPercent(){return bestRoiPercent;} public BigDecimal getWorstRoiPercent(){return worstRoiPercent;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public Map<String,Object> getSummarySnapshot(){return summarySnapshot==null?Map.of():Map.copyOf(summarySnapshot);}
    public String getCalculatedBy(){return calculatedBy;} public Instant getCalculatedAt(){return calculatedAt;}
}
'@

$domain["backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/domain/SensitivityPointEntity.java"] = @'
package com.sakhtyar.sensitivity.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="sensitivity_point")
public class SensitivityPointEntity {
    @Id private UUID id;
    @Column(name="sensitivity_analysis_id",nullable=false) private UUID sensitivityAnalysisId;
    @Column(name="sequence_no",nullable=false) private int sequenceNo;
    @Column(name="sale_price_change_percent",nullable=false,precision=12,scale=6) private BigDecimal salePriceChangePercent;
    @Column(name="sellable_area_change_percent",nullable=false,precision=12,scale=6) private BigDecimal sellableAreaChangePercent;
    @Column(name="construction_cost_change_percent",nullable=false,precision=12,scale=6) private BigDecimal constructionCostChangePercent;
    @Column(name="adjusted_sellable_area_m2",nullable=false,precision=20,scale=6) private BigDecimal adjustedSellableAreaM2;
    @Column(name="adjusted_sale_price_per_m2",nullable=false,precision=20,scale=2) private BigDecimal adjustedSalePricePerM2;
    @Column(name="adjusted_base_construction_cost",nullable=false,precision=20,scale=2) private BigDecimal adjustedBaseConstructionCost;
    @Column(name="gross_revenue",nullable=false,precision=20,scale=2) private BigDecimal grossRevenue;
    @Column(name="total_project_cost",nullable=false,precision=20,scale=2) private BigDecimal totalProjectCost;
    @Column(name="projected_profit",nullable=false,precision=20,scale=2) private BigDecimal projectedProfit;
    @Column(name="roi_percent",precision=20,scale=6) private BigDecimal roiPercent;
    @Column(name="profit_margin_percent",precision=20,scale=6) private BigDecimal profitMarginPercent;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected SensitivityPointEntity(){}

    public SensitivityPointEntity(UUID id,UUID sensitivityAnalysisId,int sequenceNo,
            BigDecimal salePriceChangePercent,BigDecimal sellableAreaChangePercent,
            BigDecimal constructionCostChangePercent,BigDecimal adjustedSellableAreaM2,
            BigDecimal adjustedSalePricePerM2,BigDecimal adjustedBaseConstructionCost,
            BigDecimal grossRevenue,BigDecimal totalProjectCost,BigDecimal projectedProfit,
            BigDecimal roiPercent,BigDecimal profitMarginPercent,Instant createdAt) {
        this.id=id; this.sensitivityAnalysisId=sensitivityAnalysisId; this.sequenceNo=sequenceNo;
        this.salePriceChangePercent=salePriceChangePercent; this.sellableAreaChangePercent=sellableAreaChangePercent;
        this.constructionCostChangePercent=constructionCostChangePercent; this.adjustedSellableAreaM2=adjustedSellableAreaM2;
        this.adjustedSalePricePerM2=adjustedSalePricePerM2; this.adjustedBaseConstructionCost=adjustedBaseConstructionCost;
        this.grossRevenue=grossRevenue; this.totalProjectCost=totalProjectCost; this.projectedProfit=projectedProfit;
        this.roiPercent=roiPercent; this.profitMarginPercent=profitMarginPercent; this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getSensitivityAnalysisId(){return sensitivityAnalysisId;}
    public int getSequenceNo(){return sequenceNo;} public BigDecimal getSalePriceChangePercent(){return salePriceChangePercent;}
    public BigDecimal getSellableAreaChangePercent(){return sellableAreaChangePercent;}
    public BigDecimal getConstructionCostChangePercent(){return constructionCostChangePercent;}
    public BigDecimal getAdjustedSellableAreaM2(){return adjustedSellableAreaM2;}
    public BigDecimal getAdjustedSalePricePerM2(){return adjustedSalePricePerM2;}
    public BigDecimal getAdjustedBaseConstructionCost(){return adjustedBaseConstructionCost;}
    public BigDecimal getGrossRevenue(){return grossRevenue;} public BigDecimal getTotalProjectCost(){return totalProjectCost;}
    public BigDecimal getProjectedProfit(){return projectedProfit;} public BigDecimal getRoiPercent(){return roiPercent;}
    public BigDecimal getProfitMarginPercent(){return profitMarginPercent;} public Instant getCreatedAt(){return createdAt;}
}
'@

$domain["backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/domain/SensitivityAnalysisRepository.java"] = @'
package com.sakhtyar.sensitivity.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface SensitivityAnalysisRepository extends JpaRepository<SensitivityAnalysisEntity,UUID>{
    List<SensitivityAnalysisEntity> findByFinancialAnalysisIdOrderByCalculatedAtDesc(UUID financialAnalysisId);
    List<SensitivityAnalysisEntity> findByCaseIdOrderByCalculatedAtDesc(UUID caseId);
}
'@

$domain["backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/domain/SensitivityPointRepository.java"] = @'
package com.sakhtyar.sensitivity.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface SensitivityPointRepository extends JpaRepository<SensitivityPointEntity,UUID>{
    List<SensitivityPointEntity> findBySensitivityAnalysisIdOrderBySequenceNoAsc(UUID sensitivityAnalysisId);
}
'@

foreach ($relative in $domain.Keys) {
    Write-Utf8NoBom (Join-Path $RepoRoot $relative) $domain[$relative]
}

Write-Step "6/8 - Writing sensitivity calculator, service, API and tests"

Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/application/SensitivityCalculator.java") @'
package com.sakhtyar.sensitivity.application;

import java.math.*;
import java.util.*;

public final class SensitivityCalculator {
    private static final BigDecimal ONE_HUNDRED=new BigDecimal("100");

    public record Baseline(
            BigDecimal sellableAreaM2,
            BigDecimal salePricePerM2,
            BigDecimal otherRevenue,
            BigDecimal baseConstructionCost,
            BigDecimal additionalCost,
            BigDecimal financingCost,
            BigDecimal taxesAndFees
    ) {}

    public record Shock(
            BigDecimal salePriceChangePercent,
            BigDecimal sellableAreaChangePercent,
            BigDecimal constructionCostChangePercent
    ) {}

    public record Point(
            BigDecimal adjustedSellableAreaM2,
            BigDecimal adjustedSalePricePerM2,
            BigDecimal adjustedBaseConstructionCost,
            BigDecimal grossRevenue,
            BigDecimal totalProjectCost,
            BigDecimal projectedProfit,
            BigDecimal roiPercent,
            BigDecimal profitMarginPercent
    ) {}

    public Point calculate(Baseline b,Shock s) {
        requireChange(s.salePriceChangePercent(),"salePriceChangePercent");
        requireChange(s.sellableAreaChangePercent(),"sellableAreaChangePercent");
        requireChange(s.constructionCostChangePercent(),"constructionCostChangePercent");

        BigDecimal area=applyChange(b.sellableAreaM2(),s.sellableAreaChangePercent(),6);
        BigDecimal price=applyChange(b.salePricePerM2(),s.salePriceChangePercent(),2);
        BigDecimal baseCost=applyChange(b.baseConstructionCost(),s.constructionCostChangePercent(),2);

        if(area.signum()<=0) throw new IllegalArgumentException("Adjusted sellable area must remain positive.");
        if(price.signum()<0 || baseCost.signum()<0) throw new IllegalArgumentException("Adjusted financial values cannot be negative.");

        BigDecimal revenue=area.multiply(price).add(b.otherRevenue()).setScale(2,RoundingMode.HALF_UP);
        BigDecimal totalCost=baseCost.add(b.additionalCost()).add(b.financingCost()).add(b.taxesAndFees())
                .setScale(2,RoundingMode.HALF_UP);
        BigDecimal profit=revenue.subtract(totalCost).setScale(2,RoundingMode.HALF_UP);
        BigDecimal roi=totalCost.signum()==0?null:profit.multiply(ONE_HUNDRED).divide(totalCost,6,RoundingMode.HALF_UP);
        BigDecimal margin=revenue.signum()==0?null:profit.multiply(ONE_HUNDRED).divide(revenue,6,RoundingMode.HALF_UP);

        return new Point(area,price,baseCost,revenue,totalCost,profit,roi,margin);
    }

    private BigDecimal applyChange(BigDecimal base,BigDecimal percent,int scale) {
        return base.multiply(BigDecimal.ONE.add(percent.divide(ONE_HUNDRED,10,RoundingMode.HALF_UP)))
                .setScale(scale,RoundingMode.HALF_UP);
    }

    private void requireChange(BigDecimal value,String name) {
        if(value==null) throw new IllegalArgumentException(name+" is required.");
        if(value.compareTo(new BigDecimal("-99.999999"))<0 || value.compareTo(new BigDecimal("1000"))>0)
            throw new IllegalArgumentException(name+" must be between -99.999999 and 1000.");
    }
}
'@

Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/api/SensitivityDtos.java") @'
package com.sakhtyar.sensitivity.api;

import com.sakhtyar.sensitivity.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class SensitivityDtos {
    private SensitivityDtos(){}

    public record ShockRequest(
            @NotNull @DecimalMin("-99.999999") @DecimalMax("1000") BigDecimal salePriceChangePercent,
            @NotNull @DecimalMin("-99.999999") @DecimalMax("1000") BigDecimal sellableAreaChangePercent,
            @NotNull @DecimalMin("-99.999999") @DecimalMax("1000") BigDecimal constructionCostChangePercent
    ) {}

    public record CreateSensitivityRequest(
            @NotNull UUID financialAnalysisId,
            @NotEmpty @Size(max=250) List<@Valid ShockRequest> points
    ) {}

    public record PointResponse(
            UUID id,int sequenceNo,BigDecimal salePriceChangePercent,BigDecimal sellableAreaChangePercent,
            BigDecimal constructionCostChangePercent,BigDecimal adjustedSellableAreaM2,
            BigDecimal adjustedSalePricePerM2,BigDecimal adjustedBaseConstructionCost,
            BigDecimal grossRevenue,BigDecimal totalProjectCost,BigDecimal projectedProfit,
            BigDecimal roiPercent,BigDecimal profitMarginPercent,Instant createdAt
    ) {
        public static PointResponse from(SensitivityPointEntity e) {
            return new PointResponse(e.getId(),e.getSequenceNo(),e.getSalePriceChangePercent(),
                    e.getSellableAreaChangePercent(),e.getConstructionCostChangePercent(),
                    e.getAdjustedSellableAreaM2(),e.getAdjustedSalePricePerM2(),
                    e.getAdjustedBaseConstructionCost(),e.getGrossRevenue(),e.getTotalProjectCost(),
                    e.getProjectedProfit(),e.getRoiPercent(),e.getProfitMarginPercent(),e.getCreatedAt());
        }
    }

    public record SensitivityResponse(
            UUID id,UUID financialAnalysisId,UUID caseId,UUID scenarioId,String currencyCode,
            int pointCount,BigDecimal bestProfit,BigDecimal worstProfit,BigDecimal bestRoiPercent,
            BigDecimal worstRoiPercent,String algorithmVersion,Map<String,Object> inputSnapshot,
            Map<String,Object> summarySnapshot,String calculatedBy,Instant calculatedAt,
            List<PointResponse> points
    ) {}
}
'@

Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/application/SensitivityService.java") @'
package com.sakhtyar.sensitivity.application;

import static com.sakhtyar.sensitivity.api.SensitivityDtos.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.finance.domain.*;
import com.sakhtyar.sensitivity.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensitivityService {
    private static final String ALGORITHM_VERSION="1.0";

    private final SensitivityAnalysisRepository analysisRepo;
    private final SensitivityPointRepository pointRepo;
    private final FinancialAnalysisRepository financialRepo;
    private final AuditService auditService;
    private final SensitivityCalculator calculator=new SensitivityCalculator();

    public SensitivityService(SensitivityAnalysisRepository analysisRepo,SensitivityPointRepository pointRepo,
            FinancialAnalysisRepository financialRepo,AuditService auditService) {
        this.analysisRepo=analysisRepo; this.pointRepo=pointRepo;
        this.financialRepo=financialRepo; this.auditService=auditService;
    }

    @Transactional
    public SensitivityResponse create(CreateSensitivityRequest r,Authentication auth) {
        FinancialAnalysisEntity base=financialRepo.findById(r.financialAnalysisId())
                .orElseThrow(()->new IllegalArgumentException("Financial analysis not found."));

        if(r.points()==null || r.points().isEmpty()) throw new IllegalArgumentException("At least one sensitivity point is required.");
        if(r.points().size()>250) throw new IllegalArgumentException("Maximum 250 sensitivity points are allowed.");

        var baseline=new SensitivityCalculator.Baseline(
                base.getSellableAreaM2(),base.getExpectedSalePricePerM2(),base.getOtherRevenue(),
                base.getBaseConstructionCost(),base.getAdditionalCost(),base.getFinancingCost(),base.getTaxesAndFees());

        List<CalculatedPoint> calculated=new ArrayList<>();
        for(int i=0;i<r.points().size();i++) {
            var request=r.points().get(i);
            var result=calculator.calculate(baseline,new SensitivityCalculator.Shock(
                    request.salePriceChangePercent(),request.sellableAreaChangePercent(),
                    request.constructionCostChangePercent()));
            calculated.add(new CalculatedPoint(i+1,request,result));
        }

        BigDecimal bestProfit=calculated.stream().map(x->x.result().projectedProfit()).max(BigDecimal::compareTo).orElse(null);
        BigDecimal worstProfit=calculated.stream().map(x->x.result().projectedProfit()).min(BigDecimal::compareTo).orElse(null);
        BigDecimal bestRoi=calculated.stream().map(x->x.result().roiPercent()).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        BigDecimal worstRoi=calculated.stream().map(x->x.result().roiPercent()).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);

        Map<String,Object> input=new LinkedHashMap<>();
        input.put("financialAnalysisId",base.getId().toString());
        input.put("baselineSellableAreaM2",base.getSellableAreaM2().toPlainString());
        input.put("baselineSalePricePerM2",base.getExpectedSalePricePerM2().toPlainString());
        input.put("baselineBaseConstructionCost",base.getBaseConstructionCost().toPlainString());
        input.put("currencyCode",base.getCurrencyCode());
        input.put("pointCount",calculated.size());

        Map<String,Object> summary=new LinkedHashMap<>();
        put(summary,"bestProfit",text(bestProfit)); put(summary,"worstProfit",text(worstProfit));
        put(summary,"bestRoiPercent",text(bestRoi)); put(summary,"worstRoiPercent",text(worstRoi));

        Instant now=Instant.now();
        var analysis=new SensitivityAnalysisEntity(
                UUID.randomUUID(),base.getId(),base.getCaseId(),base.getScenarioId(),base.getCurrencyCode(),
                calculated.size(),bestProfit,worstProfit,bestRoi,worstRoi,ALGORITHM_VERSION,
                input,summary,actor(auth),now
        );
        analysisRepo.save(analysis);

        List<SensitivityPointEntity> entities=new ArrayList<>();
        for(var c:calculated) {
            var q=c.request(); var p=c.result();
            entities.add(pointRepo.save(new SensitivityPointEntity(
                    UUID.randomUUID(),analysis.getId(),c.sequenceNo(),
                    q.salePriceChangePercent(),q.sellableAreaChangePercent(),q.constructionCostChangePercent(),
                    p.adjustedSellableAreaM2(),p.adjustedSalePricePerM2(),p.adjustedBaseConstructionCost(),
                    p.grossRevenue(),p.totalProjectCost(),p.projectedProfit(),p.roiPercent(),
                    p.profitMarginPercent(),now
            )));
        }

        auditService.record("SENSITIVITY_ANALYSIS",analysis.getId(),"SENSITIVITY_ANALYSIS_CALCULATED",
                Map.of("financialAnalysisId",base.getId().toString(),"pointCount",calculated.size()));

        return toResponse(analysis,entities);
    }

    @Transactional(readOnly=true)
    public SensitivityResponse get(UUID id) {
        var e=analysisRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Sensitivity analysis not found."));
        return toResponse(e,pointRepo.findBySensitivityAnalysisIdOrderBySequenceNoAsc(id));
    }

    @Transactional(readOnly=true)
    public List<SensitivityResponse> byFinancialAnalysis(UUID financialAnalysisId) {
        return analysisRepo.findByFinancialAnalysisIdOrderByCalculatedAtDesc(financialAnalysisId).stream()
                .map(e->toResponse(e,pointRepo.findBySensitivityAnalysisIdOrderBySequenceNoAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public List<SensitivityResponse> byCase(UUID caseId) {
        return analysisRepo.findByCaseIdOrderByCalculatedAtDesc(caseId).stream()
                .map(e->toResponse(e,pointRepo.findBySensitivityAnalysisIdOrderBySequenceNoAsc(e.getId()))).toList();
    }

    private SensitivityResponse toResponse(SensitivityAnalysisEntity e,List<SensitivityPointEntity> points) {
        return new SensitivityResponse(e.getId(),e.getFinancialAnalysisId(),e.getCaseId(),e.getScenarioId(),
                e.getCurrencyCode(),e.getPointCount(),e.getBestProfit(),e.getWorstProfit(),
                e.getBestRoiPercent(),e.getWorstRoiPercent(),e.getAlgorithmVersion(),e.getInputSnapshot(),
                e.getSummarySnapshot(),e.getCalculatedBy(),e.getCalculatedAt(),
                points.stream().map(PointResponse::from).toList());
    }

    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
    private static String text(BigDecimal v){return v==null?null:v.stripTrailingZeros().toPlainString();}
    private static void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private record CalculatedPoint(int sequenceNo,ShockRequest request,SensitivityCalculator.Point result){}
}
'@

Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/sensitivity/src/main/java/com/sakhtyar/sensitivity/api/SensitivityController.java") @'
package com.sakhtyar.sensitivity.api;

import static com.sakhtyar.sensitivity.api.SensitivityDtos.*;
import com.sakhtyar.sensitivity.application.SensitivityService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sensitivity")
public class SensitivityController {
    private final SensitivityService service;
    public SensitivityController(SensitivityService service){this.service=service;}

    @PostMapping("/analyses")
    public SensitivityResponse create(@Valid @RequestBody CreateSensitivityRequest request,Authentication auth) {
        return service.create(request,auth);
    }

    @GetMapping("/analyses/{id}")
    public SensitivityResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/financial-analyses/{financialAnalysisId}")
    public List<SensitivityResponse> byFinancial(@PathVariable UUID financialAnalysisId) {
        return service.byFinancialAnalysis(financialAnalysisId);
    }

    @GetMapping("/cases/{caseId}")
    public List<SensitivityResponse> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }
}
'@

Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/sensitivity/src/test/java/com/sakhtyar/sensitivity/application/SensitivityCalculatorTest.java") @'
package com.sakhtyar.sensitivity.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SensitivityCalculatorTest {
    private final SensitivityCalculator calculator=new SensitivityCalculator();

    private SensitivityCalculator.Baseline baseline() {
        return new SensitivityCalculator.Baseline(
                new BigDecimal("1000"),new BigDecimal("100"),
                BigDecimal.ZERO,new BigDecimal("60000"),
                new BigDecimal("5000"),new BigDecimal("2000"),new BigDecimal("3000"));
    }

    @Test
    void zeroShockMatchesBaselineEconomics(){
        var p=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        assertEquals(0,p.grossRevenue().compareTo(new BigDecimal("100000.00")));
        assertEquals(0,p.totalProjectCost().compareTo(new BigDecimal("70000.00")));
        assertEquals(0,p.projectedProfit().compareTo(new BigDecimal("30000.00")));
    }

    @Test
    void negativeSalePriceShockReducesProfit(){
        var base=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var downside=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                new BigDecimal("-10"),BigDecimal.ZERO,BigDecimal.ZERO));
        assertTrue(downside.projectedProfit().compareTo(base.projectedProfit())<0);
    }

    @Test
    void constructionCostShockIncreasesTotalCost(){
        var base=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var downside=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("20")));
        assertTrue(downside.totalProjectCost().compareTo(base.totalProjectCost())>0);
    }

    @Test
    void areaShockChangesRevenue(){
        var p=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,new BigDecimal("10"),BigDecimal.ZERO));
        assertEquals(0,p.adjustedSellableAreaM2().compareTo(new BigDecimal("1100.000000")));
        assertEquals(0,p.grossRevenue().compareTo(new BigDecimal("110000.00")));
    }
}
'@

Write-Step "7/8 - Writing Phase 5.10 documentation"

Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.10-sensitivity-analysis.md") @'
# Phase 5.10 — Sensitivity Analysis

## Purpose
Phase 5.10 measures how project financial outputs change when selected assumptions move around an exact Phase 5.9 financial baseline.

The engine is deterministic and does not forecast or invent future market values.

## Baseline
Every sensitivity analysis references one immutable `financial_analysis`.

Baseline values reused:
- sellable area
- expected sale price per m²
- base construction cost
- other revenue
- additional cost
- financing cost
- taxes and fees
- case/scenario/currency context

## Supported shocks
Each point independently specifies:
- `salePriceChangePercent`
- `sellableAreaChangePercent`
- `constructionCostChangePercent`

A zero/zero/zero point reproduces baseline economics.

The API accepts up to 250 explicit points. This avoids hidden combinatorial expansion and keeps every tested scenario visible and reproducible.

## Calculated outputs per point
- adjusted sellable area
- adjusted sale price per m²
- adjusted base construction cost
- gross revenue
- total project cost
- projected profit
- ROI
- profit margin

Unchanged costs (additional, financing, taxes/fees, other revenue) remain fixed from the baseline.

## Summary
The analysis stores:
- best/worst projected profit
- best/worst ROI
- point count
- algorithm version
- baseline snapshot
- summary snapshot
- actor/time

## Boundaries
Sensitivity analysis is a deterministic what-if tool, not a probabilistic forecast.
Phase 5.10 does not assign probabilities to scenarios and does not recommend an investment decision.

## Deferred
- Monte Carlo simulation
- probability distributions
- correlated variables
- cash-flow sensitivity
- NPV/IRR sensitivity
- tornado charts/UI
- automatic scenario generation
- AI recommendations
'@

Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.10-sensitivity-api.md") @'
# Phase 5.10 — Sensitivity API

Base path: `/api/v1/sensitivity`

## Create analysis
`POST /api/v1/sensitivity/analyses`

Example:
```json
{
  "financialAnalysisId": "00000000-0000-0000-0000-000000000000",
  "points": [
    {
      "salePriceChangePercent": 0,
      "sellableAreaChangePercent": 0,
      "constructionCostChangePercent": 0
    },
    {
      "salePriceChangePercent": -10,
      "sellableAreaChangePercent": 0,
      "constructionCostChangePercent": 15
    },
    {
      "salePriceChangePercent": 10,
      "sellableAreaChangePercent": 5,
      "constructionCostChangePercent": 0
    }
  ]
}
```

## Read
- `GET /api/v1/sensitivity/analyses/{id}`
- `GET /api/v1/sensitivity/financial-analyses/{financialAnalysisId}`
- `GET /api/v1/sensitivity/cases/{caseId}`

Maximum explicit points per request: 250.
'@

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
Write-Host "Phase 5.10 applied on v2 successfully." -ForegroundColor Green
Write-Host "Base SHA used: $baseSha"
Write-Host ""
Write-Host "Next validation:" -ForegroundColor Yellow
Write-Host "1) Run backend from IntelliJ and confirm Flyway V18 applies."
Write-Host "2) Confirm the application stays running."
Write-Host "3) This phase is backend-only; no frontend files should change."
Write-Host "4) After runtime validation, commit and push directly to v2."
