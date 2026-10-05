# Hoàn thiện 23 hồ sơ sách còn lại — 2026-10-05

## Kết quả

Đã cập nhật vào **MySQL local `book` của `D:\book\book`** theo yêu cầu đối chiếu nội dung trên mạng và sửa catalog. Đây là đợt 23 cuốn sau đợt 33 cuốn trước đó. Manifest có nguồn: [23 hồ sơ đã đối chiếu](../book-knowledge/profiles-reviewed-remaining-2026-10-05.json).

- 23 tóm tắt, 23 đối tượng đọc, 23 phong cách trình bày, 23 mục tiêu đọc; bổ sung mức đọc cho AI - Sức Mạnh Của Trí Tuệ Nhân Tạo và Blockchain Cơ Bản.
- Sửa tên hoặc tác giả của 15 sản phẩm: 10 tên và 12 tác giả, có cuốn sửa cả hai. Đồng bộ mô tả catalog với hồ sơ đã duyệt.
- Hai ảnh của tác phẩm khác ở ID 35 và 36 được thay bằng ảnh minh họa có nhãn rõ ràng, có cùng tệp trong storefront và admin.
- Thêm 78 liên kết chủ đề; tổng hiện có 232. Embedding đã tạo lại, không còn hồ sơ đang chờ.
- Sau hai đợt, 56/56 sách đang bán có hồ sơ đã qua đối chiếu biên tập. **Không đồng nghĩa 56 ấn bản đã xác minh đầy đủ**: một số cuốn mới có căn cứ tên/tác giả hoặc nội dung chung của bộ.
- Kiểm tra trước/sau: ID, giá, số lượng, giảm giá, số đã bán, trạng thái, danh mục, ngày nhập, cờ đọc thử và trường thư mục/ấn bản không đổi.

Các ID trong đợt này: 3, 4, 5, 6, 7, 8, 11, 12, 14, 15, 17, 18, 20, 21, 22, 23, 24, 35, 36, 39, 44, 45, 54.

## Các tên và tác giả đã sửa

Nguồn trong bảng dùng để đối chiếu tác phẩm/tác giả hoặc phạm vi văn bản. Ghi chú và phạm vi từng trường nằm trong manifest; không lấy thông tin của một bản in khác để điền ISBN, số trang hoặc năm xuất bản cho sản phẩm đang bán.

| ID | Dữ liệu trước | Dữ liệu sau | Nguồn đối chiếu |
|---|---|---|---|
| 3 | Hiến Pháp Nước Cộng Hòa Xã Hội Chủ Nghĩa Việt Nam (Hiện Hành) — Quốc Hội Việt Nam | Hiến Pháp Nước Cộng Hòa Xã Hội Chủ Nghĩa Việt Nam Năm 2013 (Sửa Đổi, Bổ Sung Năm 2025) — Quốc Hội Việt Nam | [NXB Chính trị quốc gia Sự thật](https://www.nxbctqg.org.vn/xuat-ban-hai-tai-lieu-phap-ly-quan-trong-thiet-yeu.html) |
| 4 | Luật Doanh Nghiệp (Hiện Hành) — Quốc Hội Việt Nam | Luật Doanh Nghiệp — Quốc Hội Việt Nam | [CSDL văn bản pháp luật](https://vbpl.moj.gov.vn/bolaodong/Pages/vbpq-toanvan.aspx?ItemID=142881&Keyword=) |
| 5 | Luật Lao Động Thực Hành — Nguyễn Văn Phần | Bộ Luật Lao Động — Quốc Hội Việt Nam | [NXB Chính trị quốc gia Sự thật](https://nxbctqg.org.vn/bo-luat-lao-dong-hien-hanh--54.html) |
| 6 | Cẩm Nang Pháp Lý Cho Doanh Nghiệp — Trương Thanh Đức | Cẩm Nang Pháp Luật Dành Cho Doanh Nghiệp — Tăng Bình & Ái Phương (hệ thống) | [Thư viện ĐH Đông Á](https://thuvien.donga.edu.vn/sach/thong-tin?id=22321) |
| 7 | Luật Đất Đai Và Văn Bản Hướng Dẫn Thi Hành — Bộ Tư Pháp | Luật Đất Đai (Sửa Đổi, Bổ Sung Năm 2024) Và Các Văn Bản Hướng Dẫn Thi Hành — Quốc Hội Việt Nam (văn bản luật) | [NXB Chính trị quốc gia Sự thật](https://nxbctqg.org.vn/luat-dat-dai-47.html) |
| 8 | Tìm Hiểu Bộ Luật Dân Sự Việt Nam — Đỗ Văn Đại | Bộ Luật Dân Sự — Quốc Hội Việt Nam | [NXB Chính trị quốc gia Sự thật](https://nxbctqg.org.vn/bo-luat-dan-su-hien-hanh--23.html) |
| 15 | Lịch Sử Thế Giới Hiện Đại — Nguyễn Anh Thái | Lịch Sử Thế Giới Hiện Đại — Nguyễn Anh Thái (chủ biên) | [Thư viện ĐH Công nghiệp Hà Nội](https://lic.haui.edu.vn/vn/gioi-thieu-sach-moi/lich-su-the-gioi-hien-dai/63442) |
| 17 | AI Cơ Bản - Trí Tuệ Nhân Tạo Cho Mọi Người — Phạm Huy Hoàng | AI - Sức Mạnh Của Trí Tuệ Nhân Tạo — Nguyễn Văn Lượng | [Nhà sách Bella](https://nhasachbella.com/san-pham/sach-ai-suc-manh-cua-tri-tue-nhan-tao/) |
| 18 | Blockchain Fundamentals - Nền Tảng Công Nghệ Chuỗi Khối — Don Tapscott | Blockchain Cơ Bản — Daniel Drescher | [Nhã Nam](https://nhanam.vn/blockchain-co-ban-nha-nam) |
| 20 | Giáo Trình Cấu Trúc Dữ Liệu Và Giải Thuật — Đinh Mạnh Tường | Giáo Trình Cấu Trúc Dữ Liệu Và Giải Thuật — Đỗ Trung Kiên (chủ biên), Đặng Xuân Thọ, Nguyễn Thị Hồng, Phạm Thị Lan | [Thư viện ĐH Quốc tế Hồng Bàng](https://thuvien.hiu.vn/Opac/DmdInfo.aspx?=&collection_id=27&dmd_id=27629&heading_id=5) |
| 21 | Giáo Trình Mạng Máy Tính — Nguyễn Thúc Hải | Mạng Máy Tính Và Các Hệ Thống Mở — Nguyễn Thúc Hải | [Thư viện ĐH Công nghiệp Hà Nội](https://lic.haui.edu.vn/vn/gioi-thieu-sach-moi/mang-may-tinh-va-cac-he-thong-mo/67056) |
| 22 | Giáo Trình Lập Trình Java — Trần Đình Quế | Giáo Trình Lập Trình Java — Phạm Xuân Lâm (chủ biên) | [Thư viện Quốc gia](https://nlv.gov.vn/dmdocuments/tmqg-09-2024.pdf) |
| 23 | Giáo Trình Cơ Sở Dữ Liệu — Phạm Thị Anh Lê | Giáo Trình Cơ Sở Dữ Liệu — Phan Tấn Quốc & Nguyễn Thị Uyên Nhi | [NXB ĐHQG TP.HCM](https://vnuhcmpress.edu.vn/giao-trinh-co-so-du-lieu-b40207.html) |
| 44 | Giáo Trình Nhập Môn Trí Tuệ Nhân Tạo — Đinh Mạnh Tường | Giáo Trình Nhập Môn Trí Tuệ Nhân Tạo — Từ Minh Phương | [NXB Khoa học tự nhiên và Công nghệ](https://nxbkhcntt.vn/node/15847) |
| 45 | Giáo Trình Kinh Tế Vi Mô — Nguyễn Văn Công | Giáo Trình Kinh Tế Vĩ Mô — Lê Thanh Tùng | [Thư viện ĐH Mở TP.HCM](https://thuquan.ou.edu.vn/chi-tiet-sach/Kinh-t%C3%A1%C2%BA%C2%BF-MTcwOA/Giao-trinh-Kinh-te-vi-mo-%282025%29%5B1%5D-MUIwODQ2MjY) |

## Giới hạn xác minh

- Các sách pháp luật: ghi rõ giới hạn bản in và cập nhật. Bìa Hiến pháp ghi sửa đổi năm 2025; dữ liệu này không chứng minh bản bán đã tích hợp mọi thay đổi tại ngày khách hỏi. Bỏ nhãn “Hiện Hành” thiếu căn cứ ở Luật Doanh Nghiệp.
- Java và Cơ Sở Dữ Liệu: tên/chủ biên được đối chiếu, nhưng chưa đủ mục lục để khẳng định Java 21, Spring Boot, SQL Server hoặc chương cụ thể. Hồ sơ không dùng các chi tiết chưa xác nhận làm căn cứ chọn sách đúng yêu cầu.
- Luật Đất Đai: phân biệt cơ quan ban hành văn bản luật với người hệ thống/biên soạn tuyển tập; chưa xác minh người biên soạn của đúng bản cửa hàng.
- Kho Tàng Truyện Cổ Tích Việt Nam và 10 Vạn Câu Hỏi Vì Sao: bìa cũ không thuộc tác phẩm tương ứng. Ảnh mới là minh họa, cần bìa thật và tập/bộ cụ thể để xác minh nhóm tuổi, bản rút gọn và truyện/chủ đề có trong cuốn bán.
- Đại Việt Sử Ký Toàn Thư và Muôn Kiếp Nhân Sinh: chưa xác định tập/bản, không mặc định giá là trọn bộ. Chí Phèo chưa xác nhận ngôn ngữ hoặc các truyện khác đi kèm đúng bản bán.
- Đối tượng và mục tiêu đọc là ánh xạ biên tập từ nội dung, không phải khảo sát thị hiếu hoặc bảo đảm sách đem lại kết quả cho mọi người.

## Các lỗi chatbot phát hiện và sửa trong lần kiểm tra

| Vấn đề | Xử lý |
|---|---|
| Nhiều sách cùng có vài từ trong tên bị kéo vào khi khách hỏi một cuốn cụ thể | Ưu tiên tên được nhắc đầy đủ, vẫn giữ hai tên khi khách thật sự so sánh |
| Câu nối tiếp “bản đó”, “tập đó”, “cuốn đó hay …” mất sách đang nói tới | Dùng cuốn/danh sách trước đó để tra nội dung hoặc so sánh; câu hỏi về bản luật không chuyển nhầm sang chính sách cửa hàng |
| Hỏi cả tác giả/chủ biên và nội dung/điều kiện đọc nhưng chỉ trả một phần | Nhận diện các phần được hỏi và ghép thông tin có căn cứ; không tự thêm giá khi khách không hỏi |
| Tên Don Tapscott bị hiểu nhầm là tên sách không tồn tại hoặc từ “đơn” | Phân biệt tên tác giả với đơn hàng, trả thông tin Daniel Drescher của sản phẩm hiện có |
| Bỏ dấu làm “vi mô” và “vĩ mô” trùng nhau | Giữ ý nghĩa hai lĩnh vực trước khi chuẩn hóa; tìm vi mô không lấy vĩ mô làm kết quả đúng yêu cầu |
| Câu “chưa xác nhận Spring Boot” lại được dùng làm chứng cứ có Spring Boot | Chỉ chấp nhận thông tin khẳng định; lưu yêu cầu bắt buộc cho câu hỏi tiếp theo |
| Đổi từ Spring Boot sang Java vẫn giữ điều kiện cũ; cảnh báo Java 21 loại cả sách Java | Tách chủ đề mới khỏi phần yêu cầu đã bỏ; xét tên sách độc lập với chi tiết phiên bản chưa xác minh |

## Kiểm chứng

- Backend được biên dịch và chạy `Chat*Test,Book*Test`: **379 kiểm thử, 0 lỗi, 0 bỏ qua**. Thêm kiểm thử tái hiện điều kiện cũ khi đổi chủ đề, tên gần nhau, vĩ mô/vi mô và nội dung framework chưa xác nhận.
- Công cụ nhập: 5 kiểm thử kiểm tra manifest/nguồn/trường được phép qua. Trước nhập đã kiểm tra ID sai bị từ chối và dữ liệu cũ trong kế hoạch gây rollback toàn bộ catalog/hồ sơ/chủ đề của transaction.
- Sau nhập, chạy cả manifest 33 và 23 cuốn ở dry-run: **0 thay đổi**. Đối chiếu dữ liệu được bảo vệ của cả 56 sản phẩm với snapshot trước nhập: khớp.
- Hai SVG được storefront phục vụ HTTP 200, có nhãn “Ảnh minh họa”; SHA-256 trùng với bản trong admin. Đã xem hai trang sản phẩm trên trình duyệt. Admin cổng 4300 không chạy ở lần kiểm tra HTTP cuối, nên kiểm tra cuối với admin chỉ xác nhận tệp giống nhau.
- Chạy **74 lượt API ứng dụng thật** qua storefront `localhost:4200` → Spring Boot/Gemini/MySQL; model báo `gemini-3.1-flash-lite`. Mỗi tình huống tạo phiên khách riêng, câu nối tiếp đọc bộ nhớ máy chủ; không gửi history giả vào request. Mọi lượt HTTP 200 và `fallback: false`; 45/45 phiên kiểm thử được xóa sau khi chạy (HTTP 204).
- HTTP 200 và không fallback chỉ chứng minh luồng chạy được. Các lần đầu vẫn có lỗi nội dung/định tuyến, được giữ nguyên trong kết quả và sửa ở các lần tiếp theo.

### Kết quả bản cuối trên app thật

| Chuỗi câu hỏi | Kết quả đã kiểm tra |
|---|---|
| Chỉ lấy sách dạy Spring Boot → bỏ Spring Boot, chuyển sang Java → bỏ Java, đổi sang vĩ mô | Không có sách Spring Boot được xác nhận → đúng Java ID 22 → đúng vĩ mô ID 45, không kèm sách lập trình không phù hợp |
| Chỉ lấy Spring Boot đã xác nhận → còn cuốn nào đúng chủ đề | Cả hai lượt không gắn sách Java có cảnh báo nội dung vào nhóm đúng yêu cầu |
| Chỉ tìm vi mô, không lấy vĩ mô → còn cuốn nào đúng yêu cầu | Cả hai lượt báo chưa có sách phù hợp; không trả sách vĩ mô |
| Blockchain hỏi tác giả và có cần toán/lập trình không | Lần kiểm tra trước bản cuối trả cả Daniel Drescher và điều kiện đọc theo hồ sơ đã đối chiếu |

### Câu hỏi và câu trả lời nguyên văn

| Đợt | Lượt hỏi | Tệp kết quả |
|---|---:|---|
| Lần đầu: đủ 23 cuốn | 35 | [Câu hỏi/câu trả lời](results-book-profiles-remaining-live-2026-10-05.md), [JSON đầy đủ](results-book-profiles-remaining-live-2026-10-05.json) |
| Kiểm tra lại tham chiếu, tác giả và nội dung | 22 | [Câu hỏi/câu trả lời](results-book-profiles-remaining-verified-live-2026-10-05.md), [JSON đầy đủ](results-book-profiles-remaining-verified-live-2026-10-05.json) |
| Tái hiện lỗi tìm vi mô và Spring Boot | 2 | [Câu hỏi/câu trả lời](results-book-profiles-remaining-discovery-live-2026-10-05.md), [JSON đầy đủ](results-book-profiles-remaining-discovery-live-2026-10-05.json) |
| Kiểm tra bộ lọc; phát hiện điều kiện cũ khi đổi chủ đề | 8 | [Câu hỏi/câu trả lời](results-book-profiles-remaining-final-live-2026-10-05.md), [JSON đầy đủ](results-book-profiles-remaining-final-live-2026-10-05.json) |
| Bản cuối: đổi chủ đề và kiểm tra điều kiện đúng yêu cầu | 7 | [Câu hỏi/câu trả lời](results-book-profiles-remaining-switch-verified-live-2026-10-05.md), [JSON đầy đủ](results-book-profiles-remaining-switch-verified-live-2026-10-05.json) |

Đây là kiểm thử API app thật và xem hai trang bìa sách, chưa phải kiểm thử toàn bộ thao tác widget, tài khoản đăng nhập, checkout hoặc đơn hàng trong đợt này. Bản sửa đã được khởi động trên backend local để người dùng thử tiếp.

## Cập nhật và phục hồi dữ liệu

Công cụ nhập dùng transaction, khóa hàng và kiểm tra trạng thái trước khi ghi. Bản sao lưu trước nhập: `.run-logs/book-profile-backups/before-20261005-182331-044734.json`; snapshot các trường được bảo vệ: `.run-logs/book-profile-backups/catalog-before-remaining-20261005.json`. Đây là dữ liệu local không đưa vào Git. Công cụ nhận diện tên/tác giả đã sửa khi chạy lại, nhưng từ chối ghi đè hồ sơ đã được biên tập tiếp bằng kế hoạch cũ.

Khi sửa/thêm sách qua ứng dụng, tiếp tục dùng catalog và hồ sơ MySQL hiện có; chỉ mục tìm kiếm/embedding được làm mới qua luồng hiện có. Xem [hướng dẫn hồ sơ](../book-knowledge/README.md) và [công cụ nhập](../../db/README.md).

## Phần cần cải thiện tiếp

Thông tin bản in còn thiếu cần bổ sung từ đúng sách đang bán. Một số câu trả lời xác minh còn lặp đoạn giới hạn hoặc dùng lời dẫn theo mẫu; đợt này đã xử lý dữ liệu và những lỗi cụ thể gặp khi kiểm tra, chưa chứng minh mọi cách diễn đạt mới đều được hiểu đúng.
