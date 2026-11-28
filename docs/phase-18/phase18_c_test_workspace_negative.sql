-- Run only on a disposable isolated clone with an existing provisioned workspace login.
-- psql -X -v workspace=<UUID> -f phase18_c_test_workspace_negative.sql
-- No grants, no role creation, no REAL data writes, no commit. Failure is not PASS.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('app.sakhtyar_scope','TEST',true),set_config('app.sakhtyar_workspace',:'workspace',true);
DO $$
DECLARE w uuid:=current_setting('app.sakhtyar_workspace')::uuid; stamp timestamptz:=statement_timestamp(); observed timestamptz:=stamp-interval '1 hour';
 m uuid:=gen_random_uuid(); src uuid:=gen_random_uuid(); obs uuid:=gen_random_uuid(); agg uuid:=gen_random_uuid();
 ass uuid:=gen_random_uuid(); comp uuid:=gen_random_uuid(); est uuid:=gen_random_uuid(); sc uuid:=gen_random_uuid(); item uuid:=gen_random_uuid(); snap uuid:=gen_random_uuid();
BEGIN
 IF session_user<>current_user OR current_user::text<>'sakhtyar_tws_'||replace(w::text,'-','')
 OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb))
 OR NOT public.sakhtyar_material_scope_ok('TEST',w) OR NOT EXISTS(SELECT 1 FROM public.simulation_workspace WHERE id=w AND state='ACTIVE') THEN
  RAISE EXCEPTION 'BLOCKED: restricted TEST workspace login required';
 END IF;
 INSERT INTO public.knowledge_source(id,source_code,name,source_type,trust_level,enabled,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(src,'TEST-P18C-'||src,'Synthetic only','MANUAL','UNVERIFIED',true,stamp,stamp,'TEST',w);
 INSERT INTO public.material_item(id,code,name_fa,category,unit_code,active,created_by,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(m,'TEST-P18C-'||m,'Synthetic material','TEST','KG',true,'test',stamp,stamp,'TEST',w);
 INSERT INTO public.material_price_observation(id,material_item_id,price_type,amount,currency_code,unit_code,quantity_basis,source_id,observed_at,collected_at,confidence,review_status,origin,created_by,data_scope,test_workspace_id,quote_key)
 VALUES(obs,m,'RETAIL',1500000,'TOMAN','KG',50,src,observed,stamp,0.9,'PENDING_REVIEW','ADMIN','test','TEST',w,repeat('c',64));
 UPDATE public.material_price_observation SET review_status='APPROVED',reviewed_by='test-reviewer',reviewed_at=stamp,review_note='Synthetic TEST' WHERE id=obs;
 INSERT INTO public.material_price_aggregate(id,material_item_id,price_type,currency_code,unit_code,province,city,sample_count,min_amount,max_amount,average_amount,median_amount,period_start,period_end,algorithm_version,calculated_at,data_scope,test_workspace_id,current_reference,expires_at,publication_key,evidence_ids)
 VALUES(agg,m,'RETAIL','TOMAN','KG','TEST','TEST',1,30000,30000,30000,30000,observed,observed,'2.0',stamp,'TEST',w,true,observed+interval '720 hours',repeat('d',64),jsonb_build_array(obs::text));
 INSERT INTO public.cost_assembly(id,code,name_fa,category,output_unit_code,active,created_by,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(ass,'TEST-P18C-'||ass,'TEST assembly','TEST','M2',true,'test',stamp,stamp,'TEST',w);
 INSERT INTO public.cost_assembly_component(id,assembly_id,material_item_id,quantity,waste_factor,unit_code,price_basis,created_at,data_scope,test_workspace_id)
 VALUES(comp,ass,m,2,0.10,'KG','MEDIAN',stamp,'TEST',w);
 INSERT INTO public.cost_estimate(id,assembly_id,requested_quantity,output_unit_code,price_type,currency_code,province,city,unit_cost,total_cost,algorithm_version,calculated_by,calculated_at,data_scope,test_workspace_id)
 VALUES(est,ass,5,'M2','RETAIL','TOMAN','TEST','TEST',66000,330000,'2.0','test',stamp,'TEST',w);
 INSERT INTO public.cost_estimate_line(id,estimate_id,assembly_component_id,material_item_id,aggregate_id,base_quantity,waste_factor,effective_quantity,unit_code,unit_price,line_total,price_basis,source_sample_count,source_period_start,source_period_end,data_scope,test_workspace_id)
 VALUES(gen_random_uuid(),est,comp,m,agg,2,0.10,11,'KG',30000,330000,'MEDIAN',1,observed,observed,'TEST',w);
 INSERT INTO public.construction_scenario(id,code,name_fa,scenario_status,active,created_by,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(sc,'TEST-P18C-'||sc,'TEST scenario','READY',true,'test',stamp,stamp,'TEST',w);
 INSERT INTO public.construction_scenario_item(id,scenario_id,assembly_id,quantity,source,enabled,created_at,data_scope,test_workspace_id)
 VALUES(item,sc,ass,5,'MANUAL',true,stamp,'TEST',w);
 INSERT INTO public.scenario_cost_snapshot(id,scenario_id,price_type,currency_code,province,city,total_cost,algorithm_version,calculated_by,calculated_at,data_scope,test_workspace_id)
 VALUES(snap,sc,'RETAIL','TOMAN','TEST','TEST',330000,'2.0','test',stamp,'TEST',w);
 INSERT INTO public.scenario_cost_snapshot_line(id,snapshot_id,scenario_item_id,assembly_id,assembly_estimate_id,quantity,unit_cost,line_total,data_scope,test_workspace_id)
 VALUES(gen_random_uuid(),snap,item,ass,est,5,66000,330000,'TEST',w);
 SET CONSTRAINTS ALL IMMEDIATE;
 IF (SELECT total_cost FROM public.scenario_cost_snapshot WHERE id=snap)<>330000 THEN RAISE EXCEPTION 'FAIL: fixed cost oracle'; END IF;
 RAISE NOTICE 'PASS: SQL fixture chain fixed expected total 330000';
 BEGIN
   UPDATE public.scenario_cost_snapshot SET total_cost=1 WHERE id=snap;
   RAISE EXCEPTION 'FAIL: snapshot update accepted';
 EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: snapshot mutation denied'; END;
 BEGIN
   DELETE FROM public.cost_estimate_line WHERE estimate_id=est;
   RAISE EXCEPTION 'FAIL: estimate line deletion accepted';
 EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: estimate deletion denied'; END;
 UPDATE public.material_price_aggregate SET current_reference=false WHERE id=agg;
 IF NOT EXISTS(SELECT 1 FROM public.cost_estimate_line WHERE estimate_id=est AND aggregate_id=agg AND line_total=330000) THEN RAISE EXCEPTION 'FAIL: historical cost changed'; END IF;
 PERFORM set_config('app.sakhtyar_scope','REAL',true);
 IF EXISTS(SELECT 1 FROM public.cost_assembly) OR EXISTS(SELECT 1 FROM public.scenario_cost_snapshot) THEN RAISE EXCEPTION 'FAIL: workspace login sees REAL'; END IF;
 PERFORM set_config('app.sakhtyar_scope','TEST',true);
 PERFORM set_config('app.sakhtyar_workspace',gen_random_uuid()::text,true);
 IF EXISTS(SELECT 1 FROM public.cost_estimate) OR EXISTS(SELECT 1 FROM public.scenario_cost_snapshot_line) THEN RAISE EXCEPTION 'FAIL: cross-workspace read'; END IF;
 RAISE NOTICE 'PASS: immutable price linkage and denied alternate scope';
END $$;
ROLLBACK;
