-- 回答（Spec 4.4）。
--
-- content 用單一欄位而不分 answer_text / transcript：兩種模式的內容互斥，
-- 下游分析只關心「送進來的是什麼文字」。分成兩欄會讓每個讀取點都要寫 if/else。
-- 需要區分模式時查 input_mode。
--
-- state = SUPERSEDED 而非刪除（Spec §17.2「重新錄音只清除目前回答，不新增 Attempt；
-- 成功送出分析時才保存 Attempt」）：保留 row 但標記取代，attempt_number 不跳號，
-- 也保留了使用者實際重試幾次的觀測資料。
--
-- duration_seconds 對文字模式只存不驗：瀏覽器計時器無法可信驗證，
-- 90 秒是前端 enforcement（Spec AC-05）。語音模式會用 ffprobe 驗證音檔真實長度後才寫入。
CREATE TABLE attempts (
  id                VARCHAR(40)  NOT NULL,
  question_id       VARCHAR(40)  NOT NULL,
  mockset_id        VARCHAR(40)  NOT NULL,
  user_id           VARCHAR(40)      NULL,
  attempt_number    SMALLINT     NOT NULL,
  input_mode        VARCHAR(10)  NOT NULL,
  content           TEXT         NOT NULL,

  audio_storage_key VARCHAR(512)     NULL,
  audio_mime_type   VARCHAR(100)     NULL,
  audio_byte_size   BIGINT           NULL,
  transcript_status VARCHAR(20)  NOT NULL DEFAULT 'NOT_APPLICABLE',

  duration_seconds  SMALLINT     NOT NULL DEFAULT 0,
  state             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  analysis_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  created_at        DATETIME(6)  NOT NULL,
  purge_after       DATETIME(6)      NULL,
  PRIMARY KEY (id),
  KEY idx_attempts_question (question_id, state, attempt_number),
  KEY idx_attempts_user (user_id, created_at),
  KEY idx_attempts_purge (purge_after),
  KEY idx_attempts_mockset (mockset_id),
  CONSTRAINT fk_attempts_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
  CONSTRAINT ck_attempts_mode       CHECK (input_mode IN ('TEXT','VOICE')),
  CONSTRAINT ck_attempts_state      CHECK (state IN ('ACTIVE','SUPERSEDED')),
  CONSTRAINT ck_attempts_status     CHECK (analysis_status IN ('PENDING','READY','FAILED')),
  CONSTRAINT ck_attempts_transcript CHECK (transcript_status IN ('NOT_APPLICABLE','PENDING','CONFIRMED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 逐字稿時間序（P2B 語音）。P1A/P1B 這張表是空的，但現在就建。
-- Spec 4.5.4.5「語音回答的對應時間區間於『你的回答』中呈現」靠它。
-- 2B 上線時只要開始寫入，不需要 migration。
CREATE TABLE attempt_transcript_segments (
  id         VARCHAR(40) NOT NULL,
  attempt_id VARCHAR(40) NOT NULL,
  seq        SMALLINT    NOT NULL,
  start_ms   INT         NOT NULL,
  end_ms     INT         NOT NULL,
  text       TEXT        NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_segments (attempt_id, seq),
  CONSTRAINT fk_segments_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE,
  CONSTRAINT ck_segments_range CHECK (end_ms >= start_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
