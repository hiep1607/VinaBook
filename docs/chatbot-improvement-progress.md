# Hoàn thiện chatbot theo bộ 100 câu

## Đợt triển khai 09/09/2026

- Lọc chủ đề bắt buộc trước khi chấm điểm tương đồng: lập trình/CNTT, ngôn ngữ Java/Python, Spring Boot, trẻ em, tiếng Anh và truyện. Hỗ trợ OR cho các ngôn ngữ, không để rating/giá hợp thức hóa sách sai chủ đề.
- Ưu tiên so sánh trước truy vấn rating; nhận diện tìm theo tác giả/danh mục; COD phải là từ riêng; số lượng cụ thể được xử lý trước “rẻ nhất”.
- Thao tác loại cuốn đắt/rẻ nhất, chọn cuốn rẻ hơn và lập bảng chỉ sử dụng ID sách đang hiển thị. Không có tham chiếu thì hỏi lại. Rating vắng mặt ghi rõ, không coi là 0.
- Thêm câu trả lời cho metadata chưa có, dự đoán hết hàng, giả thuyết sản phẩm bị xóa, ngoài phạm vi và yêu cầu giả mạo quyền/truy xuất dữ liệu khách hàng.
- Bộ chạy hồi quy mới lưu request cùng response; lịch sử gần nhất 8 lượt, giới hạn 24.000 ký tự, reset ID sau kết quả rỗng, bốn lần testcase 99 và log nối tiếp sau mỗi lượt. Các log cũ được giữ nguyên. Đây là kiểm thử API ẩn danh, không phải UI end-to-end.

## Những phần chưa hoàn tất

- Bộ nhớ nhu cầu có cấu trúc qua hội thoại dài và mọi cách diễn đạt; hiện vẫn phụ thuộc lịch sử được gửi lên và các mẫu follow-up.
- So sánh theo nội dung/prerequisite, xác định cuốn đáng mua nhất và thứ tự đọc: hiện bảng có dữ liệu xác minh nhưng có trường hợp phải hỏi thêm.
- Giao tập bestseller và rating cần định nghĩa rõ nhóm bestseller và dữ liệu doanh số; chưa khẳng định mọi truy vấn nâng cao đều hoàn chỉnh.
- Tư vấn kiến thức Python so với Java, tiếng lóng ngân sách và tìm gần đúng tên sản phẩm chưa có: cần bổ sung có kiểm chứng.
- Kiểm thử mua hàng thật end-to-end ở môi trường dữ liệu thử, nhiều địa chỉ và tồn kho cạnh tranh chưa thực hiện trong đợt này. Các kiểm thử checkout hiện có dùng mock, không tạo đơn thật.
- Đánh giá lại đủ 100 câu và điểm chất lượng sau hồi quy. Không suy ra điểm đạt từ HTTP 200 hoặc số lượng unit test.

## Kết quả xác minh

- Backend: 132 test đạt, không có failure/error (gồm checkout, history, intent, retrieval và thao tác danh sách).
- Frontend: 28 test chatbot service/component đạt trên ChromeHeadless.
- Hồi quy API: `work/chatbot-regression-1788946039721/results.json`, 103 lượt/100 testcase, bốn lần câu 99, không lỗi gọi và không fallback. Thời gian API trung bình 2,873 giây. Chưa chấm điểm chất lượng toàn bộ.
- Sau lượt hồi quy có hai sửa bổ sung: loại khớp “dữ liệu” quá chung khỏi tiêu chí CNTT, và thêm điều kiện vào thông báo tìm kiếm rỗng. Đã chạy lại 132 test và kiểm tra trực tiếp hai tình huống ảnh hưởng; chưa chạy lại cả 100 câu trên hai sửa cuối.
- Kiểm tra cuối: câu 52 trả bốn sách CNTT đủ điều kiện (ID 20, 23, 9, 16), không còn sách kinh tế; truy vấn lập trình dưới 200k từ 4 sao trả rỗng với các điều kiện được nêu rõ.
- Không tạo đơn thực, không thay đổi dữ liệu catalog, không push/commit trong đợt này.
