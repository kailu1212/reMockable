-- 職缺資訊解析結果（Spec 4.1）。
-- 六個欄位拆成實體欄位而非塞 JSON：它們是固定的產品欄位，前端逐欄顯示。
-- 無法擷取者為 NULL，前端顯示 '--'（Spec D-021、AC-02：不得自行產生或填入未確認的資料）。
CREATE TABLE job_postings (
  id                    VARCHAR(40)   NOT NULL,
  user_id               VARCHAR(40)       NULL,
  input_type            VARCHAR(10)   NOT NULL,
  source_url            VARCHAR(2048)     NULL,
  source_text           MEDIUMTEXT    NOT NULL,
  source_content_hash   VARCHAR(71)   NOT NULL,

  job_title             VARCHAR(255)      NULL,
  company_name          VARCHAR(255)      NULL,
  industry              VARCHAR(255)      NULL,
  experience            VARCHAR(500)      NULL,
  job_description       TEXT              NULL,
  requirements          TEXT              NULL,

  extracted_field_count SMALLINT      NOT NULL DEFAULT 0,
  missing_fields        JSON          NOT NULL,
  parse_status          VARCHAR(20)   NOT NULL,
  prompt_id             VARCHAR(20)       NULL,
  prompt_version        VARCHAR(20)       NULL,
  model                 VARCHAR(100)      NULL,
  created_at            DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_job_postings_user (user_id, created_at),
  KEY idx_job_postings_hash (source_content_hash),
  CONSTRAINT ck_job_postings_input  CHECK (input_type IN ('TEXT','URL')),
  CONSTRAINT ck_job_postings_status CHECK (parse_status IN ('READY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 履歷（Spec 4.1）。storage_key 存 S3 object key 而非 URL：
-- URL 會因為 bucket 搬遷或 CDN 換域名失效，存 key 才能隨時重新產生簽名 URL。
-- extracted_text 用 MEDIUMTEXT：TEXT 的 64 KB 上限對多頁履歷不夠。
CREATE TABLE resumes (
  id                VARCHAR(40)  NOT NULL,
  user_id           VARCHAR(40)      NULL,
  original_filename VARCHAR(255) NOT NULL,
  mime_type         VARCHAR(100) NOT NULL,
  byte_size         BIGINT       NOT NULL,
  storage_key       VARCHAR(512) NOT NULL,
  extracted_text    MEDIUMTEXT   NOT NULL,
  page_count        SMALLINT         NULL,
  content_hash      VARCHAR(71)  NOT NULL,
  extract_status    VARCHAR(20)  NOT NULL,
  created_at        DATETIME(6)  NOT NULL,
  purge_after       DATETIME(6)      NULL,
  PRIMARY KEY (id),
  KEY idx_resumes_user (user_id, created_at),
  KEY idx_resumes_purge (purge_after),
  CONSTRAINT ck_resumes_status CHECK (extract_status IN ('READY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
