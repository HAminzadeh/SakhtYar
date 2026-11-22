-- RESTRICTED physical LOGIN only. ONLY on disposable TEST clone; NOT production or shared DB.
-- psql usage: psql "$PHASE02_RESTRICTED_JDBC_URL" -U "$PHASE02_RESTRICTED_USERNAME" \
--  -v own_workspace=<uuid> -v other_workspace=<uuid> -v other_case_id=<uuid> \
--  -f tests/phase02/03_clone_only_negative_writes.sql
-- A real other-case baseline MUST be independently audited by DBA. This file never commits.
\set ON_ERROR_STOP on
BEGIN;
SET TRANSACTION READ WRITE;
SELECT set_config('app.sakhtyar_scope', 'TEST', true);
SELECT set_config('app.sakhtyar_workspace', :'other_workspace', true);
SELECT set_config('app.phase02_own_workspace', :'own_workspace', true);
SELECT set_config('app.phase02_other_case_id', :'other_case_id', true);
DO $guard$
DECLARE role_expected text;
BEGIN
    role_expected := 'sakhtyar_tws_' || replace(current_setting('app.phase02_own_workspace'), '-', '');
    IF current_database() <> 'sakhtyar_test_clone_phase02'
        OR current_user <> session_user
        OR current_user <> role_expected
        OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=current_user
            AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole) THEN
        RAISE EXCEPTION 'STOP: restricted clone physical login guard failed';
    END IF;
    IF current_setting('app.sakhtyar_workspace') = current_setting('app.phase02_own_workspace') THEN
        RAISE EXCEPTION 'STOP: other workspace must differ';
    END IF;
END $guard$;
-- Negative UPDATE: should affect 0 rows or be denied. This also checks no broad UPDATE grants.
DO $update$
DECLARE rows_changed bigint := 0;
BEGIN
  BEGIN
    UPDATE public.construction_case SET title=title || ' phase02-negative'
    WHERE id=current_setting('app.phase02_other_case_id')::uuid;
    GET DIAGNOSTICS rows_changed = ROW_COUNT;
  EXCEPTION WHEN insufficient_privilege THEN
    rows_changed := 0;
  END;
  IF rows_changed <> 0 THEN
    RAISE EXCEPTION 'SECURITY FAILURE: cross-workspace UPDATE touched % rows', rows_changed;
  END IF;
END $update$;
-- Negative INSERT into REAL from TEST role must be REJECTED (not merely silently filtered).
DO $insert$
DECLARE rejected boolean := false;
BEGIN
  BEGIN
    INSERT INTO public.construction_case
      (id,title,status,description,city,district,address,land_area_m2,created_by,
       created_at,updated_at,project_code,data_scope,test_workspace_id)
    VALUES (gen_random_uuid(),'PHASE02 NEGATIVE','DRAFT','TEST CLONE ONLY',
            'TEST_CITY',null,'TEST CLONE ADDRESS',300.00,'phase02_test',
            now(),now(),'SYT-NEG-'||substr(replace(gen_random_uuid()::text,'-',''),1,20),
            'REAL',null);
  EXCEPTION WHEN insufficient_privilege OR check_violation THEN
    rejected := true;
  END;
  IF NOT rejected THEN RAISE EXCEPTION 'SECURITY FAILURE: REAL INSERT accepted by restricted TEST role'; END IF;
END $insert$;
ROLLBACK;
