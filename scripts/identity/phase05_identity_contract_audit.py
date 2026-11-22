#!/usr/bin/env python3
"""Read-only identity source contracts. No DB, secrets, network, writes or migrations."""
import json
import pathlib
import sys


def audit(root):
    root = pathlib.Path(root)
    prefix = 'backend/modules/identity/src/main/java/com/sakhtyar/identity/'
    cases = {
        'login-row-lock': (prefix+'application/AuthService.java', 'findByUsernameIgnoreCaseForUpdate(normalized)'),
        'register-db-unique-conflict': (prefix+'application/AuthService.java', 'repository.saveAndFlush(user);'),
        'admin-create-db-unique-conflict': (prefix+'application/UserAdminService.java', 'repository.saveAndFlush(user);'),
        'lockout-no-rollback': (prefix+'application/AuthService.java', 'noRollbackFor = ResponseStatusException.class'),
        'last-admin-locked': (prefix+'application/UserAdminService.java', 'lockUsersByRoleAndStatus('),
        'last-admin-locked-count': (prefix+'application/UserAdminService.java', 'lockedActiveAdminCount <= 1'),
        'role-change-revocation': (prefix+'application/UserAdminService.java', 'if (roleChanged || request.status() != UserStatus.ACTIVE)'),
        'session-single-lock': (prefix+'application/AuthSessionService.java', 'findByIdForUpdate(sessionId)'),
        'session-all-lock': (prefix+'application/AuthSessionService.java', 'findAllByUserIdForUpdate(userId)'),
        'session-rotate-lock': (prefix+'domain/AuthSessionRepository.java', '@Lock(LockModeType.PESSIMISTIC_WRITE)'),
        'repository-user-lock': (prefix+'domain/UserRepository.java', 'findByUsernameIgnoreCaseForUpdate(@Param("username") String username)'),
        'repository-admin-lock': (prefix+'domain/UserRepository.java', 'lockUsersByRoleAndStatus(@Param("role") UserRole role,'),
        'session-hash-storage': (prefix+'domain/AuthSessionEntity.java', 'refresh_token_hash'),
        'tester-limited-role': (prefix+'domain/UserRole.java', 'TESTER(false, Permission.QUALITY_VIEW, Permission.QUALITY_RUN_SAFE)'),
        'csrf-spa': (prefix+'config/SecurityConfig.java', '.csrf(csrf -> csrf'),
        'csrf-refresh': ('backend/modules/identity/src/main/java/com/sakhtyar/auth/api/AuthController.java', '@PostMapping("/refresh")'),
        'production-no-flyway': ('backend/app/src/main/resources/application-production.yml', 'enabled: false'),
    }
    checks = []
    for name, (rel, snippet) in cases.items():
        try:
            data = (root / rel).read_text(encoding='utf-8-sig')
            passed = snippet in data
        except (OSError, UnicodeError):
            passed = False
        checks.append({'check': name, 'passed': passed})
    state = 'PASS_OFFLINE_SOURCE_CONTRACTS_ONLY' if all(x['passed'] for x in checks) else 'BLOCKED'
    return {'status': state, 'checks': len(checks), 'passed': sum(x['passed'] for x in checks),
            'failed': [c['check'] for c in checks if not c['passed']], 'database_changed': False,
            'runtime_isolation_proven': False, 'concurrent_postgresql_tested': False,
            'http_integration_tested': False}

if __name__ == '__main__':
    report = audit(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '.'))
    print(json.dumps(report, indent=2))
    sys.exit(0 if report['status'].startswith('PASS_') else 2)
