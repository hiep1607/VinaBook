# Cải thiện lời tư vấn, hội thoại dài, lỗi và thời gian phản hồi — 11/10/2026

## Kết quả chính

- Chặn lời khen thiếu căn cứ và suy diễn mức độ cho người mới trong cả reason, intro, closing.
- Sửa lỗi chèn thêm lời giải thích bên trên nguyên mô tả dài; thay phần nội dung và giữ câu giới hạn dữ liệu của backend.
- Tái hiện rồi sửa lỗi “còn cuốn nào khác” trả Conan/Doraemon vào nhóm văn học Việt Nam. Nguồn gốc văn học phải có cụm phân loại rõ trong dữ liệu catalog; bản dịch tiếng Việt hoặc độc giả Việt Nam không đủ chứng minh.
- Tách lỗi chưa xác định chắc ý khách khỏi lỗi nhà cung cấp AI và lỗi lấy dữ liệu. Cả ba giữ ngữ cảnh tốt trước đó. AI viết hỏng JSON/timeout vẫn trả kết quả backend đã tìm được.
- Giới hạn riêng bước AI viết ở 8 giây trong ngân sách AI của lượt. Timeout nội bộ không làm khóa Gemini đang hoạt động bị cooldown sang lượt sau.
- Đọc hồ sơ sách theo lô trong phạm vi một lượt. Không tạo catalog mới, không lưu cache qua lượt, không cache giá/tồn kho. Luôn dọn phạm vi đọc trong finally.

Các thay đổi đã nạp vào backend local; storefront chạy ở http://localhost:4200/. Không sửa dữ liệu catalog trong đợt này.

## Kiểm thử

Một lần chạy cuối có **124 tests, 0 failures, 0 errors, 0 skipped**, trên 9 bộ kiểm thử liên quan. Bao gồm HTTP timeout thực qua server local giả lập, đọc batch, dữ liệu mới ở lượt sau, cách ly luồng, lỗi AI/DB, ngữ cảnh 10 lượt, eligibility toàn catalog và các truy vấn nhãn đã đóng băng. Không phải 124 cuộc hội thoại thật và không quy đổi thành độ chính xác chatbot.

Đã kiểm tra trực tiếp trên chatbot với Gemini, cùng một phiên khách, có reload/restart giữ ngữ cảnh. Chỉ ghi lại các câu do người kiểm thử nhập, không xuất lịch sử người dùng cũ.

| Tình huống | Quan sát |
|---|---|
| Folklore Việt Nam | Một sách đúng chủ đề; hết lặp nguyên mô tả dài; giữ giới hạn tập/ấn bản |
| 3 sách văn học Việt Nam, tối đa 100k | Truyện Kiều, Số Đỏ, Chí Phèo |
| Bỏ Số Đỏ | Thay bằng Tắt Đèn; giữ mức giá và chủ đề |
| Giá cuốn thứ hai | Chí Phèo, 49.500đ |
| Xen hỏi giao hàng rồi quay lại cuốn thứ hai | Vẫn Chí Phèo, tác giả Nam Cao |
| Giảm còn 60k, giữ phần đã loại | Chí Phèo, Tắt Đèn, Dế Mèn; không đưa lại Số Đỏ |
| Còn sách khác — trước sửa | Sai: Conan và Doraemon bị gọi là đúng yêu cầu |
| Cùng câu còn sách khác — sau sửa, giữ ngữ cảnh | Báo đã hết sách đáp ứng; không lấp bằng truyện nước ngoài |
| Đổi sang lạm phát/thất nghiệp/tỷ giá, bỏ giá cũ | Giáo Trình Kinh Tế Vĩ Mô, 94.050đ; chứng minh không giữ nhầm 60k |
| Bài tập kèm đáp án | Trả lời riêng từng điểm chưa xác nhận, đúng cuốn kinh tế |
| Networking rồi TCP/IP/Wi-Fi 6 | Đọc file UI kèm theo: kiểm tra dữ kiện xác nhận và giới hạn của cuốn đang chọn |

Các lỗi cố ý gây ra bằng mock/server thử nghiệm nằm ở kiểm thử tự động, không phải đã làm gián đoạn Gemini thật để thử.

## Đo tốc độ

Đã thêm understandingMs, executionMs, narrationMs và failureStage vào metadata/chẩn đoán; log chỉ ghi thời gian và trạng thái, không ghi nội dung chat hay secret.

Cặp hỏi cùng giáo trình kinh tế trước/sau tối ưu đọc batch:

| Phần | Trước (ms) | Sau (ms) |
|---|---:|---:|
| Đọc hiểu AI | 1876 | 2365 |
| Backend lấy/lọc dữ liệu | 4733 | 396 |
| AI viết | 1652 | 1667 |
| Tổng backend | 8280 | 4487 |

Đây là một cặp đo local, không phải benchmark có kiểm soát; độ trễ Gemini và tải máy thay đổi. Bằng chứng độc lập ở unit test: 90 lần đọc các trường hồ sơ trong một lượt chỉ gọi 1 batch, không gọi findById từng cuốn. Lượt sau vẫn đọc dữ liệu mới. Giới hạn 8 giây áp dụng cho bước AI viết, không bảo đảm toàn bộ câu trả lời dưới 8 giây.

## Lỗi cũ U05

Log 10/10 lúc 17:45:43–17:45:58 cho thấy bước understanding thất bại sau 45.023ms; slot 2 có HTTP 503, hai slot khác ghi invalid_response. Có thể xác định lượt đó thất bại trước truy xuất sách; log cũ chưa đủ kết luận nguyên nhân sâu của hai phản hồi còn lại. Không tuyên bố đã sửa lỗi 503 phía nhà cung cấp. Bản mới phân loại thông báo lỗi, bảo toàn ngữ cảnh và thêm thời gian từng bước để điều tra lần sau.

## Giới hạn còn lại

- Guard lời tư vấn bảo vệ các nhóm phát biểu đã nhận diện, chưa phải bộ kiểm chứng ngữ nghĩa toàn diện. Một số cách diễn đạt vẫn có thể rộng hơn dữ liệu.
- Mô tả catalog cũ vẫn có câu quảng bá, ví dụ Truyện Kiều được gọi là “kiệt tác/đỉnh cao”. Đợt này không thay hồ sơ biên tập; backend fallback có thể còn nguyên câu đó.
- Một số câu vừa nói nội dung vừa nêu giới hạn vẫn hơi lặp. Chưa chứng minh hội thoại dài bất kỳ đều đúng.
- Mẫu thử do người phát triển chọn, không mù và chưa đại diện người dùng thật. Chưa đánh giá tải đồng thời.

## Bằng chứng

- [quality-hardening-2026-10-11-ui.json](quality-hardening-2026-10-11-ui.json): chỉ các lượt thử của đợt này, gồm lỗi và thử lại.
- [quality-hardening-2026-10-11-tests.json](quality-hardening-2026-10-11-tests.json): kết quả 9 suites lần chạy cuối.
- [quality-hardening-2026-10-11-timings.json](quality-hardening-2026-10-11-timings.json): thời gian từng bước trước/sau.
- [quality-hardening-2026-10-11-runtime.json](quality-hardening-2026-10-11-runtime.json): hash 7 lớp build khớp runtime.
- [quality-hardening-2026-10-11.png](quality-hardening-2026-10-11.png): màn hình chatbot sau sửa.

Chưa commit/push trong đợt này.
