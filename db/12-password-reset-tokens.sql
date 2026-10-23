-- Migration 12: one active, expiring, hashed reset token per account.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  user_id BIGINT NOT NULL PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_password_reset_hash (token_hash),
  KEY idx_reset_expiry (expires_at)
);
