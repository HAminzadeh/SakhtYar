#!/usr/bin/env bash
# Manual production deployment only. Never invoked by source ZIP installer.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"
[[ "${SAKHTYAR_DEPLOY_CONFIRM:-}" == 'YES_DEPLOY' ]] || {
  echo 'BLOCKED: explicitly export SAKHTYAR_DEPLOY_CONFIRM=YES_DEPLOY after review' >&2; exit 2;
}
[[ -f .env ]] || { echo 'BLOCKED: production .env is absent' >&2; exit 2; }
command -v python3 >/dev/null || { echo 'BLOCKED: python3 required' >&2; exit 2; }
command -v docker >/dev/null || { echo 'BLOCKED: docker required' >&2; exit 2; }
python3 scripts/devops/phase03_deploy_preflight.py --env-file .env
# Source-only and Compose config checks must pass before any infrastructure changes.
python3 scripts/devops/phase03_infra_contracts.py --root .
docker compose --env-file .env -f docker-compose.yml config --quiet
# Existing DB must have independent backup before deploy. No auto-restore or migration.
if [[ -n "$(docker compose --env-file .env -f docker-compose.yml ps -q db 2>/dev/null)" ]]; then
  [[ -n "${SAKHTYAR_BACKUP_PROOF:-}" && -f "${SAKHTYAR_BACKUP_PROOF}" ]] || {
    echo 'BLOCKED: existing database requires a verified external backup proof path' >&2; exit 2;
  }
fi
# Build images; operator-approved service replacement follows. Never prune volumes.
docker compose --env-file .env -f docker-compose.yml build
docker compose --env-file .env -f docker-compose.yml up -d --no-build --wait --wait-timeout 180
docker compose --env-file .env -f docker-compose.yml ps
echo 'Deployment invoked. Check /actuator/health internally and independently verify RLS and real/TEST isolation.'
