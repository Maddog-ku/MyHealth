#!/usr/bin/env bash
# Real-backend smoke test for auth token rotation and meal confirmation validation.
#
# Usage:
#   scripts/smoke-api.sh
#   API_BASE_URL=http://127.0.0.1:18080/api/v1 scripts/smoke-api.sh

set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8080/api/v1}"
PASSWORD="Secret123"
EMAIL="codex-smoke-$(date +%s)-$$@example.com"

require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required to run this smoke test." >&2
    exit 1
  fi
}

request_json() {
  local method="$1"
  local path="$2"
  local body="$3"
  local output="$4"
  local status

  status=$(curl -sS -o "$output" -w '%{http_code}' \
    -X "$method" \
    -H 'Content-Type: application/json' \
    -d "$body" \
    "$API_BASE_URL$path")
  echo "$status"
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  local body="${4:-}"

  if [[ "$actual" != "$expected" ]]; then
    echo "${label}: expected HTTP ${expected}, got ${actual}" >&2
    [[ -n "$body" && -f "$body" ]] && cat "$body" >&2
    exit 1
  fi
  echo "${label}: ${actual}"
}

delete_account() {
  if [[ -n "${ACCESS_TOKEN:-}" ]]; then
    curl -sS -o /dev/null -w '' \
      -X DELETE \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      "$API_BASE_URL/me" >/dev/null 2>&1 || true
  fi
}

require_jq
trap delete_account EXIT

register_body=$(jq -n --arg email "$EMAIL" --arg password "$PASSWORD" '{
  email: $email,
  password: $password,
  name: "Smoke",
  gender: "other",
  heightCm: 170,
  weightKg: 65,
  age: 30,
  goal: "maintain",
  equipment: [],
  experience: "beginner",
  theme: "system",
  language: "zh-TW"
}')

login_body=$(jq -n --arg email "$EMAIL" --arg password "$PASSWORD" '{
  email: $email,
  password: $password
}')

status=$(request_json POST /auth/register "$register_body" /tmp/myhealth-smoke-register.json)
assert_status "$status" 201 "register" /tmp/myhealth-smoke-register.json

status=$(request_json POST /auth/login "$login_body" /tmp/myhealth-smoke-login.json)
assert_status "$status" 200 "login" /tmp/myhealth-smoke-login.json

ACCESS_TOKEN=$(jq -r '.accessToken' /tmp/myhealth-smoke-login.json)
old_refresh=$(jq -r '.refreshToken' /tmp/myhealth-smoke-login.json)
if [[ "$ACCESS_TOKEN" == "null" || "$old_refresh" == "null" ]]; then
  echo "login response did not include accessToken and refreshToken" >&2
  exit 1
fi

refresh_body=$(jq -n --arg refreshToken "$old_refresh" '{refreshToken: $refreshToken}')
status=$(request_json POST /auth/refresh "$refresh_body" /tmp/myhealth-smoke-refresh.json)
assert_status "$status" 200 "refresh" /tmp/myhealth-smoke-refresh.json

new_refresh=$(jq -r '.refreshToken' /tmp/myhealth-smoke-refresh.json)
if [[ "$new_refresh" == "null" || "$new_refresh" == "$old_refresh" ]]; then
  echo "refresh did not rotate the refresh token" >&2
  exit 1
fi

status=$(request_json POST /auth/refresh "$refresh_body" /tmp/myhealth-smoke-refresh-reuse.json)
assert_status "$status" 401 "old refresh reuse" /tmp/myhealth-smoke-refresh-reuse.json

# Reuse detection intentionally revokes the token family, so login again for cleanup and meal validation.
status=$(request_json POST /auth/login "$login_body" /tmp/myhealth-smoke-login-after-reuse.json)
assert_status "$status" 200 "login after reuse detection" /tmp/myhealth-smoke-login-after-reuse.json
ACCESS_TOKEN=$(jq -r '.accessToken' /tmp/myhealth-smoke-login-after-reuse.json)

invalid_items='[{"name":"","grams":-1,"kcal":-1,"protein":-1,"fat":-1,"carb":-1,"confidence":2}]'
status=$(curl -sS -o /tmp/myhealth-smoke-invalid-meal.json -w '%{http_code}' \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F description=bad \
  -F slot=lunch \
  -F "items=${invalid_items}" \
  "$API_BASE_URL/meals/confirm")
assert_status "$status" 400 "invalid meal confirm" /tmp/myhealth-smoke-invalid-meal.json

status=$(curl -sS -o /tmp/myhealth-smoke-delete.json -w '%{http_code}' \
  -X DELETE \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "$API_BASE_URL/me")
assert_status "$status" 204 "delete smoke account" /tmp/myhealth-smoke-delete.json
ACCESS_TOKEN=""

echo "smoke test passed (${EMAIL})"
