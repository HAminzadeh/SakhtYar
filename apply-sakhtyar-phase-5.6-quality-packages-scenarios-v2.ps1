param(
    [string]$Branch = "v2",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 92) -ForegroundColor DarkCyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 92) -ForegroundColor DarkCyan
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

Write-Step "SakhtYar Phase 5.6 - Quality Packages & Construction Scenarios (directly on v2)"

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
        throw "Staged changes exist. Commit or stash them before Phase 5.6.`n$($staged -join "`n")"
    }
}

& git diff --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Tracked working-tree changes exist. Commit or stash them before Phase 5.6."
}

Write-Step "1/8 - Updating v2"
Invoke-Git fetch origin $Branch
Invoke-Git pull --ff-only origin $Branch
$baseSha = (& git rev-parse HEAD).Trim()
Write-Host "Working directly on $Branch at $baseSha" -ForegroundColor Green

Write-Step "2/8 - Creating backup"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $RepoRoot ".local\patch-backups\phase-5.6-$stamp"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

@(
    "backend/pom.xml",
    "backend/app/pom.xml",
    "backend/app/src/main/resources/db/migration/V14__phase5_quality_packages_scenarios.sql",
    "backend/modules/scenario/pom.xml",
    "docs/phase-5/phase-5.6-quality-packages-scenarios.md",
    "docs/phase-5/phase-5.6-scenario-api.md"
) | ForEach-Object { Backup-IfExists $_ }

Write-Host "Backup: $BackupDir" -ForegroundColor Green

Write-Step "3/8 - Registering scenario module"

$parentPom = Join-Path $RepoRoot "backend/pom.xml"
$appPom = Join-Path $RepoRoot "backend/app/pom.xml"

Replace-Once $parentPom @'
    <module>modules/material</module>
    <module>modules/assembly</module>
    <module>modules/analysis</module>
'@ @'
    <module>modules/material</module>
    <module>modules/assembly</module>
    <module>modules/scenario</module>
    <module>modules/analysis</module>
'@ "<module>modules/scenario</module>"

Replace-Once $parentPom @'
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-assembly</artifactId>
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
        <artifactId>sakhtyar-assembly</artifactId>
        <version>${project.version}</version>
      </dependency>
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
'@ "<artifactId>sakhtyar-scenario</artifactId>"

Replace-Once $appPom @'
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-assembly</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-analysis</artifactId></dependency>
'@ @'
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-assembly</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-scenario</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-analysis</artifactId></dependency>
'@ "<artifactId>sakhtyar-scenario</artifactId>"

$scenarioPom = @'
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

  <artifactId>sakhtyar-scenario</artifactId>

  <dependencies>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-shared-kernel</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-audit</artifactId></dependency>
    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-assembly</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/scenario/pom.xml") $scenarioPom

Write-Step "4/8 - Writing V14 quality package & scenario migration"

$migration = @'
CREATE TABLE quality_package (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name_fa VARCHAR(400) NOT NULL,
    name_en VARCHAR(400),
    quality_level VARCHAR(40) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_quality_package_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_quality_package_level ON quality_package(quality_level, active);

CREATE TABLE quality_package_selection (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES quality_package(id) ON DELETE CASCADE,
    slot_code VARCHAR(120) NOT NULL,
    slot_name_fa VARCHAR(300) NOT NULL,
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    quantity_multiplier NUMERIC(20,6) NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_quality_package_slot UNIQUE(package_id, slot_code),
    CONSTRAINT chk_quality_package_multiplier CHECK (quantity_multiplier > 0)
);

CREATE INDEX idx_quality_package_selection_package
    ON quality_package_selection(package_id, sort_order, id);

CREATE TABLE construction_scenario (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name_fa VARCHAR(400) NOT NULL,
    name_en VARCHAR(400),
    quality_package_id UUID REFERENCES quality_package(id) ON DELETE SET NULL,
    structural_system VARCHAR(160),
    scenario_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    description TEXT,
    assumptions JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_construction_scenario_assumptions_object CHECK (jsonb_typeof(assumptions) = 'object')
);

CREATE INDEX idx_construction_scenario_package
    ON construction_scenario(quality_package_id, scenario_status, active);

CREATE TABLE construction_scenario_item (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE CASCADE,
    slot_code VARCHAR(120),
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    quantity NUMERIC(20,6) NOT NULL,
    source VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_scenario_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_construction_scenario_item_scenario
    ON construction_scenario_item(scenario_id, sort_order, id);

CREATE TABLE scenario_cost_snapshot (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    price_type VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    province VARCHAR(160),
    city VARCHAR(160),
    total_cost NUMERIC(20,2) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    calculated_by VARCHAR(150) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_scenario_cost_snapshot_total CHECK (total_cost >= 0)
);

CREATE INDEX idx_scenario_cost_snapshot_scenario
    ON scenario_cost_snapshot(scenario_id, calculated_at DESC);

CREATE TABLE scenario_cost_snapshot_line (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES scenario_cost_snapshot(id) ON DELETE CASCADE,
    scenario_item_id UUID NOT NULL REFERENCES construction_scenario_item(id) ON DELETE RESTRICT,
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    assembly_estimate_id UUID NOT NULL REFERENCES cost_estimate(id) ON DELETE RESTRICT,
    quantity NUMERIC(20,6) NOT NULL,
    unit_cost NUMERIC(20,2) NOT NULL,
    line_total NUMERIC(20,2) NOT NULL,
    CONSTRAINT chk_scenario_cost_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_scenario_cost_line_values CHECK (unit_cost >= 0 AND line_total >= 0)
);

CREATE INDEX idx_scenario_cost_snapshot_line_snapshot
    ON scenario_cost_snapshot_line(snapshot_id);
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/app/src/main/resources/db/migration/V14__phase5_quality_packages_scenarios.sql") $migration

Write-Step "5/8 - Writing scenario domain"

$domainFiles = @{
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/QualityLevel.java" = @'
package com.sakhtyar.scenario.domain;
public enum QualityLevel { ECONOMY, STANDARD, PREMIUM, LUXURY, CUSTOM }
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ScenarioStatus.java" = @'
package com.sakhtyar.scenario.domain;
public enum ScenarioStatus { DRAFT, READY, ARCHIVED }
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ScenarioItemSource.java" = @'
package com.sakhtyar.scenario.domain;
public enum ScenarioItemSource { PACKAGE, OVERRIDE, MANUAL }
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/QualityPackageEntity.java" = @'
package com.sakhtyar.scenario.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(name="quality_package")
public class QualityPackageEntity {
 @Id private UUID id; @Column(nullable=false,unique=true,length=120) private String code;
 @Column(name="name_fa",nullable=false,length=400) private String nameFa; @Column(name="name_en",length=400) private String nameEn;
 @Enumerated(EnumType.STRING) @Column(name="quality_level",nullable=false,length=40) private QualityLevel qualityLevel;
 @Column(columnDefinition="text") private String description; @Column(nullable=false) private boolean active;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> metadata;
 @Column(name="created_by",nullable=false,length=150) private String createdBy; @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt; protected QualityPackageEntity(){}
 public QualityPackageEntity(UUID id,String code,String nameFa,String nameEn,QualityLevel qualityLevel,String description,boolean active,Map<String,Object> metadata,String createdBy,Instant now){
  this.id=id;this.code=code;this.nameFa=nameFa;this.nameEn=nameEn;this.qualityLevel=qualityLevel;this.description=description;this.active=active;this.metadata=metadata==null?Map.of():Map.copyOf(metadata);this.createdBy=createdBy;this.createdAt=now;this.updatedAt=now;}
 public void update(String nameFa,String nameEn,QualityLevel q,String description,boolean active,Map<String,Object> metadata,Instant now){this.nameFa=nameFa;this.nameEn=nameEn;this.qualityLevel=q;this.description=description;this.active=active;this.metadata=metadata==null?Map.of():Map.copyOf(metadata);this.updatedAt=now;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getNameFa(){return nameFa;} public String getNameEn(){return nameEn;} public QualityLevel getQualityLevel(){return qualityLevel;} public String getDescription(){return description;} public boolean isActive(){return active;} public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);} public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/QualityPackageSelectionEntity.java" = @'
package com.sakhtyar.scenario.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="quality_package_selection")
public class QualityPackageSelectionEntity {
 @Id private UUID id; @Column(name="package_id",nullable=false) private UUID packageId; @Column(name="slot_code",nullable=false,length=120) private String slotCode;
 @Column(name="slot_name_fa",nullable=false,length=300) private String slotNameFa; @Column(name="assembly_id",nullable=false) private UUID assemblyId;
 @Column(nullable=false) private boolean required; @Column(name="quantity_multiplier",nullable=false,precision=20,scale=6) private BigDecimal quantityMultiplier;
 @Column(name="sort_order",nullable=false) private int sortOrder; @Column(columnDefinition="text") private String note; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected QualityPackageSelectionEntity(){} public QualityPackageSelectionEntity(UUID id,UUID packageId,String slotCode,String slotNameFa,UUID assemblyId,boolean required,BigDecimal quantityMultiplier,int sortOrder,String note,Instant createdAt){this.id=id;this.packageId=packageId;this.slotCode=slotCode;this.slotNameFa=slotNameFa;this.assemblyId=assemblyId;this.required=required;this.quantityMultiplier=quantityMultiplier;this.sortOrder=sortOrder;this.note=note;this.createdAt=createdAt;}
 public UUID getId(){return id;} public UUID getPackageId(){return packageId;} public String getSlotCode(){return slotCode;} public String getSlotNameFa(){return slotNameFa;} public UUID getAssemblyId(){return assemblyId;} public boolean isRequired(){return required;} public BigDecimal getQuantityMultiplier(){return quantityMultiplier;} public int getSortOrder(){return sortOrder;} public String getNote(){return note;} public Instant getCreatedAt(){return createdAt;}
}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ConstructionScenarioEntity.java" = @'
package com.sakhtyar.scenario.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(name="construction_scenario")
public class ConstructionScenarioEntity {
 @Id private UUID id; @Column(nullable=false,unique=true,length=120) private String code; @Column(name="name_fa",nullable=false,length=400) private String nameFa; @Column(name="name_en",length=400) private String nameEn; @Column(name="quality_package_id") private UUID qualityPackageId; @Column(name="structural_system",length=160) private String structuralSystem; @Enumerated(EnumType.STRING) @Column(name="scenario_status",nullable=false,length=40) private ScenarioStatus status; @Column(columnDefinition="text") private String description; @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> assumptions; @Column(nullable=false) private boolean active; @Column(name="created_by",nullable=false,length=150) private String createdBy; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected ConstructionScenarioEntity(){} public ConstructionScenarioEntity(UUID id,String code,String nameFa,String nameEn,UUID qualityPackageId,String structuralSystem,String description,Map<String,Object> assumptions,boolean active,String createdBy,Instant now){this.id=id;this.code=code;this.nameFa=nameFa;this.nameEn=nameEn;this.qualityPackageId=qualityPackageId;this.structuralSystem=structuralSystem;this.status=ScenarioStatus.DRAFT;this.description=description;this.assumptions=assumptions==null?Map.of():Map.copyOf(assumptions);this.active=active;this.createdBy=createdBy;this.createdAt=now;this.updatedAt=now;}
 public void update(String nameFa,String nameEn,UUID qualityPackageId,String structuralSystem,String description,Map<String,Object> assumptions,boolean active,Instant now){this.nameFa=nameFa;this.nameEn=nameEn;this.qualityPackageId=qualityPackageId;this.structuralSystem=structuralSystem;this.description=description;this.assumptions=assumptions==null?Map.of():Map.copyOf(assumptions);this.active=active;this.updatedAt=now;} public void setStatus(ScenarioStatus status,Instant now){this.status=status;this.updatedAt=now;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getNameFa(){return nameFa;} public String getNameEn(){return nameEn;} public UUID getQualityPackageId(){return qualityPackageId;} public String getStructuralSystem(){return structuralSystem;} public ScenarioStatus getStatus(){return status;} public String getDescription(){return description;} public Map<String,Object> getAssumptions(){return assumptions==null?Map.of():Map.copyOf(assumptions);} public boolean isActive(){return active;} public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ConstructionScenarioItemEntity.java" = @'
package com.sakhtyar.scenario.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="construction_scenario_item")
public class ConstructionScenarioItemEntity {
 @Id private UUID id; @Column(name="scenario_id",nullable=false) private UUID scenarioId; @Column(name="slot_code",length=120) private String slotCode; @Column(name="assembly_id",nullable=false) private UUID assemblyId; @Column(nullable=false,precision=20,scale=6) private BigDecimal quantity; @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private ScenarioItemSource source; @Column(nullable=false) private boolean enabled; @Column(name="sort_order",nullable=false) private int sortOrder; @Column(columnDefinition="text") private String note; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected ConstructionScenarioItemEntity(){} public ConstructionScenarioItemEntity(UUID id,UUID scenarioId,String slotCode,UUID assemblyId,BigDecimal quantity,ScenarioItemSource source,boolean enabled,int sortOrder,String note,Instant createdAt){this.id=id;this.scenarioId=scenarioId;this.slotCode=slotCode;this.assemblyId=assemblyId;this.quantity=quantity;this.source=source;this.enabled=enabled;this.sortOrder=sortOrder;this.note=note;this.createdAt=createdAt;}
 public UUID getId(){return id;} public UUID getScenarioId(){return scenarioId;} public String getSlotCode(){return slotCode;} public UUID getAssemblyId(){return assemblyId;} public BigDecimal getQuantity(){return quantity;} public ScenarioItemSource getSource(){return source;} public boolean isEnabled(){return enabled;} public int getSortOrder(){return sortOrder;} public String getNote(){return note;} public Instant getCreatedAt(){return createdAt;}
}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ScenarioCostSnapshotEntity.java" = @'
package com.sakhtyar.scenario.domain;
import com.sakhtyar.material.domain.MaterialPriceType; import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="scenario_cost_snapshot")
public class ScenarioCostSnapshotEntity {
 @Id private UUID id; @Column(name="scenario_id",nullable=false) private UUID scenarioId; @Enumerated(EnumType.STRING) @Column(name="price_type",nullable=false,length=40) private MaterialPriceType priceType; @Column(name="currency_code",nullable=false,length=3) private String currencyCode; @Column(length=160) private String province; @Column(length=160) private String city; @Column(name="total_cost",nullable=false,precision=20,scale=2) private BigDecimal totalCost; @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion; @Column(name="calculated_by",nullable=false,length=150) private String calculatedBy; @Column(name="calculated_at",nullable=false) private Instant calculatedAt;
 protected ScenarioCostSnapshotEntity(){} public ScenarioCostSnapshotEntity(UUID id,UUID scenarioId,MaterialPriceType priceType,String currencyCode,String province,String city,BigDecimal totalCost,String algorithmVersion,String calculatedBy,Instant calculatedAt){this.id=id;this.scenarioId=scenarioId;this.priceType=priceType;this.currencyCode=currencyCode;this.province=province;this.city=city;this.totalCost=totalCost;this.algorithmVersion=algorithmVersion;this.calculatedBy=calculatedBy;this.calculatedAt=calculatedAt;}
 public UUID getId(){return id;} public UUID getScenarioId(){return scenarioId;} public MaterialPriceType getPriceType(){return priceType;} public String getCurrencyCode(){return currencyCode;} public String getProvince(){return province;} public String getCity(){return city;} public BigDecimal getTotalCost(){return totalCost;} public String getAlgorithmVersion(){return algorithmVersion;} public String getCalculatedBy(){return calculatedBy;} public Instant getCalculatedAt(){return calculatedAt;}
}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ScenarioCostSnapshotLineEntity.java" = @'
package com.sakhtyar.scenario.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="scenario_cost_snapshot_line")
public class ScenarioCostSnapshotLineEntity {
 @Id private UUID id; @Column(name="snapshot_id",nullable=false) private UUID snapshotId; @Column(name="scenario_item_id",nullable=false) private UUID scenarioItemId; @Column(name="assembly_id",nullable=false) private UUID assemblyId; @Column(name="assembly_estimate_id",nullable=false) private UUID assemblyEstimateId; @Column(nullable=false,precision=20,scale=6) private BigDecimal quantity; @Column(name="unit_cost",nullable=false,precision=20,scale=2) private BigDecimal unitCost; @Column(name="line_total",nullable=false,precision=20,scale=2) private BigDecimal lineTotal;
 protected ScenarioCostSnapshotLineEntity(){} public ScenarioCostSnapshotLineEntity(UUID id,UUID snapshotId,UUID scenarioItemId,UUID assemblyId,UUID assemblyEstimateId,BigDecimal quantity,BigDecimal unitCost,BigDecimal lineTotal){this.id=id;this.snapshotId=snapshotId;this.scenarioItemId=scenarioItemId;this.assemblyId=assemblyId;this.assemblyEstimateId=assemblyEstimateId;this.quantity=quantity;this.unitCost=unitCost;this.lineTotal=lineTotal;}
 public UUID getId(){return id;} public UUID getSnapshotId(){return snapshotId;} public UUID getScenarioItemId(){return scenarioItemId;} public UUID getAssemblyId(){return assemblyId;} public UUID getAssemblyEstimateId(){return assemblyEstimateId;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getLineTotal(){return lineTotal;}
}
'@
}

foreach ($relative in $domainFiles.Keys) { Write-Utf8NoBom (Join-Path $RepoRoot $relative) $domainFiles[$relative] }

$repoFiles = @{
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/QualityPackageRepository.java" = @'
package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface QualityPackageRepository extends JpaRepository<QualityPackageEntity,UUID>{Optional<QualityPackageEntity> findByCodeIgnoreCase(String code);List<QualityPackageEntity> findAllByOrderByUpdatedAtDesc();}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/QualityPackageSelectionRepository.java" = @'
package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface QualityPackageSelectionRepository extends JpaRepository<QualityPackageSelectionEntity,UUID>{List<QualityPackageSelectionEntity> findByPackageIdOrderBySortOrderAscIdAsc(UUID packageId);Optional<QualityPackageSelectionEntity> findByPackageIdAndSlotCodeIgnoreCase(UUID packageId,String slotCode);}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ConstructionScenarioRepository.java" = @'
package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ConstructionScenarioRepository extends JpaRepository<ConstructionScenarioEntity,UUID>{Optional<ConstructionScenarioEntity> findByCodeIgnoreCase(String code);List<ConstructionScenarioEntity> findAllByOrderByUpdatedAtDesc();}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ConstructionScenarioItemRepository.java" = @'
package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ConstructionScenarioItemRepository extends JpaRepository<ConstructionScenarioItemEntity,UUID>{List<ConstructionScenarioItemEntity> findByScenarioIdOrderBySortOrderAscIdAsc(UUID scenarioId);}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ScenarioCostSnapshotRepository.java" = @'
package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ScenarioCostSnapshotRepository extends JpaRepository<ScenarioCostSnapshotEntity,UUID>{List<ScenarioCostSnapshotEntity> findByScenarioIdOrderByCalculatedAtDesc(UUID scenarioId);}
'@
"backend/modules/scenario/src/main/java/com/sakhtyar/scenario/domain/ScenarioCostSnapshotLineRepository.java" = @'
package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ScenarioCostSnapshotLineRepository extends JpaRepository<ScenarioCostSnapshotLineEntity,UUID>{List<ScenarioCostSnapshotLineEntity> findBySnapshotIdOrderByIdAsc(UUID snapshotId);}
'@
}
foreach ($relative in $repoFiles.Keys) { Write-Utf8NoBom (Join-Path $RepoRoot $relative) $repoFiles[$relative] }

Write-Step "6/8 - Writing scenario services, API and tests"

$dto = @'
package com.sakhtyar.scenario.api;
import com.sakhtyar.material.domain.MaterialPriceType; import com.sakhtyar.scenario.domain.*; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*;
public final class ScenarioDtos {
 private ScenarioDtos(){}
 public record UpsertPackageRequest(@NotBlank @Size(max=120) String code,@NotBlank @Size(max=400) String nameFa,@Size(max=400) String nameEn,@NotNull QualityLevel qualityLevel,@Size(max=10000) String description,boolean active,Map<String,Object> metadata){}
 public record CreateSelectionRequest(@NotBlank @Size(max=120) String slotCode,@NotBlank @Size(max=300) String slotNameFa,@NotNull UUID assemblyId,boolean required,@NotNull @DecimalMin("0.000001") BigDecimal quantityMultiplier,@Min(0) int sortOrder,@Size(max=4000) String note){}
 public record UpsertScenarioRequest(@NotBlank @Size(max=120) String code,@NotBlank @Size(max=400) String nameFa,@Size(max=400) String nameEn,UUID qualityPackageId,@Size(max=160) String structuralSystem,@Size(max=10000) String description,Map<String,Object> assumptions,boolean active){}
 public record CreateScenarioItemRequest(@Size(max=120) String slotCode,@NotNull UUID assemblyId,@NotNull @DecimalMin("0.000001") BigDecimal quantity,@NotNull ScenarioItemSource source,boolean enabled,@Min(0) int sortOrder,@Size(max=4000) String note){}
 public record ChangeStatusRequest(@NotNull ScenarioStatus status){}
 public record CalculateScenarioRequest(@NotNull MaterialPriceType priceType,@NotBlank @Size(min=3,max=3) String currencyCode,@Size(max=160) String province,@Size(max=160) String city){}
 public record PackageResponse(UUID id,String code,String nameFa,String nameEn,QualityLevel qualityLevel,String description,boolean active,Map<String,Object> metadata,String createdBy,Instant createdAt,Instant updatedAt){public static PackageResponse from(QualityPackageEntity e){return new PackageResponse(e.getId(),e.getCode(),e.getNameFa(),e.getNameEn(),e.getQualityLevel(),e.getDescription(),e.isActive(),e.getMetadata(),e.getCreatedBy(),e.getCreatedAt(),e.getUpdatedAt());}}
 public record SelectionResponse(UUID id,UUID packageId,String slotCode,String slotNameFa,UUID assemblyId,boolean required,BigDecimal quantityMultiplier,int sortOrder,String note,Instant createdAt){public static SelectionResponse from(QualityPackageSelectionEntity e){return new SelectionResponse(e.getId(),e.getPackageId(),e.getSlotCode(),e.getSlotNameFa(),e.getAssemblyId(),e.isRequired(),e.getQuantityMultiplier(),e.getSortOrder(),e.getNote(),e.getCreatedAt());}}
 public record ScenarioResponse(UUID id,String code,String nameFa,String nameEn,UUID qualityPackageId,String structuralSystem,ScenarioStatus status,String description,Map<String,Object> assumptions,boolean active,String createdBy,Instant createdAt,Instant updatedAt){public static ScenarioResponse from(ConstructionScenarioEntity e){return new ScenarioResponse(e.getId(),e.getCode(),e.getNameFa(),e.getNameEn(),e.getQualityPackageId(),e.getStructuralSystem(),e.getStatus(),e.getDescription(),e.getAssumptions(),e.isActive(),e.getCreatedBy(),e.getCreatedAt(),e.getUpdatedAt());}}
 public record ScenarioItemResponse(UUID id,UUID scenarioId,String slotCode,UUID assemblyId,BigDecimal quantity,ScenarioItemSource source,boolean enabled,int sortOrder,String note,Instant createdAt){public static ScenarioItemResponse from(ConstructionScenarioItemEntity e){return new ScenarioItemResponse(e.getId(),e.getScenarioId(),e.getSlotCode(),e.getAssemblyId(),e.getQuantity(),e.getSource(),e.isEnabled(),e.getSortOrder(),e.getNote(),e.getCreatedAt());}}
 public record SnapshotLineResponse(UUID id,UUID scenarioItemId,UUID assemblyId,UUID assemblyEstimateId,BigDecimal quantity,BigDecimal unitCost,BigDecimal lineTotal){public static SnapshotLineResponse from(ScenarioCostSnapshotLineEntity e){return new SnapshotLineResponse(e.getId(),e.getScenarioItemId(),e.getAssemblyId(),e.getAssemblyEstimateId(),e.getQuantity(),e.getUnitCost(),e.getLineTotal());}}
 public record SnapshotResponse(UUID id,UUID scenarioId,MaterialPriceType priceType,String currencyCode,String province,String city,BigDecimal totalCost,String algorithmVersion,String calculatedBy,Instant calculatedAt,List<SnapshotLineResponse> lines){}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/scenario/src/main/java/com/sakhtyar/scenario/api/ScenarioDtos.java") $dto

$service = @'
package com.sakhtyar.scenario.application;
import static com.sakhtyar.scenario.api.ScenarioDtos.*;
import com.sakhtyar.assembly.api.AssemblyDtos; import com.sakhtyar.assembly.application.AssemblyService; import com.sakhtyar.assembly.domain.CostAssemblyRepository; import com.sakhtyar.audit.application.AuditService; import com.sakhtyar.scenario.domain.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*; import org.springframework.security.core.Authentication; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service
public class ScenarioService {
 private static final String COST_ALGORITHM_VERSION="1.0";
 private final QualityPackageRepository packageRepo; private final QualityPackageSelectionRepository selectionRepo; private final ConstructionScenarioRepository scenarioRepo; private final ConstructionScenarioItemRepository itemRepo; private final ScenarioCostSnapshotRepository snapshotRepo; private final ScenarioCostSnapshotLineRepository snapshotLineRepo; private final CostAssemblyRepository assemblyRepo; private final AssemblyService assemblyService; private final AuditService auditService;
 public ScenarioService(QualityPackageRepository p,QualityPackageSelectionRepository s,ConstructionScenarioRepository c,ConstructionScenarioItemRepository i,ScenarioCostSnapshotRepository sr,ScenarioCostSnapshotLineRepository sl,CostAssemblyRepository ar,AssemblyService as,AuditService au){packageRepo=p;selectionRepo=s;scenarioRepo=c;itemRepo=i;snapshotRepo=sr;snapshotLineRepo=sl;assemblyRepo=ar;assemblyService=as;auditService=au;}
 @Transactional(readOnly=true) public List<PackageResponse> packages(){return packageRepo.findAllByOrderByUpdatedAtDesc().stream().map(PackageResponse::from).toList();}
 @Transactional public PackageResponse createPackage(UpsertPackageRequest r,Authentication a){String code=r.code().trim().toUpperCase(Locale.ROOT);packageRepo.findByCodeIgnoreCase(code).ifPresent(x->{throw new IllegalArgumentException("Quality package code already exists.");});Instant now=Instant.now();var e=new QualityPackageEntity(UUID.randomUUID(),code,r.nameFa().trim(),trim(r.nameEn()),r.qualityLevel(),trim(r.description()),r.active(),r.metadata(),actor(a),now);packageRepo.save(e);auditService.record("QUALITY_PACKAGE",e.getId(),"QUALITY_PACKAGE_CREATED",Map.of("code",code));return PackageResponse.from(e);}
 @Transactional public PackageResponse updatePackage(UUID id,UpsertPackageRequest r){var e=reqPkg(id);if(!e.getCode().equalsIgnoreCase(r.code().trim()))throw new IllegalArgumentException("Quality package code is immutable.");e.update(r.nameFa().trim(),trim(r.nameEn()),r.qualityLevel(),trim(r.description()),r.active(),r.metadata(),Instant.now());auditService.record("QUALITY_PACKAGE",id,"QUALITY_PACKAGE_UPDATED",Map.of("code",e.getCode(),"active",e.isActive()));return PackageResponse.from(e);}
 @Transactional(readOnly=true) public List<SelectionResponse> selections(UUID id){reqPkg(id);return selectionRepo.findByPackageIdOrderBySortOrderAscIdAsc(id).stream().map(SelectionResponse::from).toList();}
 @Transactional public SelectionResponse addSelection(UUID id,CreateSelectionRequest r){reqPkg(id);if(!assemblyRepo.existsById(r.assemblyId()))throw new IllegalArgumentException("Assembly not found.");selectionRepo.findByPackageIdAndSlotCodeIgnoreCase(id,r.slotCode().trim()).ifPresent(x->{throw new IllegalArgumentException("Slot code already exists in package.");});var e=new QualityPackageSelectionEntity(UUID.randomUUID(),id,r.slotCode().trim().toUpperCase(Locale.ROOT),r.slotNameFa().trim(),r.assemblyId(),r.required(),r.quantityMultiplier(),r.sortOrder(),trim(r.note()),Instant.now());selectionRepo.save(e);auditService.record("QUALITY_PACKAGE",id,"QUALITY_PACKAGE_SELECTION_ADDED",Map.of("selectionId",e.getId().toString()));return SelectionResponse.from(e);}
 @Transactional public void removeSelection(UUID id,UUID selectionId){reqPkg(id);var e=selectionRepo.findById(selectionId).orElseThrow(()->new IllegalArgumentException("Selection not found."));if(!e.getPackageId().equals(id))throw new IllegalArgumentException("Selection does not belong to package.");selectionRepo.delete(e);auditService.record("QUALITY_PACKAGE",id,"QUALITY_PACKAGE_SELECTION_REMOVED",Map.of("selectionId",selectionId.toString()));}
 @Transactional(readOnly=true) public List<ScenarioResponse> scenarios(){return scenarioRepo.findAllByOrderByUpdatedAtDesc().stream().map(ScenarioResponse::from).toList();}
 @Transactional public ScenarioResponse createScenario(UpsertScenarioRequest r,Authentication a){String code=r.code().trim().toUpperCase(Locale.ROOT);scenarioRepo.findByCodeIgnoreCase(code).ifPresent(x->{throw new IllegalArgumentException("Scenario code already exists.");});validatePkg(r.qualityPackageId());Instant now=Instant.now();var e=new ConstructionScenarioEntity(UUID.randomUUID(),code,r.nameFa().trim(),trim(r.nameEn()),r.qualityPackageId(),trim(r.structuralSystem()),trim(r.description()),r.assumptions(),r.active(),actor(a),now);scenarioRepo.save(e);auditService.record("CONSTRUCTION_SCENARIO",e.getId(),"SCENARIO_CREATED",Map.of("code",code));return ScenarioResponse.from(e);}
 @Transactional public ScenarioResponse updateScenario(UUID id,UpsertScenarioRequest r){var e=reqScenario(id);if(!e.getCode().equalsIgnoreCase(r.code().trim()))throw new IllegalArgumentException("Scenario code is immutable.");validatePkg(r.qualityPackageId());e.update(r.nameFa().trim(),trim(r.nameEn()),r.qualityPackageId(),trim(r.structuralSystem()),trim(r.description()),r.assumptions(),r.active(),Instant.now());auditService.record("CONSTRUCTION_SCENARIO",id,"SCENARIO_UPDATED",Map.of("code",e.getCode(),"active",e.isActive()));return ScenarioResponse.from(e);}
 @Transactional public ScenarioResponse changeStatus(UUID id,ChangeStatusRequest r){var e=reqScenario(id);if(r.status()==ScenarioStatus.READY){if(!e.isActive())throw new IllegalStateException("Inactive scenario cannot be READY.");if(itemRepo.findByScenarioIdOrderBySortOrderAscIdAsc(id).stream().noneMatch(ConstructionScenarioItemEntity::isEnabled))throw new IllegalStateException("Scenario must contain at least one enabled item before READY.");}e.setStatus(r.status(),Instant.now());auditService.record("CONSTRUCTION_SCENARIO",id,"SCENARIO_STATUS_CHANGED",Map.of("status",r.status().name()));return ScenarioResponse.from(e);}
 @Transactional(readOnly=true) public List<ScenarioItemResponse> items(UUID id){reqScenario(id);return itemRepo.findByScenarioIdOrderBySortOrderAscIdAsc(id).stream().map(ScenarioItemResponse::from).toList();}
 @Transactional public ScenarioItemResponse addItem(UUID id,CreateScenarioItemRequest r){var sc=reqScenario(id);if(!assemblyRepo.existsById(r.assemblyId()))throw new IllegalArgumentException("Assembly not found.");if(r.slotCode()!=null&&sc.getQualityPackageId()!=null){var sel=selectionRepo.findByPackageIdAndSlotCodeIgnoreCase(sc.getQualityPackageId(),r.slotCode().trim()).orElseThrow(()->new IllegalArgumentException("Slot is not defined in selected quality package."));if(r.source()==ScenarioItemSource.PACKAGE&&!sel.getAssemblyId().equals(r.assemblyId()))throw new IllegalArgumentException("PACKAGE item assembly must match package selection.");}var e=new ConstructionScenarioItemEntity(UUID.randomUUID(),id,r.slotCode()==null?null:r.slotCode().trim().toUpperCase(Locale.ROOT),r.assemblyId(),r.quantity(),r.source(),r.enabled(),r.sortOrder(),trim(r.note()),Instant.now());itemRepo.save(e);auditService.record("CONSTRUCTION_SCENARIO",id,"SCENARIO_ITEM_ADDED",Map.of("itemId",e.getId().toString()));return ScenarioItemResponse.from(e);}
 @Transactional public void removeItem(UUID id,UUID itemId){reqScenario(id);var e=itemRepo.findById(itemId).orElseThrow(()->new IllegalArgumentException("Scenario item not found."));if(!e.getScenarioId().equals(id))throw new IllegalArgumentException("Item does not belong to scenario.");itemRepo.delete(e);auditService.record("CONSTRUCTION_SCENARIO",id,"SCENARIO_ITEM_REMOVED",Map.of("itemId",itemId.toString()));}
 @Transactional public SnapshotResponse calculate(UUID id,CalculateScenarioRequest r,Authentication a){var sc=reqScenario(id);if(!sc.isActive())throw new IllegalStateException("Scenario is inactive.");if(sc.getStatus()==ScenarioStatus.ARCHIVED)throw new IllegalStateException("Archived scenario cannot be recalculated.");var items=itemRepo.findByScenarioIdOrderBySortOrderAscIdAsc(id).stream().filter(ConstructionScenarioItemEntity::isEnabled).toList();if(items.isEmpty())throw new IllegalStateException("Scenario has no enabled items.");String currency=r.currencyCode().trim().toUpperCase(Locale.ROOT);BigDecimal total=BigDecimal.ZERO;List<AssemblyResult> results=new ArrayList<>();for(var item:items){var estimate=assemblyService.calculate(item.getAssemblyId(),new AssemblyDtos.CalculateCostRequest(item.getQuantity(),r.priceType(),currency,trim(r.province()),trim(r.city())),a);total=total.add(estimate.totalCost());results.add(new AssemblyResult(item,estimate));}Instant now=Instant.now();var snap=new ScenarioCostSnapshotEntity(UUID.randomUUID(),id,r.priceType(),currency,trim(r.province()),trim(r.city()),total,COST_ALGORITHM_VERSION,actor(a),now);snapshotRepo.save(snap);List<ScenarioCostSnapshotLineEntity> lines=new ArrayList<>();for(var result:results){var line=new ScenarioCostSnapshotLineEntity(UUID.randomUUID(),snap.getId(),result.item().getId(),result.item().getAssemblyId(),result.estimate().id(),result.item().getQuantity(),result.estimate().unitCost(),result.estimate().totalCost());lines.add(snapshotLineRepo.save(line));}auditService.record("SCENARIO_COST_SNAPSHOT",snap.getId(),"SCENARIO_COST_CALCULATED",Map.of("scenarioId",id.toString(),"totalCost",total.toPlainString(),"currency",currency));return toSnapshot(snap,lines);}
 @Transactional(readOnly=true) public List<SnapshotResponse> snapshots(UUID id){reqScenario(id);return snapshotRepo.findByScenarioIdOrderByCalculatedAtDesc(id).stream().map(s->toSnapshot(s,snapshotLineRepo.findBySnapshotIdOrderByIdAsc(s.getId()))).toList();}
 private SnapshotResponse toSnapshot(ScenarioCostSnapshotEntity s,List<ScenarioCostSnapshotLineEntity> lines){return new SnapshotResponse(s.getId(),s.getScenarioId(),s.getPriceType(),s.getCurrencyCode(),s.getProvince(),s.getCity(),s.getTotalCost(),s.getAlgorithmVersion(),s.getCalculatedBy(),s.getCalculatedAt(),lines.stream().map(SnapshotLineResponse::from).toList());}
 private QualityPackageEntity reqPkg(UUID id){return packageRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Quality package not found."));} private ConstructionScenarioEntity reqScenario(UUID id){return scenarioRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Scenario not found."));} private void validatePkg(UUID id){if(id!=null&&!packageRepo.existsById(id))throw new IllegalArgumentException("Quality package not found.");} private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();} private static String trim(String s){return s==null?null:s.trim();} private record AssemblyResult(ConstructionScenarioItemEntity item,AssemblyDtos.EstimateResponse estimate){}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/scenario/src/main/java/com/sakhtyar/scenario/application/ScenarioService.java") $service

$controller = @'
package com.sakhtyar.scenario.api;
import static com.sakhtyar.scenario.api.ScenarioDtos.*; import com.sakhtyar.scenario.application.ScenarioService; import jakarta.validation.Valid; import java.util.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scenarios")
public class ScenarioController {
 private final ScenarioService service; public ScenarioController(ScenarioService service){this.service=service;}
 @GetMapping("/quality-packages") public List<PackageResponse> packages(){return service.packages();}
 @PostMapping("/quality-packages") public PackageResponse createPackage(@Valid @RequestBody UpsertPackageRequest r,Authentication a){return service.createPackage(r,a);}
 @PutMapping("/quality-packages/{id}") public PackageResponse updatePackage(@PathVariable UUID id,@Valid @RequestBody UpsertPackageRequest r){return service.updatePackage(id,r);}
 @GetMapping("/quality-packages/{id}/selections") public List<SelectionResponse> selections(@PathVariable UUID id){return service.selections(id);}
 @PostMapping("/quality-packages/{id}/selections") public SelectionResponse addSelection(@PathVariable UUID id,@Valid @RequestBody CreateSelectionRequest r){return service.addSelection(id,r);}
 @DeleteMapping("/quality-packages/{id}/selections/{selectionId}") public void removeSelection(@PathVariable UUID id,@PathVariable UUID selectionId){service.removeSelection(id,selectionId);}
 @GetMapping public List<ScenarioResponse> scenarios(){return service.scenarios();}
 @PostMapping public ScenarioResponse create(@Valid @RequestBody UpsertScenarioRequest r,Authentication a){return service.createScenario(r,a);}
 @PutMapping("/{id}") public ScenarioResponse update(@PathVariable UUID id,@Valid @RequestBody UpsertScenarioRequest r){return service.updateScenario(id,r);}
 @PostMapping("/{id}/status") public ScenarioResponse status(@PathVariable UUID id,@Valid @RequestBody ChangeStatusRequest r){return service.changeStatus(id,r);}
 @GetMapping("/{id}/items") public List<ScenarioItemResponse> items(@PathVariable UUID id){return service.items(id);}
 @PostMapping("/{id}/items") public ScenarioItemResponse addItem(@PathVariable UUID id,@Valid @RequestBody CreateScenarioItemRequest r){return service.addItem(id,r);}
 @DeleteMapping("/{id}/items/{itemId}") public void removeItem(@PathVariable UUID id,@PathVariable UUID itemId){service.removeItem(id,itemId);}
 @PostMapping("/{id}/calculate") public SnapshotResponse calculate(@PathVariable UUID id,@Valid @RequestBody CalculateScenarioRequest r,Authentication a){return service.calculate(id,r,a);}
 @GetMapping("/{id}/snapshots") public List<SnapshotResponse> snapshots(@PathVariable UUID id){return service.snapshots(id);}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/scenario/src/main/java/com/sakhtyar/scenario/api/ScenarioController.java") $controller

$test = @'
package com.sakhtyar.scenario.domain;
import static org.junit.jupiter.api.Assertions.assertEquals; import org.junit.jupiter.api.Test;
class ScenarioEnumsTest {
 @Test void qualityLevels(){assertEquals(QualityLevel.STANDARD,QualityLevel.valueOf("STANDARD"));assertEquals(QualityLevel.LUXURY,QualityLevel.valueOf("LUXURY"));}
 @Test void scenarioStates(){assertEquals(ScenarioStatus.DRAFT,ScenarioStatus.valueOf("DRAFT"));assertEquals(ScenarioStatus.READY,ScenarioStatus.valueOf("READY"));}
}
'@
Write-Utf8NoBom (Join-Path $RepoRoot "backend/modules/scenario/src/test/java/com/sakhtyar/scenario/domain/ScenarioEnumsTest.java") $test

Write-Step "7/8 - Writing Phase 5.6 documentation"

Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.6-quality-packages-scenarios.md") @'
# Phase 5.6 — Quality Packages & Construction Scenarios

Phase 5.6 adds reusable quality packages and project construction scenarios on top of Phase 5.5 assemblies.

Quality levels:
- ECONOMY
- STANDARD
- PREMIUM
- LUXURY
- CUSTOM

Quality packages contain named slots pointing to canonical assemblies. Construction scenarios reference an optional package, structural system, assumptions and explicit assembly quantities.

Scenario item sources:
- PACKAGE
- OVERRIDE
- MANUAL

Scenario lifecycle:
- DRAFT
- READY
- ARCHIVED

A READY scenario must be active and contain at least one enabled item.

Scenario costing reuses the Phase 5.5 Assembly & Cost Engine. Every scenario cost snapshot stores the exact assembly estimate IDs used, preserving the lineage:

scenario -> scenario item -> assembly estimate -> estimate line -> material price aggregate.

This phase does not add frontend/UI screens.

Deferred:
- automatic quantity takeoff
- labor/equipment/subcontractor pricing
- package auto-expansion into project quantities
- currency/inflation conversion
- urban/regulation validation
- feasibility and sensitivity analysis
'@

Write-Utf8NoBom (Join-Path $RepoRoot "docs/phase-5/phase-5.6-scenario-api.md") @'
# Phase 5.6 — Scenario API

Base path: `/api/v1/scenarios`

Quality packages:
- GET `/quality-packages`
- POST `/quality-packages`
- PUT `/quality-packages/{id}`
- GET `/quality-packages/{id}/selections`
- POST `/quality-packages/{id}/selections`
- DELETE `/quality-packages/{id}/selections/{selectionId}`

Scenarios:
- GET `/api/v1/scenarios`
- POST `/api/v1/scenarios`
- PUT `/api/v1/scenarios/{id}`
- POST `/api/v1/scenarios/{id}/status`

Items:
- GET `/api/v1/scenarios/{id}/items`
- POST `/api/v1/scenarios/{id}/items`
- DELETE `/api/v1/scenarios/{id}/items/{itemId}`

Cost:
- POST `/api/v1/scenarios/{id}/calculate`
- GET `/api/v1/scenarios/{id}/snapshots`
'@

Write-Step "8/8 - Running Maven tests"

if (-not $SkipTests) {
    Push-Location (Join-Path $RepoRoot "backend")
    try {
        & mvn test
        if ($LASTEXITCODE -ne 0) { throw "Maven tests failed." }
    } finally { Pop-Location }
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
Write-Host "Phase 5.6 applied on v2 successfully." -ForegroundColor Green
Write-Host "Base SHA used: $baseSha"
Write-Host ""
Write-Host "Next validation:" -ForegroundColor Yellow
Write-Host "1) Run backend from IntelliJ and confirm Flyway V14 applies."
Write-Host "2) Confirm the application stays running."
Write-Host "3) This phase is backend-only; no frontend files should change."
Write-Host "4) After runtime validation, commit and push directly to v2."
