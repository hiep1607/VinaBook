# Dialogue v3 — bàn giao và bằng chứng kiểm thử

Ngày: 10/09/2026. Môi trường local: storefront `http://localhost:4200`, backend `http://localhost:8080`. Không push/deploy production, không đặt đơn thật trong tài khoản người dùng.

## Phạm vi đã thực hiện

Xây lại runtime hội thoại theo state có cấu trúc, tách focus và shortlist, một lần gọi model cho lượt cần tư vấn, kiểm tra ID và dựng dữ kiện/thẻ ở server. Bỏ các nhánh điều phối V2 không còn dùng trong ChatService. Giá được phân tích riêng để không nhầm năm trong tên sách với ngân sách. Lọc toàn catalog trước khi sort/limit.

Mua bằng lời nói mở bản nháp; giải thích thông tin giao hàng; chọn địa chỉ; sửa sách/số lượng/thông tin; xem lại rồi xác nhận bằng chat hoặc nút. Không chốt bằng “ok”. Lưu địa chỉ có đồng ý rõ ràng. Lựa chọn sách của khách có thể tiếp tục sau đăng nhập trong cùng trang. Không chuyển lịch sử/địa chỉ từ tài khoản này sang tài khoản khác. Session riêng vẫn giữ khi reload. Khung chat cao hơn, bảng so sánh có bảng HTML an toàn và cuộn ngang.

## Kiểm thử tự động

- Backend: **156 test, 0 failure, 0 error** (`Chat*Test`), Java 17/Maven. Có state nhiều lượt, request retry, account/session isolation, thiếu sách/thiếu rating, tên sách/năm/tiền, chọn/mua, quote ownership/expiry/idempotency/rollback và kiểm tra phản hồi AI. Model/repository dùng mock trong các test này.
- Frontend: **35 test, 0 failure**, Chrome Headless. Có đổi tài khoản, phản hồi muộn, no-address/one-address/multiple-addresses, đổi sách, số lượng, thông tin riêng, “ok” so với xác nhận, địa chỉ opt-in, quote hết hạn, retry chưa rõ kết quả, guest sign-in resume và bảng so sánh.
- Production build storefront: thành công. Còn cảnh báo bundle ban đầu khoảng 883 kB so với budget 600 kB; chatbot CSS 12,29 kB so với warning 12 kB; product-detail CSS cũng có warning. Không tăng budget để che cảnh báo.
- `git diff --check` trên các file chatbot/docs được sửa: sạch.

## Kiểm thử với backend/Gemini thật

Nguồn chính xác là hai tệp 100 câu gốc của người dùng. Harness lưu nguyên request/response, không chấm HTTP 200 thành câu trả lời đúng.

1. [Toàn bộ 100 câu](../work/chatbot-regression-1788973250299/results.json): 103 lượt (câu 99 lặp 4 lần), 0 lỗi HTTP; 21 câu trả lời từ Gemini, 82 local; 1 fallback tại câu 30. Trung bình toàn bộ lượt 1,39 giây, chậm nhất 23,18 giây. Đây là bản trong quá trình hoàn thiện, có lỗi nội dung được ghi nguyên, không sửa log để làm đẹp kết quả.
2. [Chạy lại nhóm liên quan sau sửa](../work/chatbot-regression-1788973919081/results.json): 19 câu/22 lượt, 0 lỗi HTTP. Xác minh câu 29/66 không bị bỏ thẻ khi model trả nhãn ADVICE kèm ID hợp lệ; câu 33 hỏi lại cặp sách thay vì chọn tùy ý trong 3 sách; câu 40 nhận “cuốn sách đó” và trả giá; câu 54 lọc bán chạy kèm rating; câu 100 không tự tạo gợi ý sau kết quả Python rỗng.
3. [Kiểm tra lại tư vấn và chọn một cuốn](../work/chatbot-regression-1788974068085/results.json): 4 lượt (30, 32–34), HTTP 200. Câu 30 nhận phản hồi Gemini, không fallback sau khi làm rõ schema ADVICE. Câu 34 trả một thẻ, nhưng model còn sinh câu kết nhắc xử lý đơn không cần thiết; đã bổ sung bộ lọc và test hồi quy cho chính câu này sau lượt chạy.
4. [Kiểm tra không lộ mã sản phẩm trong tư vấn](../work/chatbot-regression-1788974157077/results.json): câu 66, Gemini, HTTP 200, không fallback/không mã nội bộ. Model có thể chọn trả lời lộ trình chung không kèm sách khi người dùng chỉ nói muốn học backend; không coi việc không có thẻ ở mọi câu là lỗi.

Sau các lần chạy, đã thêm bộ lọc không cho intro/closing AI lộ `productId`/CATALOG hoặc hứa tạo/xử lý đơn. Câu về đơn phải do state checkout thực hiện, không từ lời model. Bộ test backend cuối cùng bao gồm các lỗi mới này. **Chưa chạy lại toàn bộ 100 câu trên snapshot cuối cùng**; không suy diễn thành 100/100 đạt, không chấm điểm tổng mới.

## Kiểm tra trên trình duyệt thật

Dùng giao diện local, không inject dữ liệu hoặc state ứng dụng:

- Tìm/so sánh hai sách lập trình rẻ nhất: bảng hiện Cấu Trúc Dữ Liệu 83.600đ và Cơ Sở Dữ Liệu 90.160đ, đúng hai thẻ; rating thiếu được ghi rõ.
- Hỏi “Nói rõ hơn về cuốn thứ hai”: Gemini trả nội dung Cơ Sở Dữ Liệu, chỉ thẻ tương ứng.
- Xen câu hỏi phí giao hàng rồi “giúp t mua”: vẫn chọn đúng cuốn đang nói tới.
- Reload trang, mở chat, “giúp t mua”: lịch sử và sách đang tập trung vẫn đúng.
- Quan sát ảnh giao diện: chat gần hết chiều cao màn hình, bảng nằm trong vùng cuộn riêng. Chưa thực hiện chốt một đơn thật qua browser; confirmation/rollback/ownership được kiểm tra bằng test tự động có mock.

## Giới hạn cần giữ minh bạch

- Đây là conversational retrieval trên catalog, **chưa phải vector RAG hoặc chỉ mục toàn văn**. Mô tả sách ngắn không đủ xác minh chương, trích dẫn, độ dài/ấn bản, mọi câu hỏi chuyên sâu. Không bổ sung sách/nội dung/rating giả để vượt test.
- Catalog hiện thiếu sách Python, Spring Boot, tiếng Anh và rating ở nhiều nhóm. Bot giữ điều kiện và hỏi trước khi nới; các chuỗi hỏi tiếp khi không có kết quả vẫn có thể lặp thông báo. Cần bổ sung nguồn thật hoặc cải thiện cách hỏi tiếp, không tự gán sách Java là sách Python.
- Độ trễ model và chất lượng diễn đạt vẫn biến thiên; HTTP thành công không đảm bảo tư vấn hay. Các kiểm tra deterministic chỉ kiểm soát được ID/dữ kiện/thao tác, không chứng minh mọi nhận định ngôn ngữ đều đúng.
- Bộ nhớ hội thoại/báo giá nằm RAM có TTL, phù hợp môi trường hiện tại một backend. Cần kho state dùng chung và kiểm thử concurrent đa instance trước khi scale.
- Chat checkout hiện một đầu sách mỗi bản nháp, COD; địa chỉ device-local. Chưa hỗ trợ thanh toán online trong chat hoặc giỏ nhiều đầu sách.

Kiến trúc/retention đầy đủ: [chatbot.md](chatbot.md).
