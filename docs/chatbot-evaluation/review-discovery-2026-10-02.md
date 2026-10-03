# Hoàn thiện gợi ý thêm sách — 02/10/2026

## Thay đổi

- “Còn cuốn khác” giữ chủ đề/bộ lọc nhưng loại các sách đã hiển thị trong cả hội thoại, kể cả sau câu hỏi thông tin sách hoặc chính sách. Khi đọc hội thoại cũ, danh sách ID được khôi phục từ transcript một lần.
- Mặc định tối đa 5 cuốn mỗi lượt. Số khách yêu cầu được ưu tiên hơn số do AI điền. Bổ sung bộ lọc giữ số đã yêu cầu; hỏi thêm không nêu số mới dùng tối đa 5. “Tất cả” có thể trả nhiều hơn.
- Chọn nhóm đúng yêu cầu trước, sau đó mới bổ sung nhóm gần chủ đề nếu được phép. Có nhãn riêng, lý do, số lượng thực tế và thông báo thiếu. Hết cả hai nhóm trả danh sách rỗng. Điều kiện giá, rating, tác giả, trình độ, tồn kho và tổng ngân sách được giữ.
- Tìm gần chủ đề dùng nội dung và liên kết đã duyệt. Không dùng tên tác giả làm bằng chứng; không coi mọi “hành trình” là phiêu lưu hoặc mọi “gia đình” là tình cảm. Chủ đề mới được nói rõ ưu tiên hơn scope sai từ AI.
- Lời giải thích có thể nối vào mục tiêu đọc bằng Gemini, cùng dẫn dắt/bước trao đổi tiếp theo phù hợp. Tên, giá, số lượng, nhóm sách và actions do backend giữ. Nhận xét về độ tuổi, độ khó hoặc văn phong không có trong mô tả bị bỏ; lỗi diễn đạt giữ câu trả lời xác thực.
- Nhánh không có AI dùng cùng công cụ chọn sách.

## Kiểm chứng

Backend Java 17 biên dịch thành công. **244 kiểm thử đạt, không lỗi**, gồm 8 lớp kiểm thử về tools, fallback, ngữ cảnh, nhận diện, bộ lọc và dữ liệu đã duyệt. Có ca yêu cầu 9 cuốn với kho thử đủ sách để xác nhận không bị giới hạn 5/8, ca phân nhóm, tổng ngân sách, không lặp và khôi phục hội thoại cũ.

15 tình huống trong 7 bộ được gửi tới **app đang chạy qua `localhost:4200/api/chat`**, dùng Gemini 3.1 Flash Lite và MySQL thực. Các phiên khách thử được tạo riêng và xóa sau khi ghi kết quả; không thay đổi hội thoại của người dùng. Sau khi sửa các vấn đề phát hiện, chạy lại những bộ bị ảnh hưởng. Phản hồi nguyên văn và JSON đầy đủ nằm trong [results-discovery-fixed-live-2026-10-02.md](results-discovery-fixed-live-2026-10-02.md) và [bản JSON](results-discovery-fixed-live-2026-10-02.json).

15/15 phản hồi HTTP 200, không dùng fallback; đã đọc lại câu trả lời và danh sách sách thực tế.

Các kết quả đáng chú ý:

- Trinh thám: trả Conan và Sherlock Holmes. Xen câu hỏi tồn kho và phí giao hàng, rồi hỏi thêm nhiều lần: không trả lại hai cuốn cũ; nói hiện chưa có lựa chọn khác đủ phù hợp.
- Yêu cầu 6 cuốn trinh thám: trả 2, nói rõ chưa đủ 6. Yêu cầu chỉ đúng thể loại cũng giữ kết quả này.
- Yêu cầu 9 cuốn lập trình: kho hiện có 5 cuốn đáp ứng, trả 5 và thông báo thiếu. Không bổ sung sách công nghệ chung để đủ số.
- Phát triển bản thân: 3 sách; không đưa Pragmatic Programmer vào vì chỉ trùng từ “thói quen”.
- Yêu cầu 6 cuốn phiêu lưu: 1 đúng yêu cầu và 3 gần chủ đề (Không Gia Đình, Nhà Giả Kim, Harry Potter), nói tổng 4 và chưa đủ 6. Hỏi tiếp trả rỗng, không lặp 4 cuốn.
- Đổi từ trinh thám sang tình yêu: Mắt Biếc đúng chủ đề; Tôi Thấy Hoa Vàng Trên Cỏ Xanh và Không Gia Đình ở nhóm gần chủ đề. Loại gợi ý do tên tác giả, gia đình mafia hoặc tình bạn chung chung gây nhầm.

Các lần chạy trước sửa được giữ trong `results-discovery-live-*`, `results-discovery-intermediate-live-*` và `results-discovery-before-content-guard-*` để đối chiếu.

## Phạm vi

Không đổi giao diện hoặc streaming trong đợt này. Các ca live kiểm tra API qua proxy của storefront, không thao tác mua hàng/admin. Chất lượng phân loại vẫn phụ thuộc mô tả và hồ sơ sách đã duyệt; dữ liệu thiếu thì thông báo thiếu thay vì cố lấp đủ danh sách.
