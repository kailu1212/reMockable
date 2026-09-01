-- 題目集（Spec 4.2）。
-- UNIQUE (mockset_id, category) 是 M-07「沿用題目」的實作核心：
-- 「同一 Mock Set + 同一題型只會有一組題目」由資料庫保證，不靠應用層記得先查。
-- 使用者連點兩下「下一步」時，第二次會撞唯一鍵而不是生出第二組題目。
CREATE TABLE question_sets (
  id             VARCHAR(40)  NOT NULL,
  mockset_id     VARCHAR(40)  NOT NULL,
  category       VARCHAR(20)  NOT NULL,
  language       VARCHAR(10)  NOT NULL DEFAULT 'en',
  status         VARCHAR(20)  NOT NULL,
  prompt_id      VARCHAR(20)      NULL,
  prompt_version VARCHAR(20)      NULL,
  model          VARCHAR(100)     NULL,
  created_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_question_sets (mockset_id, category),
  CONSTRAINT fk_question_sets_mockset FOREIGN KEY (mockset_id) REFERENCES mocksets (id) ON DELETE CASCADE,
  CONSTRAINT ck_question_sets_category CHECK (category IN ('INTRODUCTION','BEHAVIORAL','TECHNICAL','CULTURAL_FIT')),
  CONSTRAINT ck_question_sets_status CHECK (status IN ('READY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 題目（Spec 4.3）。
-- mockset_id 是反正規化欄位：跨題型去重與查詢時避免 join question_sets。
-- embedding 存 JSON 陣列而非向量型別 —— 目前只用於「新增題目時跟既有題目太像就重生」，
-- 不做向量檢索。上 pgvector 之類的方案是為還不存在的需求付維運成本。
CREATE TABLE questions (
  id                    VARCHAR(40) NOT NULL,
  question_set_id       VARCHAR(40) NOT NULL,
  mockset_id            VARCHAR(40) NOT NULL,
  category              VARCHAR(20) NOT NULL,
  position              SMALLINT    NOT NULL,
  question_text         TEXT        NOT NULL,
  question_translation  TEXT            NULL,
  difficulty            VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
  intent                TEXT            NULL,
  expected_evidence     JSON        NOT NULL,
  personalization_level VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
  source_references     JSON        NOT NULL,
  origin                VARCHAR(10) NOT NULL DEFAULT 'INITIAL',
  embedding             JSON            NULL,
  created_at            DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_questions_position (question_set_id, position),
  KEY idx_questions_mockset (mockset_id),
  CONSTRAINT fk_questions_set FOREIGN KEY (question_set_id) REFERENCES question_sets (id) ON DELETE CASCADE,
  CONSTRAINT ck_questions_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
  CONSTRAINT ck_questions_origin CHECK (origin IN ('INITIAL','ADDED')),
  CONSTRAINT ck_questions_personalization CHECK (personalization_level IN ('PERSONALIZED','GENERAL')),
  CONSTRAINT ck_questions_category CHECK (category IN ('INTRODUCTION','BEHAVIORAL','TECHNICAL','CULTURAL_FIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 每日新增題目額度（Spec M-18、D-010、4.3.4.5）。
--
-- 為什麼用獨立表而不是像 prototype 那樣用「當日 event 計數」推算：
--   1. Spec 明確要求「生成失敗不消耗當日新增次數」，用 event 推算必須小心過濾失敗事件
--   2. event 是可清理的觀測資料，拿它當業務規則真相來源，日後 log rotation 會意外重置額度
--
-- 配合 INSERT ... ON DUPLICATE KEY UPDATE used_count = used_count + 1，
-- 額度遞增是原子的，使用者連點也不會超額。
--
-- quota_date 用 Asia/Taipei 當地日期：使用者認知的「今天」是台北時間的今天，
-- 用 UTC 會讓額度在台灣時間早上 8 點前重置，體感很怪。
--
-- 上限值（3）不寫進 CHECK：它由 remockable.limits.daily-add-question-limit 決定，
-- 產品要調整時不應該需要改 schema。
CREATE TABLE question_addition_quotas (
  id              VARCHAR(40) NOT NULL,
  question_set_id VARCHAR(40) NOT NULL,
  quota_date      DATE        NOT NULL,
  used_count      SMALLINT    NOT NULL DEFAULT 0,
  updated_at      DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_quotas (question_set_id, quota_date),
  CONSTRAINT fk_quotas_set FOREIGN KEY (question_set_id) REFERENCES question_sets (id) ON DELETE CASCADE,
  CONSTRAINT ck_quotas_used CHECK (used_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
