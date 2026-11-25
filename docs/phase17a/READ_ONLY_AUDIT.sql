-- SakhtYar Phase 17 - Read-only inventory of already-extracted rules.
-- Based on branch v2 @ 8936cbdbef08e2ee9f211b3ce29fe3f83f2f702d, 2026-11-25.
-- This file deliberately contains no DDL/DML or external network calls.
-- Run on an isolated local DB/clone. It returns aggregated counts, not rule texts.
\set ON_ERROR_STOP on
\pset pager off
\echo '=== P17 READ-ONLY INVENTORY / START ==='
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '45s';
SET LOCAL lock_timeout = '5s';
SELECT current_database() AS database_name, current_user AS database_role,
       current_setting('transaction_read_only') AS transaction_read_only,
       now() AS measured_at;

\echo '01 - AI archive/batch import statuses'
SELECT status, count(*) AS batches
FROM knowledge_ai_import_batch GROUP BY status ORDER BY status;

\echo '02 - AI extracted items: review and type'
SELECT record_type, review_status, count(*) AS item_count,
       count(*) FILTER (WHERE archived_at IS NOT NULL) AS archived_count
FROM knowledge_ai_import_item
GROUP BY record_type, review_status ORDER BY record_type, review_status;

\echo '03 - AI expert-catalogue publication (NOT legal enactment)'
SELECT publication_kind,
       count(*) FILTER (WHERE revoked_at IS NULL) AS currently_listed,
       count(*) FILTER (WHERE revoked_at IS NOT NULL) AS revoked
FROM knowledge_ai_publication GROUP BY publication_kind ORDER BY publication_kind;

\echo '04 - Source-preserving legal segments'
SELECT segment_type, review_status, count(*) AS segments,
       count(*) FILTER (WHERE rule_id IS NOT NULL) AS linked_to_candidate_rule
FROM knowledge_legal_segment GROUP BY segment_type, review_status
ORDER BY segment_type, review_status;

\echo '05 - Canonical knowledge rules: numeric vs text-only'
SELECT status,
       count(*) AS rules,
       count(*) FILTER (WHERE numeric_value IS NOT NULL) AS numeric_rules,
       count(*) FILTER (WHERE numeric_value IS NULL AND nullif(btrim(text_value),'') IS NOT NULL) AS text_only_rules
FROM knowledge_rule GROUP BY status ORDER BY status;

\echo '06 - Human-reviewed canonical knowledge rules'
SELECT decision, count(*) AS reviews,
       count(*) FILTER (
           WHERE source_page > 0 AND source_sha256 = d.sha256
       ) AS matching_source_sha_and_page,
       count(*) FILTER (WHERE source_document_id IS NULL OR d.id IS NULL) AS missing_source_document
FROM knowledge_rule_review v
LEFT JOIN knowledge_source_document d ON d.id = v.source_document_id
GROUP BY decision ORDER BY decision;

\echo '07 - Canonical knowledge release statuses'
SELECT status, count(*) AS releases
FROM knowledge_release GROUP BY status ORDER BY status;

\echo '08 - Rule types and evidence-binding coverage'
SELECT rule_type,
       count(*) AS urban_rules,
       count(*) FILTER (WHERE active) AS active_rules,
       count(*) FILTER (WHERE verified_knowledge_rule_id IS NOT NULL) AS bound_to_knowledge,
       count(*) FILTER (WHERE active AND verified_knowledge_rule_id IS NOT NULL) AS active_bound
FROM urban_rule GROUP BY rule_type ORDER BY rule_type;

\echo '09 - Rules with the complete documented evidence chain (not case-scoped)'
SELECT count(DISTINCT u.id) AS currently_evidence_eligible_active_urban_rules
FROM urban_rule u
JOIN knowledge_rule k ON k.id = u.verified_knowledge_rule_id
JOIN knowledge_rule_review v ON v.rule_id = k.id AND v.decision = 'APPROVED'
JOIN knowledge_source_document d ON d.id = v.source_document_id
WHERE u.active = true
  AND k.status = 'VERIFIED'
  AND v.source_page > 0
  AND v.source_sha256 = d.sha256
  AND (v.valid_from IS NULL OR v.valid_from <= CURRENT_DATE)
  AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)
  AND EXISTS (
      SELECT 1 FROM knowledge_release rel
       WHERE rel.knowledge_version_id = k.knowledge_version_id
         AND rel.status = 'PUBLISHED'
  );
-- IMPORTANT: Query 09 is NOT a claim of case applicability. Case jurisdiction,
-- property type, exceptions, and the current service's LIMIT 1000 still matter.

\echo '10 - Operational funnel summary (counts are not necessarily disjoint)'
SELECT
  (SELECT count(*) FROM knowledge_ai_import_item) AS ai_staged,
  (SELECT count(*) FROM knowledge_ai_import_item WHERE review_status='APPROVED') AS ai_staging_approved,
  (SELECT count(*) FROM knowledge_legal_segment) AS legal_segments,
  (SELECT count(*) FROM knowledge_rule WHERE status='CANDIDATE') AS canonical_candidates,
  (SELECT count(*) FROM knowledge_rule WHERE status='VERIFIED') AS canonical_verified,
  (SELECT count(*) FROM knowledge_rule_review WHERE decision='APPROVED') AS human_approved_canonical,
  (SELECT count(*) FROM knowledge_release WHERE status='PUBLISHED') AS published_canonical_releases,
  (SELECT count(*) FROM urban_rule WHERE active) AS active_urban_rules,
  (SELECT count(*) FROM urban_rule WHERE active AND verified_knowledge_rule_id IS NOT NULL) AS active_bound_urban_rules;

\echo '11 - Workflow state counts'
SELECT status, count(*) AS executions
FROM knowledge_intake_execution GROUP BY status ORDER BY status;

\echo '12 - Approved LAW split: canonical bridge intake candidate counts'
SELECT i.record_type,i.segment_type,COUNT(*) AS items,
       COUNT(DISTINCT i.document_id) AS distinct_source_documents,
       COUNT(*) FILTER(WHERE d.source_sha256 IS NULL) AS missing_original_source_hash,
       COUNT(*) FILTER(WHERE i.page_from IS NULL OR i.page_from<1) AS invalid_start_page,
       COUNT(*) FILTER(WHERE i.content_sha256<>pub.source_sha256) AS catalogue_hash_mismatch
FROM knowledge_ai_import_item i
JOIN knowledge_ai_import_document d ON d.id=i.document_id
JOIN knowledge_ai_import_batch b ON b.id=i.batch_id
LEFT JOIN knowledge_ai_publication pub ON pub.item_id=i.id
WHERE i.review_status='APPROVED' AND d.category='LAW'
  AND i.archived_at IS NULL AND d.archived_at IS NULL AND b.archived_at IS NULL
GROUP BY i.record_type,i.segment_type
ORDER BY i.record_type,i.segment_type;

\echo '13 - Catalogue items with exact original source SHA correspondence (NOT legal validation)'
SELECT i.record_type, count(*) AS approved_law_items,
  count(*) FILTER (WHERE exists(
       SELECT 1 FROM knowledge_source_document s WHERE s.sha256=d.source_sha256
  )) AS linked_by_exact_document_hash,
  count(*) FILTER (WHERE NOT exists(
       SELECT 1 FROM knowledge_source_document s WHERE s.sha256=d.source_sha256
  )) AS no_exact_hash_link
FROM knowledge_ai_import_item i
JOIN knowledge_ai_import_document d ON d.id=i.document_id
WHERE i.review_status='APPROVED' AND i.archived_at IS NULL
  AND d.category='LAW' AND d.archived_at IS NULL
GROUP BY i.record_type ORDER BY i.record_type;

\echo '14 - Canonical candidate and release status (no assumptions of live legal validity)'
SELECT status, count(*) AS canonical_count
FROM knowledge_rule GROUP BY status ORDER BY status;

COMMIT;
\echo '=== P17 READ-ONLY INVENTORY / COMPLETE ==='
