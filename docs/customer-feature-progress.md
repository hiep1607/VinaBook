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
