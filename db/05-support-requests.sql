-- Dữ liệu yêu cầu đổi trả/hỗ trợ. Production chạy migration này trước khi deploy.
-- Local spring.jpa.hibernate.ddl-auto=update cũng tạo cùng bảng từ entity.
CREATE TABLE IF NOT EXISTS support_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  request_key VARCHAR(80) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  order_id BIGINT NULL,
  product_id BIGINT NULL,
  received_on DATE NULL,
  outcome VARCHAR(16) NULL,
  message VARCHAR(2000) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reply VARCHAR(2000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_support_owner_key UNIQUE (owner_id, request_key),
  INDEX ix_support_owner_id (owner_id, id),
  INDEX ix_support_status_id (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
