# VinaBook API

Backend Spring Boot 2.6/Java 17 cho storefront và admin.

## Chức năng

- REST API dưới `/api/*`.
- Homepage CMS: `GET /api/homepage` công khai; `PUT /api/homepage` chỉ dành cho `ROLE_ADMIN`, lưu trong bảng `homepage_config`.
- JWT với vai trò `ROLE_USER` và `ROLE_ADMIN`.
- Catalog, category, cart checkout, order, review và thống kê qua JPA/MySQL.
- WebSocket admin tại `/notification`, xác thực JWT trong handshake.
- Email xác nhận/quên mật khẩu khi cấu hình SMTP.

## Chạy local

Backend bắt buộc có `JWT_SECRET` — thiếu biến môi trường này ứng dụng sẽ không khởi động được. Sinh secret ngẫu nhiên ngay trong bộ nhớ PowerShell (không lưu vào file nào trong repo):

```powershell
$jwtBytes = New-Object byte[] 64
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "123456"
.\mvnw.cmd spring-boot:run
```

Mỗi terminal mới cần đặt lại `$env:JWT_SECRET`.

Mặc định ứng dụng kết nối database `book` ở `localhost:3306` và chạy cổng `8080`.

## Kiểm tra và đóng gói

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
java -jar target\VinaBook-1.0.jar
```

## Biến môi trường production

| Biến | Mục đích |
|---|---|
| `DB_USERNAME` | Tài khoản MySQL |
| `DB_PASSWORD` | Mật khẩu MySQL (**bắt buộc**, không có mặc định) |
| `JWT_SECRET` | Khóa ký JWT riêng cho môi trường (**bắt buộc**) |
| `FRONTEND_URL` | Origin storefront dùng cho thanh toán (**bắt buộc**) |
| `BACKEND_PUBLIC_URL` | Origin HTTPS public dùng trong link reset mật khẩu/thanh toán (**bắt buộc**) |
| `ADMIN_FRONTEND_URL` | Origin được phép mở WebSocket admin (**bắt buộc**) |
| `MAIL_USERNAME` | Tài khoản SMTP |
| `MAIL_PASSWORD` | Mật khẩu/app password SMTP |

Chạy production với `SPRING_PROFILES_ACTIVE=prod`; profile này tắt SQL debug/`open-in-view`, chỉ validate schema và fail-fast khi thiếu bất kỳ biến bắt buộc nào (`JWT_SECRET`, `DB_PASSWORD`, `FRONTEND_URL`, `BACKEND_PUBLIC_URL`, `ADMIN_FRONTEND_URL`). Không dùng giá trị dev mặc định khi public. Các endpoint dữ liệu người dùng phải lấy danh tính từ JWT và kiểm tra owner ở backend.
