# T10-P01 / Gate04 — focused runtime authorization review

## Observed from Gate03 file

- Flyway V63: SUCCESS
- DB audit identity: sakhtyar, SUPERUSER and BYPASSRLS
- Three E2E history tables: FORCE RLS active, zero policies
- Discovered Java locations: 250 (report cap, not complete coverage)
- Candidate restricted TEST fixture role is distinct from the audit user

## Verified against v2 source (GitHub)

**JwtCookieAuthenticationFilter.java** validates JWT, session ID and user ID, session usability, enabled user, and populates Spring SecurityContext. This supplies a verified username, but does not itself authorize a simulation workspace.

**CaseAccessService.java** checks ROLE_ADMIN, case creator, or active membership in construction_case_access. This authorizes cases, not simulation workspaces.

**SimulationController.java** checks USER_MANAGE for admin simulation endpoints, and declares a 24-scenario catalog. An admin permission by itself is not a proof of workspace ownership/authorization for history rows.

**SimulationCoreFixtureService.java** provisions a restricted workspace role but relies on a privileged provisioning datasource; changing the default DB user would need a redesign.

**application.yml / application-local.yml** use POSTGRES_USER fallback and Flyway; these are configuration defaults, not proof of effective running API principal.

## Required concrete implementation work

1. Introduce a server-side workspace authorization service driven from verified Authentication and simulation_workspace.owner_username or another authoritative membership model. Decide whether USER_MANAGE may administer all workspaces, with explicit tests.
2. Separate migration/privileged fixture provisioning datasource from history runtime datasource. Never use a SUPERUSER or BYPASSRLS principal for history E2E isolation tests.
3. Define a dedicated non-login history reader/writer access role or limited login role; grant only minimum INSERT/SELECT/UPDATE on the three tables and required sequences. Do not grant privileges to PUBLIC.
4. Add V64 only after authorization is proven: history parent policy, child policies with cross-parent workspace checks, and rules against changing finalized evidence.
5. Test with at least two separate workspaces and two restricted identities. Verify allowed own-workspace INSERT/SELECT, denied cross-workspace SELECT/INSERT/UPDATE, and deny-by-default without context.
6. Keep V63 unchanged and do not blindly force RLS on app_user, auth_session, or construction_case_access until their authorization model is reviewed.

## Important limitation

This file is a source-level review and plan; it is not a completed Gate04 proof or runnable V64. The local source may contain newer unpushed changes than branch v2.
