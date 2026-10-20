# Ô tìm sách và tự điền thông tin đăng nhập

Ô tìm sách là một form riêng với `role="search"`, tên `book-search`, input `type="search"`, tên `bookQuery` và `autocomplete="off"`. Form đăng nhập sử dụng `autocomplete="username"` cho email cùng `current-password` cho mật khẩu, giúp trình duyệt phân biệt đúng nơi lưu/tự điền tài khoản.

Khi rời Đăng nhập hoặc Đăng ký, header xoá nội dung tìm kiếm còn sót, kể cả khi trình duyệt chỉ tự điền DOM mà không cập nhật Angular model. Nếu chuyển đến trang tìm kiếm, ô được khôi phục từ tham số `q` trên URL. Không xoá hoặc ghi đè từ khoá thật khi chuyển giữa các trang thông thường, không đưa email đăng nhập vào lịch sử tìm kiếm.

Form tìm kiếm dùng một luồng submit chung cho nút tìm và phím Enter, tránh gọi tìm kiếm hai lần. Không dùng input giả, timeout quét nội dung hoặc khoá bàn phím để ngăn autofill. Tiện ích quản lý mật khẩu của bên thứ ba vẫn có thể áp đặt hành vi riêng ngoài thuộc tính chuẩn của website.
