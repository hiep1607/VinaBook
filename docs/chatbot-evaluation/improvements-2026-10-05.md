# Cải thiện tư vấn chatbot — 2026-10-05

## Những phần đã sửa

- Giữ mục đích đọc riêng với chủ đề: thư giãn, người ít đọc/dễ hiểu, cần cách áp dụng cụ thể; giữ tuổi người đọc qua các lượt bổ sung giá và hỏi tiếp. AI có thể nhận diện cách diễn đạt khác qua `readingNeed`, nhưng phải trích lời khách ở lượt hiện tại làm căn cứ.
- Xếp sách theo nhu cầu gốc bằng tìm kiếm ngữ nghĩa, sau đó ưu tiên căn cứ trong hồ sơ đã duyệt. Yêu cầu đọc nhẹ nhàng tránh nội dung được mô tả là bi kịch, mafia, đau đáu, day dứt; khi khách chủ động muốn trinh thám nhẹ nhàng, vẫn cho xem sách trinh thám và ghi rõ mức độ nhẹ nhàng chưa được xác nhận.
- Không dùng danh mục do AI tự suy ra làm bộ lọc cứng cho các yêu cầu về mục đích đọc. Một cuốn truyện có thể nằm ở kệ văn học hoặc thiếu nhi.
- Khi khách không yêu cầu đủ số lượng, ưu tiên những cuốn có căn cứ về không khí câu chuyện thay vì lấp đủ năm vị trí bằng sách chỉ cùng thể loại. Yêu cầu số lượng cụ thể, giới hạn giá, sách đã xem và quy tắc sách gần chủ đề vẫn được giữ.
- Đổi nhãn của nhóm chưa xác nhận đầy đủ sang “Sách theo nội dung bạn tìm”; ghi rõ độ dễ đọc, độ tuổi hoặc cách áp dụng chưa được xác nhận dưới từng cuốn. Ưu tiên “truyện nhẹ nhàng” nhận được các cách mô tả tương ứng như trong trẻo/hồn nhiên, thay vì chỉ tìm đúng nguyên cụm từ.
- Kiểm tra lời AI theo nhóm nhận xét: độ dễ đọc, đối tượng, độ tuổi, tác dụng giáo dục, bài tập/phương pháp cụ thể, phong cách. Các trường đã duyệt riêng có thể làm căn cứ dù phần tóm tắt không nhắc lại chúng.
- Không suy ra độ dễ đọc từ truyện ngắn, tác dụng phát triển tư duy từ truyện tranh, hoặc độ tuổi đọc từ tuổi nhân vật. Không suy ra sách không nói về một chủ đề chỉ vì mô tả không nhắc đến chủ đề đó.
- Hỏi sâu hơn về giao tiếp sẽ phân biệt phạm vi ứng xử đã được mô tả với thuyết trình/đàm phán chưa được xác nhận; không tự tạo bài tập hay mục lục.
- Với câu hỏi chọn sách cho bé sau câu chen về giao hàng, giữ danh sách và tuổi cũ, chỉ ra những cuốn là truyện tranh theo mô tả rồi hỏi bé quen đọc truyện chữ hay truyện tranh. Chưa có căn cứ tuổi thì không tuyên bố một cuốn hợp tuổi nhất.
- Giảm lời chào lại và câu kết “Nếu bạn muốn…”. Kết quả trống giữ kết luận của bộ tìm sách, không gọi AI để thêm lời hứa tìm sách gần chủ đề khi đã kiểm tra hết.
- Sửa luồng dự phòng: yêu cầu tìm truyện cho bé hoặc đổi sang truyện thư giãn vẫn dùng công cụ tìm sách chung, giữ yêu cầu về tuổi/giá và các loại trừ. Lỗi kế hoạch AI không còn làm các câu này rơi vào lời hỏi nhu cầu chung chung.

## Kiểm thử

- Bộ hồi quy gồm `ChatReadingQualityTest`, `ChatConversationRefinementTest`, `ChatToolsTest`, `ChatServiceTest`, `ChatDialogueFlowTest`, `ChatIntentServiceTest`. Có 15 kiểm thử mới trong `ChatReadingQualityTest`; tổng bộ hiện tại 307 kiểm thử.
- Đã chạy 59 lượt trên ứng dụng thật qua `localhost:4200/api/chat`, đi qua proxy storefront, Spring Boot, Gemini 3.1 Flash Lite và MySQL. Mỗi tình huống dùng phiên khách riêng; không truyền lịch sử từ trình chạy, để kiểm tra bộ nhớ hội thoại phía máy chủ. Xóa đúng các phiên thử sau khi chạy.
- Đây là kiểm thử API ứng dụng, không phải kiểm thử thao tác giao diện hoặc tài khoản đã đăng nhập.
- Vòng 21 lượt đầu: `results-reading-quality-live-2026-10-05-selected.{json,md}`.
- Vòng 21 lượt tiếp: `results-reading-quality-final-live-2026-10-05-selected.{json,md}`. Vòng này phát hiện hai lỗi kế hoạch AI (`IllegalArgumentException`, không phải 503 hoặc 429) và một câu tiếp theo mất yêu cầu người đọc. Giữ nguyên toàn bộ bản ghi lỗi để đối chiếu.
- Sau sửa luồng dự phòng: `results-reading-quality-verified-live-2026-10-05-selected.{json,md}` — 12 lượt, tất cả HTTP 200, không có fallback; giữ đúng thể loại, giá, danh sách cũ và yêu cầu cho bé sau câu chen về giao hàng.
- Vòng bổ sung: `results-reading-quality-extra-live-2026-10-05.{json,md}` — 5 lượt, tất cả HTTP 200, không có fallback. Kiểm tra lại sở thích nhẹ nhàng và trinh thám nhẹ nhàng; khi chỉ giữ sách đã xác nhận đầy đủ về độ nhẹ nhàng, chatbot không tự gắn nhãn cho các cuốn chưa có dữ liệu đó.
- Sau đọc vòng bổ sung, đã sửa thêm câu dẫn không nhắc “các cuốn còn lại” khi toàn bộ kết quả đều đáp ứng ưu tiên, và chặn suy đoán nhịp kể khi hồ sơ chưa có thông tin này. Các bản ghi câu trả lời vẫn giữ nguyên văn của phiên bản tại thời điểm chạy.

## Kết quả và giới hạn

Yêu cầu truyện nhẹ nhàng cho người 22 tuổi chuyển từ cổ tích/Sherlock/Không Gia Đình sang Hoàng Tử Bé và hai cuốn Nguyễn Nhật Ánh. Yêu cầu văn học cho người ít đọc ưu tiên Cho Tôi Xin Một Vé Đi Tuổi Thơ, Dế Mèn Phiêu Lưu Ký và Hoàng Tử Bé; chatbot ghi rõ mức đọc chưa được xác nhận. Không tự bổ sung sách mới khi đã hết trinh thám đúng hoặc đủ gần.

HTTP 200 và không có fallback là chỉ số vận hành, không phải chứng minh mọi lời giải thích đều hay hoặc hoàn toàn đúng. Lời tư vấn vẫn có thể khái quát; một số phần dự phòng lặp mô tả hoặc lời giới thiệu từ dữ liệu cửa hàng.

Kiểm tra MySQL tại ngày chạy: có 56 sách đang bán và 56 hồ sơ đã duyệt, nhưng các trường `audience`, `readingLevel`, `readingStyle`, `learningGoal` đều chưa có dữ liệu. Muốn giải thích sâu hơn bằng ví dụ, mức đọc và lý do chọn theo tuổi cần bổ sung hồ sơ từ nguồn sách/nhà xuất bản có căn cứ rồi duyệt qua hệ thống hồ sơ hiện có. Đợt này không tự điền các thông tin đó bằng suy đoán và không đổi dữ liệu sách trong MySQL.

Ứng dụng đã được khởi động lại để dùng mã mới. Các thay đổi thuộc repo `D:\book\book` (VinaBook).
