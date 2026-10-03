# Sửa chất lượng hội thoại từ phản hồi người dùng — 03/10/2026

## Những lỗi đã xử lý

1. **Gợi ý theo giới tính khi thiếu sở thích.** Câu “Có cuốn sách nào các bạn nữ đặc biệt ưa thích không?” trước đây có thể dẫn đến danh sách sách luật bất kỳ. Chatbot giờ hỏi chủ đề/sở thích và nói rõ chưa có thống kê yêu thích theo giới tính. Nếu khách đã nêu chủ đề, chatbot vẫn tìm theo chủ đề đó.
2. **Mất tham chiếu cả danh sách.** “Tại sao lại là những cuốn đó?” giờ giải thích các cuốn trong danh sách đang tư vấn, kể cả ngay trước đó vừa xem riêng một cuốn. Không bắt khách chọn lại một cuốn để giải thích cả nhóm.
3. **Giải thích ngắn và lặp.** Lời tư vấn nhận ngữ cảnh nhu cầu ban đầu và các phần đã giải thích. Giới thiệu có thể dùng khoảng hai câu cho mỗi sách; khi hỏi sâu hơn, có thể giải thích 2–4 câu nếu thông tin đã duyệt đủ. Lượt tiếp theo mở rộng về nội dung, đối tượng, mục tiêu hoặc điểm còn thiếu; loại phần giải thích bị chép lại nguyên văn từ lịch sử gần đây. Giới hạn tương ứng là 600/900 ký tự cho mỗi sách, không bắt buộc phải viết đầy giới hạn. Nếu lời diễn giải của AI không đạt kiểm tra dữ kiện, phần thay thế vẫn nối lại yêu cầu và phân biệt kết quả đúng/gần chủ đề, kèm nội dung đã duyệt và các trường còn thiếu khi hỏi sâu; không chỉ in lại đoạn mô tả đơn lẻ. Có thêm bộ đếm chấp nhận/loại diễn giải để kiểm tra chất lượng, không ghi nội dung khách hoặc thông tin tài khoản vào log này.
4. **Bỏ mất giải thích hợp lệ.** Bộ lọc bỏ dấu từng nhầm cụm “mục tiêu học” với “tiểu học” và bỏ lời tư vấn vì cho rằng AI tự gán đối tượng đọc. Đã phân biệt hai cụm này.
5. **Hỏi tiếp sau tìm kiếm rỗng bị lặp câu làm rõ.** Chatbot giữ yêu cầu trước, giải thích vì sao chưa có danh sách và nêu phần thiếu căn cứ. Hỏi tiếp sẽ nhận thêm giải thích về giới hạn thông tin thay vì hỏi lại “cuốn nào?”.
6. **Đổi trả chỉ đọc chính sách.** Đã có ngữ cảnh riêng về yêu cầu đổi trả và lý do trong hội thoại. Thiếu lý do thì hỏi; lý do đã có ngay ở câu đầu hoặc được bổ sung ở câu sau thì hướng dẫn tiếp. Câu hỏi về địa chỉ gửi lại và thời gian hoàn tiền được trả lời theo bước khách đang hỏi. Nội dung chính sách lấy từ nguồn chính sách hiện hành.
7. **Nhầm sách đã mua với giỏ.** Yêu cầu tìm sách đã mua được chuyển sang tra đơn thuộc tài khoản xác thực, kể cả khách gọi nhầm là giỏ. Khách chưa đăng nhập được hướng dẫn đăng nhập để tra cứu.

## Luồng đổi trả

- “Tôi muốn trả hàng” → hỏi lý do.
- “Sách bị lỗi in/rách/giao sai” → đối chiếu chính sách và hướng dẫn lấy mã đơn, gửi yêu cầu qua Liên hệ, cung cấp ngày nhận/tình trạng, giữ sách và hóa đơn để chờ xác nhận.
- “Tôi chán rồi, muốn trả hàng” → dùng lý do thay đổi sở thích đã có, không hỏi lại và không hứa nhận trả.
- “Gửi lại về đâu?” → hướng dẫn xin địa chỉ/cách gửi được cửa hàng xác nhận; không tự đoán địa chỉ hoặc bên chịu phí.
- “Bao lâu hoàn tiền?” → nêu cần cửa hàng xác nhận và không tự đặt thời hạn hoàn tiền.
- Chuyển sang tìm sách mới → ngừng áp dụng bước hỏi lý do đổi trả cho tin nhắn về sách.

Chatbot chỉ hướng dẫn và tra cứu. Các câu trả lời trên không tự tạo phiếu trả hàng hoặc xác nhận hoàn tiền.

## Kiểm thử

- **223 ca kiểm thử backend:** `ChatConversationRefinementTest`, `ChatToolsTest`, `ChatHistoryTest`, `ChatIntentServiceTest`, `ChatDialogueFlowTest`; không có lỗi. Trong đó có 17 ca mới cho các lỗi trên, gồm ngăn lời giới thiệu tự khẳng định biết chương trình/học phần của khách.
- **22 lượt trong 8 hội thoại thực tế qua link ngrok:** Gemini 3.1 Flash Lite, storefront đang chạy, backend và MySQL thật. Đây là gọi API của app qua đường dẫn công khai, không phải nhập tay từng câu trên giao diện trình duyệt.
- Câu trả lời nguyên văn và kết quả kiểm tra được lưu trong [results-conversation-live-2026-10-03-final.md](results-conversation-live-2026-10-03-final.md), [JSON](results-conversation-live-2026-10-03-final.json) và [checks](results-conversation-live-2026-10-03-final-checks.json).
- Mỗi bộ dùng một phiên khách thử mới. Chỉ các phiên thử đó được xóa sau khi lưu kết quả; không xóa hội thoại của khách dùng thử.
- Vòng kiểm thử nhanh có một lần bị HTTP 429 ở câu tìm sách pháp luật do chạm bộ giới hạn gửi tin của app. Đã chạy lại phần còn lại sau khi cửa sổ giới hạn được đặt lại và thêm khoảng nghỉ tối thiểu 3,2 giây giữa các câu trong script. Giới hạn bảo vệ của app được giữ nguyên; đây không phải lỗi hạn mức Gemini.

## Giới hạn thực tế

Một số sách chỉ có mô tả ngắn, chưa có đủ dữ kiện đã duyệt về mức đọc, đối tượng hoặc phong cách. Khi đã hết thông tin có căn cứ, chatbot cần nói rõ phần chưa xác nhận; không kéo dài câu trả lời bằng việc tự bổ sung nội dung sách. Ca mục tiêu rèn thói quen trong bộ này kiểm tra cả trường hợp chưa tìm được sách đủ liên quan.
