# reMockable Backend — API Interface（給前端）

| 項目 | 值 |
|---|---|
| 文件版本 | v0.1.0 |
| 對應 Spec | reMockable MVP SPEC v0.8.0 |
| 後端技術棧 | Java 21 / Spring Boot 3.x / AWS RDS |
| Base URL | `/api` |
| 機器可讀規格 | [`openapi.yaml`](./openapi.yaml)（可直接匯入 Postman / Swagger UI / 產生 client） |

> **這份文件是前後端的契約來源。**前端只依賴這裡定義的欄位名稱、enum 值與 error code，
> 不依賴後端的 prompt 文字、模型名稱或資料表結構。

---

## 1. 通用約定

### 1.1 認證

所有 endpoint 都接受 `Authorization` header：

```
Authorization: Bearer <access_token>
```

| Phase | 狀態 | 行為 |
|---|---|---|
| **P1A / P1B（9/10）** | **optional** | 後端忽略此 header。以無帳號流程驗證（見 Phase Overview P1A 註記） |
| **P2A / P2B（9/17）** | optional | 同上 |
| **P2C（9/17）起** | **required** | 缺少或無效回 `401 ACCESS_DENIED` |

**前端請從第一天就把這個 header 帶上（有 token 就帶，沒有就省略）**，P2C 上線時不需要改任何呼叫程式碼。

### 1.2 共用 Request Headers

| Header | 必要性 | 說明 |
|---|---|---|
| `Content-Type` | 視 endpoint | `application/json` 或 `multipart/form-data` |
| `Authorization` | 見 1.1 | `Bearer <token>` |
| `Idempotency-Key` | **建議** | UUID v4。所有 `POST` 都支援。相同 key 在 24 小時內重送會回傳**第一次的結果**，不會重複建立資源或重複扣模型成本 |
| `X-Request-Id` | 選填 | 前端自帶的追蹤 id；未帶則後端產生。一律回寫在 response 的 `request_id` |

> **為什麼一定要帶 `Idempotency-Key`：** Spec §13.3 要求「使用者重新整理頁面不應重複生成題目或重複扣模型成本」、
> 「具 idempotency key 的請求不可重複建立同一個 Attempt」。這是驗收條件，不是最佳實務建議。

### 1.3 統一錯誤格式

任何非 2xx 回應都是這個形狀：

```json
{
  "status": "failed",
  "error": {
    "code": "JOB_PAGE_UNREADABLE",
    "message_key": "job_input_try_paste_text",
    "retryable": true,
    "meta": { "field": "value" }
  },
  "request_id": "req_01J8X..."
}
```

| 欄位 | 說明 |
|---|---|
| `code` | 穩定機器碼。前端用它決定行為（回到哪個欄位、要不要顯示重試按鈕） |
| `message_key` | 文案索引鍵。**後端不回中文句子**，中文文案由前端依 spec §12 管理（Spec 明訂：「API 只回傳穩定的 error.code 與 message_key；中文前端文案由產品與設計共同管理」） |
| `retryable` | `true` 時前端應提供「請稍候再試」與重試入口 |
| `meta` | 選填，補充資訊（例如超過的位元組數） |

完整 error code 表見 [§4](#4-error-code-全表)。

### 1.4 非同步 Job 契約（所有 AI 呼叫共用）

生題約 15–40 秒、分析約 20–45 秒，都超過一般 HTTP timeout，因此**所有會呼叫 AI 模型的操作都走同一套非同步流程**。前端只要寫一次輪詢邏輯。

```
POST <AI endpoint>
  ↓
202 Accepted
{ "job_id": "job_01J8X...", "status": "QUEUED", "poll_after_ms": 1500 }
  ↓ 依 poll_after_ms 輪詢
GET /api/jobs/{job_id}
```

**Job 物件：**

```json
{
  "job_id": "job_01J8X...",
  "job_type": "QUESTION_SET_GENERATION",
  "status": "RUNNING",
  "progress": 0.4,
  "elapsed_ms": 12034,
  "poll_after_ms": 2000,
  "result": null,
  "error": null,
  "created_at": "2026-09-10T02:11:07Z",
  "finished_at": null
}
```

| `status` | 說明 | `result` | `error` |
|---|---|---|---|
| `QUEUED` | 已排入佇列，尚未開始 | `null` | `null` |
| `RUNNING` | 模型處理中 | `null` | `null` |
| `READY` | 完成 | 有值（形狀依 `job_type`） | `null` |
| `FAILED` | 失敗 | `null` | 有值（同 §1.3 的 `error`） |

**輪詢規則（前端請照做）：**

1. 依 response 的 `poll_after_ms` 決定下次輪詢時間，**不要固定 1 秒打**
2. 後端會採退避策略回傳：`1500ms → 2000ms → 3000ms → 5000ms`（上限 5 秒）
3. `status` 進入 `READY` 或 `FAILED` 即停止輪詢
4. **總輪詢上限 180 秒**；超過視同逾時，顯示「請稍候再試」並提供重試
5. Job 結果保留 **24 小時**，重整頁面後仍可用同一個 `job_id` 取回

**失敗不消耗額度：** 新增題目的每日配額只在 job **成功寫入題目時**才 +1。生成失敗不扣額度（Spec 4.3.4.5）。

### 1.5 Enum 定義

前端與後端共用這些值，**大小寫敏感**。

| Enum | 值 | 對應中文（前端顯示用） |
|---|---|---|
| `category`（題型） | `INTRODUCTION` | 自我介紹 |
| | `BEHAVIORAL` | 行為問題 |
| | `TECHNICAL` | 技術問題 |
| | `CULTURAL_FIT` | 文化契合 |
| `difficulty`（難度） | `EASY` / `MEDIUM` / `HARD` | 簡單 / 中等 / 困難 |
| `input_mode`（回答模式） | `TEXT` | 文字（P1B） |
| | `VOICE` | 語音（P2B） |
| `fit_state`（燈號） | `GREEN` / `YELLOW` / `RED` | 綠 / 黃 / 紅 |
| `overall_state` | `STRONG` / `ACCEPTABLE` / `NEEDS_IMPROVEMENT` | — |
| `job_status` | `QUEUED` / `RUNNING` / `READY` / `FAILED` | — |
| `input_type`（JD 來源） | `TEXT` / `URL` | 貼上文字 / 職缺連結 |
| `analysis_type` | `FIRST` / `COMPARISON` | 首次分析 / 第二次比較分析 |
| `reference_answer_source` | `USER_REQUESTED` / `FROM_ANALYSIS` | 使用者點擊生成 / 分析產出 |

**燈號門檻**（Spec D-025、AC-13，前端固定文案，後端不回這段）：
綠燈 90 分以上 ／ 黃燈 60–89 分 ／ 紅燈未滿 60 分。

> ⚠️ **API 不回傳 numeric score。** Spec D-025 明訂「Server 保存 numeric score，UI 不顯示數字」。
> 分數存在資料庫供模型評估使用，但**不出現在任何 response**，避免前端誤顯示。

### 1.6 JD 來源支援範圍（本輪縮減）

| 來源 | 本輪狀態 |
|---|---|
| 貼上 JD 文字 | ✅ **P1A 支援** |
| CakeResume 職缺連結 | ✅ **P1A 支援**（單純爬蟲即可） |
| 104 職缺連結 | ❌ 本輪不做 |
| LinkedIn 職缺連結 | ❌ 本輪不做 |

前端的 supporting text 目前寫「可貼上 104、Cake 或 LinkedIn 連結」，**需同步改為只提 Cake**，
否則使用者貼 104 連結會拿到 `JOB_URL_NOT_SUPPORTED`。此項已列入 PM 待確認清單。

---

## 2. 畫面 ↔ API 對照

| Spec 畫面 | 動作 | API |
|---|---|---|
| 4.1 建立面試資料 | 送出職缺資訊 | `POST /api/job-postings/parse` → 輪詢 job |
| | 上傳履歷 | `POST /api/resumes` |
| | 點「下一步」建立 Mock Set | `POST /api/mocksets` |
| 4.2 選擇題型 | 點「下一步」 | `GET /api/mocksets/{id}/question-sets?category=X`（先查沿用）<br>不存在再 `POST /api/mocksets/{id}/question-sets` → 輪詢 job |
| 4.3 選擇題目 | 進入頁面 | `GET /api/question-sets/{id}` |
| | 點「新增題目（x/3）」 | `POST /api/question-sets/{id}/questions` → 輪詢 job |
| 4.4 練習回答 | 進入頁面 | `GET /api/questions/{id}` |
| | 點「生成參考答案」 | `POST /api/questions/{id}/reference-answer` → 輪詢 job |
| | 點「參考前次答案」 | `GET /api/questions/{id}/reference-answer`（**永不觸發生成**） |
| | 點「確認並開始分析」 | `POST /api/questions/{id}/attempts` → `POST /api/attempts/{id}/analysis` → 輪詢 job |
| 4.5 分析結果 | 進入頁面 | `GET /api/attempts/{id}/analysis` |
| | 顯示「前一次回答」 | `GET /api/questions/{id}/attempts` |
| 4.0 首頁導向 | 判斷去 4.1 還是 4.2 | `GET /api/me`（P2C）／ `GET /api/mocksets`（P1A） |
| 4.6 登入 / 登出 | 寄驗證信 | `POST /api/auth/email/request`（P2C） |
| | 點驗證連結 | `POST /api/auth/email/verify`（P2C） |
| 全站 | 埋點 | `POST /api/events` |

---

## 3. Endpoints

### 3.0 Meta

#### `GET /api/health`

服務與 provider 就緒狀態。無需認證。

**200**
```json
{
  "status": "ok",
  "service": "remockable-api",
  "version": "0.1.0",
  "request_id": "req_01J8X...",
  "uptime_seconds": 8241,
  "providers": {
    "llm":  { "configured": true },
    "stt":  { "configured": true }
  },
  "limits": {
    "max_resume_bytes": 10000000,
    "max_audio_bytes": 25000000,
    "max_answer_seconds": 90,
    "min_answer_chars": 100,
    "max_answer_chars": 2000,
    "daily_add_question_limit": 3,
    "max_questions_per_category": 20
  }
}
```

> `limits` 是**前端驗證規則的唯一來源**。前端請在啟動時讀一次，不要把數字寫死在程式碼裡。

---

#### `GET /api/jobs/{jobId}`

輪詢任何非同步工作。形狀見 §1.4。

**200** — Job 物件
**404** `NOT_FOUND` — job 不存在或已超過 24 小時保留期

---

### 3.1 Auth（P2C，9/17）

> 這一段在 P1A/P1B **不會被呼叫**，但介面先定義好，前端可先把登入彈窗的串接位置留出來。

#### `POST /api/auth/email/request`

**Request**
```json
{ "email": "you@example.com" }
```

**200**
```json
{ "status": "sent", "email": "you@example.com", "expires_in": 900 }
```

**400** `VALIDATION_ERROR` — email 格式不符（前端也要先驗 `x@x.x`）
**429** `RATE_LIMITED` — 同一 email 60 秒內重複請求

---

#### `POST /api/auth/email/verify`

**Request**
```json
{ "token": "<驗證信連結帶的 token>" }
```

**200**
```json
{
  "access_token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "expires_in": 2592000,
  "user": { "id": "usr_...", "email": "you@example.com", "display_name": "you", "avatar_letter": "Y" }
}
```

**401** `ACCESS_DENIED` — token 無效或已使用
**410** `AUTH_TOKEN_EXPIRED` — token 逾期（15 分鐘）

---

#### `POST /api/auth/google`（P3）

**Request**
```json
{ "id_token": "<Google OAuth id_token>" }
```
**200** — 同 `email/verify`

---

#### `GET /api/me`

**200**
```json
{
  "user": { "id": "usr_...", "email": "you@example.com", "display_name": "you", "avatar_letter": "Y" },
  "has_mockset": true,
  "default_mockset_id": "ms_01J8X..."
}
```

> **首頁導向邏輯（Spec 4.0.4.4 / M-17）**：`has_mockset = false` → 導向 4.1 建立面試資料；
> `true` → 導向 4.2 選擇題型，並帶 `default_mockset_id`。

**401** `ACCESS_DENIED`

---

#### `POST /api/auth/logout`

**204** — 無回應內容。前端清除本地 token。

---

### 3.2 職缺資訊解析（4.1）

#### `POST /api/job-postings/parse`

**Phase**：P1A ｜ **非同步**

**Request**
```json
{ "input_type": "TEXT", "value": "We are looking for a Product Manager..." }
```
或
```json
{ "input_type": "URL", "value": "https://www.cake.me/jobs/xxxx" }
```

> 後端也會自行判斷 `value` 是否為有效 http/https URL（Spec 4.1.4.2）。
> 若 `input_type` 與內容不符，以**實際內容**為準，並在 job result 回傳實際使用的 `input_type`。

**202**
```json
{ "job_id": "job_01J8X...", "status": "QUEUED", "poll_after_ms": 1500 }
```

**Job result（`job_type: "JOB_POSTING_PARSE"`）**
```json
{
  "job_posting_id": "jp_01J8X...",
  "input_type": "TEXT",
  "source_url": null,
  "extraction": {
    "job_title": "Product Manager",
    "company_name": "Northstar Labs",
    "industry": "SaaS / B2B 軟體",
    "experience": "3 年以上產品管理經驗",
    "job_description": "負責產品策略、使用者研究與跨團隊協作，推動 SaaS 產品從規劃到上線。",
    "requirements": null
  },
  "extracted_field_count": 5,
  "missing_fields": ["requirements"]
}
```

> **無法擷取的欄位一律回 `null`，不回空字串、不回預設值、不編造內容**（Spec D-021、AC-02）。
> 前端把 `null` 顯示為 `--`。`extracted_field_count` 直接用在「已讀取 x 個欄位」。

**Job error**
| code | 情境 | 前端恢復 |
|---|---|---|
| `JOB_PAGE_UNREADABLE` | URL 讀不到 | 回到職缺資訊欄位，顯示「讀取連結失敗，請重新上傳」 |
| `JOB_URL_NOT_SUPPORTED` | 非 CakeResume 網域（104 / LinkedIn） | 提示改貼文字 |
| `JOB_TEXT_TOO_SHORT` | 文字過短，不足以解析 | 回到欄位，提示補充內容 |
| `MODEL_UNAVAILABLE` / `MODEL_OUTPUT_INVALID` | 模型異常 | 顯示「請稍候再試」＋重試 |

---

#### `GET /api/job-postings/{id}`

重新取得解析結果（重整頁面後用）。**200** 回傳與 job result 相同的物件。

---

### 3.3 履歷上傳（4.1）

#### `POST /api/resumes`

**Phase**：P1A ｜ **同步**（PDF 文字抽取不呼叫 AI 模型，通常 < 2 秒）

**Request** — `multipart/form-data`

| 欄位 | 型別 | 說明 |
|---|---|---|
| `file` | binary | **僅接受 PDF**，≤ 10 MB |

**201**
```json
{
  "resume_id": "res_01J8X...",
  "filename": "andre-hung-resume-2026.pdf",
  "byte_size": 842113,
  "page_count": 2,
  "extracted_char_count": 4821,
  "status": "READY"
}
```

**錯誤**
| code | 情境 |
|---|---|
| `RESUME_UNSUPPORTED_TYPE` (415) | 非 PDF |
| `UPLOAD_TOO_LARGE` (413) | 超過 10 MB |
| `RESUME_EXTRACTION_FAILED` (422) | PDF 無法解析（例如純圖片掃描檔） |
| `RESUME_EMPTY` (422) | 解析後沒有可用文字 |

---

### 3.4 Mock Set（4.1）

#### `POST /api/mocksets`

**Phase**：P1A ｜ **同步**（只是凍結已解析好的資料，不呼叫模型）

**Request**
```json
{
  "name": "PM (Northstar Labs)",
  "job_posting_id": "jp_01J8X...",
  "resume_id": "res_01J8X..."
}
```

| 欄位 | 規則 |
|---|---|
| `name` | 必填，**至少 2 個字元**（Spec 4.1.4.1）。建立後不可修改 |
| `job_posting_id` | 必填，來自 §3.2 |
| `resume_id` | 必填，來自 §3.3 |

**201**
```json
{
  "id": "ms_01J8X...",
  "name": "PM (Northstar Labs)",
  "status": "READY",
  "job_posting": {
    "id": "jp_01J8X...",
    "job_title": "Product Manager",
    "company_name": "Northstar Labs",
    "industry": "SaaS / B2B 軟體",
    "experience": "3 年以上產品管理經驗",
    "job_description": "負責產品策略...",
    "requirements": null,
    "source_url": null
  },
  "resume": { "id": "res_01J8X...", "filename": "andre-hung-resume-2026.pdf" },
  "created_at": "2026-09-10T02:11:07Z"
}
```

> **建立後 JD 與履歷即凍結**（Spec M-04、D-003）。沒有 `PATCH /mocksets/{id}` —— 要換資料只能建新的 Mock Set。

**錯誤**
| code | 情境 |
|---|---|
| `VALIDATION_ERROR` (400) | `name` 少於 2 字元 |
| `MOCKSET_SOURCE_MISSING` (400) | `job_posting_id` 或 `resume_id` 缺少／不存在 |
| `MOCKSET_LIMIT_REACHED` (409) | 超過 Mock Set 數量上限（**P3 才啟用，數量待 PM 確認**） |

---

#### `GET /api/mocksets`

**200**
```json
{ "mocksets": [ { "id": "ms_...", "name": "PM (Northstar Labs)", "status": "READY", "created_at": "..." } ] }
```

> P1A 無帳號流程下，前端請把 `mockset_id` 存在 `localStorage`，重整頁面後用 `GET /api/mocksets/{id}` 取回。
> P2C 登入上線後改由 `GET /api/me` 的 `default_mockset_id` 提供。

---

#### `GET /api/mocksets/{id}`

**200** — 同 `POST /api/mocksets` 的 201 形狀。
**404** `NOT_FOUND`

---

#### `DELETE /api/mocksets/{id}`（P3）

**204**

---

### 3.5 題型與題目（4.2 / 4.3）

#### `GET /api/mocksets/{id}/question-sets?category={category}`

**Phase**：P1A ｜ **同步** ｜ 對應 **M-07 沿用題目**

使用者在 4.2 點「下一步」時，**前端請先呼叫這支**。已有題目就直接進 4.3，跳過 loading 畫面，也不會重複扣模型成本。

**200**（已存在）
```json
{ "exists": true, "question_set": { "id": "qs_01J8X...", "category": "INTRODUCTION", "question_count": 5 } }
```

**200**（不存在）
```json
{ "exists": false, "question_set": null }
```

> 刻意用 `200 + exists:false` 而非 `404`，讓前端不必把「還沒生成」當成錯誤處理。

---

#### `POST /api/mocksets/{id}/question-sets`

**Phase**：P1A ｜ **非同步**（生成 5 題，約 15–40 秒）

**Request**
```json
{ "category": "INTRODUCTION" }
```

**202** — Job envelope
**200** — 若該 Mock Set + 題型已有題目，**直接回既有結果，不重新生成**（Spec M-07）：
```json
{ "status": "READY", "question_set_id": "qs_01J8X...", "reused": true }
```

**Job result（`job_type: "QUESTION_SET_GENERATION"`）**
```json
{
  "question_set": {
    "id": "qs_01J8X...",
    "mockset_id": "ms_01J8X...",
    "category": "INTRODUCTION",
    "status": "READY",
    "question_count": 5
  },
  "quota": { "used": 0, "limit": 3, "remaining": 3, "reset_at": "2026-09-11T00:00:00+08:00" },
  "questions": [
    {
      "id": "q_01J8X...",
      "position": 1,
      "question_text": "Why are you interested in this Product Manager role?",
      "question_translation": "你為什麼對這個產品經理職位有興趣？",
      "difficulty": "EASY",
      "origin": "INITIAL",
      "attempt_count": 0,
      "has_reference_answer": false
    }
  ]
}
```

> `question_translation` 是**繁體中文翻譯**，顯示在題目卡的 supporting text（Spec D-031、AC-11）。
> `position` 從 1 開始；前端顯示為兩位數編號 `01`、`02`……

**Job error**
| code | 情境 |
|---|---|
| `QUESTION_GENERATION_INVALID` | 模型輸出不合 schema（後端已自動 repair retry 一次） |
| `QUESTION_GENERATION_BLOCKED` | JD 或履歷資訊不足以生成個人化題目（Spec D-022） |
| `MODEL_QUOTA_EXCEEDED` | 模型額度用盡 |

失敗時前端回到 4.2 選擇題型頁，保留原本選取的題型，顯示「題目生成失敗，請稍候再試」，按鈕恢復 Enabled。

---

#### `GET /api/question-sets/{id}`

**Phase**：P1A ｜ **同步**

**200** — 與上方 job result 相同形狀（`question_set` + `quota` + `questions[]`）。

---

#### `POST /api/question-sets/{id}/questions`

**Phase**：P2B ｜ **非同步** ｜ 對應 **M-18 新增一題**

每次新增 **1 題**。每日上限 **3 題**，以「Mock Set＋題型」為單位計算。

**Request** — 無 body（或 `{}`）

**202** — Job envelope

**Job result（`job_type: "QUESTION_ADDITION"`）**
```json
{
  "question": {
    "id": "q_01J8Y...",
    "position": 6,
    "question_text": "Tell me about a product decision you regret.",
    "question_translation": "說說一個你後悔的產品決策。",
    "difficulty": "HARD",
    "origin": "ADDED",
    "attempt_count": 0,
    "has_reference_answer": false
  },
  "quota": { "used": 1, "limit": 3, "remaining": 2, "reset_at": "2026-09-11T00:00:00+08:00" }
}
```

**錯誤**
| code | 情境 | 前端 |
|---|---|---|
| `ADD_QUESTION_LIMIT_REACHED` (429) | 當日已成功新增 3 題 | 按鈕 Disabled，顯示「已達本日上限」 |
| `QUESTION_LIMIT_REACHED` (409) | 該題型已達 20 題上限（Spec D-011）| **文案待 PM 補**，見待確認清單 |
| `QUESTION_GENERATION_INVALID` | 生成失敗 | 按鈕恢復 Enabled，顯示「題目新增失敗，請稍候再試」。**不消耗當日額度** |

> 新題目接在既有列表**下方**，既有題目與目前選取狀態保留（Spec 4.3.4.5）。

---

#### `GET /api/questions/{id}`

**200**
```json
{
  "id": "q_01J8X...",
  "question_set_id": "qs_01J8X...",
  "mockset_id": "ms_01J8X...",
  "category": "INTRODUCTION",
  "position": 1,
  "question_text": "Why are you interested in this Product Manager role?",
  "question_translation": "你為什麼對這個產品經理職位有興趣？",
  "difficulty": "EASY",
  "attempt_count": 1,
  "latest_attempt_id": "att_01J8X...",
  "has_reference_answer": true,
  "reference_answer_source": "USER_REQUESTED"
}
```

> `attempt_count` 決定 4.4 顯示「生成參考答案」還是「參考前次答案」：
> `0` → 生成參考答案；`>= 1` → 參考前次答案（Spec 4.4.4.6）。

---

### 3.6 練習回答（4.4）

#### `POST /api/questions/{id}/attempts`

建立一次回答。**文字模式同步、語音模式非同步**（因為語音要跑 STT）。

##### 文字模式（P1B）— `application/json`

**Request**
```json
{
  "input_mode": "TEXT",
  "content": "I am interested in this role because ...",
  "duration_seconds": 78
}
```

| 欄位 | 規則 |
|---|---|
| `content` | 有效內容 **100–2000 字元**；僅允許英文字母、數字、空格、半形標點與換行（Spec 4.4.4.1） |
| `duration_seconds` | 前端計時器的秒數，供分析參考。**後端不用它擋送出**（見下方註記） |

**201**
```json
{
  "attempt": {
    "id": "att_01J8X...",
    "question_id": "q_01J8X...",
    "attempt_number": 1,
    "input_mode": "TEXT",
    "content": "I am interested in this role because ...",
    "segments": [],
    "duration_seconds": 78,
    "transcript_status": "NOT_APPLICABLE",
    "analysis_status": "PENDING",
    "state": "ACTIVE",
    "created_at": "2026-09-10T02:31:44Z"
  }
}
```

> ⚠️ **關於 90 秒限制：後端只驗長度，不驗時間。**
> `duration_seconds` 由瀏覽器提供，後端無法可信驗證（使用者可以竄改）。
> 90 秒倒數是**前端 enforcement**：到 00:00 鎖定輸入欄位、不自動送出、不自動跳轉（Spec AC-05）。
> 後端會把 `duration_seconds` 存下來供分析與觀測使用，但不會因為它超過 90 而拒絕請求。
> 語音模式則不同 —— 後端會用 ffprobe 驗證音檔真實長度（見下）。

##### 語音模式（P2B）— `multipart/form-data`

| 欄位 | 說明 |
|---|---|
| `input_mode` | `VOICE` |
| `audio` | 音檔，≤ 25 MB。支援 `audio/webm`、`audio/ogg`、`audio/mp4`、`audio/mpeg`、`audio/wav` |
| `duration_seconds` | 瀏覽器計時秒數 |

**202** — Job envelope（`job_type: "ATTEMPT_TRANSCRIPTION"`）

**Job result**
```json
{
  "attempt": {
    "id": "att_01J8Y...",
    "attempt_number": 1,
    "input_mode": "VOICE",
    "content": "I am interested in this role because ...",
    "segments": [
      { "seq": 1, "start_ms": 0,    "end_ms": 4200,  "text": "I am interested in this role" },
      { "seq": 2, "start_ms": 4200, "end_ms": 9800,  "text": "because I have shipped two B2B products." }
    ],
    "duration_seconds": 78,
    "transcript_status": "CONFIRMED",
    "analysis_status": "PENDING",
    "state": "ACTIVE"
  }
}
```

> `segments` 是**逐字稿時間序**。文字模式回空陣列。4.5「本次回答」要顯示語音回答的對應時間區間就靠這個。
> 逐字稿**不可編輯**（Spec D-016、M-10），API 沒有提供修改 transcript 的方法。

**重錄取代規則**：同一題在分析完成前重新送出 attempt，**舊的未分析 attempt 會被標為 `SUPERSEDED`**，
`attempt_number` 不會跳號（Spec §17.2：「重新錄音只清除目前回答，不新增 Attempt；成功送出分析時才保存 Attempt」）。

**錯誤**
| code | 情境 |
|---|---|
| `EMPTY_ANSWER` (400) | 內容為空／音檔中無可辨識語音 |
| `ANSWER_TOO_SHORT` (400) | 文字有效內容 < 100 字元 |
| `ANSWER_TEXT_TOO_LONG` (400) | 文字 > 2000 字元 |
| `ANSWER_TEXT_INVALID_CHARS` (400) | 含非英文字元 |
| `ANSWER_TOO_LONG` (400) | 音檔實際長度超過 90 秒 + 5 秒容差 |
| `AUDIO_UNSUPPORTED_TYPE` (415) | 音檔格式不支援 |
| `UPLOAD_TOO_LARGE` (413) | 音檔 > 25 MB |
| `STT_FAILED` (502) | 轉寫失敗 → 前端提供「重新錄音」或「切換文字模式」 |

---

#### `GET /api/attempts/{id}`

**200** — `{ "attempt": { ... } }`，形狀同上。

---

#### `GET /api/questions/{id}/attempts`

取得該題所有已完成的 attempt，用於 4.5「前一次回答」。

**200**
```json
{
  "attempts": [
    { "id": "att_01J8X...", "attempt_number": 1, "input_mode": "TEXT", "content": "...",
      "segments": [], "duration_seconds": 78, "analysis_status": "READY", "created_at": "..." },
    { "id": "att_01J8Y...", "attempt_number": 2, "input_mode": "TEXT", "content": "...",
      "segments": [], "duration_seconds": 84, "analysis_status": "READY", "created_at": "..." }
  ]
}
```

> 只回 `state = ACTIVE` 的 attempt，依 `attempt_number` 升冪。被取代的重錄不會出現。
> 保存上限 **100 筆／題**（Spec 4.5.4.5）。

---

### 3.7 參考答案（4.4）

> ⚠️ **這一節有一個 Spec 歧義尚未由 PM 拍板**，見 [`06-pm-open-questions.md`](./06-pm-open-questions.md) 第 1 項。
> 目前介面設計成**可以容納兩種來源**（`source` 欄位），無論 PM 怎麼決定都不需要改契約。

#### `POST /api/questions/{id}/reference-answer`

**Phase**：P2A ｜ **非同步**

**202** — Job envelope（`job_type: "REFERENCE_ANSWER_GENERATION"`）
**200** — 若已存在，**直接回既有內容，不重新呼叫模型**（Spec AC-12）：
```json
{ "status": "READY", "reused": true, "reference_answer": { ... } }
```

**Job result**
```json
{
  "reference_answer": {
    "question_id": "q_01J8X...",
    "source": "USER_REQUESTED",
    "text": "I am drawn to this role because (此處需引述個人經歷，請自行填入) ...",
    "outline": ["點出動機", "連結一段具體經歷", "收在對這個職缺的價值"],
    "practice_tip": "先說做法，再說結果。",
    "requires_user_fill": true,
    "placeholders": ["(此處需引述個人經歷，請自行填入)"],
    "is_editable": false,
    "created_at": "2026-09-10T02:29:12Z"
  }
}
```

| 欄位 | 說明 |
|---|---|
| `text` | 英文參考答案，**最多 2000 字元**（含空格換行，Spec §17.4） |
| `requires_user_fill` | `true` 表示內容含 placeholder，前端應提示使用者需自行補上個人經歷 |
| `placeholders` | 固定標記字串。**AI 不得編造使用者沒提供的經歷、成果或指標**（Spec AC-14、§4.7.4） |
| `is_editable` | 恆為 `false`。參考答案**不可選取、複製或編輯**（Spec D-018、AC-12） |

**錯誤**
| code | 情境 |
|---|---|
| `REFERENCE_ANSWER_NOT_ALLOWED` (409) | 業務規則不允許此時生成 |
| `MODEL_UNAVAILABLE` (502) | 顯示「請稍候再試」，按鈕恢復 Enabled |

---

#### `GET /api/questions/{id}/reference-answer`

**純讀取，永遠不會觸發生成、不會呼叫模型、不會產生成本。**
對應 4.4「參考前次答案」按鈕（Spec AC-12：使用者返回題目集再選回同一題時，讀取已保存內容）。

**200** — `{ "reference_answer": { ... } }`
**404** `NOT_FOUND` — 尚未生成過

---

### 3.8 分析結果（4.5）

#### `POST /api/attempts/{id}/analysis`

**Phase**：P1B（首次）／ P2A（比較）｜ **非同步**（約 20–45 秒）

**Request** — 無 body

> **前端不需要指定是首次還是比較分析。**後端會自己判斷：
> 這題已有其他已完成分析 → `COMPARISON`（模型用 A02 prompt）；否則 → `FIRST`（A01）。

**202** — Job envelope（`job_type: "ANSWER_ANALYSIS"`）

**Job result — 首次分析（`analysis_type: "FIRST"`）**
```json
{
  "analysis": {
    "id": "ana_01J8X...",
    "attempt_id": "att_01J8X...",
    "analysis_type": "FIRST",
    "overall_state": "NEEDS_IMPROVEMENT",
    "fits": {
      "jd_fit": {
        "state": "YELLOW",
        "evidence": "I have shipped two B2B products.",
        "reason": "回答有提到 B2B 產品經驗，方向與這個職缺相符，但沒有對應到 JD 中「跨團隊協作」與「使用者研究」這兩項明確要求。",
        "signal": "補上一段跨團隊合作或使用者研究的具體經驗。"
      },
      "answer_fit": {
        "state": "RED",
        "evidence": "I am interested in this role because it is a good fit.",
        "reason": "回答說明了興趣，但沒有情境、行動與結果，面試官無法判斷你實際做過什麼。",
        "signal": "用一個具體專案說明你的做法與最後的結果。"
      },
      "delivery_fit": {
        "state": "GREEN",
        "evidence": "I led the discovery phase and aligned three teams.",
        "reason": "用詞與句構正確清楚，時態一致，沒有影響理解的文法問題。",
        "signal": "維持目前的句子長度，避免一句塞太多子句。"
      }
    },
    "priority_improvement": {
      "title": "把興趣換成一段可驗證的經歷",
      "summary": "這次回答說明了你為什麼想要這個職位，但沒有提出任何做過的事情作為支撐。",
      "user_answer_evidence": "I am interested in this role because it is a good fit.",
      "why_it_matters": "面試官在這一題要判斷的是你的經歷和這個職缺的關聯，只講興趣無法提供判斷依據。",
      "next_instruction_title": "先講一個專案，再講結果",
      "next_instruction_detail": "挑一個和這個職缺最相關的專案，說明當時的情境、你採取的行動，最後說出可衡量的結果。",
      "example_sentence": "At (此處需引述個人經歷，請自行填入), I led ... which resulted in ...",
      "reference_answer": "I am interested in this Product Manager role because ..."
    },
    "comparison": null,
    "created_at": "2026-09-10T02:33:51Z"
  }
}
```

**Job result — 第二次分析（`analysis_type: "COMPARISON"`）**

在上面的基礎上，`comparison` 有值、且 `priority_improvement` **沒有 `reference_answer` 欄位**
（Spec §17.2：第二輪應讓使用者用自己的話持續練習）：

```json
{
  "analysis": {
    "analysis_type": "COMPARISON",
    "overall_state": "ACCEPTABLE",
    "fits": { "...": "同上" },
    "comparison": {
      "previous_attempt_id": "att_01J8X...",
      "improved": [
        {
          "area": "具體性",
          "explanation": "這次補上了專案名稱與你在其中的角色，面試官可以判斷你實際負責的範圍。",
          "before": "I am interested in this role because it is a good fit.",
          "after": "At Northstar, I led the discovery phase for our billing product."
        }
      ],
      "not_yet_improved": ["仍然沒有說出可衡量的結果"]
    },
    "priority_improvement": {
      "title": "補上可衡量的結果",
      "summary": "這次說清楚了做法，但結束在行動，沒有交代後來發生什麼。",
      "user_answer_evidence": "I led the discovery phase for our billing product.",
      "why_it_matters": "面試官需要從結果判斷你的行動是否有效。",
      "next_instruction_title": "在最後加一句結果",
      "next_instruction_detail": "用一個數字或明確的變化收尾，例如採用率、時間縮短或錯誤下降。",
      "example_sentence": "As a result, (此處需引述個人經歷，請自行填入)."
    },
    "created_at": "2026-09-17T02:33:51Z"
  }
}
```

> **`improved` 可以是空陣列。**如果第二次回答沒有真的改善，模型不會編造進步
>（Spec A02 rules：「Do not credit an improvement the second answer does not contain」）。
> 前端要能處理空陣列，顯示「這次沒有明顯改善」而不是壞掉。

**Job error**
| code | 情境 | 前端 |
|---|---|---|
| `ANALYSIS_UNAVAILABLE` (502) | 模型異常或輸出不合 schema | **留在 4.4 練習回答頁**，顯示「請稍候再試」，保留原有回答與前一次分析結果，可再次點擊分析（Spec 4.5.4.11） |
| `EMPTY_ANSWER` (400) | attempt 沒有內容 | 提示重新回答 |
| `MODEL_QUOTA_EXCEEDED` (429) | 額度用盡 | 不可重試 |

> **失敗時不保存不完整結果、不顯示假資料、不覆蓋已成功的 Mock Set / Question / Attempt**（Spec AC-09）。

---

#### `GET /api/attempts/{id}/analysis`

**200** — `{ "analysis": { ... } }`，形狀同上。
**404** `NOT_FOUND` — 尚未分析

---

### 3.9 事件紀錄（S-04）

#### `POST /api/events`

前端埋點。對應 Spec §15.1 的 28 個事件名。

**Request**
```json
{
  "events": [
    { "name": "question_selected", "occurred_at": "2026-09-10T02:30:00Z",
      "mockset_id": "ms_...", "question_id": "q_...", "metadata": { "position": 1 } }
  ]
}
```

**202** — `{ "accepted": 1 }`

> ⚠️ **`metadata` 不得包含履歷內容、回答全文、逐字稿或任何個人資料**（Spec §13.1）。
> 後端會拒絕過大的 metadata（> 2 KB）並回 `VALIDATION_ERROR`。

---

## 4. Error Code 全表

> 這張表沿用 prototype `server/lib/errors.js` 的既有契約（已對齊 Spec §12），
> 並補上本輪新增的碼。前端可以直接複用現有的 `src/i18n/errors.js` 對照表。

| Code | HTTP | `message_key` | `retryable` | 恢復方式 |
|---|---|---|---|---|
| `JOB_PAGE_UNREADABLE` | 422 | `job_input_try_paste_text` | ✅ | 回到職缺資訊欄位：「讀取連結失敗，請重新上傳」 |
| `JOB_URL_NOT_SUPPORTED` | 422 | `job_url_not_supported` | ✅ | 提示改貼 JD 文字（本輪僅支援 Cake） |
| `JOB_TEXT_TOO_SHORT` | 422 | `job_input_too_short` | ✅ | 提示補充職缺內容 |
| `RESUME_UNSUPPORTED_TYPE` | 415 | `resume_unsupported_type` | ✅ | 「請使用 PDF」 |
| `RESUME_EXTRACTION_FAILED` | 422 | `resume_extraction_failed` | ✅ | 「請重新上傳可讀取的 PDF 文件」 |
| `RESUME_EMPTY` | 422 | `resume_empty` | ✅ | 同上 |
| `MOCKSET_SOURCE_MISSING` | 400 | `mockset_source_missing` | ✅ | 阻止建立，回到缺少的欄位 |
| `MOCKSET_IMMUTABLE` | 409 | `mockset_immutable` | ❌ | 提示需建立新的 Mock Set |
| `MOCKSET_LIMIT_REACHED` | 409 | `mockset_limit_reached` | ❌ | P3 才啟用 |
| `QUESTION_GENERATION_INVALID` | 502 | `question_generation_retry` | ✅ | 「題目生成失敗，請稍候再試」 |
| `QUESTION_GENERATION_BLOCKED` | 422 | `question_generation_blocked` | ✅ | JD／履歷資訊不足 |
| `ADD_QUESTION_LIMIT_REACHED` | 429 | `add_question_limit_reached` | ❌ | 按鈕 Disabled：「已達本日上限」 |
| `QUESTION_LIMIT_REACHED` | 409 | `question_limit_reached` | ❌ | 該題型已達 20 題（**文案待補**） |
| `EMPTY_ANSWER` | 400 | `answer_empty` | ✅ | 不進行分析，提示重新回答 |
| `ANSWER_TOO_SHORT` | 400 | `answer_too_short` | ✅ | 「請至少輸入 100 個字元」 |
| `ANSWER_TEXT_TOO_LONG` | 400 | `answer_text_too_long` | ✅ | 達 2000 字元後不可繼續輸入 |
| `ANSWER_TEXT_INVALID_CHARS` | 400 | `answer_text_invalid_chars` | ✅ | 僅允許英文字母、數字、空格、半形標點與換行 |
| `ANSWER_TOO_LONG` | 400 | `answer_too_long` | ❌ | 音檔超過 90 秒 |
| `AUDIO_UNSUPPORTED_TYPE` | 415 | `audio_unsupported_type` | ✅ | 重新錄音 |
| `UPLOAD_TOO_LARGE` | 413 | `upload_too_large` | ✅ | 履歷 10 MB／音檔 25 MB |
| `STT_FAILED` | 502 | `stt_failed` | ✅ | 重新錄音或切換文字模式 |
| `ANALYSIS_UNAVAILABLE` | 502 | `analysis_retry` | ✅ | 留在 4.4，「請稍候再試」 |
| `REFERENCE_ANSWER_NOT_ALLOWED` | 409 | `reference_answer_not_allowed` | ❌ | 隱藏生成按鈕 |
| `MODEL_UNAVAILABLE` | 502 | `model_unavailable` | ✅ | 「請稍候再試」 |
| `MODEL_QUOTA_EXCEEDED` | 429 | `model_quota_exceeded` | ❌ | 提示稍後再來 |
| `MODEL_OUTPUT_INVALID` | 502 | `model_output_invalid` | ✅ | 「請稍候再試」 |
| `PROVIDER_NOT_CONFIGURED` | 503 | `provider_not_configured` | ❌ | 環境問題，非使用者可恢復 |
| `AUTH_TOKEN_EXPIRED` | 410 | `auth_token_expired` | ✅ | 重新寄送驗證信 |
| `ACCESS_DENIED` | 401 | `access_denied` | ❌ | 導向登入 |
| `RATE_LIMITED` | 429 | `rate_limited` | ✅ | 稍後重試 |
| `VALIDATION_ERROR` | 400 | `validation_error` | ✅ | 回到對應欄位 |
| `NOT_FOUND` | 404 | `not_found` | ❌ | — |
| `INTERNAL_ERROR` | 500 | `internal_error` | ✅ | 「請稍候再試」 |

---

## 5. 給前端的重點提醒

1. **不要把 limits 寫死。**啟動時讀 `GET /api/health` 的 `limits`，100 / 2000 字元、90 秒、3 題、10 MB 都從那裡拿。
2. **輪詢請照 `poll_after_ms` 退避**，不要固定 1 秒。手機網路上固定短輪詢會明顯耗電且拖慢頁面。
3. **所有 POST 帶 `Idempotency-Key`。**使用者手快點兩次「生成題目」時，這是唯一能避免扣兩次模型成本的機制。
4. **`null` 就是 `--`。**JD 解析欄位拿到 `null` 直接顯示 `--`，不要 fallback 成空字串或範例文字（AC-02 明確禁止顯示假資料）。
5. **API 不回分數。**Fit 只有 `GREEN` / `YELLOW` / `RED`。小 i 提示框裡的「90 分以上／60–89／未滿 60」是固定文案，前端自己寫。
6. **`comparison.improved` 可能是空陣列。**要有對應的 UI，不能假設一定有內容。
7. **90 秒倒數是前端的責任。**後端不會因為超時拒絕文字回答。語音則會用音檔真實長度驗證。
8. **P1A 無帳號流程**：`mockset_id`、`question_set_id` 請存 `localStorage`，這是重整頁面後找回進度的唯一方法。P2C 登入上線後改由 `GET /api/me` 提供。
