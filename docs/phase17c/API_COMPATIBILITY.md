# API / UI / Schema Compatibility — Phase17C

- Existing `/api/v1/regulations/evaluate`, `/api/v1/regulations/properties/{propertyId}/evaluations` and `/api/v1/feasibility/assessments` DTO structures remain unchanged.
- New `UrbanEvaluation.inputSnapshot` JSON metadata fields: `propertyUpdatedAt`, `scenarioUpdatedAt` (ISO Instant strings). Both are recorded only when present; old rows remain immutable.
- Feasibility `create` now returns HTTP 409 Conflict if stale / missing snapshot provenance; it will **not** silently promote outdated rows.
- Current release provenance page cannot be inferred from `UrbanEvaluationResult.details`; no invented citation URL or PDF preview is introduced.
- `RegulationCandidateBridgeService.candidates` existing GET is reused in admin Knowledge page; the new UI is read-only.
- No migration is added. No production database is changed by installer.
- To avoid stale React Query collisions, urban history keys include `caseId`, `propertyId`, `propertyUpdatedAt`; case-specific keys are invalidated after evaluation.
- Global ConstructionScenario has no ownership ID; it is treated as shared scenario; version timestamps must match but per-case ownership is a separate blocked design decision.
