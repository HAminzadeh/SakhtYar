# Phase 15 Handoff — Neshan integration

Baseline HEAD: `067f4f29e26fd83688ea399d57a7277183a8784f` (v2, 2026-11-24T15:06:53Z)
Module: `backend/modules/integration-neshan`.

Changes: 3 existing Java classes updated; 2 new Java policy classes; 3 JUnit test suites; 2 documents. Zero migrations; all Phase14 endpoints kept.

Safety: outbound disabled by default in newly bound properties; explicit `APP_INTEGRATIONS_NESHAN_OUTBOUND_ENABLED=true` required alongside enabled + API key. TESTER role blocked. Only official HTTPS API host accepted. No automatic HTTP retries; in-memory rate/circuit; no location cache or DB changes.

Tests: Java21 stub policy compilation / 14 independent guard checks PASSED in authoring sandbox. Maven/Spring JUnit MockRestServiceServer tests must execute in user's Windows environment. App/Browser E2E, full reactor, Web map tile egress, distributed quota and real Neshan intentionally not verified.

Next phase (16): before changes, refetch latest v2 HEAD, read new commits & local diffs, and consume the Phase15 acceptance results rather than assuming production readiness. Do not run DB migrations automatically.
