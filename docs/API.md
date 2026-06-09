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
7b. [Foods（食物資料庫）](#7b-foods食物資料庫)
8. [Stats（統計）](#8-stats統計)
8b. [Health Plan（健康計畫）](#8b-health-plan健康計畫)
9. [Habits（每日習慣）](#9-habits每日習慣)
10. [AI Gateway](#10-ai-gateway)
10b. [System Diagnostics（系統狀態）](#10b-system-diagnostics系統狀態)
11. [資料型別參考](#11-資料型別參考)
12. [HTTP 狀態碼](#12-http-狀態碼)
13. [速率限制與分頁](#13-速率限制與分頁)

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

### 5.4 修改密碼

`POST /me/password`

```json
{ "currentPassword": "OldPass1", "newPassword": "NewPass2" }
```

驗證目前密碼後更新為新密碼。新密碼規則同註冊：8–128 字元、需同時包含大小寫英文字母（僅限英數字）。為了安全，變更成功後會**登出其他所有裝置**；若帶上 `X-Refresh-Token: <目前的 refresh token>` 標頭，會保留目前這台裝置的登入。

**Response 204**

**Errors**：`400 BAD_REQUEST`（目前密碼不正確，或新密碼與目前相同）、`400 VALIDATION_ERROR`（新密碼不符規則）

---

### 5.4b 匯出我的資料

`GET /me/export`

回傳目前使用者的**完整資料快照** JSON（個人檔案、體重量測、運動、餐點、體重目標、常用餐點、習慣紀錄、已解鎖成就、每週訓練目標）。運動／餐點的 items 以巢狀 JSON 嵌入（非字串）。回應帶 `Content-Disposition: attachment; filename="myhealth-export-<日期>.json"`，前端用帶 JWT 的請求取得後在瀏覽器端產生下載，資料全程不離開裝置。涵蓋範圍與 `DELETE /me` 會清除的資料一致。

**Response 200**
```json
{
  "exportedAt": "2026-06-07T08:00:00Z",
  "account": { "id": 1, "email": "...", "name": "...", "profile": { } },
  "bodyMeasurements": [ ],
  "workouts": [ { "date": "2026-06-01", "category": "legs", "items": [ ], "totalKcal": 200 } ],
  "meals": [ ],
  "weightGoal": null,
  "favoriteMeals": [ ],
  "habits": [ ],
  "achievements": [ { "code": "STREAK_7", "title": "一週不間斷", "unlockedAt": "2026-06-05T10:00:00Z" } ],
  "workoutGoal": { "targetSessionsPerWeek": 4, "createdAt": "2026-05-30T00:00:00Z" }
}
```

---

### 5.5 登入裝置（工作階段）管理

每一個未撤銷、未過期的 refresh token 代表一個登入中的裝置。Token 輪替（refresh）時，原本的登入時間與裝置資訊會帶到新 token，所以同一裝置在清單上維持一筆穩定的工作階段。

`GET /me/sessions`

選擇性帶上 `X-Refresh-Token: <目前的 refresh token>` 標頭；後端用它把「目前這台裝置」標記為 `current`（不放在 URL，避免寫進 log）。

**Response 200**
```json
{
  "sessions": [
    {
      "id": 10,
      "device": "Chrome · macOS",
      "createdAt": "2026-06-01T09:00:00Z",
      "lastActiveAt": "2026-06-07T08:30:00Z",
      "expiresAt": "2026-07-01T09:00:00Z",
      "current": true
    }
  ]
}
```

> `device` 由 User-Agent 解析出的「瀏覽器 · 系統」標籤；無法判斷時為「未知裝置」。`createdAt` 是首次登入時間，`lastActiveAt` 是該工作階段最近一次換發 token 的時間。

### 5.6 登出指定裝置

`DELETE /me/sessions/{id}` → 204

撤銷該工作階段的 refresh token（可撤銷目前裝置＝登出自己）。**Errors**：`404 NOT_FOUND`（工作階段不存在或不屬於你）

### 5.7 登出其他所有裝置

`POST /me/sessions/revoke-others`

```json
{ "refreshToken": "<目前的 refresh token>" }
```

撤銷除了目前這台以外的所有工作階段。若提供的 token 無法對應到有效工作階段，會撤銷全部（等同登出所有裝置）。**Response 200**：更新後的工作階段清單（同 5.4）。

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

### 6.7 訓練量分析

`GET /workouts/volume?weeks=4`

統計最近 N 週「已完成」的訓練：總次數、總組數、總消耗、活躍天數、平均每週次數，以及各部位頻率與每週趨勢。唯讀，從 `WorkoutPlan` 即時計算。

| 參數 | 必填 | 說明 |
|---|---|---|
| `weeks` | ✗ | 往前統計的週數（1–12，超出範圍自動夾住），預設 4 |

範圍為「本週週一往前推 `weeks-1` 週」至今天；`series` 一定包含連續 `weeks` 個以週一對齊的桶（沒有訓練的週填 0），方便畫趨勢圖。各部位（`byCategory`）依次數由多到少排序。`neglectedCategories` 列出在此範圍內**完全沒有專項訓練**的主要肌群（`legs`、`chest`、`back`、`arms`、`abs`、`glutes`；`cardio`/`full_body` 不列入），供前端提示訓練平衡。

**Response 200**
```json
{
  "from": "2026-05-18",
  "to": "2026-06-07",
  "weeks": 4,
  "totalSessions": 9,
  "totalSets": 96,
  "totalKcal": 1820,
  "activeDays": 7,
  "avgSessionsPerWeek": 2.3,
  "byCategory": [
    { "category": "legs", "sessions": 4, "sets": 48, "kcal": 900 }
  ],
  "series": [
    { "weekStart": "2026-05-18", "sessions": 2, "sets": 22, "kcal": 420 }
  ],
  "neglectedCategories": ["back", "arms", "abs", "glutes"]
}
```

> `kcal` 取每筆已完成訓練的實際消耗（`burnedKcal`，舊資料回退 `totalKcal`）；`activeDays` 為有完成訓練的不重複天數。

---

### 6.8 每週訓練目標

使用者每週想完成的訓練次數（每人一個）；`progress` 對照「本週（週一至今天）已完成的訓練數」即時計算，未設定時為 `null`。

`GET /workout-goal`

**Response 200**
```json
{
  "progress": {
    "targetSessionsPerWeek": 4,
    "completedThisWeek": 2,
    "remaining": 2,
    "progressPct": 50,
    "weekStart": "2026-06-01",
    "achieved": false,
    "createdAt": "2026-06-01T00:00:00Z"
  }
}
```

`PUT /workout-goal`
```json
{ "targetSessionsPerWeek": 4 }
```
建立或更新目標（`targetSessionsPerWeek` 介於 1–14），回傳含最新 `progress` 的同上格式。**Errors**：`400 VALIDATION_ERROR`（缺漏或超出範圍）

`DELETE /workout-goal` → 204

---

## 6b. Workout Schedules（週期課表規劃）

讓 AI 依使用者目標排出「一週訓練分配（split）」：7 天每天為休息或某個分類，這個一週模板可重複數週（最多 4 週，約一個月）。模板本身不含詳細動作；要訓練時把某一天「套用」到一個實際日期，會走 6.1 的 AI 產生流程生出當天的詳細菜單。

`ScheduleDay` 結構：
| 欄位 | 說明 |
|---|---|
| `weekday` | ISO 星期：1 = 週一 … 7 = 週日 |
| `rest` | 是否為休息日 |
| `category` | 分類代碼（同 6.1）；休息日為 `null` |
| `durationMin` | 建議時長（分鐘）；休息日為 0 |
| `focus` | 一句訓練重點 |

### 6b.1 AI 規劃週期課表

`POST /workout-schedules/generate`

**Request**
```json
{ "startDate": "2026-06-03", "daysPerWeek": 3, "weeks": 4, "intensity": "medium" }
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `startDate` | ✓ | 起始日；後端自動往前對齊到當週週一 |
| `daysPerWeek` | ✓ | 每週訓練天數（2–6） |
| `weeks` | ✓ | 模板重複週數（1–4） |
| `intensity` | ✗ | `low` \| `medium` \| `high`，預設 `medium`；套用某天時沿用此強度 |

目標（`goal`）與訓練經驗（`experience`：初學者／中階／進階）取自個人檔案，不需傳入；兩者都會帶進 AI 規劃 prompt，讓課表貼合使用者的目標與程度。AI 無法使用時回退到內建的分部位模板（仍尊重 `daysPerWeek`，減脂目標會多排有氧）。

**Response 201**
```json
{
  "id": 7,
  "goal": "增肌",
  "startDate": "2026-06-01",
  "weeks": 4,
  "daysPerWeek": 3,
  "intensity": "medium",
  "days": [
    { "weekday": 1, "rest": false, "category": "legs", "durationMin": 40, "focus": "下肢肌力" },
    { "weekday": 2, "rest": true, "category": null, "durationMin": 0, "focus": "休息與恢復" }
  ],
  "createdAt": "2026-06-01T00:00:00Z"
}
```

**Errors**：`400 VALIDATION_ERROR`（`daysPerWeek`/`weeks` 超出範圍或缺漏）、`429 RATE_LIMITED`

---

### 6b.2 取得課表清單

`GET /workout-schedules` → `WorkoutSchedule[]`（信封格式，最新的在前）

### 6b.3 取得單筆

`GET /workout-schedules/{id}`

### 6b.4 套用某一天到實際日期

`POST /workout-schedules/{id}/apply`

把該課表中某個 `weekday` 的訓練，依 6.1 的 AI 產生流程生成到指定日期的菜單（沿用課表強度）。

**Request**
```json
{ "date": "2026-06-01", "weekday": 1 }
```

`date` 的星期必須與 `weekday` 一致；休息日不可套用。

**Response 201**：新建立的 `WorkoutPlan`（同 6.1）

**Errors**：`400 BAD_REQUEST`（日期星期不符、該天為休息日、或分類無效）、`404 NOT_FOUND`、`429 RATE_LIMITED`

---

### 6b.5 刪除課表

`DELETE /workout-schedules/{id}` → 204

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

### 7.2 AI 餐點預覽與確認

推薦前端流程：先呼叫 `POST /meals/preview` 取得候選食物與總營養，讓使用者確認後再呼叫 `POST /meals/confirm` 寫入餐點日誌。這可避免照片辨識錯誤直接落地。

預覽會用食物資料庫校準營養：AI 負責辨識食物與克數，凡名稱命中 catalog 的項目，其熱量與三大營養素會改以資料庫每 100g 基準按克數重算（取名稱最長的最精確匹配），未命中的食物維持 AI 估值。使用者仍可在確認清單再調整。

`POST /meals/preview`
Content-Type：`multipart/form-data`

| Part | 型別 | 必填 | 說明 |
|---|---|---|---|
| `image` | file (jpg/png/webp, ≤ 10 MB) | ✗ | 餐點照片；只用於 AI 分析，不會在 preview 階段保存 |
| `description` | string | ✗ | 文字描述 |
| `slot` | string | ✓ | `breakfast` \| `lunch` \| `dinner` \| `snack` |
| `date` | string (YYYY-MM-DD) | ✗ | 預設今日 |

**Response 200**
```json
{
  "date": "2026-05-25",
  "slot": "lunch",
  "description": "雞胸肉沙拉 + 半碗糙米飯",
  "items": [
    { "name": "雞胸肉", "grams": 150, "kcal": 248, "protein": 46.5, "fat": 5.4, "carb": 0, "confidence": 0.92 }
  ],
  "totalKcal": 248,
  "totalProtein": 46.5,
  "totalFat": 5.4,
  "totalCarb": 0,
  "aiSuggestion": "蛋白質充足，建議補充綠色蔬菜增加纖維。"
}
```

`POST /meals/confirm`
Content-Type：`multipart/form-data`

| Part | 型別 | 必填 | 說明 |
|---|---|---|---|
| `image` | file (jpg/png/webp, ≤ 10 MB) | ✗ | 使用者確認後才保存的原餐點照片 |
| `description` | string | ✗ | 確認後的文字描述 |
| `slot` | string | ✓ | `breakfast` \| `lunch` \| `dinner` \| `snack` |
| `date` | string (YYYY-MM-DD) | ✗ | 預設今日 |
| `items` | JSON string | ✓ | `FoodItem[]`，最多 5 筆；後端依此重新計算總量 |
| `aiSuggestion` | string | ✗ | preview 回傳的 AI 建議 |

**Response 201**：同 `MealResponse`

**Errors**：`400 BAD_REQUEST`（未提供圖片或描述、`items` JSON 無效或超過 5 筆）、`413 PAYLOAD_TOO_LARGE`、`415 UNSUPPORTED_MEDIA_TYPE`

---

### 7.3 取得當日飲食

`GET /meals?date=2026-05-25`

**Response 200**：`Meal[]`

---

### 7.4 取得單筆

`GET /meals/{id}`

### 7.5 最近可重用餐點

`GET /meals/recent?beforeDate=2026-06-01&limit=5`

回傳 `beforeDate` 之前的最近餐點，用於快速複製到今天。`limit` 範圍 1–10，預設 5。

**Response 200**
```json
{
  "data": [
    {
      "id": 42,
      "date": "2026-05-31",
      "displayName": "鮭魚 + 白飯",
      "slot": "dinner",
      "description": "鮭魚與白飯",
      "items": [
        { "name": "鮭魚", "grams": 120, "kcal": 240, "protein": 26, "fat": 14, "carb": 0, "confidence": 1 }
      ],
      "totalKcal": 420,
      "totalProtein": 30,
      "totalFat": 15,
      "totalCarb": 48,
      "createdAt": "2026-05-31T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

### 7.6 常用餐點

`GET /meals/favorites`

回傳使用者收藏的常用餐點模板。常用餐點不保存照片，只保存描述、餐別、營養明細與總量。

`POST /meals/{id}/favorite`

把既有餐點收藏為常用餐點。

**Request**
```json
{ "name": "健身午餐" }
```

`name` 可省略；後端會用食物明細或描述產生預設名稱。

`DELETE /meals/favorites/{id}` → 204

### 7.7 複製餐點

`POST /meals/{id}/copy`

從既有餐點複製成新的每日紀錄。

`POST /meals/favorites/{id}/copy`

從常用餐點複製成新的每日紀錄。

**Request**
```json
{
  "date": "2026-06-01",
  "slot": "dinner"
}
```

`date` 必填；`slot` 可省略，省略時沿用來源餐別。複製不會重新執行 AI，也不會複製原餐點照片。

**Response 201**：同 `MealResponse`

### 7.8 更新餐點（手動修正 AI 結果）

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

### 7.9 刪除餐點

`DELETE /meals/{id}` → 204

---

## 7b. Foods（食物資料庫）

常見食物搜尋，用於餐點 AI 預覽確認清單、既有餐點手動修正與手動補項目。第一版使用後端內建 catalog；回應營養素以 `servingGrams` 這份常見份量計算，前端加入餐點時可直接轉成 `FoodItem`，並在使用者調整克數或切換 0.5/1/1.5/2 份時按比例重算熱量與三大營養素。

`GET /foods?q=雞&limit=8`

| Query | 型別 | 必填 | 說明 |
|---|---|---|---|
| `q` | string | ✗ | 食物名稱、分類或 alias；空白回 `[]` |
| `limit` | number | ✗ | 預設 10，上限 20 |

**Response 200**
```json
[
  {
    "id": "chicken-breast",
    "name": "雞胸肉",
    "category": "蛋白質",
    "servingGrams": 150,
    "kcal": 248,
    "protein": 46.5,
    "fat": 5.4,
    "carb": 0,
    "aliases": ["雞肉", "chicken breast", "chicken"]
  }
]
```

### 今日飲食建議 — `GET /foods/suggestions`  *(需登入)*

依當日熱量收支與蛋白質缺口，從食物 catalog 推薦幾項具體食物（常見份量）。缺口由後端以 `GET /stats/budget` 同源計算：`proteinGapG = max(0, 蛋白質目標 − 已攝取)`。超出預算時改推低熱量、低油的選項。完全不需 AI（決定性）。

**Response 200**
```json
{
  "remainingKcal": 500,
  "proteinGapG": 60,
  "over": false,
  "headline": "下一餐優先補蛋白質（缺口約 60g）",
  "items": [
    { "id": "chicken-breast", "name": "雞胸肉", "category": "蛋白質",
      "grams": 150, "kcal": 248, "protein": 46.5, "reason": "高蛋白，補足今日蛋白質缺口" }
  ]
}
```

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

### 8.1b 當日熱量預算環

`GET /stats/budget?date=2026-05-25`

把當日的目標、運動消耗、攝取整合成「預算環」,並依使用者目標把目標熱量拆成三大營養素的克數目標。**全部即時計算**(複用 `/stats/daily`)。

- `budgetKcal = goalKcal + burnKcal`(運動把熱量補回來),`remainingKcal = budgetKcal − intakeKcal`(可為負)。
- `consumedPct` 為 `intake / budget` 百分比(未夾上限,前端自行夾住環形);`over` 在攝取超過預算時為 `true`。
- 營養素目標佔目標熱量比例(蛋白質/碳水 4 kcal/g、脂肪 9 kcal/g):減脂 35/35/30、維持 30/40/30、增肌 30/45/25。

**Response 200**
```json
{
  "date": "2026-05-25",
  "goalKcal": 1700, "intakeKcal": 1200, "burnKcal": 300,
  "budgetKcal": 2000, "remainingKcal": 800, "consumedPct": 60, "over": false,
  "macros": [
    { "name": "protein", "targetG": 128, "consumedG": 90,  "pct": 70 },
    { "name": "carb",    "targetG": 170, "consumedG": 120, "pct": 71 },
    { "name": "fat",     "targetG": 57,  "consumedG": 40,  "pct": 70 }
  ]
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

## 8b. Health Plan（健康計畫）

Health Plan 是 Dashboard 的聚合層，整合今日熱量預算、體重目標、每週訓練目標與整體 streak，回傳一組可直接顯示的計畫摘要與下一步建議。第一版為 read-only 推導，不新增資料表。

### 8b.1 取得指定日期健康計畫

`GET /health-plan?date=2026-06-08` *(需登入)*

**Response 200**
```json
{
  "date": "2026-06-08",
  "primaryGoal": "減脂",
  "readinessScore": 78,
  "nutrition": {
    "goalKcal": 1700,
    "budgetKcal": 1950,
    "intakeKcal": 1200,
    "burnKcal": 250,
    "remainingKcal": 750,
    "consumedPct": 62,
    "over": false,
    "macros": [
      { "name": "protein", "targetG": 149, "consumedG": 67, "pct": 45 }
    ]
  },
  "weight": {
    "configured": true,
    "currentWeightKg": 76.0,
    "targetWeightKg": 70.0,
    "remainingKg": -6.0,
    "progressPct": 25,
    "targetDate": "2026-07-01",
    "projectedDate": "2026-08-31",
    "onTrack": false,
    "achieved": false
  },
  "workout": {
    "configured": true,
    "workoutsDoneToday": 1,
    "workoutsPlannedToday": 1,
    "targetSessionsPerWeek": 4,
    "completedThisWeek": 2,
    "remainingThisWeek": 2,
    "progressPct": 50,
    "achievedThisWeek": false
  },
  "streak": { "current": 3, "longest": 8, "lastActiveDate": "2026-06-08" },
  "nextActions": [
    {
      "type": "PLAN_WORKOUT",
      "title": "安排下一次訓練",
      "detail": "本週還差 2 次訓練，建議先排入行事曆。",
      "priority": 70,
      "href": "/workouts"
    }
  ]
}
```

### 8b.2 取得今日健康計畫

`GET /health-plan/today` *(需登入)*

等同 `GET /health-plan?date=<server today>`。

### 8b.3 取得健康計畫設定

`GET /health-plan/settings` *(需登入)*

回傳目前整合設定：主目標、目前體重、體重目標進度、每週訓練目標進度。

**Response 200**
```json
{
  "primaryGoal": "fat_loss",
  "currentWeightKg": 76.0,
  "weightGoal": null,
  "workoutGoal": null
}
```

`weightGoal` 與 `workoutGoal` 形狀分別同 `/weight-goal`、`/workout-goal` 的 `progress`；尚未設定時為 `null`。

### 8b.4 更新健康計畫設定

`PUT /health-plan/settings` *(需登入)*

此端點是整合目標入口，會同步更新：
- `primaryGoal` → `profile.goal`
- `weightGoal` → 體重目標（`enabled=false` 時清除）
- `workoutGoal` → 每週訓練目標（`enabled=false` 時清除）

**Request**
```json
{
  "primaryGoal": "muscle_gain",
  "weightGoal": {
    "enabled": true,
    "targetWeightKg": 82.0,
    "targetDate": "2026-09-01"
  },
  "workoutGoal": {
    "enabled": true,
    "targetSessionsPerWeek": 4
  }
}
```

**Response 200**：同 `GET /health-plan/settings`

**Errors**：`400 BAD_REQUEST`（啟用目標但缺少必填值）、`400 VALIDATION_ERROR`

---

## 9. Habits（每日習慣）

每日習慣是系統內建的輕量 checklist，目前固定 4 項：`WATER`、`STRETCH`、`PROTEIN`、`SLEEP`。每位使用者同一天同一項最多只有一筆完成紀錄。

### 9.1 取得每日習慣

`GET /habits/daily?date=2026-06-06`

**Response 200**
```json
{
  "date": "2026-06-06",
  "completed": 1,
  "total": 4,
  "items": [
    {
      "type": "WATER",
      "title": "喝水",
      "description": "今天至少補足 6 杯水",
      "completed": true,
      "completedAt": "2026-06-06T02:14:12Z",
      "streak": 3
    },
    {
      "type": "STRETCH",
      "title": "伸展",
      "description": "完成 5 分鐘伸展或活動度練習",
      "completed": false,
      "completedAt": null,
      "streak": 0
    }
  ]
}
```

`streak` 為該習慣**連續達成天數**（截至查詢日）：當日尚未完成時給寬限日，從前一天起算，連續紀錄不會因今天還沒打勾就歸零。

### 9.2 切換習慣完成狀態

`POST /habits/{type}/toggle`

`type` 可為：`WATER`、`STRETCH`、`PROTEIN`、`SLEEP`。

**Request**
```json
{
  "date": "2026-06-06",
  "completed": true
}
```

`completed=true` 時建立完成紀錄；`completed=false` 時刪除該日期該習慣的完成紀錄。重複送出同一狀態是 idempotent。

**Response 200**：同 `GET /habits/daily`

---

## 10. AI Gateway

### 10.1 取得狀態

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

### 10.2 手動釋放本地模型

`POST /ai/unload`  *(需登入；admin 或本機請求)*

**Response 200**
```json
{ "unloaded": true }
```

> 註：呼叫後下一次 AI 請求會自動 lazy load，預期延遲 1–10 秒。

### 10.3 AI 小幫手對話

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

## 10b. System Diagnostics（系統狀態）

### 10b.1 取得系統狀態

`GET /system/status` *(需登入)*

回傳後端、資料庫、AI provider 與限流後端的狀態。此端點只提供診斷摘要，不暴露資料庫 URL、Redis URI 或 secret。

**Response 200**
```json
{
  "status": "UP",
  "checkedAt": "2026-06-08T00:00:00Z",
  "components": [
    { "key": "backend", "label": "Backend API", "status": "UP", "detail": "request handled" },
    { "key": "database", "label": "Database", "status": "UP", "detail": "connection validated" },
    { "key": "ai", "label": "AI provider", "status": "UP", "detail": "local text=gemma4:e4b vision=gemma4:e4b idle" },
    { "key": "rateLimit", "label": "Rate limiter", "status": "UP", "detail": "memory backend" }
  ]
}
```

`status` 可為：
- `UP`：所有元件正常。
- `DEGRADED`：非資料庫元件異常，核心 API 仍可處理請求。
- `DOWN`：資料庫不可用，核心功能無法正常運作。

---

## 9b. 每週健康報告（Weekly Report）

把一整週（自然週，週一–週日）的飲食、運動與體重整合成一份由本機 AI 產生的回顧。**關鍵數字每次即時計算**（永遠反映最新資料），只有 **AI 敘述**會依 `(使用者, 週起始日)` 持久化快取。

**取得報告** — `GET /reports/weekly?weekStart=YYYY-MM-DD`  *(需登入)*
- `weekStart` 選填，可為該週任一日（後端正規化到週一）；省略則取當週。
- 涵蓋區間為 `weekStart … min(weekStart+6, 今天)`。
- **不**觸發 AI；`narrative` 為已快取的敘述，未生成過則為 `null`。
- `adherence` 為當週達標率（皆 0~100）：`caloriePct`（有記錄的天數中攝取未超標的比例）、`proteinPct`（實際蛋白質 ÷ 目標蛋白質）、`workoutPct`（完成次數 ÷ 每週訓練目標；未設目標時 `workoutTarget` 為 `null`、`workoutPct` 為 0）、`loggingPct`（有記錄餐點的天數 ÷ 已涵蓋天數）。
- `trends` 為偵測到的可行動訊號（可能為空）：`PROTEIN_LOW`、`LOGGING_GAP`（依本週數字）、`WORKOUT_DECLINE`、`WEIGHT_PLATEAU`（與上週比較，跨週訊號僅在兩週皆完整涵蓋 7 天時才觸發，避免半週誤判）、`MUSCLE_GAIN`／`BODYFAT_DOWN`（本週體組成正向變化：肌肉量 ≥ +0.3 kg、體脂率 ≤ −0.5%，僅在有量測時觸發）。每筆含 `type` / `severity`（`info`｜`warn`）/ `title` / `detail`。

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
  "adherence": {
    "caloriePct": 86, "proteinPct": 92, "workoutPct": 75,
    "workoutTarget": 4, "loggingPct": 100, "daysLogged": 7, "daysCovered": 7
  },
  "trends": [
    { "type": "WORKOUT_DECLINE", "severity": "warn", "title": "訓練量下降",
      "detail": "完成訓練從上週 4 次降到本週 2 次…" }
  ],
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

## 9c. 連續記錄與成就（Streak & Achievements）

連續打卡天數**每次即時從餐點／運動／體重資料推算**（永不落地、不會與資料漂移）；只有「已解鎖的徽章」會持久化(用來顯示解鎖時間並避免重複通知)。

**取得 Streak 與成就牆** — `GET /streak`  *(需登入)*
- 回傳三種連續記錄與徽章牆。
- **副作用(冪等)**：呼叫時會 reconcile 成就——把已達門檻但尚未解鎖的徽章寫入，並在 `newlyUnlocked` 回報本次新解鎖的代碼(供前端慶祝)。

連續記錄定義(皆給「今日」一天寬限，今天還沒記不算斷)：
| 欄位 | 「當天有打卡」的判定 |
| --- | --- |
| `mealStreak` | 當天至少一筆餐點 |
| `workoutStreak` | 當天至少一筆 `done=true` 的訓練 |
| `overallStreak` | 當天有記任一項(餐／運動／體重) |

**Response 200**
```json
{
  "mealStreak":    { "current": 3, "longest": 12, "lastActiveDate": "2026-06-06" },
  "workoutStreak": { "current": 1, "longest": 5,  "lastActiveDate": "2026-06-06" },
  "overallStreak": { "current": 3, "longest": 14, "lastActiveDate": "2026-06-06" },
  "achievements": [
    { "code": "STREAK_7", "title": "一週不間斷", "emoji": "🔥", "description": "連續 7 天記錄健康數據",
      "threshold": 7, "progress": 7, "unlocked": true, "unlockedAt": "2026-06-06T02:00:00Z" },
    { "code": "MEALS_100", "title": "飲食達人", "emoji": "🥗", "description": "累計記錄 100 筆餐點",
      "threshold": 100, "progress": 62, "unlocked": false, "unlockedAt": null }
  ],
  "newlyUnlocked": ["STREAK_7"]
}
```

徽章目錄(寫死於 `AchievementCatalog`)：`STREAK_3/7/30`、`MEALS_50/100`、`WORKOUTS_10/50`、`FIRST_WEIGHT`、`PHOTO_5/25`(照片記餐筆數)、`WEEKLY_GOAL_1/4`(達成每週訓練目標的週數；未設定每週訓練目標時恆為 0)。

---

## 9d. 通知中心（Notification Center）

把「成就解鎖」與「即時提醒」整合成單一通知流。**內容皆即時推導**(成就來自已解鎖紀錄、提醒由當日資料計算)；只持久化每位使用者的「最後讀取時間」浮水印,用來算未讀數。

**取得通知** — `GET /notifications`  *(需登入)*

**標記全部已讀** — `POST /notifications/read`  *(需登入)* — 將浮水印推進到現在,回傳 `unreadCount: 0` 的最新清單。

提醒規則(即時計算,每日刷新)：
| `type` | 觸發條件 | `severity` |
| --- | --- | --- |
| `ACHIEVEMENT` | 已解鎖的徽章(最近 15 筆) | success |
| `MEAL_REMINDER` | 今天還沒記任何餐點 | warning |
| `PROTEIN_REMINDER` | 今天已記錄餐點，但蛋白質達標率 < 40% | info |
| `STREAK_RISK` | 連續紀錄 ≥ 2 天且今天還沒任何活動 | warning |
| `WEIGHT_REMINDER` | 距上次量體重 ≥ 7 天 | info |
| `REPORT_REMINDER` | 上週的 AI 健康報告尚未生成 | info |

未讀判定：成就項 `unlockedAt > lastReadAt` 即未讀;提醒項在「今天尚未開啟過通知中心」時為未讀。

**Response 200**
```json
{
  "items": [
    { "key": "meal:2026-06-06", "type": "MEAL_REMINDER", "title": "今天還沒記錄飲食",
      "body": "別忘了把今天吃的記下來，讓 AI 幫你分析營養。", "emoji": "🍽️",
      "severity": "warning", "createdAt": "2026-06-06T00:00:00Z", "actionHref": "/meals", "read": false },
    { "key": "ach:STREAK_7", "type": "ACHIEVEMENT", "title": "解鎖成就：一週不間斷",
      "body": "連續 7 天記錄健康數據", "emoji": "🔥",
      "severity": "success", "createdAt": "2026-06-05T02:00:00Z", "actionHref": "/", "read": true }
  ],
  "unreadCount": 1
}
```

---

## 9e. 體重目標（Weight Goal）

每位使用者一個體重目標。設定時會把「起始體重／起始日」快照下來作為錨點;**目前體重、每週速率、預估達標日與是否如期**全部即時計算(不快取)。

**取得目標與進度** — `GET /weight-goal`  *(需登入)* — `progress` 為 `null` 代表尚未設定。

**設定／更新** — `PUT /weight-goal`  *(需登入)* — 重新把起始錨點設為「現在」。
```json
{ "targetWeightKg": 70.0, "targetDate": "2026-08-01" }
```
- `targetWeightKg` 必填(20–400);`targetDate` 選填(無期限)。

**移除** — `DELETE /weight-goal`  *(需登入)* — 回 `204`。

進度欄位(`progress`,帶正負號保留方向,負值=減少)：
| 欄位 | 說明 |
| --- | --- |
| `currentWeightKg` | 最新體重(無紀錄則回退 Profile 體重) |
| `remainingKg` / `changeSoFarKg` | 目標−目前 / 目前−起始 |
| `progressPct` | 0–100,只計入朝目標方向的進展 |
| `ratePerWeekKg` | 自起始日起的平均週速率;不足 7 天或無變化為 `null` |
| `requiredRatePerWeekKg` | 從今天到 `targetDate` 如期達標所需的每週速率(帶正負號);無期限／已達標／期限已過為 `null` |
| `projectedDate` | 依目前速率預估達標日;無法推估為 `null` |
| `onTrack` | 預估是否在 `targetDate` 前達成;已達標為 `true`,無法判斷為 `null` |
| `achieved` | 是否已達標(依目標方向判定) |

**Response 200**
```json
{
  "progress": {
    "targetWeightKg": 70.0, "startWeightKg": 80.0, "currentWeightKg": 76.0,
    "startDate": "2026-05-23", "targetDate": "2026-08-01",
    "remainingKg": -6.0, "changeSoFarKg": -4.0, "progressPct": 40,
    "ratePerWeekKg": -2.0, "requiredRatePerWeekKg": -0.6, "projectedDate": "2026-06-27",
    "onTrack": true, "achieved": false, "createdAt": "2026-05-23T00:00:00Z"
  }
}
```

---

## 9f. 集中搜尋（Search）

跨「餐點」與「運動」的關鍵字搜尋,直接查現有資料表(無搜尋索引)。

**搜尋** — `GET /search?q=雞&limit=20`  *(需登入)*
- `q` 空白回傳空結果;`limit` 選填(預設 20,上限 50)。
- 比對範圍:餐點的描述／食物項目／時段、運動的分類／動作項目(皆不分大小寫)。
- 結果合併後依日期新到舊排序並截斷;`title`/`subtitle` 已在地化(時段、運動分類);`type` 為 `MEAL` 或 `WORKOUT`,前端據此跳到對應頁。
- `foods` 另外回傳食物資料庫(catalog)中符合關鍵字的營養基準(最多 5 筆,形狀同 `GET /foods`),與使用者自己的紀錄分開呈現,供查營養參考。

**Response 200**
```json
{
  "query": "雞",
  "results": [
    { "type": "WORKOUT", "id": 22, "title": "腹肌核心", "subtitle": "已完成", "date": "2026-06-06", "kcal": 120 },
    { "type": "MEAL", "id": 11, "title": "雞胸肉沙拉", "subtitle": "午餐", "date": "2026-06-05", "kcal": 420 }
  ],
  "foods": [
    { "id": "chicken-breast", "name": "雞胸肉", "category": "蛋白質", "servingGrams": 150,
      "kcal": 248, "protein": 46.5, "fat": 5.4, "carb": 0, "aliases": ["雞肉", "chicken"] }
  ]
}
```

---

## 11. 資料型別參考

### 11.1 `Exercise`
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

### 11.2 `FoodItem`
| 欄位 | 型別 | 說明 |
|---|---|---|
| `name` | string | 食物名稱 |
| `grams` | number | 重量 |
| `kcal` | int | 熱量 |
| `protein` | number | 蛋白質（g） |
| `fat` | number | 脂肪（g） |
| `carb` | number | 碳水（g） |
| `confidence` | number | 0–1，AI 信心度（手動輸入為 1.0） |

### 11.3 列舉

| 名稱 | 可選值 |
|---|---|
| `WorkoutCategory` | `abs` `waist` `legs` `chest` `back` `glutes` `arms` `full_body` `cardio` |
| `MealSlot` | `breakfast` `lunch` `dinner` `snack` |
| `Goal` | `muscle_gain` `fat_loss` `maintain` |
| `Experience` | `beginner` `intermediate` `advanced` |
| `Gender` | `male` `female` `other` |
| `Role` | `USER` `ADMIN` |

---

## 12. HTTP 狀態碼

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

## 13. 速率限制與分頁

### 13.1 速率限制
| 範疇 | 限制 | 狀態 |
|---|---|---|
| `POST /auth/register` | 5 req / min / IP | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |
| `POST /auth/login` | 10 req / min / IP + email | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |
| `POST /auth/refresh` | 30 req / min / IP | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |
| 登入後一般 API | 120 req / min / user | 📋 規劃中 |
| AI 端點（`/workouts/generate`、`POST /meals`、`POST /meals/preview`） | 20 req / min / user | ✅ 已實作；預設 memory fixed window，可切 Redis Bucket4j token bucket |

超限回 `429 RATE_LIMITED`。設定 `RATE_LIMIT_BACKEND=redis` 時，限流狀態會存放在 Redis，適合多台 backend 共用；Redis 不可用時預設 `RATE_LIMIT_REDIS_FAIL_OPEN=false`，會回 `503`，正式環境建議維持 fail-closed。預設不信任 `X-Forwarded-For`；只有後端部署在可信任 reverse proxy 後方時才設定 `RATE_LIMIT_TRUST_FORWARDED_FOR=true`。目前限流不回傳 `X-RateLimit-*` header。

### 13.2 分頁
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
