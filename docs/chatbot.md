# Trợ lý VinaBook

Chatbot dùng một API chung `POST /api/chat`. Angular không biết API key và không phụ thuộc trực tiếp vào cấu trúc response của bất kỳ hãng AI nào.

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
- `POST /api/chat`: nhận `message` tối đa 1000 ký tự và tối đa 10 lượt `history`.
- Response chuẩn hóa gồm `answer`, `provider`, `model`, `fallback`, `products` và `chips`.

Backend lấy catalog đang hoạt động từ MySQL, xếp hạng sách liên quan và đưa một context giới hạn vào prompt. Giá hiển thị được tính từ giá gốc và phần trăm giảm trong database. Nếu provider chưa cấu hình, timeout hoặc lỗi, backend trả lời bằng chế độ `local`; nếu cả request backend thất bại, bộ FAQ trong Angular tiếp quản.

Thẻ sản phẩm chỉ được đính kèm khi câu hỏi có ý định tìm/gợi ý/so sánh sách, nhắc đúng tên sách, tác giả hoặc thể loại có trong catalog. Câu hỏi về giao hàng, thanh toán, đổi trả, tài khoản hay trò chuyện thông thường không tự động hiện sách. Khi khách nhắc đúng một hoặc nhiều tên sách, response chỉ kèm các sách được nhắc tới thay vì thêm sản phẩm bán chạy không liên quan.

Bộ nhận diện hiểu các cách hỏi tự nhiên có từ chen giữa như “tìm vài cuốn sách chủ đề phiêu lưu”, đồng thời xếp hạng theo tên, tác giả, thể loại và mô tả. Truy vấn theo chủ đề chỉ hiện các kết quả có tín hiệu liên quan; AI chỉ nhận đúng nhóm sách được hiển thị để nội dung tư vấn và thẻ sản phẩm luôn đồng bộ. Các câu thống kê hoặc hỏi danh sách thể loại dùng phần tóm tắt catalog nhưng không tự hiện sách.

Prompt AI nhận mô tả rút gọn của các sách liên quan và kiến thức về quy trình mua, tài khoản, đơn hàng, giao hàng, thanh toán, đổi trả để trả lời được nhiều cách hỏi hơn. Giao diện chỉ hiện quick replies ở tin nhắn bot mới nhất; các bộ nút cũ vẫn được lưu trong lịch sử nhưng không lặp lại trên màn hình.

Ở chế độ `local`, câu hỏi nêu đúng tên sách về tồn kho, giá hoặc phần trăm giảm được trả lời trực tiếp từ catalog. Số lượng luôn kèm lưu ý có thể thay đổi khi phát sinh đơn hàng mới.

Lịch sử giao diện chỉ lưu trong `sessionStorage` của tab hiện tại với key `vinabook_chat_session`, tối đa 24 tin nhắn. Không gửi mật khẩu, OTP, số thẻ đầy đủ hoặc CVV vào chat.
