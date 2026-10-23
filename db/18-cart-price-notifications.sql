-- Apply once before deploying with schema validation. The new category requires customer opt-in.
ALTER TABLE automation_preferences ADD COLUMN cart_price_changes BOOLEAN NOT NULL DEFAULT FALSE;
