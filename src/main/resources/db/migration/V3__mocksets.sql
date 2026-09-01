-- Mock Set（Spec 4.1）。
-- 沒有 UPDATE 路徑，也刻意沒有 updated_at：
-- Spec M-04 / D-003 規定建立後 JD 與履歷不可修改，要換資料只能建新的 Mock Set。
--
-- 注意：name 是使用者輸入的「練習名稱」，不是 JD 解析出的職稱。
-- 職稱在 job_postings.job_title。Spec §4.7.2 特別區分過這兩者。
CREATE TABLE mocksets (
  id             VARCHAR(40)  NOT NULL,
  user_id        VARCHAR(40)      NULL,
  name           VARCHAR(100) NOT NULL,
  job_posting_id VARCHAR(40)  NOT NULL,
  resume_id      VARCHAR(40)  NOT NULL,
  status         VARCHAR(20)  NOT NULL DEFAULT 'READY',
  frozen_at      DATETIME(6)  NOT NULL,
  created_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_mocksets_user (user_id, created_at),
  KEY idx_mocksets_job_posting (job_posting_id),
  KEY idx_mocksets_resume (resume_id),
  CONSTRAINT fk_mocksets_job_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
  CONSTRAINT fk_mocksets_resume      FOREIGN KEY (resume_id)      REFERENCES resumes (id),
  CONSTRAINT ck_mocksets_status CHECK (status IN ('READY','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 凍結快照（Spec §4.7.3 CCP 步驟 4「凍結」與步驟 5「重用」）。
--
-- 這看起來像 job_postings + resumes 的重複資料，但沒有它，日後修正解析邏輯
-- 會讓既有 Mock Set 的題目與分析對不上當初依據的資料，AI 產出的可追溯性就斷了。
--
-- sections 讓 prompt 能引用具體段落（例如 jd_requirements）而不是整包文字丟進去，
-- 這是 grounding 檢查能運作的前提。
CREATE TABLE mockset_sources (
  id             VARCHAR(40) NOT NULL,
  mockset_id     VARCHAR(40) NOT NULL,
  type           VARCHAR(20) NOT NULL,
  extracted_text MEDIUMTEXT  NOT NULL,
  sections       JSON        NOT NULL,
  content_hash   VARCHAR(71) NOT NULL,
  created_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mockset_sources (mockset_id, type),
  CONSTRAINT fk_mockset_sources_mockset FOREIGN KEY (mockset_id) REFERENCES mocksets (id) ON DELETE CASCADE,
  CONSTRAINT ck_mockset_sources_type CHECK (type IN ('JD','RESUME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
