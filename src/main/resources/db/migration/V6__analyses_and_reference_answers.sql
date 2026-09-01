-- 分析結果（Spec 4.5）。
--
-- UNIQUE (attempt_id) 讓分析天然冪等：同一個 attempt 只會有一筆分析，
-- 重複點「開始分析」不會產生第二次模型呼叫（Spec §13.3）。
--
-- 六個 Fit 欄位拉出來、不留在 result JSON 裡，理由有三個：
--   1. Spec D-025 要求 Server 保存 numeric score，而 §15.3 的模型品質評估需要跨筆查詢分數分布
--   2. 燈號門檻（90 / 60）若調整，可以用 SQL 重算並驗證影響範圍
--   3. CHECK 能在資料庫層擋住「score 92 但 state 是 YELLOW」這種模型輸出不一致
--
-- ⚠️ *_score 絕不出現在任何 API response（Spec D-025：UI 不顯示數字）。
--    DTO 層只映射 *_state。
CREATE TABLE analyses (
  id                          VARCHAR(40)  NOT NULL,
  attempt_id                  VARCHAR(40)  NOT NULL,
  previous_attempt_id         VARCHAR(40)      NULL,
  analysis_type               VARCHAR(20)  NOT NULL,
  overall_state               VARCHAR(30)  NOT NULL,

  jd_fit_score                SMALLINT     NOT NULL,
  jd_fit_state                VARCHAR(10)  NOT NULL,
  answer_fit_score            SMALLINT     NOT NULL,
  answer_fit_state            VARCHAR(10)  NOT NULL,
  delivery_fit_score          SMALLINT     NOT NULL,
  delivery_fit_state          VARCHAR(10)  NOT NULL,

  result                      MEDIUMTEXT   NOT NULL,
  unsupported_claims_detected TINYINT(1)   NOT NULL DEFAULT 0,
  prompt_id                   VARCHAR(20)  NOT NULL,
  prompt_version              VARCHAR(20)  NOT NULL,
  model                       VARCHAR(100) NOT NULL,
  created_at                  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_analyses_attempt (attempt_id),
  KEY idx_analyses_previous (previous_attempt_id),
  CONSTRAINT fk_analyses_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE,
  CONSTRAINT ck_analyses_type    CHECK (analysis_type IN ('FIRST','COMPARISON')),
  CONSTRAINT ck_analyses_overall CHECK (overall_state IN ('STRONG','ACCEPTABLE','NEEDS_IMPROVEMENT')),
  CONSTRAINT ck_analyses_jd  CHECK (jd_fit_score       BETWEEN 0 AND 100 AND jd_fit_state       IN ('RED','YELLOW','GREEN')),
  CONSTRAINT ck_analyses_ans CHECK (answer_fit_score   BETWEEN 0 AND 100 AND answer_fit_state   IN ('RED','YELLOW','GREEN')),
  CONSTRAINT ck_analyses_del CHECK (delivery_fit_score BETWEEN 0 AND 100 AND delivery_fit_state IN ('RED','YELLOW','GREEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 參考答案（Spec 4.4）。
--
-- UNIQUE (question_id, source) 而不是 UNIQUE (question_id) —— 這是刻意為了吸收
-- 一個尚未由 PM 拍板的 Spec 歧義：
--   - AC-06：「若使用者沒有點擊『生成參考答案』，不顯示 reference answer」→ 使用者主動觸發
--   - 但 A01 分析輸出的 priority_improvement 本身就含一個 reference_answer（Spec 4.5.4.8）
--   - 而 4.4「參考前次答案」又說內容來源是「4.5.4.8 最優先改善欄位 - 參考答案」
-- 這兩個是同一份還是兩份，Spec 沒講清楚。用 source 欄位的話，PM 無論怎麼決定都不用改表。
CREATE TABLE reference_answers (
  id                 VARCHAR(40)  NOT NULL,
  question_id        VARCHAR(40)  NOT NULL,
  user_id            VARCHAR(40)      NULL,
  source             VARCHAR(20)  NOT NULL,
  answer_text        TEXT         NOT NULL,
  outline            JSON         NOT NULL,
  practice_tip       TEXT             NULL,
  requires_user_fill TINYINT(1)   NOT NULL DEFAULT 0,
  placeholders       JSON         NOT NULL,
  source_references  JSON         NOT NULL,
  prompt_id          VARCHAR(20)  NOT NULL,
  prompt_version     VARCHAR(20)  NOT NULL,
  model              VARCHAR(100) NOT NULL,
  created_at         DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reference_answers (question_id, source),
  CONSTRAINT fk_reference_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
  CONSTRAINT ck_reference_answers_source CHECK (source IN ('USER_REQUESTED','FROM_ANALYSIS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
