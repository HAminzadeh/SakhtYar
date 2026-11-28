-- Phase 19-B. Forward-only: no existing row rewrite, grants, role changes or REAL business DML.
-- The financial formula remains 2.0. V71 and V72 continue to govern immutable evidence.
ALTER TABLE public.cost_estimate ADD COLUMN request_key uuid,
 ADD COLUMN recalculated_from_id uuid REFERENCES public.cost_estimate(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX ux_phase19b_estimate_request ON public.cost_estimate(assembly_id,calculated_by,request_key)
 WHERE request_key IS NOT NULL;
CREATE INDEX ix_phase19b_estimate_origin ON public.cost_estimate(recalculated_from_id) WHERE recalculated_from_id IS NOT NULL;
CREATE INDEX ix_phase19b_estimate_transaction ON public.cost_estimate(assembly_id,creation_txid) WHERE creation_txid IS NOT NULL;

CREATE FUNCTION public.sakhtyar_phase19b_calculation_guard() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
BEGIN
 IF NEW.requested_quantity<=0 OR NEW.total_cost<=0 OR NEW.unit_cost<=0 OR
    NEW.unit_cost IS DISTINCT FROM round(NEW.total_cost/nullif(NEW.requested_quantity,0),2) THEN
   RAISE EXCEPTION 'Invalid cost estimate unit/total reconciliation' USING ERRCODE='23514';
 END IF;
 IF NEW.recalculated_from_id IS NOT NULL AND (NEW.recalculated_from_id=NEW.id OR NOT EXISTS(
   SELECT 1 FROM public.cost_estimate e WHERE e.id=NEW.recalculated_from_id AND e.assembly_id=NEW.assembly_id
    AND e.data_scope=NEW.data_scope AND e.test_workspace_id IS NOT DISTINCT FROM NEW.test_workspace_id
    AND e.requested_quantity=NEW.requested_quantity AND e.output_unit_code=NEW.output_unit_code
    AND e.currency_code=NEW.currency_code AND e.price_type=NEW.price_type
    AND e.province IS NOT DISTINCT FROM NEW.province AND e.city IS NOT DISTINCT FROM NEW.city
 )) THEN RAISE EXCEPTION 'Invalid recalculation origin' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_phase19b_calculation_guard BEFORE INSERT ON public.cost_estimate
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_phase19b_calculation_guard();

CREATE FUNCTION public.sakhtyar_phase19b_line_guard() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
BEGIN
 IF NEW.unit_price<=0 OR NEW.line_total<=0 OR NOT EXISTS(
   SELECT 1 FROM public.cost_assembly_component c WHERE c.id=NEW.assembly_component_id
     AND upper(c.unit_code)=upper(NEW.unit_code)) THEN
   RAISE EXCEPTION 'Invalid positive cost line or component unit' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_phase19b_line_guard BEFORE INSERT ON public.cost_estimate_line
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_phase19b_line_guard();

-- V72 deferred seal alone can be evaluated early via SET CONSTRAINTS. Prevent later
-- definition changes in that transaction even if the deferred check has already fired.
-- Component writes increment the parent revision via V72 and therefore also hit this guard.
CREATE FUNCTION public.sakhtyar_phase19b_definition_write_guard() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
BEGIN
 IF (NEW.revision,NEW.name_fa,NEW.name_en,NEW.category,NEW.description,NEW.active,NEW.metadata)
    IS DISTINCT FROM (OLD.revision,OLD.name_fa,OLD.name_en,OLD.category,OLD.description,OLD.active,OLD.metadata)
    AND EXISTS(SELECT 1 FROM public.cost_estimate e WHERE e.assembly_id=OLD.id AND e.creation_txid=txid_current()) THEN
   RAISE EXCEPTION 'Definition cannot change after estimate creation in the same transaction' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_phase19b_definition_write_guard BEFORE UPDATE ON public.cost_assembly
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_phase19b_definition_write_guard();
