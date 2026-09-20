#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/SakhtYar"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$1" && pwd)"

for folder in backend frontend readMe; do
  if [[ -d "$ROOT/$folder" ]]; then
    mkdir -p "$REPO/$folder"
    cp -R "$ROOT/$folder/." "$REPO/$folder/"
  fi
done

echo "Phase 3 repository fix applied successfully."
echo "Base commit reviewed: f9c4c20c7a1b377a3def7bfc0931800581ada025"
echo "Next: build backend, build frontend, restart both services."
