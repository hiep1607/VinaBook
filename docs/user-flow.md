# Luồng nghiệp vụ chính

## Đăng nhập và route bảo vệ

```mermaid
sequenceDiagram
    actor U as Người dùng
    participant G as AuthGuard
    participant L as Login
    participant A as AuthService
    participant API as Backend
    U->>G: mở /checkout, /profile, /orders hoặc /wishlist
    G->>A: isLoggedIn()
    alt JWT hợp lệ
        G-->>U: cho phép route
    else chưa đăng nhập/hết hạn
        G-->>L: /login?returnUrl=...
        L->>API: POST /api/auth/signin
        API-->>A: profile + JWT, không có password
        A-->>U: quay lại returnUrl
    end
```

## Catalog và tìm kiếm

```mermaid
sequenceDiagram
    participant P as Page/Search
    participant PS as ProductService
    participant API as GET /api/products
    P->>PS: getProducts()
    alt cache chưa có
        PS->>API: tải catalog
        API-->>PS: products + category relation
    end
    PS-->>P: catalog đã map/cache
```

Home, books, search, product detail, category và wishlist đều dựa trên nguồn này.

## Checkout

```mermaid
sequenceDiagram
    actor U as Người dùng
    participant C as Checkout
    participant OS as OrderService
    participant API as Backend
    participant DB as MySQL
    U->>C: chọn địa chỉ + phương thức thanh toán
    C->>C: validate người nhận, điện thoại, địa chỉ
    C->>OS: placeOrder(selectedItems)
    OS->>API: đồng bộ cart/cartDetail
    API->>DB: đọc product, tính lại giá, kiểm tra tồn kho
    OS->>API: POST /api/orders/{email}
    API->>DB: tạo order/detail, tổng + phí ship
    API-->>C: order thành công
    C->>C: xóa dòng đã mua và buy-now
    alt COD
        C-->>U: mở lịch sử đơn hàng
    else VietQR
        C->>API: tạo mã QR theo order
        API-->>C: QR + nội dung chuyển khoản
        C-->>U: chờ admin đối soát
    else VNPay hoặc MoMo
        C->>API: tạo giao dịch đã ký
        API-->>U: chuyển sang cổng thanh toán
        API->>DB: xác thực callback, số tiền và cập nhật paymentStatus
    end
```

Backend không tin giá/owner do client gửi. Mức ship hiện là `20.000đ`, miễn phí từ `300.000đ`.

## Review

User đăng nhập chọn 1–5 sao và nhập bình luận. `ReviewService` POST hoặc PUT `/api/rates`; backend gắn review với user/product canonical và chỉ cho owner sửa. Sau thành công, `changes$` làm mới rating ở product card/detail/profile.
