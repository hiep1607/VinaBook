# Thanh toán

VinaBook hỗ trợ bốn phương thức: COD, chuyển khoản VietQR, VNPay Sandbox và MoMo Test. COD luôn khả dụng. Storefront tự khóa phương thức online chưa có cấu hình backend.

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
