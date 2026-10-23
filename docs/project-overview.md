# Tổng quan VinaBook

## Trạng thái hiện tại

VinaBook là hệ thống ba tầng đang chạy local với MySQL thật:

| Thành phần | Trách nhiệm |
|---|---|
| `bookstore-ui` | Trải nghiệm mua sách của khách hàng |
| `angular-admin` | Quản trị dữ liệu và vòng đời đơn hàng |
| `VinaBookStore` | API, xác thực, phân quyền và persistence |

Catalog không còn nằm trong source Angular. Storefront tải sách/category từ backend, cache catalog trong thời hạn ngắn và dùng cùng dữ liệu cho trang chủ, danh sách, tìm kiếm, chi tiết và wishlist.

## Ranh giới dữ liệu

- MySQL: catalog, tài khoản, giỏ, địa chỉ/avatar/wishlist, đơn/đánh giá/hỗ trợ/voucher/thông báo; snapshot/transcript chat và nháp checkout riêng.
- localStorage: JWT và cache theo tài khoản, lịch sử tìm kiếm, theme và tối đa 24 tin chat gần nhất để đọc khi mất mạng.
- sessionStorage: lựa chọn mua ngắn hạn qua đăng nhập/tải lại, chỉ mã sách/số lượng; không lưu thông tin giao hàng ở nháp trình duyệt.
- In-memory: toast, cache catalog 60 giây, bộ nhớ chat có giới hạn và hàng đợi email có retry.

## Luồng phát triển

MySQL và backend phải chạy trước. Storefront dùng proxy `/api` ở chế độ dev; admin gọi backend local trực tiếp. Production của cả hai Angular app dùng URL tương đối để tránh trình duyệt gọi nhầm `localhost`.

## Cấu trúc chính

```text
book/
├── bookstore-ui/       # Storefront Angular
├── angular-admin/      # Admin Angular
├── VinaBookStore/      # Spring Boot API
├── docs/               # Tài liệu hiện hành
├── db/                 # Seed đã làm sạch và migration theo phiên bản
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

## Vận hành hiện tại

Chatbot được tải khi mở. Luồng structured là planner AI duy nhất trong production; fixture planner cũ chỉ phục vụ kiểm tra tương thích trong src/test. Phần vòng đời hội thoại nằm ở ChatService, câu trả lời local/guard ở ChatLocalDialogue. Xem [vận hành, migration và giới hạn triển khai](operations.md).
