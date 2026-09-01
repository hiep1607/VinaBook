# Routes storefront

Routes khai báo trong `bookstore-ui/src/app/app-routing.module.ts`. Tất cả page hiện eager-loaded; mỗi route có `pageTitle` và router tự đưa trang về đầu khi điều hướng.

| Path | Page | Đăng nhập | Ghi chú |
|---|---|---:|---|
| `/` | Home | Không | Catalog mới/bán chạy/category và nội dung từ homepage CMS |
| `/books` | Books (`all`) | Không | Tìm kiếm, sort, pagination |
| `/books/new` | Books (`new`) | Không | Sách mới nhất |
| `/books/bestseller` | Books (`bestseller`) | Không | Xếp theo `sold` |
| `/books/sale` | Books (`sale`) | Không | Chỉ sách có giảm giá |
| `/category/:id` | Category | Không | Lọc bằng ID category |
| `/product/:id` | Product detail | Không | Mua/review/wishlist cần đăng nhập |
| `/search?q=` | Search | Không | Query và filter catalog |
| `/cart` | Cart | Không | Có scope guest và user |
| `/checkout` | Checkout | Có | `AuthGuard`, COD/QR/VNPay/MoMo |
| `/payment/result?orderId=` | Payment result | Có | QR, polling trạng thái và retry gateway |
| `/wishlist` | Wishlist | Có | `AuthGuard` |
| `/orders` | Orders | Có | `AuthGuard` |
| `/profile` | Profile | Có | `AuthGuard` |
| `/login` | Login | Không | Hỗ trợ `returnUrl` |
| `/register` | Register | Không | Chuyển sang login khi thành công |
| `/about` | About | Không | Thống kê catalog/category thật |
| `/contact` | Contact | Không | Mở `mailto:` sau validation |
| `**` | Not found | Không | Trang 404 |

Guard chỉ cải thiện UX; backend vẫn phải kiểm tra JWT, role và owner cho mọi dữ liệu bảo vệ.

## API chatbot công khai

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/api/chat/status` | Provider/model đang hoạt động, không chứa secret |
| `POST` | `/api/chat` | Hỏi đáp, grounding catalog và gợi ý sách; rate limit theo IP |
