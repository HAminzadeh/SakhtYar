# T10-P01 Gate03: runtime authorization review

Status: BLOCKED (design), AUDIT COMPLETE (collection).

1. Identify **effective** backend runtime DataSource user from process-safe runtime evidence, without logging passwords.
2. Prove it is non-superuser and has no BYPASSRLS; separate Flyway/DDL and runtime roles.
3. Map JWT/session principal to server-verified workspace ownership/membership.
4. Determine restricted SQL writer/read role for T10 history and transaction-scoped authorization.
5. Define cross-workspace positive/negative DB tests under the actual restricted role.
6. Do not modify V63. Do not create V64 policies based solely on client-settable GUCs.
7. Existing SimulationCoreFixtureService requires provisioning privileges; plan a controlled migration before switching application datasource role.
8. Inspect Java symbol locations in gate03-runtime-auth-discovery.json; source matches alone are not proof.