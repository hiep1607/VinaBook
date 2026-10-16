# Lỗi mở và nợ kỹ thuật

Tài liệu này chỉ giữ vấn đề còn hiệu lực. Các mô tả prototype catalog tĩnh/local auth đã được loại bỏ.

## Ưu tiên cao

1. **Angular 12 đã cũ.** Admin có dependency tree legacy và cảnh báo `npm audit`; cần nâng cấp theo từng major, không dùng `npm audit fix --force`.
2. **Test coverage còn mỏng ở storefront/admin.** CI chạy build và toàn bộ test Angular; storefront có kiểm thử hành vi checkout, chatbot, review và giỏ hàng, nhưng một số trang/admin vẫn chủ yếu có smoke test khởi tạo. Cần bổ sung kiểm thử tích hợp các luồng người dùng. Ngày 07/10/2026 đã sửa mock checkout thiếu `StoreKnowledgeService` và mock route chi tiết sách thiếu `queryParamMap`; toàn bộ 149 test storefront đạt trên bản source Git với dependency cài sạch bằng `npm ci`.
3. Giỏ hàng đã đồng bộ theo tài khoản sau thay đổi. Mạng gián đoạn và cập nhật cùng lúc ở nhiều thiết bị vẫn cần kiểm thử tích hợp riêng; UI yêu cầu tải lại giỏ khi có xung đột.

## Ưu tiên trung bình

4. Lựa chọn Mua ngay đã có nháp theo tài khoản trong sessionStorage (chỉ mã sách/số lượng, hạn 30 phút), khôi phục bằng giá và tồn kho mới. Tên, điện thoại và địa chỉ không nằm trong nháp này.
5. Storefront còn dùng nhiều `any`, chưa có model/domain types dùng chung.
6. Chi tiết sách và nhóm trang khách hàng đã lazy-load; bundle ban đầu vẫn vượt ngưỡng cảnh báo 600 kB, cần tối ưu thêm theo từng nhóm phụ thuộc.
7. Yêu cầu hỗ trợ đã có hội thoại với nhân viên theo ticket (polling); chưa có thông báo email/push khi nhân viên phản hồi.
8. Avatar, address và wishlist đã đồng bộ server; search history còn trên thiết bị. Thông báo có hàng hiện chỉ nằm trong tài khoản trên web.
9. Backend đang dùng Spring Boot 2.6 và `open-in-view`/SQL logging cần cấu hình rõ theo profile production.
10. VietQR hiện đối soát thủ công trong admin; muốn tự động cần dịch vụ webhook ngân hàng hoặc payOS/Open Banking.

## Đã xử lý gần đây

- 14/10/2026, giao diện tài khoản/giỏ: đơn hàng gần đây dùng thẻ có bìa sách, tên, lượng, giá đã lưu, trạng thái và thành tiền; có trạng thái tải/lỗi riêng và bỏ qua response cũ khi đổi tài khoản hoặc tải lại. Danh sách yêu thích có nút Cập nhật đồng bộ thiết kế, bìa giữ trọn ảnh. Tiến trình đơn có đường nối riêng khỏi chữ và chỉ tô đường của bước đã hoàn tất. Giỏ giữ DOM theo mã sách, không chạy lại reveal/nảy khi đổi lượng; vùng trạng thái đồng bộ nằm ngoài lưới và dành sẵn chiều cao. Mobile dùng grid cố định cho bìa/tên/lượng thay vì flex xuống dòng thất thường.
- 13/10/2026, theo báo cáo rà trải nghiệm: danh mục lọc luôn hiển thị thẻ sách; giữ lựa chọn mua qua đăng nhập/tải lại; chỉnh số lượng và cập nhật tiền ngay tại checkout, địa chỉ mặc định dạng một thẻ, thu gọn voucher/nhãn địa chỉ; chatbot mở bản xem trước khi khách nêu ý định mua rõ và trả lời câu hỏi ứng dụng bằng minh họa tách biệt nội dung sách; header điện thoại gọn, hỗ trợ gắn mã đơn, mua lại cho chọn cuốn còn hàng. Thêm API phân trang đơn theo JWT và lấy chi tiết sách theo lô, không gọi một HTTP request cho mỗi đơn. Giới hạn lượng giỏ theo tồn kho/99; chỉ báo thêm giỏ thành công sau xác nhận máy chủ.
- 16/10/2026, chuẩn bị push phần còn lại: đồng bộ spec login/search với component hiện tại; giữ lại các kiểm tra đồng bộ giỏ, chống đặt đơn lặp, chính sách giao hàng và cấu hình ChromeHeadlessCI. Khôi phục cấu hình test admin và giá trị mặc định dashboard; đăng ký không ghi JWT vào cột dành cho token đặt lại mật khẩu. Build output, harness local và ghi chú trợ lý được Git ignore.

- CI storefront: cập nhật mock theo dependency/chính sách giao hàng hiện tại, giữ các kiểm thử chống đặt lặp và thanh mua cố định; bổ sung kiểm thử phí giao hàng và chặn tạo đơn khi chính sách chưa tải được. Kiểm tra bằng đúng lệnh CI (`tsc -p tsconfig.spec.json --noEmit`, `ng test --watch=false --browsers=ChromeHeadlessCI`) thay vì chỉ chạy nhóm test chatbot.
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
