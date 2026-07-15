#!/bin/sh
# End-to-end smoke test: mock LLM + stub gradle + real server + real HTTP API.
set -e
MF=$(cd "$(dirname "$0")/.." && pwd)
SCRATCH=$(mktemp -d)
JAR="$SCRATCH/cookies.txt"

export MOCK_PORT=4519
node "$MF/scripts/fixtures/mock-openai.js" &
MOCK_PID=$!

export DATA_DIR="$SCRATCH/data"
export PORT=4518
export OPENAI_COMPAT_BASE_URL="http://127.0.0.1:4519/v1"
export FREE_PROVIDER=openai-compat
export FREE_MODEL=mock-model
export MODFORGE_GRADLE="$MF/scripts/fixtures/fake-gradle.sh"
export ALLOW_DEV_UPGRADE=1
chmod +x "$MODFORGE_GRADLE"
node "$MF/server.js" &
SERVER_PID=$!
trap 'kill $MOCK_PID $SERVER_PID 2>/dev/null' EXIT
sleep 1.5

B="http://127.0.0.1:4518/api"
say() { echo "== $1"; }

say "signup"
curl -sf -c "$JAR" -X POST "$B/auth/signup" -H 'Content-Type: application/json' \
  -d '{"email":"e2e@test.de","password":"testpass123"}' | grep -q '"ok":true'

say "me shows free plan + backend"
ME=$(curl -sf -b "$JAR" "$B/me")
echo "$ME" | grep -q '"plan":"free"'
echo "$ME" | grep -q 'openai-compat'

say "create project"
PID=$(curl -sf -b "$JAR" -X POST "$B/projects" -H 'Content-Type: application/json' \
  -d '{"name":"Rubin Mod","kind":"mod"}' | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
echo "   project id=$PID"

say "send first message (expect ask_user pause)"
curl -sf -b "$JAR" -X POST "$B/projects/$PID/messages" -H 'Content-Type: application/json' \
  -d '{"text":"Mach mir einen Mod mit einem Rubin-Item"}' | grep -q '"ok":true'

for i in $(seq 1 20); do
  STATUS=$(curl -sf -b "$JAR" "$B/projects/$PID" | sed -n 's/.*"status":"\([a-z_]*\)".*/\1/p')
  [ "$STATUS" = "waiting_user" ] && break
  sleep 0.5
done
[ "$STATUS" = "waiting_user" ] || { echo "FAIL: expected waiting_user, got $STATUS"; exit 1; }
curl -sf -b "$JAR" "$B/projects/$PID" | grep -q 'Kreativ-Inventar'
echo "   ask_user question visible ✔"

say "answer question (resume -> write -> build -> deliver)"
curl -sf -b "$JAR" -X POST "$B/projects/$PID/messages" -H 'Content-Type: application/json' \
  -d '{"text":"Ja"}' | grep -q '"ok":true'

for i in $(seq 1 60); do
  PROJ=$(curl -sf -b "$JAR" "$B/projects/$PID")
  STATUS=$(echo "$PROJ" | sed -n 's/.*"status":"\([a-z_]*\)".*/\1/p')
  [ "$STATUS" = "idle" ] && break
  sleep 0.5
done
[ "$STATUS" = "idle" ] || { echo "FAIL: run did not finish, status=$STATUS"; echo "$PROJ"; exit 1; }
echo "$PROJ" | grep -q '"filename":"testmod-1.0.0.jar"' || { echo "FAIL: no delivered artifact"; echo "$PROJ"; exit 1; }
echo "   artifact delivered ✔"

say "download artifact"
AID=$(echo "$PROJ" | sed -n 's/.*"artifacts":\[{"id":\([0-9]*\).*/\1/p')
curl -sf -b "$JAR" -o "$SCRATCH/dl.jar" "$B/artifacts/$AID/download"
[ -s "$SCRATCH/dl.jar" ] || { echo "FAIL: empty download"; exit 1; }
echo "   downloaded $(wc -c < "$SCRATCH/dl.jar") bytes ✔"

say "usage counted"
curl -sf -b "$JAR" "$B/me" | grep -q '"messages_used":2'

say "dev-upgrade flips plan"
curl -sf -b "$JAR" -X POST "$B/billing/dev-upgrade" | grep -q '"plan":"premium"'
curl -sf -b "$JAR" "$B/me" | grep -q '"plan":"premium"'

say "checkout returns stub without keys"
curl -sf -b "$JAR" -X POST "$B/billing/checkout" | grep -q '"stub":true'

say "auth required"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$B/projects")
[ "$CODE" = "401" ] || { echo "FAIL: expected 401, got $CODE"; exit 1; }

echo ""
echo "E2E SMOKE TEST PASSED ✔"
