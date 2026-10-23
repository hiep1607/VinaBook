# Vận hành và bảo trì VinaBook

## Cấu hình

Local dùng profile `dev`, cho phép cập nhật bảng entity. File `VinaBookStore/.env.local` giữ key và secret riêng; không commit. Gemini vẫn dùng model đã cấu hình, các key dự phòng thuộc cùng model.

Production đặt `SPRING_PROFILES_ACTIVE=production`, `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `PUBLIC_BASE_URL` và `ALLOWED_ORIGINS` từ môi trường triển khai. `PUBLIC_BASE_URL` là HTTPS origin phục vụ `/forgot-password`; `ALLOWED_ORIGINS` liệt kê các origin storefront/admin được mở WebSocket, phân cách dấu phẩy. Profile production kiểm tra schema bằng `validate`, tắt SQL logging và open-in-view. Không dùng tài khoản demo trên môi trường public.

## Migration

Các script `db/01` đến `db/15` có thứ tự rõ ràng. Database đang có đơn hàng chỉ áp dụng các migration còn thiếu, không import lại seed.

| Phiên bản | Nội dung |
|---|---|
| 12 | Bảng token đặt lại mật khẩu có hash và thời hạn |
| 13 | Cột địa chỉ đơn đủ chứa địa chỉ 255 ký tự và tên người nhận |
| 14 | Nháp checkout theo tài khoản, có hạn và phiên bản |
| 15 | Outbox email mã hóa, trạng thái retry và claim chống xử lý đồng thời |

Trước khi triển khai: sao lưu DB, đối chiếu schema/migration đã áp dụng, chạy lần lượt script còn thiếu trong cửa sổ bảo trì, rồi khởi động profile production. Các script trước 12 có ALTER chỉ chạy một lần. Local đã được Hibernate cập nhật không chạy lại ALTER mù quáng; kiểm tra schema trước. Framework/migration runner có thể được nâng trong đợt riêng theo [kế hoạch dependency](dependency-upgrade-plan.md).

## Chatbot và thời gian chờ

`GET /api/chat/status` có `aiEnabled` cho trạng thái cấu hình và `health` (`unknown`, `ready`, `degraded`, `local`) cho kết quả hoạt động gần nhất. Không gọi AI tốn phí chỉ để trả health. Khi nhà cung cấp lỗi, yêu cầu đơn giản dùng cùng công cụ catalog đã kiểm tra; yêu cầu có điều kiện khó phân tích được hỏi rõ. Không tự đổi model.

AI có deadline 45 giây; vector truy vấn tối đa 2,5 giây và nằm trong thời gian còn lại. SSE gửi tiến độ theo giai đoạn và chỉ trả văn bản đã kiểm tra ở `done`. Client dùng một deadline 60 giây cho cả stream và JSON dự phòng. Executor/queue có giới hạn; đóng kết nối sẽ hủy công việc còn chờ. Xóa cuộc trò chuyện phối hợp với commit để lượt đang chạy không ghi lại lịch sử đã xóa.

Lịch sử ban đầu tối đa 25 lượt. `GET /api/chat/conversation/page?sessionId=...&before=...&limit=25` tải trang cũ bằng ID exchange. Tài khoản và guest session có scope khác nhau. Chỉ khôi phục các thao tác an toàn với giá/tồn mới; thao tác hủy đơn, đánh giá hoặc gửi yêu cầu phải được mở lại từ yêu cầu mới.

`GET /api/chat/admin/metrics?days=30` tổng hợp SQL trong khoảng 1–365 ngày, mặc định 30 ngày. Telemetry không chứa nội dung chat; bảng hội thoại riêng có lưu câu hỏi/câu trả lời và snapshot để khôi phục ngữ cảnh. Nội dung đơn hàng và input nhận diện nhạy cảm bị loại khỏi transcript chung.

## Chính sách lưu dữ liệu

Retention mặc định tắt để không tự xóa lịch sử hiện có. Có thể đặt:

```properties
chat.retention.enabled=true
chat.retention.guest-days=30
chat.retention.telemetry-days=90
```

Khi bật, tác vụ 03:30 theo Asia/Bangkok dọn cuộc trò chuyện guest không hoạt động và telemetry quá hạn; giữ lịch sử tài khoản. Nháp checkout hết hạn được dọn sau thêm một ngày để có thể kiểm tra lại kết quả xác nhận. Nháp chỉ lưu trong bảng checkout riêng, không đưa điện thoại/địa chỉ vào prompt hoặc transcript.

## Tài khoản, email và thông báo

Tài khoản khóa bị từ chối cả khi đăng nhập lẫn dùng JWT cũ. Reset mật khẩu tạo token ngẫu nhiên, chỉ lưu hash, hạn 15 phút, dùng một lần; không mở lại tài khoản khóa. Yêu cầu reset bị giới hạn theo IP và email, trả thông điệp chung để không tiết lộ tài khoản có tồn tại. Endpoint OTP cũ đã đóng.

Admin lấy vé WebSocket qua `POST /api/notification/socket-ticket` bằng JWT. Vé dùng một lần trong 30 giây; JWT không nằm trong URL socket. Chỉ server phát thông báo sau commit, và quyền admin được kiểm tra lại trước khi phát. Một kết nối dùng chung cho header/dashboard/order; ngắt khi không còn màn hình dùng.

Outbox email lưu trong DB, giới hạn 1.000 thư đang chờ và thử tối đa ba lần; giữ qua restart. Nội dung, địa chỉ và liên kết reset được mã hóa AES-GCM; xóa payload sau khi gửi xong hoặc hết lượt thử, dọn bản ghi kết thúc sau 7 ngày. Worker claim từng thư bằng khóa DB, phục hồi claim quá 5 phút và không cho worker cũ hoàn tất claim mới. SMTP có timeout 10 giây cho kết nối/đọc/ghi và không ghi địa chỉ/nội dung vào log lỗi. SMTP credentials chỉ nằm trong môi trường.

Đặt `MAIL_OUTBOX_KEY` riêng, ít nhất 32 ký tự, ổn định qua các lần khởi động. Nếu chưa đặt, dùng `JWT_SECRET` để tương thích local; đổi khóa khi còn thư chờ sẽ khiến các thư đó không giải mã được. Cơ chế giao thư là at-least-once: nếu SMTP đã nhận thư nhưng backend chết trước khi ghi trạng thái, lần phục hồi có thể gửi lại. Không khẳng định SMTP bảo đảm exactly-once.

## Phạm vi triển khai

Nháp checkout và chống xác nhận lặp đã được bảo vệ bằng DB. Khóa hội thoại/CAS hiện vẫn theo tiến trình backend; trước khi chạy nhiều instance cần khóa/version hội thoại ở DB hoặc kho dùng chung. Không đặt cân bằng tải nhiều instance cho chat mà chưa bổ sung bước này.
