DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'material_price_observation',
    'material_price_aggregate',
    'scenario_cost_snapshot',
    'financial_analysis',
    'sensitivity_analysis'
  ]
  LOOP
    IF to_regclass(t) IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS currency_id UUID REFERENCES global_currency(id)', t);
    END IF;
  END LOOP;
END $$;

DO $$
BEGIN
  IF to_regclass('feasibility_assessment') IS NOT NULL THEN
    ALTER TABLE feasibility_assessment
      ADD COLUMN IF NOT EXISTS currency_id UUID REFERENCES global_currency(id);
  END IF;
END $$;

ALTER TABLE material_price_observation ALTER COLUMN currency_code TYPE VARCHAR(8);
ALTER TABLE material_price_aggregate ALTER COLUMN currency_code TYPE VARCHAR(8);
ALTER TABLE scenario_cost_snapshot ALTER COLUMN currency_code TYPE VARCHAR(8);
ALTER TABLE financial_analysis ALTER COLUMN currency_code TYPE VARCHAR(8);
ALTER TABLE sensitivity_analysis ALTER COLUMN currency_code TYPE VARCHAR(8);
ALTER TABLE feasibility_assessment ALTER COLUMN cost_currency_code TYPE VARCHAR(8);

CREATE OR REPLACE FUNCTION sakhtyar_currency_id(code_text text)
RETURNS uuid LANGUAGE sql STABLE AS $$
  SELECT id FROM global_currency WHERE upper(code)=upper(code_text) AND active=TRUE LIMIT 1
$$;

UPDATE material_price_observation SET currency_id=sakhtyar_currency_id(currency_code) WHERE currency_id IS NULL;
UPDATE material_price_aggregate SET currency_id=sakhtyar_currency_id(currency_code) WHERE currency_id IS NULL;
UPDATE scenario_cost_snapshot SET currency_id=sakhtyar_currency_id(currency_code) WHERE currency_id IS NULL;
UPDATE financial_analysis SET currency_id=sakhtyar_currency_id(currency_code) WHERE currency_id IS NULL;
UPDATE sensitivity_analysis SET currency_id=sakhtyar_currency_id(currency_code) WHERE currency_id IS NULL;
UPDATE feasibility_assessment SET currency_id=sakhtyar_currency_id(cost_currency_code) WHERE currency_id IS NULL;

CREATE OR REPLACE FUNCTION sakhtyar_sync_currency_fk()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c text;
BEGIN
  IF TG_TABLE_NAME='feasibility_assessment' THEN c := NEW.cost_currency_code;
  ELSE c := NEW.currency_code;
  END IF;
  NEW.currency_id := sakhtyar_currency_id(c);
  IF NEW.currency_id IS NULL THEN
    RAISE EXCEPTION 'Unknown currency code: %', c;
  END IF;
  RETURN NEW;
END $$;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'material_price_observation','material_price_aggregate','scenario_cost_snapshot',
    'financial_analysis','sensitivity_analysis','feasibility_assessment'
  ]
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_sync_currency_fk ON %I',t);
    EXECUTE format(
      'CREATE TRIGGER trg_sync_currency_fk BEFORE INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION sakhtyar_sync_currency_fk()',t
    );
  END LOOP;
END $$;