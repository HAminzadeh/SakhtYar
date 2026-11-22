# Gate06-E negative-test matrix â€” NOT YET EXECUTED

1. Observe actual Spring application DataSource current_user; require rolsuper=false, rolbypassrls=false.
2. Confirm runtime cannot CREATE/ALTER/DROP tables, CREATE ROLE, read/write flyway_schema_history or SET ROLE to higher privileges.
3. Confirm Flyway uses a distinct account in isolated clone and migrations work without runtime owner powers.
4. Confirm dedicated Fixture provider uses distinct principal; strict flag rejects missing settings.
5. In disposable clone, isolate workspace A and B: cross-workspace SELECT/UPDATE/DELETE and ORM lookups all rejected.
6. TEST fixture cannot see/modify REAL records or trigger external integrations, notifications, indexes, storage side effects.
7. Ensure RLS fails closed for missing tenant scope, and pooled sessions clear scope after transaction or rollback.
8. Verify required JPA, security, background jobs, dev-seed and business API paths still work with restricted grants.
9. Verify INSERT/UPDATE WITH CHECK, SELECT/DELETE USING, foreign keys, sequences, function EXECUTE and ownership.
10. Record sanitized evidence, backup and connection-config rollback; V64 remains blocked on any failure.
