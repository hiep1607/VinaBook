# Địa chỉ giao hàng và vị trí hiện tại

Áp dụng cho sổ địa chỉ trong Tài khoản, biểu mẫu Thanh toán và biểu mẫu đặt hàng trong chat. Địa chỉ đã lưu vẫn được chọn như trước.

## Cách dùng

- Nhãn địa chỉ dùng combobox **Nhà riêng / Văn phòng / Khác** trong hồ sơ và checkout; nhãn cũ do người dùng đặt vẫn được giữ khi chỉnh sửa.
- Chọn tỉnh/thành phố, phường/xã, điền số nhà và tên đường; hoặc chuyển sang **Nhập địa chỉ đầy đủ**. Danh mục sử dụng địa giới sau sáp nhập 2025, hai cấp tỉnh và xã. Không ép chuyển đổi địa chỉ cũ.
- **Dùng vị trí hiện tại** chỉ gọi định vị sau cú bấm của người dùng. Trình duyệt yêu cầu quyền vị trí và cần HTTPS; localhost hỗ trợ khi phát triển.
- Tọa độ được gửi tới Photon để lấy địa chỉ gần đó. Gợi ý không thay đổi biểu mẫu cho đến khi người dùng bấm **Dùng địa chỉ này**. Người dùng phải bổ sung/kiểm tra số nhà, tầng, căn hộ; kết quả gần nhất không bảo đảm đúng toà nhà.
- Từ chối quyền, chờ quá lâu, dịch vụ lỗi hoặc không có địa chỉ Việt Nam đều giữ cách nhập thủ công. Có nút huỷ; kết quả đến muộn không ghi đè địa chỉ đã sửa, form đã đóng hoặc control đã khóa.
- Không tự lưu vào sổ địa chỉ, đặt hàng hay xin vị trí ngay khi mở trang. Dữ liệu vẫn qua bước lưu/xác nhận và xác minh chủ tài khoản hiện có.

## Tích hợp và dịch vụ

`DeliveryAddressComponent` là ControlValueAccessor dùng chung với template-driven forms. Giá trị ra vẫn là chuỗi `street` (5–255 ký tự), nên không đổi schema backend, migration, dữ liệu địa chỉ cũ hoặc API đơn hàng. Tên người nhận, điện thoại và nhãn vẫn do form cha quản lý.

`DeliveryLocationService` dùng HttpClient tạo từ HttpBackend, không đi qua interceptor JWT/ngrok của ứng dụng. Không gửi tên, điện thoại, token, cookie hay địa chỉ gõ tay tới dịch vụ ngoài. Tọa độ chỉ dùng cho yêu cầu định vị, không ghi localStorage, log hoặc payload địa chỉ/đơn hàng. Danh mục tỉnh/phường được cache trong RAM, tải phường theo tỉnh, gộp request đồng thời, có timeout và thử lại khi lỗi.

- Danh mục: [Province Open API v2](https://provinces.open-api.vn/), `/api/v2/p/` và `/api/v2/p/{code}?depth=2`.
- Đổi tọa độ: [Photon](https://github.com/komoot/photon), `/reverse`. Máy chủ demo công khai phù hợp lưu lượng nhỏ, không cam kết khả dụng; sản phẩm có nhiều người dùng cần endpoint Photon riêng hoặc nhà cung cấp có SLA. Có thể thay hai endpoint bằng provider cho injection token `DELIVERY_LOCATION_API` mà không sửa component.
- Attribution OpenStreetMap/Photon hiển thị cùng gợi ý. Không dùng public Nominatim, không tải bản đồ hoặc dò địa chỉ nền.

Độ chính xác hiển thị là độ chính xác thiết bị trả về; không phải sai số địa chỉ của geocoder. Gợi ý có thể còn tên địa giới cũ từ OpenStreetMap và được sửa tự do trước khi lưu. Không tự đoán số nhà từ POI lân cận.

Tra cứu vị trí yêu cầu `lang=default` để ưu tiên tên địa phương, không lấy tên dịch theo ngôn ngữ trình duyệt. Tên tỉnh/thành còn được đối chiếu với danh mục đã tải để chuẩn hoá các biến thể như **Hanoï / Hanoi → Hà Nội**, gộp phần khu vực trùng nhau; không sửa tuỳ tiện tên đường hoặc tên toà nhà.

## Kiểm chứng

REA đã thử trang địa chỉ Shopee ngày 20/10/2026 nhưng bị chuyển sang `/verify/traffic/error`. Vì vậy chức năng được xây theo yêu cầu trải nghiệm, không khẳng định đã khôi phục hoặc sao chép logic nội bộ Shopee. Evidence: `ev_bd50733d2e271d147489992cbe8dd6b6a0bdace70eb540092db2279d72ab95ea`.

Kiểm thử dùng tọa độ giả và HTTP mock, không thu thập vị trí thật. Các ca bao gồm ghép địa chỉ, đổi tỉnh/reset phường, response cũ, giữ địa chỉ legacy, xác nhận gợi ý, từ chối quyền, timeout, huỷ/khóa form và không gửi JWT tới dịch vụ ngoài.
