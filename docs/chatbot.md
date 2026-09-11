> Cập nhật 10/09/2026: luồng AI hiện dùng bộ điều phối tools v4. Xem [kiến trúc và cách thêm chức năng](chatbot-tools-v4.md). Phần bên dưới mô tả các lớp và luồng nền tảng trước v4.

# Trợ lý VinaBook

## Dialogue v3 — kiến trúc đang dùng (2026-09-10)

Phần này thay thế pipeline V2 trong các mục lịch sử bên dưới. Không phải vector database hoặc GraphRAG.

- `ChatService`: kiểm tra phạm vi/quyền → đọc trạng thái → giải sách/thao tác → lọc catalog → tối đa một lần gọi `ChatDialogueInterpreter` → kiểm tra ID → dựng nội dung và thẻ từ cùng sản phẩm. Planner/grounding V2 không còn được gọi trong runtime này.
- `ChatDialogueMemory`: RAM tối đa 2.000 phiên, hết hạn sau 30 phút không hoạt động; khóa gồm principal xác thực và session ID. Giữ mục tiêu, trình độ, tiêu chí, shortlist, danh sách hiển thị, cuốn đang tập trung riêng biệt. Không lưu địa chỉ, phản hồi đơn hàng hoặc toàn transcript. Restart backend làm mất bộ nhớ này.
- Frontend giữ session ID theo tài khoản trong sessionStorage, không tạo phiên mới chỉ vì reload. Nút Mới reset phiên; đổi tài khoản hủy request đang chạy và bỏ bản nháp/thông tin riêng khỏi màn hình.
- `ChatCatalogQuery` lọc toàn catalog hoạt động trước khi sort/limit. Giá sau giảm, tồn kho, tác giả và rating lấy từ cửa hàng; không coi thiếu rating là 0 sao. `ChatMoney` không coi năm trong tên sách hay số lượng cuốn là ngân sách.
- Mỗi lượt AI nhận tối đa 80 ứng viên, mô tả tối đa 1.100 ký tự/cuốn, lịch sử công khai có giới hạn. ID không hợp lệ bị từ chối. Giá/tên sách được server render. Phản hồi sai schema/ID dùng thông tin có thể đối chiếu, không khẳng định là Gemini mất kết nối.
- `requestId` giữ nguyên khi SSE chuyển sang JSON; retry yêu cầu gần nhất không thực hiện lại chuyển trạng thái (ví dụ loại thêm sách). Tra đơn luôn đọc theo tài khoản, không cache câu trả lời đơn hàng trong bộ nhớ hội thoại.
- Mua bằng lời nói mở bản nháp khi xác định một cuốn. Lựa chọn sách công khai của khách có thể tiếp tục sau đăng nhập trong cùng trang; không chuyển bản nháp/địa chỉ từ A sang B. Không có địa chỉ thì hỏi bổ sung lịch sự; nhiều địa chỉ thì hỏi chọn; một địa chỉ thì tự lập báo giá để xem lại.
- Shipping chat xử lý cục bộ và đánh dấu private, không gửi AI. “ok” không tạo đơn; nút hoặc tin “xác nhận đặt hàng” sau báo giá mới chốt. Đổi sách/thông tin phải báo giá lại. Lưu địa chỉ opt-in, chỉ lưu sau thành công.
- Checkout kiểm tra JWT, chủ báo giá, hạn 15 phút, giá/tồn kho và ghi đơn trong transaction; payment reference chống retry cùng đơn. Hiện hỗ trợ một đầu sách/bản nháp và COD; địa chỉ lưu theo tài khoản trên trình duyệt. Chưa có giỏ nhiều đầu sách, đồng bộ địa chỉ đa thiết bị hoặc kho báo giá dùng chung nhiều backend.
- Khung chat gần hết chiều cao viewport; bảng so sánh dùng nội suy Angular (không HTML của AI), cuộn ngang trên màn hẹp; cập nhật stream theo nhóm nhỏ thay vì trì hoãn từng từ.

Nguồn kiến thức chính vẫn là mô tả catalog, chưa có toàn văn/chỉ mục chương. Không hứa trả lời chính xác mọi câu hỏi sâu khi thiếu nguồn. Test tự động kiểm tra luồng/an toàn không tương đương điểm chất lượng tư vấn với model thật.

Luồng chốt đơn nhận cả “đặt đơn giúp t”, “đặt hàng giúp mình”; khi xác định một sách, frontend mở bản nháp trực tiếp. Form giải thích mục đích thông tin giao hàng và yêu cầu kiểm tra lại trước xác nhận. Checkbox lưu địa chỉ mặc định không chọn; chỉ lưu sau khi đặt thành công, theo tài khoản trên trình duyệt, không gửi cho AI. Khi yêu cầu mua cuốn khác trong bản nháp chưa xác nhận, truy vấn lại sách; không thay đơn đang xử lý hoặc chưa rõ kết quả.

## Lịch sử dài và lỗi transport (2026-09-08)

Backend nhận tối đa 8000 ký tự mỗi tin lịch sử (10 tin/request). Frontend gửi tối đa 8 tin hoàn tất gần nhất, 8000 ký tự/tin và 24000 ký tự tổng; rút gọn bản gửi đi, không sửa nội dung hiển thị hoặc ID sách. Không cắt đôi surrogate UTF-16.

HTTP 400/413, 401/403, 429 và lỗi kết nối có thông báo riêng. SSE nhận lỗi xác định thì không gửi lại qua JSON. Khi AI lỗi, câu trả lời local nêu trạng thái giới hạn lượt, timeout hoặc AI tạm thời không trả lời được. Kiểm thử: 90 test backend và 3 test frontend đạt; API đang chạy đã nhận lịch sử giả lập 8000 ký tự với câu hỏi POLICY xử lý local. Không gọi Gemini trong kiểm thử này.

## Conversational RAG (2026-09-08)

Pipeline hiện tại: giải tham chiếu hội thoại → lập kế hoạch → truy xuất catalog có ràng buộc → AI chọn lại ứng viên → dựng câu trả lời/thẻ từ cùng ID. Đây là nâng cấp RAG hội thoại trên bộ tìm từ khóa/ngữ nghĩa mở rộng hiện có, chưa phải vector database hay GraphRAG.

- `ChatConversationContext` viết lại câu hỏi có “cuốn thứ hai”, “cuốn cuối”, “tóm tắt thêm” bằng tên sách xác minh từ catalog, theo thứ tự ID thẻ của lượt trước. Nhiều sách mà thiếu tham chiếu rõ thì hỏi lại; policy/đơn hàng không bị kéo vào ngữ cảnh sách.
- Câu tìm sách mới đặt lại tiêu chí; các lượt hỏi sách khác giữ chủ đề/ngân sách qua nhiều lượt user trong lịch sử gửi lên (không có bộ nhớ dài hạn phía server).
- Bộ lọc category/author do người dùng nêu không tự nới khi thiếu kết quả. Hỏi sách khác trong một chủ đề không tự bù bằng bestseller. “Rẻ hơn” phải có giá thấp hơn cuốn rẻ nhất trong nhóm trước.
- AI vẫn nhận nhóm ứng viên rộng để chọn; giới hạn số gợi ý sau khi chọn, trước khi dựng nội dung và thẻ. Hỏi chi tiết không chèn giá/tồn kho vào bài phân tích; quick replies chuyển sang nội dung, độ khó đọc và giá.
- 89 test chatbot đạt, gồm thứ tự tham chiếu, lịch sử nhiều lượt, ràng buộc giá, thiếu ngữ cảnh, đúng tên sách và thẻ khớp nội dung. Kiểm thử dùng AI mock; chưa đo chất lượng với provider thật. Nguồn kiến thức vẫn là mô tả catalog; không có toàn văn sách để chứng minh mọi chi tiết.

## Sửa tư vấn nội dung và thẻ sách (2026-09-08)

Bổ sung hồi quy câu “nói rõ hơn về cuốn luật lao động thực hành”: nhận tên sách trước khi lập kế hoạch tìm chủ đề; yêu cầu giải thích được chuyển sang PRODUCT_INFO. UNKNOWN không đính kèm thẻ ứng viên. 85 test chatbot đạt. Backend đã nạp lại với cấu hình Gemini hiện có và validate schema. Kiểm thử câu hỏi qua AI thật chưa thực hiện do cơ chế duyệt tự động chặn việc gửi câu hỏi/catalog tới provider ngoài.

- Câu hỏi nội dung về Nhà Giả Kim không còn bị từ “giả” trong tên sách đưa vào handler giá. Handler giá nhận các cụm hỏi giá sau khi loại tên sách khỏi câu hỏi.
- Ở chế độ local hoặc AI lỗi, danh sách thẻ và action được giới hạn theo đúng các sách xuất hiện trong câu trả lời (tối đa 5 gợi ý, 1 sách khi hỏi chi tiết).
- “Cuốn này/sách này” dùng product ID của lượt trước khi chỉ có một sách; nhiều sách thì hỏi lại tên thay vì chọn ngẫu nhiên.
- Prompt chi tiết nhận mô tả tối đa 6000 ký tự và cho phép giải thích nội dung dài hơn. Phân biệt kiến thức văn học phổ biến với dữ kiện catalog; không tự tạo trích dẫn, chương, số trang hay chi tiết ấn bản. Local nói rõ giới hạn dữ liệu.
- Kiểm thử: 84 test chatbot đạt (intent, retrieval/grounding, service), gồm hồi quy tên sách bị nhầm thành giá, thẻ dư và ngữ cảnh một/nhiều cuốn. Provider được mock trong test; chưa xác minh chất lượng câu trả lời với dịch vụ AI thật cho bản sửa này.

Chatbot V2 dùng backend làm nguồn nghiệp vụ duy nhất. Angular không biết API key, không chứa bản sao FAQ/search rule và không phụ thuộc trực tiếp vào cấu trúc response của bất kỳ hãng AI nào.

## Chế độ hoạt động

| `AI_PROVIDER` | API backend sử dụng | `AI_BASE_URL` |
|---|---|---|
| `local` | Bộ trả lời nội bộ, không gọi AI ngoài | Không cần |
| `openai` | OpenAI Responses API | Tùy chọn, mặc định `https://api.openai.com/v1` |
| `openai-compatible` | `POST /chat/completions` | Bắt buộc |
| `gemini` | Gemini `generateContent` | Tùy chọn, mặc định Google Generative Language API |
| `anthropic` | Anthropic Messages API | Tùy chọn, mặc định `https://api.anthropic.com/v1` |

`openai-compatible` dùng được với gateway hoặc dịch vụ có contract Chat Completions tương thích, ví dụ Ollama, vLLM, Groq, OpenRouter hoặc LiteLLM. Khả năng tương thích cụ thể phụ thuộc dịch vụ và model.

## Cấu hình

Mặc định không cần thiết lập gì:

```powershell
$env:AI_PROVIDER = "local"
```

Ví dụ cấu hình OpenAI:

```powershell
$env:AI_PROVIDER = "openai"
$env:AI_MODEL = "ten-model-trong-tai-khoan"
$env:AI_API_KEY = "api-key-cua-ban"
```

Ví dụ đổi sang API tương thích OpenAI:

```powershell
$env:AI_PROVIDER = "openai-compatible"
$env:AI_MODEL = "ten-model"
$env:AI_BASE_URL = "http://localhost:11434/v1"
$env:AI_API_KEY = "gia-tri-do-dich-vu-yeu-cau"
```

Ví dụ dùng OpenRouter với model miễn phí:

```powershell
$env:AI_PROVIDER = "openai-compatible"
$env:AI_BASE_URL = "https://openrouter.ai/api/v1"
$env:AI_MODEL = "openrouter/free"
$env:AI_API_KEY = "sk-or-v1-..."
```

`openrouter/free` tự chọn một model miễn phí đang khả dụng. Nếu cần cố định model, thay bằng model ID có hậu tố `:free` trên OpenRouter. Free model phù hợp cho demo và phát triển; hạn mức, tốc độ và khả năng sẵn sàng phụ thuộc từng model/provider. Không đưa API key vào frontend hoặc commit vào Git.

Ví dụ Gemini hoặc Anthropic chỉ cần đổi `AI_PROVIDER`, `AI_MODEL` và `AI_API_KEY`. Khởi động lại backend sau khi đổi biến môi trường.

### Thiết lập nhanh Gemini trên Windows

Workspace đã có sẵn `VinaBookStore/.env.local` bị Git ignore. Mở file đó và chỉ thay dòng:

```text
AI_API_KEY=PASTE_GEMINI_API_KEY_HERE
```

Sau đó chạy:

```powershell
cd VinaBookStore
.\run-gemini.cmd
```

Script tự nạp `AI_PROVIDER=gemini`, model `gemini-3.1-flash-lite` và API key vào process backend. Model này được chọn từ danh sách `generateContent` khả dụng của khóa Google AI Studio mới. Script gửi một request kiểm tra rất nhỏ trước khi chạy backend, nhờ đó phát hiện ngay khóa sai, model không được cấp hoặc hết quota. Nếu clone project trên máy mới, lần chạy đầu sẽ tự tạo `.env.local` từ file mẫu. Không thêm dấu cách quanh tên biến và không chia sẻ nội dung `.env.local`.

Các biến tinh chỉnh:

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `AI_TIMEOUT_SECONDS` | `25` | Timeout cho một request AI |
| `AI_MAX_OUTPUT_TOKENS` | `600` | Giới hạn output, backend chặn trong khoảng 100 đến 2000 |
| `AI_TEMPERATURE` | `0.35` | Độ ngẫu nhiên, backend chặn trong khoảng 0 đến 1 |
| `AI_RATE_LIMIT_PER_MINUTE` | `20` | Số câu hỏi tối đa mỗi IP trong một phút |
| `AI_MAX_HISTORY_MESSAGES` | `8` | Số lượt gần nhất gửi cho provider |

Không commit API key vào `application.properties`, file Angular, GitHub hoặc ảnh chụp màn hình. Secret chỉ truyền bằng biến môi trường hay secret manager của nền tảng deploy.

## Contract

- `GET /api/chat/status`: trả provider, model và trạng thái AI; không bao giờ trả key hoặc base URL.
- `POST /api/chat`: contract JSON thông thường; nhận `message` tối đa 1000 ký tự, tối đa 10 lượt `history`, `sessionId` và tối đa 8 `contextProductIds`.
- `POST /api/chat/stream`: cùng request, trả SSE theo thứ tự `ready`, nhiều `chunk`, rồi `done`. Storefront tự fallback về JSON nếu stream không khả dụng.
- `POST /api/chat/feedback`: nhận `messageId`, `positive|negative` và comment tùy chọn tối đa 500 ký tự.
- `GET /api/chat/admin/metrics`: tổng hợp intent, fallback, latency và feedback; bắt buộc `ROLE_ADMIN`.
- Response chuẩn hóa gồm `answer`, `provider`, `model`, `fallback`, `products`, `chips`, `messageId`, `intent`, `criteria` và `actions`.

Backend dùng pipeline RAG trên catalog MySQL. Giá hiển thị được tính từ giá gốc và phần trăm giảm trong database. Nếu provider chưa cấu hình, timeout, trả JSON sai hoặc chọn ID không tồn tại, backend chuyển sang câu trả lời `local` được dựng từ chính kết quả truy xuất. Angular chỉ retry qua transport JSON và hiện trạng thái lỗi nếu backend không truy cập được; không tự bịa câu trả lời nghiệp vụ.

Khi dùng transport stream, Angular cập nhật cùng một bubble bot sau mỗi `chunk` và nhường nhịp khoảng 20ms giữa các chunk để trình duyệt vẽ hiệu ứng đang gõ. Thẻ sách, feedback và action chỉ được gắn ở event `done`, nên không nhấp nháy hoặc xuất hiện trước khi câu trả lời hoàn tất.

Widget chatbot có khung cuộn độc lập, không bị header sticky che. Khi panel mở, nút FAB nổi được ẩn để tránh một nút X trùng lặp; nút đóng nằm trong header chatbot. Người dùng có thể kéo góc panel để điều chỉnh chiều rộng và chiều cao, còn nút `Mới` sẽ xóa phiên hiện tại và bắt đầu từ lời chào mới.

## Pipeline RAG

1. `ChatIntentService` nhận diện nhanh intent, ngân sách, tác giả, category và chủ đề phổ biến.
2. `ChatQueryPlannerService` dùng model để chuyển câu hỏi phức tạp thành JSON query plan có cấu trúc. Model chỉ bổ sung intent, từ khóa và chủ đề ngữ nghĩa; ngân sách, category, author và kiểu sắp xếp chỉ trở thành bộ lọc cứng khi người dùng nêu rõ trong câu hỏi. Quy tắc này tránh để một category do AI suy đoán che mất sách tốt khi metadata catalog chưa hoàn hảo.
3. `ChatRetrievalService` tìm trên tên, tác giả, category và mô tả; mở rộng từ đồng nghĩa tiếng Việt như `pháp luật → pháp lý, luật, hiến pháp, dân sự, lao động`, chấm điểm mức liên quan và yêu cầu một tín hiệu neo đủ mạnh trước khi nhận ứng viên. Từ mở rộng chỉ hỗ trợ xếp hạng, không tự biến chủ đề gần giống thành kết quả đúng.
4. `ChatGroundingService` gửi tối đa 20 ứng viên đã xếp hạng cho model. Model chỉ trả JSON gồm `productId` và lý do; không được tự viết tên, giá hay tồn kho.
5. Backend loại mọi ID không thuộc context, lấy lại tên/giá/tồn kho từ entity `Product`, tự dựng câu trả lời và tạo card/action từ cùng một danh sách ID. Nếu model xác nhận không có kết quả trực tiếp, backend bỏ mọi phương án “gần chủ đề” mà model cố thêm để lấp danh sách.
6. Policy, đơn hàng, giá và tồn kho chính xác vẫn đi qua handler deterministic; model không được quyết định các dữ kiện này.

Một câu hỏi tìm sách thường dùng hai lượt model: một lượt lập query plan và một lượt chọn/giải thích trên context đã truy xuất. Cách này chậm hơn prompt đơn nhưng tăng recall cho câu hỏi cần suy luận và chặn việc giới thiệu sách không có trong kho. Nếu lượt tổng hợp lỗi hoặc timeout, fallback chỉ giữ sách có tín hiệu khớp trực tiếp từ câu hỏi gốc; nó không hiển thị toàn bộ ứng viên mở rộng.

Intent hiện gồm tìm sách, thông tin sản phẩm, so sánh, chính sách, hỗ trợ đơn, chào hỏi và chưa nhận diện. Bộ eval bao phủ hơn 70 trường hợp, gồm câu tiếng Việt có dấu/không dấu, truy vấn pháp luật, follow-up, ngân sách và ID do model bịa.

Thẻ sản phẩm chỉ được đính kèm khi câu hỏi có ý định tìm/gợi ý/so sánh sách, nhắc đúng tên sách, tác giả hoặc thể loại có trong catalog. Câu hỏi về giao hàng, thanh toán, đổi trả, tài khoản hay trò chuyện thông thường không tự động hiện sách. Khi khách nhắc đúng một hoặc nhiều tên sách, response chỉ kèm các sách được nhắc tới thay vì thêm sản phẩm bán chạy không liên quan.

Bộ nhận diện hiểu các cách hỏi tự nhiên có từ chen giữa như “tìm vài cuốn sách chủ đề phiêu lưu” và nhu cầu ẩn như “không học luật nhưng muốn hiểu quyền lợi khi đi làm”. Truy vấn theo chủ đề chỉ hiện các kết quả có tín hiệu liên quan; AI chỉ chọn ID trong nhóm đã truy xuất để nội dung tư vấn và thẻ sản phẩm luôn đồng bộ. Các câu thống kê hoặc hỏi danh sách thể loại dùng phần tóm tắt catalog nhưng không tự hiện sách.

Prompt AI nhận mô tả rút gọn của các sách liên quan và kiến thức về quy trình mua, tài khoản, đơn hàng, giao hàng, thanh toán, đổi trả để trả lời được nhiều cách hỏi hơn. Giao diện chỉ hiện quick replies ở tin nhắn bot mới nhất; các bộ nút cũ vẫn được lưu trong lịch sử nhưng không lặp lại trên màn hình.

Các quick reply “Còn cuốn khác không?”, “Sách rẻ hơn” và “So sánh các cuốn này” gửi product ID của câu trả lời gần nhất làm ngữ cảnh. ID chỉ dùng để chọn lại catalog hiện hành; giá và tồn kho vẫn đọc lại từ database. Action `ADD_TO_CART` luôn mở xác nhận inline trước khi gọi `CartService`. Action đơn hàng chỉ tra dữ liệu khi JWT hợp lệ và dùng principal hiện tại; client không gửi email/user ID để chọn chủ sở hữu.

Ở chế độ `local`, câu hỏi nêu đúng tên sách về tồn kho, giá hoặc phần trăm giảm được trả lời trực tiếp từ catalog. Số lượng luôn kèm lưu ý có thể thay đổi khi phát sinh đơn hàng mới.

Lịch sử giao diện chỉ lưu trong `sessionStorage` của tab hiện tại với key `vinabook_chat_session`, tối đa 24 tin nhắn; `vinabook_chat_id` là mã phiên ngẫu nhiên. Backend không lưu câu hỏi hay câu trả lời: bảng `chat_events` chỉ có metadata vận hành và feedback. Không gửi mật khẩu, OTP, số thẻ đầy đủ hoặc CVV vào chat.

## Mua hàng trong chat

Lịch sử từ bản sửa phân tách tài khoản dùng key `vinabook_chat_session_v2:user:{email}`; khách chưa đăng nhập dùng key riêng `vinabook_chat_session_v2:guest`. Khi đổi tài khoản/đăng xuất: hủy stream, xóa nội dung đang gõ, lỗi, action, bản nháp và ngữ cảnh hiển thị; chỉ tải lịch sử của tài khoản mới, đồng thời đổi mã phiên chat. Lịch sử cũ ở key dùng chung bị xóa do không có owner để xác minh, không tự chuyển sang tài khoản đang đăng nhập. Đây là lưu trữ giao diện trong tab, không phải ranh giới bảo mật thay cho xác thực backend.

- Intent PURCHASE nhận các câu mua/chốt/lấy sách, tên sách và tham chiếu theo thứ tự card. Không rõ sách thì hỏi chọn lại; không lấy sách trước đó thay cho tên không tìm thấy. Nhận diện mua hiện là rule tiếng Việt, không phụ thuộc Gemini và chưa bao phủ mọi cách diễn đạt.
- Mỗi bản nháp hỗ trợ một đầu sách, số lượng 1–99 và COD. Người dùng phải đăng nhập. Không thay đổi giỏ hàng hiện có.
- Dùng AddressService theo email hiện tại: một địa chỉ thì chuẩn bị quote; nhiều địa chỉ thì hỏi chọn (không tự chọn mặc định); chưa có thì hỏi tên, điện thoại, địa chỉ. Có thể nhập form hoặc trả lời tuần tự trong chat. Địa chỉ vẫn lưu theo thiết bị; thông tin nhập riêng trong chat không tự ghi vào sổ địa chỉ.
- POST /api/chat/checkout/quote lấy giá và tồn kho từ server, tính phí 20.000đ, miễn phí từ 300.000đ. Chưa tạo order. Bản nháp hiển thị số lượng, người nhận, địa chỉ, COD, giá sách, phí ship và tổng tiền; có nút sửa/bỏ.
- POST /api/chat/checkout/{token}/confirm chỉ được gọi khi bấm Xác nhận đặt hàng. JWT xác định owner. Order và OrderDetail ghi trong cùng transaction, kiểm tra lại giá/tồn kho. Retry cùng token trả lại mã đơn cũ, dùng paymentReference CHAT-{token} để khôi phục kết quả sau restart.
- Khi mất mạng lúc xác nhận, giao diện khóa sửa/bỏ và retry cùng token. Sau khi tải lại trang cần kiểm tra Đơn hàng trước khi đặt lại. Các bản nháp chưa xác nhận nằm trong bộ nhớ backend, hết hạn sau 15 phút; restart làm mất bản nháp chưa chốt. Hiện thiết kế cho một backend instance; cần kho draft bền vững và unique idempotency key riêng trước khi scale nhiều instance.
- Thông tin giao hàng của bản nháp không gửi AI, không lưu trong lịch sử chat; thông báo đơn hàng cũng loại khỏi history gửi AI/sessionStorage. Đổi tài khoản xóa bản nháp và thông báo riêng tư. Trong lúc lập bản nháp, câu hỏi phí giao hàng/giá/thanh toán được xử lý riêng, không gán vào trường giao hàng. Câu hỏi nội dung sách có thể tiếp tục dùng ngữ cảnh sách công khai, không kèm các tin nhắn riêng tư.
- Đổi sách bằng “Đổi sang cuốn …” chỉ thay bản nháp khi xác định đúng một sách, giữ thông tin giao hàng đã nhập và lấy lại giá server. Có thể nhắn “lấy 2 cuốn”, “sửa địa chỉ”, “sửa số điện thoại”; dữ liệu không hợp lệ được hỏi lại. Nút xác nhận đặt hàng luôn là thao tác riêng, không tự tạo đơn từ câu “ok”. Hủy bản nháp cũng hủy phản hồi đổi sách đang chờ, tránh mở lại bản nháp cũ.
- Khung chat desktop cao gần hết viewport, rộng 460px; bản nháp thu gọn được để đọc hội thoại. Tổng tiền và nút xác nhận đặt ngoài vùng cuộn, thông báo lỗi xác nhận tự mở lại bản nháp. Trên màn hình nhỏ khung chat co theo viewport và safe area.
- Tra cứu “Đơn #123 sao rồi?” chỉ tìm trong đơn của principal hiện tại; không thấy sẽ hỏi kiểm tra mã. Trạng thái là dữ liệu cửa hàng, chưa có vị trí vận chuyển thời gian thực.
- Giữ quy trình tồn kho hiện có: kiểm tra khi tạo, trừ khi hoàn thành đơn; chưa giữ chỗ tồn kho cho đơn chờ xử lý. Chưa thêm email xác nhận cho luồng chat. Thanh toán online và mua nhiều đầu sách vẫn dùng checkout thường.

Kiểm thử checkout dùng repository/HTTP giả lập, không tạo đơn khách hàng thật.
# Sửa truy vấn ngân sách và ý định mua tiếp nối

- Truy vấn chỉ có ngân sách (ví dụ “Gợi ý sách dưới 150k”) lọc theo giá sau giảm, không bắt từ chỉ giá khớp tên/mô tả. Khi AI lỗi, kết quả hợp lệ vẫn được giữ. Truy vấn có chủ đề vẫn phải khớp chủ đề.
- Câu mua rút gọn như “giúp t mua”, “mua ngay”, “chốt nhé” dùng danh sách sách đang được tham chiếu. Một sách mở bản nháp; nhiều sách hỏi chọn lại. Không thay tên sách không tồn tại bằng sách cũ; đặt hàng vẫn cần xác nhận riêng.
