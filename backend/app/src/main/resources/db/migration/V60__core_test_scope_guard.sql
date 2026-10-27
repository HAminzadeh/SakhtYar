-- T05.1 CORE DOMAIN ISOLATION ONLY. This is NOT application-wide REAL/TEST isolation.
-- Requires additive V58 metadata. Existing rows remain REAL, no existing business rows deleted.
-- Explicit application runtime database role must be NON-SUPERUSER and without BYPASSRLS.

CREATE OR REPLACE FUNCTION public.sakhtyar_core_scope_ok(p_scope varchar, p_workspace uuid)
RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT CASE WHEN COALESCE(NULLIF(current_setting('app.sakhtyar_scope',true),''),'REAL')='REAL'
   THEN p_scope='REAL' AND p_workspace IS NULL
   WHEN current_setting('app.sakhtyar_scope',true)='TEST'
   THEN p_scope='TEST' AND p_workspace IS NOT NULL
        AND p_workspace::text = NULLIF(current_setting('app.sakhtyar_workspace',true),'')
   ELSE false END
$$;

CREATE OR REPLACE FUNCTION public.sakhtyar_core_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_scope varchar(8); parent_workspace uuid; parent_id uuid;
BEGIN
  IF NOT public.sakhtyar_core_scope_ok(NEW.data_scope, NEW.test_workspace_id) THEN
    RAISE EXCEPTION 'SakhtYar REAL/TEST scope violation on %', TG_TABLE_NAME USING ERRCODE='42501';
  END IF;
  IF TG_OP='UPDATE' AND
     (OLD.data_scope IS DISTINCT FROM NEW.data_scope OR
      OLD.test_workspace_id IS DISTINCT FROM NEW.test_workspace_id) THEN
    RAISE EXCEPTION 'Changing a record data scope is prohibited' USING ERRCODE='42501';
  END IF;
  IF NEW.data_scope='TEST' AND NOT EXISTS
    (SELECT 1 FROM public.simulation_workspace WHERE id=NEW.test_workspace_id AND state='ACTIVE') THEN
    RAISE EXCEPTION 'Unknown or archived simulation workspace' USING ERRCODE='23503';
  END IF;
  IF TG_TABLE_NAME='property' THEN
    parent_id := NEW.case_id;
    SELECT data_scope,test_workspace_id INTO parent_scope,parent_workspace
      FROM public.construction_case WHERE id=parent_id;
  ELSIF TG_TABLE_NAME='property_owner' THEN
    parent_id := NEW.property_id;
    SELECT data_scope,test_workspace_id INTO parent_scope,parent_workspace
      FROM public.property WHERE id=parent_id;
  ELSIF TG_TABLE_NAME='case_document' THEN
    parent_id := NEW.case_id;
    SELECT data_scope,test_workspace_id INTO parent_scope,parent_workspace
      FROM public.construction_case WHERE id=parent_id;
  ELSE
    RETURN NEW;
  END IF;
  IF parent_scope IS NULL OR parent_scope IS DISTINCT FROM NEW.data_scope OR
     parent_workspace IS DISTINCT FROM NEW.test_workspace_id THEN
    RAISE EXCEPTION 'Cross-scope parent reference prohibited for %',TG_TABLE_NAME
       USING ERRCODE='23503';
  END IF;
  RETURN NEW;
END $$;

DO $guard$
DECLARE table_name text;
BEGIN
 FOREACH table_name IN ARRAY ARRAY['construction_case','property','property_owner','case_document'] LOOP
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',table_name);
  EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY',table_name);
  EXECUTE format('DROP POLICY IF EXISTS sakhtyar_core_scope_policy ON public.%I',table_name);
  EXECUTE format('CREATE POLICY sakhtyar_core_scope_policy ON public.%I FOR ALL USING (public.sakhtyar_core_scope_ok(data_scope,test_workspace_id)) WITH CHECK (public.sakhtyar_core_scope_ok(data_scope,test_workspace_id))',table_name);
  EXECUTE format('DROP TRIGGER IF EXISTS trg_sakhtyar_core_scope ON public.%I',table_name);
  EXECUTE format('CREATE TRIGGER trg_sakhtyar_core_scope BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_core_guard()',table_name);
 END LOOP;
END $guard$;

-- T05.3: explicitly scoped, append-only fixture evidence. Not a replacement for business E2E runs.
CREATE TABLE IF NOT EXISTS public.simulation_core_fixture (
 id uuid PRIMARY KEY,
 workspace_id uuid NOT NULL REFERENCES public.simulation_workspace(id) ON DELETE RESTRICT,
 request_key varchar(100) NOT NULL,
 case_id uuid NOT NULL UNIQUE,
 property_id uuid NOT NULL UNIQUE,
 owner_a_id uuid NOT NULL UNIQUE,
 owner_b_id uuid NOT NULL UNIQUE,
 created_by varchar(160) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 outcome varchar(20) NOT NULL DEFAULT 'COMMITTED' CHECK (outcome='COMMITTED'),
 UNIQUE(workspace_id,request_key)
);
CREATE INDEX IF NOT EXISTS ix_sim_core_fixture_workspace ON public.simulation_core_fixture(workspace_id,created_at DESC);
-- Deliberately no unrestricted simulation mutation permissions, no outbound dispatch integration.
