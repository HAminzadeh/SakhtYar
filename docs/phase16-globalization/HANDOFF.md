# Handoff — Phase 16 Globalization

Baseline `v2@75ce9aad000aa0601fb382eefa9204434f611d4e`. Scoped changes only, no historical migration rewrite, no DB writes by installer.

Delivered: service-side case ACL and lock, REAL scope; FX submission PENDING + review/quote and provenance; UI preference controls and Persian date; GeoNames egress guards and startup safety; new tests and deployment package. Reuse original global catalog & foreign keys.

Pending gate: full Maven/JUnit reactor, isolated Clone PostgreSQL read/write under two identities + RLS, browser date/RTL validation, distributed import lock decision, large GeoNames fixture validation, FX two-person review policy, FX conversion regression of downstream clients. Check actual Git HEAD before next phase; unpushed local changes are not visible on GitHub.

Expected next phase: Phase 17 after fully verifying/committing Phase 16.
