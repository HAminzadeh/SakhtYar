#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/SakhtYar" >&2
  exit 2
fi
OVERLAY="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$1" && pwd)"
for top in backend frontend readMe; do
  [ -d "$OVERLAY/$top" ] || continue
  while IFS= read -r -d '' file; do
    rel="${file#$OVERLAY/}"
    dest="$REPO/$rel"
    mkdir -p "$(dirname "$dest")"
    cp "$file" "$dest"
    echo "Applied $rel"
  done < <(find "$OVERLAY/$top" -type f -print0)
done
echo "Phase 3 overlay applied to $REPO"
echo "Next: (cd $REPO/backend && mvn clean test)"
echo "Then: (cd $REPO/frontend && npm run build)"
