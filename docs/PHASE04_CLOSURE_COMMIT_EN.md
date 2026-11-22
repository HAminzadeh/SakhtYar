feat(knowledge,crawler): harden Phase 04 archive import and crawler isolation contracts

Scope and baseline
- Pin source hardening to SakhtYar v2 at c2537a043d9f71ab92e28b3d8b69479ee3a62525.
- Preserve the shared REAL/TEST PostgreSQL architecture and all applied Flyway migrations.

Knowledge package ingestion
- Introduce Phase04ArchiveGuard for bounded MinIO ZIP transfer and pre-extraction verification.
- Reject zip-slip traversal, duplicate and case-colliding entries, unsafe segments, invalid methods, compression bombs, oversized entries and invalid CRC/lengths.
- Clean temporary extraction files on import preparation failures.
- Require unique manifest artifact paths and valid SHA-256 formatting before artifact lookup.
- Keep existing transactional domain import and manual activation behavior intact.

OCR, review and retrieval contracts
- Add a deterministic synthetic OCR contract with byte/text/source SHA-256, exact UTF-8 text preservation and character-error-rate calculation.
- Add in-memory synthetic checkpoint, idempotent replay, retry-budget and review version-conflict contracts.
- Add a scope-pinned synthetic retrieval index that rejects cross-workspace and stale-version evidence.
- Document that these are non-production fixtures and do not prove real OCR, SQL concurrency or crash recovery.

Crawler hardening
- Enforce USER_MANAGE at the HTTP route boundary and within the actual IngestionPipeline service.
- Reject anonymous/invalid crawler operator contexts and invalid page limits before creating crawl jobs.
- Enforce robots.txt before crawling whenever respectRobots is enabled, including explicit denial for unavailable or unexpected responses.
- Reject missing allowlisted hosts in UrlDiscovery and enforce allowed URL syntax in canonicalization.
- Reject unrecognized successful HTTP content types in PageFetcher.
- Preserve real DNS/public-address validation, redirect checks, response limits and TESTER denial.

Testing, delivery and safety
- Add JUnit positive/negative contract tests for ZIP security, robots, synthetic OCR, reviewer conflicts, checkpoint replay, cross-workspace retrieval and crawler URL boundaries, fake-transport MIME denial and operator authorization.
- Add offline Node source contracts without database or network calls.
- Provide a PowerShell 5.1 installer with pinned package SHA-256, per-file Git blob checks, git apply --check, safe backups, rollback, idempotent verification and structured reports.
- Do not create database clones, modify REAL data, execute Flyway, provision roles, send outbound TEST messages or commit/push automatically.

Validation as delivered
- 20 synthetic Java 21 checks: PASS (offline, using temporary dependency stubs).
- Git patch apply/check/reverse-check on synthetic baseline: PASS.
- Manifest and package integrity: PASS.
- Full Maven/JUnit, frontend, database/HTTP and actual Business E2E: NOT_RUN/BLOCKED until operator environment execution.

No claim of 24/24 Business E2E success is made by this change.
