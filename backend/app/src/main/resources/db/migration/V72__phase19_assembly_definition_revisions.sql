-- Phase 19-A: forward-only definition lineage; preserve V71 price and scope guards.
-- No historical definition is invented for pre-V72 estimates; their new columns stay NULL.
ALTER TABLE public.cost_assembly ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK (revision>0);
ALTER TABLE public.cost_estimate ADD COLUMN definition_revision bigint,
 ADD COLUMN assembly_definition jsonb,
 ADD CONSTRAINT chk_phase19a_estimate_definition CHECK (
   (definition_revision IS NULL AND assembly_definition IS NULL) OR
   (definition_revision IS NOT NULL AND definition_revision>0 AND assembly_definition IS NOT NULL
    AND jsonb_typeof(assembly_definition)='object'));

CREATE INDEX ix_phase19a_estimate_line_component ON public.cost_estimate_line(assembly_component_id);

CREATE TABLE public.cost_assembly_revision (
 assembly_id uuid NOT NULL REFERENCES public.cost_assembly(id) ON DELETE RESTRICT,
 revision bigint NOT NULL CHECK(revision>0),
 definition jsonb NOT NULL CHECK(jsonb_typeof(definition)='object'),
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 data_scope varchar(8) NOT NULL,
 test_workspace_id uuid,
 test_run_id uuid,
 PRIMARY KEY(assembly_id,revision),
 CHECK ((data_scope='REAL' AND test_workspace_id IS NULL AND test_run_id IS NULL)
     OR (data_scope='TEST' AND test_workspace_id IS NOT NULL))
);
CREATE INDEX ix_phase19a_revision_workspace ON public.cost_assembly_revision(test_workspace_id) WHERE data_scope='TEST';
ALTER TABLE public.cost_assembly_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cost_assembly_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY phase19a_revision_allow ON public.cost_assembly_revision FOR ALL
 USING (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id))
 WITH CHECK (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id));
CREATE POLICY phase19a_revision_limit ON public.cost_assembly_revision AS RESTRICTIVE FOR ALL
 USING (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id))
 WITH CHECK (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id));

-- Volatile so nested trigger calls see earlier changes within the transaction.
-- SECURITY INVOKER by default; never bypass RLS or expose another workspace.
CREATE FUNCTION public.sakhtyar_assembly_definition(p_id uuid) RETURNS jsonb
 LANGUAGE sql VOLATILE SECURITY INVOKER SET search_path=pg_catalog,public AS $$
 SELECT jsonb_build_object('assemblyId',a.id,'revision',a.revision::text,'code',a.code,
   'nameFa',a.name_fa,'nameEn',a.name_en,'category',a.category,'outputUnitCode',a.output_unit_code,
   'description',a.description,'active',a.active,'metadata',a.metadata,
   'components',coalesce((SELECT jsonb_agg(jsonb_build_object('id',c.id,'materialItemId',c.material_item_id,
      'variantId',c.variant_id,'quantity',c.quantity::text,'wasteFactor',c.waste_factor::text,
      'unitCode',c.unit_code,'priceBasis',c.price_basis,'sortOrder',c.sort_order,'note',c.note)
      ORDER BY c.sort_order,c.id) FROM public.cost_assembly_component c WHERE c.assembly_id=a.id),'[]'::jsonb))
 FROM public.cost_assembly a WHERE a.id=p_id
   AND public.sakhtyar_material_scope_ok(a.data_scope,a.test_workspace_id)
 $$;

CREATE FUNCTION public.sakhtyar_assembly_revision_guard() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
 BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Assembly revision archive is immutable' USING ERRCODE='42501'; END IF;
 -- A top-level INSERT is not an authorized archive operation.
 IF pg_trigger_depth()<2 OR NOT coalesce(public.sakhtyar_material_scope_ok(NEW.data_scope,NEW.test_workspace_id),false)
    OR NOT EXISTS(SELECT 1 FROM public.cost_assembly a WHERE a.id=NEW.assembly_id AND a.revision=NEW.revision
       AND a.data_scope=NEW.data_scope AND a.test_workspace_id IS NOT DISTINCT FROM NEW.test_workspace_id
       AND a.test_run_id IS NOT DISTINCT FROM NEW.test_run_id)
    OR NEW.definition IS DISTINCT FROM public.sakhtyar_assembly_definition(NEW.assembly_id)
 THEN RAISE EXCEPTION 'Invalid assembly revision archive' USING ERRCODE='42501'; END IF;
 RETURN NEW;
 END $$;
CREATE TRIGGER trg_phase19a_revision_guard BEFORE INSERT OR UPDATE OR DELETE ON public.cost_assembly_revision
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_assembly_revision_guard();

CREATE FUNCTION public.sakhtyar_assembly_revision() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
 BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Archive assembly by deactivation, not deletion' USING ERRCODE='42501'; END IF;
 IF TG_OP='INSERT' THEN
   IF NEW.revision<>1 THEN RAISE EXCEPTION 'Invalid initial revision' USING ERRCODE='23514'; END IF;
   RETURN NEW;
 END IF;
 IF (NEW.id,NEW.code,NEW.output_unit_code,NEW.created_by,NEW.created_at)
    IS DISTINCT FROM (OLD.id,OLD.code,OLD.output_unit_code,OLD.created_by,OLD.created_at)
 THEN RAISE EXCEPTION 'Assembly identity and output unit are immutable' USING ERRCODE='23514'; END IF;
 IF NEW.revision<>OLD.revision AND (pg_trigger_depth()<2 OR NEW.revision<>OLD.revision+1)
 THEN RAISE EXCEPTION 'Revision is database managed' USING ERRCODE='23514'; END IF;
 IF NEW.revision=OLD.revision AND (NEW.name_fa,NEW.name_en,NEW.category,NEW.description,NEW.active,NEW.metadata)
    IS NOT DISTINCT FROM (OLD.name_fa,OLD.name_en,OLD.category,OLD.description,OLD.active,OLD.metadata)
 THEN RETURN NEW; END IF;
 INSERT INTO public.cost_assembly_revision(assembly_id,revision,definition,data_scope,test_workspace_id,test_run_id)
 VALUES(OLD.id,OLD.revision,public.sakhtyar_assembly_definition(OLD.id),OLD.data_scope,OLD.test_workspace_id,OLD.test_run_id);
 NEW.revision:=OLD.revision+1;
 RETURN NEW;
 END $$;
CREATE TRIGGER trg_phase19a_assembly_revision BEFORE INSERT OR UPDATE OR DELETE ON public.cost_assembly
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_assembly_revision();

CREATE FUNCTION public.sakhtyar_assembly_component_revision() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
 DECLARE parent_id uuid;
 BEGIN
 IF TG_OP='UPDATE' THEN
   IF (NEW.id,NEW.assembly_id,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.assembly_id,OLD.created_at)
   THEN RAISE EXCEPTION 'Component identity is immutable' USING ERRCODE='23514'; END IF;
   IF (NEW.material_item_id,NEW.variant_id,NEW.quantity,NEW.waste_factor,NEW.unit_code,NEW.price_basis,NEW.sort_order,NEW.note)
      IS NOT DISTINCT FROM (OLD.material_item_id,OLD.variant_id,OLD.quantity,OLD.waste_factor,OLD.unit_code,OLD.price_basis,OLD.sort_order,OLD.note)
   THEN RETURN NEW; END IF;
 END IF;
 IF TG_OP='DELETE' THEN
   parent_id:=OLD.assembly_id;
   IF EXISTS(SELECT 1 FROM public.cost_estimate_line WHERE assembly_component_id=OLD.id)
   THEN RAISE EXCEPTION 'Component has historical estimates' USING ERRCODE='23503'; END IF;
 ELSE
   parent_id:=NEW.assembly_id;
   IF NEW.quantity<=0 OR NEW.waste_factor<0 OR NEW.waste_factor>10 OR NEW.sort_order<0
      OR NEW.price_basis NOT IN ('MIN','MAX','AVERAGE','MEDIAN')
   THEN RAISE EXCEPTION 'Invalid component definition' USING ERRCODE='23514'; END IF;
 END IF;
 -- Serialize definition changes and estimates on their assembly parent.
 UPDATE public.cost_assembly SET revision=revision+1,updated_at=clock_timestamp()
 WHERE id=parent_id AND (TG_OP='DELETE' OR active);
 IF NOT FOUND THEN RAISE EXCEPTION 'Assembly missing or inactive' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
 END $$;
CREATE TRIGGER trg_phase19a_component_revision BEFORE INSERT OR UPDATE OR DELETE ON public.cost_assembly_component
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_assembly_component_revision();

CREATE FUNCTION public.sakhtyar_estimate_definition() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
 DECLARE a public.cost_assembly%ROWTYPE;
 BEGIN
 SELECT * INTO a FROM public.cost_assembly WHERE id=NEW.assembly_id FOR UPDATE;
 IF NOT FOUND OR NOT a.active OR a.output_unit_code IS DISTINCT FROM NEW.output_unit_code
 THEN RAISE EXCEPTION 'Estimate definition missing, inactive or incompatible' USING ERRCODE='23514'; END IF;
 NEW.definition_revision:=a.revision;
 NEW.assembly_definition:=public.sakhtyar_assembly_definition(a.id);
 IF NEW.assembly_definition IS NULL THEN RAISE EXCEPTION 'Missing estimate definition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
 END $$;
CREATE TRIGGER trg_phase19a_estimate_definition BEFORE INSERT ON public.cost_estimate
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_estimate_definition();

-- A calculation and a definition mutation cannot be mixed for the same assembly in one transaction.
-- Existing V71 deferred amount/line-count seals and history mutation guards remain unchanged.
CREATE FUNCTION public.sakhtyar_estimate_definition_seal() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
 BEGIN
 IF NEW.definition_revision IS NULL OR NEW.assembly_definition IS NULL OR
    NEW.assembly_definition IS DISTINCT FROM public.sakhtyar_assembly_definition(NEW.assembly_id)
 THEN RAISE EXCEPTION 'Estimate definition changed before transaction seal' USING ERRCODE='23514'; END IF;
 RETURN NULL;
 END $$;
CREATE CONSTRAINT TRIGGER trg_phase19a_definition_seal AFTER INSERT ON public.cost_estimate
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_estimate_definition_seal();
