# Các chức năng khách hàng còn lại — 08/10/2026

Thực hiện từng chức năng trên web trước, sau đó nối vào chat. Các thay đổi local không liên quan được giữ nguyên.

## 1. Chi tiết đơn hàng

Trang `/orders/:id` dùng API `/api/orders/me/{id}` xác minh chủ sở hữu từ JWT. Response chỉ chứa thông tin đơn, sách và giao hàng, không trả entity tài khoản. Có tiến trình đơn, tổng tiền từ giá đã lưu, thanh toán lại và các biểu mẫu hủy/mua lại/đổi trả dùng chung. Chat chỉ trả liên kết chi tiết sau khi tra đúng đơn của khách.

5 kịch bản kiểm tra:
1. Mở đơn của mình từ Đơn hàng, Hồ sơ và câu “xem đơn #…” trong chat; mã đơn, sách, giá đã mua và phí giao phải giống nhau.
2. Đổi URL sang mã đơn của tài khoản khác; không được thấy sách, địa chỉ hoặc trạng thái của đơn đó.
3. Mở mã sai/âm/không tồn tại và thử lại sau lỗi mạng; hiển thị đúng trạng thái, không nhân đôi request khi đổi URL.
4. Chuyển đơn hoặc chuyển tài khoản khi response đang chậm; không hiển thị dữ liệu của đơn/tài khoản cũ.
5. Đơn COD đủ điều kiện mở xác nhận hủy; đơn hoàn thành mở đổi trả; mua lại và thanh toán dùng đúng đơn, không tự thực thi khi mới mở trang.

## 2. Hội thoại nhân viên

Khách và nhân viên nhắn trực tiếp trong cùng yêu cầu; cập nhật mỗi 3 giây, có xem tin cũ. API xác minh chủ phiếu/role admin, không đưa nội dung tin vào AI hoặc cache chatbot. Retry giữ cùng client key; nội dung khác cùng key bị từ chối. Phiếu đã đóng chỉ cho xem. Migration: `db/07-support-chat.sql`.

5 kịch bản:
1. Khách gửi yêu cầu ở Liên hệ rồi nhắn; nhân viên trả lời ở admin và khách nhận tin mà không tải lại trang.
2. Mở cùng yêu cầu qua chatbot; hội thoại phải giống trang Liên hệ.
3. Tài khoản khác/khách thường gọi route admin không được đọc hoặc gửi tin.
4. Mạng mất sau khi gửi: thử lại cùng tin chỉ lưu một bản, không mất nội dung đang nhập.
5. Đổi tài khoản/phiếu hoặc đóng widget: polling cũ dừng, nội dung riêng tư xóa; phiếu hoàn tất không nhận tin mới.

## 3. Đồng bộ tài khoản

Wishlist dùng bảng favorites hiện có; địa chỉ/avatar lưu ở customer_preferences với API theo JWT. Có backup trước khi chuyển dữ liệu local, khóa migration theo trình duyệt và revision chống ghi đè từ thiết bị khác. Giá/tồn kho vẫn theo ProductService. Avatar giới hạn PNG/JPEG/GIF/WebP 256 KB. Các thao tác web và chat đợi biên nhận server trước khi báo thành công. Migration: `db/08-customer-preferences.sql`.

5 kịch bản:
1. Đăng nhập cùng tài khoản trên hai trình duyệt; thay đổi yêu thích/địa chỉ/avatar, cập nhật ở trình duyệt kia phải nhận cùng dữ liệu.
2. Tài khoản có dữ liệu local cũ được chuyển một lần và giữ backup; đăng nhập lại không tạo bản trùng hoặc phục hồi sách đã xóa.
3. Hai thiết bị sửa địa chỉ cùng lúc: bản cũ bị yêu cầu tải lại, không ghi đè âm thầm.
4. Mất mạng khi lưu: không báo thành công, không thay cache bằng dữ liệu chưa được server xác nhận.
5. Đổi tài khoản khi đang tải hoặc gửi request: response cũ không cập nhật dữ liệu của tài khoản mới; avatar quá lớn/SVG bị từ chối.

## 4. Hoàn tiền VNPay/MoMo
Nhân viên mở chi tiết đơn hoặc yêu cầu đổi trả, nhập số tiền/lý do, xem lại rồi xác nhận. Backend giữ hạn mức trong transaction trước khi gửi HTTP, khóa đơn và chống gửi trùng. Timeout/thiếu chữ ký/sai số tiền giữ trạng thái cần đối soát; không tự gửi thêm giao dịch. Kết quả chưa chắc chắn chỉ được nhân viên đối soát với mã biên bản. Khách xem trạng thái trên chi tiết đơn (chatbot có nút mở cùng trang). Thanh toán gốc giữ nguyên PAID; giao hàng/tồn kho không đổi do hoàn tiền.
`PAYMENT_REFUNDS_ENABLED=false` mặc định. Bật sau khi cấu hình merchant và quyền hoàn tiền; đơn VNPay cũ thiếu vnp_CreateDate không đủ dữ liệu để hoàn tự động. MoMo tối thiểu 10.000đ để bao phủ thẻ ATM. SQL: db/09-payment-refunds.sql, chạy ALTER một lần.
Tài liệu giao thức: [VNPay](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html), [MoMo](https://developers.momo.vn/v3/docs/payment/api/payment-api/refund/).
5 kịch bản Luna:
1. Hoàn một phần đơn sandbox đã trả tiền; xem hạn mức còn lại và trạng thái khách.
2. Nhấp xác nhận/gửi lại cùng request nhiều lần: chỉ một giao dịch hoàn.
3. Hai yêu cầu đồng thời vượt tổng tiền đơn: yêu cầu sau bị chặn.
4. Timeout hoặc response sai chữ ký/số tiền: chưa báo thành công, giữ hạn mức, đối soát trước yêu cầu mới.
5. Tài khoản khách gọi admin API, xem đơn người khác, đơn COD/chưa trả tiền/cấu hình tắt: bị từ chối, không gửi tiền.

## 5. Mã giảm giá
Admin quản lý mã tại /admin/vouchers. Web checkout và checkout trong chat dùng chung ô áp dụng mã và API tính từ catalog thật. Có giảm tiền/%, trần giảm, tiền sách tối thiểu, thời gian, tổng lượt và lượt theo JWT. Báo giá chưa trừ lượt; confirm khóa mã, kiểm tra lại và ghi lượt cùng transaction đơn/tồn kho. Mã không giảm phí ship; ngưỡng miễn ship vẫn tính trước mã. Chi tiết đơn lưu đúng discount/ship lịch sử. Lượt đã dùng không tự trả lại khi hủy/hoàn tiền.
SQL db/10-vouchers.sql (ALTER một lần); không tự tạo chương trình khuyến mãi thật.
5 kịch bản Luna:
1. Admin tạo mã tiền/%, khách áp dụng trên web và chat: cùng tổng, lưu đúng đơn.
2. Mã hết hạn/chưa bắt đầu/tắt/sai/không đủ tiền sách: giải thích lý do, có thể bỏ mã.
3. Giảm % có trần; mã lớn hơn tiền sách: tối đa tiền sách, không tổng âm, ship theo quy định.
4. Mã còn 1 lượt, hai tài khoản xác nhận đồng thời: chỉ một đơn được giảm; đơn còn lại xem lại báo giá.
5. Retry confirm không dùng thêm lượt; tài khoản đã hết lượt hoặc client sửa discount/giá không được hưởng thêm ưu đãi.

## 6. Thông báo khi có hàng
Trang sách hết hàng và thẻ sách hết hàng trong chat dùng chung RestockWatchComponent/API. Theo dõi nằm trên server theo JWT, một dòng/tài khoản/sách; thao tác lặp không tạo trùng. Job mỗi 60 giây và đọc thông báo cập nhật khi sách được bán lại/tồn kho > 0. Khách mở /notifications từ biểu tượng chuông, xem thời điểm, đánh dấu đã đọc, bỏ theo dõi. Thông báo trong tài khoản, chưa gửi email/push ra ngoài. Không giữ chỗ hoặc hứa ngày nhập sách.
SQL db/11-restock-watches.sql. Mỗi tài khoản tối đa 100 đăng ký hoạt động.
5 kịch bản Luna:
1. Theo dõi một sách hết hàng từ web, mở chat cùng sách thấy đã theo dõi.
2. Nhấp/gửi PUT lặp: một đăng ký; chưa đăng nhập không đăng ký dưới tên người khác.
3. Admin tăng tồn kho: trong tối đa một chu kỳ job có thông báo; đọc/tải lại không nhân đôi.
4. Bỏ theo dõi trước khi nhập hàng: không tạo thông báo; đã đọc trên thiết bị A thấy trạng thái trên B.
5. Sách bị ẩn/xóa hoặc lại hết hàng: không hứa mua được, không tự đặt/giữ sách; đổi tài khoản xóa dữ liệu hiển thị cũ.

Chat có hướng dẫn riêng cho mã giảm giá, thông báo có hàng và tra trạng thái hoàn tiền, không chuyển nhầm thành gợi ý sách. API tra đơn kiểm tra JWT trước khi hiển thị chi tiết hoàn tiền. Avatar cũ quá lớn/khác định dạng giữ nguyên trong bản sao trình duyệt và có thông báo chọn ảnh mới.

Các trang tài khoản/checkout/đơn hàng/yêu thích/thông báo tải theo nhu cầu để giữ bundle khởi động trong giới hạn; AuthGuard vẫn bảo vệ từng route. Các biểu mẫu dùng chung nằm trong CustomerComponentsModule để web và chat giữ cùng hành vi.

Trang chi tiết sách và bìa 3D cũng tải riêng khi mở sách, giữ hành vi route /product/:id và giảm mã tải lúc vào trang chủ.

## Kiểm tra và chạy thử
- Build production storefront đạt, hash 536c57e6717f7226c2b2. Bundle khởi động ES5 1005,38 KB và CSS chi tiết sách 13,99 KB nằm dưới ngưỡng lỗi; còn cảnh báo dung lượng ở ngưỡng khuyến nghị. Các trang tải riêng và guard đã được kiểm tra trên trình duyệt.
- 58 test backend trong 10 lớp liên quan: chi tiết đơn, hội thoại, preferences, refunds, voucher, restock, checkout và điều hướng chat; không có failure/error.
- 5 test chi tiết đơn + 5 hội thoại + 25 test tích hợp storefront + 5 form hoàn tiền admin đã đạt (scoped tests; không chạy lại toàn bộ các spec local cũ).
- Admin kiểm tra kiểu dữ liệu/template đạt. Backend đã khởi động với MySQL local, có 56 sản phẩm; preferences/voucher admin/restock/refund admin trả 401 khi chưa đăng nhập.
- Source storefront chạy tại http://localhost:4200; admin tại http://localhost:4300. Đường dẫn các log chạy nằm trong thư mục kiểm tra Codex, không chứa secret và không đưa vào Git.
- UI đã thử câu “Dùng mã giảm giá thế nào?”: trả hướng dẫn áp mã, tổng tiền và nút giỏ/checkout. Mở trang sách sau khi tải riêng vẫn dựng bìa 3D.
- Chưa thử hoàn tiền thật qua merchant; cần cấu hình và bật PAYMENT_REFUNDS_ENABLED. Thông báo có hàng là thông báo trong tài khoản, chưa tích hợp email/push.

Trang giỏ hàng tải theo nhu cầu cùng module khách hàng, vẫn mở được cho khách chưa đăng nhập; chỉ checkout và các trang riêng tư yêu cầu AuthGuard.

Trang Giới thiệu/Liên hệ cũng tải khi mở; cả hai và Giỏ hàng không yêu cầu đăng nhập để đọc. Biểu mẫu gửi yêu cầu vẫn kiểm tra phiên tài khoản.
