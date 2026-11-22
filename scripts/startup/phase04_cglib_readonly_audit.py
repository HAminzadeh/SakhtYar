#!/usr/bin/env python3
"""Read-only contract checks for the P02 admin controller Spring AOP startup regression."""
import argparse
import json
import pathlib
import re
import sys

TARGET = pathlib.Path('backend/modules/quality/src/main/java/com/sakhtyar/quality/Phase02SharedDatabaseAuditController.java')
TEST = pathlib.Path('backend/modules/quality/src/test/java/com/sakhtyar/quality/Phase02SharedDatabaseAuditCglibTest.java')


def audit(root):
    path = root / TARGET
    checks = {}
    if not path.is_file():
        return {'status': 'BLOCKED', 'checks': {'controller_exists': False}}
    source = path.read_text(encoding='utf-8')
    checks['controller_exists'] = True
    checks['controller_is_nonfinal_public_class'] = bool(re.search(r'\bpublic\s+class\s+Phase02SharedDatabaseAuditController\s*\{', source))
    checks['controller_is_not_final'] = not bool(re.search(r'\bpublic\s+final\s+class\s+Phase02SharedDatabaseAuditController', source))
    checks['transaction_readonly_kept'] = bool(re.search(r'@Transactional\s*\(\s*readOnly\s*=\s*true\s*\)', source))
    checks['admin_endpoint_kept'] = '/api/v1/admin/quality/database-isolation' in source
    checks['administrator_permission_kept'] = 'USER_MANAGE' in source
    checks['tester_explicitly_denied'] = 'ROLE_TESTER' in source
    checks['auth_denied_without_privilege'] = 'HttpStatus.FORBIDDEN' in source
    checks['select_only_readonly_catalog'] = 'pg_catalog' in source and 'jdbc.queryForList(CATALOG_SQL)' in source and 'jdbc.queryForMap(PRINCIPAL_SQL)' in source
    checks['no_false_global_isolation_claim'] = 'result.put("globalIsolationVerified", false)' in source
    checks['no_business_e2e_write_admission'] = 'result.put("businessE2eWritesAuthorized", false)' in source
    checks['cglib_regression_test_exists'] = (root / TEST).is_file() and 'AopUtils.isCglibProxy' in (root / TEST).read_text(encoding='utf-8')
    return {'status': 'PASS' if all(checks.values()) else 'BLOCKED', 'checks': checks, 'pass_count': sum(checks.values()), 'total': len(checks), 'database_modified': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=pathlib.Path, required=True)
    args = parser.parse_args()
    report = audit(args.root.resolve())
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['status'] == 'PASS' else 2


if __name__ == '__main__':
    sys.exit(main())
