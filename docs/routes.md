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
| `/search?q=` | Search | Không | Query canonical là `q`; link cũ dùng `keyword` vẫn hoạt động và được tự đổi sang `q` bằng `replaceUrl`. Pagination 12 sản phẩm/trang, đồng bộ `page` trên URL; sort (`sort=price-asc|price-desc`) và filter (`category`, `promo=1`, `stock=1`, `minPrice`, `maxPrice`) cũng nằm trên URL nên F5/chia sẻ link giữ nguyên trạng thái; đổi query/category/sort/filter luôn về trang 1 |
| `/forgot-password` | Forgot password | Không | Gửi yêu cầu đặt lại mật khẩu qua `POST /api/auth/send-mail-forgot-password-token`; thông báo trung tính không tiết lộ email có tồn tại; việc đặt mật khẩu mới chạy trên trang backend `/forgot-password/{token}` từ link trong email |
| `/cart` | Cart | Không | Có scope guest và user |
| `/checkout` | Checkout | Có | `AuthGuard`, COD/QR/VNPay/MoMo |
| `/payment/result?orderId=` | Payment result | Có | QR, polling trạng thái và retry gateway |
| `/wishlist` | Wishlist | Có | `AuthGuard` |
| `/orders` | Orders | Có | `AuthGuard` |
| `/orders/:id` | Order detail | Có | `AuthGuard`, timeline trạng thái, thanh toán lại |
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
