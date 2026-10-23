# Tự động hóa VinaBook

## Đúng loại tin theo từng tài khoản — 22/10/2026

- `priceDrops` chỉ điều khiển giảm giá sách đang yêu thích. `cartPriceChanges` là lựa chọn riêng cho tăng/giảm giá sách đang trong giỏ; mặc định tắt và khách phải tự bật. Mỗi loại có quy tắc quản trị riêng (`PRICE_DROP`, `CART_PRICE_CHANGE`).
- Điều kiện tạo thông báo là: quy tắc admin bật, đúng tài khoản, đúng loại tin khách bật và có sách/đơn/theo dõi liên quan. Bật email chỉ thêm kênh gửi cho các loại tin đã chọn, không bật mọi loại tin.
- API lưu tùy chọn chỉ cập nhật trường được gửi; trường bị bỏ qua giữ nguyên giá trị đã lưu, bao gồm tác giả theo dõi. Vì vậy sửa một lựa chọn không bật lại các loại tin khách đã tắt.
- Giá trong giỏ được lấy từ catalog sau giảm giá, theo từng chủ giỏ. Lần quét đầu ghi giá mốc; các lần sau báo cả tăng và giảm. Một sách đồng thời nằm trong giỏ và wishlist chỉ tạo một thông báo giá nếu cả hai lựa chọn bật. Xóa khỏi giỏ ngừng theo dõi; thêm lại tạo mốc mới. Tắt loại tin vẫn cập nhật mốc để khi bật lại không gửi bù các thay đổi cũ.
- Thư giá trong giỏ được kiểm tra lại trước gửi: khách vẫn bật loại tin và email, sách còn trong giỏ, giá vẫn khớp nội dung, quy tắc admin còn bật và người nhận vẫn đúng địa chỉ đã chọn. Không gửi thư thật khi SMTP chưa cấu hình.
- Migration production: áp dụng `db/18-cart-price-notifications.sql` một lần. Cột mới mặc định false, giữ nguyên các lựa chọn cũ; checkpoint JSON tương thích với dữ liệu thiếu trường mới. Scanner hiện chạy mỗi 60 giây, tối đa 25 tài khoản mỗi lượt; giao diện cũng có chu kỳ tải lại riêng nên thông báo không xuất hiện ngay tại thời điểm admin sửa giá.

## Email nhận thông báo — 22/10/2026

Khách bật “Gửi thêm qua email” để nhập địa chỉ nhận. Ô email được điền sẵn từ địa chỉ đã lưu hoặc email của chính tài khoản đang đăng nhập; có gợi ý và nút “Dùng email tài khoản”. Khách có thể nhập email khác, không nhập mật khẩu/SMTP và không thay đổi email đăng nhập. Hệ thống không tìm hay gợi ý email tài khoản của người khác.

Địa chỉ được kiểm tra ở giao diện và backend, lưu theo owner từ JWT. Để trống khi lưu sẽ chọn email tài khoản; payload cũ không có trường `emailAddress` giữ nguyên địa chỉ đã lưu. Khách luôn có thể tắt nhận email dù ô nhập đang sửa dở. Tùy chọn có thể lưu trước khi hộp thư gửi của cửa hàng được cấu hình, nhưng khi `emailAvailable=false` backend không xếp thư để gửi và giao diện nói rõ trạng thái chờ dịch vụ.

Khi tạo thư, outbox dùng địa chỉ khách đã chọn và lưu dấu vân tay người nhận trên thông báo. Nếu khách đổi địa chỉ hoặc tắt nhận trước lúc gửi, job cũ bị hủy; thư reset mật khẩu giữ luồng riêng. Người nhận chỉ có một địa chỉ thuần, không nhận display name, danh sách địa chỉ hoặc ký tự header. Địa chỉ thực nằm trong payload outbox đã mã hóa; thông báo chỉ giữ hash.

Migration production: `db/17-notification-email.sql` thêm hai cột nullable. Local với Hibernate update tự thêm cột và giữ dữ liệu cũ. Theo yêu cầu hiện tại chỉ hoàn thiện phần nhập/lưu email; chưa thiết lập hộp thư gửi, chưa bật `AUTOMATION_EMAIL_ENABLED`, chưa gửi email thật.

Các luồng dùng dữ liệu catalog, wishlist, đơn, giỏ, đánh giá và đăng ký chờ có sẵn. Không tạo catalog hay giỏ song song. Tham khảo hành vi công khai qua REA được ghi riêng trong báo cáo nghiên cứu.

## Luồng và điều kiện

| Luồng | Điều kiện tạo thông báo |
|---|---|
| RESTOCK | Đăng ký chờ chuyển sang AVAILABLE, sách đang bán và còn hàng. Mỗi mốc availableAt chỉ tạo một lần. |
| PRICE_DROP | Giá thực bán (sau discount, làm tròn đồng) thấp hơn giá quan sát trước đó của cùng dòng wishlist, sách đang bán/còn hàng. |
| CART_PRICE_CHANGE | Giá thực bán thay đổi so với mốc của sách trong giỏ và khách đã bật `cartPriceChanges`; chỉ gửi cho chủ giỏ có sách đó. |
| ORDER_STATUS | Đơn mới hoặc trạng thái thay đổi. Sự kiện đơn được xử lý sau commit; scanner đối chiếu bổ sung. |
| CART_REMINDER | Giỏ giữ nguyên ít nhất 24 giờ kể từ lần quan sát thay đổi đầu tiên, vẫn có sách đủ tồn kho, không có đơn mua mới kể từ đó. Mỗi lần sửa giỏ chỉ nhắc một lần. |
| REVIEW_REMINDER | Sau 168 giờ kể từ lúc ghi nhận chuyển sang đã giao; chọn sách chưa được tài khoản đánh giá, bỏ qua đơn có yêu cầu RETURN chưa bị từ chối. |
| AUTHOR_RELEASE | Sách được thêm vào danh mục cửa hàng của tác giả đang theo dõi. Không khẳng định ngày phát hành của nhà xuất bản. Theo dõi mới lấy mốc danh mục hiện có, không gửi lại toàn bộ sách cũ. |
| LOW_STOCK | Báo cáo quản trị các sách đang bán có quantity <= ngưỡng (mặc định 5), kèm số đăng ký chờ. Ngưỡng 0 chỉ lấy hết hàng. |

Các mốc giờ và ngưỡng được chỉnh trong trang quản trị. Delay nguyên 1..720 giờ; ngưỡng tồn nguyên 0..10000. Các luồng trong web bật mặc định, email tắt mặc định. Khi tắt luồng, scanner vẫn cập nhật mốc đã quan sát để không dồn thông báo cũ lúc bật lại.

Giỏ đồng bộ tài khoản và sách yêu thích mới ghi mốc giá ngay trong giao dịch thêm sách, nên đổi giá trước lần quét đầu vẫn được phát hiện. Sửa số lượng không ghi đè thay đổi giá đang chờ; bỏ sách rồi thêm lại lấy mốc mới. Khi bật lại nhận tin giá, mốc bắt đầu tại giá hiện tại để không gửi bù thay đổi lúc đã tắt. Dữ liệu lịch sử chưa có mốc vẫn lấy baseline yên lặng ở lần quét đầu. Ngoại lệ chuyển đổi là đăng ký có hàng AVAILABLE còn phù hợp từ chức năng cũ, được đưa vào inbox một lần và giữ trạng thái đã đọc. Đơn lịch sử đã giao không có mốc giao đáng tin cậy nên không gửi lời mời đánh giá hàng loạt.

## Lưu bền, quyền và giới hạn

- Inbox, tùy chọn nhận tin, mốc quan sát, rule và lịch sử chạy lưu MySQL. Unique(owner_id,event_key) ngăn lặp kể cả khởi động lại; xóa inbox là tombstone nên không tạo lại cùng sự kiện.
- API khách lấy owner từ JWT. API admin có role ADMIN và kiểm tra CurrentUserAccess. Không tin owner/email do client gửi.
- Scanner chạy mỗi 60 giây, khóa checkpoint để các instance không chạy chồng cùng lượt, xét 25 tài khoản đang hoạt động mỗi lượt, lưu con trỏ rồi quay vòng.
- Mỗi tài khoản xét 500 wishlist gần nhất, 100 đăng ký chờ, 100 đơn gần nhất, tối đa 500 dòng/đơn và 500 dòng giỏ. Các sách được tham chiếu có lookup theo ID từng nhóm 200, không bị mất vì nằm ngoài danh mục mới nhất.
- Khám phá sách mới theo tác giả xét 1.000 sách đang bán có ID mới nhất. Đây là giới hạn của luồng tác giả, không phải toàn bộ catalog. Tác giả được chọn từ slice này, tối đa 50 tác giả/tài khoản.
- Thời gian bỏ giỏ và mốc giao là thời gian quan sát của hệ thống, không phải timestamp thao tác chính xác trên thiết bị. Không suy ra được các thay đổi qua lại hoàn tất giữa hai lượt scan. Sự kiện đơn sau commit giúp cập nhật nhanh các thao tác qua hệ thống hiện tại.
- Không giữ chỗ tồn kho, không tự mua hàng, không tự đối soát ngân hàng và chưa kết nối API hãng vận chuyển. Trạng thái giao dựa vào đơn trong VinaBook.
- Trang admin hiển thị tối đa 100 sách tồn thấp, 100 đầu sách có lượt đăng ký chờ cao nhất, 20 lần chạy gần nhất. Lịch sử chạy được giữ 30 ngày. waitingReaders là số đăng ký chờ theo sách, không phải tổng người duy nhất toàn cửa hàng.

## Email

Email chỉ được xếp hàng khi cả ba điều kiện đúng: `AUTOMATION_EMAIL_ENABLED=true`, SMTP đã có cấu hình và khách bật email trong tùy chọn. Mặc định toàn cục và khách đều tắt; `emailAvailable` cho UI biết máy chủ đã sẵn sàng hay chưa. Nút Kiểm tra ngay của admin luôn chỉ tạo inbox, không xếp email.

Luồng dùng MailOutboxStore đã mã hóa dữ liệu ở nơi lưu. QUEUED chỉ nghĩa đã xếp hàng, không khẳng định đã gửi. Trước lúc gửi SMTP, guard kiểm tra lại tùy chọn/rule, tồn kho, giá, wishlist, đơn mới, đánh giá/đổi trả và trạng thái đơn. Tin không còn phù hợp được đánh dấu SUPPRESSED và xóa payload job. Kết quả SMTP ghi SENT, hoặc FAILED khi hết lần thử; chỉ thống kê lỗi tự động hóa, không lẫn thư quên mật khẩu. Các email nghiệp vụ đã có giữ hành vi cũ.

## Triển khai và kiểm tra

Local đang dùng `ddl-auto=update`: khởi động lại backend để tạo bảng. Production `ddl-auto=validate`: áp dụng `db/automation-schema.sql` trước khi khởi động. Migration chỉ thêm bảng và có CREATE TABLE IF NOT EXISTS; không chỉnh hay xóa dữ liệu có sẵn.

Kiểm thử dùng H2 riêng và mock outbox, không gửi mail, không sửa database `book`. Bộ integration kiểm tra bảy luồng, chống lặp/đổi tài khoản, định giá sau discount, sách không hoạt động/hết hàng, baseline, xóa tombstone, mail sau mua và giới hạn cấu hình. Bộ outbox kiểm tra trạng thái thực sau gửi/thử lại/hủy job.


## Nhu cầu sách chưa có

`GET /api/admin/catalog-demand` yêu cầu ADMIN, trả tối đa 50 tên sách được tìm nhưng chưa có trong 30 ngày. Chỉ ghi nhận sau một lượt tra catalog thành công, câu hỏi có tên sách rõ ràng khớp đúng tên bị thiếu. Các yêu cầu tư vấn chủ đề chung, cuốn đã có nhưng không đạt bộ lọc, lỗi AI/DB hoặc ngữ cảnh sách cũ không được tính.

Mỗi phiên/tên sách/ngày chỉ tính một lượt bằng khóa SHA-256; không lưu session ID gốc, email, user ID hay nguyên hội thoại trong bảng này. Loại đầu vào chứa liên kết, email, số điện thoại dài, ký tự điều khiển và markup. Đây là số lượt tìm đã giới hạn trùng, không phải số khách duy nhất. Dọn dữ liệu quá 30 ngày hằng ngày. Một số câu hỏi tên sách không rõ hoặc có định dạng đặc biệt sẽ không được ghi nhận để tránh đếm sai.

Local tạo bảng qua ddl-auto=update. Production áp dụng thêm `db/automation-catalog-demand.sql` trước khi khởi động với schema validation. Báo cáo REA đi kèm source tại `docs/automation-research.md`; đây là quan sát trang công khai, không khẳng định đã đọc hệ thống riêng của các bên tham khảo.
