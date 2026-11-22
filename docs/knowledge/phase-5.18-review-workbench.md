# SakhtYar Knowledge Review Workbench — Phase 5.18

## Why this change
The extraction pipeline currently emits *candidate* numeric observations, not verified urban planning rules. A page number, footnote number, year or value in a training document can be extracted as an `OBSERVED` numeric candidate. Even evidence marked `SUPPORTED` only proves that the snippet was present in the page, not that it represents an applicable legal requirement.

## Source-document categories
An authorized administrator must explicitly assign each document one of:
- `LAW` — binding legal material such as approved acts, ordinances, municipal regulation documents or validated editions of urban planning documents.
- `EDUCATION` — textbooks, tutorials, presentations and training materials.
- `TECHNICAL` — engineering standards, construction manuals, technical specifications.
- `REFERENCE` — datasets, catalogs, background references.
- `OTHER` — everything else.

The absence of an explicit human classification is `UNCLASSIFIED`, **not** `LAW`. Folder names (`detected_category`) are merely contextual clues. Classification is recorded with reviewer identity and timestamp, and an append-only event is recorded on each change.

## User flow
1. Allow a document extraction to complete. Do not restart or stop an active run to deploy this feature.
2. Open **Knowledge Center → Document Classification**; search for the uploaded source and inspect filename, path, SHA-256 and page count.
3. If the source is genuinely an authoritative legal document, choose **Laws and regulations** and add a justification. Educational manuals belong to `EDUCATION` even when they quote regulations.
4. Open **Extracted Rules**. Filter by `CANDIDATE`, `VERIFIED`, `REJECTED`, or document category; read the exact evidence text and PDF page. These are observed numeric statements, **not** automatically valid urban planning rules.
5. Open **Review and Approval**, click each eligible candidate and verify authoritative source, governing territory, numeric subject, inequality, unit, exception, date of applicability, and physical PDF page. Never bulk-approve.
6. Review decisions are written through the existing `/api/v1/knowledge/admin/governance/rules/{id}/review` endpoint, with identity and revision events. The Backend now blocks an `APPROVED` decision unless its source document is explicitly `LAW`.
7. Release publishing is unchanged and continues to require an individual decision for all rules. Do not publish until an independent legal review and QA audit have passed.

## Data model and HTTP API
Flyway V47 creates `knowledge_document_classification` and append-only `knowledge_document_classification_event` without changing existing rules.
- `GET /api/v1/knowledge/admin/catalog/documents` — paginated supervised classification inventory
- `GET /api/v1/knowledge/admin/catalog/rules` — paginated extracted numeric statements, current review status, source page and document hash
- `POST /api/v1/knowledge/admin/catalog/documents/{id}/classification` — manual category decision with optional explanation
All endpoints require `USER_MANAGE` and the ordinary SPA CSRF protection on writes.

## Known limitations and blockers
- The `KNOWLEDGE_EXTRACTION` implementation uses regex-based number recognition with an 80-number-per-page cap. It does not independently resolve legal clause boundaries, condition/exception logic or table row-to-column alignment.
- The PDF's R/S/M/G zoning tables need visual table-structure validation, not simply lexical overlap. For the 1398 Tehran detailed-plan document, critical tables appear on PDF pages 9, 14, 18 and 22.
- The existing publishing gate requires a decision for *every* candidate rule in the version; it does not implement category-specific release composition. This remains a workflow improvement, not a reason to auto-approve a non-legal candidate.
- Source document classification indicates document type, **not** current legal validity, amendment status or authority. Approvers still must verify legal currency against the relevant authority.
- Running database audit ZIP data is required before concluding extraction fidelity or revising the numeric extraction engine. Changes to the extraction engine are intentionally deferred until its actual measured errors are known.

## Acceptance checks
- Document classification is visible in its own tab, persists after refresh and keeps audit events.
- An unclassified or educational source cannot produce an APPROVED knowledge rule through the Governance API (403/400, depending on security/error mapping).
- Candidate rules can be searched and filtered with pagination; original source/page/hash and evidence snippet are visible.
- Ordinary users without `USER_MANAGE` cannot use the new API endpoints even if they call HTTP directly.
- Existing in-progress pipeline resumes and current database content are preserved.
- `mvn clean verify`, `npm run build`, `git diff --check` pass locally.
