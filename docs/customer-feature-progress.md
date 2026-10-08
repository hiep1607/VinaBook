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
