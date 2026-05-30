# MyHealth — AI 健身與飲食管理平台

> **文件狀態：產品與技術企劃 + M1 實作中（v0.3）**
> 本文件描述 MyHealth 的產品願景、技術架構與 API 規格。下面 §0「目前實作狀態」標示每個模組目前是 ✅ 已實裝 / 🟡 stub / 🔴 未實作 / 📋 規劃中。所有資料庫 schema、API 端點、技術選型仍會依實作驗證調整；任何標示「規劃中／Phase 2」之功能均不保證進入最終版本。

---

## 〇、目前實作狀態（last updated 2026-05-30）

| 模組 | 狀態 | 備註 |
|---|---|---|
| Auth（註冊/登入/JWT/Refresh rotation） | ✅ | 含 12 個 AuthService + 7 個 JwtService 單元測試；登入、refresh 與 AI 高成本端點已支援 memory / Redis Bucket4j 限流 |
| User profile + body measurements | ✅ | |
| Workouts CRUD + complete | ✅ | 文字 AI 可走 Ollama；不可用或輸出異常時 fallback 模板 |
| Meals CRUD（文字 + 圖片 multipart） | ✅ | 文字走 text model；圖片會以 base64 `images` payload 送入 vision model；無法可靠辨識時不猜測熱量 |
| Stats `/daily`、`/range` | ✅ | 即時計算，體重趨勢讀 `body_measurements` 歷史紀錄，無彙總表 |
| AI Provider 介面 + IdleWatcher | ✅ | 介面 + 排程到位 |
| **LocalAiProvider 真實串接 Ollama** | ✅ | 預設模型 `gemma4:e4b`；NDJSON streaming + 自動 `unload()`；運動 JSON 解析失敗時 fallback 到模板，餐點則要求手動修正 |
| 圖片 AI 辨識（vision model payload） | ✅ | 餐點圖片會送入 Ollama vision model；不支援或解析失敗時 fallback |
| OpenAI / Anthropic Provider | 🔴 | Phase 2 |
| Frontend：Tailwind + shadcn-ui + TanStack Query + Router + Axios | ✅ | 6 個 pages、深淺色主題、JWT auto-refresh |
| Recharts 趨勢圖 | ✅ | 7 天體重趨勢已串接歷史量測資料 |
| DTO / request validation | ✅ | 身體數據範圍、theme/language、Workout category/intensity、Meal slot 皆有後端約束 |
| Service 層單元測試（Auth/User/Workout/Meal/Stats/Jwt/AI） | ✅ | 65 個案例 |
| Controller @WebMvcTest（5 個 Controller + GlobalExceptionHandler） | ✅ | 39 + 4 個案例 |
| Coverage ≥ 60%（DoD） | ✅ | 共 112 個測試案例，Service 與 Controller 兩層皆覆蓋 |
| Google OAuth2 | 🔴 | Phase 2 |
| Maven Wrapper + `scripts/dev.sh` 一鍵啟動 | ✅ | |
| Flyway migrations（V1 + V2） | ✅ | |
| ErrorBoundary + 全域錯誤格式 | ✅ | |

一個整合「每日運動菜單規劃」與「三餐飲食紀錄／熱量分析」的 Web 平台。
**架構為前後端分離**：前端 React（SPA），後端 Java（Spring Boot），資料庫 PostgreSQL；所有功能透過 REST API 串接。
透過本地端 AI（以 API 形式串接，可切換至雲端 AI 服務）提供個人化的健身建議與飲食評估，並在閒置時自動釋放 RAM，降低本地端功耗。

> ⚠️ **免責聲明**：本平台提供之運動建議、熱量估算與飲食建議僅供一般參考，**非醫療、營養或運動處方**。如有疾病、孕期、傷後復健或特殊飲食需求，請諮詢合格醫師、營養師或運動專業人員。詳見 §十「產品風險與免責」。

---

## 一、專案目標

1. **前後端分離（API-first）**：所有業務邏輯與資料存取由 Java 後端透過 REST API 提供，React 前端透過 HTTP/JSON 呼叫；前後端可獨立部署、版本獨立演進。
2. **多使用者帳號系統**：支援註冊／登入，每位使用者擁有獨立的個人檔案、運動紀錄與飲食紀錄，資料以帳號隔離；資料庫採用 **PostgreSQL** 集中管理。
3. **運動規劃**：使用者依目標（練腹肌、瘦肚子、練腿、增肌、有氧…）選擇分類，AI 產生當日／當週菜單（動作、組數、次數、休息秒數、預估消耗熱量）。
4. **飲食紀錄**：使用者上傳餐點照片或輸入文字描述，AI 即時辨識食物、估算熱量大卡與三大營養素，並給出飲食建議。
5. **本地 AI 為主、可切換**：以統一的 AI Provider 介面串接本地 LLM／VLM（Ollama、LM Studio、llama.cpp server），未來可切換為 OpenAI、Anthropic、Gemini 等雲端 API。
6. **功耗友善**：偵測閒置時自動 unload 模型釋放 VRAM／RAM，使用時再動態載入。

---

## 二、核心功能

### 2.0 帳號系統（多使用者管理）
- **註冊（必填）**：Email 帳號、密碼（BCrypt 雜湊，至少 8 字，只允許半形英數且需含大小寫英文）、**姓名**、**性別**、**身高 (cm)**、**體重 (kg)**
- **註冊（選填）**：年齡、體脂率 (%)、肌肉量 (kg)、基礎代謝率 BMR、腰圍、體水分率、健身目標、可用器材、健身經驗
- **OAuth2（Phase 2）**：Google 登入；首次登入後仍須補齊上列必填欄位才能使用主要功能
- **登入／登出**：Spring Security + JWT（Access Token + Refresh Token）
- **個人檔案**：使用者可隨時於設定頁更新身體檢測數據；歷史資料以時間序列記錄供趨勢分析
- **資料隔離**：所有運動菜單、飲食紀錄、統計資料皆以 `user_id` 為外鍵，後端在 Service 層強制注入「目前登入者」過濾條件
- **角色（Phase 2 預留）**：`USER` / `ADMIN`（admin 可查看系統健康度、模型狀態，無權讀取他人健康資料）
- **密碼重設（Phase 2）**：Email 寄送驗證連結（開發階段印到 console）
- **多裝置**：Refresh Token 以 DB 儲存可逐一撤銷（MVP 僅做 DB 層撤銷能力；管理 UI 列入 Phase 2）

### 2.1 運動健身菜單
- 分類選擇：腹肌 / 瘦肚 / 練腿 / 練胸 / 練背 / 臀部 / 手臂 / 全身 / 有氧
- 個人化參數：性別、年齡、身高體重、健身經驗、可用器材、訓練時長
- AI 產生：當日菜單 + 動作說明 + 替代動作 + 預估卡路里
- 紀錄：完成度打卡、訓練量趨勢圖

### 2.2 飲食紀錄
- 輸入方式：**拍照上傳** 或 **文字描述**（兩者可同時）
- AI 處理：食物辨識 → 份量推估 → 熱量／蛋白質／脂肪／碳水換算
- 即時建議：依當日剩餘熱量、訓練目標給出加減餐建議
- 三餐 + 點心分區記錄、每日／每週統計

### 2.3 儀表板
- 今日熱量收支（攝取 − 消耗）
- 訓練完成度、體重趨勢、營養素達成率

---

## 三、技術架構

```
┌────────────────────────────────────────────────────────┐
│  Frontend — React 18 + Vite + TypeScript               │
│  React Router / TanStack Query / Tailwind / shadcn-ui  │
└──────────────────────┬─────────────────────────────────┘
                       │ REST (JSON) + JWT Bearer
                       │ CORS enabled
┌──────────────────────▼─────────────────────────────────┐
│  Backend — Java 21 + Spring Boot 3                     │
│  Spring Web / Security (JWT) / Data JPA / Validation   │
│  ─ AuthController / UserController                     │
│  ─ WorkoutController / MealController / StatsController│
│  ─ AiController (Gateway)                              │
│  ─ AiProvider 介面（Local / OpenAI / Anthropic）       │
│  ─ IdleWatcher（@Scheduled, 模型 unload）              │
└──────────┬───────────────────────┬─────────────────────┘
           │ JDBC                  │ HTTP
   ┌───────▼────────┐      ┌───────▼──────────────────┐
   │  PostgreSQL 16 │      │  AI Provider              │
   │  + Flyway 遷移 │      │  ─ Local: Ollama          │
   └────────────────┘      │  ─ OpenAI / Anthropic …   │
                           └───────────────────────────┘
```

### 3.1 技術選型
| 層級 | 選擇 | 備註 |
|------|------|------|
| Frontend | React 18 + Vite + TypeScript | ✅ 已實裝 |
| 前端路由 | React Router v6 | ✅ |
| 前端資料層 | TanStack Query v5 + Axios | ✅ JWT auto-refresh interceptor 已串接 |
| UI | Tailwind CSS + shadcn/ui 風格元件 | ✅ 自建在 `src/components/ui/`（Radix UI primitives）|
| 圖表 | Recharts | ✅ 7 天體重趨勢已串接 |
| Backend | Java 21 + Spring Boot 3 | |
| Web | Spring Web (MVC) | RESTful Controller |
| 安全性 | Spring Security 6 + JWT (jjwt) | Stateless，Bearer Token |
| ORM | Spring Data JPA + Hibernate | |
| 遷移 | Flyway | SQL 版本控管 |
| 驗證 | Jakarta Bean Validation | DTO 驗證 |
| 文件 | springdoc-openapi (Swagger UI) | 自動產生 API 文件 |
| 資料庫 | **PostgreSQL 16** | |
| 限流 | Bucket4j + Redis / in-memory fallback | Auth 與 AI 高成本端點已支援；預設 memory，正式多節點可切 Redis |
| 檔案儲存 | 本地 `uploads/`（開發） / S3 相容（正式） | 餐點圖片 |
| 構建 | Maven 或 Gradle | 範例採 Maven |
| 本地 AI | Ollama | 文字：`qwen2.5:7b`；視覺：`qwen2-vl:7b` |
| HTTP Client（後端） | Java 11+ `HttpClient` 或 Spring WebClient | 呼叫 Ollama／雲端 AI |

### 3.2 AI Provider 抽象介面（Java）
```java
public interface AiProvider {
    String provider();
    String textModel();
    String visionModel();
    List<ExerciseItem> generateWorkout(String category, int durationMin, String intensity);
    MealAnalysis analyzeMeal(String description, MealImage image);
    void markUsed();
    void unload();
    boolean loaded();
    Instant lastUsedAt();
}
```
**目前實作狀態**：
- `LocalAiProvider`：✅ 已透過 `OllamaClient` 呼叫 Ollama `POST /api/chat`，解析模型回傳 JSON；Ollama 不可用、回傳空資料或 JSON malformed 時 fallback 到內建模板。
- 餐點圖片：✅ 已支援 multipart 上傳、驗證與儲存；有圖片時會把 bytes 編成 base64，透過 Ollama `images` payload 傳給 vision model。
- `OpenAiProvider` / `AnthropicProvider`：🔴 未建立。
- 切換策略（規劃中）：Spring `@ConditionalOnProperty(name = "ai.provider")` 注入。

### 3.3 閒置釋放 RAM 策略
1. 每次 AI 請求更新 `lastUsedAt`（記在 `AiProvider` Bean 內，volatile）。
2. Spring `@Scheduled(fixedDelay = 60_000)` 每分鐘檢查：
   - 若 `now - lastUsedAt > aiIdleTimeoutSec`，呼叫 `provider.unload()`。
   - Ollama：對該模型送一次 `keep_alive: "0s"` 請求即釋放。
3. 下次請求自動 `warmup()` lazy load；前端依 `GET /api/ai/status` 顯示載入中骨架。
4. `GET /api/ai/status`、`POST /api/ai/unload` 提供前端手動控制。

---

## 四、資料模型（PostgreSQL — 透過 JPA Entity 對應，Flyway 管理 schema）

```sql
-- V1__init.sql 概念示意
CREATE TABLE users (
  id              BIGSERIAL PRIMARY KEY,
  email           VARCHAR(255) UNIQUE NOT NULL,
  password_hash   VARCHAR(255),
  name            VARCHAR(100),
  role            VARCHAR(20) NOT NULL DEFAULT 'USER',
  email_verified  TIMESTAMP,
  created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles (
  user_id          BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  gender           VARCHAR(10)  NOT NULL,        -- 必填
  height_cm        NUMERIC(5,2) NOT NULL,        -- 必填
  weight_kg        NUMERIC(5,2) NOT NULL,        -- 必填
  age              INT,                          -- 選填
  body_fat_pct     NUMERIC(4,1),                 -- 選填，體脂率 %
  muscle_mass_kg   NUMERIC(5,2),                 -- 選填，肌肉量
  bmr_kcal         INT,                          -- 選填，基礎代謝率
  waist_cm         NUMERIC(5,2),                 -- 選填，腰圍
  body_water_pct   NUMERIC(4,1),                 -- 選填，體水分率
  goal             VARCHAR(30),                  -- 選填
  equipment        TEXT[],                       -- 選填
  experience       VARCHAR(20),                  -- 選填
  theme            VARCHAR(10) NOT NULL DEFAULT 'system',   -- light | dark | system（MVP 同步偏好用）
  language         VARCHAR(10) NOT NULL DEFAULT 'zh-TW',    -- zh-TW | en（MVP 預設僅 zh-TW；en 為 Phase 2）
  updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 身體檢測歷史紀錄（供趨勢圖）
CREATE TABLE body_measurements (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  measured_at     TIMESTAMP NOT NULL DEFAULT NOW(),
  weight_kg       NUMERIC(5,2),
  body_fat_pct    NUMERIC(4,1),
  muscle_mass_kg  NUMERIC(5,2),
  bmr_kcal        INT,
  waist_cm        NUMERIC(5,2),
  body_water_pct  NUMERIC(4,1),
  note            TEXT
);
CREATE INDEX idx_body_measurements_user_time ON body_measurements(user_id, measured_at);

CREATE TABLE refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(255) NOT NULL,
  device_info VARCHAR(255),
  expires_at  TIMESTAMP NOT NULL,
  revoked     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE workout_plans (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date        DATE NOT NULL,
  category    VARCHAR(30) NOT NULL,
  items       JSONB NOT NULL,          -- Exercise[]
  total_kcal  INT NOT NULL,
  done        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_workout_user_date ON workout_plans(user_id, date);

CREATE TABLE meals (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date           DATE NOT NULL,
  slot           VARCHAR(20) NOT NULL,
  description    TEXT,
  image_url      TEXT,
  items          JSONB NOT NULL,        -- FoodItem[]
  total_kcal     INT NOT NULL,
  total_protein  NUMERIC(6,2),
  total_fat      NUMERIC(6,2),
  total_carb     NUMERIC(6,2),
  ai_suggestion  TEXT,
  created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_meals_user_date ON meals(user_id, date);

-- MVP 階段不建立 daily_stats 彙總表；統計 API 直接由 meals、workout_plans、
-- body_measurements 即時計算。若 Phase 2 需要更高查詢效能，再新增彙總表或物化檢視。
```

> 所有與使用者相關的表皆以 `user_id` 為外鍵，並設 `ON DELETE CASCADE`，刪除帳號時連動清除個人資料。

---

## 五、REST API

Base URL：`/api/v1`
認證：除 `auth/*` 外皆需 `Authorization: Bearer <accessToken>`

| Method | Path | 說明 |
|--------|------|------|
| POST   | `/auth/register` | 註冊（email + password） |
| POST   | `/auth/login` | 登入，回傳 `accessToken` + `refreshToken` |
| POST   | `/auth/refresh` | 以 refresh token 換新 access token |
| POST   | `/auth/logout` | 撤銷當前 refresh token |
| GET    | `/me` | 取得當前登入者資料 |
| PUT    | `/me/profile` | 更新個人檔案 |
| DELETE | `/me` | 刪除帳號（連動清除所有資料） |
| POST   | `/workouts/generate` | AI 依分類 + 個人資料產生菜單 |
| GET    | `/workouts?date=YYYY-MM-DD` | 取得當日菜單 |
| POST   | `/workouts/{id}/complete` | 完成打卡 |
| GET    | `/meals?date=YYYY-MM-DD` | 取得當日飲食 |
| POST   | `/meals` | 新增餐點（multipart：`image` + `description` + `slot`） |
| DELETE | `/meals/{id}` | 刪除餐點 |
| GET    | `/stats/daily?date=YYYY-MM-DD` | 當日熱量收支 |
| GET    | `/stats/range?from=&to=` | 區間統計（回傳 `{from, to, series:[{date, intakeKcal, burnKcal, weightKg}]}`，給趨勢圖） |
| GET    | `/ai/status` | 目前 Provider / 是否載入 / 閒置秒數 |
| POST   | `/ai/unload` | 手動釋放本地模型 |

統一錯誤格式：
```json
{ "timestamp": "2026-05-25T12:34:56Z", "status": 400, "error": "VALIDATION_ERROR",
  "message": "email must be valid", "path": "/api/v1/auth/register" }
```

API 文件：
- **完整 API 草案見 [`docs/API.md`](docs/API.md)**（涵蓋所有端點的 Request／Response、欄位驗證、錯誤碼、cURL 範例）
- 後端啟動後可於 `http://localhost:8080/swagger-ui.html` 取得 springdoc 自動產生的互動文件

---

## 六、目錄結構（規劃 — Monorepo）

```
MyHealth/
├─ backend/                          # Spring Boot 應用
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/myhealth/
│     │  ├─ MyHealthApplication.java
│     │  ├─ config/         # SecurityConfig, CorsConfig, OpenApiConfig
│     │  ├─ auth/           # Controller, Service, JwtFilter, dto
│     │  ├─ user/           # User, Profile entity + Controller
│     │  ├─ workout/        # WorkoutController, Service, Entity
│     │  ├─ meal/           # MealController, Service, Entity, FileStorage
│     │  ├─ stats/
│     │  ├─ ai/             # AiProvider, OllamaProvider, IdleWatcher, AiController
│     │  └─ common/         # ApiError, GlobalExceptionHandler
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/   # Flyway: V1__init.sql, V2__... 
├─ frontend/                          # React + Vite
│  ├─ package.json
│  ├─ vite.config.ts
│  └─ src/
│     ├─ main.tsx
│     ├─ App.tsx
│     ├─ api/              # axios instance, endpoints (auth, workouts, meals, ai)
│     ├─ hooks/            # useAuth, useWorkouts, useMeals (TanStack Query)
│     ├─ pages/            # Login, Register, Dashboard, Workouts, Meals, Settings
│     ├─ components/       # UI 元件（shadcn-ui based）
│     └─ lib/              # utils, jwt store
├─ docker-compose.yml                 # postgres + redis + ollama + (option) backend/frontend
├─ .env.example
└─ README.md
```

---


## 七、環境變數

### Backend（`backend/src/main/resources/application.yml` 或環境變數）
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/myhealth
SPRING_DATASOURCE_USERNAME=myhealth
SPRING_DATASOURCE_PASSWORD=changeme

JWT_SECRET=                    # 至少 32 bytes，prod profile 不允許空值或預設值；建議 openssl rand -base64 48
JWT_ACCESS_TTL_MIN=15
JWT_REFRESH_TTL_DAYS=30

# OAuth（Phase 2 才需要設定；MVP 階段可留空）
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

AI_PROVIDER=local              # local | openai | anthropic
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_TEXT_MODEL=qwen2.5:7b
OLLAMA_VISION_MODEL=qwen2-vl:7b
AI_IDLE_TIMEOUT_SEC=300

RATE_LIMIT_BACKEND=memory       # memory | redis；正式多節點部署建議 redis
RATE_LIMIT_REGISTER_LIMIT=5     # /auth/register 每個 IP 每個 window 可嘗試次數
RATE_LIMIT_LOGIN_LIMIT=10       # /auth/login 每個 IP + email 每個 window 可嘗試次數
RATE_LIMIT_REFRESH_LIMIT=30     # /auth/refresh 每個 IP 每個 window 可嘗試次數
RATE_LIMIT_AI_LIMIT=20          # /workouts/generate、POST /meals 每個 user 每個 window 可用次數
RATE_LIMIT_WINDOW=1m
RATE_LIMIT_TRUST_FORWARDED_FOR=false # 只有後端位於可信任 reverse proxy 後方時才設 true
REDIS_URL=redis://localhost:6379
RATE_LIMIT_REDIS_KEY_PREFIX=myhealth:rate-limit
RATE_LIMIT_REDIS_FAIL_OPEN=false # Redis 不可用時是否放行請求；正式環境建議 false
RATE_LIMIT_REDIS_REQUEST_TIMEOUT=2s
RATE_LIMIT_REDIS_TTL_PADDING=10s

# 雲端 AI Provider Key（Phase 2 才需要設定；MVP 階段可留空）
OPENAI_API_KEY=
ANTHROPIC_API_KEY=

UPLOAD_DIR=./uploads
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

### Frontend（`frontend/.env`）
```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

---

## 八、本機啟動方式

> 已隨 repo 提供 Maven Wrapper（`backend/mvnw`），開發者**不需要先安裝 Maven**。Ollama 改為 docker-compose 的 `ai` profile（預設不啟動，避免拉幾 GB 模型）；沒有 Ollama 也能啟動，AI 端點會在呼叫失敗或解析失敗時回退到內建模板。

### 一鍵啟動（推薦）
```bash
scripts/dev.sh         # 自動 docker compose up -d postgres，再並行起 backend/frontend
scripts/dev.sh --ai    # 同時啟動 Ollama（profile=ai）
scripts/dev.sh --stop  # 停止 docker compose
```
Backend log → `.dev-logs/backend.log`；Frontend log → `.dev-logs/frontend.log`。

### 手動分步
```bash
# 0. 啟動 Postgres（Ollama 預設不啟動；要 AI 真實推論時加 --profile ai）
docker compose up -d postgres
# docker compose --profile ai up -d ollama && ollama pull qwen2.5:7b

# 1. 後端（用 wrapper，無須事先安裝 Maven）
cd backend
./mvnw spring-boot:run          # http://localhost:8080
# 首次啟動 Flyway 會自動套用 db/migration/*.sql

# 2. 前端
cd ../frontend
npm install
npm run dev                     # http://localhost:5173
```

Swagger UI（非 prod）：`http://localhost:8080/swagger-ui.html`

---

## 九、MVP 範圍與 Phase 2

為避免第一版範圍過大、延後上線，明確劃分 **MVP（最小可行版本）** 與 **Phase 2（後續迭代）**。MVP 完成並驗證需求後再決定 Phase 2 投入順序。

### 9.1 MVP（v1.0 目標範圍）
聚焦「本地部署、支援多帳號 + AI 規劃／記錄」核心閉環（部署目標為個人或小團體的本機環境，可註冊多個帳號各自獨立使用）：

- 帳號系統：**Email + 密碼**註冊／登入、JWT、個人檔案、必填身體數據
- 運動：分類選擇、AI 產生當日菜單、打卡、當日／本週列表
- 飲食：**文字輸入** + **拍照上傳** 至少擇一可用、AI 熱量估算、AI 飲食建議
- 儀表板：當日熱量收支、近 7 天體重／攝取趨勢
- AI Gateway：**本地 Ollama Provider** + Idle Watcher 自動 unload
- 前端：深淺色模式、繁中介面、響應式 RWD
- 部署：本機 `docker compose`（Postgres + Ollama + 後端 + 前端）

### 9.2 Phase 2（MVP 上線後依需求排程）
- **帳號**：Google OAuth2、密碼重設（Email）、多裝置 Session 撤銷 UI、Email 驗證
- **AI Provider**：OpenAI / Anthropic / Gemini 雲端 Provider、Provider 切換 UI
- **儲存**：S3 相容物件儲存（取代本機 `uploads/`）、CDN
- **前端**：英文介面 i18n、PWA 離線快取、影片動作示範
- **健身**：週／月菜單規劃、教練模式（教練派發菜單）、訓練量分析
- **飲食**：常用餐點收藏、條碼掃描查詢食材庫
- **整合**：穿戴裝置匯入（Apple Health / Google Fit）、體脂計藍牙同步
- **平台**：行動 App（React Native 共用 REST API）
- **營運**：API 速率限制（Bucket4j）、稽核日誌、Prometheus / Grafana 監控
- **管理**：Admin 後台（系統健康度、AI 模型管理；不讀取他人健康資料）

### 9.3 不在範圍（Out of Scope）
- 醫療診斷、處方藥物建議、疾病評估
- 兒童（< 13 歲）與孕期專用菜單
- 線上付費／訂閱制
- 社群動態牆、好友系統（教練模式除外）

### 9.4 核心使用流程（MVP Happy Path）
1. **註冊** → 填妥必填身體資料（性別／身高／體重）→ 自動登入並建立第一筆 `body_measurements`
2. **首頁儀表板** → 顯示「今日尚無菜單」與「今日尚無飲食紀錄」兩個入口
3. **運動規劃** → 選擇分類（如「腹肌」）→ 點「AI 產生菜單」→ 等待 ≤ 10 秒 → 取得菜單 → 完成後打卡
4. **飲食紀錄** → 拍照 或 輸入「雞胸肉沙拉」→ AI 回傳熱量與三大營養素 → 確認儲存（可手動修正）
5. **回首頁** → 看見今日攝取 / 消耗 / 淨熱量，以及近 7 天體重趨勢
6. **設定** → 切換深淺色 → 更新體重 → 自動寫入趨勢資料

### 9.5 MVP 驗收標準（Definition of Done）
功能、品質與體驗三軸全部達成方可宣告 v1.0 完成：

**功能**
- [ ] 註冊／登入／登出可正常運作，必填欄位驗證生效
- [ ] 9 種運動分類皆可由 AI 產生包含 ≥ 3 個動作的菜單
- [ ] 拍照與文字輸入兩種飲食記錄路徑皆可走通
- [ ] 儀表板顯示當日熱量收支與近 7 天體重趨勢
- [ ] AI 閒置超過 `AI_IDLE_TIMEOUT_SEC` 後自動 unload，下次請求 lazy load 成功
- [ ] `DELETE /me` 後資料庫內該使用者所有資料皆被清除（含 body_measurements、meals、workout_plans）

**品質**
- [ ] 後端單元測試覆蓋率 ≥ 60%（Service 層 ≥ 70%）
- [ ] 主要端點具整合測試（auth、workouts/generate、meals POST、stats/daily）
- [ ] OpenAPI 文件可自動產生且與 `docs/API.md` 一致
- [ ] Flyway migration 可從空 DB 套用至最新版且 idempotent

**體驗 / 非功能**
- [ ] 一般頁面 TTFB < 500 ms（不含 AI 端點）
- [ ] AI 端點在已 warm 模型下 p50 < 8 秒（菜單生成）、p50 < 12 秒（食物辨識）
- [ ] 深淺色切換不閃白，可在所有頁面正確套用
- [ ] 註冊頁顯示並要求勾選「非醫療建議聲明」
- [ ] AI 結果旁固定顯示「估算」「僅供參考」標籤

### 9.6 產品成功指標（多數需 Phase 2 事件紀錄系統支援）
非硬性目標，作為 Phase 2 投入優先順序依據。**MVP 僅承諾蒐集 ✅ 標記項**；其餘指標需先完成 9.7「事件紀錄」基礎建設才能準確量測。

| 指標 | 目標 | 衡量方式 | MVP 可量測 |
|---|---|---|---|
| 啟用率（Activation） | 註冊後 24 小時內至少完成 1 次菜單生成 + 1 次飲食記錄 | 事件埋點（Phase 2） | ✗ |
| 留存（W1 Retention） | 註冊後第 7 天回訪比例 ≥ 30% | 第 7 天是否有 meals / workout_plans / body_measurements 新增 | ✅（以既有表近似） |
| AI 結果採用率 | 菜單／飲食 AI 回應「不採用」回報 < 20% | 「不採用」按鈕事件（Phase 2） | ✗ |
| 平均功耗 | 閒置 > 5 分鐘後本地 AI 不占用 GPU/RAM | OS 監控 + `/ai/status.loaded` 輪詢 | ✅ |

### 9.7 事件紀錄（Phase 2）
為支援上述啟用率、AI 採用率等指標，預計於 Phase 2 加入事件紀錄機制：

- **方案 A（優先）**：自有 `events` 表（`user_id`、`event_type`、`payload jsonb`、`occurred_at`），後端 Service 層在關鍵動作埋點（`workout.generated`、`workout.completed`、`meal.created`、`ai.rejected`…）；前端透過 `POST /api/v1/events` 上報純前端事件（頁面進入、按鈕點擊）。
- **方案 B**：接外部 analytics（PostHog 自架版），保留資料本地化。
- **隱私原則**：事件僅記錄 `event_type` 與必要的數值欄位，**不寫入飲食描述、照片內容、個人身體數據明細**；遵循 §10.1 最小化原則。
- **MVP 階段**：不導入埋點，避免拖延上線；以資料庫既有資料（`users.created_at`、`meals`、`workout_plans`、`body_measurements`、`/ai/status`）做粗略估算。

---

## 十、產品風險與免責

### 10.1 健康資料隱私
- **資料分類**：身高、體重、體脂率、飲食照片屬於敏感個人資料，依《個資法》第 6 條相關規範處理。
- **儲存與傳輸**：
  - 密碼使用 BCrypt 雜湊，從不明碼儲存。
  - JWT Secret 至少 32 bytes；`prod` profile 會拒絕空值、預設值或太短的 secret；正式環境強制 HTTPS。
  - `prod` profile 會關閉 springdoc Swagger/OpenAPI，避免公開暴露互動 API 文件。
  - Rate limit 預設不信任 `X-Forwarded-For`，除非服務只接收可信任 reverse proxy 轉發。
  - 餐點照片預設僅本機儲存；若啟用雲端儲存須加密。
- **資料主權**：使用者可透過 `DELETE /me` 一鍵刪除帳號與所有關聯資料（schema 設 `ON DELETE CASCADE`）；提供「匯出我的資料」端點作為 Phase 2 規劃。
- **AI 與資料外流**：MVP 預設使用本地 Ollama，使用者資料**不離開本機**；切換至雲端 Provider（OpenAI／Anthropic）時，UI 必須明確警示「將傳送至第三方」。
- **最小化原則**：日誌不寫入密碼、Token、餐點照片內容；錯誤回報遮罩 Email。
- **法遵備援**：未來若公開上線，需補上隱私權政策、Cookie 政策、使用者同意流程，並指定資料保護聯絡窗口。

### 10.2 AI 熱量估算與規劃誤差
- **熱量估算**：VLM 對食物份量與內容物的辨識存在誤差，**±20–30% 為常見區間**。UI 須以「估算」「approx.」字樣呈現，避免暗示精準量測。
- **菜單適配性**：AI 不知道使用者既往傷病、藥物影響、過敏原；產生的菜單可能不適合所有人。設定頁應提供「禁忌動作」「過敏食材」輸入欄位（Phase 2），並寫入 system prompt。
- **可解釋性**：所有 AI 回應顯示「由本地 AI 產生，僅供參考」標籤；提供「不採用」按鈕並可回報問題。
- **資料漂移**：模型升級或切換 Provider 後相同輸入可能得到差異結果；建議保留歷史紀錄不覆寫。
- **退場機制**：當 AI 不可用（`503 AI_UNAVAILABLE`）時，前端允許使用者**手動輸入**熱量與菜單，避免功能完全失效。

### 10.3 非醫療建議聲明（須在註冊頁與首次使用顯示同意勾選）
> MyHealth 提供之運動菜單、熱量估算與飲食建議由 AI 模型基於使用者輸入產生，**僅供一般健康與健身參考**，不構成醫療診斷、營養處方或運動處方，亦無法取代合格醫師、營養師或運動專業人員之專業判斷。
>
> 若您具有以下任一情況，請務必先諮詢專業人員後再使用本平台：心血管疾病、糖尿病、慢性病、孕期或哺乳期、傷後復健、飲食障礙、未滿 18 歲等。
>
> 使用者因採信本平台建議所致之任何健康、人身或財產損失，開發者不負法律責任。

### 10.4 其他營運風險（待 Phase 2 評估）
| 風險 | 緩解策略 |
|---|---|
| 本地 AI 硬體門檻高（需 GPU/大 RAM） | 提供雲端 Provider 切換、模型可選輕量版本（`qwen2.5:3b`） |
| 圖片儲存空間膨脹 | 自動壓縮、超過閾值移至冷儲存或自動刪除 90 天前圖片 |
| AI Prompt Injection（使用者描述含惡意指令） | 後端對 user-supplied text 做模板隔離、限制輸出 schema（JSON Mode） |
| 多使用者併發拖累 AI | 排隊 + 單一模型 instance + 顯示等待人數 |
