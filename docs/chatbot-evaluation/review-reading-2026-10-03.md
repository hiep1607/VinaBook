# Hoàn thiện chatbot VinaBook — 03/10/2026

## Các thay đổi

### 1. Phân tích yêu cầu và cập nhật ngữ cảnh

- Tách điều kiện bắt buộc, ưu tiên, loại trừ, mục tiêu đọc, người nhận, trình độ, tổng ngân sách và số cuốn trong trạng thái hội thoại hiện có.
- “Ưu tiên Python” không loại hết các sách lập trình khác. “Chỉ lấy Python” được lưu như điều kiện bắt buộc và giữ ở lượt hỏi thêm.
- Bỏ ưu tiên không xóa điều kiện “không lấy Java”. Tăng ngân sách không bắt đầu một yêu cầu mới. “Ưu tiên cũ” tham chiếu giá trị cũ.
- “Tìm lại” sau khi đổi bộ lọc khác “tìm thêm”: tìm lại được xét cả những sách đã hiện; tìm thêm loại các sách đã giới thiệu.
- Khi có tổng ngân sách, bộ chọn giữ số cuốn có thể đáp ứng rồi mới ưu tiên sở thích. Không nhầm số cuốn hoặc giá mỗi cuốn thành tổng tiền nhóm/đơn hàng.
- Kế hoạch AI lỗi được sửa một lần kèm kế hoạch trước và lý do kiểm tra. Những yêu cầu bỏ điều kiện rõ ràng được cập nhật trực tiếp vào context.

### 2. Dữ liệu và tìm theo ngữ nghĩa

- Đã tạo vector cho 56/56 hồ sơ sách đã duyệt của catalog local. Worker tạo sáu lô, mỗi lô tối đa 10 cuốn; log kết thúc với `remaining=0`.
- Tìm vector xét catalog qua điều kiện giá, tồn kho, trình độ và loại trừ. Giữ tiếng Việt có dấu khi tạo query embedding; cache query tối đa 256 mục trong 15 phút.
- Hồ sơ từ bản nháp, vector hỏng hoặc sai chiều không được dùng làm căn cứ. Lỗi embedding khác kết quả không có sách gần nghĩa.
- Câu hỏi theo mục tiêu tránh thu hẹp theo cụm tên sách ngẫu nhiên. Ví dụ “mở doanh nghiệp” không mặc nhiên chọn “Luật Doanh Nghiệp”. Khi cần bổ sung kết quả ngữ nghĩa, đặt chúng trong nhóm gần nội dung, kèm tóm tắt đã duyệt; xếp kết quả gần nghĩa trước các gợi ý chỉ gần chủ đề. Sắp xếp giá được yêu cầu rõ vẫn được giữ.
- Hồ sơ vừa xuất bản được tìm bằng văn bản/quan hệ ngay khi giao dịch hoàn tất. Chỉ mục chạy sau đó và tự thử lại. Ghi vector có điều kiện theo văn bản hiện hành và trạng thái duyệt, tránh ghi đè chỉ mục cũ sau khi sửa hồ sơ.

### 3. Cách diễn đạt

- Phần tư vấn nhận yêu cầu đọc, kết quả đã xác thực và ba trao đổi gần nhất.
- Lời giải thích phải có trường nguồn và đoạn trích tương ứng trong hồ sơ đã duyệt. Backend giữ tên, giá, số lượng, phân nhóm và thẻ sách.
- Khi hết sách, giữ kết luận đã kiểm chứng và gợi ý bước tiếp theo có liên quan; không thêm điều kiện độ dài chưa được hỏi hoặc hứa chắc còn sách.
- Không chào lại sau lượt đầu; bỏ lời dẫn dư thừa khi kết quả rỗng. Một câu diễn đạt không hợp lệ bị bỏ riêng, không làm mất toàn bộ câu trả lời hợp lệ.

## Kiểm thử backend và build

**285 kiểm thử ở 9 nhóm, không lỗi, không bỏ qua.**

| Nhóm | Số kiểm thử |
|---|---:|
| ChatToolsTest | 122 |
| ChatServiceTest | 57 |
| ChatIntentServiceTest | 59 |
| ChatDialogueFlowTest | 24 |
| ChatExplicitFiltersTest | 7 |
| ChatMemoryStoreTest | 3 |
| BookKnowledgeServiceTest | 9 |
| BookEmbeddingGatewayTest | 2 |
| StoreKnowledgeServiceTest | 2 |

Admin đã qua TypeScript và build production sau khi đổi nhãn nút xuất bản. Build dùng output riêng vì thư mục dist cũ gặp lỗi EPERM khi xóa file. Build hoàn tất, có cảnh báo bundle vượt ngưỡng 66,52 kB và selector Bootstrap `:lang`; các cảnh báo này không chặn build.

## Kiểm thử app đang chạy

Chạy API qua storefront `localhost:4200` → backend `localhost:8080`, Gemini 3.1 Flash Lite và MySQL. Mỗi bộ dùng một phiên khách thử riêng; chỉ các phiên thử này được xóa sau khi lưu phản hồi. Đây là kiểm thử chức năng qua API của app đang chạy, không phải nhập từng câu qua giao diện.

Phản hồi các vòng trước được giữ trong `results-reading-live-2026-10-03-first.*` và `results-reading-live-2026-10-03-fixed.*`. Chúng đã giúp phát hiện và sửa mất điều kiện loại trừ, nhầm “ưu tiên cũ”, nhầm tìm lại/tìm thêm, nhầm tên sách với mục tiêu đọc và lời dẫn thêm điều kiện chưa có.

Phản hồi mới nhất theo từng bộ được lưu trong [câu hỏi và câu trả lời nguyên văn](results-reading-live-2026-10-03-final.md), cùng [JSON đầy đủ](results-reading-live-2026-10-03-final.json).

**25 lượt hỏi trong 9 bộ: 25 HTTP 200, 0 fallback.** Các bộ giữ cùng phiên qua nhiều lượt; bốn câu theo mục tiêu đã được chạy lại sau khi chỉnh thứ tự gợi ý ngữ nghĩa. Thời gian phản hồi ghi nhận từ 2,1 đến 11,8 giây, trung vị 5,9 giây.

| Bộ | Lượt | Kết quả quan sát |
|---|---:|---|
| Ưu tiên và loại trừ | 5 | Không biến Python thành bắt buộc; bỏ ưu tiên giữ loại trừ Java; Java chỉ xuất hiện sau khi khách bỏ loại trừ |
| Tổng ngân sách | 3 | Nhóm đầu 173.760đ trong 300k; nhóm tiếp 324.960đ trong 400k; bỏ ngân sách giữ chủ đề, ưu tiên và số cuốn |
| Thiếu sách và hỏi thêm | 3 | Báo chỉ tìm được 2/6 sách trinh thám; hai lượt sau không lặp sách cũ |
| Sách và phí giao hàng | 2 | Trả cả danh sách và chính sách; không biến giá mỗi cuốn thành tổng tiền đơn |
| Thay đổi bộ lọc | 4 | Giữ chủ đề và số lượng qua lọc giá, bỏ giới hạn, hỏi thêm |
| Chỉ Python | 2 | Giữ điều kiện ở cả hai lượt; không trả sách lập trình khác khi không có Python phù hợp |
| Số lượng khác ngân sách | 1 | “Tổng cộng 5 cuốn” không sinh tổng tiền nhóm |
| Chủ đề không có | 1 | Nuôi ong trả kết quả rỗng, không chọn sách ngẫu nhiên |
| Diễn đạt mục tiêu | 4 | Khởi nghiệp tinh gọn đứng đầu câu kiểm chứng ý tưởng; Clean Code cho mã khó bảo trì; Sherlock Holmes cho chuyện cần suy luận; không ép gợi ý khi mục tiêu thay đổi thói quen thiếu căn cứ |

16 kiểm tra tự động trên phản hồi thật đều đạt: [chi tiết kiểm tra và thời gian](results-reading-live-2026-10-03-checks.json). Đây là kiểm tra điều kiện và tính nhất quán của các ca cụ thể, không phải chấm điểm tổng quát khả năng tư vấn.

## Giới hạn của kết quả

- Kiểm thử tập trung vào những luồng vừa sửa; chưa phải phép đo cho mọi kiểu câu hỏi hoặc tải đồng thời nhiều người.
- Hồ sơ sách không có toàn văn. Câu hỏi vượt quá nội dung đã duyệt vẫn cần nêu thiếu dữ liệu.
- Ngưỡng vector 0,66 và khoảng 0,035 so với kết quả cao nhất được kiểm tra trên catalog local bằng [bốn câu đo độ tương đồng](semantic-calibration-2026-10-03.json). Cần kiểm tra lại khi thay model hoặc bổ sung nhiều dữ liệu; không coi đây là ngưỡng dùng chung cho mọi catalog.
- Luồng xuất bản ngay và xử lý lỗi/stale index được kiểm tra bằng backend tests. Lần này không thêm một sách thật qua form admin hoặc thử lại upload Cloudinary.
