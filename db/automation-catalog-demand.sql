-- Additive migration. No customer conversation, session ID, email or account ID is stored.
CREATE TABLE IF NOT EXISTS catalog_demand_requests (
    id VARCHAR(64) NOT NULL,
    title VARCHAR(140) NOT NULL,
    normalized_title VARCHAR(200) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_catalog_demand_time (requested_at),
    INDEX idx_catalog_demand_title (normalized_title)
);
