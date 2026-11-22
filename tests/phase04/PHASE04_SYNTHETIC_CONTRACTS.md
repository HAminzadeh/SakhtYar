# Phase 04 synthetic-only contracts

These Java fixtures do not connect to a database, MinIO, Redis, the network, or AI providers.

- `Phase04FakeOcrEngine`: supplied Persian text + byte hash; no OCR is actually performed; `FAKE_DETERMINISTIC` only.
- `Phase04SyntheticReviewCheckpoint`: in-memory revision gate, reviewer separation, run-scoped idempotence. Restart persistence NOT validated.
- `Phase04SyntheticRetrievalIndex`: workspace and pinned-version constrained synthetic lookup. Production DB RLS and index invalidation NOT validated.
- `Phase04ArchiveGuard`: bounded ZIP inspection, duplicate name, CRC, zip-slip and compression ratio checks. Called by actual importer before extraction.
- `Phase04RobotsPolicy`: deterministic rule parsing and explicit deny-on-unknown. Called by actual crawler when respectRobots=true; network access still must be denied by infrastructure when operating in TEST.

Do not mark Business E2E scenarios PASSED from these contracts.
