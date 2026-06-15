#!/usr/bin/env bash
# MyHealth dev 一鍵啟動：Postgres + Redis + backend + frontend
# Ollama 預設不啟動（屬於 compose profile "ai"）；不帶 --ai 時 AI 端點會回 503，其餘功能正常。
#
# 用法：
#   scripts/dev.sh            # 啟動 Postgres + Redis + backend + frontend
#   scripts/dev.sh --ai       # 額外啟動 Ollama（AI 端點可用）
#   scripts/dev.sh --restart  # 啟動前先收掉殘留在 8080/5173 的舊 backend/frontend（乾淨重啟）
#   scripts/dev.sh --infra    # 只起依賴服務（Postgres/Redis），不跑 backend/frontend
#   scripts/dev.sh --reset    # 重置 DB（DROP SCHEMA 後讓 Flyway 重建）再正常啟動
#   scripts/dev.sh --seed     # 後端就緒後建立 demo 帳號（demo@example.com / Secret123）
#   scripts/dev.sh --stop     # 停止並移除 docker compose 服務
#   scripts/dev.sh --help     # 顯示說明
#
# 方便測試的一鍵指令：scripts/dev.sh --restart --seed --ai
#   （乾淨重啟、建好 demo 帳號、啟用本機 AI）

set -euo pipefail
cd "$(dirname "$0")/.."

WITH_AI=0
STOP=0
INFRA_ONLY=0
RESET=0
SEED=0
RESTART=0
ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --ai)        WITH_AI=1 ;;
    --infra)     INFRA_ONLY=1 ;;
    --reset)     RESET=1 ;;
    --seed)      SEED=1 ;;
    --restart|--fresh) RESTART=1 ;;
    -y|--yes)    ASSUME_YES=1 ;;
    --stop|--down) STOP=1 ;;
    -h|--help)
      sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "unknown arg: ${arg} (用 --help 看用法)" >&2; exit 2 ;;
  esac
done

# --- 前置檢查：docker daemon 是否在跑 -----------------------------------------
if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker daemon 未啟動，請先開啟 Docker Desktop。" >&2
  exit 1
fi

if [[ $STOP -eq 1 ]]; then
  echo "==> 停止 docker compose 服務（含 ai profile）"
  docker compose --profile ai down
  exit 0
fi

# --- 1. 起依賴服務（背景）-----------------------------------------------------
if [[ $WITH_AI -eq 1 ]]; then
  echo "==> docker compose up postgres + redis + ollama"
  docker compose --profile ai up -d postgres redis ollama
else
  echo "==> docker compose up postgres + redis"
  docker compose up -d postgres redis
fi

# --- 2. 等 Postgres 真的可連線（從 host 角度）---------------------------------
echo "==> 等 Postgres 健康檢查通過"
for i in {1..30}; do
  if docker compose exec -T postgres pg_isready -U myhealth -d myhealth >/dev/null 2>&1; then
    echo "    Postgres ready (localhost:5433)"
    break
  fi
  sleep 1
  if [[ $i -eq 30 ]]; then
    echo "❌ Postgres 啟動逾時" >&2
    exit 1
  fi
done

# --- 2.5 (--reset) 重置資料庫 -------------------------------------------------
# DROP SCHEMA 會清掉所有資料表與 Flyway 歷史，讓 backend 啟動時由 Flyway 從頭重建。
# 用於 Flyway 歷史與資料表不同步（例如歷史被清空導致一重啟就崩潰）的情況。
if [[ $RESET -eq 1 ]]; then
  if [[ $ASSUME_YES -ne 1 ]]; then
    if [[ -t 0 ]]; then
      printf "⚠ 這會清空 myhealth 資料庫的所有資料（含使用者帳號）。確定？[y/N] "
      read -r ans
      [[ "$ans" == "y" || "$ans" == "Y" ]] || { echo "已取消重置。"; exit 0; }
    else
      echo "❌ --reset 需確認；非互動環境請加 -y/--yes。" >&2
      exit 1
    fi
  fi
  echo "==> 重置資料庫 schema（Flyway 將於啟動時重建）"
  docker compose exec -T postgres psql -U myhealth -d myhealth \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO myhealth; GRANT ALL ON SCHEMA public TO public;" \
    >/dev/null
  echo "    schema 已清空"
fi

# --- 3. 確認 Redis 有回應 -----------------------------------------------------
if docker compose exec -T redis redis-cli ping >/dev/null 2>&1; then
  echo "    Redis ready (localhost:6379)"
else
  echo "    ⚠ Redis 尚未就緒（rate-limit 預設用記憶體後端，通常不影響啟動）"
fi

if [[ $INFRA_ONLY -eq 1 ]]; then
  echo
  echo "✅ 依賴服務已啟動（--infra）。停止：scripts/dev.sh --stop"
  exit 0
fi

# --- 3.5 連接埠前置檢查 -------------------------------------------------------
# backend 無法換 port，8080 被占用就會啟動失敗（exit 1）並連帶讓本腳本收尾。
# 先擋下來給清楚訊息，而不是啟動一個註定失敗的 backend。
port_pid() { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1; }

# (--restart) 啟動前先收掉殘留在 8080/5173 的舊 backend/frontend，
# 讓 Ctrl+C 沒收乾淨、或在別的終端機殘留時也能一鍵乾淨重啟。
if [[ $RESTART -eq 1 ]]; then
  echo "==> --restart：清掉殘留在 8080 / 5173 的舊程序"
  killed_pids=""
  for port in 8080 5173; do
    rpid=$(port_pid "$port") || true
    if [[ -n "$rpid" ]]; then
      echo "    kill ${rpid} (port ${port}: $(ps -p "${rpid}" -o comm= 2>/dev/null))"
      kill "${rpid}" 2>/dev/null || true
      killed_pids="${killed_pids} ${rpid}"
    fi
  done
  # 等舊 listener 和 8080 真的釋放，避免緊接著的前置檢查仍看到占用。
  # 再給 wrapper 一點時間寫完 SIGTERM/143 訊息，避免混進新一輪 log 尾端造成誤判。
  for _ in {1..10}; do
    ports_clear=1
    [[ -n "$(port_pid 8080)" ]] && ports_clear=0
    for rpid in $killed_pids; do
      kill -0 "$rpid" 2>/dev/null && ports_clear=0
    done
    [[ $ports_clear -eq 1 ]] && break
    sleep 1
  done
  [[ -n "$killed_pids" ]] && sleep 1
fi

pid=$(port_pid 8080) || true
if [[ -n "$pid" ]]; then
  echo "❌ 連接埠 8080 已被占用 (PID ${pid}: $(ps -p "${pid}" -o comm= 2>/dev/null))" >&2
  echo "   多半是先前的 backend 還在跑。可一鍵乾淨重啟:" >&2
  echo "     scripts/dev.sh --restart" >&2
  echo "   或手動停掉再重試:  kill ${pid}   # 或 pkill -f spring-boot:run" >&2
  exit 1
fi
pid=$(port_pid 5173) || true
if [[ -n "$pid" ]]; then
  echo "⚠ 連接埠 5173 已被占用 (PID ${pid}); Vite 會自動改用 5174 等其他埠 (見 frontend 日誌)"
fi

# --- 4. backend（mvnw）、frontend（npm）並行 ---------------------------------
LOG_DIR=".dev-logs"
mkdir -p "$LOG_DIR"
: >"$LOG_DIR/backend.log"
: >"$LOG_DIR/frontend.log"

echo "==> 啟動 backend (logs: $LOG_DIR/backend.log)"
( cd backend && ./mvnw -q spring-boot:run ) >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "==> 啟動 frontend (logs: $LOG_DIR/frontend.log)"
if [[ ! -d frontend/node_modules ]]; then
  echo "    首次啟動，安裝前端相依套件中…"
  ( cd frontend && npm install ) >"$LOG_DIR/frontend-install.log" 2>&1
fi
( cd frontend && npm run dev ) >"$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

stop_children() {
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
on_interrupt() {
  echo
  echo "==> 收到 Ctrl+C，正在停止 backend/frontend（Postgres/Redis 保持執行）"
  stop_children
  exit 0
}
trap on_interrupt INT TERM

cat <<EOF

✅ 正在啟動：
   Backend  http://localhost:8080  (PID $BACKEND_PID, log: $LOG_DIR/backend.log)
   Frontend http://localhost:5173  (PID $FRONTEND_PID, log: $LOG_DIR/frontend.log)
   Swagger  http://localhost:8080/swagger-ui.html
$( [[ $WITH_AI -eq 1 ]] && echo "   Ollama   http://localhost:11434  (AI 端點已啟用)" )

   按 Ctrl+C 結束 backend/frontend（不會停 Postgres/Redis）。
   只停依賴服務：scripts/dev.sh --stop
   看後端日誌：  tail -f $LOG_DIR/backend.log
EOF

# 等 backend 真正就緒：輪詢 /actuator/health（DB 連得上才會回 UP/200）。
# 比固定睡 30 秒準確，也能在 backend 啟動失敗時即時發現。
echo "==> 等待 backend 就緒（/actuator/health）…"
for i in $(seq 1 60); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then break; fi   # 交給下面的死亡偵測處理
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "    ✅ backend ready — http://localhost:5173 可以開了"
    break
  fi
  sleep 1
  [[ $i -eq 60 ]] && echo "    ⚠ 60 秒內未就緒，請看 $LOG_DIR/backend.log"
done

# (--seed) 後端就緒後建立 demo 帳號，省去每次 --reset 後手動註冊。
# 透過 register API 建立（dev-only，不寫進 Flyway migration 以免帶到 prod）。
if [[ $SEED -eq 1 ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/v1/auth/register \
    -H 'Content-Type: application/json' \
    -d '{"email":"demo@example.com","password":"Secret123","name":"Demo","gender":"other","heightCm":170,"weightKg":65}' 2>/dev/null || true)
  case "${code}" in
    201) echo "    ✅ 已建立 demo 帳號: demo@example.com / Secret123" ;;
    409) echo "    ℹ demo 帳號已存在: demo@example.com / Secret123" ;;
    *)   echo "    ⚠ demo 帳號建立失敗 (HTTP ${code})，可稍後手動註冊" ;;
  esac
fi

# 若任一服務先結束就一起收掉，避免殘留孤兒程序
# （macOS 內建 bash 3.2 沒有 `wait -n`，改用輪詢）
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done

# 走到這裡代表「不是 Ctrl+C，而是某個服務自己結束了」——指出是誰、附上日誌末尾
echo
if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
  echo "❌ backend 已結束（很可能啟動失敗）。$LOG_DIR/backend.log 末尾："
  tail -n 15 "$LOG_DIR/backend.log" 2>/dev/null | sed 's/^/   /'
else
  echo "❌ frontend 已結束。$LOG_DIR/frontend.log 末尾："
  tail -n 15 "$LOG_DIR/frontend.log" 2>/dev/null | sed 's/^/   /'
fi
echo "==> 正在停止另一個服務（Postgres/Redis 保持執行）"
stop_children
exit 1
