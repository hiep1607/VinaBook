# Hồ sơ sách đã đối chiếu — 2026-10-05

## Dữ liệu đang dùng

Chatbot đọc catalog và hồ sơ đã duyệt trong MySQL `book`; tệp JSON ở thư mục này là bản biên tập có nguồn để cập nhật các hồ sơ hiện có. Tệp không tạo một catalog riêng và không được nạp cố định vào prompt.

Đã đối chiếu và cập nhật **56/56 sách đang bán** trong database local của dự án: đợt đầu 33 cuốn, đợt tiếp theo 23 cuốn. Mức độ xác minh khác nhau: có cuốn đủ nguồn mô tả nội dung, có cuốn mới xác minh tên/chủ biên hoặc đặc điểm chung của bộ. Đây **không phải 56 ấn bản đã được xác minh đầy đủ**. Sau hai đợt:

| Trường / chỉ mục | Số lượng |
|---|---:|
| Hồ sơ đã duyệt của sách đang bán | 56 |
| Hồ sơ đã qua đối chiếu biên tập | 56 |
| Có đối tượng / sở thích đọc trong `audience` | 56 |
| Có phong cách trình bày trong `readingStyle` | 55 |
| Có mục tiêu đọc trong `learningGoal` | 55 |
| Có căn cứ riêng về mức đọc trong `readingLevel` | 4 |
| Liên kết chủ đề mới trong hai đợt | 176 (98 + 78) |
| Tổng liên kết chủ đề | 232 |
| Hồ sơ còn chờ embedding ở lần kiểm tra cuối | 0 |

Đợt đầu cập nhật 31 tóm tắt; đợt sau cập nhật 23 tóm tắt và đồng bộ với mô tả catalog. Truyện Kiều chỉ bổ sung đối tượng và chủ đề; Conan giữ tóm tắt cũ, bổ sung thông tin đọc. Bốn hồ sơ có `readingLevel` là Clean Code, Cho Tôi Xin Một Vé Đi Tuổi Thơ, AI - Sức Mạnh Của Trí Tuệ Nhân Tạo và Blockchain Cơ Bản. Trường trống ở các cuốn khác vẫn thể hiện thiếu căn cứ; không đồng nghĩa sách khó đọc.

Đợt 23 cuốn sửa tên hoặc tác giả của 15 sản phẩm (10 tên, 12 tác giả; có sản phẩm đổi cả hai). Hai ảnh đang là bìa của tác phẩm khác được thay bằng SVG có nhãn “Ảnh minh họa”, được phục vụ ở storefront và admin. Giá, tồn kho, số đã bán, giảm giá, danh mục, ID và thông tin ấn bản giữ nguyên qua kiểm tra trước/sau nhập. Xem [báo cáo đợt 23 cuốn](../chatbot-evaluation/book-profiles-remaining-2026-10-05.md).

## Quy tắc biên tập

- Dùng trang nhà xuất bản, tác giả, đơn vị phát hành/giữ quyền, thư viện quốc gia/đại học, viện nghiên cứu và CSDL văn bản pháp luật chính thức. URL, loại nguồn, các trường được hỗ trợ và ghi chú đối chiếu nằm trong hai manifest `profiles-reviewed-2026-10-05.json` và `profiles-reviewed-remaining-2026-10-05.json`.
- Phạm vi là **tác phẩm / bộ truyện** (`scope: work`). Khi chưa xác định chính xác bản bán tại cửa hàng, không lấy ISBN, số trang, năm in, nhà xuất bản hoặc đặc điểm bản dịch từ một ấn bản khác.
- Nội dung được diễn đạt lại ngắn gọn. Chủ đề, sở thích người đọc và mục tiêu đọc là ánh xạ biên tập từ nội dung có căn cứ; không phải số liệu khảo sát người mua hay khẳng định tác dụng của sách.
- Không suy ra độ dễ đọc từ số chương ngắn; không lấy tuổi nhân vật làm tuổi độc giả; không lấy phong cách kể chuyện của tác giả làm kỹ thuật mà sách dạy khách.
- Phân loại tuổi của NXB được ghi với phạm vi bộ truyện và lưu nguồn. Đây là cơ sở xếp gợi ý, không phải kết luận y khoa hoặc quy định cấm đọc.
- Nội dung tâm linh, tiềm thức và quan điểm tác giả được ghi như nội dung tác phẩm; không chuyển thành bảo đảm khoa học hoặc sức khỏe.
- `reviewed: true` ghi nhận việc đối chiếu biên tập trong đợt này. Công cụ nhập kiểm tra cấu trúc, nguồn được cho phép và tính nhất quán dữ liệu; công cụ không tự chứng minh mọi câu từ đều đúng với nguồn.

Ví dụ nguồn quan trọng: [NXB Trẻ giới thiệu Cho Tôi Xin Một Vé Đi Tuổi Thơ](https://www.nxbtre.com.vn/diem-tin/gioi-thieu-sach-cho-toi-xin-mot-ve-di-tuoi-tho-1348.html), [NXB Trẻ giới thiệu Mắt Biếc](https://www.nxbtre.com.vn/sach/mat-biec-146386.html), [Dale Carnegie — nguyên tắc đầu tiên của Đắc Nhân Tâm](https://www.dalecarnegie.com/en/resources/how-to-win-friends-and-influence-people-chapter-1). Danh sách nguồn đầy đủ nằm trong JSON.

## Cách nhập và cập nhật chỉ mục

Từ thư mục gốc repo, đặt `MYSQL_PWD` trong môi trường tiến trình; không ghi mật khẩu vào JSON hoặc Git. Cần Python và MySQL CLI. Ví dụ kiểm tra kế hoạch, với đường dẫn CLI phù hợp máy chạy:

```powershell
python .\db\apply-reviewed-book-profiles.py .\docs\book-knowledge\profiles-reviewed-2026-10-05.json --mysql '<đường dẫn mysql.exe>' --host 127.0.0.1 --port 3306 --user '<tài khoản>' --database book
```

Mặc định chỉ đọc và báo kế hoạch. Thêm `--apply` để áp dụng vào database đã xác định. Với 23 cuốn tiếp theo, dùng `profiles-reviewed-remaining-2026-10-05.json` ở cùng thư mục. Cần MySQL 8.0.16 trở lên để kiểm tra `CHECK` trong transaction có hiệu lực. Đây là các manifest cho catalog hiện có ngày 2026-10-05; seed sạch chỉ có ID 3..55 nên chưa đủ tất cả các tên trong manifest.

Công cụ:

1. Đối chiếu **tên và tác giả chính xác**, yêu cầu duy nhất một sản phẩm đang bán với hồ sơ đã duyệt. Manifest có sửa catalog phải khớp thêm `productId`; tên/tác giả đã sửa cũng được nhận diện để chạy lại an toàn.
2. Sao lưu catalog, hồ sơ và liên kết chủ đề trước khi ghi, vào `.run-logs/book-profile-backups/`. Không đọc tài khoản hay đơn hàng.
3. Khóa các hàng, kiểm tra dữ liệu chưa bị sửa kể từ lúc lập kế hoạch, cập nhật trong một transaction và rollback khi kiểm tra không đạt.
4. Chỉ sửa tên, tác giả, mô tả khi manifest có trường `catalog` và nguồn đối chiếu; chỉ thay bìa sai bằng placeholder có ghi chú kiểm tra. Giữ giá, tồn kho, thông tin ấn bản và quan hệ sách. Bổ sung chủ đề; không xóa các liên kết cũ.
5. Cập nhật `search_text`, đặt embedding cũ về `NULL`. Bộ xử lý chỉ mục hiện có tạo lại embedding; trong lúc chờ, hồ sơ đã duyệt và tìm kiếm văn bản vẫn dùng được.

Lần chạy lại sau nhập trả về **0 thay đổi**. Nếu hồ sơ đã được biên tập tiếp, công cụ từ chối ghi đè kế hoạch cũ: cần đối chiếu và tạo bản biên tập mới. Bản sao lưu chứa trạng thái trước và kế hoạch nhập để phục hồi có kiểm tra; không có lệnh tự động ghi đè mọi dữ liệu từ backup.

Khi thêm hoặc sửa sách qua ứng dụng, tiếp tục dùng `BookKnowledgeService` và luồng duyệt hồ sơ hiện có. Sau commit, dữ liệu tìm kiếm được làm mới và bộ xử lý chỉ mục cập nhật embedding. Việc thêm sách không cần sửa manifest này hay sửa prompt chatbot.

## Những thông tin vẫn cần xác minh

23 cuốn đã được xử lý trong manifest tiếp theo, nhưng các trường chưa đủ nguồn vẫn để trống và chatbot cần nói rõ khi khách hỏi vượt quá dữ liệu.

| ID | Sách | Thông tin cần bổ sung / đối chiếu |
|---|---|---|
| 3, 4, 7 | Hiến Pháp; Luật Doanh Nghiệp; Luật Đất Đai Và Văn Bản Hướng Dẫn Thi Hành | ISBN/năm in/bản cụ thể để kiểm tra nhãn “hiện hành”; không suy ra tính cập nhật pháp luật từ tên catalog |
| 5, 6, 8 | Bộ Luật Lao Động; Cẩm Nang Pháp Luật Dành Cho Doanh Nghiệp; Bộ Luật Dân Sự | Tên/tác giả đã sửa; còn thiếu bản in và mức cập nhật pháp luật |
| 11, 12, 39 | Số Đỏ; Vang Bóng Một Thời; Chí Phèo | Không gắn độ dễ đọc tùy ý; Chí Phèo chưa xác nhận ngôn ngữ và truyện khác đi kèm |
| 14, 15, 24 | Việt Nam Sử Lược; Lịch Sử Thế Giới Hiện Đại; Đại Việt Sử Ký Toàn Thư | Bản dịch/tập; mốc cuối của giáo trình lịch sử thế giới; Đại Việt Sử Ký không mặc định trọn bộ |
| 17, 18 | AI - Sức Mạnh Của Trí Tuệ Nhân Tạo; Blockchain Cơ Bản | Đã xác minh tác phẩm/tác giả; còn thiếu ISBN/bản in cửa hàng |
| 20, 21, 22, 23, 44, 45 | Các giáo trình cấu trúc dữ liệu, mạng, Java, cơ sở dữ liệu, AI, kinh tế vĩ mô | Tên/tác giả đã đối chiếu; Java và cơ sở dữ liệu chưa đủ mục lục để khẳng định framework, phiên bản hoặc chương cụ thể |
| 35, 36 | Kho Tàng Truyện Cổ Tích Việt Nam; 10 Vạn Câu Hỏi Vì Sao | Hai bìa sai đã thay ảnh minh họa; vẫn cần bìa thật, bộ/tập và nhóm tuổi của đúng sản phẩm |
| 54 | Muôn Kiếp Nhân Sinh | Chưa xác nhận tập/bản in; chỉ dùng đặc điểm chung của tác phẩm/bộ |

Không còn hồ sơ nào chỉ giữ ghi chú “Mô tả sản phẩm hiện có trong catalog”. Điều đó ghi nhận phạm vi biên tập đã phủ catalog, không thay thế việc xác minh các ấn bản còn thiếu ở trên.
