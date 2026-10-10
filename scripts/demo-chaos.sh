#!/usr/bin/env bash
# Chaos demo: break things on purpose and show Relay holds its promises.
# Run from the repo root after:  docker compose -f docker/docker-compose.yml up --build -d
set -euo pipefail

BASE=${BASE:-http://localhost:8080}
MOCK=${MOCK:-http://localhost:9000}
ADMIN_TOKEN=${RELAY_ADMIN_TOKEN:-dev-admin-token}   # sent as: Authorization: Bearer <admin token>
INNER=http://mock-receiver:9000                     # how relay-server reaches the mock INSIDE docker
C="docker compose -f docker/docker-compose.yml"
C2="docker compose -f docker/docker-compose.yml -f docker/docker-compose.scale.yml"

say()  { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
need() { command -v "$1" >/dev/null || { echo "missing tool: $1"; exit 1; }; }
need curl; need jq; need docker

api()  { curl -fsS -H "Authorization: Bearer $KEY" -H 'Content-Type: application/json' "$@"; }
wait_for() {  # wait_for "what" seconds command...
  local what=$1 t=$2; shift 2
  for ((i = 0; i < t; i++)); do "$@" >/dev/null 2>&1 && return 0; sleep 1; done
  echo "TIMEOUT waiting for: $what"; exit 1
}
mk_ep()   { api -X POST "$BASE/api/v1/endpoints" -d "{\"url\":\"$1\",\"description\":\"chaos\",\"eventTypeFilter\":\"$2\"}"; }
publish() { api -X POST "$BASE/api/v1/events" -H "Idempotency-Key: k-$RANDOM-$RANDOM" -d "{\"type\":\"$1\",\"payload\":$2}"; }
statuses() { api "$BASE/api/v1/deliveries?endpointId=$1&limit=200" | jq -r '[.items[].status] | unique | join(",")'; }
is_status() { [ "$(statuses "$1")" = "$2" ]; }
attempts() {
  local d; d=$(api "$BASE/api/v1/deliveries?endpointId=$1&limit=1" | jq -r '.items[0].id')
  api "$BASE/api/v1/deliveries/$d/attempts" | jq -r '.[] | "  attempt \(.attemptNumber)  status=\(.statusCode // "none")  \(.error // "")"'
}

wait_for "relay-server health" 120 curl -fs "$BASE/actuator/health"
wait_for "mock-receiver" 60 curl -fs "$MOCK/inspect/stats"

say "Setup: create an app"
APP=$(curl -fsS -X POST "$BASE/api/v1/apps" -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H 'Content-Type: application/json' -d "{\"name\":\"chaos-$RANDOM\"}")
KEY=$(jq -r '.apiKey' <<<"$APP")
[ -n "$KEY" ] && [ "$KEY" != null ] || { echo "no api key in: $APP"; exit 1; }
curl -fsS -X POST "$MOCK/reset" >/dev/null

say "1. Signature verification (the reference receiver rejects bad signatures)"
E=$(mk_ep "$INNER/hook/verify" demo.verify); EP=$(jq -r .id <<<"$E")
curl -fsS -X POST "$MOCK/config/secret" -H 'Content-Type: text/plain' --data "$(jq -r .signingSecret <<<"$E")"; echo
publish demo.verify '{"hello":"world"}' >/dev/null
wait_for "verified delivery" 30 is_status "$EP" SUCCEEDED
echo "delivered, and the receiver verified the HMAC (a 401 would have caused retries)"
attempts "$EP"

say "2. Receiver fails the first 3 attempts, then recovers"
E=$(mk_ep "$INNER/hook/fail-first/3" demo.failfirst); EP=$(jq -r .id <<<"$E")
publish demo.failfirst '{"n":1}' >/dev/null
wait_for "retries to succeed" 90 is_status "$EP" SUCCEEDED
attempts "$EP"

say "3. Kill Relay in the middle of a send (lease recovery, at-least-once)"
E=$(mk_ep "$INNER/hook/slow/3" demo.slow); EP=$(jq -r .id <<<"$E")
curl -fsS -X POST "$MOCK/reset" >/dev/null
publish demo.slow '{"n":1}' >/dev/null
sleep 1.5
$C kill relay-server
echo "relay-server killed mid-send. Restarting..."
$C start relay-server
wait_for "relay back up" 90 curl -fs "$BASE/actuator/health"
wait_for "recovered delivery (needs the 15s lease to expire)" 120 is_status "$EP" SUCCEEDED
attempts "$EP"
echo "mock saw: $(curl -s "$MOCK/inspect/stats")"
echo "(deliveriesWithMultipleHits >= 1 is at-least-once in action. Nothing was lost.)"

say "4. Two Relay instances, 1000 events: no delivery sent twice"
$C2 up -d relay-server-2
wait_for "second instance up" 120 curl -fs http://localhost:8081/actuator/health
E=$(mk_ep "$INNER/hook/ok" demo.bulk); EP=$(jq -r .id <<<"$E")
curl -fsS -X POST "$MOCK/reset" >/dev/null
export BASE KEY
seq 1000 | xargs -P 16 -I{} sh -c \
  'curl -fsS -o /dev/null -X POST "$BASE/api/v1/events" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" -d "{\"type\":\"demo.bulk\",\"payload\":{\"n\":{}}}"'
bulk_done() { [ "$(curl -s "$MOCK/inspect/stats" | jq .distinctDeliveries)" -ge 1000 ]; }
wait_for "1000 deliveries" 240 bulk_done
sleep 3
STATS=$(curl -s "$MOCK/inspect/stats"); echo "$STATS"
[ "$(jq .deliveriesWithMultipleHits <<<"$STATS")" = 0 ] && echo "PASS: every delivery was sent exactly once" \
                                                      || { echo "FAIL: duplicate sends"; exit 1; }

say "5. Dead receiver trips the circuit breaker, then resume drains the backlog"
E=$(mk_ep "$INNER/hook/switch" demo.switch); EP=$(jq -r .id <<<"$E")
curl -fsS -X POST "$MOCK/config/switch?ok=false"; echo
for i in $(seq 8); do publish demo.switch "{\"n\":$i}" >/dev/null; done
ep_paused() { [ "$(api "$BASE/api/v1/endpoints/$EP" | jq -r .status)" = PAUSED ]; }
wait_for "breaker to open" 90 ep_paused
echo "endpoint PAUSED. delivery statuses: $(statuses "$EP")  (PENDING/IN_FLIGHT, never DEAD)"
curl -fsS -X POST "$MOCK/config/switch?ok=true"; echo
api -X POST "$BASE/api/v1/endpoints/$EP/resume" >/dev/null
wait_for "backlog to drain" 120 is_status "$EP" SUCCEEDED
echo "backlog drained after resume: all SUCCEEDED"

say "6. SSRF: the cloud metadata address must be refused"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/endpoints" \
  -H "Authorization: Bearer $KEY" -H 'Content-Type: application/json' \
  -d '{"url":"http://169.254.169.254/latest/meta-data","description":"evil","eventTypeFilter":"*"}')
echo "HTTP $CODE (expected 400)"; [ "$CODE" = 400 ]

say "Chaos demo complete."
