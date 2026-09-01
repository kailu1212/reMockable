-- 非同步工作（本輪架構核心，見 docs/01-api-interface.md §1.4）。
--
-- request_payload 存的是參數（category、mockset_id），不是使用者原始內容。
-- 履歷與回答全文留在各自的表，job 只帶引用，避免敏感資料被複製到多處（Spec §13.1）。
CREATE TABLE ai_jobs (
  id                VARCHAR(40) NOT NULL,
  user_id           VARCHAR(40)     NULL,
  job_type          VARCHAR(40) NOT NULL,
  target_type       VARCHAR(40)     NULL,
  target_id         VARCHAR(40)     NULL,
  status            VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  progress          SMALLINT    NOT NULL DEFAULT 0,
  request_payload   JSON        NOT NULL,
  result            MEDIUMTEXT      NULL,
  error_code        VARCHAR(50)     NULL,
  error_message_key VARCHAR(80)     NULL,
  retry_count       SMALLINT    NOT NULL DEFAULT 0,
  started_at        DATETIME(6)     NULL,
  finished_at       DATETIME(6)     NULL,
  expires_at        DATETIME(6) NOT NULL,
  created_at        DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_jobs_status (status, created_at),
  KEY idx_ai_jobs_target (target_type, target_id),
  KEY idx_ai_jobs_expires (expires_at),
  CONSTRAINT ck_ai_jobs_status CHECK (status IN ('QUEUED','RUNNING','READY','FAILED')),
  CONSTRAINT ck_ai_jobs_progress CHECK (progress BETWEEN 0 AND 100),
  CONSTRAINT ck_ai_jobs_type CHECK (job_type IN (
    'JOB_POSTING_PARSE','QUESTION_SET_GENERATION','QUESTION_ADDITION',
    'ATTEMPT_TRANSCRIPTION','REFERENCE_ANSWER_GENERATION','ANSWER_ANALYSIS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 冪等（Spec §13.3）。一次解決三條硬性要求：
--   - 具 idempotency key 的請求不可重複建立同一個 Attempt
--   - 使用者重新整理頁面不應重複生成題目或重複扣模型成本
--   - 失敗狀態可重新嘗試，但不覆蓋已成功的結果
--
-- state = IN_PROGRESS 很重要：使用者連點兩下時，第二個請求會看到 IN_PROGRESS
-- 而回同一個 job_id，不會在第一個還沒寫完時又發起一次模型呼叫。
--
-- request_hash 防止前端 bug 造成的「同一個 key 送不同內容」被誤判為重送。
CREATE TABLE idempotency_keys (
  id              VARCHAR(40)  NOT NULL,
  idempotency_key VARCHAR(64)  NOT NULL,
  user_id         VARCHAR(40)      NULL,
  endpoint        VARCHAR(200) NOT NULL,
  request_hash    VARCHAR(71)  NOT NULL,
  response_status SMALLINT         NULL,
  response_body   MEDIUMTEXT       NULL,
  state           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
  expires_at      DATETIME(6)  NOT NULL,
  created_at      DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_idempotency_keys (idempotency_key, endpoint),
  KEY idx_idempotency_expires (expires_at),
  CONSTRAINT ck_idempotency_state CHECK (state IN ('IN_PROGRESS','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Prompt 版本管理（Spec §4.8 Prompt / Model Registry）。
--
-- category 讓同一個 prompt_id（例如 P01 初始題目生成）可以有四個題型專屬版本。
-- 用 'ALL' 當共用版本的哨兵值而非 NULL：MySQL 的 UNIQUE 允許多個 NULL，
-- 用 NULL 會讓 (prompt_id, category, version) 的唯一性失效。
--
-- active_uniq 是 generated column，用來實作「同一個 prompt + 題型只能有一個 ACTIVE 版本」。
-- PostgreSQL 可以直接用 partial unique index；MySQL 沒有，只能這樣繞。
CREATE TABLE prompt_templates (
  id             VARCHAR(40)  NOT NULL,
  prompt_id      VARCHAR(20)  NOT NULL,
  version        VARCHAR(20)  NOT NULL,
  category       VARCHAR(20)  NOT NULL DEFAULT 'ALL',
  content        MEDIUMTEXT   NOT NULL,
  output_schema  JSON         NOT NULL,
  schema_version VARCHAR(20)  NOT NULL DEFAULT 'v1',
  provider       VARCHAR(30)  NOT NULL,
  model          VARCHAR(100) NOT NULL,
  parameters     JSON         NOT NULL,
  status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  change_reason  TEXT             NULL,
  created_by     VARCHAR(100) NOT NULL DEFAULT 'system',
  created_at     DATETIME(6)  NOT NULL,
  active_uniq    VARCHAR(60) GENERATED ALWAYS AS (
    CASE WHEN status = 'ACTIVE' THEN CONCAT(prompt_id, ':', category) ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uq_prompt_templates (prompt_id, category, version),
  UNIQUE KEY uq_prompt_active (active_uniq),
  CONSTRAINT ck_prompt_templates_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 模型呼叫追溯（Spec §13.2）。
--
-- 只存 hash 與 metadata，不存 prompt 的完整輸入輸出 ——
-- Spec §13.1：「不在一般 application log 保存完整履歷、音檔或回答全文」。
-- 這張表是成本與品質監控用的，不是除錯用的原始資料倉。
CREATE TABLE ai_call_logs (
  id                  VARCHAR(40)   NOT NULL,
  job_id              VARCHAR(40)       NULL,
  request_id          VARCHAR(64)   NOT NULL,
  prompt_id           VARCHAR(20)   NOT NULL,
  prompt_version      VARCHAR(20)   NOT NULL,
  provider            VARCHAR(30)   NOT NULL,
  model               VARCHAR(100)  NOT NULL,
  mockset_id          VARCHAR(40)       NULL,
  question_id         VARCHAR(40)       NULL,
  attempt_id          VARCHAR(40)       NULL,
  source_content_hash VARCHAR(71)       NULL,
  schema_version      VARCHAR(20)       NULL,
  latency_ms          INT               NULL,
  input_tokens        INT               NULL,
  output_tokens       INT               NULL,
  estimated_cost_usd  DECIMAL(10,6)     NULL,
  cache_hit           TINYINT(1)    NOT NULL DEFAULT 0,
  retry_count         SMALLINT      NOT NULL DEFAULT 0,
  validation_result   VARCHAR(20)   NOT NULL,
  error_code          VARCHAR(50)       NULL,
  fallback_reason     VARCHAR(100)      NULL,
  created_at          DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_call_logs_job (job_id),
  KEY idx_ai_call_logs_prompt (prompt_id, prompt_version, created_at),
  KEY idx_ai_call_logs_created (created_at),
  CONSTRAINT ck_ai_call_logs_validation CHECK (validation_result IN ('PASSED','REPAIRED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
