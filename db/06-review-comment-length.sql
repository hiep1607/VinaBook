-- Expand review comments to match the 2,000-character API/form limit.
-- Run against the selected application database before deploying this change.
-- Existing rows are retained. Re-running this migration is harmless.
SET @review_comment_size = (
  SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rates' AND COLUMN_NAME = 'comment'
);
SET @review_comment_ddl = IF(@review_comment_size < 2000,
  'ALTER TABLE rates MODIFY COLUMN comment VARCHAR(2000) DEFAULT NULL',
  'SELECT ''Review comment column already supports 2000 characters'' AS migration_status'
);
PREPARE review_comment_migration FROM @review_comment_ddl;
EXECUTE review_comment_migration;
DEALLOCATE PREPARE review_comment_migration;
