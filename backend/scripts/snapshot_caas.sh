#!/usr/bin/env bash
# Downloads a snapshot of the real CAAS data into src/main/resources/snapshot, which the
# backend serves when it can't reach CAAS at runtime (Cloud Run's IPs are blocked by CAAS).
# CI runs this on a GitHub runner, which can reach CAAS, before building the image.
#
# Usage: CAAS_API_KEY=... backend/scripts/snapshot_caas.sh
set -euo pipefail

: "${CAAS_API_KEY:?set CAAS_API_KEY}"
BASE="${CAAS_BASE_URL:-https://api.swimapisg.info}"
OUT="$(dirname "$0")/../src/main/resources/snapshot"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

get() { curl -fsS --retry 3 -m 120 -H "apikey: $CAAS_API_KEY" "$BASE$1"; }

get /flight-manager/displayAll > "$TMP/flights.json"
for t in fixes navaids airports airways; do get "/geopoints/list/$t" > "$TMP/$t.json"; done

# The airway search matches substrings, so one search per letter returns every airway with its fixes.
# If that ever fails, the snapshot still works; routes are just drawn without airway detail.
for l in {A..Z}; do get "/geopoints/search/airways/$l" > "$TMP/search-$l.json" || echo '[]' > "$TMP/search-$l.json"; done
jq -s 'add | unique' "$TMP"/search-*.json > "$TMP/airway-search.json"
rm "$TMP"/search-*.json

for f in "$TMP"/{flights,fixes,navaids,airports,airways}.json; do jq -e 'type == "array" and length > 0' "$f" > /dev/null || { echo "Unexpected content in $(basename "$f")" >&2; exit 1; }; done
jq -n --arg t "$(date -u +%Y-%m-%dT%H:%MZ)" '{takenAt: $t}' > "$TMP/meta.json"

rm -rf "$OUT" && mkdir -p "$OUT" && cp "$TMP"/*.json "$OUT"/
echo "Snapshot written to $OUT:"
for f in "$OUT"/*.json; do echo "  $(basename "$f"): $(jq 'if type == "array" then length else . end' -c "$f")"; done
