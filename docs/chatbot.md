# Trợ lý VinaBook

Chatbot dùng backend làm nguồn nghiệp vụ duy nhất. Angular không biết API key và không phụ thuộc trực tiếp vào cấu trúc response của hãng AI. Luồng phân tích tình huống, hồ sơ sách, xuất bản và chính sách dùng chung được mô tả trong [thiết kế chatbot hiện tại](chatbot-rebuild.md).

## Hội thoại và khôi phục ngữ cảnh (19/10/2026)

Luồng có AI hiện đọc **ý nghĩa câu hỏi trước khi truy xuất catalog/giỏ hàng**. AI có thể trả lời trực tiếp bằng `reply` cho chào hỏi, hỏi khả năng hỗ trợ, giải nghĩa/dịch phần chữ khách cung cấp hoặc gợi mở ý tưởng đọc chung. Nhánh này có `calls=[]`, không hiện thẻ sách/menu ngành hàng ngoài ý khách và không đổi lựa chọn đang giữ. Khi câu hỏi cần dữ liệu cửa hàng (giá, tồn kho, sách có bán, chính sách, đơn...) AI mới đề xuất công cụ; backend xác minh tham số/quyền và lấy dữ liệu thật. Ví dụ “hi bạn có thể giúp gì cho tôi” không được coi là nhu cầu đọc sách để dựng menu thể loại.

Các hướng gợi mở trong hội thoại chưa phải cam kết cửa hàng có sách; AI có thể nhắc lại hoặc hiểu lựa chọn từ lịch sử. Menu đã được kiểm tra có sách vẫn dùng trạng thái riêng và phải lấy dữ liệu cửa hàng. Không coi lời chatbot là sở thích đã xác nhận.

Nhánh luật tham chiếu/lệnh cụ thể được chuyển sang dự phòng khi nhà cung cấp không đọc được yêu cầu, thay vì chạy trước AI. Bước đọc ý có ngân sách15 giây trong ngân sách chung45 giây; lỗi vẫn giữ ngữ cảnh. Các phép kiểm tra giá, tồn kho, quyền tài khoản và bước khách xác nhận thao tác giữ nguyên. Giỏ tài khoản được đọc từ server khi công cụ thực sự cần giỏ, không dùng snapshot client làm nguồn cho giỏ tài khoản. Xem [hợp đồng luồng AI](chatbot-structured-pipeline.md).

- Câu hỏi chung như “Đắc Nhân Tâm nói về những nội dung gì?” dùng phần nội dung đã được duyệt. Tên sách khách nêu rõ được ưu tiên kể cả sau một lượt tìm không có kết quả. Hỏi sâu hoặc tạm dừng mua để hỏi nội dung không mở một đợt gợi ý sách khác.
- Lựa chọn mua của khách chưa đăng nhập vẫn giữ mã sách. “Đổi thành 3 cuốn” cập nhật lựa chọn chờ đăng nhập; lượng bằng 0 bị từ chối và khách có thể sửa lại ngay. Tồn kho và giới hạn lượng vẫn do backend kiểm tra; chưa tạo đơn ở bước này.
- Khi khách đã nêu nhu cầu cụ thể, các hướng đọc phải liên quan đến nhu cầu đó. Có một hướng phù hợp thì hiển thị một hướng, không bù bằng chủ đề xa. Chọn số thứ tự cùng số cuốn/ngân sách tổng giữ cả những điều kiện đó. Nếu chỉ tìm được một phần số cuốn yêu cầu, câu trả lời nói rõ.
- Khi nhà cung cấp lỗi, các yêu cầu cơ bản dùng công cụ đã xác thực: tên sách, tìm sách với giá/số lượng và loại trừ rõ ràng. Câu quá phức tạp hoặc chưa đủ căn cứ giữ nguyên ngữ cảnh và cho thử lại; không chuyển sang menu chung. Tra cứu rõ dạng “Có sách [tên] không?” có nhánh dự phòng nội bộ; khi AI hoạt động, ý định vẫn được AI đọc trước.
- Chưa có trường mục lục được duyệt theo ấn bản: khi khách yêu cầu, chatbot nói rõ dữ liệu chưa có và đề nghị kiểm tra với cửa hàng, không tự tạo tên chương.
- Email/điện thoại trong câu tra đơn nhận hướng dẫn đăng nhập và phạm vi đơn thuộc tài khoản hiện tại. Guard mật khẩu/OTP vẫn được ưu tiên và không đưa dữ liệu riêng tư sang AI.
- Khôi phục lịch sử giữ nút `SHOW_CART` (“Xem và sửa giỏ”). Các hành động xóa giỏ/hủy đơn vẫn không được tự khôi phục để thực thi.

Các nhánh này có kiểm tra hồi quy cho parser, pipeline/công cụ và restore action. Chúng không thay thế kiểm thử checkout/thanh toán sau đăng nhập hoặc đánh giá toàn bộ 50 kịch bản.

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

Launcher chung `start-vinabook.cmd` ở gốc dự án nạp các dòng `NAME=VALUE` trong `VinaBookStore/.env.local` làm giá trị literal, không thực thi nội dung file và không in khóa. Biến môi trường đã có trong tiến trình được ưu tiên. Cấu hình AI được truyền cho backend cùng với `JWT_SECRET`.

Sau khi backend sẵn sàng, launcher đối chiếu provider tại `GET /api/chat/status` với `AI_PROVIDER`. Nếu cấu hình yêu cầu AI nhưng backend đang chạy `local` hoặc `aiEnabled=false`, launcher báo lỗi để tránh đánh giá nhầm chatbot. Thay đổi file cần khởi động lại backend. Khi kiểm thử bản có AI, xác nhận `aiEnabled=true` và `architecture=structured-v1`; chế độ nội bộ báo `dialogue-v3-local`.

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

Ví dụ Anthropic chỉ cần đổi `AI_PROVIDER`, `AI_MODEL` và `AI_API_KEY`. Gemini có bốn ô khóa riêng như dưới đây. Khởi động lại backend sau khi đổi biến môi trường.

### Thiết lập nhanh Gemini trên Windows

Workspace đã có sẵn `VinaBookStore/.env.local` bị Git ignore. Mở file đó và điền tối đa bốn khóa Google AI Studio; ô 2, 3 và 4 có thể để trống:

```text
GEMINI_API_KEY_1=khoa-chinh
GEMINI_API_KEY_2=khoa-du-phong-1
GEMINI_API_KEY_3=khoa-du-phong-2
GEMINI_API_KEY_4=khoa-du-phong-3
```

Sau đó chạy:

```powershell
cd VinaBookStore
.\run-gemini.cmd
```

Script tự nạp `AI_PROVIDER=gemini`, model `gemini-3.1-flash-lite` và tối đa bốn khóa vào backend. `GEMINI_API_KEY_4` là ô khóa bổ sung, có thể để trống. Chạy `run-gemini.ps1 -Check` khi cần xác thực model; lúc khởi động thường script không gọi thử Gemini. Ô khóa trống, placeholder hoặc trùng được bỏ qua. Backend luân phiên khóa còn dùng được; khi một khóa hết hạn mức hoặc lỗi tạm thời, nó thử khóa khác với cùng model. Thời gian xử lý dùng chung ngân sách của lượt chat. Cấu hình local và file mẫu để `AI_FALLBACK_MODEL` trống theo lựa chọn chỉ dùng model cũ; không tự đổi sang model khác. Nếu sau này chủ động cấu hình model dự phòng, gateway vẫn hỗ trợ chuyển model khi cần. Nếu bước đọc hiểu thất bại, pipeline giữ nguyên ngữ cảnh và báo khách thử lại; nếu chỉ bước diễn đạt thất bại, backend vẫn trả nội dung sách đã xác thực. Cấu hình cũ `AI_API_KEY` vẫn hoạt động khi cả bốn ô khóa đều trống. File `.env.local` được Git ignore; không chia sẻ nội dung của nó. Sau khi điền hoặc đổi khóa, khởi động lại backend để nạp cấu hình.

Google áp [hạn mức Gemini theo dự án](https://ai.google.dev/gemini-api/docs/rate-limits), không theo API key. Các khóa cùng dự án không làm tăng số lượt được cấp.

Các biến tinh chỉnh:

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `AI_FALLBACK_MODEL` | trống | Model Gemini dự phòng tùy chọn; để trống để chuyển API key nhưng giữ nguyên model chính |
| `AI_TIMEOUT_SECONDS` | `25` | Timeout cho một request AI |
| `AI_MAX_OUTPUT_TOKENS` | `4096` | Giới hạn output, backend chặn trong khoảng 100 đến 8192; cần đủ chỗ cho phần suy luận và JSON của Gemini |
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

Backend lấy catalog đang hoạt động từ MySQL, xếp hạng sách liên quan và đưa một context giới hạn vào prompt. Giá hiển thị được tính từ giá gốc và phần trăm giảm trong database. Nếu provider chưa cấu hình, timeout hoặc lỗi, backend trả lời bằng chế độ `local`. Angular chỉ retry qua transport JSON và hiện trạng thái lỗi nếu backend không truy cập được; không tự bịa câu trả lời nghiệp vụ.

Pipeline backend gồm `ChatIntentService` → `ChatProductSearchService` → policy/order handler → `AiGateway` hoặc local answer → typed actions → analytics. Intent hiện gồm tìm sách, thông tin sản phẩm, so sánh, chính sách, hỗ trợ đơn, chào hỏi và chưa nhận diện. Bộ eval có hơn 50 cách hỏi tiếng Việt có dấu/không dấu.

Thẻ sản phẩm chỉ được đính kèm khi câu hỏi có ý định tìm/gợi ý/so sánh sách, nhắc đúng tên sách, tác giả hoặc thể loại có trong catalog. Câu hỏi về giao hàng, thanh toán, đổi trả, tài khoản hay trò chuyện thông thường không tự động hiện sách. Khi khách nhắc đúng một hoặc nhiều tên sách, response chỉ kèm các sách được nhắc tới thay vì thêm sản phẩm bán chạy không liên quan.

Bộ nhận diện hiểu các cách hỏi tự nhiên có từ chen giữa như “tìm vài cuốn sách chủ đề phiêu lưu”, đồng thời xếp hạng theo tên, tác giả, thể loại và mô tả. Truy vấn theo chủ đề chỉ hiện các kết quả có tín hiệu liên quan; AI chỉ nhận đúng nhóm sách được hiển thị để nội dung tư vấn và thẻ sản phẩm luôn đồng bộ. Các câu thống kê hoặc hỏi danh sách thể loại dùng phần tóm tắt catalog nhưng không tự hiện sách.

Prompt AI nhận mô tả rút gọn của các sách liên quan và kiến thức về quy trình mua, tài khoản, đơn hàng, giao hàng, thanh toán, đổi trả để trả lời được nhiều cách hỏi hơn. Giao diện chỉ hiện quick replies ở tin nhắn bot mới nhất; các bộ nút cũ vẫn được lưu trong lịch sử nhưng không lặp lại trên màn hình.

Các quick reply “Còn cuốn khác không?”, “Sách rẻ hơn” và “So sánh các cuốn này” gửi product ID của câu trả lời gần nhất làm ngữ cảnh. ID chỉ dùng để chọn lại catalog hiện hành; giá và tồn kho vẫn đọc lại từ database. Các action sửa giỏ luôn mở xác nhận trước khi gọi `CartService`. Action đơn hàng chỉ tra dữ liệu khi JWT hợp lệ và dùng principal hiện tại; client không gửi email/user ID để chọn chủ sở hữu. Luồng giỏ và báo giá mới được mô tả trong [đặt sách trong chat](chatbot-commerce.md).

Ở chế độ `local`, câu hỏi nêu đúng tên sách về tồn kho, giá hoặc phần trăm giảm được trả lời trực tiếp từ catalog. Số lượng luôn kèm lưu ý có thể thay đổi khi phát sinh đơn hàng mới.

Tên sách rút gọn gồm ít nhất hai từ đặc trưng, như “Sherlock Holmes”, được đối chiếu với tên đầy đủ trong catalog. Câu hỏi “có bản nào?” trả danh sách bản sách tìm được; khi không xác định được nhu cầu hoặc cuốn sách, chatbot hỏi lại thay vì chọn sách không liên quan.

Khi khách nêu rõ hai tên sách để so sánh, chatbot đối chiếu cả hai với catalog và lập bảng cho đúng cặp sách; câu “nên chọn cuốn nào” được bổ sung lời khuyên theo mục tiêu đọc có căn cứ từ mô tả sách. Yêu cầu gợi ý có sẵn chủ đề hoặc mục đích đọc được xử lý ngay. Người đọc/người nhận, tuổi, trình độ và ngân sách là thông tin bổ sung, không bắt buộc phải hỏi trước khi tư vấn; nếu khách đã nói thì dùng để lọc và giải thích lựa chọn. Những trường hợp đã đủ dữ kiện này truy vấn catalog trực tiếp nên vẫn trả lời được khi hạn mức AI tạm thời không khả dụng. Với câu hỏi chọn quà cho trẻ có nêu tuổi, chatbot đưa gợi ý từ nhóm sách thiếu nhi và dùng đúng tuổi đã nêu; thông tin phù hợp độ tuổi của từng cuốn chỉ được khẳng định khi có dữ liệu đã duyệt. Trong cùng một lượt tìm theo chủ đề, “còn cuốn khác không?” loại các cuốn đã giới thiệu trước đó; khi hết kết quả phù hợp, chatbot nói rõ và đề nghị nới điều kiện hoặc đổi chủ đề.

## Cập nhật chất lượng 02/10/2026

Chatbot giữ riêng danh sách so sánh và cuốn đang được chọn. “Cuốn nào rẻ hơn?” lấy giá hiện tại từ catalog và cập nhật cuốn được chọn; “cuốn đó” tiếp tục dùng cuốn này, “cuốn còn lại” dùng cặp so sánh đã lưu. Việc hỏi chi tiết một cuốn không làm mất cặp so sánh. Context gửi tới bộ phân tích có tên, giá và tồn kho của các sách đang được nhắc để tránh yêu cầu người dùng nhắc lại tên.

Câu hỏi chứa nhiều dữ kiện được trả lời theo từng trường từ catalog, kể cả khi bộ phân tích chọn một loại thông tin quá hẹp. Tác giả và tình trạng bán không phụ thuộc hồ sơ thư mục; ISBN, số trang, nhà xuất bản và năm xuất bản chỉ được dùng khi đã duyệt, và chatbot nêu cụ thể trường còn thiếu. So sánh luôn kèm mô tả hiện có; nhận xét theo mục tiêu đọc chỉ được bổ sung khi có bằng chứng trong mô tả đã cung cấp.

Gợi ý là các phương án thay thế, chỉ cộng tổng tiền khi người dùng nêu ngân sách cho một nhóm sách. Chủ đề khởi nghiệp được lọc riêng với kinh doanh nói chung; danh mục rộng không được dùng như bằng chứng cho một chủ đề cụ thể. Gợi ý cho trẻ có nêu tuổi nói rõ giới hạn khi hồ sơ chưa xác nhận độ tuổi. Câu hỏi về quyền xem đơn không bị chuyển sang cuốn sách đang chọn. Hỏi phí ship với nhiều mức tiền sách được tính từng mức bằng cấu hình giao hàng dùng chung với checkout.

Tên và tham chiếu trong lời khách được ưu tiên hơn title/ID do AI điền: “mua 2 bản cuốn đó” dùng cuốn vừa hỏi, giữ số lượng và dẫn tới trang mua, không tự biến thành yêu cầu thêm giỏ. Yêu cầu về mã nguồn, viết mã hoặc kỹ sư phần mềm giữ điều kiện nội dung lập trình kể cả khi AI chỉ gửi các mục tiêu chung như dễ hiểu/bảo trì. Bộ lọc bán chạy chỉ có hiệu lực khi khách yêu cầu hoặc đang tiếp tục một lượt tìm bán chạy đã yêu cầu; tìm thông thường xếp theo mức liên quan. Câu “đừng đoán giá” không chặn tra cứu tên sách đã nêu.

Action mua mang `quantity` trong đường dẫn trang sách. Trang sách điền số lượng sau khi nạp tồn kho mới, chỉ nhận số nguyên 1–99 và giới hạn theo tồn kho; khách vẫn tự bấm mua/thêm giỏ. Yêu cầu hủy đơn đi vào kiểm tra quyền và điều kiện hủy; phản hồi chưa đăng nhập giữ đúng yêu cầu hủy để khách gửi lại sau đăng nhập.

## Gợi ý thêm sách và cách trình bày — 02/10/2026

- Mặc định mỗi lượt giới thiệu tối đa 3 cuốn. Số lượng được nói rõ trong tin nhắn (chữ hoặc số, ví dụ “sáu cuốn”, “9 cuốn”) ưu tiên hơn `limit` do AI điền; số lượng cụ thể không bị giới hạn ở 3 hoặc 8. Lượt bổ sung bộ lọc giữ số lượng đã yêu cầu, lượt hỏi thêm bắt đầu nhóm mới tối đa 3 nếu khách không nêu số mới. “Tất cả” tìm toàn bộ danh sách đáp ứng yêu cầu.
- `scope=more` giữ chủ đề và bộ lọc của lượt tìm trước, loại tất cả ID đã xuất hiện trong cuộc trò chuyện. Danh sách này không bị xóa khi tìm chủ đề mới, hỏi chi tiết sách hay chính sách. Nó được lưu cùng trạng thái hội thoại và chỉ mất khi tạo cuộc trò chuyện mới.
- Khi đọc hội thoại lưu bằng phiên bản cũ, backend khôi phục các ID đã giới thiệu từ toàn bộ transcript một lần rồi lưu phiên bản trạng thái mới. Câu đổi sang chủ đề được nói rõ ưu tiên hơn `scope` sai do AI điền.
- Chọn sách đúng yêu cầu trước. Khi hỏi thêm hoặc yêu cầu số cụ thể mà thiếu, hỏi khách trước khi mở rộng. Chỉ bổ sung khi khách cho phép qua lời nói hoặc đồng ý đề nghị vừa nhận; số lượng yêu cầu và match do planner tự điền không phải sự cho phép. Dùng nội dung đã xác nhận cho một chủ đề lân cận; danh mục rộng như “Tiểu thuyết” không đủ chứng minh sách gần với “trinh thám”. Hai nhóm được ghi rõ trong câu trả lời, kèm lý do cho nhóm gần chủ đề.
- Chủ đề lân cận phải có nội dung cụ thể: ví dụ phiêu lưu có thể mở sang truyện về chu du, tìm kho báu hoặc giả tưởng; từ “hành trình” đơn lẻ không đủ để chọn sách lịch sử hay tâm linh. Sách về thói quen nghề lập trình không tự được xếp vào nhóm phát triển bản thân nói chung.
- Bằng chứng cho nhóm gần chủ đề dùng tên và mô tả đã duyệt, không chỉ một tag chung với sách đã xem. Liên kết đã duyệt tiếp tục phục vụ yêu cầu tìm sách quanh một cuốn cụ thể. Không dùng từ nằm trong tên tác giả để chứng minh chủ đề sách. Nhóm phiêu lưu/trinh thám/lãng mạn vẫn giữ điều kiện nội dung truyện khi mở rộng; “gia đình mafia” không được đồng nhất với tình cảm gia đình.
- Các điều kiện giá, đánh giá, tác giả, tồn kho, trình độ đọc và tổng ngân sách vẫn phải được đáp ứng khi bổ sung. Danh mục/chủ đề có thể được mở rộng và được nói rõ. Nếu không đủ số cuốn khác nhau, thông báo số tìm được và số yêu cầu; nếu hết cả hai nhóm, trả danh sách rỗng và nói không còn lựa chọn phù hợp. `match=exact_only` không bổ sung sách gần chủ đề.
- Phần tư vấn có thể thêm dẫn dắt, giải thích nội dung và một bước trao đổi tiếp theo bằng Gemini. Mỗi lời giải thích phải có đoạn bằng chứng nguyên văn trong mô tả được cung cấp. Backend giữ tên, giá, số lượng, phân nhóm, thẻ sách và actions; lời dẫn không được tự nêu số liệu, tồn kho hay xác nhận thao tác. Nếu phần diễn đạt lỗi, giữ câu trả lời đã xác thực.
- Nhánh dự phòng không có AI dùng cùng công cụ chọn sách để giữ quy tắc loại sách cũ và số lượng; phần diễn đạt dùng câu dẫn có sẵn.

## Rà soát ngữ cảnh và điều kiện tìm sách — 03/10/2026

- Câu nhắc một cuốn cụ thể như “gợi ý thêm sách tương tự Clean Code” dùng cuốn đó làm gốc, ưu tiên hơn chủ đề của lượt trước. Backend lưu `relatedSourceId` trong trạng thái hội thoại để lượt “còn cuốn khác cùng chủ đề” tiếp tục dùng đúng gốc, kể cả khi sách gốc không nằm trong danh sách vừa trả. Chỉ nhận ID gốc đã xác định; không cho AI chọn tùy ý một ID chưa có trong ngữ cảnh.
- Gợi ý quanh một cuốn dùng liên kết đã duyệt hoặc nội dung đã xác nhận có chủ đề lân cận, kèm bằng chứng cho từng lựa chọn. Sách hết hàng và sách đã giới thiệu bị loại khi hỏi thêm; điều kiện giá, rating và ngôn ngữ loại trừ được giữ qua các lượt.
- “Trinh thám hoặc tình yêu” lấy hợp của hai nhóm, khử trùng ID, áp dụng bộ lọc cho cả hai. “Vài cuốn lập trình và vài cuốn trinh thám” cũng chọn từ các nhóm riêng, với một giới hạn tổng; lấy lần lượt đại diện mỗi nhóm để một nhóm không chiếm hết danh sách. Yêu cầu một cuốn kết hợp nhiều nội dung vẫn phải đáp ứng đồng thời các nội dung đó. Sắp xếp giá/rating được nói rõ vẫn được ưu tiên.
- Điều kiện “không lấy Java” được hiểu là loại trừ, không phải yêu cầu tìm thêm Java; nó áp dụng cả sách gần chủ đề. “Chỉ lấy Python” giữ tìm đúng ngôn ngữ và không tự mở sang sách lập trình tổng quát. Các điều kiện này được lưu trong cuộc trò chuyện.
- Bỏ giới hạn giá và giữ chủ đề là bổ sung bộ lọc: xóa giá nhưng giữ số cuốn đã yêu cầu, ngay cả khi bộ phân tích điền nhầm `scope=new`.
- Phần diễn đạt bỏ lời chào lại sau lượt đầu và các nhận định thêm về ngôn ngữ áp dụng, nhịp kể hoặc quá trình trưởng thành của nhân vật khi mô tả chưa xác nhận. Lời dẫn không được tự kết luận catalog còn/thiếu sách; phần thông báo kết quả do backend giữ.

Xem kết quả kiểm tra và phản hồi nguyên văn trong [báo cáo 03/10/2026](chatbot-evaluation/archive/2026-09-10/review-context-2026-10-03.md).

## Yêu cầu đọc có cấu trúc và tìm theo ngữ nghĩa — 03/10/2026

- `readingRequest` là góc nhìn có cấu trúc của `toolSearch` đang lưu trong hội thoại: điều kiện bắt buộc, phần ưu tiên, mục tiêu, người đọc, trình độ, tổng ngân sách và số cuốn. Không tạo thêm nguồn lưu trùng. Bộ phân tích dùng cùng một lần gọi Gemini để chọn tình huống và điền công cụ.
- `query` chỉ chứa nội dung bắt buộc; `preferredQuery` ưu tiên thứ tự nhưng không loại hết các lựa chọn khác; `excludedQuery` loại nội dung phải tránh. Mục tiêu/người nhận chỉ nhận cụm từ từ lời khách. “Ưu tiên cũ” giữ giá trị cũ. Bỏ ưu tiên không xóa điều kiện loại trừ; tăng/giảm ngân sách không bắt đầu nhu cầu mới. “Chỉ lấy Python” được backend lưu thành điều kiện tìm chính xác, kể cả khi bộ phân tích điền query quá rộng.
- Tổng ngân sách áp dụng cả nhóm sách. Bộ chọn giữ số cuốn có thể đáp ứng trong ngân sách rồi mới ưu tiên sở thích, tránh chọn một cuốn ưu tiên đắt làm rơi những cuốn còn lại. “Tổng cộng 5 cuốn, mỗi cuốn dưới 150k” là số lượng và giá mỗi cuốn. Giá mỗi cuốn trong tin vừa tìm sách vừa hỏi phí ship không được dùng làm tổng tiền đơn.
- Vector tìm kiếm xét toàn bộ hồ sơ đã duyệt qua các bộ lọc bắt buộc, giữ tiếng Việt có dấu khi tạo query embedding. Nó có thể tìm sách gần ý nghĩa dù không trùng từ khóa; các kết quả được bổ sung theo vector được để trong nhóm gần nội dung với tóm tắt nguồn, không tự khẳng định đúng thể loại. Với câu hỏi theo mục tiêu, nếu phép tìm ngữ nghĩa chạy thành công thì chỉ giữ kết quả từ khóa nằm trong nhóm có căn cứ; không dùng một từ chung để khẳng định sách đáp ứng toàn bộ mục tiêu. Lỗi dịch vụ embedding được phân biệt với kết quả thật sự không có sách gần nghĩa.
- Yêu cầu bỏ điều kiện rõ ràng được cập nhật trực tiếp vào context. Các câu hỏi theo mục tiêu có mẫu rõ dùng công cụ tư vấn trên cả catalog, tránh nhầm một cụm như “mở doanh nghiệp” với tên sách “Luật Doanh Nghiệp”. Kế hoạch AI không hợp lệ được sửa một lần với cả kế hoạch trước và lý do kiểm tra, trước khi chạy bất kỳ công cụ nào.
- Ngưỡng hiện tại được kiểm tra trên catalog local: cosine tối thiểu 0,66 và trong khoảng 0,035 so với kết quả cao nhất. Đây là mức lọc thận trọng cho bộ dữ liệu hiện tại, cần kiểm tra lại khi thay model hoặc mở rộng catalog. Các vector hỏng/sai chiều/bản nháp không được dùng làm bằng chứng. Query embedding được cache tối đa 256 mục trong 15 phút; giá, tồn kho và catalog vẫn đọc mới mỗi lượt.
- Phần diễn đạt nhận thêm ba trao đổi gần nhất, yêu cầu đọc và trạng thái kết quả. Mỗi lời giải thích nêu trường nguồn cùng đoạn trích trong hồ sơ đã duyệt; backend kiểm tra lại trước khi dùng. Khi hết sách, giữ thông báo đã kiểm chứng và chỉ gợi ý nới điều kiện đang có. Không tự thêm điều kiện độ dài, hứa còn sách, hoặc chào lại mỗi lượt.
- Sau khi xuất bản hoặc sửa hồ sơ, chỉ mục chạy sau khi lưu thành công. Worker thử lại theo lô 10 hồ sơ mỗi phút, mỗi lô dùng một request embedding. Việc ghi chỉ mục có điều kiện theo văn bản hiện hành và trạng thái duyệt để không ghi vector cũ sau khi hồ sơ thay đổi.

Chi tiết kiểm thử và câu trả lời được ghi trong [báo cáo hoàn thiện yêu cầu đọc](chatbot-evaluation/archive/2026-09-10/review-reading-2026-10-03.md).

## Hai lớp ngữ cảnh

- **Cuộc trò chuyện hiện tại:** backend lưu từng cặp câu hỏi/câu trả lời và trạng thái chọn sách trong `chat_conversations` + `chat_exchanges`. Không có thời hạn tự xóa. `GET /api/chat/conversation?sessionId=...` trả tối đa 1000 tin mới nhất để dựng lại giao diện; bản ghi cũ hơn vẫn ở database. Mỗi lần gọi AI chỉ đưa vào một phần lịch sử gần đây hoặc liên quan cùng trạng thái có cấu trúc, tránh gửi cả 1000 tin lên Gemini. Nút “Cuộc trò chuyện mới” gọi `DELETE /api/chat/conversation?sessionId=...`, xóa lịch sử của phiên đó rồi tạo ID mới. Nếu xóa lỗi, giao diện giữ lịch sử để có thể thử lại. Bản cache 24 tin gần nhất trong `localStorage` chỉ giúp hiện giao diện tức thì khi đang tải dữ liệu từ server.
- **Bộ nhớ tài khoản:** `chat_account_memories` gắn với `userId` từ JWT, sống qua nhiều cuộc trò chuyện và không bị nút chat mới xóa. Nó tích lũy sở thích/điều tránh mà khách nói rõ, mức ngân sách thường dùng khi được nói như thói quen, và số lượt xem theo thể loại. Sách yêu thích hiện có cũng được đọc từ tài khoản khi tạo context. Những tín hiệu hoạt động là gợi ý yếu; yêu cầu rõ ở tin nhắn hiện tại luôn được ưu tiên. Khách vãng lai không có bộ nhớ tài khoản.

`POST /api/chat/activity` nhận `productId` từ trang chi tiết sách khi người dùng đã đăng nhập. Backend kiểm tra sản phẩm trong catalog và gắn tín hiệu với principal, không tin user ID từ trình duyệt. `GET /api/chat/account-memory` cho tài khoản đăng nhập kiểm tra các tín hiệu đang dùng. Lịch sử tài khoản chỉ đọc được bằng JWT cùng chủ sở hữu; phiên khách vãng lai dùng ID ngẫu nhiên 192 bit như khóa truy cập. Backend không lưu văn bản có dấu hiệu mật khẩu, OTP, email, số điện thoại hoặc nội dung tra đơn vào transcript. Không gửi số thẻ đầy đủ hoặc CVV vào chat.

Production dùng `ddl-auto=validate`: chạy [chat-memory.sql](../VinaBookStore/src/main/resources/db/chat-memory.sql) trước khi deploy phiên bản này.

## Siết chất lượng tư vấn — 08/10/2026

Giữ ngân sách khi siết chủ đề; đổi nhu cầu rõ ràng bỏ loại trừ/mức đọc cũ, xử lý câu bỏ loại trừ và nhận loại trừ mới trong cùng lượt. Dạng tiểu thuyết/truyện ngắn/truyện tranh được xác nhận từ nội dung, không suy từ danh mục chung. Câu phủ định không chứng minh chủ đề hay độ dễ đọc. Chatbot giải thích lý do chọn, giá và các dữ kiện còn thiếu, không tự bù đủ số cuốn bằng nhóm gần chủ đề. 259 test backend và 12 ca UI local đạt theo tiêu chí quan sát; xem [báo cáo](chatbot-evaluation/archive/2026-09-10/recommendation-improvements-2026-10-08.md).

## Danh sách gọn và lời tư vấn — 09/10/2026

Khi không có yêu cầu số lượng, chatbot chọn tối đa 3 cuốn. Với nhu cầu thư giãn hoặc nhiều tiêu chí đọc, ưu tiên nhóm có nhiều tiêu chí được xác nhận nhất, không bù bằng sách chỉ trùng chủ đề. Nếu không đủ căn cứ cho mọi tiêu chí, từng sách vẫn ghi rõ phần chưa xác nhận. Lượt hỏi thêm loại sách đã xem trước khi chọn nhóm tiếp theo; số lượng cụ thể, “tất cả”, giá và ngân sách vẫn được giữ.

Lời dẫn nối với nhu cầu đọc; giới hạn giá nhắc một lần. Mỗi sách có nội dung riêng và bằng chứng đúng trường về giọng kể, mức đọc hoặc cách áp dụng. Không lặp câu chọn sách chung chung, không dùng thông tin độ khó làm bằng chứng cho giọng kể. Người nhận và ngân sách vẫn tùy chọn. Xem [kết quả kiểm tra](chatbot-evaluation/archive/2026-09-10/shortlist-and-voice-2026-10-09.md).

### Sửa lỗi hội thoại tự nhiên — 09/10/2026

- “Có cuốn nào thú vị không?” là tìm sách, không tự động thành so sánh các cuốn đã chọn. Mục đích đọc giải trí khi buồn chán đủ để bắt đầu; không ép hỏi người nhận/ngân sách, cũng không suy diễn thành yêu cầu nhẹ nhàng hoặc tư vấn sức khỏe tinh thần. Chọn tác phẩm kể chuyện có bằng chứng về dạng truyện/thể loại, không chỉ dựa vào nhãn văn học hoặc cụm “câu chuyện” trong sách chiêm nghiệm.
- “Phiêu lưu hay trinh thám” dùng OR giữa các chủ đề nhận diện được; “sách hay” vẫn là tính từ. “Vừa… vừa…” giữ phép giao. Chủ đề cụ thể luôn ưu tiên hơn cách diễn đạt về mục đích giải trí.
- “Tiếp”, “thêm nữa”, “xem tiếp” sau tư vấn/chi tiết/so sánh dùng bộ lọc đang giữ và loại toàn bộ sách đã giới thiệu. Sau câu hỏi chính sách, không tự coi “tiếp” là yêu cầu sách. “?”/“ý là sao” giải thích kết quả hiện có, giữ yêu cầu và danh sách, không khởi động lại phần hỏi nhu cầu.
- Các quy tắc được dùng ở cả đường local và điều phối AI. Kiểm tra tích hợp dùng intake, bộ nhớ, tool và catalog fixture thực; UI dùng provider local. Không xác nhận Gemini thật chỉ từ mock điều phối.

Chi tiết tái hiện và kiểm tra: [context-repair-2026-10-09.md](chatbot-evaluation/archive/2026-09-10/context-repair-2026-10-09.md).

## Luồng hiểu yêu cầu và ghi ngữ cảnh bằng AI — 09/10/2026

Khi đã cấu hình AI, ChatStructuredPipeline thay bước intake/planner cũ bằng một lần đọc hiểu trả intent, patch ngữ cảnh có trích dẫn nguồn và kế hoạch công cụ. Backend kiểm tra phiên bản, kiểu dữ liệu, tiền, số lượng và tham chiếu; ghép vào bản sao của cùng toolSearch trước khi truy xuất catalog. Trường chưa nhắc được bỏ qua hoặc null; chỉ clear mới xóa điều kiện. Người nhận và ngân sách tiếp tục là thông tin bổ sung.

AI viết nhận gói dữ kiện đã xác minh, nhu cầu đã ghép, nguồn nội dung và các điểm chưa biết. Thẻ sách, giá, tồn kho và thao tác do backend giữ; lời giải thích sai căn cứ bị loại. Lỗi đọc hiểu/thực thi không ghi đè ngữ cảnh tốt; ngân sách AI toàn lượt là 45 giây. Local vẫn dùng đường hiện có khi chưa bật provider.

Hợp đồng, kiến trúc và 25 tình huống nghiệm thu: [chatbot-structured-pipeline.md](chatbot-structured-pipeline.md).
Kết quả triển khai và thử Gemini thật: [structured-pipeline-2026-10-09.md](chatbot-evaluation/archive/2026-09-10/structured-pipeline-2026-10-09.md).

## Củng cố ngày 17/10/2026

- Giữ Gemini 3.1 Flash-Lite và bốn slot key. Khi dịch vụ lỗi, yêu cầu đơn giản đi qua cùng công cụ truy xuất đã kiểm tra; yêu cầu phức tạp được làm rõ, không âm thầm bỏ điều kiện. Health tách khỏi trạng thái cấu hình.
- Lưu snapshot/transcript trong một giao dịch public, chỉ cập nhật RAM sau thành công; phối hợp xóa với commit của lượt đang chạy.
- Vector truy vấn có thời gian tối đa 2,5 giây, gộp truy vấn giống nhau và không khóa HTTP toàn cục. Catalog được tái dùng trong một lượt.
- SSE gửi tiến độ, bỏ các frame từng từ dư thừa; câu trả lời đã kiểm tra hiển thị ngay. Stream/JSON dự phòng dùng một deadline, worker có giới hạn và hỗ trợ hủy.
- Lịch sử tải 25 lượt mỗi trang; các nút mua/đăng nhập được khôi phục với giá/tồn mới. Hủy đơn, viết đánh giá và yêu cầu hỗ trợ cũ cần được mở lại từ yêu cầu mới.
- Widget tải khi mở; phần văn bản tư vấn bỏ thông tin giá/tác giả lặp với thẻ sách. Nháp checkout riêng có thể khôi phục sau restart, không chứa trong transcript.
- Planner/RAG cũ không còn nằm trong runtime; các ca tương thích được giữ ở fixture test, guard nội dung dùng narrator hiện hành.

Chi tiết cấu hình, retention và giới hạn triển khai: [operations.md](operations.md).


### 24/10/2026 — Gợi mở hướng đọc khi chưa rõ chủ đề

- Khi khách muốn chọn sách nhưng chưa có chủ đề, bước hiểu hội thoại gọi menu gợi ý đã kiểm chứng theo catalog và ngân sách hiện tại. Lời chào/hỏi khả năng vẫn là hội thoại tự nhiên.
- Các từ nối như “nên”, “với” trong câu chung không được coi là hoàn cảnh cá nhân để chấp nhận một thể loại do AI đoán. Chủ đề cụ thể như kỹ năng giao tiếp vẫn được giữ.
- Mỗi hướng hiển thị có nhóm sách đủ điều kiện riêng; chỉ khi khách chọn mới xác nhận chủ đề. Kiểm thử bao gồm câu thực tế, nhiều hướng, chọn số và giữ điều kiện tìm kiếm.

- Biết người nhận (ví dụ “tìm sách cho bạn gái”) chưa đủ để đoán một thể loại yêu thích. Chatbot gợi mở nhiều hướng; vẫn giữ hoàn cảnh cụ thể nếu khách thực sự đã nêu.
- Gợi ý chủ đề ưu tiên nhãn nội dung đã duyệt trước các nhóm danh mục rộng, vẫn kiểm tra đủ sách và loại nhóm trùng nhau.
- “Tình cảm hay phiêu lưu gì đó” là lựa chọn hoặc. Bộ hiểu và bộ lọc dùng chung họ từ đồng nghĩa; khi AI đã xác nhận nghĩa hoặc, nhánh gõ sai không được biến thành điều kiện bắt buộc cho nhánh đúng. “Sách hay” vẫn là tính từ, “vừa A vừa B” vẫn cần đủ hai chủ đề. Không tự khẳng định nhánh gõ sai có sách phù hợp.

- Tham chiếu danh sách: khi khách yêu cầu so sánh hai cuốn vừa gợi ý, backend lấy đúng hai cuốn đầu trong danh sách đang giữ, kể cả AI chỉ gửi focus/count=1. Cặp có tên sách hoặc số thứ tự cụ thể vẫn giữ cách giải quyết riêng.
- Nhắc lại menu cũ bằng một lệnh rõ sẽ khôi phục cả danh sách nút chọn ở backend sau bước AI hiểu hội thoại, không chỉ chép lại chữ. Câu trích để dịch/giải thích không kích hoạt lệnh này.
- Với một chủ đề duy nhất, metadata AND/OR tương đương nên không làm mất nhóm sách đã kiểm chứng; các thay đổi thật về giá, kho, nội dung hay điều kiện vẫn buộc tra lại.

- Lệnh so sánh danh sách rõ ràng được chuẩn hóa thành getProducts/reference=selection/field=table sau khi AI đọc câu hỏi; không mang theo tham số lẫn từ công cụ khác. Lệnh có thêm tên sách, điều kiện hay nội dung trích vẫn qua bộ hiểu thông thường.
- Chọn một hướng vừa hiển thị luôn là refine nên không bị AI mode=new xóa preview hoặc điều kiện cũ. Thông báo sửa kế hoạch cho tham số không hợp lệ liệt kê đúng các tên tham số được công cụ chấp nhận.

- Chọn số/tên của một chủ đề đã hiển thị được chuẩn hóa về đúng nhãn ở backend sau bước AI, tránh AI diễn đạt lại thành bộ lọc khác. Lựa chọn kèm giá hoặc số cuốn vẫn giữ đầy đủ bước hiểu điều kiện bổ sung.

- Khi preview hết hạn hoặc dữ liệu thay đổi, lần tra lại chủ đề vẫn dùng cùng tiêu chí bằng chứng nội dung/nhãn đã duyệt như menu ban đầu. Các từ rời như “bạn đọc” và “tinh thần” không chứng minh chủ đề “tình bạn”.

- Các câu hỏi dữ kiện rõ theo số thứ tự (giá, tác giả, tồn kho, rating) dùng đúng thứ tự của cặp so sánh/danh sách sau bước AI. Các ô rỗng/0 thuộc công cụ khác trong schema hợp nhất được bỏ qua; tham số lạ có nội dung hoặc tham số thật của công cụ vẫn kiểm tra nghiêm ngặt.
