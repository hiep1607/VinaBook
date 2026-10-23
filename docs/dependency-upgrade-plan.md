# Kế hoạch nâng dependency

Đây là kế hoạch migration, không phải tuyên bố đã nâng major framework. Storefront hiện Angular 12.1, admin Angular 12.0 và backend Spring Boot 2.6/Java17; giữ NgModule và contract dữ liệu hiện có trong đợt sửa lỗi.

1. Chốt baseline build và các luồng auth, cart, checkout, chat, admin; ghi kích thước bundle và môi trường Node/Java đang dùng.
2. Nâng đồng bộ các package Angular trong từng app theo từng major. Chọn Node/TypeScript/RxJS đúng bảng tương thích của major đó; không trộn phiên bản compiler/core/CLI. Dùng `ng update` trong nhánh riêng và xem từng migration. Không dùng `npm audit fix --force`.
3. Loại dependency không dùng và CSS trùng trước khi nâng: `ngb-modal` đã được bỏ; jQuery/Bootstrap scripts và theme còn có người dùng thực tế. Preview 3D tải theo module; GSAP vẫn phục vụ các chuyển động hiện hành.
4. Backend nâng theo lộ trình chuyển Spring Security và `javax` → `jakarta`; đối chiếu JWT, WebSocket, JPA locking và query projections. Chạy migration schema có phiên bản và dùng profile production `validate`.
5. Nâng một ứng dụng mỗi lần, đo bundle và chạy kiểm tra đúng phần bị migration tác động. Trước release thay major mới chạy hồi quy rộng và kiểm tra dữ liệu triển khai trên bản sao.

Các cổng nghiệm thu bắt buộc: JWT tài khoản khóa; reset dùng một lần; ticket socket không replay; quote/confirm không trùng đơn; guard nguồn câu trả lời; giữ lựa chọn mua qua reload/đăng nhập; API production không trỏ localhost của khách.
