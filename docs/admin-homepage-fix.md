# Sửa tải Quản trị trang chủ — 08/10/2026

API `/api/admin/products` thiếu CORS nên preflight từ `http://localhost:4300` trả 403 (Invalid CORS request), khiến forkJoin của trang chủ admin thất bại dù cấu hình trang chủ và thể loại trả 200. Thêm đúng hai origin admin local (`localhost:4300`, `127.0.0.1:4300`); quy tắc JWT/ROLE_ADMIN giữ nguyên. Production dùng API cùng origin theo environment hiện có.

Trang admin xóa lỗi trước mỗi lần tải lại, chỉ báo đã đồng bộ sau thành công, nhận diện API bị lỗi, phân biệt phiên không hợp lệ và thiếu quyền, chặn lưu form mặc định khi chưa tải được, giới hạn chờ 15 giây và dừng request khi rời trang.

5 kịch bản kiểm tra:
1. Admin đã đăng nhập mở `/admin/homepage`: cấu hình và các sách/thể loại được tải, không có lỗi CORS; mở `/admin/product` cũng tải được danh sách.
2. Ngắt backend, mở trang rồi bật lại và bấm Thử lại: form hiện ra, thông báo lỗi cũ biến mất, chưa tải thành công thì không báo Đã đồng bộ.
3. Phiên hết hạn/bị từ chối: hiển thị Đăng nhập lại; không cho lưu nội dung mặc định đè lên cấu hình thật.
4. Dùng tài khoản không có quyền admin hoặc origin khác: API từ chối; trang giải thích quyền, không mở dữ liệu bằng cách bỏ xác thực.
5. Request không trả lời hoặc bấm Thử lại liên tiếp: hết chờ sau 15 giây, không tạo các lượt tải song song; rời trang hủy request.

Kiểm tra đạt: 5 test backend CORS + 5 test giao diện tải/thử lại/phiên/quyền/timeout; Angular kiểm tra kiểu dữ liệu và template đạt. Backend đã khởi động lại với sửa đổi: preflight danh sách/chi tiết sách từ admin trả 200 cùng header cho phép Authorization, origin lạ trả 403, API admin không có JWT vẫn trả 401. Cấu hình trang chủ và thể loại trả 200; admin dev server biên dịch thành công. Chưa kiểm tra trực tiếp phiên admin của người dùng trong Chrome vì browser connector chỉ truy cập được trình duyệt in-app.
