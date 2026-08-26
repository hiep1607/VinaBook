# VinaBook — Scripts cơ sở dữ liệu

Thư mục `db/` gom toàn bộ script SQL của dự án, đánh số theo thứ tự thực thi. Đây là dữ liệu seed/migration **có chủ đích**, không phải build artifact.

## Danh sách file

| File | Mục đích | Khi nào chạy |
|---|---|---|
| `01-schema-seed.sql` | Dump seed đầy đủ schema + dữ liệu mẫu của database `book` (sản phẩm ID 3..55) | Import **một lần** trên MySQL dev sạch |
| `02-product-catalog-refresh.sql` | Thay các sản phẩm placeholder bằng catalog nhà sách Việt Nam thực tế (chỉ `UPDATE`, giữ nguyên khóa ngoại) | Sau bước 01, nếu muốn dùng catalog mới |
| `03-category-data-fix.sql` | Sửa category #8 (thừa khoảng trắng đầu tên) và gộp category #9 trùng lặp vào #3 | Sau khi đã có dữ liệu products |
| `04-product57-category-fix.sql` | Sửa thể loại sai cho sản phẩm «Đắc Nhân Tâm» (idempotent) | **Chờ chủ dự án phê duyệt**, chạy thủ công |

## Thứ tự chuẩn trên máy dev sạch

Dump đã tạo/dùng đúng schema `book` mà backend mặc định kết nối (`jdbc:mysql://localhost:3306/book`).

PowerShell **không hỗ trợ toán tử redirect `<`**, nên chạy mysql qua `cmd /c`:

```powershell
cmd /c "mysql -u <user> -p --default-character-set=utf8mb4 book < D:\book\book\db\01-schema-seed.sql"
cmd /c "mysql -u <user> -p --default-character-set=utf8mb4 book < D:\book\book\db\02-product-catalog-refresh.sql"
cmd /c "mysql -u <user> -p --default-character-set=utf8mb4 book < D:\book\book\db\03-category-data-fix.sql"
```

`04-product57-category-fix.sql` chỉ chạy sau khi được chủ dự án phê duyệt (xem blocker trong `docs/bugs.md`).

## Lưu ý quan trọng

- **Chỉ chạy trên MySQL dev.** Không chạy bất kỳ script nào trong thư mục này trên MySQL đang phục vụ người dùng thật, và không tự ý sửa dữ liệu database thật.
- **Tài khoản trong seed là tài khoản demo** dành cho môi trường local (domain `.example` là domain dành riêng cho dữ liệu ví dụ theo RFC 2606):
  - Quản trị: `admin@vinabook.example`
  - Người dùng mẫu: `user1@vinabook.example` … `user4@vinabook.example`
  - Mật khẩu của tất cả tài khoản demo: `ChangeMe123!`
  - Không bao giờ dùng các tài khoản này cho môi trường public.
- Dữ liệu cá nhân trong seed (email, họ tên, số điện thoại, địa chỉ chi tiết, JWT token cũ, avatar gắn với tài khoản cũ) đã được **thay bằng giá trị giả**; chỉ giữ lại cấu trúc và ID để khóa ngoại hoạt động đúng.