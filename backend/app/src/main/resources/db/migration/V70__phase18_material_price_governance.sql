-- Phase18-B: additive governance. No rewrite/delete/backfill of business evidence.
-- V69 remains immutable. Existing aggregate rows are historical, never published by default.
ALTER TABLE public.material_price_observation ADD COLUMN quote_key varchar(64);
ALTER TABLE public.material_price_observation ADD CONSTRAINT chk_phase18b_quote_key CHECK (quote_key IS NULL OR quote_key ~ '^[0-9a-f]{64}$');
CREATE UNIQUE INDEX ux_phase18b_quote_key ON public.material_price_observation(material_item_id,quote_key) WHERE quote_key IS NOT NULL;
CREATE INDEX ix_phase18b_legacy_quote ON public.material_price_observation(material_item_id,source_id,observed_at) WHERE quote_key IS NULL;
ALTER TABLE public.material_price_aggregate ADD COLUMN current_reference boolean NOT NULL DEFAULT false;
ALTER TABLE public.material_price_aggregate ADD COLUMN expires_at timestamptz;
ALTER TABLE public.material_price_aggregate ADD COLUMN publication_key varchar(64);
ALTER TABLE public.material_price_aggregate ADD COLUMN evidence_ids jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.material_price_aggregate ADD CONSTRAINT chk_phase18b_publication CHECK (
    jsonb_typeof(evidence_ids)='array' AND (NOT current_reference OR (
       algorithm_version='2.0' AND publication_key IS NOT NULL AND publication_key ~ '^[0-9a-f]{64}$'
       AND expires_at IS NOT NULL AND expires_at>calculated_at AND jsonb_array_length(evidence_ids)=sample_count
       AND sample_count>0 AND min_amount>0 AND min_amount<=median_amount AND median_amount<=max_amount
       AND min_amount<=average_amount AND average_amount<=max_amount AND period_start<=period_end)));
CREATE UNIQUE INDEX ux_phase18b_current_group ON public.material_price_aggregate
 (material_item_id,variant_id,price_type,currency_code,unit_code,province,city) NULLS NOT DISTINCT WHERE current_reference;
CREATE INDEX ix_phase18b_current_expiry ON public.material_price_aggregate(material_item_id,expires_at) WHERE current_reference;
-- Retirement must not recalculate a historical currency FK when a currency is disabled.
-- V26 continues to validate new aggregate inserts. Currency columns are immutable below.
DROP TRIGGER trg_sync_currency_fk ON public.material_price_aggregate;
CREATE TRIGGER trg_sync_currency_fk BEFORE INSERT ON public.material_price_aggregate
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_sync_currency_fk();

CREATE FUNCTION public.sakhtyar_material_price_evidence_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='material_price_observation' THEN
   IF TG_OP='INSERT' THEN
     IF NEW.quote_key IS NULL OR NEW.review_status<>'PENDING_REVIEW' THEN
       RAISE EXCEPTION 'New price evidence requires quote identity and pending review' USING ERRCODE='23514';
     END IF;
   ELSIF TG_OP='UPDATE' THEN
     IF (to_jsonb(OLD)-ARRAY['review_status','reviewed_by','reviewed_at','review_note']) IS DISTINCT FROM
        (to_jsonb(NEW)-ARRAY['review_status','reviewed_by','reviewed_at','review_note']) THEN
       RAISE EXCEPTION 'Original price evidence is immutable' USING ERRCODE='23514';
     END IF;
     IF OLD.review_status IS DISTINCT FROM NEW.review_status THEN
       IF NOT ((OLD.review_status='PENDING_REVIEW' AND NEW.review_status IN ('APPROVED','REJECTED')) OR
               (OLD.review_status='APPROVED' AND NEW.review_status='REJECTED')) OR
           NEW.reviewed_at IS NULL OR coalesce(btrim(NEW.reviewed_by),'')='' OR coalesce(btrim(NEW.review_note),'')='' THEN
         RAISE EXCEPTION 'Invalid price review transition' USING ERRCODE='23514';
       END IF;
     ELSIF (OLD.reviewed_by,OLD.reviewed_at,OLD.review_note) IS DISTINCT FROM (NEW.reviewed_by,NEW.reviewed_at,NEW.review_note) THEN
       RAISE EXCEPTION 'Review evidence cannot be rewritten without a transition' USING ERRCODE='23514';
     END IF;
   END IF;
 ELSIF TG_TABLE_NAME='material_price_aggregate' THEN
   IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Historical price reference deletion prohibited' USING ERRCODE='42501'; END IF;
   IF TG_OP='INSERT' THEN
     IF NOT NEW.current_reference OR NEW.algorithm_version<>'2.0' OR jsonb_typeof(NEW.evidence_ids)<>'array'
        OR (SELECT count(DISTINCT e.id) FROM jsonb_array_elements_text(NEW.evidence_ids) e(id))<>NEW.sample_count
        OR NEW.expires_at IS DISTINCT FROM NEW.period_start+interval '720 hours'
        OR NOT EXISTS(SELECT 1 FROM public.material_item m WHERE m.id=NEW.material_item_id AND m.active AND m.unit_code=NEW.unit_code)
        OR NEW.period_start IS DISTINCT FROM (SELECT min(o.observed_at) FROM jsonb_array_elements_text(NEW.evidence_ids) e(id) JOIN public.material_price_observation o ON o.id=cast(e.id as uuid))
        OR NEW.period_end IS DISTINCT FROM (SELECT max(o.observed_at) FROM jsonb_array_elements_text(NEW.evidence_ids) e(id) JOIN public.material_price_observation o ON o.id=cast(e.id as uuid))
        OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(NEW.evidence_ids) e(id)
          LEFT JOIN public.material_price_observation o ON o.id=cast(e.id AS uuid)
            AND o.material_item_id=NEW.material_item_id AND o.variant_id IS NOT DISTINCT FROM NEW.variant_id
            AND o.data_scope=NEW.data_scope AND o.test_workspace_id IS NOT DISTINCT FROM NEW.test_workspace_id
          LEFT JOIN public.knowledge_source s ON s.id=o.source_id AND s.data_scope=NEW.data_scope AND s.test_workspace_id IS NOT DISTINCT FROM NEW.test_workspace_id
          LEFT JOIN public.global_currency c ON c.id=o.currency_id
          WHERE o.id IS NULL OR s.id IS NULL OR NOT s.enabled OR c.id IS NULL OR NOT c.active
            OR o.price_type<>NEW.price_type OR (o.currency_code<>NEW.currency_code AND NOT (o.currency_code IN ('IRR','TOMAN') AND NEW.currency_code IN ('IRR','TOMAN')))
            OR o.review_status<>'APPROVED' OR o.confidence IS NULL OR o.confidence<0.50
            OR o.observed_at>NEW.calculated_at OR o.observed_at+interval '720 hours'<=NEW.calculated_at
            OR EXISTS (SELECT 1 FROM public.material_price_anomaly n WHERE n.observation_id=o.id AND n.status<>'DISMISSED')) THEN
       RAISE EXCEPTION 'Aggregate lineage is incomplete or not eligible' USING ERRCODE='23514';
     END IF;
   END IF;
   IF TG_OP='UPDATE' AND ((to_jsonb(OLD)-'current_reference') IS DISTINCT FROM (to_jsonb(NEW)-'current_reference') OR (NOT OLD.current_reference AND NEW.current_reference)) THEN
     RAISE EXCEPTION 'Aggregate history is immutable; publish a new reference' USING ERRCODE='23514';
   END IF;
 ELSIF TG_TABLE_NAME='material_price_anomaly' THEN
   IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Anomaly evidence deletion prohibited' USING ERRCODE='42501'; END IF;
   IF TG_OP='INSERT' AND NEW.status<>'OPEN' THEN RAISE EXCEPTION 'New anomaly must be open' USING ERRCODE='23514'; END IF;
   IF TG_OP='UPDATE' THEN
     IF (to_jsonb(OLD)-ARRAY['status','reviewed_by','reviewed_at','review_note']) IS DISTINCT FROM
        (to_jsonb(NEW)-ARRAY['status','reviewed_by','reviewed_at','review_note']) THEN
       RAISE EXCEPTION 'Original anomaly evidence is immutable' USING ERRCODE='23514';
     END IF;
     IF OLD.status IS DISTINCT FROM NEW.status THEN
       IF OLD.status<>'OPEN' OR NEW.status NOT IN ('ACCEPTED','DISMISSED') OR NEW.reviewed_at IS NULL OR coalesce(btrim(NEW.reviewed_by),'')='' OR coalesce(btrim(NEW.review_note),'')='' THEN
         RAISE EXCEPTION 'Invalid anomaly review transition' USING ERRCODE='23514';
       END IF;
     ELSIF (OLD.reviewed_by,OLD.reviewed_at,OLD.review_note) IS DISTINCT FROM (NEW.reviewed_by,NEW.reviewed_at,NEW.review_note) THEN
       RAISE EXCEPTION 'Anomaly decision cannot be rewritten' USING ERRCODE='23514';
     END IF;
   END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
-- Alphabetical order before V26 currency sync on observation inserts: quote key required, raw input retained.
CREATE TRIGGER trg_phase18b_price_evidence BEFORE INSERT OR UPDATE ON public.material_price_observation
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_material_price_evidence_guard();
CREATE TRIGGER trg_phase18b_aggregate_evidence BEFORE INSERT OR UPDATE OR DELETE ON public.material_price_aggregate
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_material_price_evidence_guard();
CREATE TRIGGER trg_phase18b_anomaly_evidence BEFORE INSERT OR UPDATE OR DELETE ON public.material_price_anomaly
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_material_price_evidence_guard();
