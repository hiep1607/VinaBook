# Nghiệm thu luồng chatbot có ngữ cảnh có cấu trúc — 09/10/2026

## Bản chạy

- Storefront: http://localhost:4200/; backend: http://localhost:8080/.
- Provider thật: Gemini, model `gemini-3.1-flash-lite`, architecture `structured-v1`.
- Thử qua giao diện chat của tài khoản QA hiện có. Không xóa hội thoại cũ, không xác nhận tạo đơn.
- Chỉ nạp các lớp chatbot đã sửa vào backend local; các chỉnh sửa ngoài phạm vi giữ nguyên.

## Kiểm thử tự động

525 test đạt, 0 lỗi, 0 bỏ qua ở lần chạy cuối 00:11:56 ngày 10/10/2026 (phiên triển khai bắt đầu 09/10).

~~~powershell
.\mvnw.cmd '-Dvinabook.build.directory=<thư-mục-build-riêng>' '-Dtest=Chat*,AiGatewayRetryTest,AiGatewayStructuredTest,CustomerFeatureChatTest' test
~~~

Trong đó có 32 test mới: 29 trong ChatStructuredPipelineTest và 3 trong AiGatewayStructuredTest.

Những lớp kiểm tra gồm: patch chỉ lấy nguồn từ lời khách hiện tại; kiểu/enum/giá/số lượng; phiên bản context; một lần sửa JSON; không ghi memory khi DB/AI lỗi; ID có căn cứ; ưu tiên mềm; OR/AND; tổng tiền cả nhóm và biên giá; thiếu số cuốn; tuổi/lớp; thứ tự và focus; hết sách; giữ tham chiếu sau lượt rỗng; lời giải thích không bịa căn cứ/dạng truyện; request trùng không chạy lại; schema không rò sang lượt thường; deadline và retry hữu hạn.

Các test tự động dùng catalog fixture và HTTP giả lập, không tiêu quota Gemini. Kiểm tra Gemini thật là các lượt UI bên dưới.

## Hội thoại thực tế

| Tình huống | Kết quả quan sát |
|---|---|
| Trinh thám **hoặc** phiêu lưu dưới 100k | Conan 23.750đ và Dế Mèn 50.400đ; không đưa sách luật hoặc sách kỹ thuật vào nhóm |
| Thích truyện chữ hơn | Giữ giá và hai thể loại; truyện tranh vẫn là phương án thay thế |
| Chỉ trinh thám | Giữ mức 100k và ưu tiên; danh sách còn Conan |
| Giá không quan trọng nữa | Bỏ giá, giữ trinh thám và ưu tiên; thêm được Sherlock Holmes 193.500đ |
| Xem lại sách cũ | Cho phép hiện lại danh sách, khác với tìm thêm sách mới |
| Hai cuốn phiêu lưu hoặc trinh thám, tổng dưới 100k | Dế Mèn + Conan, tổng 74.150đ; không chia đều 50k mỗi cuốn; xác nhận một cuốn đúng ưu tiên truyện chữ |
| Cuốn thứ hai rồi mua cuốn này | Đúng Conan; hiện Tiếp tục đặt hàng và biểu mẫu; chưa tạo đơn |
| Hỏi phí giao hàng | 20.000đ, miễn từ 300.000đ, dự kiến 2–4 ngày làm việc theo policy backend |
| Quay lại sách sau policy và backend restart | Giữ hai thể loại, tổng 100k, số lượng hai và ưu tiên truyện chữ; trả đúng tổng 74.150đ |
| Dấu hỏi sau khi hết sách | Giải thích rằng sách phù hợp đã được giới thiệu; giữ yêu cầu và không hỏi nhu cầu từ đầu |
| Giá cuốn thứ hai sau kết quả rỗng | Nhớ đúng Conan, 23.750đ, chỉ một lần đọc hiểu, không gọi AI viết mô tả |
| Tìm sách làm quà nhưng chưa có hướng đọc | Hỏi thêm chủ đề hoặc mục đích để chọn phù hợp; không tự đưa sách bất kỳ, không bắt buộc ngân sách hoặc tuổi |
| Đang chán, muốn đọc giải trí | Dế Mèn 50.400đ, Sherlock Holmes 193.500đ và Bố Già 133.400đ; không lọt giáo trình trí tuệ nhân tạo, không bắt buộc người nhận hoặc ngân sách |

Các lượt còn lại và kết quả nguyên văn nằm trong JSON đi kèm.

## Lỗi đã phát hiện và sửa trong quá trình thử

- Schema liệt kê hàng chục thuộc tính tùy chọn làm model tự điền trường chưa được nói tới. Chuyển `set/evidence` sang map thưa; backend vẫn kiểm tra đầy đủ tên, kiểu và nguồn. Tham số discovery không nhận bộ lọc cạnh tranh với patch.
- JSON Schema tổ hợp theo mọi công cụ quá phức tạp bị provider từ chối. Schema provider được rút gọn; registry kiểm tra chính xác từng công cụ trước thực thi.
- Ưu tiên truyện chữ trước đây tìm nguyên cụm trong mô tả, mâu thuẫn với phần giải thích. Dạng truyện được xác nhận từ dữ kiện; ưu tiên thiếu căn cứ được nói là chưa xác nhận, AI không được tự gán thể loại/dạng sách.
- Hết sách mới từng xóa danh sách tham chiếu. Giữ selection gần nhất khi tìm thêm rỗng; nhu cầu mới mới xóa selection cũ.
- Dấu hỏi sau kết quả hết sách từng bị trả thành chưa tìm được sách. Phân biệt exhausted với no_match và giải thích lại kết quả hiện có.
- Có một lần gửi policy đúng lúc backend đang khởi động; giao diện hiện Thử lại. Sau khi status sẵn sàng, thử lại nhận đúng policy.
- Ghi chú mềm trích sai lời khách từng khiến câu tìm sách làm quà bị từ chối. Backend bỏ riêng ghi chú không có nguồn, giữ kiểm tra chặt patch và công cụ. “Làm quà” đơn lẻ chưa đủ hướng đọc, không tự lấy sách bất kỳ.
- Lịch sử dài từng khiến AI trích nhầm lời lượt cũ khi chuyển sang mục đích giải trí. Lượt hiện tại được đặt cuối input; lịch sử đọc hiểu rút còn bốn cặp có nhãn previousUser/previousAssistant, giới hạn 600/400 ký tự. Repair bỏ lịch sử và chỉ rõ nguồn hiện tại. Giá trị null/rỗng là no-op; chỉ clear mới xóa.
- Query chung “giải trí” từng tìm như từ khóa nên khớp sai sách “trí tuệ nhân tạo”. Backend xác định nhóm tác phẩm kể chuyện có căn cứ trước rồi chọn trong nhóm; không dùng mục đích chung làm từ khóa nội dung. Yêu cầu cụ thể về khoa học/tác giả/tên sách vẫn ưu tiên.

## Phạm vi xác nhận

Đây là kiểm tra trên catalog local và một số hội thoại Gemini thật, không phải bằng chứng mọi cách diễn đạt đều đúng. Hồ sơ thiếu dữ kiện về văn phong/độ tuổi/dạng truyện vẫn phải nêu thiếu thông tin. Nếu model hoặc catalog thay đổi, dùng 25 kịch bản trong [kế hoạch](../chatbot-structured-pipeline.md) để kiểm tra lại.

Kiến trúc dùng khóa phiên trong một backend hiện tại; triển khai nhiều backend cần CAS/khóa ở tầng lưu trữ. Gemini lỗi không ghi đè context tốt; lời mua/hủy của AI không thay bước xem lại và xác nhận.

Minh chứng: [lượt UI](structured-pipeline-2026-10-09.json), [ảnh cuối](structured-pipeline-2026-10-09.png).
