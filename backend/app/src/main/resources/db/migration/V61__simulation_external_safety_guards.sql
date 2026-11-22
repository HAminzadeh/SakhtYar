-- T05.2 restricted database egress barrier (NOT a complete global egress firewall).
-- Refuses creation/updates of TEST notifications and external crawler jobs.
-- The T05 core fixture does not call email, SMS, MinIO, outbound HTTP, or AI models.
CREATE OR REPLACE FUNCTION public.sakhtyar_block_test_external_queue()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.data_scope = 'TEST' THEN
    RAISE EXCEPTION 'Simulation TEST data cannot enter external effect queue %', TG_TABLE_NAME
      USING ERRCODE='42501';
  END IF;
  RETURN NEW;
END $$;

DO $outbox$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['notification_outbox','crawl_job'] LOOP
  IF to_regclass('public.'||t) IS NOT NULL AND EXISTS(
     SELECT 1 FROM information_schema.columns WHERE table_schema='public'
       AND table_name=t AND column_name='data_scope'
  ) THEN
    EXECUTE format('DROP TRIGGER IF EXISTS trg_sakhtyar_test_egress ON public.%I',t);
    EXECUTE format('CREATE TRIGGER trg_sakhtyar_test_egress BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_block_test_external_queue()',t);
  END IF;
 END LOOP;
END $outbox$;
