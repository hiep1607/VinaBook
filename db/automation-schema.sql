-- Additive migration for deployments using spring.jpa.hibernate.ddl-auto=validate.
-- Local development with ddl-auto=update creates the same tables automatically.
CREATE TABLE IF NOT EXISTS automation_notices (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  event_key VARCHAR(160) NOT NULL,
  type VARCHAR(24) NOT NULL,
  title VARCHAR(160) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  link VARCHAR(160) NOT NULL,
  product_id BIGINT NULL,
  order_id BIGINT NULL,
  seen BIT(1) NOT NULL DEFAULT b'0',
  deleted BIT(1) NOT NULL DEFAULT b'0',
  created_at DATETIME(6) NOT NULL,
  source_reference VARCHAR(80) NULL,
  email_state VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
  mail_job_id VARCHAR(36) NULL,
  CONSTRAINT uq_automation_notice_event UNIQUE (owner_id,event_key),
  INDEX idx_automation_notice_owner (owner_id,deleted,created_at),
  INDEX idx_automation_notice_unread (owner_id,seen,deleted),
  INDEX idx_automation_notice_mail (mail_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_preferences (
  owner_id BIGINT NOT NULL PRIMARY KEY,
  restock BIT(1) NOT NULL DEFAULT b'1',
  price_drops BIT(1) NOT NULL DEFAULT b'1',
  orders BIT(1) NOT NULL DEFAULT b'1',
  cart_reminders BIT(1) NOT NULL DEFAULT b'1',
  review_reminders BIT(1) NOT NULL DEFAULT b'1',
  author_releases BIT(1) NOT NULL DEFAULT b'1',
  email_enabled BIT(1) NOT NULL DEFAULT b'0',
  followed_authors LONGTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_states (
  state_key VARCHAR(80) NOT NULL PRIMARY KEY,
  value_json LONGTEXT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_rules (
  rule_key VARCHAR(24) NOT NULL PRIMARY KEY,
  label VARCHAR(160) NOT NULL,
  description VARCHAR(500) NOT NULL,
  enabled BIT(1) NOT NULL DEFAULT b'1',
  delay_hours INT NULL,
  threshold INT NULL,
  version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_runs (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  started_at DATETIME(6) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_count INT NOT NULL DEFAULT 0,
  error VARCHAR(300) NULL,
  INDEX idx_automation_runs_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
