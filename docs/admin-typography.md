# Font chữ admin — 01/11/2026

Admin dùng Be Vietnam Pro, dự phòng Segoe UI/sans-serif. Font chung nằm ở `--admin-font-family` trong `src/styles.css`.

Angular Material trước đây gọi `mat.core()` với typography mặc định Roboto. Chỉ đặt font cho body và `.mat-typography` chưa đổi được font ở h1/h2/h3, nút, tab, bộ phân trang hoặc các thành phần có quy tắc font riêng. Cấu hình typography vào `mat.core` để mọi thành phần dùng cùng font tiếng Việt, giữ nguyên thang cỡ chữ của Material và các cỡ chữ tùy chỉnh trong từng trang.

Form đăng nhập bỏ font `Work+Sans` không được nạp và nhận font chung. Tooltip/popover của template Bootstrap cũng dùng font chung. Các biểu đồ Chart.js lấy font từ body khi vào admin vì chữ vẽ trên canvas không kế thừa CSS. Giữ nguyên font icon Font Awesome/Material Icons và phần nội dung đọc thử sách có lựa chọn serif/sans riêng.

Khi rà lại: kiểm tra Trang chủ dashboard và Quản trị trang chủ, bảng sản phẩm/đơn/khách, form, tab, phân trang, tooltip và nhãn biểu đồ. Không lưu thay đổi nội dung hoặc thực hiện thao tác quản trị chỉ để kiểm tra font.
