#!/usr/bin/env python3
"""Read-only structural contracts, not runtime/PostgreSQL proof."""
import json
import pathlib
import re
import sys

SRC = 'backend/modules/audit/src/main/java/com/sakhtyar/audit/'

def audit(root):
    root = pathlib.Path(root)
    checks = []
    def check(label, path, predicate):
        p=root/path
        try:
            content=p.read_text(encoding='utf-8')
            ok=bool(predicate(content))
        except (OSError, UnicodeError):
            ok=False
        checks.append((label,ok))
    check('business transaction joins', SRC+'application/AuditService.java',
          lambda s: s.count('@Transactional(propagation = Propagation.REQUIRED)') == 2 and 'REQUIRES_NEW' not in s)
    check('sanitize before serialize', SRC+'application/AuditService.java',
          lambda s: 'AuditPayloadSanitizer.sanitize(payload)' in s and 'serializationError' not in s)
    check('reject malformed audit labels', SRC+'application/AuditService.java',
          lambda s: 'safeCode("action", action' in s)
    check('audit payload allowlist', SRC+'application/AuditPayloadSanitizer.java',
          lambda s: 'LABELS = Set.of' in s and 'COUNTS = Set.of' in s and 'FLAGS = Set.of' in s)
    check('no caller workspace trust', SRC+'application/AuditPayloadSanitizer.java',
          lambda s: 'workspaceId' in s and 'output.put("workspaceId"' not in s)
    check('bounded payloads', SRC+'application/AuditPayloadSanitizer.java',
          lambda s: 'MAX_FIELDS = 32' in s and 'Audit payload field limit exceeded' in s)
    check('validated correlation', SRC+'application/AuditPayloadSanitizer.java',
          lambda s: 'MDC.get("traceId")' in s and 'TRACE.matcher(trace).matches()' in s)
    check('JPA mutation guard', SRC+'domain/AuditEventEntity.java',
          lambda s: '@PreUpdate' in s and '@PreRemove' in s and 'rejectMutation()' in s)
    check('bounded repository search', SRC+'domain/AuditEventRepository.java',
          lambda s: 'Page<AuditEventEntity> findAllByOrderByCreatedAtDesc' in s and 'Pageable' in s)
    check('direct authorization', SRC+'api/AuditReadController.java',
          lambda s: '"AUDIT_READ".equals' in s and '"ROLE_TESTER".equals' in s)
    check('bounded read page', SRC+'api/AuditReadController.java',
          lambda s: 'page > 1000' in s and 'size > 50' in s)
    check('raw payload not returned', SRC+'api/AuditReadController.java',
          lambda s: 'record AuditEventView(' in s and 'String payloadText' not in s and 'getPayloadText()' in s)
    check('HTTP explicit audit permissions', 'backend/modules/identity/src/main/java/com/sakhtyar/identity/config/SecurityConfig.java',
          lambda s: '.requestMatchers(HttpMethod.GET, "/api/v1/admin/audit/events")' in s and '.requestMatchers("/api/v1/admin/audit/**").denyAll()' in s)
    check('existing tester constrained', 'backend/modules/identity/src/main/java/com/sakhtyar/identity/domain/UserRole.java',
          lambda s: 'TESTER(false, Permission.QUALITY_VIEW, Permission.QUALITY_RUN_SAFE)' in s)
    check('unit test dependencies', 'backend/modules/audit/pom.xml',
          lambda s: 'spring-boot-starter-test' in s)
    check('no migrations in patch', 'docs/audit/PHASE06_AUDIT_FA.md',
          lambda s: 'Migration: NONE' in s and 'RLS' in s)
    failed = [name for name,ok in checks if not ok]
    return {'status':'PASS_OFFLINE_SOURCE_CONTRACTS_ONLY' if not failed else 'BLOCKED',
            'checks':len(checks),'passed':len(checks)-len(failed),'failed':failed,
            'database_changed':False,'postgresql_immutability_proven':False,
            'real_test_rls_proven':False,'http_integration_proven':False}

if __name__ == '__main__':
    root=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else pathlib.Path.cwd()
    result=audit(root)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    sys.exit(0 if not result['failed'] else 2)
