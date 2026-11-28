-- Run ONLY on an explicitly disposable, isolated clone with an existing provisioned
-- dedicated TEST workspace login and narrowly scoped grants. Never run on a real DB.
-- psql -X -v workspace=<existing UUID> -f phase18_b_test_workspace_negative.sql
-- No role creation, no production credentials, no REAL write, no commit. Errors rollback on psql exit.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('app.sakhtyar_scope','TEST',true),set_config('app.sakhtyar_workspace',:'workspace',true);
DO $$
DECLARE w uuid:=current_setting('app.sakhtyar_workspace')::uuid;
        m uuid:=gen_random_uuid(); s uuid:=gen_random_uuid(); o uuid:=gen_random_uuid(); a uuid:=gen_random_uuid(); n uuid:=gen_random_uuid();
        stamp timestamptz:=statement_timestamp(); observed timestamptz:=stamp-interval '1 hour';
BEGIN
 IF session_user<>current_user OR current_user::text<>'sakhtyar_tws_'||replace(w::text,'-','')
 OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb))
 OR NOT public.sakhtyar_material_scope_ok('TEST',w)
 OR NOT EXISTS(SELECT 1 FROM public.simulation_workspace WHERE id=w AND state='ACTIVE') THEN
  RAISE EXCEPTION 'BLOCKED: dedicated restricted active TEST workspace required';
 END IF;
 INSERT INTO public.knowledge_source(id,source_code,name,source_type,trust_level,enabled,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(s,'TEST-P18B-'||s,'Synthetic fixture only','MANUAL','UNVERIFIED',true,stamp,stamp,'TEST',w);
 INSERT INTO public.material_item(id,code,name_fa,category,unit_code,active,created_by,created_at,updated_at,data_scope,test_workspace_id)
 VALUES(m,'TEST-P18B-'||m,'Synthetic material','TEST','KG',true,'test',stamp,stamp,'TEST',w);
 INSERT INTO public.material_price_observation(id,material_item_id,price_type,amount,currency_code,unit_code,quantity_basis,source_id,observed_at,collected_at,confidence,review_status,origin,created_by,data_scope,test_workspace_id,quote_key)
 VALUES(o,m,'RETAIL',1500000,'TOMAN','KG',50,s,observed,stamp,0.9,'PENDING_REVIEW','ADMIN','test','TEST',w,repeat('a',64));
 BEGIN
   UPDATE public.material_price_observation SET amount=999 WHERE id=o;
   RAISE EXCEPTION 'FAIL: raw price changed';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: raw evidence immutable'; END;
 BEGIN
   INSERT INTO public.material_price_observation(id,material_item_id,price_type,amount,currency_code,unit_code,quantity_basis,source_id,observed_at,collected_at,confidence,review_status,origin,created_by,data_scope,test_workspace_id,quote_key)
   VALUES(gen_random_uuid(),m,'RETAIL',1500000,'TOMAN','KG',50,s,observed,stamp,0.9,'PENDING_REVIEW','ADMIN','test','TEST',w,repeat('a',64));
   RAISE EXCEPTION 'FAIL: duplicate quote accepted';
 EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: duplicate key blocked'; END;
 BEGIN
   UPDATE public.material_price_observation SET review_status='APPROVED' WHERE id=o;
   RAISE EXCEPTION 'FAIL: approval without reason accepted';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: review reason required'; END;
 UPDATE public.material_price_observation SET review_status='APPROVED',reviewed_by='test-reviewer',reviewed_at=stamp,review_note='Synthetic TEST decision' WHERE id=o;
 INSERT INTO public.material_price_aggregate(id,material_item_id,price_type,currency_code,unit_code,sample_count,min_amount,max_amount,average_amount,median_amount,period_start,period_end,algorithm_version,calculated_at,data_scope,test_workspace_id,current_reference,expires_at,publication_key,evidence_ids)
 VALUES(a,m,'RETAIL','TOMAN','KG',1,30000,30000,30000,30000,observed,observed,'2.0',stamp,'TEST',w,true,observed+interval '720 hours',repeat('b',64),jsonb_build_array(o::text));
 UPDATE public.material_price_aggregate SET current_reference=false WHERE id=a;
 IF NOT EXISTS(SELECT 1 FROM public.material_price_aggregate WHERE id=a AND NOT current_reference AND median_amount=30000) THEN RAISE EXCEPTION 'FAIL: history missing'; END IF;
 BEGIN
   DELETE FROM public.material_price_aggregate WHERE id=a;
   RAISE EXCEPTION 'FAIL: historical aggregate deleted';
 EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: historical delete denied'; END;
 BEGIN
   UPDATE public.material_price_aggregate SET current_reference=true WHERE id=a;
   RAISE EXCEPTION 'FAIL: old publication reactivated';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: historical reactivation denied'; END;
 INSERT INTO public.material_price_anomaly(id,observation_id,anomaly_type,severity,score,status,reason,created_at,data_scope,test_workspace_id)
 VALUES(n,o,'MEDIAN_DEVIATION','MEDIUM',0.5,'OPEN','Synthetic test only',stamp,'TEST',w);
 UPDATE public.material_price_anomaly SET status='DISMISSED',reviewed_by='test-reviewer',reviewed_at=stamp,review_note='Synthetic test only' WHERE id=n;
 BEGIN
   UPDATE public.material_price_anomaly SET status='ACCEPTED' WHERE id=n;
   RAISE EXCEPTION 'FAIL: anomaly decision overwritten';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: terminal anomaly decision immutable'; END;
 UPDATE public.material_price_observation SET review_status='REJECTED',reviewed_at=stamp,reviewed_by='test-reviewer',review_note='Synthetic revocation' WHERE id=o;
 BEGIN
   UPDATE public.material_price_observation SET review_status='APPROVED' WHERE id=o;
   RAISE EXCEPTION 'FAIL: rejected observation reapproved';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: terminal rejection immutable'; END;
 PERFORM set_config('app.sakhtyar_scope','REAL',true);
 IF EXISTS(SELECT 1 FROM public.material_item) OR EXISTS(SELECT 1 FROM public.material_price_observation) THEN RAISE EXCEPTION 'FAIL: workspace login sees REAL'; END IF;
 PERFORM set_config('app.sakhtyar_scope','TEST',true);
 PERFORM set_config('app.sakhtyar_workspace',gen_random_uuid()::text,true);
 IF EXISTS(SELECT 1 FROM public.material_item) OR EXISTS(SELECT 1 FROM public.material_price_observation) THEN RAISE EXCEPTION 'FAIL: workspace login sees another workspace'; END IF;
 RAISE NOTICE 'PASS: workspace login cannot change scope using GUC';
END $$;
ROLLBACK;
