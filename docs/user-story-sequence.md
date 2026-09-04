# reMockable User Story 時序圖（FE / BE / DB）

**狀態：DRAFT — 內部工作稿**

依 Figma user flow（`reMockable-userflow-20260901`）逐步驟改畫成時序圖， 固定只用 **FrontEnd / BackEnd / DB** 三條 lane。LLM、STT、S3、Email、Google 等外部服務 不另外拉 lane，收斂成 BackEnd 內部的自我呼叫與註記。

> **這份是內部工作稿。**給前端的正式交付是單一份 **《reMockable API 介面規格》**，這些流程圖已收錄在該文件第 3 章， 並與通用契約、27 支 endpoint 定義放在一起。本文件保留給 PM 與 tech lead 討論流程時使用，不單獨發給前端。

---

## 共用機制｜非同步 Job 通用機制（所有 AI 呼叫共用）

**只要那支 API 會呼叫模型，就是這個形狀。**前端只需要寫一次輪詢邏輯， 後面五張圖裡凡是標「非同步」的步驟都套用這裡，不再重複展開。 目前共 **6 支**：JD 解析、生成題目、新增題目、語音轉寫、參考答案、分析。

```mermaid
sequenceDiagram
    autonumber
    participant FE as FrontEnd
    participant BE as BackEnd
    participant DB as DB

    FE->>BE: POST {任一 AI endpoint}<br/>Idempotency-Key: uuid v4
    BE->>DB: SELECT idempotency_keys

    alt 相同 key 已完成
        BE-->>FE: 回上次的同一個 jobId（不重複呼叫模型）
    else 新請求
        BE->>DB: INSERT idempotency_keys (IN_PROGRESS)<br/>INSERT ai_jobs (QUEUED)
        BE-->>FE: 202 { jobId, status: QUEUED, pollAfterMs: 1500 }
    end

    Note over BE: 背景執行緒接手，HTTP 已經回完
    BE->>BE: 呼叫 LLM / STT
    BE->>DB: 寫入結果，UPDATE ai_jobs SET status=READY

    loop 依 pollAfterMs 輪詢：1500→2000→3000→5000ms，上限 180 秒
        FE->>BE: GET /api/jobs/{jobId}
        BE->>DB: SELECT ai_jobs
        alt status = QUEUED / RUNNING
            BE-->>FE: { status, progress, pollAfterMs }
            Note over FE: 依新的 pollAfterMs 再輪詢<br/>不要固定 1 秒打
        else status = READY
            BE-->>FE: { status: READY, result }
            Note over FE: 停止輪詢，取 result
        else status = FAILED
            BE-->>FE: { status: FAILED, error: { code, messageKey, retryable } }
            Note over FE: 停止輪詢，依 code 顯示文案<br/>retryable=true 才給重試按鈕
        end
    end

    Note over FE,DB: job 結果保留 24 小時，重整頁面可用同一個 jobId 取回<br/>失敗不消耗每日新增題目額度
```

## Step 0｜首頁 → 登入彈窗（Email 驗證信 / Google SSO）

點擊「開始練習」若尚未登入，先跳登入彈窗（Spec 4.6）。Email 與 Google 兩條路徑最終都 匯到同一個「驗證成功」判斷；失敗會顯示提醒文字並回到登入彈窗重試。 這段全部是同步 API，不經過 job 機制。

```mermaid
sequenceDiagram
    autonumber
    participant FE as FrontEnd
    participant BE as BackEnd
    participant DB as DB

    Note over FE: Step 0 首頁
    FE->>FE: 點擊「開始練習」（& 登入，若尚未登入）
    FE->>FE: 檢查是否已成功登入

    alt 已成功登入
        Note over FE: 直接進入「是否已建立過 Mock Set」判斷
    else 尚未登入
        Note over FE: 顯示登入彈窗（Spec 4.6）

        alt 使用 Google 帳戶登入（Phase 3）
            FE->>FE: 導向 Google 第三方登入（OAuth）
            FE->>BE: POST /api/auth/google { idToken }
            BE->>BE: 驗證 Google id token
            alt 驗證成功
                BE->>DB: SELECT or INSERT users
                BE-->>FE: 200 { accessToken, user }
            else 驗證失敗
                BE-->>FE: 401 ACCESS_DENIED
                Note over FE: 顯示「提醒文字：請稍後再試」<br/>回到登入彈窗
            end
        else 使用 Email 驗證信
            FE->>BE: POST /api/auth/email/request { email }
            BE->>BE: 產生 token，計算 SHA-256
            BE->>DB: INSERT auth_email_tokens (token_hash, expires_at)
            BE-->>FE: 200 { status: sent, expiresIn: 900 }
            Note over FE: 顯示「請查收驗證信」
            Note over FE: 使用者於 Email 點擊連結驗證
            FE->>BE: POST /api/auth/email/verify { token }
            alt token 有效且未使用
                BE->>DB: UPDATE auth_email_tokens SET consumed_at
                BE->>DB: SELECT or INSERT users
                BE-->>FE: 200 { accessToken, user }
            else token 逾期／無效
                BE-->>FE: 410 AUTH_TOKEN_EXPIRED
                Note over FE: 顯示「提醒文字：請稍後再試」<br/>回到登入彈窗
            end
        end

        Note over FE: 驗證成功 → 關閉彈窗
    end

    FE->>BE: GET /api/me
    BE->>DB: SELECT users / mocksets
    BE-->>FE: { user, hasMockset, defaultMocksetId }
    alt 尚未建立過 Mock Set
        Note over FE: 導向 Step 1 建立面試資料
    else 已建立過
        Note over FE: 導向 Step 2 選擇題型
    end
```

## Step 1–3｜建立面試資料 → 選擇題型 → 選擇題目（含每日新增額度）

該題型題目已生成 → 不重新生成、沿用先前題目與進度；尚未生成 → 依 Step 1 的資料首次生成 5 題。新增題目按鈕是否 Enabled，取決於本日已新增次數是否超過 3 次。 **JD 解析、生成題目、新增題目三支都是非同步**，走上方共用機制。

```mermaid
sequenceDiagram
    autonumber
    participant FE as FrontEnd
    participant BE as BackEnd
    participant DB as DB

    Note over FE: Step 1 建立面試資料（Mock Set）
    FE->>FE: 使用者上傳資料<br/>必填：JD（Text/URL）、CV/Resume（PDF）<br/>選填：Portfolio（PDF）、Additional Text ← Phase 3

    rect rgba(31,110,140,0.08)
    Note over FE,DB: ① JD 解析（非同步）
    FE->>BE: POST /api/job-postings/parse { inputType, value }
    BE-->>FE: 202 { jobId }
    Note over FE,BE: 輪詢 GET /api/jobs/{jobId}
    BE->>BE: 呼叫 LLM 解析 JD（P00）
    BE->>DB: INSERT job_postings
    BE-->>FE: READY { jobPostingId, extraction, extractedFieldCount, missingFields }
    Note over FE: 擷取不到的欄位回 null，顯示「--」<br/>欄位缺漏不阻擋流程
    end

    Note over FE,DB: ② 履歷（同步，不呼叫模型）
    FE->>BE: POST /api/resumes（multipart PDF）
    BE->>BE: 抽取 PDF 文字
    BE->>DB: INSERT resumes
    BE-->>FE: 201 { resumeId }

    Note over FE,DB: ③ 建立 Mock Set（同步）
    FE->>BE: POST /api/mocksets { name, jobPostingId, resumeId }
    BE->>DB: INSERT mocksets, mockset_sources（凍結快照 CCP）
    BE-->>FE: 201 { mocksetId }
    Note over BE: 快照後 JD 原網頁再改動<br/>不影響已建立的 Mock Set

    Note over FE: Step 2 選擇題型<br/>自我介紹／行為問題／技術問題／文化契合
    FE->>BE: GET /api/mocksets/{id}/question-sets?category=
    BE->>DB: SELECT question_sets

    alt 該題型題目已生成（M-07 沿用）
        DB-->>BE: 已存在
        BE-->>FE: 200 { exists: true, questionSet, questions[5] }
        Note over FE: 不重新生成，顯示先前題目 & 進度
    else 尚未生成（M-06 首次生成，非同步）
        DB-->>BE: 無資料
        BE-->>FE: 200 { exists: false }
        rect rgba(31,110,140,0.08)
        FE->>BE: POST /api/mocksets/{id}/question-sets { category }
        BE-->>FE: 202 { jobId }
        Note over FE,BE: 輪詢 GET /api/jobs/{jobId}（15–40 秒）
        BE->>BE: 呼叫 LLM 生成 5 題（P01，基於凍結快照）
        BE->>DB: INSERT question_sets, questions × 5
        BE-->>FE: READY { questionSet, questions[5] }
        end
    end

    Note over FE: Step 3 選擇題目
    alt 本日新增題目 ≥ 3
        Note over FE: 「新增題目」按鈕 Disabled
    else 本日新增題目 未達 3
        Note over FE: 「新增題目」按鈕 Enabled
        FE->>FE: 使用者點擊「新增題目」
        rect rgba(31,110,140,0.08)
        FE->>BE: POST /api/question-sets/{id}/questions
        BE->>DB: SELECT question_addition_quotas（today, Asia/Taipei）
        BE-->>FE: 202 { jobId }
        Note over FE,BE: 輪詢 GET /api/jobs/{jobId}
        BE->>BE: 呼叫 LLM 生成 1 題（P02，與既有題目去重）
        BE->>DB: INSERT questions (origin=ADDED)<br/>INSERT ... ON DUPLICATE KEY UPDATE used_count+1
        BE-->>FE: READY { question, quota }
        Note over BE: 額度只在成功寫入題目時 +1<br/>失敗不扣
        end
        Note over FE: 新題目 +1，回到 Step 3 題目列表
    end

    FE->>FE: 使用者選擇一題
    Note over FE: 進入 Step 4 練習回答
```

## Step 4｜練習回答 — 文字輸入模式

90 秒倒數歸零不會自動送出——鎖定輸入欄、顯示「重新計時」。是否顯示「參考前次答案」 或「生成參考答案」，取決於這題是否已經練過（Step 5 判斷）。 **建立 attempt 是同步，參考答案與分析是非同步。**

```mermaid
sequenceDiagram
    autonumber
    participant FE as FrontEnd
    participant BE as BackEnd
    participant DB as DB

    Note over FE: Step 4 練習回答，輸入模式：文字

    alt 已練過此題
        FE->>BE: GET /api/questions/{id}/reference-answer
        BE->>DB: SELECT reference_answers
        BE-->>FE: 200 { referenceAnswer }
        Note over FE: 顯示「參考前次答案」
    else 尚未練過此題
        FE->>FE: 使用者點擊「生成參考答案」
        rect rgba(31,110,140,0.08)
        FE->>BE: POST /api/questions/{id}/reference-answer
        BE-->>FE: 202 { jobId }
        Note over FE,BE: 輪詢 GET /api/jobs/{jobId}
        BE->>BE: 呼叫 LLM 生成參考答案（P03）
        BE->>DB: INSERT reference_answers
        BE-->>FE: READY { referenceAnswer }
        end
        Note over FE: 顯示生成答案
    end

    FE->>FE: focus 文字輸入欄，開始 90 秒倒數
    loop 輸入中，倒數未歸零
        FE->>FE: 即時檢核字數／字元（100–2,000）
    end
    opt 90 秒倒數歸零
        Note over FE: 鎖定輸入欄，顯示「重新計時」<br/>不自動送出、不自動跳轉
        FE->>FE: 使用者點擊「重新計時」→ 清空輸入欄，恢復未計時狀態
    end

    FE->>FE: 使用者點擊「確認並開始分析」

    Note over FE,DB: ① 建立 attempt（同步）
    FE->>BE: POST /api/questions/{id}/attempts<br/>{ inputMode: TEXT, content }
    BE->>BE: 後端再驗一次長度與字元（不可只信前端）
    BE->>DB: INSERT attempts
    BE-->>FE: 201 { attemptId }
    Note over BE: 90 秒計時由前端 enforce<br/>後端無法可信驗證時間，只驗長度

    rect rgba(31,110,140,0.08)
    Note over FE,DB: ② 分析（非同步，20–45 秒）
    FE->>BE: POST /api/attempts/{id}/analysis（無 body）
    BE-->>FE: 202 { jobId }
    Note over FE,BE: 輪詢 GET /api/jobs/{jobId}
    BE->>DB: SELECT 該題是否已有完成分析
    Note over BE: 有 → COMPARISON（A02）<br/>無 → FIRST（A01）<br/>前端不需指定
    BE->>BE: 呼叫 LLM 進行 3-Fit 分析
    BE->>DB: INSERT analyses
    BE-->>FE: READY { analysis }
    end
    Note over FE: 進入 Step 5 分析結果
```

## Step 4｜練習回答 — 語音輸入模式

錄音中可「取消」（丟棄音檔）；90 秒到期會強制結束並送出轉換，隱藏「重新錄製」。 逐字稿轉出後不可編輯，僅能「重新錄製」整段重來。 **上傳音檔後 STT 走非同步**，「轉換中」畫面就是輪詢期間。

```mermaid
sequenceDiagram
    autonumber
    participant FE as FrontEnd
    participant BE as BackEnd
    participant DB as DB

    Note over FE: Step 4 練習回答，輸入模式：語音
    FE->>FE: 使用者點擊「錄音」→ 開始 90 秒倒數與錄音

    alt 使用者點擊「取消錄音」
        Note over FE: 丟棄音檔，回到未錄音狀態
    else 點擊「完成錄音」或 90 秒到期（強制結束）
        Note over FE: 顯示「轉換中」

        rect rgba(31,110,140,0.08)
        Note over FE,DB: ① 上傳與轉寫（非同步）
        FE->>BE: POST /api/questions/{id}/attempts<br/>multipart { inputMode: VOICE, audio }
        BE->>BE: 驗證 MIME / 大小，ffprobe 驗證真實長度
        BE->>DB: 上傳 S3，INSERT attempts (transcript_status=PENDING)
        BE-->>FE: 202 { jobId, attemptId }
        Note over FE,BE: 輪詢 GET /api/jobs/{jobId}<br/>「轉換中」畫面即此期間
        BE->>BE: 呼叫 STT 轉寫並校正文本

        alt 轉寫成功
            BE->>DB: UPDATE attempts SET content,<br/>transcript_status=CONFIRMED
            BE-->>FE: READY { attempt, transcript }
            Note over FE: 顯示語音逐字稿（不可編輯）
        else 轉寫失敗／無可辨識語音
            BE-->>FE: FAILED { code: STT_FAILED, retryable: true }
            Note over FE: 提供「重新錄音」或「切換文字模式」
        end
        end

        opt 使用者點擊「重新錄製」
            Note over FE: 捨棄本次逐字稿，回到錄音狀態
        end
    end

    FE->>FE: 使用者點擊「確認並開始分析」

    rect rgba(31,110,140,0.08)
    Note over FE,DB: ② 分析（非同步，與文字模式完全相同）
    FE->>BE: POST /api/attempts/{id}/analysis
    BE-->>FE: 202 { jobId }
    Note over FE,BE: 輪詢 GET /api/jobs/{jobId}
    BE->>BE: 呼叫 LLM 進行 3-Fit 分析
    BE->>DB: INSERT analyses
    BE-->>FE: READY { analysis }
    end
    Note over BE: deliveryFit 只評文法與句構<br/>不評發音、腔調、語速 —— 模型只拿到逐字稿
    Note over FE: 進入 Step 5 分析結果
```

## Step 5｜分析結果與後續動作

已練過的題目會多跑一次「前後次回答比較」，產出「這次進步了哪裡」。三個後續動作 （再練一次／重選題型／上一步）都回到練習迴圈，不是終點頁。 **比較不是另一支 API** —— 後端在同一個分析 job 內判斷，前端不需指定。

```mermaid
sequenceDiagram
    autonumber
    participant FE as FrontEnd
    participant BE as BackEnd
    participant DB as DB

    Note over FE,DB: 承接 Step 4 的分析 job（同一個 jobId，非另一支 API）

    BE->>DB: SELECT 該題是否已有完成分析

    alt 首次作答（A01）
        BE->>BE: 呼叫 LLM 分析：3-Fit + 最優先改善
        BE->>DB: INSERT analyses (analysis_type=FIRST)
        BE-->>FE: READY { analysis }
        Note over FE: 顯示紅/黃/綠、3-Fit、優先改善建議<br/>priorityImprovement 含 referenceAnswer
    else 已練過此題（A02）
        BE->>DB: SELECT 前次已完成的 attempt 與 analysis
        BE->>BE: 呼叫 LLM 比較前後次回答
        BE->>DB: INSERT analyses (analysis_type=COMPARISON)
        BE-->>FE: READY { analysis, comparison }
        Note over FE: 額外顯示前次回答紀錄、這次進步了哪裡<br/>A02 不回 referenceAnswer（讓使用者用自己的話練）
    end

    Note over FE: comparison.improved 可能是空陣列<br/>要顯示「這次沒有明顯改善」而不是壞掉

    opt 分析失敗
        BE-->>FE: FAILED { code: ANALYSIS_UNAVAILABLE, retryable: true }
        Note over FE: 留在練習頁，保留原有回答與前次分析<br/>不顯示假資料、不覆蓋已成功的結果
    end

    alt 使用者點擊「再練一次」
        Note over FE: 回到同一題，Step 4
    else 使用者點擊「重選題型」
        Note over FE: 同題型下一題，回到 Step 4
    else 使用者點擊「上一步」
        Note over FE: 回到 Step 2 選擇題型
    end
```

---

## 版本紀錄

| 編號 | 時間 | 人員 | 版號 | 說明 |
|---|---|---|---|---|
| — | — | Lyon | DRAFT | 尚未發布。內部審閱中，此階段的修改不列入版本紀錄。 |

> 本檔由 `scripts/gen_sequence_md.py` 從 `docs/user-story-sequence.html` 產生，請勿手改。
