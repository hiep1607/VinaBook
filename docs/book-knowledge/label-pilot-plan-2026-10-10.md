# Thử nghiệm nhãn cho 20 đầu sách — 10/10/2026

## Phạm vi

Chỉ gắn nhãn cho 20 sách hiện có: ID 7, 9, 13, 16, 17, 19, 22, 26, 27, 29, 32, 37, 38, 43, 48, 50, 51, 52, 55, 57. Giữ 36 sách còn lại để đối chiếu phạm vi thay đổi. Chưa mở rộng nhãn sang toàn bộ catalog.

Dùng `book_topics`, `book_topic_links`, `book_profiles.search_text` và màn hình admin “Hồ sơ AI” hiện có. Không tạo catalog thứ hai. Nhãn trong admin có tiền tố nhóm; chỉ nội dung nhãn được đưa vào chỉ mục tìm kiếm. Không thay giá, tồn kho, tên, tác giả, mô tả đã duyệt hoặc thông tin ấn bản.

## Nhãn và căn cứ

154 nhãn, 6–9 nhãn mỗi cuốn, trong bảy nhóm: thể loại, chủ đề, mục đích, dạng nội dung, độc giả, cách trình bày và mức đọc. Mỗi nhãn lưu ID sách, nhóm, giá trị, trạng thái biên tập, tên trường nguồn, nguyên văn căn cứ và URL của hồ sơ đã duyệt.

Đợt này phân loại từ hồ sơ đã được đối chiếu nguồn ngày 05/10, không xác minh lại toàn bộ ấn bản. Các từ tiếng Anh là từ đồng nghĩa phục vụ tìm kiếm. Nhãn mục đích/độc giả là ánh xạ biên tập, không phải cam kết hiệu quả. Những trường chưa có căn cứ được ghi riêng trong `unknowns`; giới hạn nằm trong `limitations`. Hai nhóm này không được nhập thành từ khóa tìm kiếm.

## Đánh giá đã chốt trước khi chạy

1. Cố định snapshot catalog và 30 truy vấn: 20 cách hỏi thông thường, 10 cách gọi đồng nghĩa. Ghi bộ sách liên quan cho từng truy vấn trước khi đo.
2. Chạy cùng mã truy xuất thật trên cùng mẫu 20 sách, một lần không có nhãn và một lần có nhãn. Tắt embedding cho cả hai nhánh để đo riêng tác động dữ liệu nhãn; không gọi AI trong A/B.
3. Đo số truy vấn có sách liên quan ở vị trí đầu, trong ba vị trí đầu và MRR. Báo riêng hai nhóm câu hỏi; đây không phải tỷ lệ chính xác của toàn chatbot.
4. Kiểm tra các giới hạn: giao tiếp không lấy truyện/sách giảm lo âu; sách đầu tư không bị coi là sách lập ngân sách; Java vẫn chưa xác nhận độ khó/bài tập/đáp án; lọc giá vẫn dùng giá hiện tại; không thay hồ sơ ngoài mẫu.
5. Thử một số câu trên chatbot với Gemini thật trước/sau nhập, cùng câu hỏi nhưng không dùng làm phép đo thống kê. Kiểm tra thêm tham chiếu và thông tin chưa biết.
6. Đối chiếu MySQL sau nhập, kiểm tra chạy lại không tạo nhãn trùng và lưu backup có điều kiện khôi phục.

## Tiêu chí quyết định

Cần tăng khả năng tìm bằng đồng nghĩa, không làm giảm kết quả vị trí đầu của nhóm thông thường, không phá các giới hạn về căn cứ/giá/phạm vi dữ liệu. Các trường hợp còn sai phải được liệt kê. Sau báo cáo, người dùng quyết định có mở rộng sang những sách còn lại; thử nghiệm này không tự mở rộng.

Khi mở rộng, cần một tập câu hỏi mới chưa dùng để xây bộ nhãn và cải thiện thứ tự ưu tiên chủ đề chính. Bộ 30 câu hiện tại là mẫu đánh giá có chủ đích, không đại diện cho tần suất sử dụng thực tế.
