#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-}"
if [[ -z "$REPO" ]]; then
  echo "Usage: ./apply-professional-mobilefirst-ui.sh /path/to/SakhtYar"
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"

for folder in frontend readMe; do
  if [[ -d "$HERE/$folder" ]]; then
    mkdir -p "$REPO/$folder"
    cp -R "$HERE/$folder/." "$REPO/$folder/"
  fi
done

echo "Professional Mobile-First UI applied."
