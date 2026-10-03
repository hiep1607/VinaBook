-- Run once before deploying with spring.jpa.hibernate.ddl-auto=validate.
CREATE TABLE IF NOT EXISTS chat_conversations (
    id VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    session_id VARCHAR(64) NOT NULL,
    state_json LONGTEXT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS chat_exchanges (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    user_text VARCHAR(1000) NOT NULL,
    response_json LONGTEXT NOT NULL,
    created_at DATETIME NULL,
    UNIQUE KEY uq_chat_exchange_request (conversation_id, request_id),
    KEY idx_chat_exchange_conversation (conversation_id, id),
    CONSTRAINT fk_chat_exchange_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversations (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_account_memories (
    user_id BIGINT NOT NULL PRIMARY KEY,
    profile_json LONGTEXT NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_chat_account_memory_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
