# Cải thiện chatbot VinaBook — 02/10/2026

Mã sửa nằm trong `D:\book\book`. App được chạy bằng backend/MySQL thật và storefront tại `http://localhost:4200`; provider cấu hình Gemini 3.1 Flash Lite. Kết quả mỗi lượt gồm toàn bộ response trong [JSON](results-quality-fixed-live-2026-10-02.json) và câu hỏi/câu trả lời nguyên văn trong [Markdown](results-quality-fixed-live-2026-10-02.md).

## Đã sửa

| Vấn đề | Cách xử lý |
|---|---|
| Mất cặp so sánh, hỏi lại tên khi hỏi rẻ hơn/tồn kho/cuốn còn lại | Lưu riêng cặp so sánh và sách đang chọn; backend giải tham chiếu theo lời khách, ưu tiên hơn title/ID do AI điền. |
| Tác giả/tồn kho có sẵn nhưng báo thiếu thư mục | Trả từng dữ kiện thực sự được hỏi; chỉ ISBN/số trang/NXB/năm xuất bản thiếu mới được báo thiếu. |
| Tên sách lạ đã cung cấp nhưng lại hỏi tên | Kiểm tra catalog và báo không tìm thấy; câu “đừng đoán giá” không chặn tra cứu. |
| Gợi ý startup/mã nguồn lẫn sách không liên quan | Tách khởi nghiệp khỏi kinh doanh chung; giữ điều kiện nội dung lập trình từ câu hỏi gốc, kể cả khi AI gửi truy vấn quá chung. |
| Gợi ý tự cộng tổng giá hoặc tự chọn bán chạy | Chỉ tính tổng khi có ngân sách cho nhóm sách; chỉ bật bán chạy khi khách yêu cầu hoặc tiếp tục bộ lọc đã yêu cầu. |
| So sánh chỉ có bảng giá hoặc chọn theo danh mục rộng | Thêm mô tả xác thực; nhận xét theo mục tiêu phải có bằng chứng. Bỏ lời khuyên chỉ dựa vào danh mục. |
| Hỏi đơn hàng rơi vào ngữ cảnh sách | Định tuyến tra đơn/hủy đơn trước tham chiếu sách, giữ xác thực chủ sở hữu. Chưa đăng nhập thì hướng dẫn đúng yêu cầu đang làm. |
| Phí ship chỉ nhắc quy tắc chung | Tính phí và tổng cho từng mức 290k/310k bằng cấu hình giao hàng dùng chung. |
| Mua qua chat chọn nhầm sách hoặc mất số lượng khi mở trang | Giữ sách vừa chọn, phân biệt mua với thêm giỏ; action trang sách mang quantity. Trang sách kiểm tra số nguyên 1–99 và giới hạn theo tồn kho mới. |

## Kiểm chứng

- Backend: 170 kiểm thử trong `ChatToolsTest`, `ChatDialogueFlowTest`, `ChatExplicitFiltersTest`, `ChatIntentServiceTest`, `ChatMemoryStoreTest`, `BookKnowledgeServiceTest`, `StoreKnowledgeServiceTest` đạt, không bỏ qua test.
- App thật: 17 câu trong bản ghi cuối đều HTTP 200 và `fallback=false`. Hai lượt mua trực tiếp/tham chiếu đều chọn Hoàng Tử Bé, số lượng 2 và route `/product/37?quantity=2`; yêu cầu hủy đơn của khách vãng lai giữ đúng hướng dẫn hủy sau đăng nhập. Các con số transport không được dùng như điểm chất lượng.
- Storefront: build production đạt, xuất vào `VinaBookStore/target/chatbot-ui-20261002` vì file dist cũ bị khóa. Còn cảnh báo kích thước bundle/SCSS.
- Trình duyệt: `/product/37?quantity=2` hiển thị số lượng 2; `/product/24?quantity=99` hiển thị 70 theo tồn kho thực và khóa nút tăng. Không bấm mua/thêm giỏ.
- Spec storefront toàn bộ chưa đạt: spec login/search tham chiếu thuộc tính/handler đã bỏ. Không có lỗi kiểu dữ liệu được báo ở phần sửa trang sách; các spec cũ cần được cập nhật riêng.
- Các phiên API kiểm thử dùng guest session ngẫu nhiên và được xóa sau khi lưu response. Không xóa hoặc sửa hội thoại của người dùng.

## Giới hạn dữ liệu còn tồn tại

- Chưa có ISBN/số trang/độ tuổi đọc/văn phong đã duyệt cho nhiều sách. Chatbot phải nêu trường thiếu; không suy ra độ tuổi hoặc độ dễ đọc từ danh mục hay độ dài mô tả.
- Catalog đang phân loại Đắc Nhân Tâm vào truyện/tiểu thuyết. Bộ lọc theo nhu cầu đã dùng nội dung để tránh bỏ sót, nhưng dữ liệu danh mục nguồn vẫn cần được chỉnh trong admin.
- Lần này chưa xác minh lại luồng thêm sách có upload ảnh qua admin từng lỗi Cloudinary. Không kết luận năm câu Bộ 5 đã đạt.
- Đây là kiểm tra các lỗi cụ thể bằng dữ liệu hiện tại, không phải tỷ lệ chính xác tổng quát của chatbot.

Các response trước khi sửa tiếp được giữ trong `results-quality-live-2026-10-02.*` và `results-quality-intermediate-live-2026-10-02.*` để đối chiếu.

Trong lúc javac ghi lại class, DevTools từng khởi động lại giữa chừng và không nạp được bean mail, khiến proxy trả 504. Sau khi kiểm thử xong đã chạy lại backend từ class đầy đủ, tắt restart tự động riêng trong process kiểm thử bằng `JAVA_TOOL_OPTIONS=-Dspring.devtools.restart.enabled=false`; status và các lượt chat cuối đều hoạt động. Không sửa dịch vụ mail vì lỗi xuất hiện trong lần nạp class đang biên dịch dở.
