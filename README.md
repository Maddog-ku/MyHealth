# MyHealth — AI 健身與飲食管理平台

一個把「每日運動規劃」與「三餐飲食紀錄」放在一起的個人健康 Web 應用。AI 在本機跑，照片、餐點描述、體重資料都不離開你的電腦。

> ⚠️ **免責聲明**：MyHealth 提供的運動建議、熱量估算、飲食建議是 AI 模型基於你輸入的資料產生的，**僅供一般健康參考**，不構成醫療診斷、營養處方或運動處方，也無法取代合格醫師、營養師、運動專業人員的判斷。詳見文末「隱私與風險」。

---

## 它能幫你做什麼

### 運動規劃
- 選擇分類（腹肌 / 練腿 / 練胸 / 練背 / 手臂 / 臀 / 有氧 / 全身）、訓練時長、強度
- AI 產生 3–6 個動作，每個動作包含組數、次數、休息秒數、預估卡路里、動作要點與替代方案
- 完成後一鍵打卡，自動累進當日消耗

### 飲食紀錄
- 用**文字描述**（「雞胸肉沙拉 150g 配半碗糙米飯」）或**拍照上傳**記錄餐點
- AI 估算每樣食物的份量、熱量、蛋白／脂肪／碳水，並給一句飲食建議
- 估算誤差約 ±20–30%，UI 上會顯示「估算」標籤，可手動修正

### 儀表板
- 今日攝取、消耗、淨熱量
- 體重歷史趨勢（自動讀取每次量測紀錄）
- AI 引擎狀態（是否已載入、何時釋放）

### 設定
- 深淺色主題、隨系統切換
- 個人生理資料隨時更新，新數據自動加入趨勢

---

## 快速開始

需要 **Java 21+**、**Node 20+**、**Docker Desktop**。不需要先裝 Maven（已內附 wrapper）。

```bash
git clone https://github.com/Maddog-ku/MyHealth.git
cd MyHealth
scripts/dev.sh
```

`scripts/dev.sh` 會自動：
1. `docker compose up -d postgres`（Postgres 在 5433，避開本機可能有的 brew Postgres）
2. 用 `./mvnw spring-boot:run` 啟動後端到 8080
3. 用 `npm run dev` 啟動前端到 5173

打開 http://localhost:5173 ，註冊一個帳號開始用。

### 要讓 AI 真的跑起來
本機需要先裝 [Ollama](https://ollama.ai) 並 pull 一個模型（預設 `gemma4:e4b`）：

```bash
ollama pull gemma4:e4b
```

或者用 docker-compose 啟動 Ollama 容器：

```bash
scripts/dev.sh --ai   # 同時啟動 Ollama profile
docker compose exec ollama ollama pull gemma4:e4b
```

不裝 Ollama 也能跑：AI 端點會自動回退到內建模板（運動可用、餐點會請你重輸入）。

### 收工
```bash
scripts/dev.sh --stop   # 停 docker-compose 服務
```

---

## 技術概覽

- **前端**：React + Vite + TypeScript、Tailwind、shadcn 風格元件（Radix）、TanStack Query、React Router、Recharts
- **後端**：Java 21 + Spring Boot 3、Spring Security + JWT、Spring Data JPA + Hibernate、Flyway、Bucket4j 限流
- **資料庫**：PostgreSQL 16（schema 由 Flyway 管理，見 `backend/src/main/resources/db/migration/`）
- **AI**：本地 Ollama；以 `AiProvider` 介面抽象，未來可切 OpenAI / Anthropic
- **部署**：本機 `docker compose`（Postgres + 可選 Redis + 可選 Ollama）

### 隱私架構亮點
- AI 預設只跑本機，照片與餐點描述**不離開你的電腦**
- 每次 AI 推論完模型立即從記憶體釋放（gemma4:e4b 約 10 GB），不會永遠常駐
- 密碼用 BCrypt，JWT secret 在 prod profile 啟動時會驗證強度
- `DELETE /me` 連動刪除使用者所有資料（運動、餐點、體重、refresh token）

完整 REST API 參考：[`docs/API.md`](docs/API.md)
後端啟動後的互動 API 文件（非 prod）：http://localhost:8080/swagger-ui.html

---

## MVP 範圍

### v1.0 包含什麼
- Email + 密碼帳號系統、JWT、refresh token rotation
- 9 種運動分類、AI 產生菜單、完成打卡
- 文字 + 照片飲食紀錄、AI 熱量與營養估算
- 儀表板（當日收支 + 7 天體重趨勢）
- 本地 Ollama 整合 + 自動釋放記憶體
- 深淺色模式、繁中介面、響應式
- 限流（auth 端點 + AI 端點）

### Phase 2 計畫
- Google OAuth2 / 密碼重設 / 多裝置 session 管理
- 雲端 AI Provider（OpenAI / Anthropic）切換
- 週／月菜單規劃、訓練量分析
- 常用餐點收藏、條碼掃描
- 穿戴裝置匯入（Apple Health / Google Fit）
- 行動 App、英文介面、PWA 離線

### 明確不做
- 醫療診斷、處方藥物建議、疾病評估
- 兒童（< 13 歲）與孕期專用菜單
- 付費／訂閱、社群動態牆

---

## 隱私與風險

### 你的資料去哪了
- **預設全部留在本機**：Postgres、Ollama、上傳照片都跑在你自己的機器
- **切換雲端 AI 時 UI 會明確警示**：往 OpenAI / Anthropic 送請求等於把餐點描述 / 照片送出去
- **不寫敏感欄位到 log**：密碼、token、照片內容都被排除；錯誤訊息只遮罩 Email
- **可一鍵清空**：`DELETE /me` 或從設定頁刪除帳號，schema 設 `ON DELETE CASCADE`

### AI 結果有多準
- 熱量估算誤差通常在 **±20–30%**；UI 上會顯示「估算」標籤
- AI 不知道你的傷病、藥物、過敏；任何建議都該以你身體實際感受為主
- 模型升級後同樣輸入可能給不同結果；歷史紀錄不會被改寫
- AI 不可用時可以手動輸入熱量與菜單

### 非醫療建議聲明
> MyHealth 提供的運動菜單、熱量估算與飲食建議由 AI 模型基於你輸入的資料產生，**僅供一般健康與健身參考**，不構成醫療診斷、營養處方或運動處方。
>
> 若你有以下任一情況，請先諮詢專業人員：心血管疾病、糖尿病、慢性病、孕期或哺乳期、傷後復健、飲食障礙、未滿 18 歲。
>
> 因採信本平台建議所致之任何健康、人身或財產損失，開發者不負法律責任。

---

## 想參與開發

```bash
cd backend && ./mvnw test        # 後端測試
cd frontend && npm run build     # 前端型別檢查 + 構建
cd frontend && npx playwright test   # 前端 e2e
```

CI（GitHub Actions）會在每次 push / PR 上自動跑這三件事，狀態在 [Actions 頁面](https://github.com/Maddog-ku/MyHealth/actions)。

PR / Issue 歡迎。
