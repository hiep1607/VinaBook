# AGENTS.md — VinaBook Workspace

## Phạm vi

Workspace có ba ứng dụng liên quan:

- `bookstore-ui/`: storefront Angular 12.1, dự án giao diện chính.
- `angular-admin/`: admin Angular 12 tại `http://localhost:4300`.
- `VinaBookStore/`: Spring Boot 2.6/Java 17 tại `http://localhost:8080`.

Catalog, tài khoản, đơn hàng và đánh giá dùng backend/MySQL. Địa chỉ, avatar, wishlist và giỏ hàng đồng bộ theo tài khoản trên backend; localStorage giữ cache và bản sao migration. Lịch sử tìm kiếm còn lưu trên thiết bị. Có trò chuyện nhân viên, voucher và thông báo có hàng trong tài khoản.

Đọc `docs/bugs.md` trước khi sửa auth/cart/checkout. Khi thay đổi hành vi, cập nhật tài liệu liên quan trong `docs/`.

## Lệnh thường dùng

```powershell
# Storefront
cd bookstore-ui
npm start
npm run build
npx tsc -p tsconfig.spec.json --noEmit

# Backend
cd VinaBookStore
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run

# Admin
cd angular-admin
npm install --legacy-peer-deps
npx ng serve --port 4300
npm run build
```

Storefront cần backend để hiển thị sách. Thứ tự chạy: MySQL → backend → Angular.

## Kiến trúc storefront

- Angular NgModule truyền thống; không dùng standalone component hoặc signal.
- Routes nằm trong `app-routing.module.ts`; page trong `pages/`, UI tái sử dụng trong `components/`, directive/toast trong `shared/`.
- Form dùng template-driven forms và chuỗi hiển thị phải là tiếng Việt.
- API base lấy từ `environment.ts`; production dùng URL tương đối và reverse proxy.
- `ProductService` là nguồn catalog duy nhất. Không tạo lại catalog tĩnh.
- `AuthGuard` bảo vệ checkout, profile, orders và wishlist.
- Kiểu dữ liệu hiện còn nhiều `any`; thêm interface cho code mới nhưng không mass-refactor ngoài phạm vi.
- Nếu template gọi service trực tiếp, dependency trong constructor phải là `public`.
- Theme mặc định là editorial (nền sáng, ink gần đen, accent terracotta); người dùng có thể đổi ocean/forest/berry bằng theme switcher.

## localStorage đang dùng

| Key | Nội dung |
|---|---|
| `currentUser` | Phiên JWT, không chứa password/hash |
| `avatar_{email}` | Avatar base64 theo tài khoản |
| `addresses_{email}` | Địa chỉ giao hàng theo tài khoản |
| `cart_{email}` / `cart_guest` | Giỏ hàng theo scope |
| `cart_migration_owner` | Đánh dấu migration giỏ hàng cũ |
| `wishlist_{email}` | Mảng product ID |
| `search_history` | Tối đa 8 truy vấn gần đây |
| `theme` | ID theme giao diện hiện tại |

Không phá contract cũ nếu chưa có migration an toàn. Catalog ID hiện là ID trong MySQL; dữ liệu localStorage từ catalog tĩnh cũ có thể không còn hợp lệ.

## Quy tắc sửa đổi

1. Không đưa `node_modules`, `dist`, `target`, cache Python/IDE, cấu hình trợ lý local hoặc secret vào Git.
2. Không commit skill dành cho công cụ vào source ứng dụng; chỉ giữ tài liệu dự án có người dùng thực tế.
3. Giữ một nguồn dữ liệu cho mỗi concern; không tạo service trùng auth/order/review/catalog.
4. Backend phải xác minh owner từ JWT, không tin `email`, `userId`, `cartId` do client gửi.
5. Không trả password, reset token hoặc dữ liệu nhạy cảm trong response.
6. Thanh toán gồm COD, VietQR đối soát admin, VNPay Sandbox và MoMo Test. Secret chỉ nằm trong biến môi trường; callback phải xác thực chữ ký, mã đơn và số tiền ở backend.
7. Cập nhật đồng thời copy phí vận chuyển: `20.000đ`, miễn phí từ `300.000đ`.
8. Không sửa admin/backend như tác dụng phụ khi task chỉ giới hạn storefront.
9. Luôn build/test phần đã chạm trước khi push.

Xem [README.md](README.md) và [docs/project-overview.md](docs/project-overview.md) để bắt đầu.
