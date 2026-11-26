\pset pager off
\echo 'P17-B READ-ONLY / START'
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout='15s';
SELECT current_database() as database_name,current_user as inspected_as, current_setting('transaction_read_only') as read_only;
SELECT status,count(*) AS releases FROM knowledge_release GROUP BY status ORDER BY status;
SELECT status,count(*) AS canonical_rules FROM knowledge_rule GROUP BY status ORDER BY status;
SELECT decision,count(*) AS reviewed FROM knowledge_rule_review GROUP BY decision ORDER BY decision;
SELECT rule_type,count(*) AS urban_rules,count(*) FILTER(WHERE active) AS active,
       count(*) FILTER(WHERE verified_knowledge_rule_id IS NOT NULL) AS bound
  FROM urban_rule GROUP BY rule_type ORDER BY rule_type;
SELECT count(*) AS eligible_active_rules FROM urban_rule u
 JOIN knowledge_rule k ON k.id=u.verified_knowledge_rule_id AND k.status='VERIFIED'
 JOIN knowledge_rule_review rv ON rv.rule_id=k.id AND rv.decision='APPROVED'
 JOIN knowledge_source_document d ON d.id=rv.source_document_id AND rv.source_sha256=d.sha256
 JOIN knowledge_release rel ON rel.knowledge_version_id=k.knowledge_version_id AND rel.status='PUBLISHED'
 WHERE u.active AND rv.source_page>0;
SELECT id,code,active,verified_knowledge_rule_id FROM urban_rule ORDER BY code LIMIT 100;
COMMIT;
\echo 'P17-B READ-ONLY / END'
