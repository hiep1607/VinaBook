# Services storefront

Mọi service đều `providedIn: 'root'`.

## API services

| Service | Trách nhiệm |
|---|---|
| `AuthService` | Signup/signin, phiên JWT, cập nhật profile, đổi mật khẩu, avatar local |
| `ProductService` | Tải/map/cache catalog, chi tiết và lọc category |
| `HomepageService` | Tải/cache cấu hình CMS trang chủ; tự dùng cấu hình mặc định nếu request thất bại |
| `CategoryService` | Cache danh sách category và tải một category |
| `OrderService` | Sync cart được chọn, tạo đơn, tải đơn và order detail |
| `PaymentService` | Tải phương thức khả dụng, tạo phiên thanh toán và đọc trạng thái đơn |
| `ChatbotService` | Gọi `/api/chat`, chuẩn hóa thẻ sách và tự dùng FAQ nội bộ khi request lỗi |
| `ReviewService` | Tải/cache rate, upsert review, phát `changes$` |

Base URL luôn ghép từ `environment.apiUrl`. Production để rỗng để gọi cùng origin.

## State trình duyệt

| Service | Persistence |
|---|---|
| `CartService` | `cart_{email}` hoặc `cart_guest`; buy-now in-memory |
| `AddressService` | `addresses_{email}` |
| `WishlistService` | `wishlist_{email}` chứa product IDs |
| `SearchHistoryService` | `search_history`, tối đa 8 mục |
| `SearchService` | Không persist; lọc trên catalog cache |
| `NotificationService` | In-memory toast stream và timeout |
| `ChatbotComponent` | Lưu tối đa 24 tin nhắn trong `sessionStorage` của tab hiện tại |
| `ThemeService` | Áp dụng/lưu theme editorial, ocean, forest hoặc berry |

## Cross-cutting

- `AuthInterceptor`: gắn bearer token cho request API.
- Admin `HomepageService`: đọc `GET /api/homepage`, lưu `PUT /api/homepage` bằng JWT admin.
- `NgrokSkipInterceptor`: header hỗ trợ môi trường tunnel dev.
- `AuthGuard`: xóa phiên hỏng/hết hạn và redirect login với `returnUrl`.

Catalog tĩnh và các service prototype trùng nghiệp vụ không còn trong source.
