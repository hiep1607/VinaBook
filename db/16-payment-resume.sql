-- Apply once before deploying the updated backend with ddl-auto=validate.
-- Store the existing gateway checkout URL so reopening a pending order reuses its transaction.
ALTER TABLE orders ADD COLUMN payment_checkout_url VARCHAR(2048) NULL;
