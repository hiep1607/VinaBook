# Tiến độ theo bộ yêu cầu chatbot 100 câu — 10/09/2026

Nguồn yêu cầu: bộ 100 câu có cột Dạng phản hồi mong muốn, gồm tìm kiếm, tư vấn, so sánh, ngữ cảnh, giỏ hàng, đơn hàng và checkout.

## Đã triển khai trong đợt này

- `recommendBooks`: chọn theo chủ đề, trình độ và tổng ngân sách; lọc trước khi giới hạn kết quả, backend tính tổng. Không chia đều ngân sách thành trần giá mỗi cuốn. Nếu dữ liệu không chứng minh phù hợp trình độ thì không đề xuất bừa.
- `listCategories`: danh mục thực lấy từ catalog đang hoạt động.
- `getProducts.field`: trả trực tiếp price/rating/inventory hoặc bảng table; không gọi narrator với phản hồi dữ liệu trực tiếp.
- Sửa danh mục khái niệm (ví dụ CNTT) chuyển sang truy vấn chủ đề mà không giữ điều kiện danh mục không tồn tại.
- Giữ limit khi tiếp tục tìm kiếm, xóa trạng thái tên sách không tồn tại sau một tìm kiếm mới.
- Bộ điều phối được hướng dẫn giữ toán tử OR, phân biệt trình độ với từ khóa tìm kiếm, dùng công cụ tư vấn cho ngân sách nhiều sách.
- Hỏi làm rõ nhu cầu bằng chủ đề/mục đích/ngân sách thay vì bắt chọn sách chưa được giới thiệu.
- Narrator nhận các trường sản phẩm thực và câu trả lời backend; chỉ dùng cho chi tiết/tư vấn, không nối diễn giải thừa vào kết quả tìm kiếm thuần túy.
- Tra đơn giữ ID đơn trong phiên theo tài khoản, kiểm tra lại quyền mỗi lần. Chi tiết có ngày tạo và sản phẩm trong đơn.
- Thêm nhận dạng typo pythn/sáhc. Truy vấn truyện được lọc bắt buộc vào dữ liệu truyện/văn học.
- Log lỗi điều phối chỉ ghi tên lớp exception, không ghi prompt, secret hoặc nội dung phản hồi của nhà cung cấp.

## Kiểm chứng

- 182 bài kiểm thử Chat*Test đạt sau tinh chỉnh sách nền tảng và typo. Sau thay đổi giữ limit của recommendBooks, chạy lại riêng ChatToolsTest.
- Sáu câu thử Gemini thật hoàn tất không fallback: danh mục, hỏi nhu cầu, CNTT rẻ nhất, Java OR Python, tư vấn trong tổng 300k, tác giả giá thấp nhất.
- Lượt Gemini phát hiện sách mạng máy tính được chọn cho người mới lập trình; đã bổ sung điều kiện liên quan lập trình trong recommendBooks. Cần kiểm tra lại với model thật sau biên dịch cuối.
- Đã có lượt 100 câu mới `work/astra-chatbot-regression-1789038168700/results.json` và đánh giá riêng. Lượt đó chạy anonymous, không thực thi action nên chưa chứng minh checkout/giỏ hàng hoàn chỉnh. Test đơn vị không thay thế đánh giá câu trả lời thực.

## Sửa sau đánh giá bộ 100 câu (10/09/2026)

- `searchProducts`/`recommendBooks` nhận `title` (khớp tên chính xác), `titleContains` (từ/cụm từ độc lập trong tên, không tìm sang mô tả, giữ dấu tiếng Việt; AI không khớp Blockchain) và `selection=bestsellers` (chọn top theo số bán trước khi sắp giá).
- Tên sách có thật được nhắc trong yêu cầu tìm kiếm giới hạn lại tập kết quả. Tên chính xác vẫn phải thỏa giá/rating. Truy vấn công nghệ bị model bỏ trống được khôi phục từ yêu cầu rõ của khách.
- Lọc công nghệ dựa vào tên/mô tả, không chỉ danh mục gộp. Lọc các chủ đề như kinh doanh không lấy danh mục gộp làm bằng chứng phù hợp.
- `respond.topic` hỗ trợ hỏi đúng thông tin còn thiếu: sản phẩm, hai sách so sánh, người nhận quà, địa chỉ hoặc nhu cầu đọc. Có xử lý trường hợp lượt tìm trước rỗng.
- Giữ `lastTask` trong phiên để điều phối biết đang tra đơn mà không lưu nội dung riêng của đơn. Chặn trường hợp planner dùng `getCart` cho “đơn này/đơn đó/đơn hàng”, chuyển sang công cụ tra đơn có kiểm tra tài khoản. Sửa lỗi unboxing ID đơn null.
- Sửa/xóa một sách trong giỏ chỉ tạo đề xuất khi sách hiện có trong snapshot giỏ; thêm sách kiểm tra cả số lượng đã có cộng số lượng muốn thêm với tồn kho.
- Nhận đúng câu hỏi đổi sách sau khi nhận. Câu hỏi cách hủy đơn trả hướng dẫn liên hệ kèm mã đơn, nói rõ chưa hỗ trợ tự hủy; không thêm nghiệp vụ hủy đơn mới.
- 191 kiểm thử `Chat*Test` đạt; sau chỉnh tìm chữ có dấu, chạy lại 39 kiểm thử liên quan đạt. Các test bao gồm catalog có dữ liệu nhiễu, giỏ có sẵn, model chọn nhầm công cụ, tra đơn chưa có focused ID và so sánh có sản phẩm xác định.

Giới hạn: chưa xử lý mọi cách diễn đạt chủ đề, chưa bổ sung rating strict `>`; vẫn cần cải thiện dữ liệu phân loại và kiểm thử mua hàng trên UI. Bộ 15 câu live sau sửa được lưu riêng, không ghi đè bộ 100 câu cũ.

Live sau sửa: 15 HTTP 200, 14 không fallback; một lỗi trước thực thi tool ở câu tìm AI. Lần thử lại phát hiện khớp chuỗi con Blockchain, đã sửa thành từ độc lập, chạy lại 39 test đạt và live trả đúng sách AI. Đã xác nhận bảng so sánh với ID 58/22 và đề xuất sửa giỏ có snapshot Java số lượng 1 thành 2; chưa thực thi mutation trên UI. Chi tiết và raw lưu tại workspace đánh giá `2026-09-10/ti/outputs/vinabook-regression-fixes-*`.

## Danh sách còn lại trước đợt hoàn thiện 11/09/2026

- Giỏ hàng backend làm nguồn chính, migration an toàn từ giỏ local và đồng bộ các màn hình; hiện giỏ trước checkout vẫn ở trình duyệt.
- Thêm luồng xóa toàn bộ giỏ có xác nhận.
- Hủy đơn trong chat cần nghiệp vụ chung, quyền sở hữu, điều kiện trạng thái và xử lý thanh toán. Endpoint hủy hiện tại dành cho admin; không cho chatbot tự gọi thay khách.
- Hoàn thiện checkout theo hội thoại, địa chỉ đã lưu và hủy bản nháp; kiểm thử UI xác nhận bằng tài khoản thử.
- Nâng độ phù hợp của tư vấn mô tả tự nhiên, so sánh lựa chọn tốt hơn và xử lý chuyển sang tài liệu liên quan khi không có sách chuyên đề.
- Dữ liệu kiểm thử cần có Python để chạy đủ chuỗi chọn sách/giỏ; kiểm thử catalog thiếu sách riêng, không thêm dữ liệu giả vào catalog thật để nâng điểm.

## Mở rộng

Chức năng mới vẫn là Spring bean `ChatTool`, tự đăng ký qua registry. Quyền truy cập và tính giá thuộc backend, model chỉ chọn chức năng/tham số. Mọi hành động thay đổi dữ liệu cần biên nhận từ nghiệp vụ; không sử dụng lời model như xác nhận giao dịch.

## Hoàn thiện nghiệp vụ — 11/09/2026

- Giỏ đã chuyển sang server làm nguồn chính cho tài khoản đăng nhập: migration một lần mỗi trình duyệt, lưu bản sao dữ liệu cũ, revision phát hiện xung đột, tuần tự hóa thao tác và đợi biên nhận trước khi báo thành công.
- Xóa toàn bộ giỏ và hủy đơn đã có đề xuất/xác nhận trên chat. Hủy đơn chỉ áp dụng đơn của khách, COD chưa thanh toán và còn chờ xử lý; hủy lặp không hoàn tồn kho lần nữa.
- Checkout trang web và chat dùng cùng quote/confirm: giá và phí tại server, giữ tồn kho lúc xác nhận, khóa sản phẩm, tiêu thụ đúng dòng giỏ đã chọn trong một transaction. Endpoint checkout cũ cũng đi qua nghiệp vụ này.
- Hủy bản nháp vô hiệu hóa token ở backend. Hội thoại hỗ trợ địa chỉ đã lưu, sửa số lượng, COD, câu xác nhận có đại từ và câu đổi ý; không bỏ qua bước xem lại đơn.
- Tránh tạo hai yêu cầu khi bấm đặt hàng hai lần lúc giỏ đang tải; chờ tải giỏ trước khi kết luận checkout rỗng.
- Sửa đăng ký email dài: không ghi JWT vào cột token dành cho reset mật khẩu.
- Kiểm thử: 208 backend và 124 frontend đạt, 25 kiểm tra API trên hai tài khoản mới đạt; build storefront đạt (còn cảnh báo dung lượng bundle/SCSS). Lỗi nhầm chữ Giáo thành giá sau lượt tìm rỗng được phát hiện khi kiểm tra UI và có kiểm thử hồi quy bổ sung.

Những con số test kỹ thuật không phải điểm chất lượng trả lời 100 câu. Kết quả hội thoại thật được lưu riêng kèm model, chế độ anonymous và giới hạn không thực thi action.

Kết quả ghi 100 câu mới: `work/astra-chatbot-regression-1789064952138/results.json`, 97 HTTP 200, 3 HTTP 500 tại 75/83/90, 5 fallback tại 13/14/29/33/87. Gián đoạn sau câu 35 được ghi trong interruptions.jsonl. Đã sửa lỗi transaction của bước preview hủy đơn và thử lại 75/83/90 đều HTTP 200; bản gốc được giữ nguyên, không thay câu lỗi bằng câu retry. Bổ sung phân biệt câu hỏi cách hủy đơn với yêu cầu thực thi hủy.

Kiểm tra UI hoàn tất: thêm/xóa toàn giỏ, từ chối xóa giữ dữ liệu, nhập giao hàng qua chat, bỏ bản nháp, xác nhận tạo đơn #44 và hủy với biên nhận; checkout thường tạo #45 chỉ cho dòng Java đã chọn, giữ Nhà Giả Kim trong giỏ. Hai đơn thử đã hủy, tồn kho Java trở lại 140. API bổ sung 11/11 đạt. Backend 209 test đạt sau sửa transaction; sau chỉnh cuối về hướng dẫn hủy, 44 test liên quan đạt. Ba câu retry đều HTTP 200 không fallback. Báo cáo tại workspace đánh giá `2026-09-10/ti/outputs/vinabook-completion-report.md`.
