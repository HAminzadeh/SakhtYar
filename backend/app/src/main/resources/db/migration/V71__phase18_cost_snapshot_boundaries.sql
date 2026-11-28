-- Phase18-C: forward-only cost chain protection. No role grants, external effects or business backfill.
-- Existing rows are not rewritten. Legacy unscoped child tables must have REAL parents.
ALTER TABLE public.cost_estimate ALTER COLUMN currency_code TYPE varchar(8);
-- Transaction identity is assigned only to newly created snapshots. Legacy rows remain NULL.
ALTER TABLE public.cost_estimate ADD COLUMN creation_txid bigint;
ALTER TABLE public.scenario_cost_snapshot ADD COLUMN creation_txid bigint;
ALTER TABLE public.quality_package_selection ADD COLUMN data_scope varchar(8) NOT NULL DEFAULT 'REAL',
 ADD COLUMN test_workspace_id uuid, ADD COLUMN test_run_id uuid;
ALTER TABLE public.scenario_cost_snapshot_line ADD COLUMN data_scope varchar(8) NOT NULL DEFAULT 'REAL',
 ADD COLUMN test_workspace_id uuid, ADD COLUMN test_run_id uuid;
ALTER TABLE public.feasibility_reason ADD COLUMN data_scope varchar(8) NOT NULL DEFAULT 'REAL',
 ADD COLUMN test_workspace_id uuid, ADD COLUMN test_run_id uuid;
-- The existing workspace function binds TEST to a physically authenticated workspace role.
-- Preflight deliberately rejects cross-scope legacy rows rather than reclassifying them.
DO $$
DECLARE e record; bad boolean;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls)) THEN
   RAISE EXCEPTION 'V71 requires an authorized migration principal that can inspect all scoped rows';
 END IF;
 FOR e IN SELECT * FROM (VALUES
 ('cost_assembly_component','assembly_id','cost_assembly'),('cost_assembly_component','material_item_id','material_item'),('cost_assembly_component','variant_id','material_variant'),
 ('cost_estimate','assembly_id','cost_assembly'),('cost_estimate_line','estimate_id','cost_estimate'),('cost_estimate_line','assembly_component_id','cost_assembly_component'),('cost_estimate_line','material_item_id','material_item'),('cost_estimate_line','variant_id','material_variant'),('cost_estimate_line','aggregate_id','material_price_aggregate'),
 ('quality_package_selection','package_id','quality_package'),('quality_package_selection','assembly_id','cost_assembly'),('construction_scenario','quality_package_id','quality_package'),('construction_scenario_item','scenario_id','construction_scenario'),('construction_scenario_item','assembly_id','cost_assembly'),('scenario_cost_snapshot','scenario_id','construction_scenario'),('scenario_cost_snapshot_line','snapshot_id','scenario_cost_snapshot'),('scenario_cost_snapshot_line','scenario_item_id','construction_scenario_item'),('scenario_cost_snapshot_line','assembly_id','cost_assembly'),('scenario_cost_snapshot_line','assembly_estimate_id','cost_estimate'),
 ('feasibility_assessment','case_id','construction_case'),('feasibility_assessment','property_id','property'),('feasibility_assessment','scenario_id','construction_scenario'),('feasibility_assessment','scenario_cost_snapshot_id','scenario_cost_snapshot'),('feasibility_assessment','urban_evaluation_id','urban_evaluation'),('feasibility_reason','assessment_id','feasibility_assessment'),('financial_analysis','feasibility_assessment_id','feasibility_assessment'),('financial_analysis','case_id','construction_case'),('financial_analysis','property_id','property'),('financial_analysis','scenario_id','construction_scenario'),('financial_participation_allocation','financial_analysis_id','financial_analysis')
 ) x(child_table,child_column,parent_table) LOOP
 EXECUTE format('SELECT EXISTS(SELECT 1 FROM public.%I c JOIN public.%I p ON p.id=c.%I WHERE (c.data_scope,c.test_workspace_id) IS DISTINCT FROM (p.data_scope,p.test_workspace_id))',e.child_table,e.parent_table,e.child_column) INTO bad;
 IF bad THEN RAISE EXCEPTION 'V71 legacy cross-scope link: %.%',e.child_table,e.child_column; END IF;
 END LOOP;
END $$;
CREATE FUNCTION public.sakhtyar_cost_scope_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p_scope varchar; p_workspace uuid; p_id uuid; i integer; j jsonb;
BEGIN
 IF TG_OP='DELETE' THEN
   IF TG_TABLE_NAME IN ('cost_estimate','cost_estimate_line','scenario_cost_snapshot','scenario_cost_snapshot_line') THEN RAISE EXCEPTION 'Cost snapshot history is immutable' USING ERRCODE='42501'; END IF;
   IF NOT public.sakhtyar_material_scope_ok(OLD.data_scope,OLD.test_workspace_id) THEN RAISE EXCEPTION 'Cost scope denied' USING ERRCODE='42501'; END IF;
   RETURN OLD;
 END IF;
 IF NOT public.sakhtyar_material_scope_ok(NEW.data_scope,NEW.test_workspace_id) THEN RAISE EXCEPTION 'Cost scope denied' USING ERRCODE='42501'; END IF;
 IF TG_OP='UPDATE' THEN
   IF TG_TABLE_NAME IN ('cost_estimate','cost_estimate_line','scenario_cost_snapshot','scenario_cost_snapshot_line') THEN RAISE EXCEPTION 'Cost snapshot history is immutable' USING ERRCODE='42501'; END IF;
   IF (OLD.data_scope,OLD.test_workspace_id,OLD.test_run_id) IS DISTINCT FROM (NEW.data_scope,NEW.test_workspace_id,NEW.test_run_id) THEN RAISE EXCEPTION 'Cost scope identity is immutable' USING ERRCODE='42501'; END IF;
 END IF;
 IF NEW.data_scope='TEST' AND NOT EXISTS(SELECT 1 FROM public.simulation_workspace WHERE id=NEW.test_workspace_id AND state='ACTIVE') THEN RAISE EXCEPTION 'Invalid TEST workspace' USING ERRCODE='23503'; END IF;
 j:=to_jsonb(NEW); i:=0;
 WHILE i<TG_NARGS LOOP
   p_id:=(j->>TG_ARGV[i])::uuid;
   IF p_id IS NOT NULL THEN
     EXECUTE format('SELECT data_scope,test_workspace_id FROM public.%I WHERE id=$1',TG_ARGV[i+1]) INTO p_scope,p_workspace USING p_id;
     IF p_scope IS NULL OR (p_scope,p_workspace) IS DISTINCT FROM (NEW.data_scope,NEW.test_workspace_id) THEN RAISE EXCEPTION 'Cost parent scope mismatch: %.%',TG_TABLE_NAME,TG_ARGV[i] USING ERRCODE='23503'; END IF;
   END IF;
   i:=i+2;
 END LOOP;
 IF TG_TABLE_NAME IN ('cost_estimate','scenario_cost_snapshot') AND TG_OP='INSERT' THEN
   IF NEW.algorithm_version<>'2.0' OR NEW.total_cost<=0 THEN RAISE EXCEPTION 'New governed cost snapshot required' USING ERRCODE='23514'; END IF;
   NEW.creation_txid:=txid_current();
 END IF;
 IF TG_TABLE_NAME='cost_assembly_component' THEN
   IF NOT EXISTS(SELECT 1 FROM public.material_item m WHERE m.id=NEW.material_item_id AND m.active AND upper(m.unit_code)=upper(NEW.unit_code))
    OR (NEW.variant_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.material_variant v WHERE v.id=NEW.variant_id AND v.active AND v.material_item_id=NEW.material_item_id)) THEN RAISE EXCEPTION 'Invalid assembly material or variant' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='cost_estimate_line' THEN
   IF NOT EXISTS(SELECT 1 FROM public.cost_estimate e JOIN public.cost_assembly_component c ON c.id=NEW.assembly_component_id AND c.assembly_id=e.assembly_id
     JOIN public.material_price_aggregate a ON a.id=NEW.aggregate_id AND a.material_item_id=c.material_item_id AND a.variant_id IS NOT DISTINCT FROM c.variant_id
     WHERE e.id=NEW.estimate_id AND e.creation_txid=txid_current() AND a.current_reference AND a.algorithm_version='2.0' AND a.expires_at>statement_timestamp()
       AND a.currency_code=e.currency_code AND a.price_type=e.price_type AND upper(a.unit_code)=upper(NEW.unit_code)
       AND c.material_item_id=NEW.material_item_id AND c.variant_id IS NOT DISTINCT FROM NEW.variant_id
       AND e.province IS NOT DISTINCT FROM a.province AND e.city IS NOT DISTINCT FROM a.city
       AND NEW.price_basis=c.price_basis AND NEW.base_quantity=c.quantity AND NEW.waste_factor=c.waste_factor
       AND NEW.effective_quantity=round(c.quantity*(1+c.waste_factor)*e.requested_quantity,6)
       AND NOT EXISTS(SELECT 1 FROM public.cost_estimate_line l WHERE l.estimate_id=e.id AND l.assembly_component_id=c.id)
       AND EXISTS(SELECT 1 FROM public.material_item m WHERE m.id=a.material_item_id AND m.active)
       AND (a.variant_id IS NULL OR EXISTS(SELECT 1 FROM public.material_variant v WHERE v.id=a.variant_id AND v.active))
       AND EXISTS(SELECT 1 FROM public.global_currency gc WHERE gc.id=a.currency_id AND gc.active)
       AND NOT EXISTS(SELECT 1 FROM jsonb_array_elements_text(a.evidence_ids) x(id)
          LEFT JOIN public.material_price_observation o ON o.id=cast(x.id as uuid)
          LEFT JOIN public.knowledge_source src ON src.id=o.source_id AND src.data_scope=a.data_scope AND src.test_workspace_id IS NOT DISTINCT FROM a.test_workspace_id
          LEFT JOIN public.global_currency gc ON gc.id=o.currency_id
          WHERE o.id IS NULL OR o.material_item_id<>a.material_item_id OR o.data_scope<>a.data_scope OR o.test_workspace_id IS DISTINCT FROM a.test_workspace_id OR src.id IS NULL OR NOT src.enabled OR gc.id IS NULL OR NOT gc.active
             OR o.review_status<>'APPROVED' OR o.confidence IS NULL OR o.confidence<0.50
             OR o.observed_at>statement_timestamp() OR o.observed_at+interval '720 hours'<=statement_timestamp()
             OR EXISTS(SELECT 1 FROM public.material_price_anomaly n WHERE n.observation_id=o.id AND n.status<>'DISMISSED'))
       AND NEW.source_sample_count=a.sample_count AND NEW.source_period_start=a.period_start AND NEW.source_period_end=a.period_end
       AND NEW.unit_price=CASE NEW.price_basis WHEN 'MIN' THEN a.min_amount WHEN 'MAX' THEN a.max_amount WHEN 'AVERAGE' THEN a.average_amount WHEN 'MEDIAN' THEN a.median_amount END
       AND NEW.line_total=round(NEW.effective_quantity*NEW.unit_price,2)) THEN RAISE EXCEPTION 'Cost line lacks matching current price evidence' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='scenario_cost_snapshot_line' THEN
   IF NOT EXISTS(SELECT 1 FROM public.scenario_cost_snapshot s JOIN public.construction_scenario_item i ON i.id=NEW.scenario_item_id AND i.scenario_id=s.scenario_id
     JOIN public.cost_estimate e ON e.id=NEW.assembly_estimate_id AND e.assembly_id=i.assembly_id
     WHERE s.id=NEW.snapshot_id AND s.creation_txid=txid_current() AND e.creation_txid=txid_current()
       AND s.province IS NOT DISTINCT FROM e.province AND s.city IS NOT DISTINCT FROM e.city
       AND NOT EXISTS(SELECT 1 FROM public.scenario_cost_snapshot_line l WHERE l.snapshot_id=s.id AND l.scenario_item_id=i.id)
       AND e.assembly_id=NEW.assembly_id AND e.currency_code=s.currency_code AND e.price_type=s.price_type
       AND e.requested_quantity=NEW.quantity AND i.quantity=NEW.quantity AND e.total_cost=NEW.line_total AND e.unit_cost=NEW.unit_cost) THEN RAISE EXCEPTION 'Scenario snapshot line mismatch' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='feasibility_assessment' THEN
   IF NOT EXISTS(SELECT 1 FROM public.scenario_cost_snapshot s WHERE s.id=NEW.scenario_cost_snapshot_id AND s.scenario_id=NEW.scenario_id AND s.algorithm_version='2.0' AND s.total_cost=NEW.scenario_cost AND s.currency_code=NEW.cost_currency_code) THEN RAISE EXCEPTION 'Feasibility snapshot mismatch' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='financial_analysis' THEN
   IF NOT EXISTS(SELECT 1 FROM public.feasibility_assessment f WHERE f.id=NEW.feasibility_assessment_id AND f.case_id=NEW.case_id AND f.property_id=NEW.property_id AND f.scenario_id=NEW.scenario_id AND f.cost_currency_code=NEW.currency_code AND f.scenario_cost=NEW.base_construction_cost AND f.input_snapshot->>'materialCostAlgorithmVersion'='2.0') THEN RAISE EXCEPTION 'Financial feasibility mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
DO $$
DECLARE e record;
BEGIN
 FOR e IN SELECT * FROM (VALUES
 ('cost_assembly',''),('cost_assembly_component','''assembly_id'',''cost_assembly'',''material_item_id'',''material_item'',''variant_id'',''material_variant'''),('cost_estimate','''assembly_id'',''cost_assembly'''),('cost_estimate_line','''estimate_id'',''cost_estimate'',''assembly_component_id'',''cost_assembly_component'',''material_item_id'',''material_item'',''variant_id'',''material_variant'',''aggregate_id'',''material_price_aggregate'''),
 ('quality_package',''),('quality_package_selection','''package_id'',''quality_package'',''assembly_id'',''cost_assembly'''),('construction_scenario','''quality_package_id'',''quality_package'''),('construction_scenario_item','''scenario_id'',''construction_scenario'',''assembly_id'',''cost_assembly'''),('scenario_cost_snapshot','''scenario_id'',''construction_scenario'''),('scenario_cost_snapshot_line','''snapshot_id'',''scenario_cost_snapshot'',''scenario_item_id'',''construction_scenario_item'',''assembly_id'',''cost_assembly'',''assembly_estimate_id'',''cost_estimate'''),
 ('feasibility_assessment','''case_id'',''construction_case'',''property_id'',''property'',''scenario_id'',''construction_scenario'',''scenario_cost_snapshot_id'',''scenario_cost_snapshot'',''urban_evaluation_id'',''urban_evaluation'''),('feasibility_reason','''assessment_id'',''feasibility_assessment'''),('financial_analysis','''feasibility_assessment_id'',''feasibility_assessment'',''case_id'',''construction_case'',''property_id'',''property'',''scenario_id'',''construction_scenario'''),('financial_participation_allocation','''financial_analysis_id'',''financial_analysis''')
 ) x(tbl,args) LOOP
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',e.tbl);
 EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY',e.tbl);
 EXECUTE format('CREATE POLICY phase18c_cost_allow ON public.%I FOR ALL USING (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id)) WITH CHECK (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id))',e.tbl);
 EXECUTE format('CREATE POLICY phase18c_cost_limit ON public.%I AS RESTRICTIVE FOR ALL USING (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id)) WITH CHECK (public.sakhtyar_material_scope_ok(data_scope,test_workspace_id))',e.tbl);
 EXECUTE format('CREATE TRIGGER trg_phase18c_cost_scope BEFORE INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_cost_scope_guard(%s)',e.tbl,e.args);
 END LOOP;
END $$;
-- Ensure newly scoped children follow the same scope marker contract as V58.
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['quality_package_selection','scenario_cost_snapshot_line','feasibility_reason'] LOOP
 EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I CHECK ((data_scope=''REAL'' AND test_workspace_id IS NULL AND test_run_id IS NULL) OR (data_scope=''TEST'' AND test_workspace_id IS NOT NULL))',t,'chk_'||t||'_phase18c_scope');
 EXECUTE format('CREATE INDEX %I ON public.%I(test_workspace_id) WHERE data_scope=''TEST''','ix_'||t||'_phase18c_workspace',t);
 END LOOP; END $$;

-- Deferred transaction seal: incomplete or inconsistent snapshots cannot commit.
-- A newly inserted line must belong to a parent created in this transaction.
CREATE FUNCTION public.sakhtyar_cost_snapshot_seal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p uuid; expected numeric; actual numeric; cnt bigint; required_count bigint;
BEGIN
 IF TG_TABLE_NAME IN ('cost_estimate','cost_estimate_line') THEN
   IF TG_TABLE_NAME='cost_estimate' THEN p:=NEW.id; ELSE p:=NEW.estimate_id; END IF;
   SELECT total_cost INTO expected FROM public.cost_estimate WHERE id=p;
   SELECT count(*),coalesce(sum(line_total),0) INTO cnt,actual FROM public.cost_estimate_line WHERE estimate_id=p;
   SELECT count(*) INTO required_count FROM public.cost_estimate e JOIN public.cost_assembly_component c ON c.assembly_id=e.assembly_id WHERE e.id=p;
 ELSE
   IF TG_TABLE_NAME='scenario_cost_snapshot' THEN p:=NEW.id; ELSE p:=NEW.snapshot_id; END IF;
   SELECT total_cost INTO expected FROM public.scenario_cost_snapshot WHERE id=p;
   SELECT count(*),coalesce(sum(line_total),0) INTO cnt,actual FROM public.scenario_cost_snapshot_line WHERE snapshot_id=p;
   SELECT count(*) INTO required_count FROM public.scenario_cost_snapshot s JOIN public.construction_scenario_item i ON i.scenario_id=s.scenario_id WHERE s.id=p AND i.enabled;
 END IF;
 IF expected IS NULL OR cnt=0 OR cnt<>required_count OR actual<>expected THEN
   RAISE EXCEPTION 'Cost snapshot is incomplete or totals do not reconcile' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['cost_estimate','cost_estimate_line','scenario_cost_snapshot','scenario_cost_snapshot_line'] LOOP
 EXECUTE format('CREATE CONSTRAINT TRIGGER trg_phase18c_cost_seal AFTER INSERT ON public.%I DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_cost_snapshot_seal()',t);
 END LOOP; END $$;
