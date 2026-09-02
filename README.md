# VinaBook

VinaBook là workspace nhà sách trực tuyến gồm storefront cho khách hàng, trang quản trị và REST API dùng chung một cơ sở dữ liệu MySQL.

| Thư mục | Vai trò | Công nghệ | Cổng dev |
|---|---|---|---|
| `bookstore-ui/` | Website mua sách | Angular 12.1, SCSS | `4200` |
| `angular-admin/` | Quản trị catalog, đơn hàng, khách hàng | Angular 12, Material/Bootstrap | `4300` |
| `VinaBookStore/` | API, JWT, đơn hàng, đánh giá | Spring Boot 2.6, Java 17, MySQL | `8080` |

Storefront lấy catalog từ `GET /api/products`; vì vậy MySQL và backend phải chạy trước hai ứng dụng Angular.

## Chạy trên máy local

Yêu cầu: Node.js/npm tương thích Angular 12, Java 17 và MySQL.

### Chạy tất cả bằng một lệnh

Sau khi đã có `VinaBookStore/.env.local`, chạy file `start-vinabook.cmd` ở thư mục root. Script sẽ tự nạp cấu hình AI, mở backend, mở storefront và mở trình duyệt tại `http://localhost:4200`. Nếu backend/frontend đã chạy thì script sẽ giữ nguyên phiên hiện tại, không tạo tiến trình trùng.

```powershell
.\start-vinabook.cmd
```

Để chỉ khởi động mà không tự mở trình duyệt:

```powershell
.\start-vinabook.cmd -NoBrowser
```

1. Tạo database `book` và import `database.sql` nếu máy chưa có dữ liệu.
2. Chạy backend:

```powershell
cd VinaBookStore
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "123456"
.\mvnw.cmd spring-boot:run
```

3. Chạy storefront ở terminal khác:

```powershell
cd bookstore-ui
npm install
npm start
```

4. Chạy admin ở terminal khác:

```powershell
cd angular-admin
npm install --legacy-peer-deps
npx ng serve --port 4300
```

Tài khoản quản trị local: `admin@vinabook.vn` / `admin123`.

## Kiểm tra trước khi phát hành

```powershell
cd bookstore-ui
npm run build
npx tsc -p tsconfig.spec.json --noEmit

cd ..\VinaBookStore
.\mvnw.cmd test

cd ..\angular-admin
npm run build
```

## Cấu hình production

Hai Angular production build gọi API theo cùng origin. Web server cần:

- phục vụ storefront/admin như SPA và fallback route về `index.html`;
- proxy `/api/*` tới Spring Boot;
- proxy WebSocket `/notification` tới Spring Boot;
- đặt `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD` bằng biến môi trường;
- không dùng secret mặc định local cho môi trường public.

Thanh toán hỗ trợ COD, chuyển khoản VietQR, VNPay Sandbox và MoMo Test. COD hoạt động ngay; ba phương thức còn lại chỉ bật khi backend có đủ biến môi trường. Xem [hướng dẫn thanh toán](docs/payment.md). Phí giao hàng là `20.000đ`, miễn phí từ `300.000đ`.

Chatbot mặc định chạy nội bộ, không cần API key. Có thể đổi qua OpenAI, API tương thích OpenAI, Gemini hoặc Anthropic chỉ bằng biến môi trường backend. Xem [hướng dẫn chatbot](docs/chatbot.md).

Với khóa Google AI Studio, chỉ cần dán khóa vào `VinaBookStore/.env.local` rồi chạy `VinaBookStore/run-gemini.cmd`. Script dùng `gemini-3.1-flash-lite`, kiểm tra khả năng tạo nội dung trước khi chạy; file chứa khóa thật đã được Git ignore.

## Tài liệu

- [Tổng quan](docs/project-overview.md)
- [Kiến trúc](docs/architecture.md)
- [Tính năng](docs/features.md)
- [Routes](docs/routes.md)
- [Services](docs/services.md)
- [Dữ liệu trình duyệt](docs/localstorage.md)
- [Luồng nghiệp vụ](docs/user-flow.md)
- [Thanh toán](docs/payment.md)
- [Chatbot và AI provider](docs/chatbot.md)
- [Lỗi và giới hạn](docs/bugs.md)
- [Roadmap](docs/roadmap.md)

Các file SQL ở root là dữ liệu/migration có chủ đích, không phải build artifact. `node_modules/`, `dist/`, `target/`, cache IDE và cấu hình trợ lý local đều được ignore.
