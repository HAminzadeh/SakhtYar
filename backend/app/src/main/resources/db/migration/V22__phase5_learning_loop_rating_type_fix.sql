-- SakhtYar Phase 5.13 runtime repair
-- Hibernate maps LearningEventEntity.rating as java.lang.Integer,
-- therefore PostgreSQL must expose INTEGER rather than SMALLINT.
--
-- V21 has already been applied in existing local databases, so this repair
-- is intentionally a new Flyway migration instead of editing V21.

ALTER TABLE learning_event
    ALTER COLUMN rating TYPE INTEGER
    USING rating::INTEGER;