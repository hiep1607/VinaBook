# VinaBook — Scripts cơ sở dữ liệu

Thư mục `db/` gom toàn bộ script SQL của dự án, đánh số theo thứ tự thực thi. Đây là dữ liệu seed/migration **có chủ đích**, không phải build artifact.

## Danh sách file

| File | Mục đích | Khi nào chạy |
|---|---|---|
| `01-schema-seed.sql` | Dump seed đầy đủ schema + dữ liệu mẫu của database `book` (sản phẩm ID 3..55) | Import **một lần** trên MySQL dev sạch |
| `02-product-catalog-refresh.sql` | Thay các sản phẩm placeholder bằng catalog nhà sách Việt Nam thực tế (chỉ `UPDATE`, giữ nguyên khóa ngoại) | Sau bước 01, nếu muốn dùng catalog mới |
| `03-category-data-fix.sql` | Sửa category #8 (thừa khoảng trắng đầu tên) và gộp category #9 trùng lặp vào #3 | Sau khi đã có dữ liệu products |
| `04-product57-category-fix.sql` | Sửa thể loại sai cho sản phẩm «Đắc Nhân Tâm» (idempotent) | **Chờ chủ dự án phê duyệt**, chạy thủ công |
| `06-review-comment-length.sql` | Mở rộng `rates.comment` từ 255 lên 2.000 ký tự, giữ dữ liệu; chạy lại an toàn | Sau khi có bảng `rates`, trước khi dùng chức năng đánh giá nhận 2.000 ký tự |

## Thứ tự chuẩn trên máy dev sạch

Dump đã tạo/dùng đúng schema `book` mà backend mặc định kết nối (`jdbc:mysql://localhost:3306/book`).

PowerShell **không hỗ trợ toán tử redirect `<`**, nên chạy mysql qua `cmd /c`:

```powershell
cmd /c "mysql -u <user> -p --default-character-set=utf8mb4 book < D:\book\book\db\01-schema-seed.sql"
cmd /c "mysql -u <user> -p --default-character-set=utf8mb4 book < D:\book\book\db\02-product-catalog-refresh.sql"
cmd /c "mysql -u <user> -p --default-character-set=utf8mb4 book < D:\book\book\db\03-category-data-fix.sql"
```

`04-product57-category-fix.sql` chỉ chạy sau khi được chủ dự án phê duyệt (xem blocker trong `docs/bugs.md`).

`06-review-comment-length.sql` cần được chạy với database ứng dụng đã chọn. Ngày 07/10/2026 đã áp dụng trên MySQL local `book` cổng 3306, xác minh `VARCHAR(2000)` và giữ nguyên 3 đánh giá hiện có. Chưa áp dụng trên production.

## Lưu ý quan trọng

- **Chỉ chạy trên MySQL dev.** Không chạy bất kỳ script nào trong thư mục này trên MySQL đang phục vụ người dùng thật, và không tự ý sửa dữ liệu database thật.
- **Tài khoản trong seed là tài khoản demo** dành cho môi trường local (domain `.example` là domain dành riêng cho dữ liệu ví dụ theo RFC 2606):
  - Quản trị: `admin@vinabook.example`
  - Người dùng mẫu: `user1@vinabook.example` … `user4@vinabook.example`
  - Mật khẩu của tất cả tài khoản demo: `ChangeMe123!`
  - Không bao giờ dùng các tài khoản này cho môi trường public.
- Dữ liệu cá nhân trong seed (email, họ tên, số điện thoại, địa chỉ chi tiết, JWT token cũ, avatar gắn với tài khoản cũ) đã được **thay bằng giá trị giả**; chỉ giữ lại cấu trúc và ID để khóa ngoại hoạt động đúng.

## Bổ sung hồ sơ chatbot đã đối chiếu

Công cụ `apply-reviewed-book-profiles.py` nhập manifest đã biên tập vào các hồ sơ sách hiện có. Mặc định dry-run, so khớp đúng tên/tác giả, sao lưu catalog/hồ sơ/chủ đề và ghi trong một transaction. Manifest có `catalog` chỉ được sửa các trường có nguồn đối chiếu, khớp thêm ID; giá và tồn kho không được ghi. Cần MySQL 8.0.16+ để transaction guard hoạt động. Xem [hướng dẫn và độ phủ dữ liệu](../docs/book-knowledge/README.md). Hai đợt 33 + 23 cuốn ngày 2026-10-05 đã được chủ dự án đồng ý và áp dụng trên database local `book` cổng 3306; đây không phải seed cho database sạch.

`test_reviewed_book_profiles.py` kiểm tra các điều kiện nhập. Tùy chọn `--integration` dùng trước khi nhập manifest 23 cuốn để chứng minh kế hoạch có dữ liệu cũ bị từ chối và mọi thay đổi catalog/hồ sơ/chủ đề trong transaction bị rollback. Sau nhập, chạy lại hai manifest ở chế độ dry-run để kiểm tra không có thay đổi.

## Chức năng khách hàng 08/10/2026
- `07-support-chat.sql`: tin nhắn khách/nhân viên, chống gửi trùng.
- `08-customer-preferences.sql`: địa chỉ, avatar, phiên bản dữ liệu và dấu migration local.
- `09-payment-refunds.sql`: lịch sử hoàn tiền và thời điểm giao dịch VNPay gốc.
- `10-vouchers.sql`: mã giảm giá, lượt sử dụng và discount/ship lưu trên đơn.
- `11-restock-watches.sql`: theo dõi và thông báo có hàng theo tài khoản.
Chạy theo thứ tự sau khi có bảng users/orders/products/support_requests. Các câu ALTER ở 09/10 chỉ chạy một lần; kiểm tra schema trước nếu local đã dùng Hibernate ddl-auto=update. Không chạy lại dump seed trên database đang có đơn hàng.

## Gia cố ngày 17/10/2026

- `12-password-reset-tokens.sql`: reset token được hash, hết hạn và dùng một lần.
- `13-order-shipping-address.sql`: địa chỉ đơn chứa 255 ký tự đường/phường/tỉnh và tên người nhận.
- `14-checkout-drafts.sql`: nháp đặt hàng riêng theo tài khoản, giữ qua restart và chống xác nhận lặp.

Áp dụng các migration còn thiếu trước khi dùng profile `production`; profile này chỉ kiểm tra schema. Local dùng `dev` có thể đã được Hibernate cập nhật, cần kiểm tra trước khi chạy ALTER. Xem [vận hành](../docs/operations.md).

### 15 — Outbox email

`15-mail-outbox.sql` tạo hàng đợi email bền vững có payload mã hóa, claim và retry. Chạy sau migration 14 trước khi dùng profile production. Cấu hình khóa và giới hạn giao thư tại [operations](../docs/operations.md).
