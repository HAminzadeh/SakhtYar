#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-}"
if [[ -z "$REPO" ]]; then
  echo "Usage: ./apply-identity-access-control.sh /path/to/SakhtYar"
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"

for folder in backend frontend readMe; do
  if [[ -d "$HERE/$folder" ]]; then
    mkdir -p "$REPO/$folder"
    cp -R "$HERE/$folder/." "$REPO/$folder/"
  fi
done

cp "$HERE/.env.example" "$REPO/.env.example"
cp "$HERE/docker-compose.yml" "$REPO/docker-compose.yml"

echo "Identity & Access Control v1 applied."
