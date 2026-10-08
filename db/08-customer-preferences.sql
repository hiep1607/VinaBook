CREATE TABLE IF NOT EXISTS customer_preferences (
 owner_id BIGINT NOT NULL PRIMARY KEY, addresses_json LONGTEXT NOT NULL, avatar LONGTEXT NOT NULL,
 imports_json LONGTEXT NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_customer_preferences_owner FOREIGN KEY(owner_id) REFERENCES users(user_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
