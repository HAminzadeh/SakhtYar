-- T10 inspection only; never auto-ALTER global identity/config tables.
BEGIN TRANSACTION READ ONLY;
SELECT c.relname AS table_name,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS force_rls,
       EXISTS (SELECT 1 FROM pg_policies p
               WHERE p.schemaname='public' AND p.tablename=c.relname) AS any_policy
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relkind='r' AND n.nspname='public'
ORDER BY c.relname;
SELECT conrelid::regclass::text AS source_table, confrelid::regclass::text AS target_table,
       conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE contype='f'
AND connamespace='public'::regnamespace
ORDER BY source_table, conname;
COMMIT;
