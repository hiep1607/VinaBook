# Chatbot: hiểu yêu cầu, nhớ ngữ cảnh và viết có căn cứ

Luồng AI mới dùng provider đã cấu hình qua AiGateway. Local tiếp tục là phương án khi chưa cấu hình AI.
Không thay API giỏ hàng, checkout, đơn, hỗ trợ hoặc đánh giá hiện có.

~~~mermaid
flowchart LR
    U[Tin nhắn và ngữ cảnh] --> A[AI đọc hiểu và đề xuất patch]
    A --> V[Backend kiểm tra cấu trúc, nguồn và tham chiếu]
    V --> M[Ghép vào bản sao ngữ cảnh]
    M --> T[Công cụ lấy dữ liệu thật hoặc chuẩn bị thao tác]
    T --> P[Gói dữ kiện, căn cứ và giới hạn]
    P --> W[AI viết lời giải thích]
    W --> C[Backend kiểm tra và ghép thẻ sách]
    C --> S[Lưu ngữ cảnh và trả lời]
~~~

## 1. Đầu vào đọc hiểu

ChatTurnUnderstanding nhận tin nhắn nguyên văn, phiên bản context, bộ lọc đã xác nhận,
nguồn của từng trường, ghi chú ngữ nghĩa, vài lượt hội thoại gần đây, câu đang chờ,
thứ tự sách đã hiển thị, sách đang tập trung, danh sách đã giới thiệu và dữ kiện sách đã xác minh.
Phần lịch sử đọc hiểu chỉ giữ bốn cặp, gắn nhãn previousUser/previousAssistant và giới hạn độ dài;
message hiện tại nằm cuối gói và là nguồn duy nhất cho patch mới. Lần sửa JSON bỏ lịch sử dài để tránh nhầm nguồn.
Không đưa JWT, thông tin đăng nhập hay entity tài khoản vào prompt.
Ngữ cảnh nằm trong snapshot hội thoại hiện có, phân vùng theo tài khoản/phiên qua ChatMemoryStore.

## 2. Hợp đồng AI

JSON gồm đúng các trường:

- contextVersion: phiên bản đầu vào, backend từ chối bản cũ.
- intent: enum ChatIntent.
- contextMode: new, refine, more, current.
- set: thông tin khách vừa cho hoặc sửa rõ ràng; trường không nhắc có thể bỏ qua hoặc để null, backend bỏ qua null/chuỗi rỗng/topics rỗng.
- clear: trường khách muốn bỏ; chỉ clear mới xóa điều kiện đã lưu.
- evidence: chuỗi `current_message` là con trỏ nguồn chung cho patch; backend tự gắn nguyên văn tin nhắn hiện tại cho từng trường set/clear trước khi kiểm tra và lưu nguồn.
- memoryNote: ghi chú ngữ nghĩa gồm text/evidence từ lời khách hoặc null; evidence dùng cùng con trỏ `current_message`. Thiếu căn cứ thì bỏ ghi chú, không làm hỏng kế hoạch hợp lệ.
- calls: tối đa ba công cụ đã đăng ký, chỉ một bước tìm sách cho số lượng/ngân sách chung.

Trường ngữ cảnh gồm topics/topicMatch, query (thay cho topics khi mục đích phức tạp), purpose,
recipient, preferredQuery, excludedQuery, format, giá tối thiểu/tối đa và tính bao gồm biên,
tổng ngân sách, số cuốn, rating, tác giả, danh mục, tên sách, sắp xếp, trình độ và tuổi/lớp nếu khách đã cho.
Chủ đề hoặc mục đích đọc đủ để giới thiệu; người nhận và ngân sách bổ sung.

Gemini nhận JSON Schema qua generationConfig.responseJsonSchema.
Schema provider ràng buộc envelope, intent, mode, số công cụ và các kiểu cơ bản;
set/evidence là map thưa để tránh tự điền hàng chục trường tùy chọn.
Schema chi tiết của bộ lọc đi cùng prompt; ChatTurnContext kiểm tra tên trường,
kiểu, enum, phạm vi và nguồn trước khi ghép. Tham số công cụ được registry kiểm tra riêng.
Backend vẫn kiểm tra kiểu/enum/phạm vi, tiền và số lượng trong nguồn, phiên bản và tham chiếu.
Con trỏ nguồn tránh lỗi AI chép sai dấu câu hoặc trích nhầm lượt cũ; chỉ tên `current_message` được phân giải.
Trích dẫn nguyên văn vẫn được backend chấp nhận để tương thích; giá trị trích dẫn sai và các con trỏ khác vẫn bị từ chối.
Một lần sửa hợp đồng được phép trước khi chạy công cụ. Thất bại không sửa context.
Ngoại lệ là ghi chú mềm tùy chọn: ghi chú sai nguồn/kiểu bị bỏ riêng, còn set/clear và công cụ vẫn phải qua mọi kiểm tra.
Schema hợp lệ không bảo đảm AI hiểu đúng ý: cần đánh giá hội thoại thực tế.

## 3. Bộ nhớ chung

toolSearch là nguồn bộ lọc duy nhất. contextSources lưu mã lượt và câu làm căn cứ.
conversationNotes giữ tối đa tám ghi chú mềm có nguồn, không được ghi đè điều kiện đã xác nhận.
Backend tự ghi thứ tự hiển thị, focus, sách đã giới thiệu và kết quả truy xuất.
Lượt tìm thêm rỗng giữ danh sách gần nhất để vẫn hiểu “cuốn thứ hai”; nhu cầu mới xóa tham chiếu cũ.
“Xem lại” được phép trả sách đã xem, khác với “thêm/tiếp” phải loại sách đã giới thiệu.
Tham chiếu “hai cuốn đó/cả hai cuốn” ưu tiên cả danh sách, không bị cụm “cuốn đó” ghi đè thành focus một cuốn.

- new: bắt đầu nhu cầu mới; không xóa lịch sử hội thoại hoặc danh sách đã từng giới thiệu.
- refine: chỉ đổi trường được nêu, giữ trường không nhắc.
- more: giữ bộ lọc, loại sách đã giới thiệu; có thể nhận số cuốn mới khi khách nói rõ.
- current: xem thông tin, so sánh, chính sách hoặc chuẩn bị thao tác với ngữ cảnh hiện tại.

Mỗi lượt làm việc trên bản sao. Chỉ commit khi thực thi thành công và contextVersion vẫn khớp.
ChatService khóa từng phiên trong tiến trình và chống lặp requestId; snapshot lưu phiên bản qua restart.
Triển khai nhiều backend đồng thời cần khóa/CAS ở tầng lưu trữ, ngoài cơ chế khóa trong tiến trình hiện tại.

## 4. Truy xuất

AI đề xuất bộ lọc; backend đối chiếu lời khách và dữ liệu đã duyệt để bảo vệ điều kiện bắt buộc.
Backend lọc catalog thật theo giá, tồn kho, chủ đề OR/AND, dạng sách, loại trừ và điều kiện đã cho.
Mục tiêu “học kỹ năng giao tiếp” được giữ trong query/topics hoặc purpose; sách đúng yêu cầu cần có căn cứ giao tiếp và hướng dẫn/nguyên tắc/phương pháp trong nội dung chính đã duyệt.
Truyện chỉ suy ngẫm về ứng xử không được tính là sách dạy kỹ năng để lấp đủ số cuốn. Các lựa chọn OR vẫn được xét theo từng nhánh.
Nhu cầu giải trí chung dùng tác phẩm kể chuyện có căn cứ, kể cả khi AI dùng query “giải trí” hay “sách thú vị”;
chủ đề cụ thể như khoa học để giải trí vẫn được giữ, không bị ép thành truyện.
Ưu tiên mềm dùng để xếp hạng; không tự trở thành loại trừ.
Tổng ngân sách kiểm tra cả nhóm; có thể chọn hai cuốn giá khác nhau.
Mặc định tối đa ba cuốn, số cụ thể theo khách. Không tự bổ sung sách gần chủ đề khi chưa được phép.

| searchOutcome | Nghĩa |
|---|---|
| matched | Có lựa chọn đáp ứng điều kiện |
| partial | Thiếu số cuốn, có sách gần chủ đề được cho phép hoặc còn giới hạn thông tin |
| no_match | Chưa có sách đáp ứng điều kiện |
| exhausted | Có sách đáp ứng nhưng đã giới thiệu hết khi khách yêu cầu thêm |

Lỗi DB/AI là trạng thái thực thi FAILED, không được ghi thành no_match.

## 5. Gói dữ liệu viết câu trả lời

ChatAnswerPacket gồm conversation, confirmedNeed, result và responseRequirements.
Mỗi sách có ID, tên, tác giả, giá thật, tình trạng hàng, căn cứ từ approvedCatalog,
phân loại selected/nearby, giới hạn phù hợp và các trường chưa biết.
AI trả intro, closing, reasons theo productId/evidenceField/evidence, cùng lựa chọn ưu tiên nếu có căn cứ.

Backend giữ tên, giá, thẻ sách, action và các lưu ý đã xác minh.
Lời giải thích dùng căn cứ sai, ID lạ hoặc nhận xét không được dữ kiện hỗ trợ sẽ bị loại,
giữ câu backend. Không gọi AI viết thêm cho kết quả rỗng hoặc truy vấn dữ kiện đơn giản.
Dạng truyện cũng phải có căn cứ; chưa xác nhận được ưu tiên truyện chữ thì nói rõ giới hạn,
không kết luận sách không đáp ứng chỉ từ mô tả thiếu dữ kiện.
Lời dẫn structured do backend giữ để bảo toàn các điều kiện và tránh hai đoạn mở đầu; AI tập trung vào lý do và lời kết có ích.
Câu hỏi cụ thể về bài tập/đáp án được trả trực tiếp theo dữ kiện đã duyệt, không gọi AI viết lại.
Có bài tập không tự chứng minh có đáp án; lời giải vụ án không phải lời giải bài tập.
Trình độ beginner được giữ ngay cả khi khách không ghi recipient; hồ sơ thiếu mức đọc phải nêu chưa xác nhận độ khó.
AI không được tự gán nội dung nền tảng hoặc kết luận hợp người mới chỉ vì cuốn sách mang tên giáo trình.
Vì vậy mỗi lượt thường có một lần đọc hiểu và một lần viết; không có lần gọi riêng chỉ để lưu memory.

Ngân sách toàn lượt AI là 45 giây; structured request không nhân ba lượt retry trên mỗi key.
Timeout/quota/lỗi định dạng có phản hồi có giới hạn thay vì để chat chờ vô hạn.

## 6. Thao tác

Dùng công cụ hiện có cho cart/order/wishlist/review/support.
AI đề xuất công cụ và tham số; lệnh rõ ràng về giỏ, mua, tra đơn và chính sách có thể đi thẳng tới cùng công cụ backend trước khi gọi AI. Backend vẫn xác minh quyền tài khoản và tham chiếu.
Mua/hủy đơn/thay đổi giỏ vẫn qua bước xem lại/xác nhận tương ứng; lời AI không phải biên nhận đã thực hiện.

## Kịch bản nghiệm thu

| Nhóm | 5 tình huống |
|---|---|
| Hiểu yêu cầu | OR giữa thể loại; AND cùng một cuốn; mục đích giải trí đủ rõ; thiếu chủ đề cần hỏi; câu ngân sách bổ sung |
| Ngữ cảnh | Ưu tiên mềm giữ giá; thu hẹp chủ đề giữ giá; bỏ giá giữ ưu tiên; tiếp không lặp; reload/restart giữ trạng thái |
| Truy xuất | Giá dưới biên; tổng ngân sách không chia đều; thiếu số cuốn; không có sách phù hợp; đã giới thiệu hết |
| Lời tư vấn | Căn cứ đúng; thiếu độ dễ đọc; thiếu độ tuổi; mô tả không có bài tập; AI viết sai thì giữ câu backend |
| Thao tác/lỗi | Cuốn thứ hai rồi mua cuốn này; đơn khác giỏ; lỗi DB giữ memory; JSON sai chỉ sửa một lần; retry request không chạy lặp |

Kiểm thử tự động: ChatStructuredPipelineTest, AiGatewayStructuredTest và các bộ Chat* hiện có.
Biên bản thử Gemini trực tiếp nằm trong docs/chatbot-evaluation.
Đợt kiểm tra bổ sung ngày 10/10: [lỗi thực tế, sửa đổi và hồi quy](chatbot-evaluation/quality-followup-2026-10-10.md).

Tham khảo hợp đồng provider:
[Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output),
[generateContent / GenerationConfig](https://ai.google.dev/api/generate-content#v1beta.GenerationConfig).


## Bổ sung ngày 11/10/2026

Lời dẫn/câu kết không mang căn cứ riêng cho sách nên cũng phải qua guard phát biểu. Reason chỉ thay nội dung mô tả; các câu giới hạn đã xác nhận vẫn được giữ. Nguồn gốc văn học bắt buộc không suy ra từ ngôn ngữ bản dịch hoặc người đọc.

AI viết có ngân sách riêng 8 giây, dùng chung deadline AI của lượt. Lỗi viết giữ câu backend; timeout nội bộ không cooldown khóa Gemini đang hoạt động. Metadata có thời gian đọc hiểu, thực thi và viết để nhận diện nút thắt.

Hồ sơ đọc được batch trong phạm vi một lượt có công cụ catalog và dọn trong finally. Không cache giá/tồn kho hay duy trì hồ sơ qua lượt. Từ bản chuẩn bị chủ đề, phạm vi đọc hồ sơ được mở trước bước đọc hiểu để cấp nhãn catalog cho AI, và dọn trong finally.

Chi tiết kiểm thử, lỗi thực tế, giới hạn còn lại và số đo: [biên bản 11/10](chatbot-evaluation/quality-hardening-2026-10-11.md).


## Gợi ý chủ đề khi chưa rõ hướng đọc

`respond(kind=clarify, topic=preferences/gift)` có thể trả `suggestedTopics` (1–6 nhãn ngăn bằng `|`), `suggestionSource` (ID nguồn do backend cấp trong `topicContextSources`) và `suggestionContext` (trích ngắn tùy chọn). Nguồn gồm lời khách hiện tại/4 lượt gần nhất còn thông tin cụ thể; loại câu hỏi mua sách chung chung và tin riêng tư. Backend kiểm tra nguồn, bỏ câu mua sách chung chung và hiển thị các hướng dưới dạng lựa chọn mềm cùng chip có sẵn. Không ghép các hướng đề xuất vào `set`. Backend chuẩn bị sách trên bản sao riêng; chỉ hiển thị sách và xác nhận chủ đề khi khách chọn. Nếu nhu cầu đọc đã đủ rõ, tiếp tục tìm sách như trước; không bắt cung cấp người nhận/ngân sách.

Chi tiết hành vi và kiểm thử: [gợi ý chủ đề 11/10](chatbot-evaluation/topic-suggestions-2026-10-11.md).

## Chủ đề có sách sẵn và chuẩn bị kết quả

Trước khi hiện chip, backend xác minh mỗi hướng có ít nhất hai sách phù hợp với bộ lọc và nhãn nội dung đã duyệt. Tìm trước trên bản sao ngữ cảnh, lưu pool ID đã xếp hạng riêng theo cuộc hội thoại trong 5 phút; không đánh dấu sách đã xem. `catalogTopicHints` giúp AI chọn các hướng thực có sách. Chọn nguyên nhãn/số thứ tự đơn giản có thể dùng pool này, bỏ AI đọc/viết, nhưng vẫn kiểm tra dữ liệu mới và dựng giá/tồn kho/thẻ từ catalog hiện tại. Điều kiện mới, TTL hoặc dữ liệu thay đổi thì tìm lại. Metadata `preparedTopic=hit/miss` cùng thời gian từng giai đoạn cho phép kiểm tra tốc độ.

Hành vi, lỗi tìm được, 154 kiểm thử và bằng chứng UI: [chuẩn bị chủ đề 11/10](chatbot-evaluation/catalog-topics-2026-10-11.md).

## Hồi quy từ bộ 50 tình huống thực tế ngày 11/10/2026

- Chủ đề chuyên biệt trong lời khách vẫn là điều kiện chọn sách khi AI dùng truy vấn rộng hơn. Cần căn cứ trong nội dung chính đã duyệt; từ trùng một phần, danh mục rộng hoặc câu mô tả phủ định không đủ. Không có sách thiết kế động cơ tên lửa/làm vườn thì trả thiếu kết quả, không lấy truyện hay sách lịch sử làm kết quả đúng chủ đề. Mở rộng chỉ sau khi khách đồng ý và tiếp tục loại sách đã giới thiệu.
- Số lượng mua được giữ nguyên từ lời khách, kể cả vượt 99 hoặc tồn kho. Backend kiểm tra số nguyên dương, tồn hiện tại, số đã có trong giỏ và giới hạn 99 của API trước khi tạo action. Không hạ số lượng về 1 để qua schema. Lệnh mua/tra đơn của khách chưa đăng nhập trả hướng dẫn và action đăng nhập; không gọi AI để quyết định quyền.
- Câu đọc thông tin/chính sách dùng `current` và không được sửa bộ lọc tìm sách. Ngữ cảnh tác vụ đọc được lưu qua các lượt chính sách và lỗi provider; “còn [tên sách] thì sao” kế thừa tác vụ đọc gần nhất. Chọn cuốn thứ hai chỉ chọn sách, không tự mua. Câu “đánh giá cuốn này” cần hỏi xem hay viết trước khi mở form.
- Khi hỏi rõ, thông tin sách trả cả tác giả, giá sau giảm, phần trăm giảm và tồn kho. Câu hỏi nhiều chính sách trả từng ý theo cấu hình hiện tại; tổng tiền sách và phí ship được tính riêng tại cả hai phía của mốc miễn phí.
- Tin chứa mật khẩu/OTP nhận hướng dẫn đúng về tài khoản, không lặp lại thông tin và không gửi sang AI. Dữ liệu giao hàng vẫn hướng về form đặt hàng.
- Khi khách hỏi lại các hướng quà trong cùng hoàn cảnh, menu đã kiểm chứng được giữ và kiểm tra lại bộ lọc/tồn kho; không tự loại chính pool đang chuẩn bị như sách đã giới thiệu. Đổi người nhận hoặc hoàn cảnh thì không tái dùng menu cũ. “Còn cuốn kia thì sao?” kế thừa trường vừa đọc trên cuốn còn lại trong danh sách so sánh.
- Dấu tiếng Việt không làm câu “Giờ mua thêm quà…” thành lệnh giỏ. Các con số là một phần tên sách được bỏ khỏi vùng phân tích số lượng; số lượng ghi ngoài tên sách vẫn được giữ.
- Lời cấm xóa sách khỏi giỏ ở dạng một mệnh đề được xác nhận là không thay đổi giỏ, không giả định sách đang có trong giỏ. Câu nhiều mệnh đề/câu hỏi vẫn qua bước đọc hiểu. Khi đổi “từ 3 cuốn thành 2 cuốn”, số cuối 2 là số lượng mới; tên số như “1984” không được xóa luôn số lượng cùng giá trị nằm ngoài tên.
- Truy hồi tên sách ưu tiên tên đầy đủ rồi mới dùng bí danh nguyên cụm trong tên catalog, giữ các bộ lọc hiện tại. Bìa cứng/mềm là thuộc tính ấn bản; chưa có trường đã duyệt thì nêu thiếu thông tin, không suy ra từ ảnh hoặc mô tả tác phẩm.

Các bộ hồi quy: `ChatLiveTopicRegressionTest`, `ChatLiveQuantityRegressionTest`, `ChatLiveContextRegressionTest`, `ChatLiveEditionRegressionTest`, `ChatLiveFactsPolicyRegressionTest`, `ChatPrivateInputRegressionTest`, `ChatCommandGuardRegressionTest`, cùng toàn bộ kiểm thử backend hiện có. Kết quả gọi chatbot thật phải ghi riêng độ phủ API, fixture chưa có và luồng UI chưa thực hiện; không tính tình huống thiếu điều kiện thành PASS.


### Lưu và nhắc lại chủ đề (12/10/2026)

Menu chủ đề được đánh số, tối đa 6 hướng có ít nhất hai sách đáp ứng điều kiện hiện tại; không thêm hướng không liên quan để đủ số. Khi chưa có hoàn cảnh cụ thể, backend bổ sung các nhãn từ catalog đã duyệt, kiểm tra lại giá, tồn, nội dung và tránh hai nhãn có cùng tập sách.

`topicMenus` giữ tối đa 4 menu đã hiển thị, tách khỏi bộ lọc đọc và danh sách sách. Chọn một hướng không xóa menu; hỏi lại sau khi chọn hoặc xen câu hỏi chính sách trả đúng menu, không gọi AI để dựng lại. Tham chiếu “chủ đề thứ tư/thứ sáu” dùng menu thật; số trần sau danh sách sách không chọn nhầm chủ đề cũ. Thêm hướng khác bỏ qua tên đã giới thiệu; hết hướng thì nói rõ và vẫn cho nhắc lại menu trước. Nhu cầu mới hoặc đổi người nhận/mục đích xóa menu cũ khỏi ngữ cảnh đang dùng.

Menu cùng trạng thái hội thoại được lưu phía server theo chủ sở hữu. Snapshot cũ được nâng cấp một lần từ chip của phản hồi `respond/NEEDS_INPUT` đã lưu phía server, không tin lịch sử do client cung cấp để dựng menu. Nhắc lại chỉ xác nhận đã từng gợi ý; giá/tồn và điều kiện được kiểm tra lại khi chọn sách.

Lời dẫn gợi ý được rút gọn, không nhắc quy trình chuẩn bị sách ở mọi lượt. Nhắc lại chỉ liệt kê; chọn hướng đi thẳng vào sách; bỏ nhãn nhóm khi chỉ có một nhóm kết quả đầy đủ. Dữ kiện thiếu và phân biệt sách gần chủ đề vẫn giữ nguyên.

### Hỏi sâu nội dung sách (12/10/2026)

Sau khi giới thiệu sách, các câu ngắn “cụ thể hơn”, “nói kỹ hơn”, “chi tiết hơn”, “đi sâu hơn” được neo trực tiếp vào cuốn đang chọn hoặc danh sách đang xem qua `getProducts(field=explanation, contextMode=current)`. Không tìm sách mới, không sửa bộ lọc và không phụ thuộc AI chọn lại tác vụ. Tham chiếu tên/cuốn thứ hai/cả danh sách được giữ; khi chưa có sách thì hỏi xác định. Câu hỏi sách rõ ràng có thể trở lại sau câu hỏi chính sách; câu ngắn sau chính sách hoặc menu chủ đề không tự lấy sách cũ ra giải thích.

Yêu cầu tìm sách khác, thay điều kiện, câu phủ định hoặc câu hỏi phí giao hàng vẫn theo luồng riêng. Giải thích chỉ dùng mô tả và dữ kiện đã duyệt; thiếu mục lục/tình tiết thì nêu thiếu thông tin. “Giới thiệu nội dung cuốn [tên sách]” không được hiểu thành hỏi sách có đề cập đến chính tên của nó hay không. Kiểm thử tập trung vào `ChatLiveContextRegressionTest` và các ca thông tin nội dung liên quan trong `ChatStructuredPipelineTest`, `ChatLiveFactsPolicyRegressionTest`.
