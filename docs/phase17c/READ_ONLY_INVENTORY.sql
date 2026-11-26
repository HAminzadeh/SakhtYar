\pset pager off
\echo === PHASE17-C READ-ONLY LEGAL INVENTORY START ===
BEGIN READ ONLY;
SET LOCAL statement_timeout='20s';
SELECT current_database() AS db, current_user AS role, current_setting('transaction_read_only') AS read_only, now() AS measured_at;
SELECT record_type,review_status,count(*) AS imported_law_items FROM knowledge_ai_import_item i
JOIN knowledge_ai_import_document d ON d.id=i.document_id
WHERE d.category='LAW' AND i.archived_at IS NULL AND d.archived_at IS NULL GROUP BY 1,2 ORDER BY 1,2;
SELECT status,count(*) FROM knowledge_rule GROUP BY 1 ORDER BY 1;
SELECT decision,count(*) FROM knowledge_rule_review GROUP BY 1 ORDER BY 1;
SELECT status,count(*) FROM knowledge_release GROUP BY 1 ORDER BY 1;
SELECT active,count(*) AS urban_rules,count(*) FILTER (WHERE verified_knowledge_rule_id IS NOT NULL) AS bound_rules FROM urban_rule GROUP BY 1 ORDER BY 1;
SELECT status,count(*) AS evaluations FROM urban_evaluation GROUP BY 1 ORDER BY 1;
COMMIT;
\echo === PHASE17-C READ-ONLY LEGAL INVENTORY END ===
