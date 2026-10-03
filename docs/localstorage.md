# Dữ liệu trình duyệt

Storefront dùng `localStorage` cho state nhẹ hoặc dữ liệu chưa chuyển hoàn toàn lên backend. Không xem dữ liệu này là nguồn tin cậy cho authorization, giá hoặc tồn kho.

| Key | Shape | Scope |
|---|---|---|
| `currentUser` | `{ userId, name, email, phone, token, roles, avatar? }` | Phiên hiện tại |
| `avatar_{email}` | Chuỗi base64 | Theo tài khoản |
| `addresses_{email}` | `Array<{ id, label, name, phone, street, isDefault }>` | Theo tài khoản |
| `cart_{email}` | Mảng product snapshot có `quantity`, `selected` | Theo tài khoản |
| `cart_guest` | Cùng shape với cart user | Thiết bị guest |
| `cart_migration_owner` | Email đã nhận giỏ hàng legacy | Migration |
| `wishlist_{email}` | `number[]` product IDs | Theo tài khoản |
| `search_history` | `string[]`, tối đa 8 | Theo thiết bị |
| `theme` | `editorial`, `ocean`, `forest` hoặc `berry` | Theo thiết bị |
| `vinabook_chat_id_v3:{guest\|user:email}` | ID phiên ngẫu nhiên | Theo cuộc chat và tài khoản |
| `vinabook_chat_session_v2:{guest\|user:email}` | Cache tối đa 24 tin mới nhất | Theo cuộc chat và tài khoản |

`currentUser` không được chứa password plaintext, bcrypt hash hoặc reset token. JWT hết hạn/sai format bị `AuthService` xóa khi kiểm tra phiên.

## Key đã nghỉ

- `users`: auth local cũ; không còn đọc/ghi.
- `user`: session prototype cũ; không còn dùng.
- `cart`: giỏ hàng dùng chung cũ; chỉ đọc một lần để migration.
- `orders`: order local cũ; đã chuyển MySQL.
- `reviews_{productId}`: review local cũ; đã chuyển `/api/rates`.

## Rủi ro và nguyên tắc migration

- Cart/wishlist lưu product ID hoặc snapshot; ID từ catalog tĩnh cũ có thể không tồn tại trong MySQL.
- Avatar base64 làm tăng dung lượng storage và không đồng bộ thiết bị.
- Cart, address, wishlist và history có thể bị người dùng sửa bằng DevTools; backend phải tính lại dữ liệu nhạy cảm.
- Khi đổi shape, parser phải chịu được JSON hỏng và dữ liệu phiên bản cũ.
- Logout chỉ xóa phiên; dữ liệu theo email được giữ để user đăng nhập lại.

## Chatbot

ID phiên và cache giao diện nay nằm trong `localStorage` để mở lại tab vẫn chọn đúng cuộc chat. Backend lưu lịch sử đầy đủ và trạng thái hội thoại; cache trình duyệt chỉ giúp hiển thị tức thì trong lúc tải. Nút “Cuộc trò chuyện mới” xóa phiên trên backend rồi mới bỏ ID/cache cục bộ. Bộ nhớ sở thích tài khoản nằm trong database, không bị xóa bởi nút này. Không lưu API key hoặc secret trong browser storage.
