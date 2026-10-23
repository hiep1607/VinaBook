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
8. Avatar, address và wishlist đã đồng bộ server; search history còn trên thiết bị. Trung tâm thông báo và các luồng tự động hóa đã lưu server; email chỉ hoạt động khi cấu hình SMTP, bật AUTOMATION_EMAIL_ENABLED và khách đồng ý nhận. Giới hạn quét theo lô/mốc thời gian quan sát được ghi trong automations.md.
9. Backend còn Spring Boot 2.6; kế hoạch nâng major nằm trong dependency-upgrade-plan.md. Production đã tắt open-in-view/SQL logging và dùng schema validation. Email đã có outbox mã hóa bền vững; khóa chat nhiều instance vẫn là cổng triển khai ở quy mô lớn.
10. VietQR hiện đối soát thủ công trong admin; muốn tự động cần dịch vụ webhook ngân hàng hoặc payOS/Open Banking.
11. VNPay/MoMo hiện là sandbox/test, chưa xác minh giao dịch với merchant thật. Đơn PENDING đã có phiên sẽ dùng lại phiên đó; trường hợp tạo giao dịch mất phản hồi hoặc phiên đã hết hạn cần đối soát, chưa tự truy vấn gateway để mở phiên mới.

## Đã xử lý gần đây

- 23/10/2026: API admin đánh dấu thông báo đã đọc giữ transaction đến khi cập nhật xong, tránh lazy proxy hết session khi production tắt open-in-view. Đánh dấu tất cả dùng một transaction và giao diện chỉ tải lại số chưa đọc sau khi máy chủ hoàn tất.

- 23/10/2026: Nút chuông và menu tài khoản của admin không mở do runtime Bootstrap thiếu Popper. Cấu hình build đã chuyển sang bootstrap.bundle.min.js chứa Popper cùng phiên bản; giữ jQuery nạp trước và không thay đổi API/dữ liệu thông báo.

- 22/10/2026: kiểm thử trực tiếp phát hiện thay đổi giá ngay sau thêm giỏ/yêu thích có thể xảy ra trước baseline của scanner. Giỏ đồng bộ và API yêu thích nay ghi mốc giá trong cùng giao dịch thêm sách; bật lại loại tin bắt đầu từ giá hiện tại. Kiểm tra hồi quy bao phủ đổi giá trước lần quét đầu, giữ sự kiện khi sửa giỏ và không gửi bù lúc bật lại.

- 21/10/2026: checkout hiện đủ COD/VietQR/VNPay/MoMo và trạng thái khả dụng; khóa gửi lại sau khi tạo đơn; khôi phục thanh toán đúng đơn sau lỗi mở cổng. Kết quả thanh toán xử lý riêng COD, đơn đã hủy/hoàn tất, QR lỗi, chờ đối soát và phiên tài khoản thay đổi. Request/polling được hủy, giới hạn và có kiểm tra thủ công. Lưu phiên gateway trên đơn để tránh tạo lại giao dịch đang PENDING; khóa dòng khi cập nhật, không đánh dấu FAILED chỉ vì mất phản hồi tạo MoMo. Xem payment.md và migration 16-payment-resume.sql.

- 19/10/2026, sửa luồng bị ép menu khi hỏi “hi bạn có thể giúp gì cho tôi”: AI đọc ý trước công cụ, thêm reply hội thoại tự nhiên không truy xuất catalog/giỏ, giữ ngữ cảnh đang chọn sách/mua. Giải nghĩa/dịch phần chữ đã có và ý tưởng đọc chung không bị chuyển thành tìm sách. Các lệnh/tham chiếu nội bộ chuyển sang dự phòng lúc provider lỗi; chỉ đọc giỏ server khi công cụ cần. Giới hạn bước đọc ý15 giây; giá/tồn kho/quyền và xác nhận thao tác vẫn do backend kiểm tra. Hợp đồng và giới hạn nằm trong chatbot-structured-pipeline.md.

- 19/10/2026, xử lý Q01–Q07 của đợt đánh giá chatbot: hỏi nội dung chung không bị hiểu thành tên chủ đề; câu hỏi đích danh sách ưu tiên hơn kết quả tìm rỗng cũ; giữ sách và đổi lượng mua trước đăng nhập, kể cả sửa lại sau lượng không hợp lệ; menu bám nhu cầu đã nói và chấp nhận một lựa chọn phù hợp; giữ số lượng/ngân sách tổng/loại trừ trong các nhánh cơ bản khi AI lỗi; tra cứu tên sách rõ ràng không phụ thuộc AI; thông báo thiếu mục lục đúng ấn bản; hướng dẫn tra đơn theo tài khoản khi khách nhập email; giữ nút SHOW_CART sau tải lại. Câu chưa hiểu khi nhà cung cấp lỗi không ghi đè ngữ cảnh đọc trước đó. Nhóm liên quan đạt 365 kiểm tra backend, 35 kiểm tra storefront; build storefront đạt với các cảnh báo kích thước hiện có. Xem chatbot.md cho giới hạn dữ liệu và phạm vi.

- 17/10/2026, thực hiện báo cáo rà soát: chặn tài khoản khóa/JWT cũ; reset token hash15 phút dùng một lần; đóng OTP cũ; admin socket dùng vé một lần và chỉ server phát sau commit; giao dịch memory/commit RAM và chống hồi sinh lịch sử; tư vấn cơ bản khi Gemini lỗi; khôi phục action mua sau reload; budget embedding, SSE có tiến độ/cancel và deadline chung; lịch sử phân trang/metrics SQL/retention cấu hình; retry trang chủ, counter theo input; validator giao hàng chung và nháp checkout bền vững. Tải chatbot khi mở giảm bundle ban đầu; dọn runtime planner/RAG cũ và dependency ngb-modal. Xem operations.md cho giới hạn còn lại.


- 16/10/2026, theo yêu cầu giữ model cũ: thêm ô `GEMINI_API_KEY_4` vào file local, file mẫu, Spring settings và launcher. Bỏ model dự phòng 3.5 vừa bật; chatbot chỉ dùng `gemini-3.1-flash-lite`, tự chuyển khóa trong cùng model. Khóa trống/trùng/placeholder được bỏ qua; giữ nguyên ba khóa trước đó.

- 16/10/2026, kết nối chatbot: Gemini 3.1 Flash-Lite trả HTTP 503; khi thiếu model dự phòng, bước đọc hiểu chờ hết ngân sách 45 giây rồi báo lỗi. Đã bật Gemini 3.5 Flash-Lite làm dự phòng trong cấu hình local và file mẫu; giữ model chính và các khóa hiện có. Khi lỗi 503, luồng JSON schema chuyển model ngay và tạm nghỉ model chính 60 giây. Đã xác minh model dự phòng trả JSON schema hợp lệ; chỉ kiểm tra nhóm kết nối AI và các câu hỏi liên quan, không chạy lại bộ 50 tình huống.

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
