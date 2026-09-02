# Trợ lý VinaBook

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
