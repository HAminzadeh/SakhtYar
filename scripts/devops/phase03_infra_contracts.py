#!/usr/bin/env python3
"""Offline invariant checker. No network, Docker daemon, credentials, or database."""
from __future__ import annotations
import argparse, ast, json, re
from pathlib import Path

class ContractError(ValueError):
    pass

def ci_migration_contract(ci: str, verifier: str) -> bool:
    """Check the live CI job and its inventory-based verifier, not a fixed version."""
    jobs = re.findall(r'(?ms)^  flyway-staging-smoke:\s*\n(.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)', ci)
    if len(jobs) != 1:
        return False
    job = jobs[0]
    # Require the actual disposable migration step followed by the verification step.
    steps = re.findall(r'(?ms)^      - name: ([^\n]+)\n(.*?)(?=^      - |\Z)', job)
    migrate = [i for i, (name, body) in enumerate(steps)
               if name == 'Execute Flyway SQL against real temporary PostgreSQL'
               and re.search(r'(?m)^        run: \|\s*$', body)
               and 'backend/app/src/main/resources/db/migration:/flyway/sql:ro' in body
               and re.search(r'(?m)^\s+.*\bmigrate\s*$', body)]
    verify = [i for i, (name, body) in enumerate(steps)
              if name == 'Verify every source migration succeeded on disposable CI database'
              and body.strip() == 'run: python3 -B scripts/database/verify_ci_migration_history.py --container sakhtyar-ci-db']
    if len(migrate) != 1 or len(verify) != 1 or migrate[0] >= verify[0]:
        return False
    # No conditional skip or continue-on-error in the migration or verification path.
    if re.search(r'(?m)^\s*(?:if|continue-on-error):', steps[migrate[0]][1]):
        return False
    if re.search(r'(?m)^    (?:if|continue-on-error):', job):
        return False
    # AST checks executable statements, not comments or a hard-coded migration number.
    try:
        tree = ast.parse(verifier)
        mains = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main']
        if len(mains) != 1:
            return False
        body = mains[0].body
        required = ast.parse("""
require(a.container=='sakhtyar-ci-db','Only disposable CI container accepted')
entries=inventory(ROOT/MIG)
rows=history(a.container,'sakhtyar')
require(not history_gate(rows,entries),'CI has pending migrations')
""").body
        actual = [ast.dump(n, include_attributes=False) for n in body]
        expected = [ast.dump(n, include_attributes=False) for n in required]
        positions = [actual.index(n) for n in expected]
        if positions != sorted(set(positions)):
            return False
        # Disallow an early return before verification; do not replace real DB evidence.
        if any(isinstance(n, ast.Return) for stmt in body[:positions[-1]+1] for n in ast.walk(stmt)):
            return False
        return True
    except (SyntaxError, ValueError):
        return False

def check(root: Path) -> dict:
    def load(rel):
        p=root/rel
        if not p.is_file(): raise ContractError('Missing file: '+rel)
        return p.read_text(encoding='utf-8-sig')
    c=load('docker-compose.yml')
    proxy=load('deploy/caddy/Caddyfile')
    server=load('scripts/deploy-server.sh')
    backup=load('scripts/backup-server.sh')
    local=load('compose.spring-local.yml')
    overlay=load('docker-compose.observability.local.yml')
    prod=load('backend/app/src/main/resources/application-production.yml')
    alloy=load('deploy/observability/alloy.alloy')
    loki=load('deploy/observability/loki.yml')
    tempo=load('deploy/observability/tempo.yml')
    ci=load('.github/workflows/ci.yml')
    verifier=load('scripts/database/verify_ci_migration_history.py')
    env=load('.env.server.example')
    required={
      'production_profile_every_jvm': c.count('production,')==4,
      'application_runtime_restricted_role_explicit': c.count('SAKHTYAR_RUNTIME_DB_USER: ${SAKHTYAR_RUNTIME_DB_USER:?')==4 and c.count('SAKHTYAR_RUNTIME_DB_PASSWORD: ${SAKHTYAR_RUNTIME_DB_PASSWORD:?')==4,
      'never_application_pg_owner': 'DB_USERNAME: ${POSTGRES_USER}' not in c and 'DB_PASSWORD: ${POSTGRES_PASSWORD}' not in c,
      'disable_auto_flyway_all_jvm': c.count('SPRING_FLYWAY_ENABLED: "false"')==4,
      'force_role_guard_all_jvm': c.count('APP_T10_REQUIRE_RESTRICTED_RUNTIME: "true"')==4,
      'public_actuator_closed': '/actuator/*' not in proxy and 'path /api/*' in proxy,
      'local_ports_loopback': not re.search(r'^\s+-\s*"(?:\$\{[^}]+\}|\d+):\d+"',local+'\n'+overlay,re.M),
      'api_readiness_healthcheck': 'http://127.0.0.1:8080/actuator/health/readiness' in c and 'start_period: 90s' in c,
      'prod_no_automatic_migration': 'enabled: false' in prod and 'require-restricted-runtime: true' in prod,
      'safe_backup_not_source_dotenv': 'source .env' not in backup and 'pg_dump' in backup and 'sha256sum' in backup,
      'deploy_points_to_existing_compose': 'docker-compose.yml' in server and 'docker-compose.server.yml' not in server,
      'deploy_requires_explicit_approval': 'SAKHTYAR_DEPLOY_CONFIRM' in server,
      'deploy_waits_for_health': '--wait --wait-timeout 180' in server,
      'alloy_log_redaction': 'loki.process "sanitize"' in alloy and 'stage.replace' in alloy and 'loki.process.sanitize.receiver' in alloy,
      'loki_retention': 'retention_enabled: true' in loki and 'delete_request_store: filesystem' in loki,
      'tempo_retention': 'block_retention: 168h' in tempo,
      'ci_migration_current': ci_migration_contract(ci, verifier) and 'phase03_infra_contracts.py' in ci,
      'env_explicit_runtime': 'SAKHTYAR_RUNTIME_DB_USER=' in env and 'SAKHTYAR_RUNTIME_DB_PASSWORD=' in env,
      'no_default_external_effects': 'SAKHTYAR_DEV_SEED_ENABLED=true' not in prod,
    }
    failures=sorted(k for k,v in required.items() if not v)
    if failures: raise ContractError('Offline policy failed: '+', '.join(failures))
    return {'status':'PASS_OFFLINE_SOURCE_CONTRACTS_ONLY','checks':len(required),
            'checks_passed':len(required),'production_runtime_proven':False,
            'database_changed':False,'network_called':False,'deploy_performed':False}

def main():
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--report',type=Path)
    a=p.parse_args()
    try: result=check(a.root)
    except (OSError,ContractError) as exc:
        result={'status':'BLOCKED_SOURCE_CONTRACTS','reason':str(exc),'database_changed':False};code=2
    else:code=0
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True)
        a.report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return code
if __name__=='__main__':raise SystemExit(main())
