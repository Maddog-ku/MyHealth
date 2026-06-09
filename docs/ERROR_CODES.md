# 錯誤代碼與解決方式

這份文件用來對照 MyHealth API 的錯誤代碼，並提供對應處理方式。適用對象包含：

- 使用者支援：判斷要提示使用者做什麼。
- 前端開發：依 `error` 分流 UI、重試、重新登入或顯示欄位錯誤。
- 後端 / 維運：定位基礎設施、資料庫、migration 或程式例外。

來源以目前後端實作為準：

- `backend/src/main/java/com/myhealth/common/ErrorCode.java`
- `backend/src/main/java/com/myhealth/common/GlobalExceptionHandler.java`
- `backend/src/main/java/com/myhealth/config/SecurityConfig.java`
- 各 service 中的 `ApiException` 拋出點

---

## 1. 錯誤回應格式

一般 controller/service 拋出的 4xx / 5xx 會回傳 `ApiErrorResponse`：

```json
{
  "timestamp": "2026-06-09T06:47:24.591Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "details": [
    { "field": "email", "code": "Email", "message": "must be a well-formed email address" }
  ]
}
```

| 欄位 | 說明 |
|---|---|
| `timestamp` | 後端產生錯誤的時間，UTC。 |
| `status` | HTTP 狀態碼。 |
| `error` | 穩定的機器可讀錯誤代碼，前端應優先依此處理。 |
| `message` | 人類可讀的簡短說明，不應包含密碼、token、照片內容等敏感資料。 |
| `path` | 出錯的請求路徑。 |
| `details[]` | 欄位級錯誤，通常只在 `VALIDATION_ERROR` 出現。 |

安全攔截器直接處理的未登入 / 權限錯誤格式較短，目前不包含 `timestamp`、`path`、`details`：

```json
{"status":401,"error":"TOKEN_EXPIRED","message":"access token expired"}
```

前端型別位於 `frontend/src/api/client.ts`：

- `ApiErrorBody`
- `ApiError`

---

## 2. 快速決策表

| `error` | HTTP | 先做什麼 |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | 讀 `details[]`，把欄位錯誤顯示在表單上。 |
| `BAD_REQUEST` | 400 | 讀 `message`，修正請求內容；不要自動重試。 |
| `UNAUTHORIZED` | 401 | 若不是 auth endpoint，前端可 refresh 一次；失敗則重新登入。 |
| `TOKEN_EXPIRED` | 401 | 用 refresh token 換新的 access token。 |
| `INVALID_REFRESH_TOKEN` | 401 | 清除本地 token，要求重新登入。 |
| `FORBIDDEN` | 403 | 使用者已登入但無權限；檢查角色或資源擁有權。 |
| `NOT_FOUND` | 404 | 確認 id、日期、資源是否屬於目前使用者；不要自動重試。 |
| `CONFLICT` | 409 | 提示資料衝突，例如 email 已註冊或已完成項目不可修改。 |
| `PAYLOAD_TOO_LARGE` | 413 | 壓縮或縮小上傳檔案。 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 改用支援的圖片格式或正確 `Content-Type`。 |
| `RATE_LIMITED` | 429 | 暫停送出，等待 rate-limit window 後再試。 |
| `SERVICE_UNAVAILABLE` | 503 | 檢查 Postgres / Redis / 依賴服務；可短暫重試。 |
| `INTERNAL_ERROR` | 500 | 查看後端 log stack trace，當成程式 bug 處理。 |

---

## 3. 錯誤代碼詳解

### `VALIDATION_ERROR`

HTTP：`400`

常見原因：

- JSON body 欄位不符合 DTO validation，例如 email 格式錯、密碼太短、數字超出範圍。
- 必填 query/form 參數缺漏，例如餐點 `slot` 或 `items` 未提供。
- `details[]` 會列出欄位、驗證代碼與訊息。

使用者解法：

- 依畫面提示補齊必填欄位。
- 修正格式，例如 email、日期、身高體重範圍、密碼規則。

前端處理：

- 用 `details[].field` 對應表單欄位。
- 多欄位錯誤應一次顯示，不要只顯示第一個。
- 不要自動重試，因為請求內容不改就會再次失敗。

後端 / 維運處理：

- 檢查 DTO annotation 是否符合 UI 規則。
- 若 UI 已合法但仍被擋，檢查前端送出的 payload 與 DTO 欄位名稱是否一致。

---

### `BAD_REQUEST`

HTTP：`400`

常見原因：

- JSON malformed 或 enum / 日期型別無法解析。
- 餐點確認的 `items` 不是合法 food item JSON。
- 餐點缺少圖片與文字描述。
- 餐點描述含 prompt injection 文字或非餐點內容。
- 統計日期區間不合法，例如 `from > to` 或超過 90 天。
- 運動週期課表套用日期 / 週期不合法。

使用者解法：

- 修正輸入內容後重新送出。
- 餐點描述只填食物、飲品與份量，例如「雞胸肉 150g、白飯一碗」。
- 日期區間縮到 90 天以內。

前端處理：

- 顯示 `message`。
- 如果是 enum / 日期 / JSON 組裝錯誤，通常是前端 bug，應回到 payload 產生處修正。
- 不要自動重試。

後端 / 維運處理：

- 查 service 的 `ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, ...)` 拋出點。
- 若錯誤來自 `HttpMessageNotReadableException`，優先檢查 request body 格式與 enum 值。

---

### `UNAUTHORIZED`

HTTP：`401`

常見原因：

- 未帶 `Authorization: Bearer <accessToken>`。
- access token 格式錯或簽章無效。
- 登入 email / 密碼錯誤。
- token 對應的使用者已不存在。

使用者解法：

- 重新登入。
- 若帳號被刪除或 email 不存在，重新註冊或使用正確帳號。

前端處理：

- 對非 `/auth/*` 請求，若本地有 refresh token，可嘗試 refresh 一次。
- refresh 失敗後呼叫 `clearAuth()` 並導回登入頁。
- 對 `/auth/login` 的 401 顯示「Email 或密碼錯誤」，不要顯示內部細節。

後端 / 維運處理：

- 檢查 `JWT_SECRET` 是否與簽發 token 時一致。
- prod profile 不可使用預設 `JWT_SECRET`，且長度至少 32 bytes。

---

### `TOKEN_EXPIRED`

HTTP：`401`

常見原因：

- access token 已超過 `app.jwt.access-ttl-min`，預設 15 分鐘。
- 此錯誤由 `JwtAuthenticationFilter` / `SecurityConfig` 的 authentication entry point 產生。

使用者解法：

- 通常不需要手動處理；前端應自動刷新 token。
- 若刷新失敗，重新登入。

前端處理：

- 呼叫 `POST /api/v1/auth/refresh`。
- 成功後重送原請求。
- 避免多請求同時 refresh；目前 `frontend/src/api/client.ts` 用 `refreshing` promise 合併刷新流程。

後端 / 維運處理：

- 若大量使用者頻繁遇到此錯誤，檢查 `JWT_ACCESS_TTL_MIN` 是否過短，或前端是否沒有保存新 token。

---

### `INVALID_REFRESH_TOKEN`

HTTP：`401`

常見原因：

- refresh token 不存在、格式錯、過期、已撤銷。
- refresh token 已被使用後又重放，觸發 reuse detection。
- 使用者改密碼、登出其他裝置或刪除帳號後，舊 token 失效。

使用者解法：

- 重新登入。
- 若頻繁發生，從帳戶中心登出其他裝置並更新密碼。

前端處理：

- 立即清除 access token 與 refresh token。
- 導回登入頁，不要重試 refresh。
- 可提示「登入已過期，請重新登入」。

後端 / 維運處理：

- reuse detection 會撤銷 token family，這是安全保護，不是一般 bug。
- 檢查 `refresh_tokens.token_hash` 唯一索引是否存在：

```bash
docker compose exec -T postgres psql -U myhealth -d myhealth \
  -c "select indexname, indexdef from pg_indexes where tablename = 'refresh_tokens';"
```

---

### `FORBIDDEN`

HTTP：`403`

常見原因：

- 使用者已登入，但沒有權限存取該端點。
- 非 prod 才允許的 Swagger / OpenAPI 在 prod 被拒絕。
- 未來 admin endpoint 會要求特定角色。

使用者解法：

- 切換到有權限的帳號。
- 若認為應有權限，聯絡管理者。

前端處理：

- 顯示無權限訊息。
- 不要自動 refresh token，因為這不是 token 過期。

後端 / 維運處理：

- 檢查 Spring Security 規則與 service 層資源擁有權判斷。
- prod 環境 Swagger 被擋是預期行為。

---

### `NOT_FOUND`

HTTP：`404`

常見原因：

- 查不到 meal、favorite meal、workout plan、workout schedule、session 或圖片。
- id 存在但不屬於目前登入使用者。
- 圖片檔案已不存在。

使用者解法：

- 回列表重新整理。
- 確認沒有使用舊連結或已刪除資料。

前端處理：

- 不要自動重試。
- 若使用者在詳情頁，可導回列表並顯示「資料不存在或已被刪除」。

後端 / 維運處理：

- 確認 repository 查詢是否有帶 `userId`，避免跨使用者讀取。
- 圖片 404 需檢查 `UPLOAD_DIR` 與資料庫 `image_path` 是否一致。

---

### `CONFLICT`

HTTP：`409`

常見原因：

- 註冊 email 已存在。
- 違反資料庫唯一鍵或外鍵約束。
- 已完成的 workout 不可編輯。
- 並行請求導致資料狀態衝突。

使用者解法：

- 註冊時改用其他 email，或改成登入既有帳號。
- 對已完成的運動，先建立新紀錄或使用允許的操作。

前端處理：

- 顯示具體衝突訊息。
- 不要自動重試，除非該操作本身設計成可重試的 upsert。

後端 / 維運處理：

- 查 `DataIntegrityViolationException` 的 constraint name。
- 若是並行造成，確認 service 是否需要交易、鎖或唯一索引保護。

---

### `PAYLOAD_TOO_LARGE`

HTTP：`413`

常見原因：

- 上傳檔案超過 `spring.servlet.multipart.max-file-size`，預設 10MB。
- multipart request 超過 `max-request-size`，預設 12MB。
- 圖片尺寸超過 `FileStorageService` 的限制。

使用者解法：

- 壓縮圖片。
- 改用較小解析度的照片。

前端處理：

- 上傳前檢查檔案大小。
- 可在 client 端壓縮圖片或限制相機輸出解析度。
- 不要自動重試同一個檔案。

後端 / 維運處理：

- 若業務需要更大檔案，調整 `application.yml` 的 multipart 設定與 `FileStorageService` 限制。
- 同時確認磁碟容量與反向代理 body size limit。

---

### `UNSUPPORTED_MEDIA_TYPE`

HTTP：`415`

常見原因：

- 上傳檔案 MIME type 不是支援的圖片格式。
- 檔案副檔名看似圖片，但內容無法被辨識為有效圖片。
- `Content-Type` 設錯，例如 JSON endpoint 送成錯誤格式。

使用者解法：

- 改用常見圖片格式，例如 JPEG、PNG、WebP。
- 重新拍照或重新選擇檔案。

前端處理：

- 檢查 `file.type`，先擋明顯不支援的檔案。
- multipart upload 交給 browser 設定 boundary，不要手動固定 `Content-Type`。

後端 / 維運處理：

- 檢查 `FileStorageService` 的 MIME 白名單與圖片解析邏輯。
- 若要新增格式，需同時更新後端驗證與前端提示。

---

### `RATE_LIMITED`

HTTP：`429`

常見原因：

- 短時間內多次註冊、登入、refresh 或呼叫 AI 端點。
- 目前限流設定在 `app.rate-limit.*`：
  - `register-limit`
  - `login-limit`
  - `refresh-limit`
  - `ai-limit`
  - `window`，預設 `1m`

使用者解法：

- 等一段時間再試。
- 避免連續快速點擊提交按鈕。

前端處理：

- 按鈕送出後應 disabled，避免連點。
- 顯示「請稍後再試」。
- 目前後端沒有回 `Retry-After` header，因此 client 可依 `RATE_LIMIT_WINDOW` 的預設 1 分鐘做保守退避。

後端 / 維運處理：

- 本機開發可用環境變數調高限制，例如 `RATE_LIMIT_LOGIN_LIMIT=100`。
- 若使用 Redis backend，確認 `REDIS_URL` 可連。
- 若 Redis 短暫不可用且希望不中斷服務，可設定 `RATE_LIMIT_REDIS_FAIL_OPEN=true`，代價是限流可能暫時失效。

---

### `AI_UNAVAILABLE`

HTTP：目前實作中通常不直接回此代碼。

目前設計：

- enum 中保留 `AI_UNAVAILABLE`。
- 多數 AI 失敗路徑會回退到內建模板或空分析，而不是中斷流程。
- 例如餐點 AI 失敗時，會回可手動編輯的空分析提示。

使用者解法：

- 若 AI 無法使用，仍可手動建立餐點或運動紀錄。
- 若需要本機 AI，確認 Ollama 已安裝並已 pull 模型。

前端處理：

- 不要假設所有 AI 失敗都會是 503。
- 優先依 API 回應中的 fallback 內容呈現可編輯狀態。

後端 / 維運處理：

- 檢查 `AI_PROVIDER`、`OLLAMA_BASE_URL`、`OLLAMA_TEXT_MODEL`、`OLLAMA_VISION_MODEL`。
- 用 `GET /api/v1/ai/status` 查看 AI 狀態。

---

### `SERVICE_UNAVAILABLE`

HTTP：`503`

常見原因：

- Postgres 暫時不可用。
- Hikari 無法取得資料庫連線。
- Redis rate-limit backend 不可用，且 `RATE_LIMIT_REDIS_FAIL_OPEN=false`。
- 依賴服務剛啟動，仍在 warm up。

使用者解法：

- 稍後重試。
- 若是本機開發，確認 Docker Desktop 與容器有啟動。

前端處理：

- 可短暫重試 1 到 2 次，需加延遲。
- 顯示「服務暫時不可用，請稍後再試」。
- 不要清除登入狀態，因為這不代表 token 無效。

後端 / 維運處理：

```bash
docker compose ps
docker compose exec -T postgres pg_isready -U myhealth -d myhealth
docker compose exec -T redis redis-cli ping
curl -fsS http://localhost:8080/actuator/health
```

常見修復：

```bash
scripts/dev.sh --infra
```

若 Flyway / schema 問題造成服務無法啟動，見本文件第 5 節。

---

### `INTERNAL_ERROR`

HTTP：`500`

常見原因：

- 未被預期攔截的 RuntimeException。
- 檔案系統、JSON 欄位、資料映射或第三方整合發生程式錯誤。
- 新功能缺少錯誤分類，導致掉進 generic exception handler。

使用者解法：

- 稍後再試。
- 若持續發生，提供操作步驟、時間點與畫面截圖給開發者。

前端處理：

- 顯示一般伺服器錯誤訊息。
- 不要暴露 stack trace。
- 若是可重試的讀取請求，可短暫重試；寫入請求避免盲目重試，以免重複建立資料。

後端 / 維運處理：

```bash
tail -n 200 .dev-logs/backend.log
grep -nE "ERROR|Exception|Caused by" .dev-logs/backend.log | tail -40
```

處理原則：

- 找 stack trace 最底層 `Caused by`。
- 若是可預期的用戶輸入，改成 `BAD_REQUEST` 或 `VALIDATION_ERROR`。
- 若是資源不存在，改成 `NOT_FOUND`。
- 若是依賴服務不可用，改成 `SERVICE_UNAVAILABLE`。

---

## 4. 常見情境對照

### 登入失敗

可能代碼：

- `UNAUTHORIZED`：email 或密碼錯。
- `VALIDATION_ERROR`：email 格式錯或密碼欄位空白。
- `RATE_LIMITED`：短時間登入太多次。
- `SERVICE_UNAVAILABLE`：資料庫或 Redis 限流後端不可用。

建議處理：

- 先顯示欄位錯誤。
- 登入 401 顯示「Email 或密碼錯誤」。
- 429 disabled 表單並提示稍後再試。
- 503 不清 token，只提示服務暫時不可用。

### Token 過期或失效

可能代碼：

- `TOKEN_EXPIRED`：access token 過期，可 refresh。
- `INVALID_REFRESH_TOKEN`：refresh token 失效或重放，必須重新登入。
- `UNAUTHORIZED`：token 格式錯、簽章錯或無 token。

建議處理：

- 對一般 API 的 401 嘗試 refresh 一次。
- refresh 失敗後清除 localStorage token。
- 看到 `INVALID_REFRESH_TOKEN` 不要再 refresh。

### 餐點上傳 / 確認失敗

可能代碼：

- `BAD_REQUEST`：缺圖片和描述、描述不是餐點、`items` JSON 不合法、食物值超出範圍。
- `VALIDATION_ERROR`：缺 `slot` / `items` 等必填參數。
- `PAYLOAD_TOO_LARGE`：圖片太大。
- `UNSUPPORTED_MEDIA_TYPE`：不是有效圖片格式。
- `RATE_LIMITED`：AI / 餐點建立頻率過高。

建議處理：

- 前端先限制檔案大小與 MIME。
- `confirm` 的 `items` 必須是最多 5 個 food item 的 JSON array。
- 每個 food item 需符合後端 `AiProvider.FoodItem` 範圍限制。

### 運動計畫操作失敗

可能代碼：

- `BAD_REQUEST`：取消的 exercise index 不存在、週期課表參數不合法。
- `CONFLICT`：已完成 workout 不可編輯。
- `NOT_FOUND`：workout plan 或 schedule 不存在。

建議處理：

- 列表頁重新整理後再操作。
- 已完成項目使用另外的建立 / 複製流程，不要編輯原紀錄。

### 系統看起來當掉

可能代碼或狀態：

- HTTP `0`：前端完全連不到後端，axios 會轉成 `ApiError.status = 0`。
- `SERVICE_UNAVAILABLE`：後端活著，但 DB / Redis 等依賴不可用。
- `INTERNAL_ERROR`：後端程式拋出未分類例外。

建議處理：

```bash
docker compose ps
curl -fsS http://localhost:8080/actuator/health
tail -n 200 .dev-logs/backend.log
```

---

## 5. 後端啟動與基礎設施排查

### 後端啟動就退出

先看 log：

```bash
tail -n 200 .dev-logs/backend.log
grep -iE "Flyway|Migration|Validate failed|APPLICATION FAILED|already exists" .dev-logs/backend.log | tail -40
```

| log 關鍵字 | 可能根因 | 解法 |
|---|---|---|
| `Connection refused` / `Unable to obtain connection from database` | Postgres 沒啟動或 host/port 錯。 | `scripts/dev.sh --infra`，確認 `docker compose ps`。 |
| `Migration ... failed` / `relation ... already exists` | Flyway history 與實際 schema 不一致。 | 本機可 `scripts/dev.sh --reset` 重建 DB。 |
| `Validate failed: checksum mismatch` | 已套用的 migration 被改過。 | 不要修改已套用 migration；本機可 reset，正式環境需新增修正 migration。 |
| `Schema-validation: missing table/column` | Entity 與 DB schema 不一致。 | 補新的 `V{n}__*.sql` migration。 |
| `JWT_SECRET must be set in prod profile` | prod 使用不安全 JWT secret。 | 設定長度足夠的 `JWT_SECRET`。 |

### 後端啟動了但 API 回 503

檢查：

```bash
docker compose ps
docker compose exec -T postgres pg_isready -U myhealth -d myhealth
docker compose exec -T redis redis-cli ping
```

常見解法：

```bash
scripts/dev.sh --infra
```

若使用 Redis rate limiter：

```bash
RATE_LIMIT_BACKEND=redis scripts/dev.sh --restart
```

Redis 暫時不可用但希望服務不中斷時：

```bash
RATE_LIMIT_REDIS_FAIL_OPEN=true RATE_LIMIT_BACKEND=redis scripts/dev.sh --restart
```

---

## 6. 環境快速健檢

```bash
docker compose ps
docker compose exec -T postgres pg_isready -U myhealth -d myhealth
docker compose exec -T redis redis-cli ping
curl -fsS http://localhost:8080/actuator/health
```

真實 API smoke test：

```bash
scripts/smoke-api.sh
```

如果後端不在 8080：

```bash
API_BASE_URL=http://127.0.0.1:18080/api/v1 scripts/smoke-api.sh
```

---

## 7. 維護規則

新增或修改錯誤代碼時，需同步檢查：

1. `ErrorCode.java`
2. `GlobalExceptionHandler.java`
3. `SecurityConfig.java` 的 authentication / access denied handler
4. 前端 `ApiError` 分流邏輯
5. `docs/API.md`
6. 本文件

新增可預期錯誤時，優先使用既有代碼；只有當前端需要新的穩定分流行為時才新增 enum。
