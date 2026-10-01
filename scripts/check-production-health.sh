#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${ROUTEPULSE_PRODUCTION_URL:-}}"
if [[ -z "${BASE_URL}" ]]; then
  echo "Usage: $0 https://routepulse.example.com" >&2
  echo "Or set ROUTEPULSE_PRODUCTION_URL." >&2
  exit 2
fi

URL="${BASE_URL%/}/actuator/health/readiness"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if ! HTTP_CODE="$(curl \
  --silent \
  --show-error \
  --location \
  --connect-timeout 5 \
  --max-time 15 \
  --output "$TMP" \
  --write-out '%{http_code}' \
  "$URL")"; then
  echo "RoutePulse readiness check failed: could not reach $URL" >&2
  exit 1
fi

BODY="$(cat "$TMP")"

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "RoutePulse readiness check failed: HTTP $HTTP_CODE from $URL" >&2
  echo "$BODY" >&2
  exit 1
fi

if ! printf '%s' "$BODY" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
  echo "RoutePulse readiness check failed: endpoint did not report UP" >&2
  echo "$BODY" >&2
  exit 1
fi

echo "RoutePulse readiness: UP ($URL)"
