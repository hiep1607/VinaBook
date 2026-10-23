# Trang tài khoản Book

Trang `/profile` sử dụng bố cục tài khoản thương mại điện tử: thông tin người dùng gọn trong menu trái, nhóm Tài khoản của tôi, Hồ sơ, Địa chỉ, Đổi mật khẩu; các mục Đơn mua, Thông báo, Sách yêu thích và Thống kê dẫn đến chức năng sẵn có.

Hồ sơ gồm form tên, email chỉ đọc, điện thoại và ngày tham gia (nếu có), với phần chọn ảnh bên phải trên desktop. Mobile chuyển ảnh lên trên form, menu thành lưới dễ bấm. Loại bỏ phần giới thiệu lớn và các khối bo góc quá rộng của trang cũ; sử dụng màu, font và theme hiện tại của Book.

Không thêm trường ngày sinh, tên đăng nhập khác hoặc liên kết ngân hàng vì backend Book chưa có contract tương ứng. Email vẫn không sửa được; ảnh vẫn dùng đồng bộ hồ sơ hiện có, giới hạn PNG/JPEG/GIF/WebP 256 KB. Chọn ảnh chỉ xem trước; người dùng bấm Lưu ảnh để đồng bộ, có thể huỷ xem trước. Ngăn bấm lưu lặp, có trạng thái đang lưu và lỗi; huỷ hoặc chọn ảnh mới không bị FileReader cũ ghi đè. Không thay đổi API, schema, quyền owner hoặc dữ liệu địa chỉ.

REA đã thử trang `https://shopee.vn/user/account/profile` ngày 20/10/2026. Shopee chuyển phiên chưa đăng nhập sang `/verify/traffic/error`; Evidence `ev_3abcdc8fa0d0986d21e3add337df71ac8fbe38d106a105e2d0f65197141ee46f`. Vì không quan sát được giao diện sau đăng nhập, bố cục mới là triển khai tham chiếu theo yêu cầu, không khẳng định sao chép chính xác toàn bộ trang hay logic Shopee.
