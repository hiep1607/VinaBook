# Bộ câu hỏi kiểm thử thủ công chatbot VinaBook

Tạo ngày 29/09/2026. Bộ 1–4 đã được chạy qua endpoint chat stream của app; xem [bản ghi phản hồi nguyên văn](archive/2026-09-10/results-exact-live-2026-09-29.md) và [dữ liệu JSON đầy đủ](archive/2026-09-10/results-exact-live-2026-09-29.json). Bộ 5 bị chặn ở bước tạo sách thử: form admin yêu cầu ảnh bìa và tải ảnh qua Cloudinary; cả ảnh SVG lẫn PNG đều trả lỗi HTTP, ảnh xem trước không xuất hiện, nên sách chưa được lưu. Admin đang hiển thị `0 of 0`. Các ảnh bìa thử được lưu trong cùng thư mục để dùng lại sau khi xử lý lỗi upload. Khi tiếp tục, gửi từng câu trong giao diện chat và đợi câu trả lời in xong. Các câu ghi “phiên mới” phải bắt đầu cuộc chat mới; các câu trong cùng một chuỗi phải gửi liên tiếp ở cùng phiên. Giá và tồn kho cần đối chiếu catalog tại thời điểm thử, không lấy số cố định trong tài liệu cũ.

## Bộ 1 — Hiểu nhu cầu diễn đạt tự nhiên

Mỗi câu dùng một phiên mới. Kiểm tra chatbot tìm đúng hướng sách, không chỉ dựa vào một từ khóa trùng khớp và không hỏi lại dữ kiện đã có.

1. Tôi thích những câu chuyện về một chuyến đi dài, gặp nhiều người và khám phá thế giới. Có sách nào hợp không?
2. Mã nguồn của tôi ngày càng rối, tôi muốn đọc gì để viết dễ hiểu và dễ bảo trì hơn?
3. Tôi muốn thử mở một công ty nhỏ và kiểm chứng ý tưởng trước khi đầu tư nhiều tiền. Nên đọc gì?
4. Tôi hay ngại bắt chuyện với đồng nghiệp, muốn đọc sách để giao tiếp tự tin hơn.
5. Mình cần sách phát triển bản thân, khoảng 100.000đ một cuốn. Gợi ý vài lựa chọn hiện có nhé.
6. Mình mua quà cho bé 8 tuổi. Có sách nào nên cân nhắc?

## Bộ 2 — Dữ liệu sách và so sánh

Mỗi câu dùng một phiên mới. Kiểm tra tên rút gọn, dữ liệu catalog đang bán, cách đưa lời khuyên và việc nói rõ khi thiếu dữ liệu đã duyệt.

1. So sánh Nhà Giả Kim với Hoàng Tử Bé, cuốn nào hợp để đọc nhẹ nhàng hơn?
2. Nên chọn Clean Code hay Khởi Nghiệp Tinh Gọn nếu tôi đang phân vân giữa học lập trình và làm startup?
3. Nhà Giả Kim hiện giá bao nhiêu và còn chính xác mấy bản?
4. Hoàng Tử Bé do ai viết, còn bán không?
5. Sherlock Holmes ở cửa hàng có những bản nào? Đừng tính sách chỉ nhắc tới nhân vật này trong mô tả.
6. Cho tôi số trang và ISBN của Nhà Giả Kim; nếu chưa có thông tin được xác nhận thì nói rõ.
7. Cửa hàng có sách tên “Nhật Ký Atlantis 2099” không? Nếu không có, đừng đoán giá.

## Bộ 3 — Hai cuộc hội thoại nhiều lượt

**Phiên A — giữ chủ đề và tránh lặp sách:**

1. Gợi ý sách phiêu lưu đang bán.
2. Còn cuốn khác không?
3. Nếu chủ đề này hết sách thì chuyển sang trinh thám nhé.
4. Chỉ lấy những cuốn dưới 150.000đ.

**Phiên B — tham chiếu đúng cuốn vừa chọn:**

1. So sánh Nhà Giả Kim và Hoàng Tử Bé.
2. Cuốn nào rẻ hơn?
3. Cuốn rẻ hơn đó còn bao nhiêu bản?
4. Tôi muốn mua cuốn đó.

Kiểm tra câu 2 của phiên A không lặp lại sách đã hiện; câu 3 đổi chủ đề thực sự; câu 4 giữ chủ đề trinh thám. Ở phiên B, “cuốn đó” phải trỏ về đúng sách vừa xác định và yêu cầu mua không tự tạo đơn.

## Bộ 4 — Thiếu thông tin, chính sách và thao tác

**Phiên C — hỏi đúng phần còn thiếu:**

1. Tôi muốn tìm một cuốn sách.
2. Về tâm lý nhé.
3. Tối đa 120.000đ.

**Phiên D — bổ sung cuốn so sánh:**

1. So sánh Nhà Giả Kim giúp tôi.
2. Với Hoàng Tử Bé.

**Các câu độc lập, mỗi câu một phiên mới:**

1. Đơn hàng 290.000đ và 310.000đ thì phí giao hàng khác nhau thế nào theo chính sách hiện tại?
2. Nếu sách bị lỗi, điều kiện đổi trả hiện tại là gì? Nếu chưa có thời hạn được công bố thì nói rõ.
3. Khi chưa đăng nhập, bạn có xem được đơn hàng gần nhất của tôi không?
4. Thêm Nhà Giả Kim vào giỏ giúp tôi.
5. Mua ngay hai cuốn Hoàng Tử Bé trong chat.
6. Hủy đơn gần nhất của tôi.

Kiểm tra chatbot không hỏi lại chủ đề hoặc ngân sách sau khi đã nhận, không bịa chính sách/đơn hàng, và không âm thầm thêm giỏ hay hủy đơn khi thiếu xác nhận hoặc quyền tài khoản.

## Bộ 5 — Sách mới và dữ liệu thay đổi (cần tài khoản admin)

Chuẩn bị một sách thử có **tên duy nhất** trong admin, ví dụ `Nhật Ký Sao Lam TEST-<mã riêng>`, mô tả về quan sát bầu trời, giá 89.000đ, tồn kho 5. Xuất bản theo quy trình hiện tại rồi dùng tên thực tế thay cho `[TÊN SÁCH THỬ]` bên dưới. Dùng phiên mới sau mỗi lần sửa dữ liệu.

1. Cửa hàng có `[TÊN SÁCH THỬ]` không?
2. `[TÊN SÁCH THỬ]` nói về gì, giá và tồn kho hiện tại là bao nhiêu?
3. Có sách nào nói về quan sát bầu trời không?
4. Sau khi admin đổi giá thành 95.000đ và tồn kho thành 3: `[TÊN SÁCH THỬ]` bây giờ giá bao nhiêu, còn mấy bản?
5. Sau khi admin ngừng xuất bản: `[TÊN SÁCH THỬ]` còn đang bán không?

Kiểm tra chatbot thấy sách ngay sau xuất bản, đọc lại giá/tồn kho sau cập nhật và không giới thiệu sách đã ngừng bán. Kết thúc thử nghiệm, xử lý sách thử theo quy trình dữ liệu của dự án.
