CREATE TABLE IF NOT EXISTS support_messages (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id BIGINT NOT NULL, sender_id BIGINT NOT NULL, client_key VARCHAR(80) NOT NULL,
 staff BIT NOT NULL, content VARCHAR(2000) NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT uq_support_message_key UNIQUE(request_id,sender_id,client_key),
 INDEX ix_support_message_request(request_id,id),
 CONSTRAINT fk_support_message_request FOREIGN KEY(request_id) REFERENCES support_requests(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
