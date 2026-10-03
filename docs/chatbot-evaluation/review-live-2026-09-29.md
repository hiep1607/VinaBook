# Nhận xét kết quả kiểm thử chatbot VinaBook — 29/09/2026

Đối chiếu [32 câu trả lời nguyên văn](results-exact-live-2026-09-29.md) với [mục tiêu từng bộ](manual-test-suites.md), thẻ sách/action trong response, catalog công khai và chính sách đã duyệt của app. `Đạt` nghĩa là trả lời đúng trọng tâm trong phạm vi đã thử; `Thiếu` nghĩa là có thông tin đúng nhưng chưa giải quyết hết câu hỏi; `Sai` nghĩa là hiểu sai ý, mất ngữ cảnh hoặc không dùng dữ kiện đã có. Đây là nhận xét chất lượng, không phải tỷ lệ chính xác thống kê.

**Tổng hợp:** 11 Đạt, 15 Thiếu, 6 Sai trong 32 lượt. Cả 32 lượt đều HTTP 200 và `fallback=false`; các số này chỉ xác nhận luồng gọi chạy được. Năm câu Bộ 5 chưa thử vì bước tải ảnh bìa sách mới qua Cloudinary lỗi, nên chưa thể kết luận khả năng cập nhật catalog theo thời gian thực.

## Bộ 1 — Hiểu nhu cầu tự nhiên

| Mã | Nhận xét | Lý do |
|---|---|---|
| B1-01 | Thiếu | Có Harry Potter và Hành Trình Về Phương Đông khá gần ý “hành trình”; “10 Vạn Câu Hỏi Vì Sao” lạc nhu cầu đọc truyện về chuyến đi. |
| B1-02 | Thiếu | Clean Code và Pragmatic Programmer đúng trọng tâm; Giáo Trình Lập Trình Java ít trực tiếp hơn mục tiêu viết mã dễ bảo trì. Tổng giá ba lựa chọn không cần thiết. |
| B1-03 | Thiếu | Khởi Nghiệp Tinh Gọn sát nhu cầu kiểm chứng ý tưởng; sách pháp lý chỉ liên quan gián tiếp và Blockchain Fundamentals lạc hướng. |
| B1-04 | Đạt | Đắc Nhân Tâm phù hợp với giao tiếp tự tin. Catalog lại gắn sách này vào “Sách Truyện, tiểu thuyết”; đây là lỗi dữ liệu nguồn. |
| B1-05 | Thiếu | Người dùng xin “vài lựa chọn” nhưng chỉ có một; cùng catalog còn Quẳng Gánh Lo Đi Và Vui Sống giá sau giảm 90.160đ. |
| B1-06 | Thiếu | Gợi ý sách thiếu nhi hợp hướng, nhưng không có căn cứ độ tuổi cụ thể để bảo đảm từng cuốn phù hợp bé 8 tuổi. |

## Bộ 2 — Dữ liệu sách và so sánh

| Mã | Nhận xét | Lý do |
|---|---|---|
| B2-01 | Thiếu | Bảng tác giả/giá có ích nhưng không trả lời “cuốn nào đọc nhẹ nhàng hơn”. |
| B2-02 | Đạt | Phân biệt rõ Clean Code cho lập trình và Khởi Nghiệp Tinh Gọn cho startup, gắn với mục tiêu người hỏi. |
| B2-03 | Đạt | Nêu đúng giá sau giảm 63.200đ và tồn kho 230 trong catalog tại lúc thử. |
| B2-04 | Sai | Trả “chưa có thông tin thư mục đã duyệt” dù thẻ sách đi kèm có tác giả Antoine de Saint-Exupéry và tồn 160; catalog công khai cũng cho thấy sách đang bán. Không trả lời cả hai ý. |
| B2-05 | Đạt | Chỉ trả bản Sherlock Holmes có tên khớp, không lẫn sách chỉ nhắc nhân vật trong mô tả. |
| B2-06 | Thiếu | Không bịa ISBN/số trang, nhưng cần nói rõ từng trường chưa có dữ liệu xác nhận; câu chung về “thông tin thư mục” quá mơ hồ. |
| B2-07 | Sai | Catalog không có “Nhật Ký Atlantis 2099”; bot không bịa giá nhưng lại hỏi tên sách đã được cung cấp thay vì báo không tìm thấy. |

## Bộ 3 — Hội thoại nhiều lượt

| Mã | Nhận xét | Lý do |
|---|---|---|
| B3-A1 | Thiếu | Dế Mèn Phiêu Lưu Ký phù hợp; chỉ đưa một lựa chọn và giải thích rằng xếp theo số bán cao nhất dù người dùng không yêu cầu tiêu chí này. |
| B3-A2 | Thiếu | Không lặp cuốn cũ, nhưng tuyên bố chưa tìm thấy cuốn khác rất sớm; từ response chưa chứng minh catalog đã hết sách cùng chủ đề. |
| B3-A3 | Đạt | Chuyển sang trinh thám và trả Conan cùng Sherlock Holmes. |
| B3-A4 | Đạt | Giữ chủ đề trinh thám và chỉ còn Conan dưới 150.000đ. |
| B3-B1 | Thiếu | Nhận đúng hai cuốn, nhưng so sánh mới là bảng dữ liệu cơ bản, chưa giúp chọn theo nội dung. |
| B3-B2 | Sai | “Cuốn nào rẻ hơn?” có thể xác định Nhà Giả Kim từ bảng ngay trước (63.200đ < 69.920đ); bot lại hỏi tên hai cuốn. |
| B3-B3 | Sai | Tiếp tục hỏi tên hai cuốn thay vì trả tồn kho Nhà Giả Kim là 230. |
| B3-B4 | Sai | Không nối “cuốn đó” về Nhà Giả Kim. Việc không tự đặt đơn là đúng, nhưng mất tham chiếu làm hỏng luồng mua. |

## Bộ 4 — Hỏi rõ, chính sách, thao tác

| Mã | Nhận xét | Lý do |
|---|---|---|
| B4-C1 | Đạt | Hỏi đúng dữ kiện còn thiếu là chủ đề. |
| B4-C2 | Thiếu | Giữ chủ đề tâm lý và có một số sách liên quan, nhưng danh sách trộn cả sách thiên về tâm linh; nên xếp theo mức liên quan hoặc hỏi nhánh người dùng thích. |
| B4-C3 | Đạt | Giữ chủ đề, không hỏi lại và chọn sách 90.160đ dưới mức 120.000đ. Cụm “giữ tổng trong ngân sách” chỉ hợp nếu người dùng đang chọn một giỏ sách. |
| B4-D1 | Đạt | Hỏi đúng tên cuốn thứ hai để so sánh. |
| B4-D2 | Thiếu | Giữ được Nhà Giả Kim và thêm Hoàng Tử Bé, nhưng chỉ xuất bảng cơ bản, chưa có nhận xét về khác biệt nội dung. |
| B4-P1 | Thiếu | Mức 20.000đ và ngưỡng miễn phí 300.000đ khớp chính sách, nhưng chưa trả trực tiếp: đơn 290.000đ chịu phí 20.000đ, đơn 310.000đ miễn phí. |
| B4-P2 | Đạt | Nêu đúng chính sách đã duyệt: lỗi in, rách hoặc giao sai được đổi trả trong 7 ngày. |
| B4-P3 | Sai | Hỏi quyền xem đơn khi chưa đăng nhập nhưng bot lại hỏi “cuốn sách nào”; định tuyến sai sang ngữ cảnh sản phẩm. |
| B4-P4 | Đạt | Chọn đúng Nhà Giả Kim, xin xác nhận và nói giỏ chưa đổi; action yêu cầu xác nhận. |
| B4-P5 | Thiếu | Nói rõ chat chưa đặt đơn và đưa sang trang sản phẩm, an toàn trong phạm vi tính năng hiện tại. Action chỉ mở trang sách, chưa mang theo số lượng 2. |
| B4-P6 | Thiếu | Yêu cầu đăng nhập là đúng; sau đăng nhập bot lại gợi ý câu hỏi tra cứu vị trí đơn thay vì tiếp tục ý định hủy đơn. |

## Ưu tiên sửa từ kết quả này

1. **Giữ thực thể trong hội thoại:** lưu ID hai sách đã so sánh, cuốn rẻ hơn và cuốn vừa được chọn để xử lý “cuốn đó” qua nhiều lượt.
2. **Định tuyến đúng ý định:** câu hỏi về đơn hàng phải vào luồng đơn hàng; tên sách đã nêu phải được tìm trong catalog trước khi hỏi lại.
3. **Trả lời đủ các ý được hỏi:** dùng tác giả, giá, tồn kho, trạng thái có sẵn trong catalog; chỉ báo thiếu với trường thật sự chưa xác nhận. So sánh cần kết luận theo tiêu chí người dùng, chính sách cần tính kết quả cho từng tình huống.
4. **Lọc gợi ý theo mức liên quan:** loại sách lệch chủ đề, đưa vài phương án khi người dùng yêu cầu, không xếp theo doanh số nếu không có lý do từ câu hỏi. Không cộng tổng giá các cuốn đang được đề xuất như các lựa chọn thay thế.
5. **Sửa dữ liệu catalog:** Đắc Nhân Tâm đang được phân loại là truyện/tiểu thuyết. Cần dữ liệu độ tuổi đã duyệt trước khi khẳng định sách phù hợp cho trẻ 8 tuổi.

Các nhận định về nguyên nhân ở trên là suy luận từ response, thẻ sản phẩm và dữ liệu công khai; báo cáo này chưa phân tích mã nguồn điều phối chatbot.
