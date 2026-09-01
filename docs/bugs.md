# Lỗi mở và nợ kỹ thuật

Tài liệu này chỉ giữ vấn đề còn hiệu lực. Các mô tả prototype catalog tĩnh/local auth đã được loại bỏ.

## Ưu tiên cao

1. **Angular 12 đã cũ.** Admin có dependency tree legacy và cảnh báo `npm audit`; cần nâng cấp theo từng major, không dùng `npm audit fix --force`.
2. **Test coverage còn mỏng.** Backend hiện có smoke/security test cơ bản; storefront/admin phần lớn mới dừng ở compile/build. Storefront compile spec thành công nhưng 15 smoke spec cũ còn thiếu `HttpClientTestingModule` hoặc service mock nên thất bại khi khởi tạo TestBed.
3. **Cart chưa đồng bộ đa thiết bị.** Cart local chỉ được đẩy lên backend lúc checkout.

## Ưu tiên trung bình

4. `buyNowItem` là state in-memory; cần bảo đảm luôn clear khi user rời checkout giữa chừng.
5. Storefront còn dùng nhiều `any`, chưa có model/domain types dùng chung.
6. Tất cả page storefront đang eager-loaded; bundle có thể giảm bằng feature modules/lazy routes.
7. Contact chỉ mở email client, chưa có ticket API hoặc theo dõi trạng thái.
8. Avatar, address, wishlist và search history còn device-local.
9. Backend đang dùng Spring Boot 2.6 và `open-in-view`/SQL logging cần cấu hình rõ theo profile production.
10. VietQR hiện đối soát thủ công trong admin; muốn tự động cần dịch vụ webhook ngân hàng hoặc payOS/Open Banking.

## Đã xử lý gần đây

- Catalog tĩnh và các service chết đã được loại khỏi luồng runtime.
- Production Angular không còn gọi `localhost` của máy người dùng.
- JWT expiry, route guard và admin role guard đã được bổ sung.
- Password/hash/reset token không còn xuất hiện trong response user/signin.
- User/cart/order/order detail/rate có ownership check ở backend.
- Cart detail tính lại giá từ database; order kiểm tra tồn kho.
- Giỏ hàng tách scope user/guest và có migration từ key `cart` cũ.
- Checkout demo QR/card bị gỡ; copy COD và phí ship được đồng bộ.
- COD, VietQR, VNPay Sandbox và MoMo Test đã có luồng server-side; chữ ký và số tiền callback được xác thực trước khi đổi trạng thái.
- Footer, 404, mobile menu, loading/error/empty state và accessibility chính đã được củng cố.
