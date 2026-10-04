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

    @Transactional(readOnly = true)
    public Map<String, Object> exportKit() {
        Map<String, Object> p = profile();
        String sourcePath = Objects.toString(p.get("sourcePath"));
        String repository = Objects.toString(p.get("repository"));
        String branch = Objects.toString(p.get("branch"));
        String runbookVersion = Objects.toString(p.get("runbookVersion"));

        String scriptName = "export-sakhtyar-knowledge-corpus.ps1";
        String outputDir = ".local\\knowledge-export";
        String expectedZip = outputDir + "\\sakhtyar-knowledge-corpus.zip";

        String runCommand = """
                powershell -ExecutionPolicy Bypass `
                  -File .\\%s `
                  -RepoRoot . `
                  -SourcePath "%s"
                """.formatted(scriptName, sourcePath);

        String sendCommand = """
                SakhtYar Knowledge Corpus Export Package را پردازش کن.

                Repository: %s
                Branch: %s
                Source Path: %s
                Runbook Version: %s

                فایل ZIP ضمیمه‌شده خروجی مکانیکی Corpus است و شامل inventory،
                duplicate manifest، متن استخراج‌شده PDF/PPT/PPTX، page/slide JSONL،
                extraction summary و errors است.

                تمام تحلیل دانشی را خودت انجام بده:
                Classification، Summary، Semantic Chunking، Topics، Entities، Relations،
                Evidence، Knowledge Candidates، Rule Candidates، Conflicts، Document Diff،
                Impact Analysis و Embeddings.

                هیچ دانش یا Rule را صرفاً از filename نتیجه‌گیری نکن؛ متن واقعی استخراج‌شده را مبنا قرار بده.
                Exact Duplicateها را از duplicate-manifest بررسی کن و مسیر/categoryهای قبلی را حفظ کن.
                در پایان Knowledge Dataset versioned و Prepared Import/Upgrade PowerShell Script تولید کن.
                Dataset جدید را خودکار ACTIVE نکن.
                """.formatted(repository, branch, sourcePath, runbookVersion);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("scriptName", scriptName);
        result.put("script", buildCorpusExportScript());
        result.put("runCommand", runCommand);
        result.put("sendCommand", sendCommand);
        result.put("expectedZip", expectedZip);
        result.put("sourcePath", sourcePath);
        result.put("repository", repository);
        result.put("branch", branch);
        return result;
    }

    private static String buildCorpusExportScript() {
        return """
                param(
                    [string]$RepoRoot = ".",
                    [string]$SourcePath = "DocumentationOfLawsAndRegulations"
                )

                $ErrorActionPreference = "Stop"
                Set-StrictMode -Version Latest

                function Step([string]$Text) {
                    Write-Host ""
                    Write-Host "============================================================" -ForegroundColor DarkCyan
                    Write-Host " $Text" -ForegroundColor Cyan
                    Write-Host "============================================================" -ForegroundColor DarkCyan
                }

                $RepoRootResolved = (Resolve-Path $RepoRoot).Path
                Set-Location $RepoRootResolved

                $source = Join-Path $RepoRootResolved $SourcePath
                if (-not (Test-Path $source)) {
                    throw "Source path not found: $source"
                }

                $workRoot = Join-Path $RepoRootResolved ".local\\knowledge-export"
                $venv = Join-Path $workRoot ".venv"
                $pythonScript = Join-Path $workRoot "extract_corpus.py"
                $outDir = Join-Path $workRoot "sakhtyar-knowledge-corpus"
                $zipPath = Join-Path $workRoot "sakhtyar-knowledge-corpus.zip"

                New-Item -ItemType Directory -Path $workRoot -Force | Out-Null

                Step "1/5 - Python environment"

                $python = Get-Command python -ErrorAction SilentlyContinue
                if (-not $python) {
                    $python = Get-Command py -ErrorAction SilentlyContinue
                }
                if (-not $python) {
                    throw "Python 3 was not found. Install Python 3 and run again."
                }

                if (-not (Test-Path $venv)) {
                    if ($python.Name -eq "py.exe" -or $python.Name -eq "py") {
                        & $python.Source -3 -m venv $venv
                    } else {
                        & $python.Source -m venv $venv
                    }
                }

                $venvPython = Join-Path $venv "Scripts\\python.exe"
                if (-not (Test-Path $venvPython)) {
                    throw "Virtual environment Python not found: $venvPython"
                }

                & $venvPython -m pip install --disable-pip-version-check --quiet --upgrade pip
                & $venvPython -m pip install --disable-pip-version-check --quiet pymupdf python-pptx pillow pytesseract

                Step "2/5 - Write extraction engine"

                $py = @'
                import sys, os, re, json, hashlib, shutil, subprocess, traceback
                from pathlib import Path

                import fitz
                from pptx import Presentation

                try:
                    import pytesseract
                    from PIL import Image
                except Exception:
                    pytesseract = None
                    Image = None

                source = Path(sys.argv[1]).resolve()
                out = Path(sys.argv[2]).resolve()

                if out.exists():
                    shutil.rmtree(out)
                (out / "documents").mkdir(parents=True, exist_ok=True)
                (out / "pages").mkdir(parents=True, exist_ok=True)

                inventory = []
                hashes = {}
                errors = []

                def norm(s):
                    if not s:
                        return ""
                    return (
                        s.replace("ي","ی").replace("ى","ی").replace("ك","ک")
                         .replace("\\u200f","").replace("\\ufeff","")
                         .replace("\\r\\n","\\n").replace("\\r","\\n")
                    )

                def sha256(path):
                    h = hashlib.sha256()
                    with open(path, "rb") as f:
                        while True:
                            block = f.read(1024 * 1024)
                            if not block:
                                break
                            h.update(block)
                    return h.hexdigest()

                def safe_id(path, digest):
                    stem = re.sub(r'[^A-Za-z0-9._-]+', '_', path.name)[:80]
                    return f"{digest[:16]}__{stem}"

                def find_tesseract():
                    if pytesseract is None:
                        return False
                    candidates = [
                        shutil.which("tesseract"),
                        r"C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                        r"C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
                    ]
                    for c in candidates:
                        if c and Path(c).exists():
                            pytesseract.pytesseract.tesseract_cmd = str(c)
                            return True
                    return False

                HAS_TESSERACT = find_tesseract()

                def ocr_page(page):
                    if not HAS_TESSERACT or Image is None:
                        return None
                    pix = page.get_pixmap(matrix=fitz.Matrix(1.6, 1.6), alpha=False)
                    img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
                    try:
                        return pytesseract.image_to_string(img, lang="fas+eng")
                    except Exception:
                        try:
                            return pytesseract.image_to_string(img, lang="eng")
                        except Exception:
                            return None

                def extract_pdf(path):
                    pages = []
                    pdf = fitz.open(path)
                    for i, page in enumerate(pdf):
                        txt = norm(page.get_text("text"))
                        used_ocr = False
                        if len(re.sub(r'\\s+', '', txt)) < 35:
                            ot = ocr_page(page)
                            if ot:
                                ot = norm(ot)
                                if len(re.sub(r'\\s+', '', ot)) > len(re.sub(r'\\s+', '', txt)):
                                    txt = ot
                                    used_ocr = True
                        pages.append({"page": i + 1, "text": txt, "ocr": used_ocr})
                    pdf.close()
                    return pages

                def extract_pptx(path):
                    prs = Presentation(path)
                    pages = []
                    for idx, slide in enumerate(prs.slides, 1):
                        parts = []
                        for shape in slide.shapes:
                            if hasattr(shape, "text") and shape.text:
                                parts.append(shape.text)
                        try:
                            notes = slide.notes_slide
                            for shape in notes.shapes:
                                if hasattr(shape, "text") and shape.text and shape.text.strip():
                                    parts.append("[NOTES]\\n" + shape.text)
                        except Exception:
                            pass
                        pages.append({"page": idx, "text": norm("\\n".join(parts)), "ocr": False})
                    return pages

                def convert_old_ppt(path, docid):
                    temp = out / "_ppt-convert" / docid
                    temp.mkdir(parents=True, exist_ok=True)

                    # Prefer LibreOffice if available.
                    lo = shutil.which("soffice") or shutil.which("libreoffice")
                    if lo:
                        subprocess.run(
                            [lo, "--headless", "--convert-to", "pptx", "--outdir", str(temp), str(path)],
                            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
                        )
                        result = list(temp.glob("*.pptx"))
                        if result:
                            return result[0]

                    # Fallback to installed Microsoft PowerPoint via PowerShell COM.
                    ps = shutil.which("powershell") or shutil.which("pwsh")
                    if ps:
                        target = temp / (path.stem + ".pptx")
                        escaped_src = str(path).replace("'", "''")
                        escaped_dst = str(target).replace("'", "''")
                        cmd = (
                            "$ppt=New-Object -ComObject PowerPoint.Application;"
                            "$ppt.Visible=-1;"
                            f"$p=$ppt.Presentations.Open('{escaped_src}',0,0,0);"
                            f"$p.SaveAs('{escaped_dst}',24);"
                            "$p.Close();$ppt.Quit()"
                        )
                        subprocess.run([ps, "-NoProfile", "-Command", cmd], check=True)
                        if target.exists():
                            return target

                    raise RuntimeError("Old .ppt needs LibreOffice or Microsoft PowerPoint for conversion.")

                files = sorted(
                    p for p in source.rglob("*")
                    if p.is_file() and p.suffix.lower() in {".pdf", ".pptx", ".ppt"}
                )

                for idx, path in enumerate(files, 1):
                    digest = sha256(path)
                    rel = path.relative_to(source.parent).as_posix()
                    hashes.setdefault(digest, []).append(rel)
                    docid = safe_id(path, digest)

                    rec = {
                        "path": rel,
                        "filename": path.name,
                        "extension": path.suffix.lower(),
                        "size": path.stat().st_size,
                        "sha256": digest,
                        "doc_id": docid,
                    }

                    try:
                        # Only extract canonical bytes once; duplicate paths remain in the manifest.
                        if len(hashes[digest]) > 1:
                            rec["status"] = "DUPLICATE_SKIPPED"
                            inventory.append(rec)
                            print(f"[{idx}/{len(files)}] DUPLICATE {rel}")
                            continue

                        if path.suffix.lower() == ".pdf":
                            pages = extract_pdf(path)
                        elif path.suffix.lower() == ".pptx":
                            pages = extract_pptx(path)
                        else:
                            converted = convert_old_ppt(path, docid)
                            pages = extract_pptx(converted)

                        rec["page_count"] = len(pages)
                        rec["ocr_pages"] = sum(1 for p in pages if p["ocr"])
                        rec["text_chars"] = sum(len(p["text"]) for p in pages)
                        rec["status"] = "OK"

                        with open(out / "pages" / f"{docid}.jsonl", "w", encoding="utf-8") as f:
                            for page in pages:
                                f.write(json.dumps(page, ensure_ascii=False) + "\\n")

                        full_text = "\\n\\n".join(
                            f"===== PAGE/SLIDE {p['page']} =====\\n{p['text']}" for p in pages
                        )
                        (out / "documents" / f"{docid}.txt").write_text(full_text, encoding="utf-8")

                    except Exception as e:
                        rec["status"] = "FAILED"
                        rec["error"] = repr(e)
                        errors.append({
                            "path": rel,
                            "stage": "extract",
                            "error": repr(e),
                            "trace": traceback.format_exc(),
                        })

                    inventory.append(rec)
                    print(f"[{idx}/{len(files)}] {rec['status']} {rel}")

                duplicates = []
                for digest, paths in hashes.items():
                    if len(paths) > 1:
                        duplicates.append({
                            "sha256": digest,
                            "canonical": paths[0],
                            "duplicates": paths[1:],
                            "all_paths": paths,
                        })

                with open(out / "inventory.jsonl", "w", encoding="utf-8") as f:
                    for item in inventory:
                        f.write(json.dumps(item, ensure_ascii=False) + "\\n")

                (out / "duplicate-manifest.json").write_text(
                    json.dumps(duplicates, ensure_ascii=False, indent=2), encoding="utf-8"
                )
                (out / "errors.json").write_text(
                    json.dumps(errors, ensure_ascii=False, indent=2), encoding="utf-8"
                )

                summary = {
                    "total_entries": len(inventory),
                    "canonical_documents": sum(x.get("status") == "OK" for x in inventory),
                    "duplicates_skipped": sum(x.get("status") == "DUPLICATE_SKIPPED" for x in inventory),
                    "failed": sum(x.get("status") == "FAILED" for x in inventory),
                    "unique_sha256": len(hashes),
                    "duplicate_groups": len(duplicates),
                    "total_text_chars": sum(x.get("text_chars", 0) for x in inventory),
                    "ocr_pages": sum(x.get("ocr_pages", 0) for x in inventory),
                    "tesseract_available": HAS_TESSERACT,
                }

                (out / "extraction-summary.json").write_text(
                    json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
                )

                manifest = {
                    "format": "sakhtyar-knowledge-corpus-export",
                    "format_version": "1.0",
                    "source_path": str(source),
                    "summary": summary,
                    "contains_ai_output": False,
                    "contains_embeddings": False,
                }
                (out / "manifest.json").write_text(
                    json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
                )

                print(json.dumps(summary, ensure_ascii=False, indent=2))
                '@

                [IO.File]::WriteAllText($pythonScript, $py, [Text.UTF8Encoding]::new($false))

                Step "3/5 - Extract canonical documents"

                if (Test-Path $outDir) {
                    Remove-Item $outDir -Recurse -Force
                }

                & $venvPython $pythonScript $source $outDir
                if ($LASTEXITCODE -ne 0) {
                    throw "Corpus extraction failed."
                }

                Step "4/5 - Create ZIP"

                if (Test-Path $zipPath) {
                    Remove-Item $zipPath -Force
                }

                Compress-Archive -Path (Join-Path $outDir "*") -DestinationPath $zipPath -CompressionLevel Optimal

                Step "5/5 - Done"

                $summaryPath = Join-Path $outDir "extraction-summary.json"
                if (Test-Path $summaryPath) {
                    Get-Content $summaryPath -Raw -Encoding UTF8 | Write-Host
                }

                Write-Host ""
                Write-Host "ZIP ready:" -ForegroundColor Green
                Write-Host $zipPath -ForegroundColor Green
                Write-Host ""
                Write-Host "Send this ZIP together with the command generated in SakhtYar Knowledge Center." -ForegroundColor Cyan
                """;
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