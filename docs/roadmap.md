# Roadmap

## P0 — An toàn vận hành

- [ ] Thêm test authorization cho toàn bộ endpoint owner/admin, gồm trường hợp user A truy cập dữ liệu user B.
- [x] Tách cấu hình Spring profile prod; tắt SQL debug/`open-in-view`, validate schema và bắt buộc `JWT_SECRET` (2026-08).
- [ ] Thêm CI build/test cho storefront, backend và admin trên pull request.
- [ ] Quét secret/dependency định kỳ và cấu hình secret production ngoài repository.

## P1 — Tính đúng nghiệp vụ

- [ ] Chuyển cart thành server-side theo user và hỗ trợ đồng bộ đa thiết bị.
- [ ] Bổ sung pessimistic/optimistic locking tồn kho; transaction và state guard cho checkout/order đã có.
- [x] Thêm trang order detail riêng cho khách hàng (`/orders/:id`, timeline trạng thái, 2026-08).
- [ ] Chuẩn hóa shared order-math và shipping rule giữa backend/frontend.
- [ ] Clear buy-now state khi rời checkout không hoàn tất.

## P2 — Chất lượng frontend

- [ ] Tạo typed models cho product, user, cart, order, rate và API response.
- [ ] Lazy-load các nhóm route lớn.
- [ ] Thêm loading skeleton/error boundary nhất quán cho các API page còn lại.
- [ ] Viết unit/component test cho auth, cart, checkout, search và guards.
- [x] Trang tìm kiếm: pagination 12/trang đồng bộ URL, sort giá và filter (thể loại/khuyến mãi/còn hàng/khoảng giá) trên query string, panel bộ lọc thu gọn trên mobile (2026-08; unit test xanh).
- [x] Header mobile: drawer dọc có focus management/Escape/click ngoài, tìm kiếm thu gọn, theme switcher trong drawer; nút Đăng nhập đạt WCAG AA mọi theme; FAB chatbot tự né CTA qua FabCollisionService (2026-08; unit test xanh).
- [ ] Chạy visual QA đa breakpoint trong browser thật và audit accessibility tự động — **bị chặn**, xem blocker trong docs/bugs.md (cần browser thật; build + 59 unit test đã xanh).

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

Mỗi mục chỉ được đánh dấu hoàn tất sau khi behavior, test và tài liệu cùng được cập nhật.
