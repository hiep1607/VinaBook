# Gợi ý hướng đọc khi chưa rõ chủ đề — 11/10/2026

## Hành vi

Khi khách muốn mua/tìm sách nhưng chưa có chủ đề hoặc mục đích đọc, chatbot hỏi hướng đọc và đưa 2–3 lựa chọn chủ đề. Nếu có hoàn cảnh liên quan từ lời khách hiện tại hoặc 4 lượt gần nhất, AI đề xuất chủ đề cùng trích dẫn căn cứ. Backend cung cấp ID cho từng lời khách làm nguồn. Nếu AI diễn đạt lại đoạn trích, backend dùng nguyên văn nguồn được chọn; không giữ câu trích bịa. Backend chỉ nhận căn cứ nằm trong lời khách; lời chatbot không phải nguồn sở thích. Các câu mua sách chung chung/ngân sách đơn thuần không đủ để gọi là cá nhân hóa.

Lựa chọn chỉ là đề xuất mềm. Không thêm chúng vào bộ lọc và không đưa thẻ sách trước khi khách chọn. Nút chủ đề dùng chip có sẵn trên storefront; khách cũng có thể gõ hướng khác. Sau khi khách chọn, chỉ hướng đã chọn được đưa vào bộ lọc, giữ những điều kiện vẫn áp dụng như giá. Nếu đã có chủ đề được xác nhận thì tiếp tục tìm, không bắt hỏi lại người nhận/tuổi/ngân sách.

Các hướng đã hiển thị được lưu riêng trong `topicSuggestions`, không thuộc bộ lọc tìm sách. Bấm chip hoặc chọn số thứ tự ngắn được backend nối đúng với hướng đó trước khi tìm, không cần AI diễn giải lại. Câu có điều kiện bổ sung vẫn qua AI cùng danh sách hướng đã gợi ý. Lựa chọn được dọn khi đã xác nhận chủ đề hoặc đổi nhu cầu mới.

Không thêm một lần gọi AI: `suggestedTopics` (2–3 nhãn ngăn bằng `|`), `suggestionSource` (ID nguồn current/previous1…4 do backend cấp) và `suggestionContext` (trích ngắn tùy chọn 8–180 ký tự) nằm trong công cụ `respond` của lượt đọc hiểu hiện có. Backend loại gợi ý thiếu căn cứ, sai định dạng hoặc chứa thông tin liên hệ nhạy cảm và dùng hướng tham khảo chung. Các hướng quản lý thời gian/kỹ năng học tập yêu cầu cụm chủ đề có căn cứ trong nội dung đã duyệt; không ghép từ rời rạc hay lấy nhãn người đọc sinh viên làm bằng chứng.

Không hứa có sách cho mọi chủ đề gợi ý; truy xuất catalog vẫn quyết định kết quả sau lựa chọn.

## Kiểm chứng

- 118 bài tự động qua: 64 pipeline, 15 hỏi chủ đề, 10 guard, 4 catalog và 25 chất lượng đọc lời tư vấn. 20 bài bổ sung trong đợt này, gồm căn cứ hiện tại/lịch sử, không nhận lời assistant, nguồn bịa/quá cũ/riêng tư, lựa chọn lỗi/trùng, ngân sách, quà tặng và xác nhận chủ đề.
- 17 lượt riêng trong đợt kiểm thử Gemini trực tiếp trên giao diện (gồm cả trước và sau sửa): câu hỏi, câu trả lời và từng đánh giá nằm trong [UI JSON](topic-suggestions-2026-10-11-ui.json). Các lượt thử trước sửa được giữ riêng bằng mã và ghi chú, không tính là đã qua.
- [Ảnh giao diện](topic-suggestions-2026-10-11.png); [kết quả test](topic-suggestions-2026-10-11-tests.json); [hash build/runtime](topic-suggestions-2026-10-11-runtime.json).

## Giới hạn

Mức liên quan của chủ đề do AI đề xuất vẫn cần đánh giá theo tình huống. Kiểm tra trích dẫn chứng minh khách từng nói hoàn cảnh đó, không chứng minh mọi chủ đề suy ra đều phù hợp. Guard câu chung chung là kiểm tra từ ngữ có giới hạn; không phải bộ hiểu nghĩa hoàn chỉnh. Nếu AI không đề xuất được nguồn hợp lệ thì chatbot vẫn hỏi và đưa vài hướng chung. Đây là một số tình huống nghiệm thu có chủ đích, không phải đo độ chính xác toàn bộ chatbot.
