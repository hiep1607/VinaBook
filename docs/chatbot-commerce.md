# Giỏ hàng và đặt sách trong chat — 06/10/2026

## Hành vi

Nút giỏ ở đầu widget mở thẻ giỏ hiện hành. Khách đổi số lượng, xóa một sách hoặc xóa toàn bộ giỏ rồi xác nhận. Thao tác dùng CartService của website và chỉ báo thành công sau khi đồng bộ hoàn tất. Giá/tồn kho tải lại từ ProductService; phí giao dùng StoreKnowledgeService. Không có catalog hay giỏ riêng cho chatbot.

Action ADD_TO_CART, REMOVE_FROM_CART, UPDATE_CART_QUANTITY và CLEAR_CART chỉ đề xuất thay đổi. Giao diện so sánh snapshot/revision của giỏ trước khi thực thi. Nút thao tác của hội thoại cũ không được thực thi lại sau khi tải lịch sử; khách có thể dùng nút giỏ để tiếp tục trên dữ liệu hiện hành.

BUY_NOW mở form cho một sách với số lượng đã chọn, không tự thêm vào hoặc xóa khỏi giỏ. CHECKOUT_CART mở form cho toàn bộ giỏ, kể cả các sách chưa đánh dấu chọn ở trang giỏ. Khách cần đăng nhập, chọn địa chỉ đã lưu hoặc nhập người nhận/điện thoại/địa chỉ, rồi chọn phương thức thanh toán đang khả dụng.

## Báo giá và xác nhận

- POST /api/chat/checkout/quote nhận một productId/quantity hoặc danh sách items, paymentMethod, thông tin giao hàng, fromCart và cartRevision. Không nhận chủ đơn từ client.
- Báo giá dùng giá, phí giao và tồn kho trên máy chủ, có hiệu lực 15 phút. Lập báo giá không tạo đơn.
- POST /api/chat/checkout/{token}/confirm chỉ gọi khi khách bấm Xác nhận đặt hàng. Backend kiểm tra lại phí giao, giá, tồn kho, phương thức thanh toán và revision; thay đổi buộc khách xem báo giá mới. Toàn bộ thao tác tạo đơn, giữ tồn kho và lấy sách khỏi giỏ nằm trong giao dịch.
- Cùng token trả lại mã đơn đã tạo; nút xác nhận bị khóa khi đang gửi. Với kết quả chưa rõ do lỗi kết nối, giữ token cũ và cho kiểm tra lại, không lập báo giá khác. sessionStorage chỉ giữ token/phương thức thanh toán theo tài khoản để phục hồi khi mở lại widget; không giữ địa chỉ hoặc điện thoại trong nhật ký này.
- COD hiện mã đơn trong chat. Các phương thức khác dùng PaymentService của website để mở thanh toán. Nếu mở thanh toán thất bại, khách tiếp tục thanh toán cùng đơn đã tạo.
- DELETE /api/chat/checkout/{token} hủy bản nháp chưa xác nhận. Hủy đơn đã tạo dùng bước xem lại và xác nhận chung trên trang Đơn hàng và trong chat (cập nhật 07/10/2026).

Form và báo giá chứa thông tin giao hàng chỉ ở bộ nhớ widget. Chuyển tài khoản hoặc đóng widget xóa state riêng; các response từ tài khoản cũ bị bỏ qua. Dữ liệu này không đưa vào history gửi cho AI. Cache hội thoại local cũng không lưu tin riêng tư và response tra đơn.

## Kiểm thử

Backend có kiểm thử giỏ nhiều sách, kiểm tra revision/số lượng trước báo giá, phí giao đổi, phương thức thanh toán bị tắt, giá/tồn kho đổi và xác nhận lặp. Bộ chatbot, CartSyncService và OrderCancellationService chạy cùng các ca mới.

16 ca ChromeHeadless cho ChatCommerceComponent kiểm tra xác nhận sửa giỏ, số lượng vượt tồn kho, đồng bộ lỗi, yêu cầu đăng nhập, form không hợp lệ, mua ngay/toàn bộ giỏ, xác nhận lặp, phục hồi cùng token, chuyển tài khoản và lỗi cổng thanh toán. Bộ test chung của storefront còn lỗi compile ở spec tìm kiếm cũ; phần commerce được chạy với entry/tsconfig riêng trong thư mục kiểm thử của tác vụ.

Đã kiểm tra widget thực tế trong bản build production bằng API fixture trên localhost: mở giỏ, mở form qua nút trong câu trả lời, xem báo giá và màn hình hẹp 390×844 không tràn ngang. Fixture chỉ dùng dữ liệu giả trong bộ nhớ. Chưa kiểm tra tạo đơn với MySQL thật hoặc giao dịch với cổng thanh toán thật trong đợt này.

## Sửa lỗi streaming khi đăng nhập — 06/10/2026

Tác vụ SSE dùng common pool phải nhận context classloader của request và khôi phục loader của worker khi kết thúc. Nếu thiếu bước này, Spring devtools có thể khiến Hibernate nhận hai lớp User khác nhau và lỗi khi tải sách yêu thích của tài khoản. SecurityContext của caller vẫn được truyền sang tác vụ nền để xác minh đúng owner.

Lỗi xử lý trả sự kiện SSE error với thông báo chung rồi đóng stream, không gửi chi tiết exception chứa thông tin tài khoản. Giao diện hủy stream nếu chưa nhận kết quả sau 60 giây; transport JSON dự phòng cũng có timeout 60 giây. Hết hạn hiển thị lỗi và nút thử lại thay vì giữ dấu ba chấm vô hạn. Stream hết hạn không tự gửi lại cùng yêu cầu qua JSON.

Ba kiểm thử ChatApiStreamTest xác minh classloader/principal, lỗi SSE an toàn và rate limit. Sáu kiểm thử ChatbotService xác minh fetch treo, stream thiếu done, error event, JSON fallback treo, trả lời hoàn tất và unsubscribe. Tổng 22 kiểm thử giao diện gồm 16 ca commerce đều đạt. Trên web localhost với backend/MySQL thật và phiên đăng nhập sẵn có, hai câu hỏi “trong giỏ hàng của tôi có những sách nào” và “Đơn hàng của tôi” đều trả lời đầy đủ. Không thay đổi giỏ hay tạo đơn trong kiểm tra này.

## Sửa đặt mua và gợi ý theo ngân sách — 06/10/2026

Các cách nói “đặt cho tôi cuốn …” và “đặt giúp tôi …” được nhận diện là yêu cầu mua. Tên sách trước phần chú thích ấn bản trong ngoặc được dùng làm tên rút gọn; nếu nhiều ấn bản cùng khớp, bot vẫn hỏi khách chọn. Từ “Bộ” trong tên Bộ Luật không còn bị hiểu như lệnh bỏ sách khỏi giỏ. Đề xuất sửa giỏ hoặc mua một sách cập nhật cuốn đang chọn để câu tiếp theo “mua cuốn này” dùng đúng sản phẩm.

Ở lần sửa ban đầu, yêu cầu gợi ý chỉ có ngân sách được xử lý trực tiếp theo điều kiện giá. Quy tắc này đã được thay bằng bước thu thập nhu cầu và người đọc ở mục bên dưới. Sau khi đủ thông tin, lựa chọn không có chủ đề hay thứ tự sắp xếp cụ thể vẫn luân phiên các danh mục, ưu tiên số lượng bán trong từng nhóm, thay vì lấy các ID đầu catalog vốn có nhiều sách luật. Bộ lọc chủ đề/tác giả và yêu cầu giá tăng dần, sách bán chạy, sách mới vẫn được giữ.

Bộ hồi quy gồm 402 kiểm thử backend đều đạt; 24 kiểm thử giao diện gồm ca hiển thị và thực thi nút BUY_NOW đều đạt. Trên phiên đăng nhập thật, “Gợi ý sách dưới 150k” trả Nhà Giả Kim, Đắc Nhân Tâm, Doraemon - Tập 1, Mắt Biếc và Từ Tốt Đến Vĩ Đại, thuộc năm danh mục. “đặt cho tôi cuốn luật đất đai” rồi “mua cuốn này” đều hiện nút Tiếp tục đặt hàng cho đúng cuốn, mở form và nhận báo giá từ backend/MySQL. Kiểm tra dừng trước nút Xác nhận đặt hàng; chưa tạo đơn trong lượt kiểm tra này.

## Hai thông tin bắt buộc trước khi tư vấn — 06/10/2026

Bot cần biết (1) chủ đề hoặc mục đích đọc và (2) người đọc/người nhận trước khi đề xuất sách. Ngân sách là tùy chọn: đủ hai thông tin thì tìm sách ngay, không chặn để hỏi giá hoặc trình độ. Khi chỉ có “Gợi ý sách dưới 150k”, bot hỏi cả hai; khi đã có chủ đề thì chỉ hỏi người đọc; “Mình muốn đọc trinh thám” đã đủ thông tin.

ChatRecommendationIntake kiểm tra trước cả AI và chế độ local. Các công cụ tìm/tư vấn/sách liên quan kiểm tra lại điều kiện khi được gọi từ ChatService. Bằng chứng chỉ lấy từ lời khách và các lượt user của lịch sử; không lấy lời assistant, trường do AI suy đoán hoặc lịch sử xem sách làm xác nhận. Thông tin và các lượt chưa hoàn tất lưu cùng state hội thoại, tách theo tài khoản và session. Câu ngắn như “cho mình” hoàn tất nhu cầu trước đó; ngân sách đã nói vẫn được giữ, ngân sách mới thay số cũ. Tuổi người đọc không được tính thành tiền. Đổi người đọc không tự chuyển sở thích của người trước sang người mới.

Tra một tên sách cụ thể, so sánh các sách đã chọn, xem giỏ, mua sách đã xác định và tra đơn vẫn xử lý theo luồng hiện có. Lời chào nêu rõ ngân sách là thông tin bổ sung. Planner nhận nhu cầu/người đọc đã xác nhận; nếu planner cố hỏi ngân sách hoặc trình độ khi đã đủ hai thông tin, công cụ respond chuyển sang tìm sách.

424 kiểm thử backend (chatbot, CartSyncService, OrderCancellationService) và 24 kiểm thử widget/commerce đều đạt. Các ca mới xác minh hỏi đúng phần thiếu, không dùng giới tính để đoán sở thích, hoàn tất qua nhiều lượt, lưu/khôi phục state, tách owner/session, ngân sách tùy chọn và cập nhật giá, tra tên sách, bảo vệ công cụ tìm kiếm và tuổi không bị hiểu thành ngân sách. Máy chủ localhost đã biên dịch lại bản mới; kiểm tra thực tế dùng phiên khách độc lập.

## Giọng trò chuyện và hướng dẫn rõ lý do — 06/10/2026

ChatVoice cung cấp quy tắc giọng nói chung cho planner, phần diễn giải hội thoại và phần giải thích sách: xưng mình/bạn, ghi nhận đúng thông tin khách đã chia sẻ, hỏi phần còn thiếu và giải thích thông tin đó giúp ích thế nào. Không lặp máy móc một câu mở đầu, đoán cảm xúc hoặc thêm lời hứa không có căn cứ. Câu trả lời trực tiếp vẫn có thể ngắn; phần cần hướng dẫn nêu rõ bước tiếp theo và mục đích.

Các câu trả lời cố định ở backend và widget cũng áp dụng cách viết này, gồm thu thập nhu cầu, chọn đúng sách/số lượng, so sánh, giỏ hàng, đăng nhập/tra đơn, chính sách, thông tin giao hàng, báo giá/xác nhận và xử lý lỗi. Lời xác nhận vẫn phân biệt đề xuất, chờ khách duyệt và thao tác đã hoàn tất. Không đổi điều kiện mua hàng, dữ kiện sách, giá/tồn kho hoặc quyền truy cập để làm câu trả lời thân thiện hơn. Hai thông tin bắt buộc và ngân sách tùy chọn được giữ nguyên.

424 kiểm thử backend và 24 kiểm thử widget/commerce đều đạt; build production thành công. Build còn cảnh báo dung lượng bundle và stylesheet trang chi tiết sách đang có. Backend đang chạy đã biên dịch lại; kiểm tra web/API thật xác minh hỏi người đọc sau khi biết chủ đề, hỏi nhu cầu sau khi biết người nhận, giải thích đăng nhập để bảo vệ thông tin đơn hàng, hướng dẫn giỏ trống và trả sách ngay khi bổ sung đủ hai thông tin mà không hỏi ngân sách. Không tạo đơn hoặc thay đổi giỏ trong lượt kiểm tra này. Ảnh kiểm tra và ghi chú bàn giao nằm trong outputs của tác vụ.
