# Phase 5.2 â€” Knowledge Platform

## Purpose
Phase 5.2 turns the Phase 5.1 `knowledge_source` foundation into a reusable reviewed knowledge platform for SakhtYar.

## Principles
- Human-reviewed knowledge is authoritative.
- LLM/crawler/import outputs enter as candidates only.
- No candidate is auto-promoted into authoritative knowledge.
- Mutable knowledge keeps immutable revision history.
- Every knowledge item may reference a source and provenance URL.
- Persian aliases are stored in both original and normalized form.
- This phase is backend-first; the full admin review UI is scheduled for Phase 5.18.

## Data model
- `knowledge_term`
- `knowledge_term_alias`
- `knowledge_relation`
- `knowledge_candidate`
- `knowledge_term_revision`
- existing `knowledge_source`

## Review states
- `PENDING_REVIEW`
- `APPROVED`
- `REJECTED`
- `SUPERSEDED`
- `ARCHIVED`

## Candidate origins
- `USER_INPUT`
- `PERSIAN_AGENT`
- `CRAWLER`
- `IMPORT`
- `ADMIN`
- `SYSTEM`

## Learning-loop boundary
The Persian Agent, crawler and imports may submit candidates. Approval remains a separate explicit action. Phase 5.13 will build the broader learning loop on top of this foundation.

## Versioning
Each term create/update stores a JSON snapshot in `knowledge_term_revision`. Revision history is append-only.

## Migration
`V10__phase5_knowledge_platform.sql`