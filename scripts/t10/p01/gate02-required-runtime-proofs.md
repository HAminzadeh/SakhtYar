# T10 P01 Gate02: Runtime Authorization Proofs

V63 is already applied and MUST NOT be edited. No V64 migration may be activated solely from this report.

1. Identify the effective JDBC username of the running API service (do not share passwords).
2. Demonstrate the API's *runtime* role is NO SUPERUSER and NO BYPASSRLS, or provide a migration plan separating privileged provisioning and Flyway from runtime.
3. Confirm whether SimulationCoreFixtureService currently needs DDL/CREATEROLE at runtime; isolate privileged actions before restricting service connections.
4. Map the server-side authenticated principal and workspace membership authorization. Client-provided workspace IDs and custom session settings are not sufficient.
5. Specify owner and child history read/write rules, terminal status immutability, and any admin override path.
6. Test on the existing database using restricted PostgreSQL role: own workspace allowed; other workspace denied; unbound workspace denied; unauthorized writes denied.
7. Confirm role and schema creation through an immutable Flyway migration; avoid blanket PUBLIC grants.
8. Inspect actual DB and repository migration versions before selecting a new version.

Gate result: BLOCKED until evidence is reviewed. This stage does not write policies or modify database permissions.