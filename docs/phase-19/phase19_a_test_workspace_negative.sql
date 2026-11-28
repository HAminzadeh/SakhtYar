-- Execute ONLY on a disposable clone using a physically authenticated restricted TEST workspace login.
-- psql -X -v workspace=<UUID> -f phase19_a_test_workspace_negative.sql
-- Transaction is rolled back; no REAL writes or role grants. Any error means BLOCKED/FAIL, not PASS.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('app.sakhtyar_scope','TEST',true),set_config('app.sakhtyar_workspace',:'workspace',true);
DO $$
DECLARE w uuid:=current_setting('app.sakhtyar_workspace')::uuid; stamp timestamptz:=statement_timestamp(); observed timestamptz:=stamp-interval '1 hour';
 m uuid:=gen_random_uuid(); src uuid:=gen_random_uuid(); obs uuid:=gen_random_uuid(); agg uuid:=gen_random_uuid();
 ass uuid:=gen_random_uuid(); comp uuid:=gen_random_uuid(); est uuid:=gen_random_uuid(); other uuid:=gen_random_uuid(); saved jsonb;
BEGIN
 IF session_user<>current_user OR current_user::text<>'sakhtyar_tws_'||replace(w::text,'-','')
 OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb))
 OR NOT coalesce(public.sakhtyar_material_scope_ok('TEST',w),false)
 OR NOT EXISTS(SELECT 1 FROM public.simulation_workspace WHERE id=w AND state='ACTIVE') THEN
   RAISE EXCEPTION 'BLOCKED: restricted active TEST workspace login required';
 END IF;
 INSERT INTO public.knowledge_source(id,source_code,name,source_type,trust_level,enabled,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(src,'TEST-P19A-'||src,'Synthetic only','MANUAL','UNVERIFIED',true,stamp,stamp,'TEST',w);
 INSERT INTO public.material_item(id,code,name_fa,category,unit_code,active,created_by,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(m,'TEST-P19A-'||m,'Synthetic material','TEST','KG',true,'test',stamp,stamp,'TEST',w);
 INSERT INTO public.material_price_observation(id,material_item_id,price_type,amount,currency_code,unit_code,quantity_basis,source_id,observed_at,collected_at,confidence,review_status,origin,created_by,data_scope,test_workspace_id,quote_key)
 VALUES(obs,m,'RETAIL',1500000,'TOMAN','KG',50,src,observed,stamp,0.9,'PENDING_REVIEW','ADMIN','test','TEST',w,repeat('c',64));
 UPDATE public.material_price_observation SET review_status='APPROVED',reviewed_by='test-reviewer',reviewed_at=stamp,review_note='Synthetic TEST' WHERE id=obs;
 INSERT INTO public.material_price_aggregate(id,material_item_id,price_type,currency_code,unit_code,province,city,sample_count,min_amount,max_amount,average_amount,median_amount,period_start,period_end,algorithm_version,calculated_at,data_scope,test_workspace_id,current_reference,expires_at,publication_key,evidence_ids)
 VALUES(agg,m,'RETAIL','TOMAN','KG','TEST','TEST',1,30000,30000,30000,30000,observed,observed,'2.0',stamp,'TEST',w,true,observed+interval '720 hours',repeat('d',64),jsonb_build_array(obs::text));
 INSERT INTO public.cost_assembly(id,code,name_fa,category,output_unit_code,active,created_by,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(ass,'TEST-P19A-'||ass,'TEST assembly','TEST','M2',true,'test',stamp,stamp,'TEST',w);
 IF (SELECT revision FROM public.cost_assembly WHERE id=ass) IS DISTINCT FROM 1::bigint THEN RAISE EXCEPTION 'FAIL initial revision'; END IF;
 INSERT INTO public.cost_assembly_component(id,assembly_id,material_item_id,quantity,waste_factor,unit_code,price_basis,created_at,data_scope,test_workspace_id)
 VALUES(comp,ass,m,2,0.10,'KG','MEDIAN',stamp,'TEST',w);
 IF (SELECT revision FROM public.cost_assembly WHERE id=ass) IS DISTINCT FROM 2::bigint OR
   (SELECT definition->'components' FROM public.cost_assembly_revision WHERE assembly_id=ass AND revision=1) IS DISTINCT FROM '[]'::jsonb
 THEN RAISE EXCEPTION 'FAIL revision after add'; END IF;
 UPDATE public.cost_assembly_component SET quantity=3 WHERE id=comp;
 IF (SELECT definition->'components'->0->>'quantity' FROM public.cost_assembly_revision WHERE assembly_id=ass AND revision=2) IS DISTINCT FROM '2.000000' THEN RAISE EXCEPTION 'FAIL historical component value'; END IF;
 UPDATE public.cost_assembly_component SET quantity=2 WHERE id=comp;
 UPDATE public.cost_assembly_component SET quantity=2 WHERE id=comp;
 IF (SELECT revision FROM public.cost_assembly WHERE id=ass) IS DISTINCT FROM 4::bigint THEN RAISE EXCEPTION 'FAIL no-op revision'; END IF;
 BEGIN
   UPDATE public.cost_assembly SET output_unit_code='M3' WHERE id=ass;
   RAISE EXCEPTION 'FAIL unit changed'; EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
   UPDATE public.cost_assembly SET revision=99 WHERE id=ass;
   RAISE EXCEPTION 'FAIL revision tampered'; EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
   INSERT INTO public.cost_assembly_revision(assembly_id,revision,definition,data_scope,test_workspace_id)
   VALUES(ass,4,public.sakhtyar_assembly_definition(ass),'TEST',w);
   RAISE EXCEPTION 'FAIL archive forged'; EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 INSERT INTO public.cost_estimate(id,assembly_id,requested_quantity,output_unit_code,price_type,currency_code,province,city,unit_cost,total_cost,algorithm_version,calculated_by,calculated_at,data_scope,test_workspace_id)
 VALUES(est,ass,5,'M2','RETAIL','TOMAN','TEST','TEST',66000,330000,'2.0','test',stamp,'TEST',w);
 INSERT INTO public.cost_estimate_line(id,estimate_id,assembly_component_id,material_item_id,aggregate_id,base_quantity,waste_factor,effective_quantity,unit_code,unit_price,line_total,price_basis,source_sample_count,source_period_start,source_period_end,data_scope,test_workspace_id)
 VALUES(gen_random_uuid(),est,comp,m,agg,2,0.10,11,'KG',30000,330000,'MEDIAN',1,observed,observed,'TEST',w);
 SET CONSTRAINTS ALL IMMEDIATE;
 SELECT assembly_definition INTO saved FROM public.cost_estimate WHERE id=est;
 IF saved IS NULL OR (SELECT definition_revision FROM public.cost_estimate WHERE id=est) IS DISTINCT FROM 4::bigint THEN RAISE EXCEPTION 'FAIL missing estimate definition'; END IF;
 IF EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='public.cost_assembly'::regclass AND tgname='trg_phase19b_definition_write_guard') THEN
   -- V73 strengthens V72: same-transaction changes after an early seal must fail.
   BEGIN
     UPDATE public.cost_assembly_component SET quantity=4 WHERE id=comp;
     RAISE EXCEPTION 'FAIL definition changed after estimate in same transaction';
   EXCEPTION WHEN check_violation THEN NULL; END;
   IF (SELECT revision FROM public.cost_assembly WHERE id=ass) IS DISTINCT FROM 4::bigint
      OR (SELECT quantity FROM public.cost_assembly_component WHERE id=comp) IS DISTINCT FROM 2::numeric
      OR (SELECT assembly_definition FROM public.cost_estimate WHERE id=est) IS DISTINCT FROM saved
   THEN RAISE EXCEPTION 'FAIL protected definition or estimate changed'; END IF;
 ELSE
   UPDATE public.cost_assembly_component SET quantity=4 WHERE id=comp;
   IF (SELECT assembly_definition FROM public.cost_estimate WHERE id=est) IS DISTINCT FROM saved OR
      (SELECT definition FROM public.cost_assembly_revision WHERE assembly_id=ass AND revision=4) IS DISTINCT FROM saved
   THEN RAISE EXCEPTION 'FAIL historical estimate or definition changed'; END IF;
 END IF;
 BEGIN
   DELETE FROM public.cost_assembly_component WHERE id=comp;
   RAISE EXCEPTION 'FAIL referenced component deleted'; EXCEPTION WHEN foreign_key_violation OR insufficient_privilege THEN NULL;
 END;
 BEGIN
   UPDATE public.cost_assembly_revision SET definition='{}'::jsonb WHERE assembly_id=ass AND revision=2;
   RAISE EXCEPTION 'FAIL revision changed'; EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   DELETE FROM public.cost_assembly_revision WHERE assembly_id=ass AND revision=2;
   RAISE EXCEPTION 'FAIL revision deleted'; EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 PERFORM set_config('app.sakhtyar_workspace',other::text,true);
 IF EXISTS(SELECT 1 FROM public.cost_assembly_revision WHERE assembly_id=ass) OR public.sakhtyar_assembly_definition(ass) IS NOT NULL THEN RAISE EXCEPTION 'FAIL cross-workspace definition visible'; END IF;
 PERFORM set_config('app.sakhtyar_workspace',w::text,true);
 PERFORM set_config('app.sakhtyar_scope','REAL',true);
 IF EXISTS(SELECT 1 FROM public.cost_assembly_revision) OR public.sakhtyar_assembly_definition(ass) IS NOT NULL THEN RAISE EXCEPTION 'FAIL TEST login accessed REAL scope'; END IF;
 RAISE NOTICE 'PASS: revision, immutability, estimate binding, restricted scopes; rolling back synthetic fixture';
END $$;
ROLLBACK;
