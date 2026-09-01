-- Phase 2C（9/17）才會寫入。P1A/P1B 以無帳號流程驗證，這兩張表是空的。
-- 現在就建，是為了讓其他表的 user_id 從第一天起就存在（nullable），P2C 上線不需要 migration。

CREATE TABLE users (
  id            VARCHAR(40)  NOT NULL,
  email         VARCHAR(320) NOT NULL,
  display_name  VARCHAR(100)     NULL,
  auth_provider VARCHAR(20)  NOT NULL,
  google_sub    VARCHAR(255)     NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME(6)      NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  UNIQUE KEY uq_users_google_sub (google_sub),
  CONSTRAINT ck_users_provider CHECK (auth_provider IN ('EMAIL','GOOGLE')),
  CONSTRAINT ck_users_status   CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 只存 token 的 SHA-256，明文只出現在寄出的信件裡。
-- 資料庫外洩時攻擊者無法直接拿它登入。
CREATE TABLE auth_email_tokens (
  id          VARCHAR(40)  NOT NULL,
  email       VARCHAR(320) NOT NULL,
  token_hash  VARCHAR(71)  NOT NULL,
  expires_at  DATETIME(6)  NOT NULL,
  consumed_at DATETIME(6)      NULL,
  created_ip  VARCHAR(45)      NULL,
  created_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_auth_email_tokens_hash (token_hash),
  KEY idx_auth_email_tokens_email (email, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
