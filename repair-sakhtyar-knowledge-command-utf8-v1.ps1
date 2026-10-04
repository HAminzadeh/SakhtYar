param(
    [string]$RepoRoot = ".",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Step([string]$Text) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor DarkCyan
    Write-Host " $Text" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor DarkCyan
}

function EnsureDir([string]$Path) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function WriteUtf8([string]$Path, [string]$Content) {
    EnsureDir (Split-Path -Parent $Path)
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        [System.Text.UTF8Encoding]::new($false)
    )
}

$RepoRootResolved = (Resolve-Path $RepoRoot).Path
Set-Location $RepoRootResolved

Step "SakhtYar Knowledge Command UTF-8 Repair - V1"
Write-Host "Repository: $RepoRootResolved"
Write-Host ("PowerShell: " + $PSVersionTable.PSVersion.ToString())

$servicePath = Join-Path $RepoRootResolved "backend\modules\knowledge\src\main\java\com\sakhtyar\knowledge\application\KnowledgeAdminService.java"
$runbookPath = Join-Path $RepoRootResolved "docs\knowledge\runbooks\knowledge-extraction-v1.md"
$userGuidePath = Join-Path $RepoRootResolved "docs\knowledge\user-guide\knowledge-administration-center.fa.md"

foreach ($p in @($servicePath, $runbookPath, $userGuidePath)) {
    if (-not (Test-Path $p)) {
        throw "Required file not found: $p"
    }
}

Step "1/4 - Backup"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $RepoRootResolved ".local\patch-backups\knowledge-command-utf8-v1-$stamp"
EnsureDir $backupRoot

Copy-Item $servicePath (Join-Path $backupRoot "KnowledgeAdminService.java") -Force
Copy-Item $runbookPath (Join-Path $backupRoot "knowledge-extraction-v1.md") -Force
Copy-Item $userGuidePath (Join-Path $backupRoot "knowledge-administration-center.fa.md") -Force

Write-Host "Backup: $backupRoot" -ForegroundColor Green

Step "2/4 - Rewrite KnowledgeAdminService with clean UTF-8 command text"

$service = @'
package com.sakhtyar.knowledge.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeAdminService {

    private static final String PROFILE_CODE = "SAKHTYAR_INTERNAL_KNOWLEDGE";
    private static final DateTimeFormatter RUN_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    public KnowledgeAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile());
        result.put("activeDataset", activeDataset());
        result.put("latestDataset", latestDataset());
        result.put("counts", Map.of(
                "datasets", count("knowledge_dataset"),
                "runs", count("knowledge_pipeline_run"),
                "pendingReview", scalar("""
                    select count(*) from knowledge_review_item
                    where status = 'PENDING_REVIEW'
                    """),
                "openConflicts", scalar("""
                    select count(*) from knowledge_conflict
                    where status = 'OPEN'
                    """),
                "openFeedback", scalar("""
                    select count(*) from knowledge_feedback
                    where status = 'OPEN'
                    """),
                "goldenQuestions", scalar("""
                    select count(*) from knowledge_golden_question
                    where enabled = true
                    """)
        ));
        result.put("recentRuns", recentRuns(20));
        result.put("health", health());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile() {
        return jdbc.query("""
            select id, code, name, repository, branch_name, source_path,
                   runbook_version, runbook_path, default_mode, enabled,
                   settings, created_at, updated_at
            from knowledge_pipeline_profile
            where code = ?
            """, rs -> {
                if (!rs.next()) {
                    throw new IllegalStateException("Knowledge pipeline profile is not initialized.");
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getObject("id"));
                row.put("code", rs.getString("code"));
                row.put("name", rs.getString("name"));
                row.put("repository", rs.getString("repository"));
                row.put("branch", rs.getString("branch_name"));
                row.put("sourcePath", rs.getString("source_path"));
                row.put("runbookVersion", rs.getString("runbook_version"));
                row.put("runbookPath", rs.getString("runbook_path"));
                row.put("defaultMode", rs.getString("default_mode"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("settings", rs.getString("settings"));
                row.put("createdAt", instant(rs.getTimestamp("created_at")));
                row.put("updatedAt", instant(rs.getTimestamp("updated_at")));
                return row;
            }, PROFILE_CODE);
    }

    @Transactional
    public Map<String, Object> updateProfile(
            String repository,
            String branch,
            String sourcePath,
            String runbookVersion,
            String runbookPath,
            String defaultMode
    ) {
        String mode = normalizeMode(defaultMode);
        jdbc.update("""
            update knowledge_pipeline_profile
            set repository = ?,
                branch_name = ?,
                source_path = ?,
                runbook_version = ?,
                runbook_path = ?,
                default_mode = ?,
                updated_at = now()
            where code = ?
            """,
            required(repository, "repository"),
            required(branch, "branch"),
            required(sourcePath, "sourcePath"),
            required(runbookVersion, "runbookVersion"),
            required(runbookPath, "runbookPath"),
            mode,
            PROFILE_CODE
        );
        return profile();
    }

    @Transactional
    public Map<String, Object> generateCommand(
            String requestedMode,
            String baselineCommit,
            Authentication authentication
    ) {
        Map<String, Object> p = profile();
        String mode = normalizeMode(requestedMode == null
                ? String.valueOf(p.get("defaultMode"))
                : requestedMode);

        Map<String, Object> previous = latestDataset();
        String previousVersion = previous == null
                ? null
                : Objects.toString(previous.get("datasetVersion"), null);

        Instant now = Instant.now();
        String runCode = "KB-" + RUN_STAMP.format(now) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        String command = buildCommand(
                runCode,
                mode,
                p,
                blankToNull(baselineCommit),
                previousVersion
        );

        UUID profileId = UUID.fromString(p.get("id").toString());
        String actor = authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();

        jdbc.update("""
            insert into knowledge_pipeline_run(
                id, run_code, profile_id, mode, status, stage,
                baseline_commit, previous_dataset_version,
                command_text, runbook_version, requested_by, created_at
            )
            values (?, ?, ?, ?, 'COMMAND_GENERATED', 'COMMAND_GENERATED',
                    ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            runCode,
            profileId,
            mode,
            blankToNull(baselineCommit),
            previousVersion,
            command,
            Objects.toString(p.get("runbookVersion")),
            actor,
            java.sql.Timestamp.from(now)
        );

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("runCode", runCode);
        result.put("mode", mode);
        result.put("command", command);
        result.put("previousDatasetVersion", previousVersion);
        result.put("baselineCommit", blankToNull(baselineCommit));
        result.put("runbookVersion", p.get("runbookVersion"));
        result.put("createdAt", now);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentRuns(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
            select run_code, mode, status, stage, baseline_commit,
                   source_commit, previous_dataset_version,
                   target_dataset_version, runbook_version,
                   requested_by, created_at, started_at, finished_at,
                   error_message
            from knowledge_pipeline_run
            order by created_at desc
            limit ?
            """, (rs, rowNum) -> {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("runCode", rs.getString("run_code"));
                row.put("mode", rs.getString("mode"));
                row.put("status", rs.getString("status"));
                row.put("stage", rs.getString("stage"));
                row.put("baselineCommit", rs.getString("baseline_commit"));
                row.put("sourceCommit", rs.getString("source_commit"));
                row.put("previousDatasetVersion", rs.getString("previous_dataset_version"));
                row.put("targetDatasetVersion", rs.getString("target_dataset_version"));
                row.put("runbookVersion", rs.getString("runbook_version"));
                row.put("requestedBy", rs.getString("requested_by"));
                row.put("createdAt", instant(rs.getTimestamp("created_at")));
                row.put("startedAt", instant(rs.getTimestamp("started_at")));
                row.put("finishedAt", instant(rs.getTimestamp("finished_at")));
                row.put("errorMessage", rs.getString("error_message"));
                return row;
            }, safeLimit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> command(String runCode) {
        return jdbc.query("""
            select run_code, command_text, mode, status, runbook_version, created_at
            from knowledge_pipeline_run
            where run_code = ?
            """, rs -> {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Knowledge run not found: " + runCode);
                }
                return Map.of(
                        "runCode", rs.getString("run_code"),
                        "command", rs.getString("command_text"),
                        "mode", rs.getString("mode"),
                        "status", rs.getString("status"),
                        "runbookVersion", rs.getString("runbook_version"),
                        "createdAt", instant(rs.getTimestamp("created_at"))
                );
            }, runCode);
    }

    private Map<String, Object> activeDataset() {
        return datasetBy("""
            select dataset_version, taxonomy_version, schema_version,
                   source_commit, status, statistics, generated_at,
                   imported_at, activated_at, created_at
            from knowledge_dataset
            where status = 'ACTIVE'
            order by activated_at desc nulls last
            limit 1
            """);
    }

    private Map<String, Object> latestDataset() {
        return datasetBy("""
            select dataset_version, taxonomy_version, schema_version,
                   source_commit, status, statistics, generated_at,
                   imported_at, activated_at, created_at
            from knowledge_dataset
            order by created_at desc
            limit 1
            """);
    }

    private Map<String, Object> datasetBy(String sql) {
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return null;
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("datasetVersion", rs.getString("dataset_version"));
            row.put("taxonomyVersion", rs.getString("taxonomy_version"));
            row.put("schemaVersion", rs.getString("schema_version"));
            row.put("sourceCommit", rs.getString("source_commit"));
            row.put("status", rs.getString("status"));
            row.put("statistics", rs.getString("statistics"));
            row.put("generatedAt", instant(rs.getTimestamp("generated_at")));
            row.put("importedAt", instant(rs.getTimestamp("imported_at")));
            row.put("activatedAt", instant(rs.getTimestamp("activated_at")));
            row.put("createdAt", instant(rs.getTimestamp("created_at")));
            return row;
        });
    }

    private Map<String, Object> health() {
        LinkedHashMap<String, Object> health = new LinkedHashMap<>();
        health.put("pendingReviews", scalar("""
            select count(*) from knowledge_review_item where status = 'PENDING_REVIEW'
            """));
        health.put("openConflicts", scalar("""
            select count(*) from knowledge_conflict where status = 'OPEN'
            """));
        health.put("unresolvedFeedback", scalar("""
            select count(*) from knowledge_feedback where status = 'OPEN'
            """));
        health.put("watchedSources", scalar("""
            select count(*) from knowledge_source_policy where watch_for_updates = true
            """));
        health.put("enabledGoldenQuestions", scalar("""
            select count(*) from knowledge_golden_question where enabled = true
            """));
        health.put("latestCitationCoverage", jdbc.query("""
            select citation_coverage
            from knowledge_quality_run
            where citation_coverage is not null
            order by created_at desc
            limit 1
            """, rs -> rs.next() ? rs.getBigDecimal(1) : null));
        return health;
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0L : value;
    }

    private long scalar(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static String buildCommand(
            String runCode,
            String mode,
            Map<String, Object> profile,
            String baselineCommit,
            String previousDatasetVersion
    ) {
        String baseline = baselineCommit == null ? "(auto-detect)" : baselineCommit;
        String previous = previousDatasetVersion == null ? "(none - first dataset)" : previousDatasetVersion;

        return """
                SakhtYar Knowledge Extraction را اجرا کن.

                Run ID: %s
                Repository: %s
                Branch: %s
                Mode: %s
                Source Path: %s
                Runbook Version: %s
                Runbook Path: %s
                Previous Dataset: %s
                Baseline Commit: %s

                ابتدا Runbook ذخیره‌شده پروژه را از Repository بخوان و دقیقاً طبق آن عمل کن.
                تمام PDF/PPT/PPTXها و منابع NEW یا CHANGED را خودت کامل بخوان و تحلیل کن.
                OCR در صورت ضرورت، نرمال‌سازی فارسی، Classification، Summary، Semantic Chunking، Topic/Entity/Relation Extraction،
                Evidence، Knowledge Candidate، Rule Candidate، Conflict Detection، Document Diff، Impact Analysis و Embedding Generation
                سمت خودت انجام شود.
                هیچ OCR/LLM/classification/summarization/embedding generation به زمان اجرای محلی SakhtYar منتقل نشود.

                Exact Duplicateها را با hash قطعی شناسایی کن؛ Canonical File و duplicate-manifest امن بساز.
                فایل مشابه با محتوای متفاوت هرگز Duplicate محسوب نشود. مسیرها و Categoryهای قبلی در metadata حفظ شوند.

                Knowledge Dataset versioned + Prepared Import/Upgrade PowerShell Script تولید کن.
                Dataset باید provenance، authority، trust، jurisdiction، temporal versioning، citation coverage، bulk review، conflicts، source priority، staleness،
                feedback، usage analytics، golden questions، search regression cases، agent snapshots، pinned knowledge، security classification، manual override audit و DR metadata را پوشش دهد.
                Dataset جدید را خودکار ACTIVE نکن.

                در پایان NEW/CHANGED/REMOVED/UNCHANGED، duplicates، documents by domain/authority/confidence، chunks، topics، entities، candidates، rules، conflicts، diffs، impacts و errors را گزارش کن.
                """.formatted(
                runCode,
                profile.get("repository"),
                profile.get("branch"),
                mode,
                profile.get("sourcePath"),
                profile.get("runbookVersion"),
                profile.get("runbookPath"),
                previous,
                baseline
        );
    }

    private static String normalizeMode(String mode) {
        return "FULL".equalsIgnoreCase(mode) ? "FULL" : "INCREMENTAL";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
'@

WriteUtf8 $servicePath $service

Step "3/4 - Rewrite Persian docs as clean UTF-8"

$runbook = @'
# SakhtYar Knowledge Extraction Runbook v1.0

## هدف
این Runbook برای ساخت یا به‌روزرسانی نسخه‌ای Knowledge Dataset ساخت‌یار از منابع داخلی Repository و بعداً crawlerها و APIهای رسمی است.

## منبع اصلی
- Repository: `HAminzadeh/SakhtYar`
- Branch: `v2`
- Source Path: `DocumentationOfLawsAndRegulations`

## اصل مهم
تحلیل سنگین محتوا باید خارج از اجرای محلی SakhtYar انجام شود.
اجرای محلی نباید OCR، LLM classification، LLM summarization یا embedding generation انجام دهد.
فقط باید بسته آماده را validate و import کند.
'@

$userGuide = @'
# راهنمای کاربری مرکز مدیریت دانش ساخت‌یار

## نکته اصلی
این بخش فرمان استاندارد استخراج دانش را تولید می‌کند.
خود برنامه نباید PDFها را با LLM یا OCR تحلیل کند.
Dataset آماده بعداً import می‌شود.

## روند
1. به تب ساخت بروید.
2. حالت Full یا Incremental را انتخاب کنید.
3. روی «تولید دستور استخراج» بزنید.
4. فرمان را برای ChatGPT بفرستید.
5. Dataset و اسکریپت Import را دریافت و اجرا کنید.
6. Review و Validation را بررسی کنید.
7. در پایان Dataset را Activate کنید.
'@

WriteUtf8 $runbookPath $runbook
WriteUtf8 $userGuidePath $userGuide

Step "4/4 - Build"

if (-not $SkipBuild) {
    Push-Location (Join-Path $RepoRootResolved "backend")
    try {
        if (Test-Path ".\mvnw.cmd") {
            & .\mvnw.cmd -q -DskipTests compile
        } elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
            & mvn -q -DskipTests compile
        } else {
            throw "Neither mvnw.cmd nor mvn was found."
        }
        if ($LASTEXITCODE -ne 0) { throw "Backend compile failed." }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Build skipped by -SkipBuild." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Knowledge command UTF-8 repair completed." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Important:"
Write-Host "  1. Existing previously generated run commands in the database may still contain mojibake."
Write-Host "  2. Generate a NEW command from the Build tab after this patch."
Write-Host "  3. If needed, you can ignore or manually clean old history rows."
Write-Host ""
Write-Host "Backup: $backupRoot"
