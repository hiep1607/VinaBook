# Lọc gợi ý chưa khớp và hỏi chi tiết về sách — 2026-10-10

## Thay đổi

- Điều kiện chủ đề phải có căn cứ trong tóm tắt/mục tiêu nội dung đã duyệt. Không lấy thể loại rộng, đối tượng đọc, lời ghi chú chưa xác nhận hoặc điểm tương đồng làm bằng chứng đáp ứng một chủ đề cụ thể.
- Bổ sung các nhóm đồng nghĩa cho trào phúng, văn hóa dân gian, mạng máy tính, cơ sở dữ liệu, lạm phát, thất nghiệp và tỷ giá. Không gắn rule theo ID sách.
- Lưu điều kiện chủ đề từ nguyên văn yêu cầu, kiểm tra sau truy xuất và trước giới hạn số cuốn. AI rút yêu cầu thành “truyện” không được bỏ điều kiện “trào phúng”; đổi ngân sách giữ điều kiện cũ. Cho phép ít hơn ba cuốn thay vì thêm sách chưa đủ khớp.
- Câu hỏi đóng về nội dung và câu nối tiếp “thế còn X thì sao” được trả lời theo từng ý, dùng câu có căn cứ trong hồ sơ. Có nhắc tới một chủ đề không chứng minh hướng dẫn sâu về chủ đề đó. Thiếu thông tin là chưa biết, không phải “không có”.
- Hỏi minh họa/độ tuổi phân biệt dữ kiện đúng ấn bản đang bán. Không suy ra độ tuổi đọc từ nhân vật hoặc từ nhãn thể loại.
- Không sửa catalog, giá, tồn kho, nhãn hoặc nội dung đã duyệt ở đợt sửa code này. Không sửa giao diện hoặc các chức năng mua hàng.

Các nhóm chủ đề trên mở rộng cơ chế guard hiện có, chưa bao phủ mọi chủ đề và cách diễn đạt. Câu hỏi mở, suy luận nhiều bước hoặc paraphrase chưa nhận diện vẫn có thể đi qua nhánh giải thích chung.

## Kiểm thử

Vòng đầy đủ chạy 61 tests qua trên lọc/truy xuất catalog, 51 ca hội thoại và hồi quy nhãn. Sau khi phát hiện câu Bluetooth rút gọn trên UI, bổ sung hai ca và chạy lại 53 tests hội thoại, đều qua. Tổng 63 tests khác nhau có kết quả cuối qua; không diễn giải thành một lần chạy 63 tests hay tỷ lệ đúng của chatbot.

Ca mới bao gồm chủ đề hẹp không bị AI rút thành thể loại rộng, đổi giá không mất điều kiện, phép “và/hoặc”, phủ định/chưa xác nhận, một câu hỏi nhiều chủ đề, công nghệ không có căn cứ, minh họa/độ tuổi, hỏi nối tiếp rút gọn và không làm mất từ AI/cơ bản. Các ca dùng dữ liệu catalog và fixture có chủ đích, không phải đánh giá mù.

Đã nạp riêng bốn lớp chatbot vào backend local với cấu hình Gemini hiện có. [Đối chiếu hash bản build/bản chạy](matching-detail-runtime-2026-10-10.json). [Kết quả tests](matching-detail-tests-2026-10-10.json).

## Chatbot thật

Kiểm tra trên localhost:4200, Gemini thật, khách chưa đăng nhập. Giữ nguyên lịch sử sẵn có, chỉ xuất các lượt kiểm tra của đợt này. Có 11 lượt kiểm tra cuối: {'pass': 11}. Bảng giữ cả lỗi M07 trước sửa và lần thử lại đúng cùng câu hỏi. Kết quả pass chấm theo tiêu chí chọn sách/trả lời chi tiết ghi ở từng dòng; không phải đánh giá toàn bộ văn phong.

| Lượt | Kết quả | Tiêu chí thực tế |
|---|---|---|
| M01 | pass | Chỉ Số Đỏ; đã bỏ Chí Phèo và Tắt Đèn khỏi gợi ý chính. |
| M02 | pass | Giữ Số Đỏ và trào phúng khi đổi ngân sách dưới 80k, không thêm sách lệch. |
| M03 | pass | Chỉ Kho Tàng Truyện Cổ Tích Việt Nam, bỏ Việt Nam Sử Lược/Truyện Kiều. Chấm riêng việc chọn sách. |
| M04 | pass | Nhớ đúng cuốn, trả riêng minh họa và độ tuổi 5 đều chưa xác nhận, không lặp toàn mô tả. |
| M05 | pass | Đúng Mạng Máy Tính Và Các Hệ Thống Mở, nói rõ chưa xác nhận chuẩn mới. |
| M06 | pass | Nhớ sách mạng, xác nhận có nhắc TCP/IP; Wi-Fi 6 và 5G đều chưa biết, không suy ra là không có. |
| M07-before | fail | Tái hiện câu hỏi rút gọn: đúng cuốn nhưng trả metadata/mô tả, không trả Bluetooth 5.3. |
| M07-after | pass | Thử lại nguyên câu sau sửa: giữ đúng sách mạng, trả thẳng Bluetooth 5.3 chưa xác nhận, không liệt kê metadata. |
| M08 | pass | Chỉ Giáo Trình Cơ Sở Dữ Liệu, giữ giới hạn chưa xác nhận chương SQL/hệ quản trị. |
| M09 | pass | Trả riêng SQL/MySQL chưa xác nhận, không lấy tên môn học hoặc câu ghi chưa xác nhận làm căn cứ khẳng định có. |
| M10 | pass | Thử lại nguyên câu U05 trước đây: lần này trả đúng Giáo Trình Kinh Tế Vĩ Mô, không lỗi xử lý. Chưa kết luận nguyên nhân lỗi cũ đã hết. |
| M11 | pass | Nhớ đúng giáo trình vĩ mô, bài tập và đáp án đều chưa xác nhận, không kéo sách mạng/dữ liệu vào câu trả lời. |

So với báo cáo mở rộng nhãn: yêu cầu trào phúng đã bỏ Chí Phèo/Tắt Đèn; folklore đã bỏ Việt Nam Sử Lược/Truyện Kiều. Câu Wi-Fi 6/5G không còn chỉ trả mô tả sách. Câu “Thế còn Bluetooth 5.3 thì sao?” ban đầu vẫn lỗi trả metadata, đã sửa và thử lại nguyên văn thành câu trả lời trực tiếp có giới hạn thông tin.

### Điểm chưa giải quyết hết

- M03 vẫn có lời khen chung “giá trị cao” và gợi ý “cơ bản để bắt đầu” trong lời AI diễn giải. Chọn sách đúng không chứng minh độ khó; chưa đánh giá văn phong này là hoàn chỉnh.
- Một số lượt giới thiệu chậm vì có bước AI viết lời tư vấn; thời gian thực tế được lưu trong [bằng chứng thực thi](matching-detail-execution-2026-10-10.json). Chưa tối ưu thời gian phản hồi trong đợt này.
- Chưa xác định nguyên nhân lượt U05 thất bại trong đợt trước. Lượt hỏi lại câu gốc ở đợt này chỉ là bằng chứng của lần chạy hiện tại, không chứng minh lỗi nhà cung cấp hoặc mọi lỗi xử lý đã hết.
- Mẫu hội thoại ngắn và có chủ đích; chưa đủ kết luận ổn định trên hội thoại dài hoặc toàn bộ câu hỏi thực tế.

[Nguyên văn các lượt UI](matching-detail-ui-2026-10-10.json) · [Ảnh kiểm tra](matching-detail-ui-2026-10-10.png).

## Tệp code

`ChatExplicitFilters.java`, `ChatBookFacts.java`, `ChatBuiltinTools.java`, lớp mới `ChatBookDetailQuestions.java`; bổ sung `ChatFocusedCatalogTest.java` và các ca trong `ChatStructuredPipelineTest.java`. Các sửa đổi cũ trong workspace được giữ nguyên. Chưa push GitHub ở lượt này.
