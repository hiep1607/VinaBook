# Paperbound reader

## Entry point và phạm vi

Tại Product Detail, nút Đọc thử hoặc double-click mô hình sách mở `BookPreviewReaderComponent` với sản phẩm hiện tại. Mô hình sản phẩm được tạm dừng; engine đọc được import động sau khi tải manifest. Bản mở rộng ngày 2026-09-08 bổ sung nhập văn bản trong admin và lưu qua API hiện có. Không thay đổi giỏ hàng, thanh toán hoặc framework; không thêm dependency JavaScript.

Nguồn tham khảo triển khai là ba file `app.js`, `book-math.js`, `pages.js` trong gói `paperbound-integration.zip` người dùng cung cấp. Chuyển thuật toán tích phân dải giấy giữ chiều dài, UV hai mặt và ngưỡng kéo/vận tốc sang TypeScript; không nhúng ứng dụng demo hoặc global DOM của bản gốc vào storefront.

## Phân chia trách nhiệm

- `bookstore-ui/src/app/components/book-preview-reader/book-preview-reader.component.*`: native dialog, focus/Escape, tải API, lifecycle và chế độ 2D.
- `reader-model.ts`: contract text/image và adapter manifest hiện có.
- `paperbound-math.ts`, `paperbound-leaf.ts`: hình học trang cong, hai mặt và cạnh giấy.
- `reader-controller.ts`: state machine; chỉ một animation tại một thời điểm.
- `reader-scene.ts`: scene, cover/spine, stack, camera, pointer và tài nguyên WebGL.
- `reader-textures.ts`: cửa sổ tối đa tám texture trang, tối đa ba ảnh bìa riêng, hủy tải và dispose kết quả muộn.

Đóng sách gom các trang bên trái thành một khối dày, đưa về phải rồi mới đóng bìa. Không khởi động đồng thời animation riêng cho tất cả trang; thời gian đóng không tăng theo số trang đã lật. Chỉ giữ ba mesh trang chi tiết và các khối stack. Render chạy theo thay đổi/animation, không có vòng vẽ liên tục khi reader đứng yên. Ảnh có timeout 12 giây và được rasterize khi tải/đổi mức phân giải, không tạo canvas texture mỗi frame.

## Dữ liệu thật

Dùng `GET /api/products/{id}/preview` hiện có. Adapter kiểm tra productId và enabled, sắp xếp pageOrder, giữ originalPageNumber. Tổng trang thật của sách độc lập với số trang được đọc thử; không thêm trang giả để làm chẵn chỉ báo. Backend vẫn quyết định quyền đọc thử. Client không phải cơ chế DRM.

Production nhận cả trang ảnh và văn bản được API trả về; không tự điền đoạn văn minh họa. `contentType` thiếu/null được hiểu là `image`, tương thích các trang cũ. Các metadata tùy chọn `pageCount`, `coverType`, `width`, `height`, `spineImage`, `backImage` được giữ lại nếu catalog có; khi thiếu dùng hình học trung tính, không suy đoán tổng số trang từ số ảnh đọc thử.

## Kiểm thử độc lập

Từ thư mục `bookstore-ui`, PowerShell:

```powershell
$env:NODE_OPTIONS='--openssl-legacy-provider'
npx ng test --watch=false --browsers=ChromeHeadless
npx ng build --configuration production
npx ng build --configuration development --main qa/main.ts --ts-config qa/tsconfig.json --output-path tmp/reader-qa
node qa/serve.cjs
```

Mở `http://127.0.0.1:4217/`. Server chỉ bind loopback. Entry `qa/main.ts` không nằm trong production routes; nội dung đều ghi rõ “Nội dung minh họa — không phải nội dung sách”. Không cần khởi động hoặc ghi database.

Kết quả ngày 2026-09-07: 65/65 unit test đạt; production build đạt. Build còn cảnh báo budget initial 843.27 kB so với 600 kB và product-detail SCSS 13.98 kB so với 12 kB (không vượt ngưỡng lỗi).

Đã thao tác trình duyệt desktop 1366×768 và mobile viewport 390×844: mở sách, chữ mặt sau không soi gương, lật/kéo ngắn trả về/kéo dài sang trang, 0/1/5/6 trang, giới hạn trang cuối, nhấn phím liên tiếp không chồng lượt, đóng nhiều trang rồi mở lại, chuyển 2D/3D, thoát giữa animation, trả focus và phục hồi scroll. Đã kiểm tra đóng khi tải chậm, thay sản phẩm trong lúc tải, và thông báo/thử lại cho trang ảnh lỗi. Unit test bổ sung kiểm tra chiều dài dải giấy, UV, điều kiện release/cancel, các số trang lẻ/chẵn tới 101, giới hạn texture và dispose kết quả tải muộn.

Kiểm tra bổ sung khi người dùng yêu cầu chạy web: storefront tại `http://127.0.0.1:4200`, API tại cổng 8080. API được khởi động với `--spring.jpa.hibernate.ddl-auto=validate --spring.sql.init.mode=never --server.address=127.0.0.1`, không tự cập nhật schema. Danh mục hiển thị 56 cuốn và dialog Truyện Kiều mở từ API thật với chỉ báo 1/5. Sau năm lượt mở/đóng ở trang QA, DOM không còn dialog và chỉ còn một canvas của mô hình nền.

Giới hạn xác minh: viewport mobile không thay thế kiểm thử pinch trên thiết bị cảm ứng thật; chưa profile GPU heap dài hạn hoặc kiểm thử toàn bộ luồng mua hàng với backend thật. Không commit, push hoặc deploy trong lần tích hợp này.
## Nhập nội dung từ admin (2026-09-08)

1. Vào admin cổng 4300, chọn sách rồi bấm Chỉnh sửa sách.
2. Trong Bản đọc thử, chọn Thêm trang văn bản hoặc Nhập chữ tại trang ảnh muốn thay.
3. Nhập tiêu đề (tối đa 120 ký tự), nội dung (tối đa 1000 ký tự và 24 dòng), số trang gốc nếu có.
4. Chọn Noto Serif/Arial, cỡ chữ 18/20/22 và căn trái/giữa; mở Xem trước nội dung để kiểm tra.
5. Lưu trang ghi riêng trang đó ngay lập tức. Hủy soạn không thay nội dung đã lưu. Khi lỗi mạng, bản đang soạn vẫn được giữ để thử lại.
6. Có từ 5 đến tối đa 20 trang, xác nhận quyền công khai và bật Cho phép khách đọc thử, sau đó Lưu thay đổi. Số trang hiển thị thực tế đúng bằng số trang admin đã lưu và bật; không tự chèn trang đệm.

Nội dung là plain text, không chạy HTML/script. Form và backend đều giới hạn độ dài; các giá trị kiểu chữ/căn lề được allowlist. Ảnh cũ không tự chuyển thành văn bản và không thể tự phục hồi chi tiết ảnh đã mất. Để thay ảnh chữ nhỏ, dùng Nhập chữ rồi tự nhập trích đoạn được phép công khai.

## Schema và triển khai

Chạy một lần `VinaBookStore/src/main/resources/db/product-preview-text.sql` sau migration preview ban đầu, trước khi khởi động phiên bản backend mới. Sáu cột nullable được thêm: content_type, text_content, heading, font_family, font_size, text_align. Không xóa/ghi đè cột ảnh hoặc trang cũ. Migration này đã được áp dụng vào database local `book` trong lượt triển khai; giữ nguyên 15 trang ảnh đang có. Không chạy lại file trên database đã có các cột.

API admin giữ nguyên URL POST/PUT trang, bổ sung các trường tương ứng. Public API không trả nội dung khi sách chưa bật đọc thử, thiếu quyền hoặc không đủ số trang. Quyền ADMIN của các endpoint quản trị không thay đổi.

## Giao diện đọc mới

Dialog toàn màn hình, header thu gọn, sách tự fit sát chiều cao/chiều rộng khả dụng. Góc mặc định gần thẳng từ trên. Chỉ giữ điều hướng hai bên và nút Aa mở tùy chọn; không còn hàng nút dưới sách. Chế độ chữ lớn dành cho đọc và cuộn trên màn hình hẹp. Focus, Escape, phím trái/phải, âm thanh mặc định tắt và reduced-motion được giữ.

Texture trang in dùng material không chịu tone mapping để nét chữ không bị cháy sáng; mesh vẫn cong và có hai mặt. Texture có hai mức 1024/1536, cache vẫn tối đa tám trang. Font Noto Serif được tự lưu tại assets/fonts ở cả hai ứng dụng, kèm giấy phép SIL OFL. Nguồn: https://fonts.google.com/noto/specimen/Noto+Serif và https://github.com/google/fonts/tree/main/ofl/notoserif. Chờ font tải trước khi rasterize chữ vào texture. Nội dung quá dài được fit trong trang; nên chia trang ở admin để giữ cỡ chữ mong muốn.

## Kiểm thử bản mở rộng

- Storefront: 66 test đạt, gồm contract text/image, Unicode giữ nguyên, hình học lật, 20 trang đọc thử, giới hạn texture và hủy tải.
- Admin: 6 test mục edit-product đạt; bao gồm giữ draft khi lỗi, lưu đúng productId, giới hạn đến trang 20, thay ảnh bằng văn bản và textarea có label trong modal thật. Chưa chạy toàn bộ suite admin.
- Backend: 94 test đạt, gồm text validation, tương thích request ảnh cũ, giới hạn đến trang 20, không lộ nội dung chưa duyệt và không sửa trang của sản phẩm khác.
- Production build storefront/admin đạt, còn cảnh báo budget đã có (storefront initial khoảng 846 kB, admin khoảng 1.61 MB) và selector Bootstrap cũ.
- Đã kiểm tra trực quan bản chữ minh họa trên desktop 1366x768 và chế độ chữ lớn mobile 390x844: dấu tiếng Việt liền nét, trang trái/phải đúng chiều, không còn footer chiếm diện tích.
- Test form dùng service mock; chưa thực hiện lưu nội dung thật qua phiên admin trên trình duyệt. Không tự điền trích đoạn vào các cuốn đang bán và không tự xác nhận quyền tác giả thay admin.
