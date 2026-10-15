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

## Thử nghiệm nhãn cho 20 sách — 2026-10-10

Đã thêm 154 nhãn biên tập vào đúng 20 hồ sơ local, dùng `book_topics`/`book_topic_links` hiện có và màn hình admin “Hồ sơ AI”. Tên nhãn giữ nhóm như `Chủ đề:` trong admin; `BookKnowledgeService` chỉ đưa phần giá trị của bảy nhóm vào chỉ mục để tránh các từ chung gây nhiễu. Các chủ đề cũ không có tiền tố vẫn được giữ. Giới hạn 20 chủ đề mỗi sách của màn hình biên tập vẫn áp dụng.

Xem [nhãn chi tiết](labels-20-2026-10-10.md), [manifest có căn cứ](labels-20-2026-10-10.json), [kế hoạch](label-pilot-plan-2026-10-10.md) và [báo cáo chất lượng](../chatbot-evaluation/labels-20-2026-10-10.md). Đợt pilot ban đầu giới hạn 20 cuốn; sau khi người dùng chốt, nhãn đã được mở rộng như mục dưới.

Công cụ `db/book-label-pilot.py` mặc định chỉ nhận đúng bộ 20 ID của thử nghiệm này (`--scope pilot20`). Bộ mở rộng yêu cầu chỉ rõ `--scope remaining36`. Mặc định kiểm tra và báo kế hoạch, không ghi. Đặt `MYSQL_PWD` trong môi trường tiến trình rồi chạy từ gốc repo:

```powershell
python .\db\book-label-pilot.py .\docs\book-knowledge\labels-20-2026-10-10.json --expected-snapshot .\VinaBookStore\src\test\resources\book-label-pilot\catalog-before.json --mysql '<đường dẫn mysql.exe>' --host 127.0.0.1 --port 3306 --user '<tài khoản>' --database book
```

Thêm `--apply` để nhập vào database đã xác định. Công cụ kiểm tra tên/tác giả, trạng thái đã duyệt, căn cứ nguyên văn, URL của hồ sơ và giới hạn chủ đề, khóa dữ liệu và ghi trong transaction MySQL 8.0.16+. Chỉ thêm chủ đề, cập nhật `search_text`, xóa embedding cũ để worker hiện có tạo lại và cập nhật thời điểm sửa. Backup trước ghi nằm trong `.run-logs/book-label-pilot/` mặc định; không đưa backup vào Git. Snapshot trong test là dữ liệu đánh giá cố định, không được dùng làm catalog runtime.

Để khôi phục, chạy lại cùng lệnh với `--rollback '<đường dẫn backup.json>'` để xem phạm vi; thêm `--apply` để thực hiện. Khôi phục chỉ được commit khi định danh, nội dung nguồn, chỉ mục và tập chủ đề hiện tại còn khớp trạng thái sau nhập. Chỉ gỡ các liên kết được thêm bởi lần nhập, phục hồi chỉ mục/embedding trước nhập, không xóa hồ sơ hoặc sản phẩm. Các hàng tên chủ đề không còn liên kết có thể vẫn còn trong bảng chủ đề.

Kiểm thử công cụ từ gốc repo:

```powershell
python -m unittest discover -s db -p test_book_label_pilot.py -v
```

### Thử xếp hạng theo nhóm nhãn — 2026-10-10

Bản thử tiếp theo giữ nguyên 20 cuốn/154 nhãn, bổ sung điểm theo nhóm chủ đề, mục đích và các trường hồ sơ đã duyệt. Cụm từ và mức đáp ứng toàn yêu cầu được ưu tiên; trong một nhóm chỉ lấy nhãn khớp tốt nhất. Liên kết và tên chủ đề được đọc theo lô cho các ứng viên có hồ sơ đã duyệt. Bộ lọc và cách sắp xếp do khách chỉ định vẫn xử lý trước xếp hạng mặc định.

Đã đo cùng dữ liệu trước/sau trên mẫu 20 sách và toàn catalog 56 sách: 30 câu cũ tăng từ 27 lên 30 đúng vị trí đầu, 30 diễn đạt mới tăng từ 26 lên 28; tám câu kiểm tra sách ngoài mẫu giữ 8/8. Đây là phép đo truy xuất tắt embedding, không phải độ chính xác toàn chatbot. Chưa mở rộng nhãn; xem [báo cáo xếp hạng và hội thoại thật](../chatbot-evaluation/ranking-20-2026-10-10.md).


## Mở rộng nhãn ra 56 sách — 2026-10-10

Theo yêu cầu người dùng, đã thêm 284 nhãn có căn cứ vào 36 sách còn lại. Hiện 56/56 sách đang bán có 438 liên kết nhãn chi tiết. Giữ nguyên toàn bộ 20 cuốn pilot, chủ đề cũ, tên/tác giả/mô tả, giá/tồn kho và các trường nội dung đã duyệt. Mỗi cuốn mới có 7–10 nhãn; không vượt giới hạn 20 chủ đề của editor.

Xem [nhãn của 36 sách](labels-remaining-36-2026-10-10.md), [manifest có căn cứ](labels-remaining-36-2026-10-10.json) và [báo cáo mở rộng](../chatbot-evaluation/labels-expanded-2026-10-10.md). Nguồn hồ sơ đã duyệt ngày 05/10 được dùng lại; đây không phải kiểm chứng mới về đúng ấn bản. Các chi tiết chưa biết vẫn được ghi riêng và không trở thành nhãn tìm kiếm.

Nhập bộ 36 cuốn bằng scope riêng, mặc định vẫn chỉ xem kế hoạch:

```powershell
python .\db\book-label-pilot.py .\docs\book-knowledge\labels-remaining-36-2026-10-10.json --scope remaining36 --expected-snapshot '<snapshot catalog trước nhập>' --mysql '<đường dẫn mysql.exe>' --host 127.0.0.1 --port 3306 --user '<tài khoản>' --database book
```

Đặt `MYSQL_PWD` trong môi trường tiến trình; thêm `--apply` khi áp dụng vào database đã xác định. Công cụ yêu cầu đúng 36 ID đã chốt và đúng tên/tác giả/hồ sơ/căn cứ, sao lưu trước ghi và dùng transaction có guard. Chạy lại sau nhập trả 0 thay đổi. Khôi phục dùng cùng manifest/scope và `--rollback '<backup.json>'`, kiểm tra kế hoạch trước, thêm `--apply` khi thực hiện. Không dùng bộ fixture đánh giá làm catalog runtime.

Kiểm thử cả hai scope:

```powershell
python -m unittest discover -s db -p 'test_book_label*.py' -v
```

Trong A/B tắt embedding trên toàn catalog, 36 câu mô tả thông thường giữ 36/36 đúng đầu, 12 câu đồng nghĩa tăng 2/12 → 12/12; 68 câu kiểm tra cũ giữ kết quả vị trí đầu. Đây là tập câu hỏi có chủ đích từ nội dung hồ sơ, không phải đánh giá mù hoặc tỷ lệ chính xác thực tế của toàn chatbot.

Sách mới về sau tiếp tục dùng màn hình Hồ sơ AI và quy trình biên tập/duyệt có căn cứ. Không tự gán độ tuổi, độ dễ đọc, công dụng hoặc chi tiết ấn bản còn thiếu.


## Lọc kết quả và hỏi chi tiết — 2026-10-10

Điều kiện chủ đề hẹp được đối chiếu với nội dung đã duyệt trước khi giới hạn danh sách; gợi ý có thể ít hơn ba cuốn. Câu hỏi chi tiết/nối tiếp được trả theo từng ý có căn cứ, không xem dữ liệu thiếu là không có. Xem [báo cáo sửa và kiểm thử](../chatbot-evaluation/matching-detail-2026-10-10.md) để biết phạm vi hỗ trợ và những điểm còn yếu.
