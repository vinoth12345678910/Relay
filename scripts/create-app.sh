#!/usr/bin/env bash
# Creates an app and prints its API key (shown only once).
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
ADMIN_TOKEN=${RELAY_ADMIN_TOKEN:-dev-admin-token}
curl -fsS -X POST "$BASE/api/v1/apps" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"name\":\"${1:-my-app}\"}" | jq .
echo "Save the key now. It is shown only once."
