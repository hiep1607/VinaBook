# Thanh toán

VinaBook hỗ trợ bốn phương thức: COD, chuyển khoản VietQR, VNPay Sandbox và MoMo Test. COD luôn khả dụng. Storefront tự khóa phương thức online chưa có cấu hình backend.

## Hoàn thiện luồng ngày 21/10/2026

- Checkout hiển thị đủ bốn phương thức, đánh dấu phương thức chưa khả dụng; chỉ chọn được phương thức backend xác nhận đang hoạt động. Lỗi tải có nút thử lại và đưa lựa chọn về COD.
- Khi đơn đã tạo, checkout khóa gửi lại; lỗi mở cổng dẫn tới kết quả của đúng đơn để tiếp tục, không tạo đơn khác. COD dẫn tới chi tiết đơn vừa đặt.
- Trang kết quả phân biệt COD, chờ xác nhận, thất bại, đã trả tiền, hoàn thành và hủy. Không đọc trạng thái thành công từ query trên URL. Khi ảnh VietQR lỗi vẫn có thông tin chuyển khoản; nút kiểm tra không tự xác nhận nhận tiền.
- Đổi URL hoặc tài khoản hủy request cũ và xóa dữ liệu riêng tư. Các lần đọc trạng thái không chạy chồng nhau, tự dừng sau khoảng 5 phút, lỗi mạng hoặc trạng thái kết thúc; khách có thể kiểm tra thủ công.
- VNPay/MoMo lưu URL phiên thanh toán trên đơn và khóa dòng khi mở/xác nhận. Mở lại đơn PENDING dùng phiên cũ. Nếu kết quả tạo giao dịch chưa rõ và không có URL, giữ PENDING, yêu cầu đối soát thay vì tự tạo giao dịch thứ hai. Phiên cũ hết hạn vẫn cần kết quả gateway/đối soát trước khi thử lại; chưa tự truy vấn trạng thái gateway.
- Callback kiểm tra đúng phương thức và tham chiếu trước khi cập nhật. Xác nhận VietQR thủ công yêu cầu admin, không xác nhận đơn đã kết thúc và chỉ trả dữ liệu thanh toán, không serialize entity tài khoản.
- Migration production: áp dụng `db/16-payment-resume.sql` một lần trước khi chạy backend mới với schema validation. Local dùng Hibernate update sẽ thêm cột nullable; không thay dữ liệu đơn cũ.
- Cấu hình mẫu ngân hàng và merchant nằm trong `.env.local.example`; thông tin thật chỉ điền trong `.env.local` hoặc biến môi trường. Chưa có cấu hình thì không thể thực hiện giao dịch online.

## Trạng thái

Mỗi order lưu riêng `paymentMethod`, `paymentStatus`, `paymentReference`, `paymentTransactionId` và `paidAt`.

| Giá trị | Ý nghĩa |
|---|---|
| `UNPAID` | COD chưa thu tiền |
| `PENDING` | Đang chờ chuyển khoản hoặc callback gateway |
| `PAID` | Đã xác nhận đúng chữ ký, mã giao dịch và số tiền; VietQR do admin đối soát |
| `FAILED` | Phiên VNPay/MoMo bị hủy hoặc thất bại, có thể thanh toán lại |

Đơn online chưa `PAID` không thể chuyển sang trạng thái đang giao. COD chuyển `PAID` khi admin hoàn thành giao hàng. Hủy đơn online đã thanh toán bị chặn để tránh mất dấu nghĩa vụ hoàn tiền.

## Cấu hình local và sandbox

Không ghi khóa thật vào `application.properties`, source Angular hoặc Git. Khai báo biến môi trường trước khi chạy backend:

```powershell
$env:FRONTEND_URL = "http://localhost:4200"
$env:BACKEND_PUBLIC_URL = "https://backend-public.example"

$env:BANK_ID = "mã ngân hàng VietQR"
$env:BANK_ACCOUNT_NO = "số tài khoản nhận tiền"
$env:BANK_ACCOUNT_NAME = "tên chủ tài khoản"
$env:BANK_QR_TEMPLATE = "compact2"

$env:VNPAY_TMN_CODE = "mã website sandbox"
$env:VNPAY_HASH_SECRET = "secret sandbox"

$env:MOMO_PARTNER_CODE = "partner code test"
$env:MOMO_ACCESS_KEY = "access key test"
$env:MOMO_SECRET_KEY = "secret key test"
```

`BACKEND_PUBLIC_URL` phải là HTTPS public để VNPay/MoMo gọi được IPN. `localhost` chỉ kiểm thử được redirect trên trình duyệt, không nhận được callback server-to-server từ gateway.

## API

| Endpoint | Quyền | Mục đích |
|---|---|---|
| `GET /api/payments/methods` | Public | Phương thức đang khả dụng, không trả secret |
| `POST /api/payments/orders/{id}/start` | User sở hữu đơn | Tạo QR hoặc URL gateway |
| `GET /api/payments/orders/{id}/status` | Owner/Admin | Đọc trạng thái đã xác thực |
| `PUT /api/payments/orders/{id}/mark-paid` | Admin | Xác nhận VietQR sau đối soát |
| `GET /api/payments/vnpay/ipn` | Public, kiểm chữ ký | Nhận kết quả VNPay server-to-server |
| `POST /api/payments/momo/ipn` | Public, kiểm chữ ký | Nhận kết quả MoMo server-to-server |

Return URL chỉ điều hướng về `/payment/result`. Storefront luôn đọc lại trạng thái từ backend, không tin tham số thành công trên URL.
