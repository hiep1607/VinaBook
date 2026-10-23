# VinaBook

VinaBook là workspace nhà sách trực tuyến gồm storefront cho khách hàng, trang quản trị và REST API dùng chung một cơ sở dữ liệu MySQL.

| Thư mục | Vai trò | Công nghệ | Cổng dev |
|---|---|---|---|
| `bookstore-ui/` | Website mua sách | Angular 12.1, SCSS | `4200` |
| `angular-admin/` | Quản trị catalog, đơn hàng, khách hàng | Angular 12, Material/Bootstrap | `4300` |
| `VinaBookStore/` | API, JWT, đơn hàng, đánh giá | Spring Boot 2.6, Java 17, MySQL | `8080` |

Storefront lấy catalog từ `GET /api/products`; vì vậy MySQL và backend phải chạy trước hai ứng dụng Angular.

## Chạy trên máy local

### Chạy nhanh trên Windows

Bấm đúp **`start-vinabook.cmd`** trong thư mục gốc dự án. Không cần sửa đường dẫn nếu di chuyển thư mục.

- Cần MySQL chạy trên cổng `3306`, database `book` đã có dữ liệu, JDK 17 và Node/npm (đã kiểm tra với Node 16.20.2, npm 8.19.4).
- Script tìm JDK từ `JAVA_HOME` hoặc Java trên `PATH`; không cần đặt lại mỗi terminal.
- Backend được clean/build trong `VinaBookStore/target-runtime`, tách khỏi `target` của IDE để tránh controller `.class` cũ. Không dùng `maven.test.skip`.
- Frontend dùng `npm ci` với lockfile khi dependency thiếu hoặc thay đổi; các lần sau không cài lại nếu dependency hợp lệ.
- Chờ backend, bundle Angular và API proxy trả lời thành công rồi mới mở `http://localhost:4200`.
- Nếu đã chạy, script dùng lại dịch vụ; nếu cổng bị chiếm hoặc có lỗi, script báo lỗi thay vì tự tắt tiến trình khác.
- Dịch vụ chạy trong hai cửa sổ thu nhỏ. Đóng cửa sổ dịch vụ để dừng. Log nằm trong `.run-logs/backend.log`, `.run-logs/storefront.log`, `.run-logs/install.log`.
- MySQL chưa chạy: mở **Services**, Start dịch vụ MySQL rồi chạy lại. Script không tự cài MySQL, tạo/import/xóa database hoặc yêu cầu quyền admin.
- Launcher nạp cấu hình backend từ `VinaBookStore/.env.local`, gồm `JWT_SECRET`, `AI_PROVIDER`, `AI_MODEL` và các khóa AI; không ghi giá trị bí mật vào log. Biến môi trường đã đặt trước khi chạy được ưu tiên hơn file.
- Có thể đặt `DB_USERNAME`, `DB_PASSWORD` trước khi chạy nếu khác cấu hình local. Đặt `JWT_SECRET` riêng trong file cấu hình hoặc biến môi trường.
- Launcher kiểm tra `/api/chat/status` và báo lỗi nếu backend đang chạy khác provider đã cấu hình hoặc AI chưa được bật. Khi thay cấu hình, khởi động lại backend; script không tự tắt dịch vụ đang chạy.
- Chỉ chạy storefront + backend; admin vẫn chạy riêng theo hướng dẫn bên dưới.

**Giới hạn dependency:** Angular 12 đã cũ. Lucide được khóa ở `1.0.0`; `.npmrc` lưu `legacy-peer-deps=true` vì peer range của thư viện yêu cầu Angular mới hơn. Đây là cấu hình tương thích tạm đã kiểm tra build, không phải nâng cấp Angular hay khắc phục toàn bộ cảnh báo dependency. Không dùng `npm audit fix --force`.

### Chạy thủ công

Yêu cầu: Node.js/npm tương thích Angular 12, Java 17 và MySQL.

1. Tạo database `book` và import bộ seed đã thay dữ liệu cá nhân theo [`db/README.md`](db/README.md) nếu máy chưa có dữ liệu.
2. Chạy backend:

```powershell
cd VinaBookStore
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "123456"
$jwtBytes = New-Object byte[] 64
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
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

Tài khoản seed local: `admin@vinabook.example` / `ChangeMe123!`. Chỉ dùng để phát triển; đổi tài khoản và mật khẩu khi triển khai.

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

Với Google AI Studio, điền tối đa bốn khóa vào `GEMINI_API_KEY_1`, `_2`, `_3`, `_4` trong `VinaBookStore/.env.local`, rồi chạy `VinaBookStore/run-gemini.cmd`. Script dùng `gemini-3.1-flash-lite`; file chứa khóa thật đã được Git ignore. Backend tự chuyển khóa khi gặp giới hạn của từng project và trả lời bằng dữ liệu cửa hàng nếu AI không khả dụng.

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

Bộ seed chia sẻ nằm trong `db/` và đã thay dữ liệu cá nhân bằng dữ liệu mẫu. `database.sql` là dump local, không đưa lên Git. `node_modules/`, `dist/`, `target/`, cache IDE và cấu hình trợ lý local đều được ignore.

## Vận hành và bảo trì

Các thay đổi bảo mật, nháp checkout, trạng thái AI, lịch sử phân trang và profile production được mô tả trong [docs/operations.md](docs/operations.md). Migration mới nằm ở db/12–14; không chạy lại seed trên database đang có dữ liệu. Kế hoạch nâng framework riêng nằm ở [docs/dependency-upgrade-plan.md](docs/dependency-upgrade-plan.md).
