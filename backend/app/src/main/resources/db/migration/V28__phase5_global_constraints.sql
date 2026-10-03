ALTER TABLE knowledge_source
    ADD CONSTRAINT chk_knowledge_source_scope
    CHECK (jurisdiction_scope IN ('GLOBAL','COUNTRY','ADMIN_DIVISION','CITY'));

ALTER TABLE knowledge_candidate
    ADD CONSTRAINT chk_knowledge_candidate_scope
    CHECK (jurisdiction_scope IN ('GLOBAL','COUNTRY','ADMIN_DIVISION','CITY'));

ALTER TABLE crawl_source
    ADD CONSTRAINT chk_crawl_source_scope
    CHECK (jurisdiction_scope IN ('GLOBAL','COUNTRY','ADMIN_DIVISION','CITY'));

ALTER TABLE urban_rule
    ADD CONSTRAINT chk_urban_rule_scope
    CHECK (jurisdiction_scope IN ('GLOBAL','COUNTRY','ADMIN_DIVISION','CITY'));

ALTER TABLE learning_event
    ADD CONSTRAINT chk_learning_event_scope
    CHECK (jurisdiction_scope IN ('GLOBAL','COUNTRY','ADMIN_DIVISION','CITY'));