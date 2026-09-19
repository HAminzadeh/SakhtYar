#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f ".env" ]]; then
  echo ".env not found"
  exit 1
fi

set -a
source .env
set +a

STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${BACKUP_DIR:-./backups}/${STAMP}"

mkdir -p "${BACKUP_DIR}"

docker exec sakhtyar-db \
  pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Fc \
  > "${BACKUP_DIR}/database.dump"

echo "Database backup created at ${BACKUP_DIR}/database.dump"
echo "MinIO volume backup should be handled by the host backup policy."
