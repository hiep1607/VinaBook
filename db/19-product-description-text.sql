-- Preserve rich book descriptions when initializing or upgrading a database.
-- Select and verify the target database before applying; this does not alter catalog values.
ALTER TABLE products MODIFY COLUMN description TEXT NULL;
