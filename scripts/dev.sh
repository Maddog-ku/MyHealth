#!/usr/bin/env bash
# MyHealth dev 一鍵啟動：postgres + backend + frontend
# Ollama 預設不啟動（屬於 compose profile "ai"），AI 端點會回 503 但其餘功能正常。
# 用法：
#   scripts/dev.sh            # 同時啟動 Postgres + backend + frontend
#   scripts/dev.sh --ai       # 額外啟動 Ollama
#   scripts/dev.sh --stop     # 停止 docker compose 服務

set -euo pipefail
cd "$(dirname "$0")/.."

WITH_AI=0
STOP=0
for arg in "$@"; do
  case "$arg" in
    --ai)   WITH_AI=1 ;;
    --stop) STOP=1 ;;
    *) echo "unknown arg: $arg" >&2; exit 2 ;;
  esac
done

if [[ $STOP -eq 1 ]]; then
  docker compose --profile ai down
  exit 0
fi

# 1. 起 Postgres（背景）
echo "==> docker compose up postgres"
if [[ $WITH_AI -eq 1 ]]; then
  docker compose --profile ai up -d postgres ollama
else
  docker compose up -d postgres
fi

# 2. 等 postgres healthy
echo "==> 等 Postgres 健康檢查通過"
for i in {1..30}; do
  if docker compose exec -T postgres pg_isready -U myhealth -d myhealth >/dev/null 2>&1; then
    echo "    Postgres ready"
    break
  fi
  sleep 1
  if [[ $i -eq 30 ]]; then
    echo "Postgres 啟動逾時" >&2
    exit 1
  fi
done

# 3. backend（mvnw）、frontend（npm）並行
LOG_DIR=".dev-logs"
mkdir -p "$LOG_DIR"

echo "==> 啟動 backend (logs: $LOG_DIR/backend.log)"
( cd backend && ./mvnw -q spring-boot:run ) >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "==> 啟動 frontend (logs: $LOG_DIR/frontend.log)"
if [[ ! -d frontend/node_modules ]]; then
  ( cd frontend && npm install ) >"$LOG_DIR/frontend-install.log" 2>&1
fi
( cd frontend && npm run dev ) >"$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

cleanup() {
  echo
  echo "==> 收到中斷，正在停止 backend/frontend"
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup INT TERM

cat <<EOF

✅ 正在啟動：
   Backend  http://localhost:8080  (PID $BACKEND_PID, log: $LOG_DIR/backend.log)
   Frontend http://localhost:5173  (PID $FRONTEND_PID, log: $LOG_DIR/frontend.log)
   Swagger  http://localhost:8080/swagger-ui.html

   啟動需 10~30 秒。按 Ctrl+C 結束（不會停 Postgres）。
   如要停 Postgres：scripts/dev.sh --stop
EOF

wait
