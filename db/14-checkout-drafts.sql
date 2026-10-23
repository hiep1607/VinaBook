CREATE TABLE IF NOT EXISTS checkout_drafts (
  token VARCHAR(36) NOT NULL PRIMARY KEY,
  owner_email VARCHAR(255) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  expires_at BIGINT NOT NULL,
  order_id BIGINT NULL,
  version BIGINT NULL,
  KEY idx_checkout_draft_owner(owner_email),
  KEY idx_checkout_draft_expiry(expires_at)
);
