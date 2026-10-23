# Thực hiện rà soát project — 17/10/2026

Đã triển khai các mục F01–F12 của báo cáo rà soát và các phần tối ưu/thu gọn liên quan. Giữ model `gemini-3.1-flash-lite` và bốn ô API key; không tự chuyển sang model khác. Thay đổi đang ở working tree, chưa commit hoặc push.

## Các vấn đề đã xử lý

| Mục | Kết quả |
|---|---|
| F01 · AI lỗi | Có health hoạt động, cooldown sau lỗi toàn bộ key và kế hoạch tư vấn cơ bản qua cùng validation/tool registry. Giữ điều kiện giá/số lượng; hỏi sâu tiếp tục đúng cuốn, không chuyển thành gợi ý mới. Điều kiện phức tạp chưa đọc chắc được hỏi rõ. |
| F02 · Thông báo | Admin nhận vé socket qua API JWT, vé một lần/hạn 30 giây. Chặn socket ẩn danh và URL JWT cũ; client không phát thông báo. Header/dashboard/đơn hàng dùng chung một kết nối. Thông báo do server phát sau commit; lỗi thông báo không biến đơn đã tạo thành lỗi checkout. |
| F03 · Tài khoản khóa | Từ chối đăng nhập và JWT đã cấp của tài khoản bị khóa; kiểm tra lại quyền admin khi phát socket. |
| F04 · Reset mật khẩu | Token ngẫu nhiên, bảng reset chỉ giữ hash, hạn 15 phút và dùng một lần; không mở khóa user. Khóa DB cùng thứ tự khi cấp/tiêu thụ token, kiểm tra lại trạng thái sau khóa. Giới hạn yêu cầu theo email/IP và trả thông điệp chung. |
| F05 · Memory | Các phương thức giao dịch public được gọi qua Spring proxy. Lưu snapshot và transcript cùng transaction, chỉ commit RAM sau thành công. Xóa khi AI đang chạy không làm sống lại lịch sử. |
| F06 · Tải lại chat | Xác minh lại giá/tồn và khôi phục nút đọc, đăng nhập, chuẩn bị mua. Không mở lại action hủy đơn/ghi dữ liệu từ lịch sử. Lượng mua được đối chiếu tồn kho mới. |
| F07 · Embedding | Không khóa toàn bộ HTTP; gộp truy vấn trùng đang chạy và cache. Truy vấn vector có ngân sách tối đa 2,5 giây trong deadline còn lại của lượt chat. |
| F08 · Stream/hủy | SSE gửi tiến độ thật, chỉ trả nội dung đã kiểm chứng khi xong; bỏ gửi/hiện từng từ giả lập. Worker và queue có giới hạn, hủy khi disconnect. Một deadline 60 giây cho cả stream và JSON dự phòng. |
| F09 · Lịch sử/metrics | Ban đầu tối đa 25 lượt; trang cũ dùng cursor. Lượt chat đọc 8 exchange gần nhất và ký ức cũ có giới hạn. Metrics tổng hợp SQL theo 1–365 ngày. Retention guest/telemetry có cấu hình, mặc định tắt. |
| F10 · Trang chủ | Thử lại phục hồi catalog, danh mục và cấu hình. Counter cập nhật khi dữ liệu về muộn hoặc giá trị đổi, tôn trọng giảm chuyển động. |
| F11 · Giao hàng | Một bộ quy tắc chung cho địa chỉ lưu, checkout web/chat; chuẩn hóa số +84. Địa chỉ đường tới 255 ký tự, tên tới 80; cột địa chỉ đơn đủ chứa thông tin kết hợp. |
| F12 · OTP/mail | Đóng API OTP cũ. Email có outbox DB mã hóa AES-GCM, retry tối đa ba lần, claim bằng khóa DB và phục hồi sau restart. Payload được xóa khi kết thúc; SMTP có timeout, log không chứa người nhận/nội dung. |

## Tối ưu và làm gọn

- Catalog frontend có TTL 60 giây và refresh rõ ràng; backend tái dùng snapshot catalog trong một lượt. Checkout vẫn xác minh giá/tồn ở lần xác nhận.
- Checkout bỏ khóa toàn service, dùng khóa theo owner và khóa DB; nháp riêng theo tài khoản giữ qua restart, lưu kết quả xác nhận chống tạo đơn lặp.
- Chatbot và phần thương mại tải khi mở. Bundle storefront ban đầu từ **869,06 kB xuống 671,61 kB**, giảm khoảng **22,7%**. Build vẫn có cảnh báo ngưỡng 600 kB và CSS chi tiết sách 13,85 kB; không nâng ngưỡng để che cảnh báo.
- Admin bỏ dependency `ngb-modal` không dùng và CSS Bootstrap nạp trùng vì theme đã chứa Bootstrap 4.6. Giữ script Bootstrap/jQuery, GSAP, Three và các asset có người dùng thực tế. Admin production đạt **1.004,66 kB** ban đầu.
- Tách lifecycle chat khỏi `ChatLocalDialogue`; tách 13 tool thao tác khách hàng sang `ChatCustomerTools`, nội dung sách sang `ChatBookPresenter`. Giữ một registry và các guard dùng chung.
- Loại runtime planner/RAG cũ, service tìm kiếm cũ, DTO/payment-data/fly-to-cart không dùng và `.gitkeep` không cần thiết. Bộ planner cũ còn cần cho kiểm thử nằm trong `src/test`, không còn là Spring bean production.
- Gom **157** file kết quả/báo cáo cũ vào [archive](chatbot-evaluation/archive/2026-09-10/README.md), cập nhật liên kết; giữ runner, bộ tình huống và fixture cần dùng.
- Tách profile dev/production, tắt SQL logging/open-in-view; production kiểm tra schema. Migration mới `db/12`–`db/15`. Launcher build sạch trong `target-runtime` và chạy nền.

## Kiểm tra đã thực hiện

Các nhóm dưới đây có ca trùng nhau; không cộng thành tổng số test duy nhất. Không chạy lại toàn bộ bộ 50 tình huống live.

| Nhóm kiểm tra | Kết quả |
|---|---|
| Backend auth/reset/memory/shipping/checkout/embedding/pipeline liên quan | 271/271 đạt |
| Sau tách tool/presenter: tool registry, pipeline, guards, identity, service | 367/367 đạt |
| Giả lập lỗi AI, giữ ràng buộc và hỏi sâu | 94/94 đạt |
| Nháp checkout, outbox qua khởi tạo lại, phân trang, lỗi thông báo, reset | 13/13 đạt |
| Handshake, SSE và cấu hình khóa outbox trống | 6/6 đạt |
| Biên dịch sạch sau loại service tìm kiếm và kiểm tra chủ đề | 16/16 đạt |
| Storefront chatbot/transport/restore action/home retry/counter | 40/40 đạt; nhóm rộng hơn trước đó 86/86 đạt |
| Admin thông báo dùng chung | Ca tập trung mới nhất đạt; nhóm liên quan trước đó 5/5 đạt |
| Build production storefront/admin | Cả hai đạt |
| Whitespace diff | Đạt |

Kiểm tra trực tiếp backend/web:

- Catalog 56 sách và 8 danh mục trả HTTP 200; API quản trị không có JWT trả 401.
- Gemini đã phản hồi lại trên model đang dùng. “Gợi ý 2 tiểu thuyết dưới 150k mỗi cuốn” trả Bố Già 133.400đ và Tắt Đèn 55.200đ. Hỏi tiếp nội dung cuốn thứ hai trả đúng Tắt Đèn.
- Mua Đắc Nhân Tâm khi là guest mở bước đăng nhập, chưa tạo đơn. Trên build production, tải lại và mở chat vẫn còn nút tiếp tục mua, có giá/tồn được xác minh mới.
- WebSocket ẩn danh và URL JWT cũ đều trả 401. SSE thực tế gửi `ready → progress(understanding) → progress(execution) → done`, không gửi frame từng từ.
- Phân trang live với limit 2 trả bốn tin nhắn và `hasMore=true` đúng khi còn lượt cũ.

## Dọn output cũ

Đã sao lưu build/log được phân loại trước khi dọn. Lần dọn đầu giải phóng **89.392.699 byte** từ 2.556 file, bỏ hai junction node_modules bằng chính đối tượng junction, không đi vào thư viện đích. Giữ `target-runtime`, node_modules đang dùng, `.env.local`, dump DB và backup hồ sơ sách.

2692 file output cũ (khoảng 92,4 MB) và `angular-admin/src/app/common/Email.ts` bị ACL Windows của tài khoản sandbox cũ chặn xóa. Đã chuẩn bị script chỉ xử lý danh sách file cụ thể đã sao lưu. Yêu cầu nâng quyền Windows trả về “operation was canceled by the user”, nên phần dọn này chưa hoàn tất; không thay ACL toàn project hoặc thử nâng quyền lại. Đây là quyền Windows, không phải lỗi build hoặc một lần từ chối của automatic approval review.

## Giới hạn nghiệm thu

Không gửi email thật, đổi mật khẩu tài khoản thật, tạo/hủy đơn hoặc thanh toán thật trong đợt này. Outbox/draft qua restart được kiểm tra bằng repository giả lập có trạng thái, backend thật đã khởi động với schema mới.

Chat hiện chạy một backend; trước khi cân bằng tải nhiều instance cần bổ sung khóa/version hội thoại dùng chung. SMTP là at-least-once, có thể gửi lại nếu backend dừng sau khi SMTP nhận nhưng trước lúc lưu trạng thái. Nâng major Angular/Spring được chuẩn bị thành [kế hoạch riêng](dependency-upgrade-plan.md), chưa thực hiện migration major trong đợt sửa lỗi. Xem [vận hành](operations.md).
