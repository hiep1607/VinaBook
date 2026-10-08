ALTER TABLE orders ADD COLUMN payment_gateway_date VARCHAR(255) NULL;
CREATE TABLE IF NOT EXISTS payment_refunds (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT NOT NULL,request_key VARCHAR(80) NOT NULL,gateway_reference VARCHAR(32) NOT NULL,
 amount BIGINT NOT NULL,provider VARCHAR(255),reason VARCHAR(200),status VARCHAR(255),created_at DATETIME(6),updated_at DATETIME(6),staff_id BIGINT,reconciled_by BIGINT,reconciliation_reference VARCHAR(120),
 UNIQUE KEY uq_refund_request(order_id,request_key),KEY ix_refund_order(order_id),FOREIGN KEY (order_id) REFERENCES orders(orders_id));
