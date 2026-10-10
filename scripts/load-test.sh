#!/usr/bin/env bash
# Rough load test. Usage: scripts/load-test.sh [events=5000] [concurrency=32]
# Needs a running stack, KEY=<api key>, and an endpoint to <mock>/hook/ok with filter "load.test".
# One curl process per request adds overhead, so treat the numbers as a LOWER bound.
set -euo pipefail
BASE=${BASE:-http://localhost:8080}; MOCK=${MOCK:-http://localhost:9000}
N=${1:-5000}; P=${2:-32}
: "${KEY:?set KEY to an API key}"
export BASE KEY
curl -fsS -X POST "$MOCK/reset" >/dev/null

echo "Publishing $N events with concurrency $P ..."
now() { python3 -c 'import time;print(time.time())'; }
s=$(now)
seq "$N" | xargs -P "$P" -I{} sh -c \
  'curl -fsS -o /dev/null -X POST "$BASE/api/v1/events" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" -d "{\"type\":\"load.test\",\"payload\":{\"n\":{}}}"'
pub=$(now)
until [ "$(curl -s "$MOCK/inspect/stats" | jq .distinctDeliveries)" -ge "$N" ]; do sleep 0.5; done
end=$(now)

awk -v n="$N" -v s="$s" -v p="$pub" -v e="$end" 'BEGIN {
  printf "publish:  %.0f events/s (%.1fs)\n", n/(p-s), p-s;
  printf "end-to-end (publish to last delivery): %.0f deliveries/s (%.1fs)\n", n/(e-s), e-s }'
curl -s "$MOCK/inspect/stats" | jq .
