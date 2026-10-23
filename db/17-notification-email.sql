-- Apply once before deploying with ddl-auto=validate. Existing accounts keep their account email.
ALTER TABLE automation_preferences ADD COLUMN email_address VARCHAR(254) NULL;
ALTER TABLE automation_notices ADD COLUMN email_recipient_hash VARCHAR(64) NULL;
