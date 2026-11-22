#!/usr/bin/env bash
# Read-only logical dump; no PostgreSQL mutation; does not back up MinIO/Redis volumes.
set -euo pipefail
umask 077
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || { echo 'BLOCKED: .env missing' >&2; exit 2; }
command -v docker >/dev/null || { echo 'BLOCKED: Docker unavailable' >&2; exit 2; }
command -v sha256sum >/dev/null || { echo 'BLOCKED: sha256sum unavailable' >&2; exit 2; }
# Default outside source checkout; overrides must be approved by operator.
BACKUP_ROOT="${BACKUP_DIR:-${ROOT}/../SakhtYar-Backups}"
mkdir -p "$BACKUP_ROOT"
BACKUP_ROOT="$(cd "$BACKUP_ROOT" && pwd)"
[[ "$BACKUP_ROOT" != "$ROOT" && "$BACKUP_ROOT" != "$ROOT/"* ]] || {
  echo 'BLOCKED: backup cannot be stored in Git working tree' >&2; exit 2;
}
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${BACKUP_ROOT}/sakhtyar-${STAMP}.dump"
TMP="${DEST}.partial.$$"
trap 'rm -f -- "$TMP"' EXIT
[[ ! -e "$DEST" ]] || { echo 'BLOCKED: refusing overwrite' >&2; exit 2; }
# Never evaluate the dotenv file as a shell script; pg_dump reads db service environment.
docker compose --env-file .env -f docker-compose.yml exec -T db sh -ceu   'exec pg_dump --format=custom --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$TMP"
[[ -s "$TMP" ]] || { echo 'BLOCKED: empty dump' >&2; exit 2; }
# Validate archive without touching the database.
docker compose --env-file .env -f docker-compose.yml exec -T db   pg_restore --list < "$TMP" > /dev/null
chmod 600 "$TMP"
mv -n -- "$TMP" "$DEST"
[[ ! -e "$TMP" ]] || { echo 'BLOCKED: concurrent backup destination creation' >&2; exit 2; }
[[ -f "$DEST" ]] || { echo 'BLOCKED: backup move failed' >&2; exit 2; }
sha256sum "$DEST" > "${DEST}.sha256"
echo "Database backup and SHA256 manifest: $DEST"
echo 'NOTE: MinIO and Redis volumes require independent consistent backup and restore drill.'
