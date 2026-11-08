feat(phase06): provide fail-closed offline AI/browser adapters and independently signed release evidence verification

AI and workflow privacy
- Introduce an explicitly instantiated deterministic Phase06FakeAiModelProvider with restricted tools, bounded inputs, synthetic output, retry/timeout injection and circuit-open behavior.
- Keep the fake provider outside Spring component discovery so TEST calls cannot silently reach REAL AiGateway or database-backed routing.
- Redact Ollama HTTP response bodies, URLs and exception details from provider errors.
- Restrict AiModelRegistry status failures and AgentBus audit failures to exception type only.
- Replace untrusted Intelligence workflow component exception text with a fixed safe error code in persistence and response paths.

Synthetic notification and config isolation
- Add an in-memory fake notification outbox with workspace/run-key deduplication, failure injection, bounded retry/cooldown and scoped teardown.
- Add frozen-clock synthetic market feed with explicit TEST-only quote codes and no real provider access.
- Add an in-memory config state machine with Draft, Review, Publish and Rollback, versioned hash snapshots, immutable values and separation of duties.
- Add positive and negative JUnit contracts for these adapters and the existing Phase02 fixture/outbound boundaries.
- Do not connect fake adapters to JDBC, real notification senders, real market services or production configuration activation.

Browser and CI gates
- Add a synthetic Playwright smoke test covering two TEST workspaces, Farsi/RTL selectors, desktop/mobile and loopback-only HTTP mocks; prohibit browser egress.
- Extend the Phase06 CI workflow with offline source/evidence-verifier contracts and a dedicated browser-synthetic-smoke job.
- Preserve standard source/unit CI, and isolate release approval from ordinary CI success.

Independent release proof verification
- Add a strict on-disk verifier requiring 96 constrained TEST proof files, real byte SHA-256, workspace/run ownership, restricted-principal metadata, timestamp freshness and a CI-owned HMAC signature.
- Verify approved source commit against GitHub Actions checkout SHA and reject missing/forged signatures, tampering, symlinks, traversal, stale evidence and incomplete manifests.
- Introduce a separate release_approval command; leave the historical release_gate module explicitly structural only.
- Require all 24 actual business scenarios and independent evidence prior to release authorization; no synthetic fixtures are promoted into Business E2E PASS.

Installation and safety
- Add a guarded PowerShell 5.1 OneClick installer with automatic ZIP discovery, hash/manifest checks, tracked Git baseline verification, timestamped backups, rerunnable idempotent changes and structured build logs.
- Run Node source/evidence contracts, selected Maven/JUnit tests, backend compile package and frontend build without database access.
- Preserve existing REAL/TEST segregation implemented in Phase02. Do not create a database clone, access or mutate REAL, run SQL, migrations, role/RLS changes, seed, restore, cleanup, Docker reset or automatic Git commit/push.

Validation
- Java 21 isolated JUnit-shaped fake adapter tests: PASSED (10 methods; not Maven runtime).
- Node on-disk signature and tamper contract: PASSED (14 checks; synthetic test key only).
- Windows PowerShell 5.1, Maven/JUnit actual source build, Playwright CI/Windows and live REAL/TEST isolation proof: PENDING.
- Business E2E 24/24: BLOCKED pending genuine domain executors and independent restricted principal evidence.

Branch: v2
Baseline: ff66065dcbb5bcdf383b32e798fe227f0841e370
