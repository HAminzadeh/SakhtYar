# T10-P01 Gate04 | Workspace authorization foundation

- Provides an owner-only, active-workspace, USER_MANAGE-gated authorization service for future history endpoints.
- Derives username solely from authenticated Spring Security principal.
- Uses a bound PostgreSQL parameter query; denies missing or foreign workspaces.
- Does NOT wire into existing SimulationController endpoints to avoid changing current admin behavior without an explicit compatibility decision.
- Does NOT implement non-superuser DB runtime role, PostgreSQL RLS policies or migration V64.
- Unit tests use Mockito only; they do NOT prove database isolation.
- Next: review whether administrators require cross-workspace access and design explicit audited delegation.
- Keep V63 immutable; never commit local *.t10-backup-* files.
