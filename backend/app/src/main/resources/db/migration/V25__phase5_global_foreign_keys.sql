ALTER TABLE property
    ADD COLUMN IF NOT EXISTS country_id UUID REFERENCES global_country(id),
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID REFERENCES global_administrative_division(id),
    ADD COLUMN IF NOT EXISTS city_id UUID REFERENCES global_city(id);

ALTER TABLE knowledge_source
    ADD COLUMN IF NOT EXISTS country_id UUID REFERENCES global_country(id),
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID REFERENCES global_administrative_division(id),
    ADD COLUMN IF NOT EXISTS city_id UUID REFERENCES global_city(id),
    ADD COLUMN IF NOT EXISTS language_id UUID REFERENCES global_language(id),
    ADD COLUMN IF NOT EXISTS jurisdiction_scope VARCHAR(30) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE knowledge_candidate
    ADD COLUMN IF NOT EXISTS country_id UUID REFERENCES global_country(id),
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID REFERENCES global_administrative_division(id),
    ADD COLUMN IF NOT EXISTS city_id UUID REFERENCES global_city(id),
    ADD COLUMN IF NOT EXISTS language_id UUID REFERENCES global_language(id),
    ADD COLUMN IF NOT EXISTS jurisdiction_scope VARCHAR(30) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE crawl_source
    ADD COLUMN IF NOT EXISTS country_id UUID REFERENCES global_country(id),
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID REFERENCES global_administrative_division(id),
    ADD COLUMN IF NOT EXISTS city_id UUID REFERENCES global_city(id),
    ADD COLUMN IF NOT EXISTS language_id UUID REFERENCES global_language(id),
    ADD COLUMN IF NOT EXISTS jurisdiction_scope VARCHAR(30) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE urban_rule
    ADD COLUMN IF NOT EXISTS country_id UUID REFERENCES global_country(id),
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID REFERENCES global_administrative_division(id),
    ADD COLUMN IF NOT EXISTS city_id UUID REFERENCES global_city(id),
    ADD COLUMN IF NOT EXISTS language_id UUID REFERENCES global_language(id),
    ADD COLUMN IF NOT EXISTS jurisdiction_scope VARCHAR(30) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE learning_event
    ADD COLUMN IF NOT EXISTS country_id UUID REFERENCES global_country(id),
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID REFERENCES global_administrative_division(id),
    ADD COLUMN IF NOT EXISTS city_id UUID REFERENCES global_city(id),
    ADD COLUMN IF NOT EXISTS language_id UUID REFERENCES global_language(id),
    ADD COLUMN IF NOT EXISTS jurisdiction_scope VARCHAR(30) NOT NULL DEFAULT 'GLOBAL';

CREATE INDEX IF NOT EXISTS idx_property_global_location
    ON property(country_id, administrative_division_id, city_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_candidate_scope
    ON knowledge_candidate(country_id, administrative_division_id, city_id, language_id);
CREATE INDEX IF NOT EXISTS idx_crawl_source_scope
    ON crawl_source(country_id, administrative_division_id, city_id, language_id);
CREATE INDEX IF NOT EXISTS idx_urban_rule_scope
    ON urban_rule(country_id, administrative_division_id, city_id, language_id);
CREATE INDEX IF NOT EXISTS idx_learning_event_scope
    ON learning_event(country_id, administrative_division_id, city_id, language_id);