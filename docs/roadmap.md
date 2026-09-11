# Roadmap

## P0 — An toàn vận hành

- [ ] Thêm test authorization cho toàn bộ endpoint owner/admin, gồm trường hợp user A truy cập dữ liệu user B.
- [x] Tách cấu hình Spring theo profile dev/prod; tắt SQL debug và `open-in-view` ở production.
- [x] Thêm CI build/test cho storefront, backend và admin trên pull request.
- [ ] Quét secret/dependency định kỳ và cấu hình secret production ngoài repository.

## P1 — Tính đúng nghiệp vụ

- [ ] Chuyển cart thành server-side theo user và hỗ trợ đồng bộ đa thiết bị.
- [ ] Thêm transaction/locking cho checkout để tránh race tồn kho.
- [ ] Thêm trang order detail riêng cho khách hàng.
- [ ] Chuẩn hóa shared order-math và shipping rule giữa backend/frontend.
- [x] Clear buy-now state khi rời checkout không hoàn tất.

## P2 — Chất lượng frontend

- [ ] Tạo typed models cho product, user, cart, order, rate và API response.
- [ ] Lazy-load các nhóm route lớn.
- [ ] Thêm loading skeleton/error boundary nhất quán cho các API page còn lại.
- [ ] Viết unit/component test cho auth, cart, checkout, search và guards.
- [ ] Chạy visual QA đa breakpoint trong browser thật và audit accessibility tự động.

## P3 — Nâng cấp nền tảng

- [ ] Nâng Angular theo từng major cho storefront/admin và bỏ `--legacy-peer-deps`.
- [ ] Nâng Spring Boot/JWT stack lên phiên bản còn được hỗ trợ.
- [ ] Thay các dependency admin cũ hoặc CommonJS nặng.

## P4 — Mở rộng sản phẩm

- [x] Tích hợp COD, VietQR, VNPay Sandbox và MoMo Test với chữ ký/callback server và mã giao dịch duy nhất.
- [ ] Thêm hoàn tiền gateway, query transaction định kỳ và đối soát ngân hàng tự động.
- [ ] Contact/ticket backend.
- [ ] Đồng bộ avatar/address/wishlist theo tài khoản.
- [ ] Email template, retry queue và trạng thái gửi.
- [ ] Bản đọc thử 5–20 trang do admin duyệt, reader 2D và mở sách 3D bằng double-click (đã có khung triển khai, còn kiểm thử tích hợp).

Mỗi mục chỉ được đánh dấu hoàn tất sau khi behavior, test và tài liệu cùng được cập nhật.
