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
