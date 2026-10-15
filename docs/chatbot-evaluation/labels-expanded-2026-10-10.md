# Mở rộng nhãn ra toàn danh mục — 2026-10-10

## Kết quả dữ liệu

Đã mở rộng theo yêu cầu người dùng: 36 cuốn còn lại có thêm 284 nhãn chi tiết, 7–10 nhãn/cuốn. Kết hợp với 20 cuốn/154 nhãn của pilot, hiện 56/56 sách đang bán có tổng 438 liên kết nhãn chi tiết trong bảy nhóm hiện có.

Nhãn lấy từ các trường hồ sơ đã duyệt ngày 05/10. Mỗi nhãn có trường nguồn, căn cứ nguyên văn và URL của hồ sơ. Đây là phân loại biên tập và bản dịch từ đồng nghĩa, không phải kiểm chứng mới nguồn hay đúng ấn bản. Không sửa tên/tác giả/mô tả, giá, tồn kho, tóm tắt hoặc dữ kiện đã duyệt để làm đẹp kết quả.

Đối chiếu MySQL trước/sau xác nhận:

- 36 cuốn được thêm đúng 284 liên kết mới; mọi liên kết chủ đề cũ được giữ.
- Toàn bộ 20 hàng sách/hồ sơ/chủ đề trong pilot giữ nguyên.
- Dữ liệu catalog và các trường nội dung đã duyệt giữ nguyên trên cả 56 cuốn.
- Không cuốn nào vượt giới hạn 20 chủ đề của editor.
- Chạy lại importer trả về 0 sách/0 nhãn cần thay đổi.
- Có backup trước ghi và lệnh rollback có kiểm tra trạng thái; backup chỉ ở thư mục tác vụ local, không đưa vào Git.

Chỉ ghi vào `book_topics`, `book_topic_links`, `book_profiles.search_text`, xóa embedding cũ của 36 hồ sơ để worker cập nhật và cập nhật thời điểm sửa. Không thay schema hoặc tạo catalog runtime thứ hai. Các mục chưa biết/giới hạn chỉ nằm trong manifest biên tập, không được thêm thành nhãn.

## Đánh giá truy xuất trước/sau

Cố định 48 câu hỏi mới trước khi viết bộ nhãn mở rộng: 36 câu theo nội dung mỗi cuốn và 12 câu dùng từ đồng nghĩa. SHA256: `f9344b088e35982114aee472212a8d6de1bc2e5a3e1721c279cbdc62f45f6896`.

Chạy cùng code truy xuất thật và cùng 56 sách; nhánh trước đã có 20 sách pilot, nhánh sau thêm nhãn cho 36 sách còn lại. Tắt embedding và không gọi AI ở cả hai nhánh để đo riêng tác động của dữ liệu nhãn. Tập câu hỏi được xây từ nội dung hồ sơ, không phải thử nghiệm mù hoặc mẫu đại diện cho tần suất dùng thực tế.

| Nhóm | Số câu | Top1 trước → sau | Top3 trước → sau |
|---|---:|---:|---:|
| Nội dung 36 sách còn lại | 36 | 36 → 36 | 36 → 36 |
| Từ đồng nghĩa của sách còn lại | 12 | 2 → 12 | 5 → 12 |
| 30 câu gốc của pilot | 30 | 30 → 30 | 30 → 30 |
| 30 câu biến thể của pilot | 30 | 30 → 30 | 30 → 30 |
| 8 câu kiểm tra sách trước đây ngoài pilot | 8 | 8 → 8 | 8 → 8 |

Không có câu nào đang đúng vị trí đầu bị giảm theo tập đáp án cố định này. Không sửa câu hỏi/ID kỳ vọng sau khi thấy kết quả. Kết quả 116/116 ở vị trí đầu sau nhập chỉ có ý nghĩa với bộ kiểm tra này, không phải tỷ lệ chính xác của toàn chatbot. Nhãn mới chủ yếu cải thiện khả năng tìm bằng cách gọi khác; các câu tiếng Việt mô tả sát nội dung vốn đã tìm đúng trước khi mở rộng.

## Kiểm thử

- Python: 19 tests qua, gồm 11 tests pilot và 8 tests scope mở rộng; kiểm tra căn cứ, danh sách ID, chống lẫn scope, idempotence, giữ pilot và rollback.
- Backend: 54 tests qua, 0 lỗi/failure/skipped; gồm A/B toàn catalog, bảo toàn dữ liệu, guard chủ đề/thông tin chưa biết và hồi quy hội thoại structured.
- Không sửa code runtime Java ở lượt mở rộng này. Backend đọc nhãn từ database hiện có; không cần nạp toàn bộ build hoặc restart web.

## Chatbot thật

Kiểm tra thủ công trên `localhost:4200` với Gemini thật, ở trạng thái khách, sau nhập nhãn. Giữ lịch sử sẵn có; dùng câu chuyển chủ đề rõ ràng. Tám kịch bản, chín lượt gửi gồm một lượt hỏi lại có thêm từ khóa: **5 đạt, 3 một phần, 1 lỗi**. Không thay lượt lỗi bằng kết quả hỏi lại.

| Lượt | Kết quả | Nhận xét |
|---|---|---|
| U01 | Đạt | Từ computer networking tìm đúng Mạng Máy Tính Và Các Hệ Thống Mở, có giới hạn chuẩn mạng chưa rõ. |
| U02 | Một phần | Đúng cuốn, không bịa chuẩn mạng mới; còn dùng mô tả chung thay vì nêu trực tiếp Wi-Fi 6/5G. |
| U03 | Một phần | Kho Tàng Truyện Cổ Tích Việt Nam đứng đầu, nhưng Việt Nam Sử Lược và Truyện Kiều chỉ liên quan rộng mà vẫn bị nhóm Sách đúng yêu cầu. |
| U04 | Đạt | Không khẳng định bản minh họa/độ tuổi 5; nêu rõ chưa xác định tập, không mặc định dành riêng trẻ nhỏ. |
| U05 | Lỗi | Chatbot trả thông báo chưa xử lý được, không trả sách; giữ nguyên kết quả lỗi trong báo cáo. |
| U05R | Đạt | Lượt hỏi lại có thêm từ kinh tế vĩ mô trả đúng Giáo Trình Kinh Tế Vĩ Mô; không xóa lỗi lượt trước, chưa xác định nguyên nhân. |
| U06 | Đạt | Nhớ đúng giáo trình vĩ mô, trả riêng bài tập và đáp án đều chưa xác nhận. |
| U07 | Một phần | Số Đỏ đứng đầu và đúng chủ đề; Chí Phèo/Tắt Đèn liên quan hiện thực phê phán nhưng không đáp ứng chắc yêu cầu trào phúng, vẫn nhóm Sách đúng yêu cầu. |
| U08 | Đạt | Không coi Số Đỏ là truyện chỉ vui/nhẹ nhàng; nêu đúng hài hước kèm phê phán châm biếm. |

Kết quả truy xuất đúng sách đứng đầu chưa đồng nghĩa toàn câu trả lời đạt. U03/U07 đưa đúng sách đầu tiên nhưng hai sách liên quan rộng vẫn bị xếp chung vào “Sách đúng yêu cầu”; cần cải thiện việc giới hạn hoặc phân nhóm gợi ý phụ. U02 không bịa chuẩn mạng, nhưng chưa trả lời trực tiếp Wi-Fi 6/5G. U05 báo chưa xử lý được; U05R có bổ sung cụm “kinh tế vĩ mô” trả đúng sách. Chưa xác định nguyên nhân lỗi U05, và không xem câu hỏi đã sửa là bằng chứng câu gốc đã hết lỗi. Các điểm này được ghi lại, chưa sửa code hội thoại trong đợt mở rộng dữ liệu.

Xem [nguyên văn các lượt kiểm tra](labels-expanded-ui-2026-10-10.json) và [ảnh giao diện](labels-expanded-ui-2026-10-10.png).


## Giới hạn còn giữ

Không thêm các nhãn không đủ nguồn như SQL/framework/chuẩn mạng mới/bài tập/đáp án, độ tuổi cụ thể, tính cập nhật pháp luật, tác dụng chữa lành hoặc lợi nhuận. Những tên chung như Kho Tàng Truyện Cổ Tích Việt Nam, 10 Vạn Câu Hỏi Vì Sao, Muôn Kiếp Nhân Sinh vẫn chưa xác định đúng tập/ấn bản; không lấy nội dung tập nguồn làm nội dung chắc chắn của sản phẩm cửa hàng.

Importer yêu cầu rõ `--scope remaining36`, đúng manifest và đúng danh sách 36 ID. Mặc định `pilot20` vẫn từ chối nhập bộ mở rộng. Việc gắn nhãn cho sách mới về sau tiếp tục qua màn hình duyệt Hồ sơ AI hiện có; không tự suy đoán rồi duyệt dữ liệu.

## Bằng chứng và tệp thay đổi

- [Bộ nhãn 36 cuốn](../book-knowledge/labels-remaining-36-2026-10-10.md) và [manifest có căn cứ](../book-knowledge/labels-remaining-36-2026-10-10.json).
- [Đối chiếu database](labels-expanded-db-2026-10-10.json), [kết quả importer](labels-expanded-import-2026-10-10.json), [A/B truy xuất](labels-expanded-ab-2026-10-10.json), [kết quả tests](labels-expanded-tests-2026-10-10.json).
- Importer `db/book-label-pilot.py` bổ sung scope `remaining36`; mặc định vẫn `pilot20`. Bổ sung `db/test_book_label_expansion.py` và `BookLabelExpansionEvaluationTest`; tái sử dụng fixture truy xuất pilot qua overload, không sửa runtime Java.

Nhãn đã được áp dụng vào MySQL local của web đang chạy. Chưa push GitHub trong lượt này.
