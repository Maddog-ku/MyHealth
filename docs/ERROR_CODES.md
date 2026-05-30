# 錯誤代碼與崩潰排查手冊

這份文件讓開發者**自行對照錯誤、定位崩潰原因**。分三部分：

1. [錯誤回應格式](#1-錯誤回應格式)
2. [錯誤代碼對照表](#2-錯誤代碼對照表)（`error` 欄位 → HTTP 狀態 → 意義 → 處理方式）
3. [崩潰排查：症狀 → 根因 → 解法](#3-崩潰排查症狀--根因--解法)（**最常用，先看這裡**）

> 來源：`backend/src/main/java/com/myhealth/common/ErrorCode.java`、`GlobalExceptionHandler.java`，以及各 service 的 `ApiException` 拋出點。若程式更新，請同步本表。

---

## 1. 錯誤回應格式

所有 4xx / 5xx 都回傳統一的 JSON（`ApiErrorResponse`）：

```json
{
  "timestamp": "2026-05-31T01:16:15.007542Z",
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "message": "Service temporarily unavailable, please retry shortly",
  "path": "/api/v1/auth/login",
  "details": []
}
```

| 欄位 | 說明 |
|------|------|
| `status` | HTTP 狀態碼 |
| `error` | 機器可讀的錯誤代碼（見下表），前端用它分流處理 |
| `message` | 人類可讀說明（**不含**密碼、token、照片內容等敏感資料）|
| `path` | 出錯的請求路徑 |
| `details[]` | 欄位級驗證錯誤：`{ field, code, message }`，僅 `VALIDATION_ERROR` 會帶 |

前端對應型別在 `frontend/src/api/client.ts` 的 `ApiError`（`status` / `code` / `details`）。

---

## 2. 錯誤代碼對照表

| `error` | HTTP | 何時發生 | 開發者怎麼處理 |
|---------|------|----------|----------------|
| `VALIDATION_ERROR` | 400 | 請求 body 欄位驗證失敗、缺必填 query 參數 | 看 `details[]` 的 `field`/`message`，修正輸入。屬用戶端錯誤，非 bug |
| `BAD_REQUEST` | 400 | 參數型別錯、body 無法解析、餐點需 image 或 description、日期區間非法（>90 天 / from>to）、檔案路徑不合法 | 看 `message`。前端不應重試 |
| `UNAUTHORIZED` | 401 | 未帶/無效 token、Email 或密碼錯、token 對應的使用者已不存在 | 引導重新登入；前端 axios 攔截器會自動嘗試 refresh 一次 |
| `TOKEN_EXPIRED` | 401 | access token **過期**（與「無效」區分；由 JWT 進入點偵測 `ExpiredJwtException`）| 前端據此靜默走 refresh 流程換新 access token；refresh 也失敗才重新登入 |
| `INVALID_REFRESH_TOKEN` | 401 | refresh token 無效、已被使用（reuse 偵測）| 清空本地 token、強制重新登入。reuse 偵測代表 token 可能外洩 |
| `FORBIDDEN` | 403 | 已登入但無權限存取該資源 | 檢查角色/擁有權邏輯 |
| `NOT_FOUND` | 404 | 找不到 workout / meal / 圖片 | 確認 id 與擁有者；前端不重試 |
| `CONFLICT` | 409 | Email 已註冊、違反唯一/外鍵約束 | 提示用戶換 Email，或檢查資料一致性 |
| `PAYLOAD_TOO_LARGE` | 413 | 上傳檔案超過上限（單檔 10MB / 請求 12MB，見 `application.yml`）| 前端先壓縮或限制大小 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 上傳的不是支援的圖片格式 | 僅接受合法圖片；前端先驗證 MIME |
| `RATE_LIMITED` | 429 | 觸發限流（register/login/refresh/AI 端點，預設每分鐘額度見 `app.rate-limit.*`）| 看回應的 `Retry-After`；前端做退避重試 |
| `SERVICE_UNAVAILABLE` | 503 | **資料庫暫時連不上**（連線被拒、連線池取不到連線）；或 **Redis 限流後端不可用且 `fail-open=false`** | 基礎設施問題，**非程式 bug**。見 [§3](#3-崩潰排查症狀--根因--解法)。前端對 5xx 會自動重試 2 次 |
| `INTERNAL_ERROR` | 500 | 未被預期攔截的例外 | **看 `.dev-logs/backend.log` 的 stack trace**。這是唯一需要真正 debug 程式的代碼 |

### 已宣告但目前不會出現的代碼

| `error` | 狀態 | 說明 |
|---------|------|------|
| `AI_UNAVAILABLE` | — | enum 中存在，但**刻意**讓 AI 失敗時回退到內建模板（HTTP 200，餐點回 `MealAnalysis.empty()` 並附「AI 暫不可用」提示），而非拋錯中斷流程。屬設計決策，詳見 README「要讓 AI 真的跑起來」 |

---

## 3. 崩潰排查：症狀 → 根因 → 解法

> 第一步永遠是看後端日誌：
> ```bash
> tail -n 200 .dev-logs/backend.log        # 最近 200 行
> tail -f .dev-logs/backend.log            # 即時追蹤
> ```

### A. 前端一直跳 500 / 503，`GET /api/v1/me` 失敗

**先抓 log 關鍵字**：
```bash
grep -iE "Connection refused|HikariPool|JDBCConnectionException" .dev-logs/backend.log | tail
```

| log 出現 | 根因 | 解法 |
|----------|------|------|
| `Connection to localhost:5433 refused` / `HikariPool-1 - Connection is not available` | **Postgres 容器沒在跑**（常見於跑過 `docker compose down`）| `scripts/dev.sh --infra` 重新拉起 Postgres + Redis |
| 回應是 **503 `SERVICE_UNAVAILABLE`** | 同上，DB 暫時連不上。後端已調成 ~5s 快速失敗、不會卡死 | 確認容器：`docker compose ps`；必要時 `scripts/dev.sh --infra` |

> 設計說明：DB 不可用時請求會在約 5 秒內失敗（Hikari `connection-timeout`），避免 Tomcat 執行緒被卡滿導致整個服務無回應。相關參數在 `application.yml` 的 `spring.datasource.hikari`，可用 `DB_CONNECTION_TIMEOUT_MS` 等環境變數覆寫。

### B. 後端啟動就崩、退出碼 1（服務根本沒起來）

```bash
grep -iE "Flyway|Migration|already exists|APPLICATION FAILED" .dev-logs/backend.log | tail
```

| log 出現 | 根因 | 解法 |
|----------|------|------|
| `Migration V1__init.sql failed` / `relation "users" already exists` | **Flyway 歷史表與實際資料表不同步**（`flyway_schema_history` 被清空但資料表還在）| `scripts/dev.sh --reset`（會 DROP SCHEMA 重建；**清空所有資料**，加 `-y` 跳過確認）|
| `Validate failed: ... checksum mismatch` | 已套用的 migration 檔被改動 | 不要改已 release 的 migration；本機開發可 `--reset` 重來 |
| `Schema-validation: missing table/column`（Hibernate `ddl-auto: validate`）| Entity 與 DB schema 不一致，少了 migration | 補一支 `V{n}__xxx.sql` migration |

### C. 前端顯示「無法連線到伺服器，請確認後端是否啟動後重試」

- **根因**：後端完全沒回應（HTTP status 0），通常是 backend 沒起來或 port 8080 沒人聽。
- **檢查**：
  ```bash
  lsof -nP -iTCP:8080 -sTCP:LISTEN     # 有沒有人在聽 8080
  tail -n 50 .dev-logs/backend.log     # backend 是不是還在啟動或已崩
  ```
- **解法**：`scripts/dev.sh`（完整啟動）或先 `--infra` 確認依賴服務再起 backend。

### D. 一直 429 `RATE_LIMITED`

- **根因**：短時間內對 auth/AI 端點請求過多，觸發 Bucket4j 限流。
- **檢查**：額度設定在 `application.yml` 的 `app.rate-limit.*`（如 `login-limit`、`window`）。
- **解法**：等 `Retry-After` 秒數；本機測試可調高對應的 `RATE_LIMIT_*` 環境變數。

### E. 偶發 500 `INTERNAL_ERROR`（非上述情況）

- 這才是**真正需要 debug 程式**的情況。`INTERNAL_ERROR` 代表有未被預期攔截的例外。
- **一定要看 stack trace**：
  ```bash
  grep -nE "ERROR|Exception|Caused by" .dev-logs/backend.log | tail -40
  ```
- 找到 `Caused by:` 最底層那行，對照拋出位置修正。

---

## 附：環境快速健檢

```bash
docker compose ps                                   # 容器狀態（postgres/redis 應 healthy）
nc -vz 127.0.0.1 5433                                # Postgres 是否可連
docker exec myhealth-redis-1 redis-cli ping          # Redis 是否回 PONG
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/actuator/health              # 200=後端+DB 就緒；503=DB 掛；000=後端沒起
```

> `/actuator/health` 是後端就緒探針（`scripts/dev.sh` 啟動時就用它判斷 backend ready）。
> 匿名只看到 `{"status":"UP"}`；帶 access token 才會展開 `db`、`diskSpace` 等元件明細：
> ```bash
> curl -s http://localhost:8080/actuator/health -H "Authorization: Bearer <token>" | python3 -m json.tool
> ```

> 提醒：同一時間只用一個工具/終端操作本專案的 docker 與資料庫。多個程序並行下 `docker compose down -v`、清 schema 會反覆破壞容器與 Flyway 歷史，造成上述 A、B 類崩潰。
