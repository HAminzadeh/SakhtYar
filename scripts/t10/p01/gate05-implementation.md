# T10-P01 Gate05 — API-level owner authorization

- Inject SimulationWorkspaceAuthorizationService into SimulationController.
- Protect core-fixture create/list, evidence, audit, run creation and run details.
- Filter workspace and run listings to the current authenticated owner.
- Run report/compare delegate to getRun and inherit the owner gate.
- Keep USER_MANAGE required, keep unchanged existing data, migrations and database roles.
- Access to archived workspaces is intentionally fail-closed; historical reports are inaccessible until a separately reviewed read-only policy is implemented.
- Gate05 is **not** a database RLS proof; privileged application DataSource still requires remediation before V64.
- Contract tests are source-level regression checks, not HTTP security integration tests.
- Verify the admin UI's expected cross-owner behavior before deployment; the change intentionally restricts administrative visibility.