# Kiểm tra và cải thiện hội thoại — 10/10/2026

## Cách kiểm tra

Thử trực tiếp trên chatbot tại localhost:4200, dùng Gemini 3.1 Flash Lite và catalog local. Giữ hội thoại QA có sẵn để kiểm tra cả việc chuyển chủ đề trong lịch sử dài. Không xác nhận tạo đơn hoặc thực hiện thanh toán.

JSON ghi nhận cả lỗi tái hiện và các bản trung gian; không coi toàn bộ lượt trong JSON là đạt. Kết quả cuối được ghi riêng trong `final`.

## Điểm yếu tìm được và thay đổi

1. **Patch đúng ý nhưng sai câu làm căn cứ:** lượt kết hợp giá mỗi cuốn và ngân sách cả nhóm bị từ chối vì thiếu/trích sai evidence. Hợp đồng mới dùng nguồn chung `current_message`; backend gắn nguyên văn nguồn cho từng trường. Các kiểm tra kiểu, tiền, số lượng, phiên bản và ID vẫn giữ nguyên. Trích dẫn cũ hợp lệ vẫn được chấp nhận; nguồn tùy ý vẫn bị từ chối.
2. **Khoảng giá nhập ngược:** AI từng bỏ/đảo đầu khoảng giá. Prompt giữ đúng vai trò hai đầu để backend hỏi lại; không tự chọn giá thay khách.
3. **Hỏi bài tập/đáp án nhưng trả mô tả chung:** backend trả trực tiếp từng thuộc tính đã hỏi. Chưa có dữ kiện thì nói chưa xác nhận; có bài tập không tự suy ra có đáp án. Không gọi thêm AI để viết lại dữ kiện này.
4. **Sách chỉ có nội dung liên quan bị tính là sách dạy kỹ năng:** truyện tuổi thơ và sách giảm lo âu từng lọt vào yêu cầu học giao tiếp. Bộ lọc cần căn cứ giao tiếp và căn cứ hướng dẫn/nguyên tắc/phương pháp trong nội dung chính đã duyệt; metadata tìm kiếm không đủ để chứng minh.
5. **“Hai cuốn đó” bị hiểu thành một cuốn:** bước giải tham chiếu cũ ghi đè selection bằng focus vì thấy “cuốn đó”. Tham chiếu số nhiều hiện có ưu tiên đúng; “cuốn thứ hai” vẫn giữ thứ tự.
6. **Tự khẳng định hợp người mới:** trình độ beginner vẫn được giữ khi không có recipient. Hồ sơ thiếu mức đọc được nêu rõ; lời AI tự gán nội dung nền tảng hoặc độ phù hợp cho người mới bị loại.
7. **Mở đầu lặp:** structured answer giữ một lời dẫn backend cùng các điều kiện, không chèn thêm một đoạn intro của AI.

## Kiểm thử tự động

538 test đạt, 0 lỗi, 0 bỏ qua; hoàn tất 00:59:10 ngày 10/10/2026. Trong đó ChatStructuredPipelineTest có 42 test, tăng 13 test so với đầu đợt này.

```powershell
.\mvnw.cmd '-Dvinabook.build.directory=<thư-mục-build-riêng>' '-Dtest=Chat*,AiGatewayRetryTest,AiGatewayStructuredTest,CustomerFeatureChatTest' test
```

Các test tự động dùng mock/HTTP giả lập, không gọi Gemini. Những lần build lỗi trong quá trình chỉnh sửa đã được sửa trước lần chạy đạt cuối cùng.

## Kết quả trực tiếp sau sửa

Các lượt dưới đây chạy trực tiếp trên giao diện web, dùng Gemini thật, sau khi nạp bản sửa cuối và khởi động lại frontend. Không gửi yêu cầu tạo đơn.

| Tình huống | Kết quả quan sát |
|---|---|
| “So sánh hai cuốn đó”, chưa mua | Hiện bảng so sánh đúng Conan và Dế Mèn, không hỏi lại tên sách. Ngữ cảnh vẫn còn sau khi tải lại trang. |
| Giá cuốn thứ hai, chỉ hỏi giá | Trả Dế Mèn 50.400đ, đúng thứ tự danh sách so sánh, không tạo thao tác mua. |
| Khoảng giá từ 300k đến 100k | Hỏi lại mức thấp nhất/cao nhất, không tự đảo khoảng hoặc báo thiếu sách. |
| Sửa thành tối đa 150k, bỏ giá tối thiểu, đổi sang Java cho người mới | Chọn Giáo Trình Lập Trình Java 91.800đ. Nêu chưa đủ dữ kiện xác nhận độ khó cho người mới; không tự gán nội dung nền tảng. |
| “Cuốn đó có bài tập thực hành kèm đáp án không?” | Giữ đúng cuốn Java; trả riêng bài tập và đáp án đều chưa có thông tin xác nhận, không suy ra có hoặc không. |
| Hai sách kỹ năng giao tiếp, mỗi cuốn dưới 150k và tổng dưới 200k | Chỉ đưa Đắc Nhân Tâm 70.520đ, tổng 70.520đ. Báo rõ chưa đủ hai cuốn phù hợp. Không thêm truyện tuổi thơ hoặc sách giảm lo âu để đủ số lượng. |

Cả sáu lượt xác nhận cuối đạt các yêu cầu tương ứng. Lượt so sánh và hỏi giá không chuẩn bị mua; giỏ hàng QA vẫn giữ số lượng trước đợt thử. Các đoạn diễn giải do AI viết có thể thay đổi giữa các lần chạy; kết quả này không thay thế một bộ đánh giá thống kê.

## Giới hạn

Đây là kiểm tra một số cách nói trên catalog local, không phải tỷ lệ chính xác trên mọi hội thoại. Lời tư vấn đôi lúc còn dài và các lượt gọi AI có thể chậm. Tiến trình frontend 4200 từng ngắt trong lúc thử, trình duyệt ghi WDS Disconnected và backend ghi ClientAbortException; các lượt mất kết nối không được tính là đạt. Đã khởi động lại riêng frontend trước khi xác nhận cuối. Kiểm thử nguồn và schema không chứng minh toàn bộ ý nghĩa AI diễn giải đều đúng, nên vẫn cần mở rộng hội thoại thực tế khi thêm catalog hoặc đổi model.

Minh chứng: [các lượt thử](quality-followup-2026-10-10.json), [ảnh cuối](quality-followup-2026-10-10.png).
