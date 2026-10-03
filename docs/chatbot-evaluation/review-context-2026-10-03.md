# Rà soát chatbot VinaBook — 03/10/2026

## Lỗi đã tái hiện trên app

| Trường hợp | Kết quả trước sửa | Nguyên nhân và cách sửa |
|---|---|---|
| Vừa hỏi trinh thám, rồi hỏi sách tương tự Clean Code | Báo không còn sách, vẫn tìm theo trinh thám | Nhánh hỏi thêm chạy trước khi xác định cuốn mới. Ưu tiên cuốn được nhắc, lưu ID gốc, dùng liên kết hoặc nội dung liên quan đã xác nhận. |
| Trinh thám **hoặc** tình yêu dưới 150k | Chỉ có Conan, bỏ mất chủ đề tình yêu | Nhánh có ngân sách chỉ lấy chủ đề đầu; truy vấn nhiều chủ đề còn lọc như AND. Giữ toán tử, tìm hợp các nhóm, lọc toàn bộ rồi chọn số cuốn. |
| Vài sách lập trình **và vài** sách trinh thám, tổng không quá 5 | Trả rỗng; sau bản sửa trung gian, trả cả 5 sách lập trình | Phân biệt các nhóm lựa chọn với một cuốn kết hợp các nội dung. Chọn lần lượt từ mỗi nhóm trong một giới hạn tổng, giữ thứ tự sắp xếp khi khách yêu cầu rõ. |
| Không lấy Java, chỉ lấy Python | Đưa Java và sách lập trình tổng quát vào nhóm gần chủ đề | Loại trừ bị coi là từ khóa tích cực; điều kiện chỉ lấy một ngôn ngữ chưa được giữ khi mở rộng. Lưu điều kiện loại trừ và giữ đúng ngôn ngữ ở cả nhánh tìm và sách tương tự. |
| Bỏ giới hạn giá nhưng giữ chủ đề sau khi đã yêu cầu 6 cuốn | Số lượng trở về mặc định 5 | AI điền scope mới làm mất số yêu cầu. Nhận diện bổ sung bộ lọc và xóa giá trực tiếp, giữ số lượng trước. |
| Lời diễn giải một số sách | Tự nói khả năng áp dụng cho Python hoặc nhịp kể/quá trình trưởng thành chưa có trong mô tả | Bổ sung kiểm tra nội dung chưa xác nhận; bỏ từng nhận định không hợp lệ, giữ phần dữ liệu và dẫn dắt hợp lệ. Không chào lại sau lượt đầu. |

## Kiểm thử backend

Java 17 biên dịch thành công; **258 kiểm thử đạt, 0 lỗi, 0 bỏ qua**, trong 8 lớp: ChatToolsTest (102), ChatServiceTest (57), ChatMemoryStoreTest (3), BookKnowledgeServiceTest (4), StoreKnowledgeServiceTest (2), ChatDialogueFlowTest (24), ChatIntentServiceTest (59), ChatExplicitFiltersTest (7).

Các ca mới kiểm tra cuốn gốc sau khi đổi chủ đề, giá và loại trừ trong nhánh tương tự, giữ gốc khi hỏi thêm, OR và AND, giới hạn chung của các nhóm, giữ số lượng khi bỏ giá, điều kiện chỉ lấy Python, nhánh local, và nhận xét nội dung chưa xác nhận.

## Phản hồi từ ứng dụng thật

Chạy qua `localhost:4200/api/chat`, proxy tới backend dùng Gemini 3.1 Flash Lite và MySQL. Mỗi bộ dùng phiên khách thử riêng. Chỉ xóa các phiên thử này sau khi ghi phản hồi; không xóa cuộc trò chuyện của người dùng hoặc thao tác admin/mua hàng.

- [Phản hồi trước sửa](results-context-live-2026-10-03.md) và [JSON trước sửa](results-context-live-2026-10-03.json): 12 câu.
- [Phản hồi trung gian](results-context-intermediate-live-2026-10-03.md): giữ bằng chứng trường hợp cả 5 vị trí bị nhóm lập trình chiếm.
- [Phản hồi sau sửa](results-context-fixed-live-2026-10-03.md) và [JSON sau sửa](results-context-fixed-live-2026-10-03.json).

**14/14 câu trả HTTP 200, không dùng fallback.** Các bộ bị ảnh hưởng bởi lần chỉnh chọn đại diện và diễn đạt đã được chạy lại; file sau sửa giữ phản hồi mới nhất theo mã câu hỏi.

Kết quả đã đọc lại:

- Quanh Clean Code: trả Pragmatic Programmer, Cấu Trúc Dữ Liệu Và Giải Thuật, Lập Trình Java và Cơ Sở Dữ Liệu; hỏi thêm trả rỗng, giữ đúng cuốn gốc, không quay về trinh thám.
- Trinh thám hoặc tình yêu dưới 150k: Conan và Mắt Biếc đúng yêu cầu; Hoa Vàng Trên Cỏ Xanh và Không Gia Đình được ghi riêng là gần chủ đề. Tổng 4, nói rõ thiếu so với yêu cầu 5. Hỏi thêm không lặp lại.
- Vài sách ở mỗi chủ đề, tổng tối đa 5: 3 sách lập trình và 2 sách trinh thám, không còn cả danh sách chỉ thuộc một nhóm.
- Chỉ Python, không Java: kho hiện không có lựa chọn đáp ứng, trả rỗng ở cả hai lượt; không tự thay bằng sách tổng quát.
- Quanh Clean Code, không Java, dưới 150k: chỉ Cấu Trúc Dữ Liệu Và Giải Thuật (83.600đ) và Cơ Sở Dữ Liệu (90.160đ); hỏi thêm trả rỗng, không bổ sung Java hoặc Pragmatic Programmer vượt giá.
- Yêu cầu 6 sách lập trình: có 5; lọc dưới 100k còn 3; bỏ giá trở lại 5 và vẫn nói thiếu so với 6. Hỏi thêm trả rỗng.

## Phạm vi

Đợt này sửa backend chọn sách, ngữ cảnh và diễn đạt. Không thay đổi giao diện hoặc streaming. Kiểm tra live sử dụng API thật qua storefront; không phải một lần chạy tự động toàn bộ giao diện. Việc gợi ý vẫn dùng hồ sơ và mô tả đã duyệt của catalog, không tự thêm sách để đủ số lượng.
