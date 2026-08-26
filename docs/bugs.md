# Lỗi mở và nợ kỹ thuật

Tài liệu này chỉ giữ vấn đề còn hiệu lực. Các mô tả prototype catalog tĩnh/local auth đã được loại bỏ.

## Blocker cần chủ dự án quyết định

- **Sai thể loại cho product #57 «Đắc Nhân Tâm» (chưa sửa được trong MySQL đang chạy).** Sản phẩm đang thuộc «Sách Truyện, tiểu thuyết» trong MySQL đang chạy. Đã tạo script idempotent `db/04-product57-category-fix.sql` (bản seed `db/01-schema-seed.sql` chỉ chứa ID 3..55 nên không thể sửa bằng re-seed): chuyển sách có tên khớp «đắc nhân tâm» sang category «Sách Tâm lý, tâm linh, tôn giáo» (taxonomy hiện có, phù hợp chủ đề), chỉ UPDATE khi category chưa đúng, kèm câu lệnh verify. **Chưa chạy** — chờ chủ dự án xác nhận đây là database dev/disposable rồi thực thi thủ công. Sau khi chạy, breadcrumb và sách liên quan của /product/57 sẽ tự đúng vì storefront đọc category từ API.
- **Visual QA đa breakpoint trên browser thật chưa thực hiện trong phiên tự động.** Build và toàn bộ unit test chạy xanh; cần người kiểm tra thủ công các route `/`, `/books`, `/search?q=tam`, `/search?keyword=tam`, `/product/57`, `/cart`, `/login` ở 1280×720, 768×1024, 390×844, 360×800 (không console error mới, không horizontal overflow, CTA không bị che, contrast theo theme).

## Ưu tiên cao

1. **Angular 12 đã cũ.** Admin có dependency tree legacy và cảnh báo `npm audit`; cần nâng cấp theo từng major, không dùng `npm audit fix --force`.
2. **Test coverage còn mỏng.** Các bộ hiện có đã chạy xanh (backend 21, storefront 17, admin 37), nhưng phần lớn frontend vẫn là smoke test; cần thêm test hành vi cho auth, cart, checkout và authorization owner/admin.
3. **Cart chưa đồng bộ đa thiết bị.** Cart local chỉ được đẩy lên backend lúc checkout.

## Ưu tiên trung bình

4. `buyNowItem` là state in-memory; cần bảo đảm luôn clear khi user rời checkout giữa chừng.
5. Storefront còn dùng nhiều `any`, chưa có model/domain types dùng chung.
6. Tất cả page storefront đang eager-loaded; bundle có thể giảm bằng feature modules/lazy routes.
7. Contact chỉ mở email client, chưa có ticket API hoặc theo dõi trạng thái.
8. Avatar, address, wishlist và search history còn device-local.
9. Backend đang dùng Spring Boot 2.6/JJWT cũ; cần nâng theo từng bước có migration test.
10. VietQR hiện đối soát thủ công trong admin; muốn tự động cần dịch vụ webhook ngân hàng hoặc payOS/Open Banking.

## Đã xử lý gần đây

- Catalog tĩnh và các service chết đã được loại khỏi luồng runtime.
- Production Angular không còn gọi `localhost` của máy người dùng.
- JWT expiry, route guard và admin role guard đã được bổ sung.
- Reset mật khẩu dùng JWT mục đích riêng, hết hạn sau 15 phút và chỉ dùng một lần; endpoint OTP công khai đã được loại bỏ.
- Password/hash/reset token không còn xuất hiện trong response user/signin.
- User/cart/order/order detail/rate/favorites có ownership check ở backend.
- Cart detail tính lại giá từ database; order kiểm tra tồn kho.
- Checkout/chuyển trạng thái đơn chạy trong transaction, chặn chuyển trạng thái sai và không trừ kho lặp khi gọi `success` lại.
- WebSocket notification chỉ chấp nhận JWT admin và origin admin đã cấu hình; interceptor admin không gửi JWT sang Cloudinary.
- Profile/customer admin giữ nguyên mật khẩu khi để trống và luôn BCrypt mật khẩu mới ở backend.
- Profile production tắt SQL debug, `open-in-view`, dùng `ddl-auto=validate` và bắt buộc `JWT_SECRET` ngoài repository.
- Giỏ hàng tách scope user/guest và có migration từ key `cart` cũ.
- Checkout demo QR/card bị gỡ; copy COD và phí ship được đồng bộ.
- COD, VietQR, VNPay Sandbox và MoMo Test đã có luồng server-side; chữ ký và số tiền callback được xác thực trước khi đổi trạng thái.
- Footer, 404, mobile menu, loading/error/empty state và accessibility chính đã được củng cố.
