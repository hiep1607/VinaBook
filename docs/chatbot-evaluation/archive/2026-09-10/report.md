# Đánh giá chatbot VinaBook — 109 câu hỏi

## Kết luận

**Phản hồi nhanh, dùng được cho FAQ cơ bản và tìm sách bằng tên đầy đủ, nhưng chưa đủ tin cậy cho tư vấn mua sách tự nhiên.** Đánh giá định tính của người kiểm thử: **5/10 ở chế độ local**. Đây không phải điểm benchmark hay tỷ lệ test đạt.

Không sửa mã nguồn chatbot trong đợt đánh giá.

## Phạm vi và dữ liệu

- 109 trường hợp, 16 nhóm, gọi POST /api/chat trên localhost; lượt chính hoàn tất 17/09/2026 lúc 23:03:22 theo đồng hồ máy.
- Provider thực tế `local`, `aiEnabled=false`, catalog 56 sách. Không đánh giá Gemini/OpenAI.
- Khách chưa đăng nhập, test API, không thao tác trình duyệt, không đặt/hủy đơn hay thanh toán.
- Lượt chính cách nhau khoảng 3,3 giây, không cố vượt rate limit. Không có retry 429 trong CSV cuối. Các lượt thử runner trước đó không nằm trong 109 nhưng có thể đã ghi metadata analytics.
- Thêm 12 lượt V01–V12 kiểm chứng nguyên phản hồi và một lượt sửa context ID.

### Tài liệu kèm theo

- [Kết quả 109 câu](results-109.csv): HTTP, intent, số sách, tên sách đầu tiên, giá, latency và snippet.
- [Nhận xét từng câu](../../case-review.md).
- [Câu hỏi gốc phần 1](../../chat-cases.ps1), [phần 2](../../chat-cases2.ps1).
- [12 phản hồi nguyên văn](verification-full.json), [ngữ cảnh đã sửa](context-corrected.json).
- [Provider và catalog lúc kiểm chứng](verification-environment.json).

**Giới hạn:** CSV gốc chỉ lưu 150 ký tự đầu câu trả lời và tên sản phẩm đầu tiên, không phải toàn văn 109 cuộc trao đổi. Câu hỏi dài bị cắt trong CSV nhưng có bản gốc trong kịch bản. Snapshot catalog được lấy sau lượt chính, không khẳng định tồn kho hai thời điểm giống nhau.

## Kết quả định lượng

| Chỉ số | Kết quả |
|---|---:|
| Tổng trường hợp | 109 |
| HTTP 200 | 107 |
| HTTP 400 đúng kỳ vọng (trắng/quá dài) | 2 |
| HTTP 5xx trong lượt chính | 0 |
| Latency HTTP 200 trung bình | khoảng 65 ms |
| Trung vị / tối đa | 63 / 148 ms |
| P95 nearest-rank (phần tử 102/107) | 113 ms |
| Khớp chính xác nhãn intent ban đầu | 64/98 = 65,3% |
| fallback=true | 86/107 |

Latency đo phía client trên localhost, không phải tốc độ trải nghiệm UI hay kiểm thử tải. Mẫu số intent loại 9 nhãn ANY và 2 kiểm tra HTTP. **ANY không tự động là đạt.** `fallback=true` ở chế độ local không đồng nghĩa lỗi AI.

Đúng HTTP/intent chưa chắc trả lời đúng. Ngược lại, tìm được đúng sách dưới nhãn PRODUCT_INFO thay vì DISCOVER_BOOKS vẫn có thể hữu ích.

## Đánh giá theo nhóm

| Nhóm | Số câu | Nhận xét |
|---|---:|---|
| Chào hỏi | 10 | Nhãn đúng 10/10; tạm biệt vẫn chào mời tìm sách |
| Tên sách | 10 | 9 câu có sách đầu tiên đúng yêu cầu; một số thêm sách ngoài tên; Sherlock Holmes rút gọn thất bại |
| Tác giả | 6 | Chỉ câu có “Tìm sách của Yuval Noah Harari” trả sách; 5 câu còn lại không có kết quả |
| Thể loại/chủ đề | 8 | Có gợi ý hữu ích nhưng bỏ sót thiếu nhi/trinh thám, trộn sách lệch chủ đề |
| Lọc giá | 8 | Một số khoảng giá đúng; “rẻ hơn 100k” bị hiểu ngược; hỏi rẻ nhất không có kết quả |
| Chính sách | 10 | POLICY đúng 10/10; phí ship/COD hữu ích; địa chỉ, VietQR là gì, thời điểm hoàn tiền chưa trả trực tiếp |
| Đơn hàng | 8 | 6 câu yêu cầu đăng nhập; 2 cách hỏi hủy/xác nhận đơn không hiểu |
| Thông tin sách | 8 | Nhãn đúng 8/8 nhưng 4 câu không giải quyết yêu cầu: tác giả, nội dung, đánh giá, tồn Harry Potter rút gọn |
| So sánh | 6 | Chỉ cặp tên đầy đủ Nhà Giả Kim/Hoàng Tử Bé so sánh được |
| Sai chính tả | 5 | Hiểu bao nhêu/shíp; không hiểu Hary/Harrry Poter; một câu không có lỗi chính tả rõ |
| Không dấu | 5 | 4 câu có phản hồi liên quan; tác giả không dấu vẫn UNKNOWN |
| Ngoại ngữ | 3 | Cả 3 UNKNOWN, chưa hỗ trợ tư vấn Anh/Nhật |
| Vô nghĩa/ngoài phạm vi | 6 | Không gắn sách ngẫu nhiên; vài chuỗi vô nghĩa bị nhận là chào hỏi |
| Câu dài/nhiều điều kiện | 5 | Không câu nào đáp ứng tốt toàn bộ nhu cầu; ngân sách sai hoặc phân loại sai |
| Follow-up | 8 | Giữ một phần ngữ cảnh nhưng chủ đề yếu; cần phân biệt lỗi runner thiếu context ID |
| Biên đầu vào | 3 | 2 HTTP 400 đạt; câu nhiều khoảng trắng lại nhận chào hỏi |

## Lỗi quan trọng và bằng chứng

1. **P1 — Khớp từ con sai ý định.** #71/#98, V01: Clean Code nhận hướng dẫn COD. #30/V02: thiếu nhi nhận chào hỏi. Bộ nhận diện dùng contains với chuỗi ngắn `cod`, `hi`, dễ trùng trong từ khác.
2. **P1 — Hiểu ngược giá.** #40/V08: “Sách rẻ hơn 100k” trả 110.000, 110.400, 114.750, 118.750đ, criteria minPrice=100000. #94/V07: khoảng 100k thành tối thiểu 100k, trả 145–155k. #95/V06: 8 tuổi thành 8.000đ và yêu cầu hai tên để so sánh.
3. **P1 — Đúng sách, sai thông tin.** #63 hỏi tác giả Chí Phèo nhận giá; #64/V04 hỏi nội dung Không Gia Đình nhận giá; #68/V05 hỏi đánh giá Đắc Nhân Tâm nhận giá. Không chấm đạt chỉ vì intent đúng.
4. **P1 — Tác giả/tên rút gọn.** #21/V03 nhận Nguyễn Nhật Ánh trong criteria nhưng không trả sách. #18/#66/#104 không nhận Sherlock Holmes/Harry Potter rút gọn.
5. **P2 — Chủ đề và hội thoại.** #27/V09 trộn Sapiens vào phiêu lưu. Khi truyền đúng ID 13,28,58,56, chatbot loại sách cũ được nhưng gợi ý Blockchain, Freakonomics, Muôn Kiếp Nhân Sinh như sách phiêu lưu. Lỗi độ liên quan, không phải hoàn toàn mất ngữ cảnh.
6. **P2 — FAQ chung chung.** #46 thiếu bước VNPay; #48 không giải thích VietQR; #50 thiếu địa chỉ; #52 không trả thời điểm hoàn tiền. Nên nói rõ chưa có thông tin, đưa kênh xác minh, không trả mẫu gần nghĩa.

## Giới hạn và cách diễn giải

- #100/#101 gốc chỉ gửi history, không gửi contextProductIds. #103 có một ID vì lượt trước chỉ trả một sách: yêu cầu ít nhất hai sách là hợp lý.
- V10 ban đầu đọc nhầm productId thay vì id của sản phẩm chat. Không dùng lượt đó kết luận lỗi loại trùng. context-corrected.json chứa request sửa đúng và xác nhận loại trùng hoạt động.
- Chưa test SSE/UI, feedback, đăng nhập/đơn thật, nút giỏ, mạng lỗi hay provider bên ngoài. Không tuyên bố đã test toàn bộ tính năng hay bảo mật.
- 109 câu không phải 109 phiên độc lập. Tập câu hỏi có thiên lệch theo catalog hiện tại, không đại diện toàn bộ người dùng.

## Ưu tiên cải thiện và chạy lại

1. Nhận diện cụm/từ có ranh giới; ưu tiên tên sách/tác giả; regression cho Clean Code, thiếu nhi, khi nào, đánh giá.
2. Tách tuổi/mã đơn/số lượng khỏi tiền; hiểu rõ dưới/rẻ hơn/trên/khoảng.
3. Tách câu hỏi giá/tác giả/nội dung/tồn/đánh giá; không suy giá từ giả/gia trong tên sách hoặc tác giả.
4. Alias/fuzzy search có ngưỡng và hỏi làm rõ; giữ criteria qua lượt; cải thiện lọc chủ đề thực sự.
5. Chạy lại cùng tập câu, lưu nguyên request/response mọi trường hợp; chấm riêng ý định, đúng dữ liệu, đáp ứng nhu cầu, thao tác UI.

Tiêu chí trước phát hành: **không gợi ý vượt giới hạn dưới/rẻ hơn; không hỏi sách trả COD; tác giả/nội dung phải trả đúng loại thông tin**. Sau đó mới đánh giá provider AI và hội thoại phong phú hơn.

## Những điểm làm tốt

- Phí ship 20.000đ, miễn phí từ 300.000đ khớp tài liệu.
- Nhà Giả Kim 63.200đ = 79.000đ giảm 20%; Dế Mèn 50.400đ = 56.000đ giảm 10%.
- Các câu tra đơn nhận diện được yêu cầu đăng nhập, không bịa trạng thái đơn cho khách ẩn danh. Không phải chứng nhận bảo mật tổng thể.
- V11 truyền hai ID 37/58 so sánh đúng hai sách và giá.
- V12 trả ADD_TO_CART với requiresConfirmation=true cho Hoàng Tử Bé. Chưa bấm xác nhận UI; không khẳng định đã thêm giỏ.
- Đầu vào trắng/quá dài bị từ chối đúng, không gây 5xx trong mẫu thử.

