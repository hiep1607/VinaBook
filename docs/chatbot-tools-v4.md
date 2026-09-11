# Chatbot tools v4

## Luồng đang chạy

Angular gửi message, sessionId, requestId, lịch sử công khai, product ID của trang đang mở và snapshot giỏ (chỉ ID/số lượng) tới `/api/chat` hoặc `/api/chat/stream`.

Khi AI đã được cấu hình, ChatService giao cho ChatOrchestrator. AI tạo kế hoạch JSON từ danh sách chức năng do ChatToolRegistry cung cấp. Registry kiểm tra toàn bộ tên chức năng, tên tham số, kiểu, miền giá trị và số bước trước khi thực thi. Tối đa 3 bước; các bước trước bước cuối chỉ được đọc sản phẩm. Kết quả trống hoặc cần thông tin dừng kế hoạch. Không có vòng lặp agent không giới hạn.

Đây là tool dispatch ở tầng ứng dụng qua JSON, chưa dùng native function calling của từng provider. AiGateway hiện có được giữ để không buộc nâng Spring Boot hoặc thay cấu hình model. Không thay API key hay thêm dependency Spring AI.

ChatBookNarrator có thể gọi model thêm một lần cho tìm kiếm/chi tiết/so sánh: giải thích phải kèm bằng chứng trích nguyên văn mô tả. ID phải thuộc kết quả; máy chủ giữ nguyên giá, rating, tồn kho, thứ tự và hành động. Nếu giải thích sai cấu trúc hoặc không qua kiểm tra, trả phần dữ liệu backend. Bộ lọc văn bản không phải bảo đảm tuyệt đối về ngữ nghĩa; cần tiếp tục đánh giá groundedness.

## Chức năng

| Tool | Trách nhiệm |
|---|---|
| searchProducts | Tìm trên catalog, lọc min/max giá, rating, tác giả, danh mục; sắp xếp trước khi giới hạn |
| getProducts | Chi tiết/so sánh theo tên, ID đã biết hoặc tham chiếu |
| updateSelection | Bỏ một cuốn khỏi danh sách đang cân nhắc; không thay giỏ |
| getCart | Đọc giỏ server khi đăng nhập, snapshot thiết bị cho khách; giá tại backend |
| prepareCartChange | Đề xuất thêm/xóa/đổi số lượng; giao diện xác nhận rồi gọi CartService |
| prepareClearCart | Đề xuất xóa cả giỏ, xác nhận rồi đồng bộ server |
| prepareCancelOrder | Kiểm tra đơn của chủ tài khoản; xác nhận rồi gọi nghiệp vụ hủy |
| prepareCheckout | Chuẩn bị bản nháp với sản phẩm/số lượng đã kiểm tra |
| getMyOrders / getOrderDetail | Dùng ChatOrderSupportService, owner lấy từ đăng nhập |
| getPolicy | Chính sách có sẵn trong ChatPolicyService |
| respond | Chào hỏi, làm rõ, thông báo chưa hỗ trợ |

## Trạng thái và xác nhận

- State phân biệt displayed, shortlist, focus, toolSearch và lịch sử điều phối ngắn. Scope theo tài khoản + session, TTL 30 phút, tối đa 2.000 phiên, hiện lưu trong bộ nhớ một backend. Restart làm mất state; chỉ phục hồi được danh sách công khai client gửi, không đầy đủ bộ lọc. Chưa có JDBC/Redis memory.
- `requestId` chống thực hiện lặp lại lượt gần nhất khi SSE chuyển sang JSON. Đơn hàng không dùng cache câu trả lời tra cứu.
- `execution` trong response có tool/status: COMPLETED, NEEDS_INPUT, AWAITING_CONFIRMATION, FAILED. COMPLETED của tool đọc chỉ nghĩa đã đọc; không phải giao dịch thành công.
- CartService đồng bộ giỏ tài khoản với server, lấy JWT làm chủ sở hữu và revision để chống ghi đè. ChatService thay snapshot của client bằng giỏ server khi đã đăng nhập. Giỏ khách vẫn theo thiết bị. Chỉ tiêu thụ proposal khi có biên nhận lưu; giá/tồn kho kiểm tra lại lúc checkout.
- Backend quote/confirm hiện có vẫn là nguồn báo giá và tạo đơn. Chat không đăng ký createOrder trực tiếp. Chỉ API confirm với token hợp lệ của owner được tạo đơn. Frontend vẫn quản lý biểu mẫu nhập địa chỉ và đưa dữ liệu riêng thẳng tới checkout, không gửi cho model. Thanh toán chat hiện vẫn là COD theo service hiện có.
- SSE phát câu trả lời đã kiểm tra, không phát token thô của model; thời gian chờ 90 giây cho bước lập kế hoạch và giải thích.
- Log điều phối chứa messageId, tên tool, status, thời gian; không ghi prompt, API key, địa chỉ hoặc dữ liệu đơn. Analytics cũ vẫn ghi metric tổng hợp.

## Thêm chức năng

1. Viết một Spring bean `implements ChatTool` (cùng package `vn.fs.service` khi dùng State nội bộ), hoặc một `@Bean ChatTool` riêng.
2. Khai báo tên duy nhất, mô tả rõ lúc nên dùng và `parameters()` với kiểu string/integer/number, enum và giới hạn. Registry tự đưa khai báo cho model và chặn tham số ngoài schema.
3. Trong `execute`, gọi service nghiệp vụ hiện có. Owner luôn lấy từ phiên server; tuyệt đối không nhận userId/email do model chọn. Chức năng ghi dữ liệu phải qua luồng xác nhận, kiểm tra lại dữ liệu và chống thực hiện trùng của nghiệp vụ, không coi câu AI nói là xác nhận.
4. Trả `Result` với dữ liệu, status và actions rõ ràng. Nếu thêm action UI mới, bổ sung contract ChatAction, handler và test xác nhận/lỗi ở Angular. Tool chỉ đọc với action hiện có không cần sửa bộ điều phối hay UI.
5. Thêm test schema, quyền, tham chiếu, lỗi và replay. Chạy kiểm thử backend/frontend và hội thoại thật trước khi công bố.

Muốn cho chức năng mới làm bước trung gian trong kế hoạch, phải đánh giá rồi bổ sung vào danh sách cho phép đọc của orchestrator. Mặc định chức năng mới chỉ được làm bước cuối.

## Kiểm chứng

Test `ChatToolsTest` dùng model fixture để kiểm tra hành vi nghiệp vụ, không dùng làm điểm chất lượng LLM. Test `chatbot-tools.spec.ts` kiểm tra xác nhận, số lượng, sửa/xóa, đổi tài khoản, lỗi storage và page context. Bộ cũ vẫn kiểm thử checkout idempotence, ownership và hồi quy hội thoại local.

Hội thoại Gemini thật lưu ở work/chat-tools-live-results.json. Không thay cho chấm lại bộ 100/200 câu chuẩn: chưa có điểm chất lượng mới từ thay đổi này.

## Hỏi lại khi thiếu thông tin (2026-09-11)

`respond(kind=clarify)` nhận `topic`, `missing` và các thông tin công khai khách đã cung cấp (`subject`, `purpose`, `recipient`, `level`, `budget`). `scope=new` bắt đầu nhu cầu mới; `continue` giữ thông tin để bổ sung. Backend hỏi một trường còn thiếu mỗi lượt, không hỏi lại trường đã có. Chủ đề đã rõ có thể được tư vấn ngay; ngân sách/tuổi/trình độ không mặc định là điều kiện bắt buộc.

Memory theo tài khoản/phiên, hết hạn sau 30 phút, giữ `pendingQuestion` cùng `clarificationFacts` cho planner nối câu trả lời ngắn vào yêu cầu trước. Sau khi xử lý xong, các trường chờ được xóa. Không dùng các trường này để chứa địa chỉ, điện thoại hay dữ liệu đơn hàng. Lỗi provider giữ câu hỏi đang chờ và thông báo lỗi xử lý, không quy thành khách cung cấp thiếu thông tin.

So sánh mới xác định một sách phải hỏi tên sách thứ hai. Mua/thêm giỏ với tên khớp nhiều sách trả danh sách ứng viên thực tế, chưa tạo action; khách chọn tên/thứ tự rồi mới nhận đề xuất xác nhận. Khi kết quả tìm rỗng, hỏi đổi điều kiện thay vì yêu cầu chọn một cuốn không tồn tại.

Tool mới tiếp tục dùng `Result.status=NEEDS_INPUT` và câu hỏi cụ thể trong `answer`; orchestrator tự chuyển câu hỏi vào context lượt sau. Không cần thêm luồng UI cho câu hỏi văn bản. Các test fixture kiểm tra trạng thái và nghiệp vụ; nhận diện nhu cầu bằng model vẫn cần kiểm chứng bằng hội thoại thật.

Kế hoạch sai JSON/schema được yêu cầu tạo lại tối đa một lần, trước khi bất kỳ tool nào chạy. Kế hoạch sửa vẫn phải qua toàn bộ kiểm tra. Không thử lại thao tác đã thực thi. Kiểm chứng: 220 test backend qua; hội thoại Gemini thật xác nhận hỏi tên sách thứ hai rồi trả bảng đúng hai sách, giữ ngân sách quà tặng, số lượng thêm giỏ và nối bộ lọc giá sau tìm rỗng. Chưa chạy lại toàn bộ bộ 100 câu để chấm điểm mới.

## Giới hạn triển khai

Giỏ đã đồng bộ server; hủy đơn COD chưa thanh toán, còn chờ xử lý đã hỗ trợ. Chưa có memory/bản nháp bền vững đa instance, RAG vector hoặc thanh toán online ngay trong hội thoại. Các chức năng này có thể thêm qua contract trên. Nếu AI không cấu hình, ChatService còn dùng nhánh local cũ để giữ khả năng hỗ trợ cơ bản; không tự tắt cấu hình AI khi model lỗi.
