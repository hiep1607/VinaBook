-- Migration 13: 255 street characters plus the recipient label/name.
ALTER TABLE orders MODIFY COLUMN address VARCHAR(400);
