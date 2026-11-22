# SakhtYar Legal NLP V51 — Hazm + local Qwen

## Intent

Replaces the old **one-number = one-rule** extraction for **new or retried (failed) knowledge runs** with source-preserving segmentation into articles, notes and clauses. The `full_text` of each segment originates from normalized source pages without a 70-character cut. `PASSAGE` rows remain available for documents with no recognized legal provisions; they are not automatically turned into rules. The V51 installer itself does not delete historical records. A separate, explicitly requested reset step now backs up PostgreSQL and clears the old knowledge-processing dataset before a fresh extraction.

## Dependencies and licensing

* Hazm 0.12.1: MIT license, https://github.com/roshan-research/hazm . Requirements: Python >=3.12,<3.14. No pretrained models are needed for `SentenceTokenizer`.
* Downloads are saved as installable wheels and source archives in `python/vendor/hazm-wheelhouse`. The isolated venv is at `.local/venv-legal-nlp` (not for committing).
* Qwen3.5:4b via already running Ollama `http://127.0.0.1:11434`; no model weights are committed.
* Model metadata is a **proposal**, never approval. Evidence quotations must exactly occur in source text. All rules remain `CANDIDATE`.

## Runtime

* Database migration V50 creates `knowledge_legal_segment`. Restart backend to apply the migration.
* A **new** Pipeline run uses the new logic at stage `KNOWLEDGE_EXTRACTION`. When the requested reset is used, historical checkpoints are intentionally cleared along with the invalid extracted dataset. A new execution must be started.
* Default Qwen metadata calls are capped at 8 per execution for predictable resource use. Remaining segments explicitly state `NOT_EVALUATED_LIMIT`.
* Configure `SAKHTYAR_LEGAL_MAX_MODEL_CALLS`, `SAKHTYAR_LEGAL_MODEL`, `SAKHTYAR_OLLAMA_URL` in the backend process environment before startup if desired.
* The UI tab `مواد و بندهای استخراج‌شده` lists exact source text; model labels are visibly distinguished from legal approval.
* For testing against an existing execution BEFORE using the reset, use the install script's `-BenchmarkOnly` option. The benchmark never mutates PostgreSQL.

## Known limitations

* PDF reading order, damaged Persian glyphs, multi-column tables and numbered headings split across lines can still prevent accurate article boundary detection.
* Cross-page segments are preserved when headings are present, but absence of a recognizable heading results in page-level `PASSAGE` rows for human classification.
* Qwen only classifies a bounded number of segments; it does not repair corrupt OCR or invent missing source material.
* An article boundary detector cannot certify jurisdiction, validity dates, exceptions or legal effect. Separate expert governance is still required.
* Manual OCR review remains independent of legal validation.
* Benchmark/Build success does not imply that an untested production deployment or every legal PDF is accurate.

## Safety

The installer creates a source backup and never clears the database, publishes releases, pushes code or triggers a pipeline run. Flyway executes V50 only during a later backend restart. Pending OCR reviews are not bypassed.

## Requested cleanup of invalid historical knowledge

The wrapper `Run-SakhtYar-Legal-NLP-V51-And-Reset.ps1` runs the installer, then executes a separate transactional reset. STOP the Java Backend on port 8080 first; keep PostgreSQL Docker running. The reset creates a full PostgreSQL custom-format backup in Downloads, checks foreign keys and refuses to clear published knowledge releases. It truncates only `knowledge_*`, `construction_catalog_*`, `construction_concept_*`, the `construction_concept` table and `structured_knowledge_version` (all under the public schema). It never runs `CASCADE`, does not delete original source PDFs, accounts, other modules, or Flyway history. A new execution is required after restarting and applying V50. The backup is sensitive; do not upload it without reviewing its contents.
