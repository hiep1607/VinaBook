# Chatbot VinaBook: thiết kế và vận hành

## Phạm vi đã chốt

- Giữ giao diện widget; câu trả lời được backend kiểm tra xong rồi hiện dần theo nhóm từ trong một bubble. Thẻ sách, nút thao tác và đánh giá xuất hiện khi chữ đã hiện xong. Chỉ tự cuộn nếu khách đang ở gần cuối.
- Mỗi lượt được AI phân tích theo một hoặc nhiều tình huống: tìm sách, tư vấn, chi tiết sách, so sánh, chính sách, giỏ, chuẩn bị thanh toán, đơn hàng, trò chuyện chung. Chỉ hỏi một thông tin thật sự còn thiếu; giữ ngữ cảnh và bộ lọc của lượt trước; backend xác thực ID, quyền và điều kiện cứng.
- Sách dùng Product làm nguồn chuẩn cho giá/tồn kho/doanh số; hồ sơ biên tập được duyệt cho nội dung/độc giả/mức đọc/chủ đề; liên kết có kiểu giữa sách và chủ đề hoặc giữa hai sách. Tìm kiếm kết hợp điều kiện chính xác, văn bản và Gemini embedding khi cấu hình.
- Chính sách giao hàng và văn bản đổi trả/hủy/hỗ trợ dùng chung giữa backend, storefront và chatbot; văn bản có phiên bản và ngày hiệu lực.
- Chat thực hiện thêm/xóa/đổi số lượng/xóa toàn bộ giỏ sau khi khách xác nhận, qua CartService dùng chung với storefront. Mua một sách hoặc đặt toàn bộ giỏ mở form trong chat, lập báo giá rồi chờ nút Xác nhận đặt hàng. Hủy đơn vẫn dẫn tới trang Đơn hàng.

## Xuất bản sách

1. Thêm sách ở trang quản trị **Sách**. API lưu `status=false` nên khách và chatbot chưa thấy sách.
2. Mở **Hồ sơ AI**. Gợi ý AI chỉ dựa vào tên, tác giả, danh mục và mô tả hiện có. Quản trị viên kiểm tra, sửa tóm tắt, chủ đề, độc giả, mức đọc, mục tiêu, thông tin thư mục có căn cứ và quan hệ với sách khác. Không có nguồn thì để trống.
3. **Duyệt và xuất bản** lưu hồ sơ đã duyệt, văn bản tìm kiếm, liên kết và `status=true` trong cùng giao dịch. Chatbot dùng được dữ liệu này ngay sau khi lưu. Sau khi giao dịch thành công, worker tạo embedding riêng; lỗi API không chặn xuất bản. Hồ sơ thiếu vector được thử lại theo lô 10 cuốn, mỗi 60 giây. Khi chưa có Gemini, tìm kiếm văn bản và quan hệ vẫn hoạt động; giao diện báo chưa có chỉ mục ngữ nghĩa.
4. Chatbot đọc catalog mỗi lượt nên lượt kế tiếp thấy sách vừa xuất bản. Giá, tồn kho và đánh giá luôn lấy dữ liệu hiện hành.
5. Sửa tên, tác giả, mô tả hoặc danh mục đưa sách về bản nháp và yêu cầu duyệt lại. Thay giá, giảm giá, tồn kho không yêu cầu duyệt lại nội dung.

Chạy `VinaBookStore/src/main/resources/db/book-knowledge.sql` một lần cho production trước khi khởi động bản mới. Nếu dev JPA đã tạo bảng nhưng chưa nạp sách cũ, chạy `db/book-knowledge-backfill.sql` (có thể chạy lại). Worker tự tạo chỉ mục cho hồ sơ đã duyệt còn thiếu; nút **Lập chỉ mục sách cũ (10/lần)** vẫn cho phép chạy thủ công. Chỉ mục chỉ được ghi nếu văn bản và trạng thái duyệt còn đúng với lúc gửi API, nên việc sửa hoặc thu hồi hồ sơ luôn được ưu tiên.

## Cấu trúc dữ liệu

- `products`: thông tin bán hàng hiện hành và trạng thái xuất bản.
- `book_profiles`: tóm tắt, đối tượng, mức đọc, cách tiếp cận, mục tiêu, thông tin thư mục, ghi chú nguồn, văn bản tìm kiếm, embedding, trạng thái duyệt.
- `book_topics` và `book_topic_links`: chủ đề có thể có cha và quan hệ sách–chủ đề kèm căn cứ.
- `book_relations`: `SAME_SERIES`, `NEXT_VOLUME`, `READ_BEFORE` giữa hai sách kèm căn cứ. Tra cứu sách liên quan xét cả hai hướng và chủ đề chung.

MySQL là nơi lưu chính; chưa cần graph database riêng. API catalog công khai không trả sách nháp, trang quản trị có endpoint riêng.

## Chính sách

Chạy `VinaBookStore/src/main/resources/db/store-knowledge.sql` trước khi triển khai production (`ddl-auto=validate`). Trang **Chính sách cửa hàng** sửa phí giao, ngưỡng miễn phí, khoảng ngày giao và duyệt văn bản. Mỗi lần duyệt tạo phiên bản mới; phiên bản có hiệu lực gần nhất được dùng công khai. Phương thức thanh toán lấy từ cổng đang hoạt động.

Storefront tải `/api/store-knowledge`; giỏ và checkout chờ cấu hình trước khi hiện tổng tiền. Trước khi đặt đơn, storefront đọc lại cấu hình và so với tổng khách vừa thấy. Backend tạo báo giá theo cùng quy tắc. Chatbot đọc văn bản chính sách hiện hành; địa chỉ/số điện thoại riêng không gửi AI.

## API quản trị

- `GET /api/admin/products` và `GET /api/admin/products/{id}` gồm sách nháp.
- `GET /api/admin/books/{id}/profile`, `POST /api/admin/books/{id}/suggest`, `PUT /api/admin/books/{id}/publish`, `POST /api/admin/books/reindex`.
- `GET /api/admin/store-knowledge`, `PUT /api/admin/store-knowledge/settings`, `PUT /api/admin/store-knowledge/policies/{key}`.

Tất cả yêu cầu `ROLE_ADMIN`.

## Xác minh và giới hạn

Chạy Maven test và build ứng dụng Angular đã sửa. Kiểm thử bao phủ xuất bản ngay khi hồ sơ đã duyệt, lỗi chỉ mục không mất dữ liệu, không ghi vector cũ sau khi sửa, phí giao theo cấu hình và kế hoạch tình huống có đúng công cụ. Dùng các cuộc hội thoại nhiều lượt trên app đang chạy để kiểm tra thay đổi ngữ cảnh và cách diễn đạt; mock test không đo khả năng hiểu ngữ nghĩa của model. Hồ sơ không chứa toàn văn sách nên chatbot phải nói rõ khi nguồn không đủ để trả lời chi tiết.
