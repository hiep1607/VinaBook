# Bổ sung nguồn và kiểm thử chatbot — 2026-10-05

## Dữ liệu đã áp dụng

- Đối chiếu 33/56 sách đang bán với trang nhà xuất bản, tác giả hoặc đơn vị giữ quyền tác phẩm. Bản biên tập: `../book-knowledge/profiles-reviewed-2026-10-05.json`; hướng dẫn và 23 sách còn chờ nguồn: `../book-knowledge/README.md`.
- Cập nhật thật vào MySQL local `book`, cổng 3306: 33 trường đối tượng, 32 phong cách, 32 mục tiêu đọc, 2 mức đọc có căn cứ riêng; thay 31 tóm tắt và thêm 98 liên kết chủ đề. Tổng liên kết sau cập nhật là 154.
- Giữ thông tin sản phẩm và ấn bản. Không lấy ISBN, năm in, số trang hoặc nhà xuất bản của một bản khác để gán cho bản đang bán.
- Có bản sao lưu hồ sơ/chủ đề trước nhập trong thư mục local bị Git bỏ qua. Import dùng transaction, kiểm tra dữ liệu cũ và đối chiếu đúng tên/tác giả; chạy lại dry-run sau nhập trả 0 thay đổi.
- 56 hồ sơ đã duyệt có embedding; không còn hồ sơ đang chờ chỉ mục ở lần kiểm tra cuối. Chatbot tiếp tục đọc dữ liệu đã duyệt từ database, không đọc một catalog tĩnh mới từ JSON.

## Những lỗi phát hiện và sửa khi thử dữ liệu mới

1. Khoảng tuổi dùng dấu `–`/`—` không được đọc đúng; sửa nhận diện khoảng tuổi, tránh nhầm 8 với 18 và không xếp gợi ý đúng tuổi khi tuổi khách nằm ngoài phân loại NXB đã biết. Đây là tiêu chí chọn gợi ý, không phải tuyên bố sách bị cấm đọc ở tuổi khác.
2. Câu hỏi “vì sao chọn cuốn ấy” mất tham chiếu. Lưu sách AI đã chọn khi căn cứ hợp lệ; với câu hỏi về mức đọc/độ tuổi và chỉ một sách có căn cứ phù hợp, hệ thống tự lưu lựa chọn từ hồ sơ ngay cả khi AI không trả `preferredProductId`.
3. “Mua sách tặng...” bị hiểu là thao tác mua dù khách đã nói sở thích. Luồng tìm quà dùng bộ tìm sách chung, giữ tuổi, sở thích và loại trừ. Khi chưa có sở thích, vẫn hỏi bổ sung; không tự tạo checkout.
4. “Còn The Pragmatic Programmer khác gì?” không dùng Clean Code vừa bàn để so sánh. Ghép sách mới được nêu với sách đang tập trung, kiểm tra cả hai còn trong catalog; không báo thiếu sách vì một truy vấn tìm kiếm thừa của AI.
5. Các trường hồ sơ bị nối thành một đoạn mô tả khó đọc và đôi lúc được lặp khi AI thêm lý do. Hiển thị tóm tắt riêng, đối tượng/mức đọc riêng khi khách hỏi; thay đúng phần lý do trong câu trả lời và giữ dữ kiện, bảng giá cùng phần chưa xác nhận. Không thêm lại câu chốt lựa chọn của AI khi công cụ đã chốt cùng một sách bằng dữ liệu.
6. AI suy ra độ dễ đọc từ chương ngắn, kỹ thuật giao tiếp từ lối kể chuyện, quản lý cảm xúc từ ứng xử hoặc khẳng định sách không có nội dung chưa được nguồn xác nhận. Bổ sung kiểm tra các nhóm nhận xét này; loại lời bổ sung không đủ căn cứ và giữ câu trả lời từ dữ liệu đã duyệt.
7. Câu hỏi lo lắng công việc/tiền bạc bị mở sang sách lập trình hoặc truyện chỉ có liên hệ mờ. Dùng mục đích đọc gốc để kiểm tra phạm vi nội dung; nếu khách chủ động tìm truyện để thư giãn, vẫn giữ hướng đọc truyện của họ.
8. Khách hỏi Mắt Biếc có nên thay vào danh sách truyện nhẹ nhàng: nối yêu cầu cũ với thông tin kết thúc buồn đã xác nhận và nêu rõ lý do không đề nghị thay. Câu hỏi lập ngân sách không được diễn giải thành đầu tư dài hạn; nêu rõ phần chưa có thông tin đã duyệt.

## Cách kiểm thử

Kiểm thử thật dùng `localhost:4200/api/chat` đi qua proxy storefront → Spring Boot → Gemini 3.1 Flash Lite → MySQL. Mỗi tình huống có một phiên khách riêng và không truyền lịch sử từ trình chạy, để kiểm tra bộ nhớ hội thoại ở máy chủ. Ghi câu trả lời nguyên văn cùng response/HTTP/execution vào JSON và Markdown; không chỉnh lại câu trả lời đã ghi. Sau mỗi nhóm, xóa đúng phiên khách do trình chạy vừa tạo.

Đây là kiểm thử API ứng dụng, không phải kiểm thử giao diện hoặc các thao tác tài khoản đã đăng nhập.

| Vòng | Lượt | Bản ghi |
|---|---:|---|
| Sau nhập hồ sơ | 23 | `results-book-profiles-live-2026-10-05.{json,md}` |
| Sau sửa ngữ cảnh và quà tặng | 23 | `results-book-profiles-verified-live-2026-10-05.{json,md}` |
| Sau sửa cách hiển thị và phạm vi nguồn | 23 | `results-book-profiles-complete-live-2026-10-05.{json,md}` |
| Kiểm tra các luồng bị ảnh hưởng bởi sửa cuối | 15 | `results-book-profiles-acceptance-live-2026-10-05-selected.{json,md}` |

Ba vòng đầu đều hoàn thành 23 lượt HTTP 200. Vòng đầu có 2 lần fallback do kế hoạch AI không hợp lệ (`IllegalArgumentException`); không ghi nhận 503/429 trong các lỗi này. Hai vòng tiếp không có fallback nhưng vẫn phát hiện lỗi nội dung/ngữ cảnh nêu trên, nên không dùng HTTP 200 để kết luận chất lượng.

Kết quả hồi quy sau sửa cuối: **326 kiểm thử, 0 lỗi, 0 thất bại**. Bộ gồm `BookKnowledgeServiceTest`, `ChatReadingQualityTest`, `ChatConversationRefinementTest`, `ChatToolsTest`, `ChatServiceTest`, `ChatDialogueFlowTest`, `ChatIntentServiceTest`. Log local: `.run-logs/chatbot-profiles-delivery-regression-20261005.log`.

### Kết quả vòng 15 lượt sau sửa

**15/15 HTTP 200, 0 fallback; cả 5 phiên thử đã xóa thành công (HTTP 204).** Tổng bốn vòng là 84 lượt trên ứng dụng thật; không coi toàn bộ 84 lượt là câu trả lời đúng, vì các vòng trước được dùng để tìm lỗi.

- Người ít đọc: chỉ ra Cho Tôi Xin Một Vé Đi Tuổi Thơ là cuốn duy nhất trong danh sách có căn cứ riêng về độ dễ đọc; câu “cuốn ấy” tiếp theo giữ đúng sách. Các cuốn khác giữ ghi chú chưa xác nhận mức đọc.
- Bé 8 tuổi: nêu phân loại 6–11 của Doraemon; Conan không nằm trong gợi ý đúng tuổi và câu hỏi riêng về Conan trả rõ phân loại 11–15 cùng lý do không xếp vào yêu cầu 8 tuổi. Hỏi về phí giao không xóa danh sách cũ.
- Lo về công việc/tiền bạc: chọn Quẳng Gánh Lo Đi Và Vui Sống; không lấp danh sách bằng Sherlock Holmes, Hoàng Tử Bé hay sách lập trình. Câu xin chọn một cuốn giữ đúng hướng đọc.
- Quà cho người 22 tuổi: giữ sở thích truyện nhẹ nhàng; không đưa Doraemon vào nhóm gợi ý đúng tuổi. Khi hỏi thay bằng Mắt Biếc, nêu kết thúc buồn đã xác nhận và không đề nghị thay theo yêu cầu cũ.
- Nhà Đầu Tư Thông Minh: nói rõ chưa xác nhận hướng dẫn lập ngân sách hàng tháng; khi khách chuyển sang chứng khoán dài hạn, trả lời theo triết lý đầu tư giá trị và chiến lược dài hạn đã đối chiếu.

So sánh Clean Code / The Pragmatic Programmer và hỏi nguyên tắc cụ thể của Đắc Nhân Tâm đã được kiểm tra ở vòng 23 lượt trước. Sửa cuối không thay luồng so sánh này. Thay đổi cuối cùng sau vòng 15 lượt chỉ bỏ câu chốt lựa chọn bị lặp, có kiểm thử riêng trong bộ hồi quy trên.

## Giới hạn còn lại

- 23 sách chưa có nguồn bổ sung trong đợt này; phần lớn sách còn thiếu mức đọc và thông tin chính xác của ấn bản đang bán. Chatbot phải giữ trạng thái chưa xác nhận cho các trường này.
- Kiểm tra lời AI theo các nhóm nhận xét và căn cứ trích dẫn giúp giảm suy diễn; chưa phải bộ chứng minh ngữ nghĩa cho mọi câu có thể được sinh ra.
- Một số câu dự phòng vẫn có giọng trình bày thông tin. Không bảo đảm mọi lần diễn đạt đều khác nhau; thay đổi câu chữ không được làm thay đổi dữ kiện.
- Các bộ kiểm thử trên không xác minh thao tác đặt hàng, đăng nhập, giao diện di động hoặc đồng bộ một sách mới do admin tạo trong đợt này. Luồng cập nhật hồ sơ/chỉ mục hiện có được kiểm tra trong `BookKnowledgeServiceTest`.
