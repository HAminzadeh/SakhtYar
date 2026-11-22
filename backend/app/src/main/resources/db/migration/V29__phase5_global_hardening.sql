-- SakhtYar Phase 5.13.5.1
ALTER TABLE user_preference
    DROP CONSTRAINT IF EXISTS chk_user_preference_theme;

ALTER TABLE user_preference
    ADD CONSTRAINT chk_user_preference_theme
    CHECK(theme IN (
        'SYSTEM','LIGHT','DARK','OCEAN','EMERALD','SUNSET','MIDNIGHT'
    ));

CREATE INDEX IF NOT EXISTS idx_knowledge_source_global_scope
    ON knowledge_source(enabled, country_id, administrative_division_id, city_id, language_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_term_source_status
    ON knowledge_term(source_id, status);

CREATE INDEX IF NOT EXISTS idx_knowledge_alias_source_status
    ON knowledge_term_alias(source_id, status);