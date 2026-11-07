feat(quality,agents): harden Phase 06 workflow gates and introduce evidence-only release checks

- Reject TESTER intelligence workflow execution before database writes or real component calls.
- Validate DAG step bounds, identifiers, dependencies, unknown components and cycles.
- Preflight all registered workflow components before opening persistent execution rows.
- Minimize AgentBus audit payload to non-sensitive metadata; avoid raw prompts and outputs.
- Separate business E2E pass coverage from preflight completion in Simulation Center UI.
- Display tester principal and captured source SHA without claiming unknown SHA is proof.
- Add independent strict 24/24 release gate and fail-closed offline regression tests.
- Add opt-in GitHub Actions acceptance workflow and local-only Playwright smoke baseline.
- Document TEST/REAL safety, evidence expectations and the outstanding E2E-21..24 gaps.
- No Flyway migration or REAL database changes.

Baseline: ad862c46e097273ae8b5d9a49897b630bfe343f6 (v2, 2026-11-07)
Verification: source anchors and offline Node contracts are testable independently.
Pending: Maven/TypeScript/Playwright execution on Windows, isolated database and the full 24 business executors.
Release status: BLOCKED until complete, independently validated HTTP/DB/browser evidence exists.
