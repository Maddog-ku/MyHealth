#!/usr/bin/env bash
# Production deployment preflight for MyHealth.
# Usage:
#   scripts/prod-check.sh
#   scripts/prod-check.sh --env .env.prod --url http://localhost:8088

set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE=".env.prod"
URL=""

usage() {
  sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV_FILE="${2:-}"
      [[ -n "$ENV_FILE" ]] || { echo "--env requires a file path" >&2; exit 2; }
      shift 2 ;;
    --url)
      URL="${2:-}"
      [[ -n "$URL" ]] || { echo "--url requires a base URL" >&2; exit 2; }
      shift 2 ;;
    -h|--help)
      usage
      exit 0 ;;
    *)
      echo "unknown arg: $1" >&2
      usage >&2
      exit 2 ;;
  esac
done

fail() {
  echo "❌ $*" >&2
  exit 1
}

warn() {
  echo "⚠ $*" >&2
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

require_cmd docker
require_cmd awk

[[ -f "$ENV_FILE" ]] || fail "$ENV_FILE not found. Copy .env.prod.example to $ENV_FILE and edit it."

# Read simple KEY=VALUE env files without executing them.
env_value() {
  awk -F= -v key="$1" '
    $0 !~ /^[[:space:]]*#/ && $1 == key {
      sub(/^[^=]*=/, "", $0)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      gsub(/^"|"$/, "", $0)
      gsub(/^'\''|'\''$/, "", $0)
      print $0
      exit
    }
  ' "$ENV_FILE"
}

require_value() {
  local key="$1"
  local value
  value="$(env_value "$key")"
  [[ -n "$value" ]] || fail "$key is missing in $ENV_FILE"
  printf '%s' "$value"
}

reject_placeholder() {
  local key="$1"
  local value="$2"
  case "$value" in
    replace-*|*your-domain.example*|change-me-*|changeme)
      fail "$key still contains a placeholder value" ;;
  esac
}

POSTGRES_PASSWORD="$(require_value POSTGRES_PASSWORD)"
SPRING_DATASOURCE_PASSWORD="$(require_value SPRING_DATASOURCE_PASSWORD)"
JWT_SECRET="$(require_value JWT_SECRET)"
CORS_ALLOWED_ORIGINS="$(require_value CORS_ALLOWED_ORIGINS)"

reject_placeholder POSTGRES_PASSWORD "$POSTGRES_PASSWORD"
reject_placeholder SPRING_DATASOURCE_PASSWORD "$SPRING_DATASOURCE_PASSWORD"
reject_placeholder JWT_SECRET "$JWT_SECRET"
reject_placeholder CORS_ALLOWED_ORIGINS "$CORS_ALLOWED_ORIGINS"

if [[ "$POSTGRES_PASSWORD" != "$SPRING_DATASOURCE_PASSWORD" ]]; then
  warn "POSTGRES_PASSWORD and SPRING_DATASOURCE_PASSWORD differ; this is valid only if the DB user password was changed separately."
fi

if [[ ${#JWT_SECRET} -lt 32 ]]; then
  fail "JWT_SECRET must be at least 32 characters"
fi

if [[ "$JWT_SECRET" == "change-me-to-a-base64-or-long-random-secret-at-least-32-bytes" ]]; then
  fail "JWT_SECRET must not use the development default"
fi

if [[ "$CORS_ALLOWED_ORIGINS" != https://* && "$CORS_ALLOWED_ORIGINS" != http://localhost:* ]]; then
  warn "CORS_ALLOWED_ORIGINS is not HTTPS or localhost: $CORS_ALLOWED_ORIGINS"
fi

echo "==> Validating docker compose production config"
MYHEALTH_ENV_FILE="$ENV_FILE" docker compose --env-file "$ENV_FILE" -f docker-compose.prod.yml config --quiet

if [[ -n "$URL" ]]; then
  require_cmd curl
  base="${URL%/}"
  echo "==> Checking deployed frontend health: $base/healthz"
  curl -fsS "$base/healthz" >/dev/null
  echo "==> Checking deployed API proxy: $base/api/v1/ai/status"
  curl -fsS "$base/api/v1/ai/status" >/dev/null
fi

echo "✅ production preflight passed"
