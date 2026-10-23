CREATE TABLE IF NOT EXISTS mail_outbox (
  id VARCHAR(36) PRIMARY KEY,
  payload LONGTEXT NOT NULL,
  state VARCHAR(12) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  due_at BIGINT NOT NULL,
  claimed_at BIGINT NOT NULL DEFAULT 0,
  claim_token VARCHAR(36),
  created_at BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  INDEX idx_mail_outbox_due (state,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
