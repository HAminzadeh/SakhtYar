# Phase 04 -> downstream business process handoff

Do not convert E2E-04/E2E-07..13, or any other business scenario, to PASSED from source-only contracts.

1. `DatasetPackageFixture`: seed exclusively with synthetic ZIP in a verified restricted TEST session; compare extract/checksum and simulate partial import rollback.
2. `OcrPageArtifact`: use provided exact UTF-8 text and source/page SHA; version fake vs real OCR engine; no legal claims from fake output.
3. `PipelineCheckpoint`: synthetic in-memory model demonstrates idempotent event contract only; persistent queue, poison-job, cancellation and crash recovery require real adapter integration and tests.
4. `ReviewEvent`: synthetic ledger demonstrates optimistic lock semantics only; enforce DB transaction, before/after audit, reviewer separation and evidence immutability in production.
5. `KnowledgeCitation`: query must pin workspace, source SHA, page, dataset version and approved review state; reject stale/cross-workspace results.
6. `FakeHttpTransport`: PageFetcher has a fake HttpClient injection seam but real TEST workers must use mandatory denied network egress. Robots parsing now runs in real crawler when opted in; DNS rebinding remains a network-level control.

Runtime acceptance gates remain BLOCKED until primary shared-DB restricted principal, safe TEST adapters, synthetic fixtures and independent audit evidence exist. No clone is created by this deliverable.
