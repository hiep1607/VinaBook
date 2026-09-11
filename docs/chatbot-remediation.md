# Sửa lỗi chatbot sau QA — 09/09/2026

## Thay đổi

- Phân biệt nhu cầu tìm sách để học/làm quà và yêu cầu mua ngay; không tự chuyển các câu lọc ngân sách sang checkout.
- Giữ chủ đề và mức độ người đọc qua các câu tiếp nối được hỗ trợ, dựng lại từ lịch sử user được gửi lên. Không dùng cache ngữ cảnh chung giữa tài khoản.
- Sửa so khớp `hon` trong `khong` khiến câu hỏi dưới 100k bị hiểu thành giá tối thiểu.
- Lọc/xếp hạng có cấu trúc bằng dữ liệu catalog: giá sau giảm, số lượng yêu cầu, đánh giá trung bình thật. Sách chưa có đánh giá không tham gia lọc rating.
- Phân biệt sách mới cập nhật với ngày phát hành (catalog chưa đủ dữ liệu ngày phát hành).
- Không trả thông tin giá/tồn kho của kết quả gần giống khi chưa xác định được sách.
- Câu trả lời tìm kiếm rỗng xóa tham chiếu thẻ sách cũ; giới hạn danh sách trước khi sinh câu trả lời local để nội dung và thẻ sách khớp nhau.
- Gợi ý nhiều sách theo tổng ngân sách chọn các sách còn hàng, tính tổng giá sách; ghi rõ chưa bao gồm phí giao hàng. Không tạo đơn qua luồng gợi ý này.
- Các câu thiếu dữ liệu doanh số/ngày phát hành hoặc yêu cầu bí mật hệ thống được trả lời có giới hạn thay vì đoán.

## Kiểm chứng

- 125 kiểm thử backend: ChatIntentServiceTest, ChatServiceTest, ChatRagServiceTest, ChatCheckoutServiceTest, ChatHistoryTest — tất cả đạt.
- 28 kiểm thử ChromeHeadless cho service và component chatbot — tất cả đạt.
- Build storefront thành công; còn cảnh báo budget bundle 878,15 kB và SCSS product-detail 13,98 kB.
- API local: dưới 100k trả 5 sách đúng giá; top 3 đánh giá trả đúng 3 thẻ; 2 sách trong 150k có tổng 43.550đ chưa ship; tên Python XYZ không xác định trả không có thẻ; follow-up tiếng Anh dưới 100k không trả sách khác chủ đề.
- Frontend localhost:4200 trả HTTP 200. Không tạo đơn hàng thực trong đợt kiểm chứng này.

## Giới hạn

Đây là kiểm tra hồi quy, không phải chạy lại đủ 50 câu hoặc bằng chứng chất lượng 100/100. Lịch sử ngữ cảnh phụ thuộc cửa sổ hội thoại client gửi lên, chưa là bộ nhớ dài hạn. Nhận diện tiếng Việt vẫn kết hợp quy tắc với AI; chưa bảo đảm mọi cách diễn đạt. Không có sách phù hợp thì cần hỏi tiếp, không tự thay bằng sách không liên quan. Xếp hạng đánh giá dùng trung bình, chưa điều chỉnh độ tin cậy theo số lượt. Chọn bộ sách ưu tiên giá thấp trong các ứng viên phù hợp, chưa tối ưu cá nhân hóa. Các guard về bảo mật chỉ là lớp trả lời, không thay thế kiểm tra quyền ở API.

Trạng thái AI được cấu hình không chứng minh mọi yêu cầu Gemini đều thành công; các truy vấn có điều kiện cứng dùng local là chủ đích, không phải mất kết nối.
