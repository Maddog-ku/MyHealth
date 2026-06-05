# MyHealth REST API 文件

版本：`v1`
Base URL（開發環境）：`http://localhost:8080/api/v1`
Swagger UI（非 prod）：`http://localhost:8080/swagger-ui.html`
OpenAPI JSON（非 prod）：`http://localhost:8080/v3/api-docs`

---

## 目錄

1. [通用規範](#1-通用規範)
2. [認證與授權](#2-認證與授權)
3. [錯誤格式](#3-錯誤格式)
4. [Auth 端點](#4-auth-端點)
5. [Me（個人資料）](#5-me個人資料)
6. [Workouts（運動菜單）](#6-workouts運動菜單)
7. [Meals（飲食紀錄）](#7-meals飲食紀錄)
8. [Stats（統計）](#8-stats統計)
9. [AI Gateway](#9-ai-gateway)
10. [資料型別參考](#10-資料型別參考)
11. [HTTP 狀態碼](#11-http-狀態碼)
12. [速率限制與分頁](#12-速率限制與分頁)

---

## 1. 通用規範

### 1.1 通訊格式
- 所有請求 / 回應皆為 **JSON（UTF-8）**；檔案上傳使用 `multipart/form-data`。
- 請求需設定 `Content-Type: application/json`（除上傳檔案外）。
- 日期格式：**ISO-8601**
  - 日期：`YYYY-MM-DD`（如 `2026-05-25`）
  - 日期時間：`YYYY-MM-DDTHH:mm:ssZ`（UTC）
- 數值：熱量為整數（`kcal`），三大營養素為 `NUMERIC(6,2)`（公克）。
- 所有列表回應使用統一信封：

```json
{
  "data": [ ... ],
  "page": 0,
  "size": 20,
  "total": 137
}
```

單筆資源直接回 JSON 物件，不再包一層 `data`。

### 1.2 命名規則
- URL：小寫複數名詞 + kebab-case，例：`/workout-plans`、`/daily-stats`
- JSON 欄位：`camelCase`
- 查詢參數：`camelCase`

### 1.3 版本控管
- 路徑版本：`/api/v1/...`
- Breaking change 將升至 `/api/v2`，舊版本至少保留 6 個月。

### 1.4 多語系
- 請求可帶 `Accept-Language: zh-TW`（或 `en`）讓後端回傳對應語系的錯誤訊息與 AI 輸出。
- 若 header 未提供，採用使用者 `profile.language`；若未登入則預設 `zh-TW`。

### 1.5 CORS
- 預設允許 `http://localhost:5173`（前端 dev server）。
- 可透過環境變數 `CORS_ALLOWED_ORIGINS` 設定多個 origin（逗號分隔）。

---

## 2. 認證與授權

### 2.1 認證機制
- **JWT Bearer Token**（無狀態）
  - `accessToken`：短期（預設 15 分鐘），帶於每個請求的 `Authorization` header。
  - `refreshToken`：長期（預設 30 天），儲存於資料庫，可逐一撤銷。
- 請求 header：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### 2.2 授權規則
- **公開端點**：`POST /auth/register`、`POST /auth/login`、`POST /auth/refresh`、`GET /ai/status`（部份）。
- **需登入端點**：其餘所有端點。
- **資料隔離**：所有 `/workouts`、`/meals`、`/stats`、`/me` 端點僅可存取當前登入者自身資料；後端在 Service 層注入 `userId` 過濾。
- **管理者端點（預留）**：路徑前綴 `/admin/*`，須 `role = ADMIN`。

### 2.3 Token 失效
- `accessToken` 過期 → 回 `401 TOKEN_EXPIRED`，前端應自動呼叫 `/auth/refresh`。
- `refreshToken` 失效或被撤銷 → 回 `401 INVALID_REFRESH_TOKEN`，需重新登入。

---

## 3. 錯誤格式

所有非 2xx 回應採用統一錯誤結構：

```json
{
  "timestamp": "2026-05-25T12:34:56Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "email must be valid",
  "path": "/api/v1/auth/register",
  "details": [
    { "field": "email", "code": "Email", "message": "must be a well-formed email address" }
  ]
}
```

| `error` 代碼 | HTTP | 說明 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 請求參數驗證失敗（`details` 列出欄位） |
| `BAD_REQUEST` | 400 | 一般請求錯誤 |
| `UNAUTHORIZED` | 401 | 未提供或無效 token |
| `TOKEN_EXPIRED` | 401 | accessToken 已過期 |
| `INVALID_REFRESH_TOKEN` | 401 | refreshToken 失效 |
| `FORBIDDEN` | 403 | 無權限存取此資源 |
| `NOT_FOUND` | 404 | 資源不存在 |
| `CONFLICT` | 409 | 衝突（如 email 已註冊） |
| `PAYLOAD_TOO_LARGE` | 413 | 檔案超過上限 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type 不支援 |
| `RATE_LIMITED` | 429 | 請求過於頻繁 |
| `AI_UNAVAILABLE` | 503 | AI Provider 不可用或載入中 |
| `INTERNAL_ERROR` | 500 | 伺服器錯誤 |

---

## 4. Auth 端點

### 4.1 註冊

`POST /auth/register`

**Request**
```json
{
  "email": "user@example.com",
  "password": "SecurePass1",
  "name": "Alice",
  "gender": "female",
  "heightCm": 165.0,
  "weightKg": 55.5,

  "age": 28,
  "bodyFatPct": 23.5,
  "muscleMassKg": 38.2,
  "bmrKcal": 1320,
  "waistCm": 68.0,
  "bodyWaterPct": 52.0,
  "goal": "fat_loss",
  "equipment": ["dumbbell", "yoga_mat"],
  "experience": "intermediate"
}
```

**驗證**

必填欄位：
| 欄位 | 型別 | 限制 |
|---|---|---|
| `email` | string | 有效 Email、unique，不限制信箱供應商 |
| `password` | string | 至少 8 字，只允許半形英文與數字，且至少包含 1 個大寫英文與 1 個小寫英文 |
| `name` | string | 1 ~ 100 字 |
| `gender` | string | `male` \| `female` \| `other` |
| `heightCm` | number | 50 ~ 250 |
| `weightKg` | number | 20 ~ 300 |

選填欄位（身體檢測 / 健身設定）：
| 欄位 | 型別 | 限制 |
|---|---|---|
| `age` | int | 1 ~ 120 |
| `bodyFatPct` | number | 1 ~ 70（%） |
| `muscleMassKg` | number | 1 ~ 150 |
| `bmrKcal` | int | 500 ~ 5000 |
| `waistCm` | number | 30 ~ 200 |
| `bodyWaterPct` | number | 1 ~ 90（%） |
| `goal` | string | `muscle_gain` \| `fat_loss` \| `maintain` |
| `equipment` | string[] | 元素 ≤ 30 字 |
| `experience` | string | `beginner` \| `intermediate` \| `advanced` |
| `theme` | string | `light` \| `dark` \| `system`（預設 `system`） |
| `language` | string | `zh-TW` \| `en`（預設 `zh-TW`） |

**Response 201**
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "Alice",
  "role": "USER",
  "profile": {
    "gender": "female",
    "heightCm": 165.0,
    "weightKg": 55.5,
    "age": 28,
    "bodyFatPct": 23.5,
    "muscleMassKg": 38.2,
    "bmrKcal": 1320,
    "waistCm": 68.0,
    "bodyWaterPct": 52.0,
    "goal": "fat_loss",
    "equipment": ["dumbbell", "yoga_mat"],
    "experience": "intermediate"
  },
  "createdAt": "2026-05-25T12:00:00Z"
}
```

> 註冊成功後，後端會同時建立 `profiles` 紀錄，並將當下的身體檢測數據寫入 `body_measurements` 作為趨勢起點。

**Errors**：`409 CONFLICT`（email 已存在）、`400 VALIDATION_ERROR`（缺必填或數值超出範圍）

---

### 4.2 登入

`POST /auth/login`

**Request**
```json
{ "email": "user@example.com", "password": "SecurePass1" }
```

**Response 200**
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "9c2f1a...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1, "email": "user@example.com", "name": "Alice", "role": "USER"
  }
}
```

**Errors**：`401 UNAUTHORIZED`（帳密錯誤）、`429 RATE_LIMITED`（登入嘗試過於頻繁）

---

### 4.3 刷新 Access Token

`POST /auth/refresh`

**Request**
```json
{ "refreshToken": "9c2f1a..." }
```

**Response 200**：同 4.2（新 `accessToken`，`refreshToken` 不變或輪替）

**Errors**：`401 INVALID_REFRESH_TOKEN`（refreshToken 失效）、`429 RATE_LIMITED`（刷新請求過於頻繁）

---

### 4.4 登出

`POST /auth/logout`  *(需登入)*

**Request**
```json
{ "refreshToken": "9c2f1a..." }
```

撤銷指定 refreshToken。

**Response 204**（無內容）

---

### 4.5 密碼重設（規劃中）

| Method | Path | 說明 |
|---|---|---|
| POST | `/auth/password/forgot` | 寄送重設信 (`{ email }`) |
| POST | `/auth/password/reset`  | 以 token 重設 (`{ token, newPassword }`) |

---

## 5. Me（個人資料）

### 5.1 取得當前使用者

`GET /me`

**Response 200**
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "Alice",
  "role": "USER",
  "profile": {
    "gender": "female",
    "heightCm": 165.0,
    "weightKg": 55.5,
    "age": 28,
    "bodyFatPct": 23.5,
    "muscleMassKg": 38.2,
    "bmrKcal": 1320,
    "waistCm": 68.0,
    "bodyWaterPct": 52.0,
    "goal": "fat_loss",
    "equipment": ["dumbbell", "yoga_mat"],
    "experience": "intermediate"
  },
  "createdAt": "2026-05-25T12:00:00Z"
}
```

### 5.2 更新個人檔案

`PUT /me/profile`

**Request**（必填欄位仍須提供，選填欄位可省略或設為 `null`）
```json
{
  "gender": "female",
  "heightCm": 165.0,
  "weightKg": 55.0,
  "age": 28,
  "bodyFatPct": 22.8,
  "muscleMassKg": 38.5,
  "bmrKcal": 1330,
  "waistCm": 67.5,
  "bodyWaterPct": 52.4,
  "goal": "fat_loss",
  "equipment": ["dumbbell", "yoga_mat"],
  "experience": "intermediate"
}
```

| 欄位 | 必填 | 型別 | 限制 |
|---|---|---|---|
| `gender` | ✓ | string | `male` \| `female` \| `other` |
| `heightCm` | ✓ | number | 50 ~ 250 |
| `weightKg` | ✓ | number | 20 ~ 300 |
| `age` | ✗ | int | 1 ~ 120 |
| `bodyFatPct` | ✗ | number | 1 ~ 70（%） |
| `muscleMassKg` | ✗ | number | 1 ~ 150 |
| `bmrKcal` | ✗ | int | 500 ~ 5000 |
| `waistCm` | ✗ | number | 30 ~ 200 |
| `bodyWaterPct` | ✗ | number | 1 ~ 90（%） |
| `goal` | ✗ | string | `muscle_gain` \| `fat_loss` \| `maintain` |
| `equipment` | ✗ | string[] | 元素 ≤ 30 字 |
| `experience` | ✗ | string | `beginner` \| `intermediate` \| `advanced` |
| `assistantAvatar` | ✗ | string | `male` \| `female`；省略時依 `gender` 自動推導 |
| `theme` | ✗ | string | `light` \| `dark` \| `system` |
| `language` | ✗ | string | `zh-TW` \| `en` |

**Response 200**：回傳更新後的 `profile` 物件（`assistantAvatar` 一律回傳已解析的 `male`/`female`）。

> 每次更新身體數據（體重、體脂率、肌肉量、BMR、腰圍、體水分率）皆會在 `body_measurements` 新增一筆歷史紀錄，供趨勢圖使用。

### 5.3 刪除帳號

`DELETE /me`

刪除帳號並 **連動清除所有個人資料**（profiles、workouts、meals、stats、refresh_tokens）。

**Response 204**

---

## 6. Workouts（運動菜單）

### 6.1 AI 產生菜單

`POST /workouts/generate`

**Request**
```json
{
  "date": "2026-05-25",
  "category": "abs",
  "durationMin": 30,
  "intensity": "medium",
  "equipmentOverride": ["dumbbell"]
}
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `date` | ✓ | 目標日期 |
| `category` | ✓ | `abs` \| `waist` \| `legs` \| `chest` \| `back` \| `glutes` \| `arms` \| `full_body` \| `cardio` |
| `durationMin` | ✗ | 預期訓練長度（10–180），預設 30 |
| `intensity` | ✗ | `low` \| `medium` \| `high`，預設 `medium` |
| `equipmentOverride` | ✗ | 覆蓋個人檔案的可用器材 |

**Response 201**
```json
{
  "id": 42,
  "date": "2026-05-25",
  "category": "abs",
  "items": [
    { "name": "捲腹", "sets": 4, "reps": "15", "restSec": 45, "durationSec": 45, "kcal": 30, "note": "下背貼地", "alt": ["反向捲腹"] }
  ],
  "totalKcal": 220,
  "burnedKcal": null,
  "done": false,
  "createdAt": "2026-05-25T12:30:00Z"
}
```

> `durationSec` 是 AI 估算的「單組工作秒數」，供前端計時引導使用（舊菜單可能為 0，前端會依 reps 推算）。`burnedKcal` 在完成前為 `null`。

**Errors**：`400 BAD_REQUEST`（不支援的 category/intensity）、`400 VALIDATION_ERROR`（日期缺漏或時長超出範圍）、`429 RATE_LIMITED`（AI 產生請求過於頻繁）

---

### 6.2 取得當日菜單

`GET /workouts?date=2026-05-25`

**Response 200**：`WorkoutPlan[]`（信封格式）

---

### 6.3 取得單筆

`GET /workouts/{id}`

---

### 6.4 完成訓練

`POST /workouts/{id}/complete`

使用者在前端的計時引導中逐組完成動作。只有「做滿設定秒數」的動作才計入消耗，前端把已完成動作的 `kcal` 加總成 `actualKcal` 送出。

**Request**（可選）
```json
{ "actualKcal": 230, "note": "完成全部組數" }
```

`actualKcal` 會被夾在 `[0, totalKcal]`，避免竄改後灌水超過菜單規劃量；省略 body 時視為完成整份菜單（`burnedKcal = totalKcal`）。當日統計的 `burnKcal` 以 `burnedKcal` 為準（舊資料 `null` 時回退 `totalKcal`）。

**Response 200**：更新後的 `WorkoutPlan`（`done: true`、`burnedKcal` 已填）

---

### 6.5 取消動作

`POST /workouts/{id}/items/remove`

從菜單中取消（移除）指定的動作，可單個或批次。送出要移除的動作索引（對應目前 `items` 陣列順序），後端移除後重算 `totalKcal`。

**Request**
```json
{ "indices": [1, 2] }
```

**Response 200**：更新後的 `WorkoutPlan`（已移除指定動作）。若移除後菜單已無任何動作，後端會直接刪除整份菜單（避免留下空卡片），回傳的菜單 `items` 為空。

**Errors**：`400 VALIDATION_ERROR`（`indices` 為空或超出範圍）、`400 BAD_REQUEST`（沒有任何索引命中）、`409 CONFLICT`（已完成的菜單不可編輯）、`404 NOT_FOUND`

---

### 6.6 刪除菜單

`DELETE /workouts/{id}` → 204

---

## 7. Meals（飲食紀錄）

### 7.1 新增餐點（含 AI 估算）

`POST /meals`
Content-Type：`multipart/form-data`

| Part | 型別 | 必填 | 說明 |
|---|---|---|---|
| `image` | file (jpg/png/webp, ≤ 10 MB) | ✗ | 餐點照片；會送入本地 vision model 分析，並保留供前端顯示 |
| `description` | string | ✗ | 文字描述 |
| `slot` | string | ✓ | `breakfast` \| `lunch` \| `dinner` \| `snack` |
| `date` | string (YYYY-MM-DD) | ✗ | 預設今日 |

> `image` 與 `description` 至少擇一。有圖片時，後端會把圖片 bytes 編為 base64，透過 Ollama `images` payload 傳給 vision model；若 Ollama 不可用、模型不支援圖片、JSON 解析失敗或回傳資料不可信，後端不會硬猜熱量，會回傳空項目與手動修正提示。

**Response 201**
```json
{
  "id": 88,
  "date": "2026-05-25",
  "slot": "lunch",
  "description": "雞胸肉沙拉 + 半碗糙米飯",
  "imageUrl": "/api/v1/meals/88/image",
  "items": [
    { "name": "雞胸肉", "grams": 150, "kcal": 248, "protein": 46.5, "fat": 5.4, "carb": 0, "confidence": 0.92 },
    { "name": "糙米飯", "grams": 100, "kcal": 112, "protein": 2.6, "fat": 0.9, "carb": 23.5, "confidence": 0.88 }
  ],
  "totalKcal": 360,
  "totalProtein": 49.1,
  "totalFat": 6.3,
  "totalCarb": 23.5,
  "aiSuggestion": "蛋白質充足，建議補充綠色蔬菜增加纖維。",
  "createdAt": "2026-05-25T12:45:00Z"
}
```

**Errors**：`400 BAD_REQUEST`（未提供圖片或描述）、`413 PAYLOAD_TOO_LARGE`、`415 UNSUPPORTED_MEDIA_TYPE`、`429 RATE_LIMITED`（AI 估算請求過於頻繁）

---

### 7.2 取得當日飲食

`GET /meals?date=2026-05-25`

**Response 200**：`Meal[]`

---

### 7.3 取得單筆

`GET /meals/{id}`

### 7.4 更新餐點（手動修正 AI 結果）

`PUT /meals/{id}`

**Request**
```json
{
  "items": [
    { "name": "雞胸肉", "grams": 180, "kcal": 297, "protein": 55.8, "fat": 6.5, "carb": 0 }
  ],
  "aiSuggestion": null
}
```
後端會依 `items` 重新計算 `total*` 欄位。

### 7.5 刪除餐點

`DELETE /meals/{id}` → 204

---

## 8. Stats（統計）

### 8.1 當日熱量收支

`GET /stats/daily?date=2026-05-25`

**Response 200**
```json
{
  "date": "2026-05-25",
  "intakeKcal": 1850,
  "burnKcal": 420,
  "netKcal": 1430,
  "protein": 110.5,
  "fat": 55.2,
  "carb": 180.0,
  "weightKg": 55.4,
  "goalKcal": 1700,
  "workoutsDone": 1,
  "workoutsPlanned": 1
}
```

### 8.2 區間統計

`GET /stats/range?from=2026-05-01&to=2026-05-25`

限制：`to - from ≤ 90` 天。

`weightKg`、`bodyFatPct`、`muscleMassKg`、`waistCm`、`bodyWaterPct` 皆使用 `body_measurements` 歷史紀錄：每一天取當日結束前最近一次量測值（各欄位獨立 carry-forward），沒有歷史量測時回退到目前 profile 對應值；仍無資料則為 `null`。

**Response 200**
```json
{
  "from": "2026-05-01",
  "to": "2026-05-25",
  "series": [
    {
      "date": "2026-05-01",
      "intakeKcal": 1900,
      "burnKcal": 350,
      "weightKg": 56.0,
      "bodyFatPct": 22.5,
      "muscleMassKg": 38.2,
      "waistCm": 70.0,
      "bodyWaterPct": 55.0
    },
    ...
  ]
}
```

---

## 9. AI Gateway

### 9.1 取得狀態

`GET /ai/status`  *(公開)*

**Response 200**
```json
{
  "provider": "local",
  "textModel": "qwen2.5:7b",
  "visionModel": "qwen2-vl:7b",
  "loaded": true,
  "lastUsedAt": "2026-05-25T12:44:10Z",
  "idleTimeoutSec": 300
}
```

### 9.2 手動釋放本地模型

`POST /ai/unload`  *(需登入；admin 或本機請求)*

**Response 200**
```json
{ "unloaded": true }
```

> 註：呼叫後下一次 AI 請求會自動 lazy load，預期延遲 1–10 秒。

### 9.3 AI 小幫手對話

對話以本機模型產生，並帶入使用者個人資料與當日數據作為背景；歷史會持久化（`DELETE /me` 連動刪除）。

> 主題限制：助手僅回答「運動／健身」與「飲食／營養」相關問題；其餘主題會以固定婉拒語回覆並把話題拉回（由 system prompt 強制，含防越獄指示）。
>
> 降低幻覺：背景資料每個欄位都會明標數值或「未提供」，並帶入今日實際餐點／運動紀錄；prompt 規定只能引用區塊內出現的數字、不得虛構使用者紀錄或精確營養數據，熱量改用區間並導向「飲食追蹤」做估算。聊天解碼參數也較保守（temperature 0.2、top_p 0.9、repeat_penalty 1.1）。

**取得歷史** — `GET /ai/chat/history`  *(需登入)*
```json
{
  "messages": [
    { "id": 12, "role": "user", "content": "今天適合做什麼運動？", "createdAt": "2026-06-03T14:02:11Z" },
    { "id": 13, "role": "assistant", "content": "依你今天的活動量，建議做 20 分鐘核心 💪", "createdAt": "2026-06-03T14:02:18Z" }
  ]
}
```

**送出訊息** — `POST /ai/chat`  *(需登入；受 AI 速率限制)*
```json
{ "message": "晚餐吃什麼比較好？" }
```
**Response 200**
```json
{
  "userMessage": { "id": 14, "role": "user", "content": "晚餐吃什麼比較好？", "createdAt": "2026-06-03T14:05:00Z" },
  "reply": { "id": 15, "role": "assistant", "content": "可以選高蛋白、低油的雞胸搭蔬菜 🥗", "createdAt": "2026-06-03T14:05:07Z" },
  "mealLogged": false,
  "workoutLogged": false,
  "weightLogged": false,
  "loggedDate": null
}
```
> `message` 必填，最長 1000 字。本機 AI 不可用時 `reply` 會回退為提示訊息（不報錯）。

**對話直接記錄餐點**：若訊息是在敘述「吃了什麼」（例如「我午餐吃了雞胸肉沙拉」），助手會自動判斷意圖、抽取時段與內容，走 `MealService` 的 AI 估算流程把該餐寫進「飲食追蹤」（等同 `POST /meals` 的文字記錄），`reply` 回傳含估算熱量／營養與建議的確認訊息，並設 `mealLogged=true`、`loggedDate` 為記錄日期（前端據此刷新餐點與當日統計）。純詢問（如「晚餐吃什麼比較好」）不會記錄。時段（早/午/晚/點心）優先取訊息中的詞，無法判斷時依當下時間推定。

**對話直接排運動菜單**：若訊息是要求安排訓練（例如「幫我排個練腿菜單」「我想練胸 30 分鐘」），助手會抽取分類／時長／強度，走 `WorkoutService.generate` 產生並存進「運動菜單」（等同 `POST /workouts/generate`），`reply` 摘要動作清單與預估消耗，並設 `workoutLogged=true`、`loggedDate` 為日期（前端據此刷新運動與當日統計）。單純問動作怎麼做（如「深蹲怎麼做」）不會產生菜單。分類無法判斷時退為全身（full_body）、強度退為 medium、時長夾在 10–180 分鐘。

**對話直接記錄體重**：若訊息是在回報目前體重（例如「我今天體重 68.5 公斤」「幫我記體重 70」），助手會抽取公斤數，走 `UserService.logWeight` 更新個人資料體重並寫入一筆完整的體量快照（`note=chat_weight_log`，等同 `PUT /me/profile` 只改體重），`reply` 回傳確認訊息，並設 `weightLogged=true`、`loggedDate` 為日期（前端據此刷新個人資料與體重趨勢統計）。純詢問（如「我體重會不會太重」）不會記錄；體重須落在 20–400 kg 的合理範圍，否則視為誤判不記錄（避免把身高等數字誤當體重）。

**清除歷史** — `DELETE /ai/chat/history`  *(需登入)* → `204 No Content`

---

## 9b. 每週健康報告（Weekly Report）

把一整週（自然週，週一–週日）的飲食、運動與體重整合成一份由本機 AI 產生的回顧。**關鍵數字每次即時計算**（永遠反映最新資料），只有 **AI 敘述**會依 `(使用者, 週起始日)` 持久化快取。

**取得報告** — `GET /reports/weekly?weekStart=YYYY-MM-DD`  *(需登入)*
- `weekStart` 選填，可為該週任一日（後端正規化到週一）；省略則取當週。
- 涵蓋區間為 `weekStart … min(weekStart+6, 今天)`。
- **不**觸發 AI；`narrative` 為已快取的敘述，未生成過則為 `null`。

**Response 200**
```json
{
  "weekStart": "2026-06-01",
  "weekEnd": "2026-06-07",
  "summary": {
    "totalIntakeKcal": 12600, "avgIntakeKcal": 1800, "totalBurnKcal": 1400,
    "netKcal": 11200, "goalKcal": 1700,
    "weightStart": 70.0, "weightEnd": 69.4, "weightDelta": -0.6,
    "workoutsDone": 3, "mealsLogged": 14, "daysCovered": 7
  },
  "narrative": "攝取：本週平均約 1800 大卡…\n運動：完成 3 次訓練…\n體重：約下降 0.6 公斤…\n本週建議：…",
  "generatedAt": "2026-06-05T10:00:00Z"
}
```

**產生／重新生成** — `POST /reports/weekly/generate`  *(需登入；受 AI 速率限制)*
```json
{ "weekStart": "2026-06-01" }
```
- body 與 `weekStart` 皆選填；省略則為當週。
- 走 `ReportService.generate`：以即時 summary 組出 grounded context（只引用真實數字、未知標「未提供」），呼叫本機模型產生敘述，upsert 進 `weekly_reports`，回傳與 GET 相同結構。
- 本機 AI 不可用時 `narrative` 回退為保守提示訊息（不報錯）。

---

## 10. 資料型別參考

### 10.1 `Exercise`
| 欄位 | 型別 | 說明 |
|---|---|---|
| `name` | string | 動作名稱 |
| `sets` | int | 組數 |
| `reps` | string | 次數（可為 `"30s"` 表時間） |
| `restSec` | int | 組間休息秒數 |
| `durationSec` | int | 單組工作秒數（計時引導用；10–600） |
| `kcal` | int | 預估消耗 |
| `note` | string | 提示要點 |
| `alt` | string[] | 替代動作 |

### 10.2 `FoodItem`
| 欄位 | 型別 | 說明 |
|---|---|---|
| `name` | string | 食物名稱 |
| `grams` | number | 重量 |
| `kcal` | int | 熱量 |
| `protein` | number | 蛋白質（g） |
| `fat` | number | 脂肪（g） |
| `carb` | number | 碳水（g） |
| `confidence` | number | 0–1，AI 信心度（手動輸入為 1.0） |

### 10.3 列舉

| 名稱 | 可選值 |
|---|---|
| `WorkoutCategory` | `abs` `waist` `legs` `chest` `back` `glutes` `arms` `full_body` `cardio` |
| `MealSlot` | `breakfast` `lunch` `dinner` `snack` |
| `Goal` | `muscle_gain` `fat_loss` `maintain` |
| `Experience` | `beginner` `intermediate` `advanced` |
| `Gender` | `male` `female` `other` |
| `Role` | `USER` `ADMIN` |

---

## 11. HTTP 狀態碼

| Code | 用途 |
|---|---|
| 200 | 成功（GET / PUT / POST 帶回應） |
| 201 | 建立成功 |
| 204 | 成功且無內容（DELETE / logout） |
| 400 | 請求格式或驗證錯誤 |
| 401 | 未登入或 token 失效 |
| 403 | 權限不足 |
| 404 | 資源不存在 |
| 409 | 衝突（unique 違反） |
| 413 | 檔案過大 |
| 415 | 不支援的 Content-Type |
| 429 | 速率超限 |
| 500 | 內部錯誤 |
| 503 | AI 暫不可用 |

---

## 12. 速率限制與分頁

### 12.1 速率限制
| 範疇 | 限制 | 狀態 |
|---|---|---|
| `POST /auth/register` | 5 req / min / IP | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |
| `POST /auth/login` | 10 req / min / IP + email | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |
| `POST /auth/refresh` | 30 req / min / IP | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |
| 登入後一般 API | 120 req / min / user | 📋 規劃中 |
| AI 端點（`/workouts/generate`、`POST /meals`） | 20 req / min / user | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |

超限回 `429 RATE_LIMITED`。設定 `RATE_LIMIT_BACKEND=redis` 時，限流狀態會存放在 Redis，適合多台 backend 共用；Redis 不可用時預設 `RATE_LIMIT_REDIS_FAIL_OPEN=false`，會回 `503`，正式環境建議維持 fail-closed。預設不信任 `X-Forwarded-For`；只有後端部署在可信任 reverse proxy 後方時才設定 `RATE_LIMIT_TRUST_FORWARDED_FOR=true`。目前限流不回傳 `X-RateLimit-*` header。

### 12.2 分頁
列表端點支援：
- `page`：頁碼（0 起算），預設 `0`
- `size`：每頁筆數，預設 `20`，上限 `100`
- `sort`：欄位排序，如 `?sort=date,desc`

範例：`GET /meals?from=2026-05-01&to=2026-05-25&page=0&size=50&sort=date,desc`

---

## 附錄 A：cURL 範例

```bash
# 註冊
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"SecurePass1","name":"Alice"}'

# 登入
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"SecurePass1"}' | jq -r .accessToken)

# 取得當前使用者
curl http://localhost:8080/api/v1/me -H "Authorization: Bearer $TOKEN"

# 產生運動菜單
curl -X POST http://localhost:8080/api/v1/workouts/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"date":"2026-05-25","category":"abs","durationMin":30}'

# 上傳餐點照片
curl -X POST http://localhost:8080/api/v1/meals \
  -H "Authorization: Bearer $TOKEN" \
  -F 'image=@lunch.jpg' \
  -F 'description=雞胸肉沙拉' \
  -F 'slot=lunch'
```
