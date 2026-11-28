-- Phase 18-A. Additive only: no reclassification, backfill, grants or business row changes.
-- Requires V58 scope markers and V60 context function. Test roles must be separately provisioned.
-- Run first on a disposable clone; migration owner must be allowed to inspect all rows.
DO $$
DECLARE t text;
BEGIN
  IF to_regprocedure('public.sakhtyar_core_scope_ok(character varying,uuid)') IS NULL THEN
    RAISE EXCEPTION 'V60 scope function is required';
  END IF;
  FOREACH t IN ARRAY ARRAY['material_item','material_variant','material_price_observation','material_price_aggregate','material_price_anomaly','knowledge_term','knowledge_source'] LOOP
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=t AND column_name='data_scope')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=t AND column_name='test_workspace_id') THEN
      RAISE EXCEPTION 'V58 scope columns missing on %',t;
    END IF;
  END LOOP;
  IF EXISTS (SELECT upper(code) FROM public.material_item GROUP BY upper(code) HAVING count(*)>1) THEN
    RAISE EXCEPTION 'Material codes collide case-insensitively; inspect and resolve before migration';
  END IF;
  IF EXISTS (SELECT 1 FROM public.material_variant c JOIN public.material_item p ON p.id=c.material_item_id WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))
     OR EXISTS (SELECT 1 FROM public.material_price_observation c JOIN public.material_item p ON p.id=c.material_item_id WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))
     OR EXISTS (SELECT 1 FROM public.material_price_aggregate c JOIN public.material_item p ON p.id=c.material_item_id WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))
     OR EXISTS (SELECT 1 FROM public.material_price_anomaly c JOIN public.material_price_observation p ON p.id=c.observation_id WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))
     OR EXISTS (SELECT 1 FROM public.material_item c JOIN public.knowledge_term p ON p.id=c.knowledge_term_id WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))
     OR EXISTS (SELECT 1 FROM public.material_price_observation c JOIN public.knowledge_source p ON p.id=c.source_id WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))
     OR EXISTS (SELECT 1 FROM public.material_price_observation c JOIN public.material_variant p ON p.id=c.variant_id WHERE (c.data_scope,c.test_workspace_id,c.material_item_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id,p.material_item_id))
     OR EXISTS (SELECT 1 FROM public.material_price_aggregate c JOIN public.material_variant p ON p.id=c.variant_id WHERE (c.data_scope,c.test_workspace_id,c.material_item_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id,p.material_item_id)) THEN
    RAISE EXCEPTION 'Existing material cross-scope or cross-item reference; migration refused';
  END IF;
END $$;
CREATE UNIQUE INDEX ux_phase18_material_code_upper ON public.material_item(upper(code));

-- Bind TEST to the physically authenticated per-workspace identity already used by core fixtures.
-- The normal application role cannot obtain TEST access merely by changing a custom GUC.
CREATE FUNCTION public.sakhtyar_material_scope_ok(p_scope varchar,p_workspace uuid)
RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT public.sakhtyar_core_scope_ok(p_scope,p_workspace) AND
 CASE WHEN p_scope='TEST' THEN p_workspace IS NOT NULL AND session_user=current_user
      AND current_user::text=('sakhtyar_tws_'||replace(p_workspace::text,'-',''))
      WHEN p_scope='REAL' THEN current_user::text !~ '^sakhtyar_tws_' AND session_user::text !~ '^sakhtyar_tws_'
      ELSE false END
$$;

CREATE FUNCTION public.sakhtyar_material_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE ps varchar(8); pw uuid; pi uuid; parent_table text; parent_id uuid;
BEGIN
  IF TG_OP='DELETE' THEN
    IF TG_TABLE_NAME IN ('material_item','material_variant','material_price_observation') THEN
      RAISE EXCEPTION 'Material identity and observation evidence may not be deleted' USING ERRCODE='42501';
    END IF;
    IF NOT public.sakhtyar_material_scope_ok(OLD.data_scope,OLD.test_workspace_id) THEN
      RAISE EXCEPTION 'Material scope denied' USING ERRCODE='42501';
    END IF;
    RETURN OLD;
  END IF;
  IF NOT public.sakhtyar_material_scope_ok(NEW.data_scope,NEW.test_workspace_id) THEN
    RAISE EXCEPTION 'Material scope denied' USING ERRCODE='42501';
  END IF;
  IF TG_OP='UPDATE' THEN
    IF (OLD.data_scope,OLD.test_workspace_id,OLD.test_run_id) IS DISTINCT FROM (NEW.data_scope,NEW.test_workspace_id,NEW.test_run_id) THEN
      RAISE EXCEPTION 'Material scope identity is immutable' USING ERRCODE='42501';
    END IF;
    IF TG_TABLE_NAME='material_item' THEN
      IF (OLD.code,OLD.unit_code) IS DISTINCT FROM (NEW.code,NEW.unit_code) THEN
        RAISE EXCEPTION 'Material code and base unit are immutable' USING ERRCODE='23514';
      END IF;
    ELSIF TG_TABLE_NAME='material_variant' THEN
      IF OLD.material_item_id IS DISTINCT FROM NEW.material_item_id THEN RAISE EXCEPTION 'Variant parent is immutable' USING ERRCODE='23514'; END IF;
      IF (OLD.brand,OLD.model,OLD.grade,OLD.manufacturer,OLD.country_code,OLD.attributes) IS DISTINCT FROM (NEW.brand,NEW.model,NEW.grade,NEW.manufacturer,NEW.country_code,NEW.attributes)
         AND (EXISTS (SELECT 1 FROM public.material_price_observation WHERE variant_id=OLD.id)
              OR EXISTS (SELECT 1 FROM public.cost_assembly_component WHERE variant_id=OLD.id)) THEN
        RAISE EXCEPTION 'Referenced variant identity is immutable; create a new variant' USING ERRCODE='23514';
      END IF;
    END IF;
  END IF;
  IF NEW.data_scope='TEST' AND NOT EXISTS (SELECT 1 FROM public.simulation_workspace WHERE id=NEW.test_workspace_id AND state='ACTIVE') THEN
    RAISE EXCEPTION 'Unknown or archived TEST workspace' USING ERRCODE='23503';
  END IF;
  IF TG_TABLE_NAME='material_item' THEN
    parent_table:='knowledge_term'; parent_id:=NEW.knowledge_term_id;
  ELSIF TG_TABLE_NAME='material_price_anomaly' THEN
    parent_table:='material_price_observation'; parent_id:=NEW.observation_id;
  ELSE
    parent_table:='material_item'; parent_id:=NEW.material_item_id;
  END IF;
  IF parent_id IS NOT NULL THEN
    EXECUTE format('SELECT data_scope,test_workspace_id FROM public.%I WHERE id=$1',parent_table) INTO ps,pw USING parent_id;
    IF ps IS NULL OR (ps,pw) IS DISTINCT FROM (NEW.data_scope,NEW.test_workspace_id) THEN
      RAISE EXCEPTION 'Material parent scope mismatch' USING ERRCODE='23503';
    END IF;
  END IF;
  IF TG_TABLE_NAME IN ('material_price_observation','material_price_aggregate') THEN
    IF NEW.variant_id IS NOT NULL THEN
      SELECT data_scope,test_workspace_id,material_item_id INTO ps,pw,pi FROM public.material_variant WHERE id=NEW.variant_id;
      IF ps IS NULL OR (ps,pw,pi) IS DISTINCT FROM (NEW.data_scope,NEW.test_workspace_id,NEW.material_item_id) THEN
        RAISE EXCEPTION 'Material variant scope or item mismatch' USING ERRCODE='23503';
      END IF;
    END IF;
  END IF;
  IF TG_TABLE_NAME='material_price_observation' THEN
    SELECT data_scope,test_workspace_id INTO ps,pw FROM public.knowledge_source WHERE id=NEW.source_id;
    IF ps IS NULL OR (ps,pw) IS DISTINCT FROM (NEW.data_scope,NEW.test_workspace_id) THEN
      RAISE EXCEPTION 'Material source scope mismatch' USING ERRCODE='23503';
    END IF;
  END IF;
  RETURN NEW;
END $$;
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['material_item','material_variant','material_price_observation','material_price_aggregate','material_price_anomaly'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
    EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY',t);
    EXECUTE format('CREATE POLICY phase18_material_allow ON public.%I FOR ALL USING (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id)) WITH CHECK (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id))',t);
    -- Restrictive as well: another permissive policy cannot broaden scope.
    EXECUTE format('CREATE POLICY phase18_material_limit ON public.%I AS RESTRICTIVE FOR ALL USING (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id)) WITH CHECK (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id))',t);
    EXECUTE format('CREATE TRIGGER trg_phase18_material_guard BEFORE INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_material_guard()',t);
  END LOOP;
END $$;
