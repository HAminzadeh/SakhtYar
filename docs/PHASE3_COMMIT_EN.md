feat(e2e): add Phase 3 offline acceptance foundations and 24-scenario fail-closed inventory

- Register E2E-21..24 as Spring-discoverable offline contract executors using the existing scenario registry; never return a false FULL_BUSINESS_E2E PASS.
- Implement deterministic AI tool allowlist and injection-denial checks, localhost-only browser destination controls, notification dedup/cooldown, and versioned sandbox configuration checks.
- Reuse and test the existing Phase06FakeNotificationOutbox and Phase06ConfigSandbox for scoped operations and denied real effects.
- Add synthetic Playwright RTL/mobile-desktop smoke with no login, API, backend, database, screenshot or trace.
- Add a read-only 24-row readiness inventory distinguishing source presence from runtime acceptance, with E2E-05 recovery explicitly BLOCKED by no-clone policy.
- Run existing Phase06 proof verifier and release gate contract tests in the Phase3 offline CI workflow.
- Preserve immutable manifests, safe install semantics, staged user work and Phase 08/09 admission requirements.

Verification: Only describe tests that have actually passed in operator logs. Java 21 offline smoke and Node readiness contract can be executed without database writes. Windows/Maven/Playwright verification remains NOT_RUN until independently observed.

Limitations: 0 verified full business E2E passes, 0 authenticated full workflows evidenced, 0 independently verified 96-artifact attestation. Do not claim Phase 3 final acceptance or release approval.
