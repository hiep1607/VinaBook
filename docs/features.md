# Tính năng hiện có

## Storefront

- Trang chủ với sách mới, bán chạy và category nổi bật lấy từ API.
- Nội dung trang chủ là cấu hình động: hero, sách hero, dải lợi ích, tiêu đề các khu vực, thống kê, category ưu tiên, CTA và trạng thái ẩn/hiện được đọc từ `GET /api/homepage`. Nếu API cấu hình tạm lỗi, storefront dùng nội dung mặc định an toàn.
- Danh sách tất cả sách, sách mới, bán chạy và khuyến mãi; tìm kiếm không dấu, sắp xếp và phân trang.
- Điều hướng category động theo `categoryId`.
- Chi tiết sách, tồn kho, số lượng mua, đánh giá trung bình và review chung từ backend.
- Đăng ký/đăng nhập JWT, kiểm tra hết hạn phiên và redirect về route ban đầu.
- Giỏ hàng theo tài khoản/guest, chọn từng dòng, giới hạn số lượng và buy-now.
- Checkout bằng địa chỉ mới hoặc địa chỉ đã lưu; COD, VietQR, VNPay Sandbox và MoMo Test; phí ship đồng nhất; gửi order lên backend.
- Trang kết quả thanh toán có QR, trạng thái tự làm mới, copy thông tin chuyển khoản và thanh toán lại khi giao dịch online thất bại.
- Lịch sử đơn hàng hiển thị riêng trạng thái vận chuyển và thanh toán, hỗ trợ mở lại QR hoặc cổng thanh toán.
- Wishlist theo tài khoản.
- Profile: thông tin cá nhân, avatar local, địa chỉ, đổi mật khẩu, thống kê và đơn gần đây.
- Tìm kiếm realtime, lịch sử tối đa 8 truy vấn.
- Toast toàn cục, chatbot hội thoại có gợi ý sản phẩm từ catalog thật, trang giới thiệu, liên hệ qua email app và trang 404.
- Header responsive, menu mobile, footer dùng chung và hỗ trợ keyboard/reduced motion ở các control chính.
- Bộ đổi màu giao diện gồm editorial, ocean, forest và berry; lựa chọn được lưu trên thiết bị.
- Giao diện storefront dùng hệ token modern editorial bám màu xanh logo, container 1280px thống nhất, chuyển cảnh route ngắn và scroll reveal bằng IntersectionObserver. Bìa sách dùng `object-fit: contain` để không cắt nội dung.

## Admin

- Màn **Giao diện → Trang chủ** tại `/admin/homepage` quản lý toàn bộ nội dung đang hiển thị trên homepage.
- Có thể bật/tắt từng khối, chọn tối đa 5 sách cho hero và 8 category ưu tiên; để trống sẽ dùng dữ liệu bán chạy/category đang hoạt động.
- Nút xem cửa hàng, theo dõi thay đổi chưa lưu, khôi phục mặc định và lưu cấu hình trực tiếp vào backend.

- Dashboard và thống kê doanh thu/category/tồn kho.
- CRUD product/category/customer.
- Quản lý đơn hàng, trạng thái thanh toán; đối soát thủ công chuyển khoản QR và chặn giao đơn online chưa thanh toán.
- Quản lý review/rate.
- JWT guard yêu cầu tài khoản có `ROLE_ADMIN`.
- WebSocket notification từ backend.

## Backend

- REST API cho auth, product, category, cart/cart detail, order/order detail, rate, favorites, notification và thống kê.
- BCrypt, JWT, role-based access và ownership check.
- Tính lại giá cart detail từ database, kiểm tra tồn kho, cập nhật sold và tổng đơn.
- Email xác nhận/quên mật khẩu khi SMTP được cấu hình.
- WebSocket `/notification`.
- Tạo URL và xác thực callback/IPN VNPay bằng HMAC-SHA512; tạo giao dịch và xác thực callback MoMo bằng HMAC-SHA256.
- Trạng thái thanh toán lưu riêng trên order, callback kiểm tra chữ ký, mã tham chiếu và số tiền trước khi ghi nhận.
- Gateway chatbot chuẩn hóa OpenAI Responses, API tương thích OpenAI Chat Completions, Gemini và Anthropic; tự chuyển về bộ trả lời nội bộ khi chưa có key hoặc provider lỗi.
- Prompt chatbot được grounding bằng catalog MySQL; endpoint giới hạn độ dài, lịch sử và tần suất theo IP, không công khai API key.

## Chưa hỗ trợ

- Hoàn tiền tự động và API đối soát ngân hàng tự động cho chuyển khoản VietQR.
- Backend contact/ticket; form liên hệ hiện mở ứng dụng email.
- Đồng bộ realtime giỏ hàng/wishlist giữa nhiều thiết bị.
- Social login và refresh token.
