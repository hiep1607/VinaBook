# VinaBook API

Backend Spring Boot 2.6/Java 17 cho storefront và admin.

## Chức năng

- REST API dưới `/api/*`.
- Homepage CMS: `GET /api/homepage` công khai; `PUT /api/homepage` chỉ dành cho `ROLE_ADMIN`, lưu trong bảng `homepage_config`.
- JWT với vai trò `ROLE_USER` và `ROLE_ADMIN`.
- Catalog, category, cart checkout, order, review và thống kê qua JPA/MySQL.
- WebSocket tại `/notification`.
- Email xác nhận/quên mật khẩu khi cấu hình SMTP.
- Bản đọc thử sách: admin quản lý 5–20 trang (ảnh hoặc văn bản) tại `/api/admin/products/{id}/preview`; khách chỉ nhận đúng các trang đã bật tại `/api/products/{id}/preview`.

## Chạy local

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "123456"
.\mvnw.cmd spring-boot:run
```

Mặc định ứng dụng kết nối database `book` ở `localhost:3306` và chạy cổng `8080`.

## Kiểm tra và đóng gói

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
java -jar target\VinaBook-1.0.jar
```

## Biến môi trường production

Bật `SPRING_PROFILES_ACTIVE=prod`. Profile này không có credential mặc định, tắt SQL log/Open Session in View và dùng `ddl-auto=validate`.

| Biến | Mục đích |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Đặt thành `prod` khi triển khai |
| `DB_URL` | JDBC URL đầy đủ của MySQL production |
| `DB_USERNAME` | Tài khoản MySQL |
| `DB_PASSWORD` | Mật khẩu MySQL |
| `JWT_SECRET` | Khóa ký JWT riêng cho môi trường |
| `FRONTEND_URL` | Origin public của storefront |
| `BACKEND_PUBLIC_URL` | Origin public của backend/callback thanh toán |
| `MAIL_USERNAME` | Tài khoản SMTP |
| `MAIL_PASSWORD` | Mật khẩu/app password SMTP |

Không dùng giá trị dev mặc định khi public. Các endpoint dữ liệu người dùng phải lấy danh tính từ JWT và kiểm tra owner ở backend.

Trước khi deploy production, chạy một lần script `src/main/resources/db/product-preview.sql`. Script tạo hai cột trạng thái đọc thử trên `products` và bảng `product_preview_pages`; profile production dùng `ddl-auto=validate` nên không tự tạo schema.
