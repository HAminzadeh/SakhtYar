# SakhtYar Phase 5.1 â€” Core Domain Foundation

## Goal

Phase 5.1 establishes the stable data and contract foundation for Phase 5 without breaking the APIs implemented in Phases 0â€“4.

`construction_case` remains the project aggregate for backward compatibility. It is extended with a stable project code, workflow stage, schema version, currency, and JSON metadata.

## New modules

- `sakhtyar-builder`
- `sakhtyar-contract`
- `sakhtyar-knowledge`
- `sakhtyar-analysis`

## New database concepts

### Builder
- `builder_profile`
- `project_builder`

### Contract foundation
- `contract_record`

This is intentionally only the contract header/foundation. Clauses, parties, amendments, disputes and arbitration are added in later Phase 5 stages.

### Knowledge source foundation
- `knowledge_source`

Detailed terminology, regulations, materials, crawler candidates and approval workflow are added in Phase 5.2+.

### Analysis reproducibility
- `analysis_snapshot`
- `data_lineage`

Every important future calculation can freeze its input/result and retain the URL/entity that contributed to each output.

### Document metadata preparation
`case_document` now supports:
- categories
- generic entity relations
- document version numbers
- parent versions
- original source URL
- JSON metadata

The existing MinIO implementation remains operational. Storage-provider abstraction is implemented in Phase 5.15.

## Canonical agent contract

Shared Kernel now contains canonical request/response records and JSON Schemas.

Important architectural rule:

1. deterministic extraction/rules run first,
2. Persian Agent is used only for unresolved language,
3. specialist agents communicate through canonical structured JSON,
4. no specialist agent is required to understand Persian.

## Compatibility

No current REST API is renamed or removed in Phase 5.1.
Existing `construction_case`, `property`, `property_owner`, agent, identity and document records remain valid.