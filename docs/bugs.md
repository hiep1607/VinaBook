# Lỗi mở và nợ kỹ thuật

Tài liệu này chỉ giữ vấn đề còn hiệu lực. Các mô tả prototype catalog tĩnh/local auth đã được loại bỏ.

## Ưu tiên cao

1. **Angular 12 đã cũ.** Admin có dependency tree legacy và cảnh báo `npm audit`; cần nâng cấp theo từng major, không dùng `npm audit fix --force`.
2. **Test coverage còn mỏng ở storefront/admin.** Backend có kiểm thử hồi quy chatbot; storefront/admin phần lớn mới dừng ở compile/build. Ngày 02/10/2026, `tsc -p tsconfig.spec.json --noEmit` thất bại ở spec login/search tham chiếu thuộc tính và handler đã bỏ (`showPassword`, `totalPages`, `pagedProducts`, ...). Một số smoke spec cũ cũng thiếu `HttpClientTestingModule` hoặc service mock. Build ứng dụng storefront vẫn thành công; cần cập nhật riêng các spec này theo giao diện hiện tại.
3. **Cart chưa đồng bộ đa thiết bị.** Cart local chỉ được đẩy lên backend lúc checkout.

## Ưu tiên trung bình

4. `buyNowItem` là state in-memory; cần bảo đảm luôn clear khi user rời checkout giữa chừng.
5. Storefront còn dùng nhiều `any`, chưa có model/domain types dùng chung.
6. Tất cả page storefront đang eager-loaded; bundle có thể giảm bằng feature modules/lazy routes.
7. Yêu cầu hỗ trợ/đổi trả đã có API, lịch sử trạng thái và phản hồi từ admin; chưa có chat trực tiếp với nhân viên hoặc thông báo chủ động khi có phản hồi.
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
- Chatbot đã gom rule về backend, có intent eval tiếng Việt, follow-up context, SSE/fallback transport, action xác nhận thêm giỏ, tra đơn theo principal, feedback và metrics admin không lưu nội dung chat.
- Hủy đơn, yêu thích, mua lại, yêu cầu đổi trả và hỗ trợ nhân viên dùng chung chức năng web với chat. Thao tác thay đổi đơn/gửi yêu cầu có bước khách xem lại và xác nhận; hỗ trợ là yêu cầu được lưu, chưa phải hội thoại trực tiếp với nhân viên.
- Đánh giá sách trong chat dùng chung nhận xét, biểu mẫu và API với trang chi tiết sách; yêu cầu đọc/viết được làm rõ, nội dung chỉ lưu khi khách tự gửi.
- Rà soát đánh giá ngày 07/10/2026: POST không nhận ID của client để ghi đè rate, POST/PUT khóa owner từ JWT khi upsert; điểm sao/cache thống nhất web và chat, form có timeout và bảo vệ đổi ngữ cảnh. Giới hạn 2.000 ký tự đã đồng bộ schema local bằng `db/06-review-comment-length.sql`; database triển khai cần migration tương ứng.
