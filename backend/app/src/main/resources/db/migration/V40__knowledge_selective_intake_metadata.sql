-- SakhtYar selective knowledge intake v0.18.5
-- V34-V39 are immutable. This migration only extends the inventory metadata used by the Wizard.
alter table knowledge_intake_inventory add column if not exists detected_title text;
alter table knowledge_intake_inventory add column if not exists detected_category varchar(240);
alter table knowledge_intake_inventory add column if not exists file_extension varchar(20);
create index if not exists ix_kii_category on knowledge_intake_inventory(execution_id,detected_category);
