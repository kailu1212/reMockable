-- 產品埋點（Spec S-04、§15.1 的 28 個事件名）。
--
-- ⚠️ metadata 絕不得包含履歷內容、回答全文、逐字稿或任何個人資料（Spec §13.1）。
--    應用層會擋掉超過 2 KB 的 metadata。
CREATE TABLE product_events (
  id          VARCHAR(40) NOT NULL,
  user_id     VARCHAR(40)     NULL,
  name        VARCHAR(60) NOT NULL,
  mockset_id  VARCHAR(40)     NULL,
  question_id VARCHAR(40)     NULL,
  attempt_id  VARCHAR(40)     NULL,
  status      VARCHAR(20)     NULL,
  error_code  VARCHAR(50)     NULL,
  latency_ms  INT             NULL,
  metadata    JSON        NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  created_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_product_events_name (name, occurred_at),
  KEY idx_product_events_user (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
