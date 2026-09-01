# Tổng quan VinaBook

## Trạng thái hiện tại

VinaBook là hệ thống ba tầng đang chạy local với MySQL thật:

| Thành phần | Trách nhiệm |
|---|---|
| `bookstore-ui` | Trải nghiệm mua sách của khách hàng |
| `angular-admin` | Quản trị dữ liệu và vòng đời đơn hàng |
| `VinaBookStore` | API, xác thực, phân quyền và persistence |

Catalog không còn nằm trong source Angular. Storefront tải sách/category từ backend, cache catalog trong phiên ứng dụng và dùng cùng dữ liệu cho trang chủ, danh sách, tìm kiếm, chi tiết và wishlist.

## Ranh giới dữ liệu

- MySQL: user, role, product, category, cart dùng lúc checkout, order/order detail, review/rate, notification.
- `localStorage`: phiên JWT, avatar, địa chỉ, giỏ hàng trước checkout, wishlist, lịch sử tìm kiếm và theme giao diện.
- `sessionStorage`: tối đa 24 tin nhắn chatbot trong tab hiện tại.
- In-memory: buy-now item và toast.

## Luồng phát triển

MySQL và backend phải chạy trước. Storefront dùng proxy `/api` ở chế độ dev; admin gọi backend local trực tiếp. Production của cả hai Angular app dùng URL tương đối để tránh trình duyệt gọi nhầm `localhost`.

## Cấu trúc chính

```text
book/
├── bookstore-ui/       # Storefront Angular
├── angular-admin/      # Admin Angular
├── VinaBookStore/      # Spring Boot API
├── docs/               # Tài liệu hiện hành
├── database.sql        # Database seed/dump
├── *_fix.sql           # Script dữ liệu có chủ đích
└── README.md           # Hướng dẫn chạy workspace
```

Build output, dependency folder, IDE metadata, skill dành cho trợ lý và cấu hình local không thuộc source phát hành.

## Quy ước sản phẩm

- Chuỗi giao diện dùng tiếng Việt.
- Thanh toán hỗ trợ COD, VietQR, VNPay Sandbox và MoMo Test; phương thức online chỉ bật khi backend có credential tương ứng.
- Phí giao hàng `20.000đ`, miễn phí từ `300.000đ`.
- Tài khoản cần đăng nhập cho checkout, profile, orders, wishlist, review và thao tác mua hàng.
- Backend là nơi quyết định quyền truy cập, giá bán, tồn kho và tổng đơn.
- API key chatbot chỉ nằm ở biến môi trường backend; `AI_PROVIDER=local` hoạt động không cần key.
