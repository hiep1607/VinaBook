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

Ở lần sửa ban đầu, yêu cầu gợi ý chỉ có ngân sách được xử lý trực tiếp theo điều kiện giá. Quy tắc này đã được thay bằng bước xác nhận nhu cầu đọc ở mục bên dưới; người đọc là thông tin bổ sung. Sau khi đủ thông tin, lựa chọn không có chủ đề hay thứ tự sắp xếp cụ thể vẫn luân phiên các danh mục, ưu tiên số lượng bán trong từng nhóm, thay vì lấy các ID đầu catalog vốn có nhiều sách luật. Bộ lọc chủ đề/tác giả và yêu cầu giá tăng dần, sách bán chạy, sách mới vẫn được giữ.

Bộ hồi quy gồm 402 kiểm thử backend đều đạt; 24 kiểm thử giao diện gồm ca hiển thị và thực thi nút BUY_NOW đều đạt. Trên phiên đăng nhập thật, “Gợi ý sách dưới 150k” trả Nhà Giả Kim, Đắc Nhân Tâm, Doraemon - Tập 1, Mắt Biếc và Từ Tốt Đến Vĩ Đại, thuộc năm danh mục. “đặt cho tôi cuốn luật đất đai” rồi “mua cuốn này” đều hiện nút Tiếp tục đặt hàng cho đúng cuốn, mở form và nhận báo giá từ backend/MySQL. Kiểm tra dừng trước nút Xác nhận đặt hàng; chưa tạo đơn trong lượt kiểm tra này.

## Nhu cầu đọc bắt buộc, người đọc tùy chọn — cập nhật 08/10/2026

Bot chỉ cần biết chủ đề hoặc mục đích đọc trước khi đề xuất sách. Người đọc/người nhận, ngân sách, tuổi và trình độ là thông tin bổ sung: có nhu cầu thì tìm sách ngay, không chặn để hỏi các thông tin này. “Gợi ý sách trinh thám” được tư vấn ngay; “Gợi ý sách dưới 150k” chỉ hỏi chủ đề hoặc mục đích. “Mua sách tặng mẹ” chưa có nhu cầu nên hỏi mẹ thích đọc gì hoặc muốn đọc để làm gì. Nếu khách cung cấp người nhận/tuổi thì vẫn dùng để chọn sách, không tự mặc định người nhận là bản thân.

ChatRecommendationIntake kiểm tra trước cả AI và chế độ local. Các công cụ tìm/tư vấn/sách liên quan kiểm tra lại điều kiện khi được gọi từ ChatService. Bằng chứng chỉ lấy từ lời khách và các lượt user của lịch sử; không lấy lời assistant, trường do AI suy đoán hoặc lịch sử xem sách làm xác nhận. Thông tin và các lượt chưa hoàn tất lưu cùng state hội thoại, tách theo tài khoản và session. Câu ngắn trả lời chủ đề hoàn tất bước thu thập nhu cầu. Hội thoại cũ đang chờ người nhận vẫn được tiếp tục khi đã có chủ đề; ngân sách đã nói vẫn được giữ, ngân sách mới thay số cũ. Tuổi người đọc không được tính thành tiền. Đổi người đọc không tự chuyển sở thích của người trước sang người mới.

Tra một tên sách cụ thể, so sánh các sách đã chọn, xem giỏ, mua sách đã xác định và tra đơn vẫn xử lý theo luồng hiện có. Lời chào chỉ hỏi hướng đọc và nêu các thông tin khác là bổ sung. Planner nhận nhu cầu đã xác nhận và người đọc nếu khách đã nói; nếu planner cố hỏi người nhận, ngân sách hoặc trình độ khi đã có nhu cầu, công cụ respond chuyển sang tìm sách.

Kiểm tra cập nhật 08/10: 242 kiểm thử trong ChatRecommendationIntakeTest, ChatServiceTest và ChatToolsTest đạt, không failure/error. Có kiểm tra planner không chặn tư vấn để hỏi người nhận/ngân sách/trình độ, tiếp tục hội thoại cũ đang chờ người nhận, giữ ngân sách và không tự thêm người nhận khi chưa được cung cấp.

5 kịch bản localhost đạt sau khi cập nhật backend đang chạy (AI_PROVIDER=local):
1. “Gợi ý sách trinh thám”: trả 2 sách ngay, không hỏi người nhận.
2. “Gợi ý sách dưới 150k”: chỉ hỏi hướng đọc, chưa trả sách.
3. “Gợi ý sách giao tiếp dưới 150k”: trả 3 sách, giữ giá mỗi cuốn dưới giới hạn.
4. “Gợi ý sách cho mẹ”: hỏi nhu cầu/chủ đề, không đoán sở thích theo người nhận.
5. “Gợi ý truyện giải trí cho bé 8 tuổi, dưới 150k”: trả lựa chọn có ghi chú mức độ xác nhận, giữ thông tin tuổi/ngân sách.

Lời chào mới đã được xác minh có trong frontend đang phục vụ ở localhost:4200. Không gọi Gemini thật, không tạo đơn hoặc thay đổi giỏ hàng trong đợt kiểm tra này.

Kết quả quy tắc cũ ngày 06/10: 424 kiểm thử backend (chatbot, CartSyncService, OrderCancellationService) và 24 kiểm thử widget/commerce đều đạt. Các ca mới xác minh hỏi đúng phần thiếu, không dùng giới tính để đoán sở thích, hoàn tất qua nhiều lượt, lưu/khôi phục state, tách owner/session, ngân sách tùy chọn và cập nhật giá, tra tên sách, bảo vệ công cụ tìm kiếm và tuổi không bị hiểu thành ngân sách. Máy chủ localhost đã biên dịch lại bản mới; kiểm tra thực tế dùng phiên khách độc lập.

## Giọng trò chuyện và hướng dẫn rõ lý do — 06/10/2026

ChatVoice cung cấp quy tắc giọng nói chung cho planner, phần diễn giải hội thoại và phần giải thích sách: xưng mình/bạn, ghi nhận đúng thông tin khách đã chia sẻ, hỏi phần còn thiếu và giải thích thông tin đó giúp ích thế nào. Không lặp máy móc một câu mở đầu, đoán cảm xúc hoặc thêm lời hứa không có căn cứ. Câu trả lời trực tiếp vẫn có thể ngắn; phần cần hướng dẫn nêu rõ bước tiếp theo và mục đích.

Các câu trả lời cố định ở backend và widget cũng áp dụng cách viết này, gồm thu thập nhu cầu, chọn đúng sách/số lượng, so sánh, giỏ hàng, đăng nhập/tra đơn, chính sách, thông tin giao hàng, báo giá/xác nhận và xử lý lỗi. Lời xác nhận vẫn phân biệt đề xuất, chờ khách duyệt và thao tác đã hoàn tất. Không đổi điều kiện mua hàng, dữ kiện sách, giá/tồn kho hoặc quyền truy cập để làm câu trả lời thân thiện hơn. Sau cập nhật 08/10, chỉ nhu cầu đọc là bắt buộc; người đọc và ngân sách là tùy chọn.

Kết quả giọng trò chuyện ngày 06/10, trước khi bỏ bắt buộc người nhận: 424 kiểm thử backend và 24 kiểm thử widget/commerce đều đạt; build production thành công. Build còn cảnh báo dung lượng bundle và stylesheet trang chi tiết sách đang có. Backend đang chạy đã biên dịch lại; kiểm tra web/API thật xác minh hỏi người đọc sau khi biết chủ đề, hỏi nhu cầu sau khi biết người nhận, giải thích đăng nhập để bảo vệ thông tin đơn hàng, hướng dẫn giỏ trống và trả sách ngay khi bổ sung đủ hai thông tin mà không hỏi ngân sách. Không tạo đơn hoặc thay đổi giỏ trong lượt kiểm tra này. Ảnh kiểm tra và ghi chú bàn giao nằm trong outputs của tác vụ.

## Hủy đơn trên web trước, tích hợp chat sau — 07/10/2026

Trang Đơn hàng trước đây chỉ hiển thị trạng thái, chưa có thao tác hủy cho khách. Đã thêm nút Hủy đơn và OrderCancellationComponent dùng chung: đọc mã đơn, ngày đặt, sách/số lượng và tổng tiền từ API xác minh JWT; xem trước không thay đổi đơn. Khách có thể Giữ đơn hàng hoặc bấm Xác nhận hủy đúng mã đơn. Sau khi phần web đạt kiểm thử, action CANCEL_ORDER được nối vào chính component này trong chat. Cả AI và local đều chuẩn bị yêu cầu; công cụ không thực hiện hủy.

GET /api/orders/me/{id}/cancellation chỉ trả dữ liệu cần xác nhận và lý do không đủ điều kiện, không trả tài khoản/địa chỉ/điện thoại. POST /api/orders/me/{id}/cancel yêu cầu confirmed=true và gọi OrderCancellationService hiện có. Backend khóa đơn, kiểm tra lại chủ sở hữu/trạng thái/thanh toán và hoàn tồn kho đã giữ đúng một lần. Chỉ đơn COD chưa thanh toán, chờ xử lý được tự hủy. Đơn đang giao/hoàn thành/thanh toán trực tuyến chuyển hướng liên hệ cửa hàng; không tự hoàn tiền.

Giao diện khóa bấm lặp, xóa state khi đổi tài khoản/đơn, bỏ response cũ, timeout 20 giây. Kết quả chưa rõ phải Kiểm tra trạng thái; không tự gửi lại thao tác hủy. Action hội thoại cũ không được thực thi trực tiếp. Kết quả hủy nằm trong state riêng của widget, không lưu vào cache chat local.

432 kiểm thử backend và 33 kiểm thử giao diện đều đạt; build production thành công, còn hai cảnh báo dung lượng bundle/stylesheet đã tồn tại. Kiểm tra web thật với backend/MySQL và phiên đăng nhập sẵn có: nút hủy trên trang Đơn hàng và câu “hủy đơn #46” trong chat đều mở cùng bước xác nhận, hiển thị đúng sách/tổng đơn. Chọn Giữ đơn hàng trong cả hai lần; không hủy đơn thật của khách. Nhánh hủy thành công/hoàn tồn kho/xác nhận lặp/quyền sở hữu đã được kiểm thử tự động bằng fixture.

## Danh sách yêu thích dùng chung website — 07/10/2026

Đã kiểm tra website có trang Yêu thích, nút lưu tại sách và nút xóa. Chat dùng cùng WishlistService và key wishlist_{email}; không tạo danh sách riêng. Snapshot wishlistProductIds chỉ là danh sách local của thiết bị, được lọc ID/sách còn bán; backend vẫn xác minh đăng nhập từ JWT. “Xem danh sách yêu thích của tôi” trả sách và giá hiện tại, “lưu/bỏ cuốn này vào/khỏi yêu thích” chuẩn bị nút cho sách đã xác định. Nút chỉ cập nhật sau khi khách bấm; thao tác thêm là idempotent, không dùng toggle gây đảo kết quả khi bấm lặp. “Mình thích trinh thám” vẫn là sở thích đọc, không bị hiểu là yêu cầu sửa yêu thích. Hỏi yêu thích không bị bước thu thập nhu cầu tư vấn chặn.

142 ca ChatToolsTest và 34 kiểm thử giao diện đạt sau khi bổ sung kiểm thử routing và sửa tham số công cụ đọc. Web thật xác minh cùng cuốn Muôn Kiếp Nhân Sinh ở trang Yêu thích và câu trả lời chat; không thay đổi danh sách thật khi kiểm tra. Danh sách này tiếp tục là dữ liệu trên thiết bị theo cơ chế website hiện có, chưa đồng bộ đa thiết bị.

## Mua lại đơn cũ — 07/10/2026

Trang Đơn hàng chưa có mua lại nên đã bổ sung OrderReorderComponent trước khi nối action REORDER_ORDER vào chat. GET /api/orders/me/{id}/reorder đọc đơn của chính JWT và chỉ trả mã sách/tên/số lượng cũ. ProductService tải giá, trạng thái bán và tồn kho hiện tại; khách xem lại và chỉnh số lượng rồi xác nhận thêm vào cùng CartService của website. Không giữ giá đơn cũ hoặc tự tạo đơn/thanh toán. Sách không còn khả dụng ngăn thêm toàn bộ bản nháp; khách được hướng dẫn chọn sách còn bán ở trang Sách.

CartService.addBooks kiểm tra mọi dòng và tổng số lượng trước khi lưu một lần, tránh giỏ bị thêm một phần khi có dòng không hợp lệ. Kết quả đồng bộ chưa rõ yêu cầu kiểm tra giỏ trước khi thao tác tiếp. Response cũ khi đổi tài khoản/đơn bị bỏ qua. Chat “mua lại đơn #…” chỉ chuẩn bị cùng biểu mẫu web; không gọi thêm giỏ trong công cụ backend.

## Yêu cầu đổi trả và quản trị — 07/10/2026

Website chưa có biểu mẫu/theo dõi đổi trả, nên đã làm phần web và kiểm thử trước khi thêm CREATE_RETURN_REQUEST vào chat. Trang Đơn hàng hiển thị Đổi trả cho đơn đã hoàn thành. Biểu mẫu dùng SupportRequestComponent chung; khách chọn sách thật trong đơn, nhập ngày nhận thực tế, cách muốn xử lý và lý do. Bước Xem lại yêu cầu tách khỏi Xác nhận gửi yêu cầu. Backend xác minh JWT/chủ đơn/sách/ngày nhận và lưu phiếu OPEN; việc lưu phiếu chưa đồng nghĩa chấp nhận đổi trả hoặc hoàn tiền. Chính sách vẫn là câu trả lời riêng khi khách chỉ hỏi điều kiện/hướng dẫn.

SupportRequestsApi cung cấp POST/GET /api/support/me, GET /me/order/{id} và GET /me/request/{key}. Chủ sở hữu được lấy từ JWT, không từ dữ liệu client. requestKey gắn với tài khoản và nội dung giúp gửi lặp trả cùng phiếu; nội dung khác cùng key bị từ chối. Khóa tài khoản khi lưu/kiểm tra kết quả tránh kết luận chưa lưu trong lúc lần gửi trước đang chạy. Giao diện giữ key theo tài khoản trong sessionStorage để phục hồi, không giữ nội dung/ngày nhận trong nhật ký này. Lỗi kết nối không tự gửi lại; khách kiểm tra kết quả trước.

Trang Liên hệ và action SHOW_SUPPORT_REQUESTS trong chat dùng cùng danh sách tối đa 50 yêu cầu mới nhất, trạng thái và phản hồi. Nội dung phiếu được tải riêng ở component, không chèn vào prompt hoặc cache hội thoại. Chuyển tài khoản xóa state và bỏ qua response cũ. Admin có hàng đợi /admin/support-requests, lọc/phân trang và phản hồi cùng trạng thái IN_REVIEW/RESOLVED/REJECTED. API admin kiểm tra quyền, khóa phiếu và version để tránh ghi đè phản hồi đồng thời; không thay đổi thanh toán hoặc tồn kho.

Schema bổ sung nằm trong db/05-support-requests.sql. Môi trường local dùng Hibernate ddl-auto=update; môi trường triển khai cần chạy migration trước khi dùng API, không dựa vào tự cập nhật schema production.

## Nhờ nhân viên hỗ trợ — 07/10/2026

Đã thêm biểu mẫu hỗ trợ cho khách đăng nhập ở trang Liên hệ trước khi tích hợp prepareHumanSupport. Chat mở bản nháp cùng SupportRequestComponent với kind SUPPORT. Tóm tắt lấy tối đa bốn lượt user không riêng tư, khách xem/sửa/bổ sung rồi xác nhận gửi. Chỉ gắn đơn khi khách nói rõ và backend xác minh sở hữu; không tự gắn đơn đang xem. Khách theo dõi phản hồi ở Liên hệ hoặc nhắn “xem yêu cầu đã gửi”. Nhân viên xử lý ở hàng đợi admin. Đây là yêu cầu hỗ trợ lưu trên web, chưa phải chuyển sang nhân viên đang trực tuyến và không hứa thời gian trả lời cụ thể.

## Hồi quy cả năm chức năng — 07/10/2026

454 kiểm thử backend (Chat*Test, CartSyncServiceTest, OrderCancellationServiceTest, MyOrderActionsApiTest, CustomerSupportServiceTest, SupportRequestsApiTest), 49 kiểm thử storefront và 3 kiểm thử admin đều đạt. Các ca mới bao gồm JWT/chủ đơn, xác nhận riêng, gửi lặp, kết quả chưa rõ, đổi tài khoản, giá/tồn kho mua lại, thao tác yêu thích, origin local/loại bỏ origin lạ và phiên local không để câu hỏi chính sách chặn lệnh đổi trả. Storefront/admin dùng bộ entry kiểm thử riêng vì các spec cũ ngoài phạm vi vẫn có lỗi đã ghi trong docs/bugs.md. Hai bản build production thành công, còn cảnh báo dung lượng bundle/stylesheet. Ba biểu mẫu dùng busyChange bất đồng bộ để việc khởi tạo component con không thay đổi binding đã kiểm tra ở cha; ca host mua lại kiểm tra hồi quy lỗi Angular NG0100.

MyOrderActionsApi/SupportRequestsApi chỉ cho CORS từ localhost/127.0.0.1 cổng 4200 và 4300. Production nên dùng reverse proxy cùng origin theo cấu hình website hiện tại; nếu frontend/backend đặt khác origin cần cấu hình đúng origin triển khai trước khi dùng các API này.

Kiểm tra localhost với backend/MySQL thật: hủy đơn mở xem lại trên web/chat và chọn Giữ đơn; mua lại đơn #46 mở đúng sách, giá hiện tại và tồn kho trên cả hai giao diện, dừng trước thêm giỏ; yêu thích đọc cùng danh sách hiện hành; đổi trả đơn đang chờ được giải thích điều kiện và hướng dẫn hỗ trợ; biểu mẫu hỗ trợ cho xem/sửa nội dung trước xác nhận; danh sách yêu cầu trong chat và Liên hệ cùng trạng thái trống của tài khoản. Không hủy, mua lại, gửi phiếu hoặc thanh toán thật trong QA. Nhánh lưu phiếu/phản hồi/đổi trả đơn hoàn thành được kiểm thử tự động bằng dữ liệu giả, chưa gửi một phiếu thật trong đợt này.

Widget chờ lượt render tiếp theo trước khi cuộn xuống cuối, để phản hồi/biểu mẫu mới không nằm ngoài khung nhìn khi lịch sử dài. Backend đang chạy được biên dịch và khởi động lại đầy đủ sau các thay đổi: tránh Spring devtools quét class trong lúc Maven đang biên dịch khiến một số API tạm thiếu. OPTIONS thật xác minh API hủy/hỗ trợ/admin cho origin local và từ chối origin lạ; các endpoint vẫn cần JWT/role ở request thực thi.

## Đánh giá sách trong chat — 07/10/2026

Trang chi tiết sách đã có điểm sao, nhận xét và biểu mẫu gửi/cập nhật, nên chat dùng chung ReviewService và API `/api/rates`. Khi khách hỏi nhận xét, bot chỉ đọc đánh giá của đúng sách, tính điểm trung bình và trích một số nhận xét thực tế; không đưa tên hay email người đánh giá vào câu trả lời. Nếu chưa rõ khách muốn đọc hay tự viết, bot hỏi lại trước khi mở đúng biểu mẫu.

Khách đăng nhập có thể mở form ngay trong chat, chọn 1–5 sao, nhập tối đa 2.000 ký tự, xem lại rồi chủ động gửi. Form nhận diện đánh giá hiện có để cập nhật thay vì tạo thêm. Khách chưa đăng nhập được hướng dẫn đăng nhập; backend lấy chủ sở hữu từ JWT và chỉ cho sửa đánh giá của chính tài khoản. POST lặp cùng sách/tài khoản cập nhật đánh giá hiện có, tránh tạo bản trùng khi giao diện chưa kịp nạp cache. Bot không tự ghi nhận đánh giá.

152 kiểm thử backend cho ChatToolsTest và RateApiTest đều đạt; 7 kiểm thử ChromeHeadless riêng cho biểu mẫu đánh giá đều đạt. Build production Angular thành công. Bộ test storefront mặc định vẫn có các spec tìm kiếm cũ lỗi compile; spec đánh giá được chạy bằng entry và tsconfig tạm chỉ nạp test của chức năng này. Build còn cảnh báo dung lượng bundle và stylesheet trang chi tiết sách.

### 5 kịch bản để Luna kiểm tra thủ công

Chạy trên web local/dev với sách có dữ liệu phù hợp và tài khoản test. Không dùng tài khoản khách thật hoặc ghi đánh giá lên production.

1. **Đọc nhận xét của đúng sách:** hỏi “Cho mình xem đánh giá của [tên sách]”. Bot phải nêu điểm trung bình/nhận xét lấy từ sách đó, không hiện tên/email người đánh giá và không tự chuyển sang sách luật hoặc sách khác.
2. **Sách chưa có đánh giá:** hỏi nhận xét của một sách chưa có rate. Bot phải nói hiện chưa có nhận xét, không bịa số sao/câu trích, và có thể đưa lựa chọn viết đánh giá.
3. **Câu hỏi chưa rõ ý:** mở ngữ cảnh một sách rồi nhắn “Đánh giá cuốn này”. Bot phải hỏi muốn xem nhận xét hay tự viết; trước câu trả lời đó không được mở form hoặc lưu dữ liệu.
4. **Khách chưa đăng nhập:** yêu cầu “Mình muốn viết đánh giá cuốn [tên sách]”. Bot phải giải thích cần đăng nhập để lưu đúng tài khoản và đưa nút đăng nhập; không hiện trạng thái đã lưu và không tạo rate.
5. **Viết mới, kiểm tra dữ liệu web và cập nhật:** dùng tài khoản/sách test đã đăng nhập. Mở form từ chat, thử gửi khi chưa chọn sao hoặc chưa nhập nhận xét (bị chặn), sau đó gửi 4 sao cùng nhận xét thử. Xác minh thành công, mở trang chi tiết sách thấy cùng nội dung/điểm và chỉ một rate của tài khoản. Mở form lại, đổi sao/nội dung rồi gửi; đánh giá hiện có phải được cập nhật, không nhân đôi. Dùng dữ liệu local/dev để có thể dọn dẹp sau.

### Rà soát chất lượng đánh giá sách — 07/10/2026

Đợt rà soát phát hiện và sửa các lỗi sau:

- Các câu “cuốn này mấy sao”, “xem đánh giá 5 sao” được xử lý là đọc đánh giá. Câu trả lời ngắn “xem”/“viết”, tên sách và số thứ tự tiếp tục đúng yêu cầu đang chờ; cuốn được chọn cập nhật ngữ cảnh cho lượt sau. Khi tên sách không tồn tại hoặc còn nhiều lựa chọn, chỉ nói “xem”/“viết” vẫn cần chọn sách, không lấy cuốn cũ thay thế.
- Điểm trung bình dùng cùng phạm vi dữ liệu 1–5 sao trên web và chat, gồm điểm thập phân cũ và đánh giá chỉ có sao; không bịa nhận xét khi thiếu nội dung. Cache nhận bản lưu được backend xác nhận ngay, bỏ qua GET cũ về muộn và giữ dữ liệu khi refresh lỗi. Form có timeout 20 giây, hủy thao tác giao diện khi đổi tài khoản/sách hoặc bị đóng và không hiện kết quả của ngữ cảnh cũ. Các panel giỏ/đơn/hỗ trợ thay thế form đánh giá để tránh chồng thao tác.
- POST từ chối ID do client gửi, tránh ghi đè một rate khác. POST/PUT khóa bản ghi tài khoản lấy từ JWT trong transaction trước khi tìm/lưu để các yêu cầu đồng thời của cùng owner được xử lý tuần tự; kiểm tra chủ sở hữu và sản phẩm của order detail nếu có. POST lặp cập nhật rate gần nhất của cùng tài khoản/sách.
- Cột `rates.comment` trước đây chỉ có 255 ký tự dù form/API nhận 2.000. Entity và migration [06-review-comment-length.sql](../db/06-review-comment-length.sql) đã đồng bộ giới hạn. Migration đã chạy trên MySQL local `book`: cột thành `VARCHAR(2000)`, vẫn giữ đủ 3 rate cũ. Cần áp dụng migration cho database triển khai trước khi dùng giới hạn mới; chưa chạy trên production.

Kết quả: **481 test backend, 27 test ChromeHeadless** của widget/form/ReviewService đều đạt; **production build Angular thành công**. Backend khởi động bằng các class vừa biên dịch và kết nối MySQL local. Tám bước API liên tiếp đều đạt: đọc Muôn Kiếp Nhân Sinh (5.0/5 từ rate thật), hỏi “mấy sao”, làm rõ đọc/viết, trả lời “xem”, khách yêu cầu viết được hướng dẫn đăng nhập, tên không tồn tại, “xem” vẫn không lấy cuốn cũ, rồi chọn một tên sách khác chưa có đánh giá.

Kiểm tra runtime dùng `AI_PROVIDER=local`, chưa đánh giá Gemini trực tiếp. Không tạo/sửa/xóa nhận xét trong lượt kiểm tra dữ liệu thật; lưu/cập nhật, kiểm tra quyền, timeout và đổi ngữ cảnh được xác minh bằng test tự động. Bộ test storefront mặc định còn lỗi compile ở các spec cũ; 27 test trên được chạy qua entry riêng. Build còn cảnh báo dung lượng bundle và stylesheet trang chi tiết sách.

## Chất lượng tư vấn sau kiểm tra UI — 08/10/2026

Đã hoàn thiện lần lượt năm phần: giữ/cập nhật đúng điều kiện qua nhiều lượt; kiểm tra dạng tác phẩm; cưỡng chế điều kiện bắt buộc từ lời khách; mở rộng chủ đề chỉ sau khi khách cho phép; giải thích lựa chọn và thiếu dữ liệu theo hồ sơ đã duyệt. Không dùng phủ định làm bằng chứng tích cực hoặc mất số cuốn vì câu giới hạn tiền. Người nhận và ngân sách vẫn tùy chọn; có nhu cầu cụ thể thì tư vấn ngay.

259 kiểm thử trong ChatToolsTest, ChatServiceTest và ChatRecommendationIntakeTest đạt. 12 tình huống cuối được thử trực tiếp qua chatbot local; xem [kết quả và ảnh](chatbot-evaluation/archive/2026-09-10/recommendation-improvements-2026-10-08.md). Chưa kiểm tra Gemini thật trong lượt này.

## Tư vấn gọn và tự nhiên — 09/10/2026

Mặc định tối đa 3 cuốn, ưu tiên mức khớp với các mong muốn đọc đã nêu. Số lượng cụ thể và tổng ngân sách vẫn được giữ. Phần tư vấn bỏ lời giải thích và giá giới hạn lặp lại dưới từng cuốn; dùng nội dung đã duyệt và thông tin đúng với nhu cầu, ghi rõ điểm chưa đủ căn cứ. Kiểm tra trực tiếp qua UI local được ghi trong [báo cáo](chatbot-evaluation/archive/2026-09-10/shortlist-and-voice-2026-10-09.md).

## Mua ngay và địa chỉ giao hàng trong chat — 12/10/2026

Thẻ sách của phản hồi hiện tại có nút Mua ngay ngay cạnh Thêm giỏ khi số lượng còn hợp lệ. Nút dùng chung luồng BUY_NOW đã có, giữ đúng sách/số lượng và không thêm vào giỏ trước khi đặt. Không tạo nút trùng nếu server đã trả BUY_NOW; không mở thao tác cũ từ lịch sử hoặc tạo lựa chọn mua cho sách hết hàng. Backend tiếp tục kiểm tra giá, tồn kho và yêu cầu xác nhận trước khi tạo đơn.

Phần đặt hàng chỉ có một khối Địa chỉ giao hàng. Địa chỉ mặc định được đưa lên đầu và chọn sẵn; khi không có mặc định thì dùng địa chỉ đầu tiên. Ô chọn chỉ hiện nhãn/người nhận và dấu Mặc định, thông tin người nhận, số điện thoại và địa chỉ đầy đủ hiện ngay bên dưới. Đổi lựa chọn cập nhật cả nội dung hiển thị lẫn thông tin gửi lập báo giá. Nhập địa chỉ khác hoặc Sửa cho đơn này mở các ô nhập ngay trong khối này; chỉnh sửa chỉ áp dụng cho đơn đang chuẩn bị, không ghi đè sổ địa chỉ. Không có địa chỉ đã lưu thì hiện ô nhập ngay. Đóng/đổi tài khoản xóa lựa chọn và dữ liệu form như trước.

Kiểm thử giới hạn ở hai component chatbot/chat-commerce, tập trung vào nút Mua ngay, địa chỉ mặc định, đổi địa chỉ, sửa địa chỉ và các bước xác nhận liên quan; không chạy lại bộ backend.

## Rút gọn bước mua trong chat — 12/10/2026

Mua ngay mở một biểu mẫu có thể sửa liên tục: sách/số lượng, một khối địa chỉ, thanh toán, mã giảm giá và tổng tiền. Bỏ nút Xem báo giá trong luồng bình thường. Thay đổi số lượng cập nhật tiền sách/phí giao/tổng dự kiến ngay trên máy; sau 250 ms hệ thống tự kiểm tra lại giá, tồn kho, phí giao và mã giảm giá. Các request quote được gửi nối tiếp và gộp thay đổi để kết quả cũ không thay thế lựa chọn mới. Nút Xác nhận đặt hàng chỉ bật khi quote khớp thông tin hiện tại và còn hạn. Tổng dự kiến chưa trừ mã đang kiểm tra; mức giảm hiện sau khi server xác nhận.

Địa chỉ mặc định được chọn trước. Đổi số lượng, địa chỉ hoặc thanh toán giữ mã giảm giá để server kiểm tra lại điều kiện. Có thể nhắn lệnh rõ ràng như “đổi thành 2 cuốn”, “giao về địa chỉ công ty”, “thanh toán chuyển khoản”, “mã: READ10”. Câu hỏi hoặc câu phủ định không tự sửa đơn. Nội dung chỉnh đơn, địa chỉ và điện thoại không được đưa vào lịch sử/prompt chat. Xác nhận tạo đơn vẫn là nút riêng.

Thêm giỏ từ thẻ hiện hành và đổi số lượng trực tiếp không cần xác nhận lại; có Hoàn tác cho thay đổi vừa được server xác nhận, chỉ khi cùng chủ giỏ và giỏ chưa thay đổi tiếp. Xóa sách/xóa giỏ vẫn hỏi lại. Mua ngay không phụ thuộc revision giỏ và không thêm sách vào giỏ. Cảnh báo thiếu tồn kho chỉ hiện sau khi đã tải catalog.

Thu nhỏ chat giữ component và bản nháp trong bộ nhớ. Khi khách chọn Đăng nhập và tiếp tục, chỉ ID sách/số lượng hoặc lựa chọn mua giỏ được giữ trong sessionStorage tối đa 15 phút và dùng một lần. Sau đăng nhập tải lại catalog/giỏ/địa chỉ của tài khoản hiện tại trước khi tiếp tục; dữ liệu giao hàng không đi qua cơ chế này. Đóng biểu mẫu hoặc đổi tài khoản xóa dữ liệu riêng tư của bản nháp.

Quote timeout sau 15 giây, xác nhận sau 20 giây. Kết quả xác nhận chưa rõ vẫn khóa chỉnh sửa/lập quote mới và kiểm tra lại bằng cùng token. Không tự tạo đơn hoặc tự xác nhận khi giá đổi. Backend và bảo vệ chủ đơn/idempotency hiện có được giữ nguyên.

Khách gõ yêu cầu mua một sách cũng giữ được lựa chọn qua đăng nhập: prepareCheckout xác định và kiểm số lượng/tồn kho như trước, trả sách công khai cùng productId/quantity trong LOGIN. Widget chỉ chuyển lựa chọn từ action PURCHASE/prepareCheckout hiện hành, chặn bấm lặp và đổi tài khoản giữa các bước; không lưu lại nguyên câu hỏi. LOGIN cho đánh giá/hỗ trợ/đơn hàng giữ nguyên.

Khi nhập địa chỉ mới, khách có thể tích Lưu địa chỉ này khi đặt hàng thành công. Tùy chọn mặc định tắt; chỉ thêm vào sổ sau khi backend xác nhận đã tạo đơn, dùng thông tin từ quote được xác nhận. Không ghi đè địa chỉ cũ, không lưu trước bước xác nhận. Nếu lưu sổ thất bại, vẫn giữ đơn đã tạo và hướng dẫn lưu lại địa chỉ, không đặt thêm đơn.

Xác minh cuối: 65 ca ChromeHeadless của ba phần mua hàng và một ca backend mới giữ lựa chọn guest đều đạt; backend target-runtime và Angular dev build biên dịch thành công. UI thật xác minh tổng đổi ngay 90.520đ → 161.040đ khi chọn 2 cuốn, miễn phí giao ở 5 cuốn (352.600đ), thu nhỏ giữ bản nháp, Thêm giỏ/Hoàn tác trả đúng giỏ ban đầu, cả nút Mua ngay và câu mua 2 cuốn trước đăng nhập đều tự tiếp tục đúng lựa chọn. Không tạo đơn/thanh toán/lưu địa chỉ thật trong lượt UI. Chỉ hiển thị biểu mẫu sau khi dữ liệu sách đã tải đủ để tránh chớp tên/giá chưa có trong lúc khôi phục.

## Thẻ đơn hàng trực quan — cập nhật 13/10/2026

- Tra đơn trong chatbot hiển thị thẻ có ảnh bìa, tên sách, số lượng, đơn giá đã lưu, thành tiền, ngày đặt và trạng thái. Thẻ lấy dữ liệu mới bằng OrderService/getOrder; backend tiếp tục kiểm tra chủ đơn từ JWT. Chỉ hydrate các mã đơn cụ thể trong kết quả getMyOrders/getOrderDetail đã hoàn tất. Nếu tải lỗi, giữ câu trả lời dạng chữ và cho tải lại. Thông tin thẻ không đưa vào prompt hoặc cache hội thoại.
- Nút trên từng thẻ mở chi tiết, mua lại, xem lại trước khi hủy hoặc đổi trả theo trạng thái hiện tại. Các thao tác thay đổi vẫn đi qua component xác nhận dùng chung, không tự hủy hoặc tự tạo đơn.
- Xác nhận hủy hiển thị ảnh sách từ chi tiết đơn khi khớp tên/số lượng với preview có thẩm quyền. Lỗi ảnh hoặc lỗi tải phần bổ sung không chặn preview hủy; không thay đổi điều kiện hủy, bảo vệ đổi tài khoản hoặc bước kiểm tra kết quả chưa rõ.
- Danh sách đơn có bộ lọc Tất cả/Chờ xử lý/Đang giao/Hoàn thành/Đã hủy, số lượng đơn, tìm mã đơn/tên sách không phân biệt dấu. Thẻ gom sách và tổng tiền cạnh nút thao tác; địa chỉ và bảng phí nằm trong chi tiết. Dữ liệu riêng được xóa và response cũ bị bỏ qua khi đổi tài khoản.
- OrderItemsComponent dùng chung ở danh sách, chi tiết, thẻ chat và preview hủy; ảnh giữ đúng tỷ lệ và có biểu tượng khi thiếu/lỗi. Mua lại có ảnh sách bên cạnh số lượng. Giao diện dùng token màu hiện có; không thêm thư viện hoặc catalog tĩnh.
- Mobile: thẻ xếp lại tên/giá theo chiều dọc, bộ lọc cuộn ngang trong vùng riêng; nút chat/đổi màu tránh vùng thành tiền và thao tác sau khi danh sách tải hoặc thay đổi bộ lọc.
- Kiểm chứng: 24 kiểm thử tập trung vào ChatOrders, hủy đơn, bộ lọc danh sách và FabCollision đạt. Build production đạt; còn cảnh báo kích thước bundle/style trang sản phẩm có sẵn. Đã xem dữ liệu thật đơn #48 trên desktop 1366×900 và mobile 390×844, tra đơn trong chat, tìm tên sách, trạng thái không có kết quả và chi tiết đơn. Đơn #48 vốn đã hủy; không gửi yêu cầu hủy, tạo đơn, thêm giỏ hoặc thanh toán thật trong lượt này. Preview hủy đủ điều kiện được kiểm tra bằng fixture trong component tests.

## Hoàn thiện theo rà soát trải nghiệm — 13/10/2026

- Danh mục không dùng hiệu ứng ẩn thẻ khi lọc/sắp xếp. Reveal dùng chuyển động nhẹ, nội dung luôn nhìn thấy; checkout không chuyển động vùng nút cố định.
- Ý định mua qua đăng nhập dùng chung ChatCheckoutService, tách surface `web`/`chat` để widget không lấy nhầm lựa chọn của website. Chỉ mã sách, lượng, loại thao tác và đường dẫn nội bộ được lưu trong sessionStorage, dùng một lần trong 15 phút. Website đăng nhập xong lấy catalog mới, mở checkout hoặc đồng bộ giỏ đúng lựa chọn; không tự tạo đơn.
- CartService giữ nháp Mua ngay theo tài khoản trong 30 phút, chỉ mã sách/lượng. Checkout khôi phục giá/tồn từ ProductService; không chuyển sang mua sản phẩm khác trong giỏ nếu sách đã hết hàng. Đi từ giỏ sang checkout xóa lựa chọn Mua ngay cũ để tránh lẫn hai nguồn.
- Checkout điền sẵn tên/điện thoại hồ sơ, chọn địa chỉ mặc định thành một thẻ, bấm Thay đổi để chọn/nhập địa chỉ khác. Nhãn địa chỉ chỉ hiện khi chọn lưu; không mặc định lưu. Có bộ chỉnh lượng, tiền và ngưỡng miễn phí giao cập nhật ngay. Voucher thu gọn, thay lượng sẽ bỏ voucher cũ và cần kiểm tra lại; chặn gửi khi voucher còn xử lý. Tổng tiền và nút xác nhận cố định ở đáy mobile, FAB tránh cả vùng này.
- Khi nhận phản hồi prepareCheckout đã hoàn tất, chat tự mở bản xem trước với sách/lượng backend đã xác định. Không mở từ phản hồi đang stream hoặc lịch sử được phục hồi. Nút cuối Xác nhận đặt hàng vẫn bắt buộc.
- Câu hỏi ví dụ/áp dụng có nhánh riêng, giữ cuốn đang nói tới. AI chỉ dùng nguyên tắc có evidence khớp dữ kiện duyệt, minh họa tách nhãn “tự xây dựng, không phải trích sách”. Khi AI lỗi, chỉ minh họa nguyên tắc giao tiếp đã được nguồn xác nhận hoặc nói rõ thiếu căn cứ. Câu hỏi gợi ý sách mới vẫn đi tìm catalog. Follow-up chỉ hiện trường được hỏi, không tự lặp danh sách mục tiêu/đối tượng/phong cách; UI dùng tham chiếu sách gọn và gợi ý theo ngữ cảnh.
- Mobile: tên đầy đủ vẫn có aria-label/title, giao diện dùng icon tài khoản; yêu thích/báo có hàng chuyển vào menu. Ẩn nút đổi phong cách trên sản phẩm/giỏ/checkout/chi tiết đơn. Chat đang mở chỉ có nút đóng trong header.
- Chi tiết đơn có liên kết hỗ trợ mang orderId sang bản xem trước tại Liên hệ; người dùng vẫn phải nhập vấn đề và xác nhận gửi. Mua lại cho chọn những sách còn bán/còn hàng; giữ nguyên bước xác nhận thêm giỏ và bảo vệ kết quả chưa rõ.
- Danh sách đơn và phần tóm tắt hồ sơ dùng `GET /api/orders/me?page=0&size=20&status=-1&q=...`. API lấy owner từ JWT, ROLE_USER; size 1–50, status -1 hoặc 0–3, query tối đa 100 ký tự. Trả `{items,total,counts,totalSpent,page,size}`. `counts` tính trên mọi đơn của tài khoản, tìm/lọc áp dụng trước phân trang. Sách của các đơn được tải theo lô, không một HTTP request cho mỗi đơn. Địa chỉ, token và entity User không nằm trong response danh sách.
- Giỏ chặn lượng vượt tồn/99; thêm từ thẻ sách/chi tiết chỉ báo thành công sau `flush()` xác nhận máy chủ. Không tự lặp thêm khi kết quả lưu chưa rõ.
