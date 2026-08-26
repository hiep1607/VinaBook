# VinaBook Admin

Trang quản trị Angular 12 cho catalog, tồn kho, đơn hàng, đánh giá, khách hàng và thống kê.

## Chạy local

Backend phải chạy tại `http://localhost:8080`.

```powershell
npm install --legacy-peer-deps
npx ng serve --port 4300
```

Mở `http://localhost:4300` và đăng nhập bằng tài khoản có `ROLE_ADMIN`.

Tài khoản seed local: `admin@vinabook.vn` / `admin123`.

## Quản trị trang chủ

Mở `http://localhost:4300/admin/homepage` hoặc chọn **Giao diện → Trang chủ**. Màn hình này chỉnh nội dung hero, sách nổi bật, lợi ích, các dải sản phẩm/thống kê, category ưu tiên, CTA và trạng thái hiển thị. Cấu hình được lưu qua `PUT /api/homepage`; cần đăng nhập tài khoản có `ROLE_ADMIN`.

## Kiểm tra

```powershell
npm run build
npm test
```

Production build dùng API cùng origin và WebSocket `/notification`; reverse proxy phải chuyển cả hai đường dẫn về Spring Boot.

Project vẫn ở Angular 12 nên `npm install` cần `--legacy-peer-deps`; không chạy `npm audit fix --force` nếu chưa có kế hoạch nâng cấp framework và kiểm thử hồi quy.
