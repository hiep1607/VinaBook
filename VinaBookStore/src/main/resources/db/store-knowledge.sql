-- Apply once before deployment with ddl-auto=validate.
CREATE TABLE store_settings (
  id BIGINT NOT NULL PRIMARY KEY,
  shipping_fee BIGINT NOT NULL,
  free_shipping_threshold BIGINT NOT NULL,
  delivery_min_days INT NOT NULL,
  delivery_max_days INT NOT NULL,
  updated_at DATETIME NULL
);
INSERT INTO store_settings(id,shipping_fee,free_shipping_threshold,delivery_min_days,delivery_max_days,updated_at)
VALUES (1,20000,300000,2,4,NOW());
CREATE TABLE store_policies (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  policy_key VARCHAR(32) NOT NULL,
  version INT NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  effective_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  approved BIT(1) NOT NULL,
  UNIQUE KEY uk_store_policy_version (policy_key, version)
);
INSERT INTO store_policies(policy_key,version,title,body,effective_at,updated_at,approved) VALUES
('returns',1,'Đổi trả','Sách lỗi in, rách hoặc giao sai được hỗ trợ đổi trả trong 7 ngày kể từ khi nhận hàng. Bạn giữ hóa đơn và liên hệ trang Hỗ trợ.',NOW(),NOW(),b'1'),
('cancellation',1,'Hủy đơn','Đơn COD chưa thanh toán và đang chờ xử lý có thể hủy; đơn đang giao, hoàn thành hoặc thanh toán trực tuyến cần liên hệ cửa hàng.',NOW(),NOW(),b'1'),
('support',1,'Liên hệ hỗ trợ','Bạn có thể gửi yêu cầu tại trang Liên hệ hoặc email hotro@vinabook.vn. Không gửi mật khẩu, OTP hay thông tin thẻ.',NOW(),NOW(),b'1');
