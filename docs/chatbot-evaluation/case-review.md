# Nhận xét từng trường hợp
Chấm thủ công theo đoạn trả lời và dữ liệu CSV, không chỉ theo nhãn intent. Đạt = đáp ứng phần yêu cầu quan sát được; Một phần = liên quan nhưng thiếu; Không đạt = sai hoặc không giải quyết yêu cầu. Không chứng nhận phần nguyên văn đã bị cắt. Các trường hợp quan trọng đối chiếu JSON kiểm chứng. Không dùng tỷ lệ này như benchmark.

## #1 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Xin chao
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 148 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #2 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Chào bạn nha
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 86 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #3 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Hello
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 77 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #4 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Hi shop
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 76 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #5 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Bạn là ai?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 80 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình là trợ lý mua sách VinaBook. Mình có thể tìm, so sánh sách, tư vấn theo ngân sách, giải đáp chính sách và tra cứu đơn hàng sau khi bạn đăng nhập.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #6 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Bạn làm được gì?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 90 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình là trợ lý mua sách VinaBook. Mình có thể tìm, so sánh sách, tư vấn theo ngân sách, giải đáp chính sách và tra cứu đơn hàng sau khi bạn đăng nhập.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #7 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Cảm ơn bạn nhiều nhé
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 79 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Rất vui được giúp bạn. Hãy mô tả người đọc, chủ đề và ngân sách để mình chọn sách chính xác hơn.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #8 — CHAO — Một phần
- Câu hỏi: Tạm biệt nha
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 86 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Tạm biệt nhưng trả lời chào mời tìm sách.

## #9 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Chào buổi sáng
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 86 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #10 — CHAO — Đạt trong phạm vi quan sát
- Câu hỏi: Ừ chào
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 87 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #11 — TIM_TEN — Một phần
- Câu hỏi: Tìm sách Dế Mèn Phiêu Lưu Ký
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 119 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400;133400;171600;63200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Đúng sách đầu tiên nhưng thêm ba sách ngoài tên yêu cầu.

## #12 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Có Hoàng Tử Bé không?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 91 ms.
- Sách đầu tiên: Hoàng Tử Bé; danh sách giá: 69920đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Hoàng Tử Bé” của Antoine de Saint-Exupéry. Câu chuyện trong trẻo và sâu sắc về cậu hoàng tử nhỏ đến từ tiểu hành tinh B612. Tác phẩm văn học được yêu
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #13 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Cho mình xem Nhà Giả Kim
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 102 ms.
- Sách đầu tiên: Nhà Giả Kim; danh sách giá: 63200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Nhà Giả Kim” hiện có giá 63.200đ, giảm 20% từ 79.000đ
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #14 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Có sách Truyện Kiều không?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 84 ms.
- Sách đầu tiên: Truyện Kiều; danh sách giá: 64600đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Truyện Kiều” của Nguyễn Du. Kiệt tác thi ca của đại thi hào Nguyễn Du — 3254 câu lục bát kể về cuộc đời nàng Kiều. Đỉnh cao của văn học cổ điển Việt 
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #15 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Tìm Harry Potter Và Hòn Đá Phù Thủy
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 89 ms.
- Sách đầu tiên: Harry Potter Và Hòn Đá Phù Thủy; danh sách giá: 118800đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Harry Potter Và Hòn Đá Phù Thủy” của J.K. Rowling. Khởi đầu của loạt truyện phù thủy huyền thoại. Cậu bé Harry bước vào trường Hogwarts và khám phá t
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #16 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Mình muốn mua Đắc Nhân Tâm
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 89 ms.
- Sách đầu tiên: Đắc Nhân Tâm; danh sách giá: 70520đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Đắc Nhân Tâm” của Dale Carnegie. Cuốn sách về nghệ thuật đối nhân xử thế bán chạy nhất mọi thời đại. Những nguyên tắc vàng giúp bạn chinh phục lòng n
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #17 — TIM_TEN — Một phần
- Câu hỏi: Tư vấn giúp cuốn Tư Duy Nhanh Và Chậm
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 116 ms.
- Sách đầu tiên: Tư Duy Nhanh Và Chậm; danh sách giá: 151300;78000;171600;103040đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Có sách yêu cầu nhưng gợi ý thêm, tư vấn chung chung.

## #18 — TIM_TEN — Không đạt
- Câu hỏi: Sherlock Holmes có bản nào không shop?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 138 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #19 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Tôi Thấy Hoa Vàng Trên Cỏ Xanh còn bán không?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 70 ms.
- Sách đầu tiên: Tôi Thấy Hoa Vàng Trên Cỏ Xanh; danh sách giá: 79200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Tôi Thấy Hoa Vàng Trên Cỏ Xanh” của Nguyễn Nhật Ánh. Câu chuyện tuổi thơ trong trẻo nơi làng quê nghèo với tình anh em, tình bạn và những rung động đ
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #20 — TIM_TEN — Đạt trong phạm vi quan sát
- Câu hỏi: Có Không Gia Đình không?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 75 ms.
- Sách đầu tiên: Không Gia Đình; danh sách giá: 125400đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Không Gia Đình” hiện có giá 125.400đ, giảm 5% từ 132.000đ
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #21 — TIM_TAC_GIA — Không đạt
- Câu hỏi: Sách của Nguyễn Nhật Ánh có những gì?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 54 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #22 — TIM_TAC_GIA — Đạt trong phạm vi quan sát
- Câu hỏi: Tìm sách của Yuval Noah Harari
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 2; latency: 42 ms.
- Sách đầu tiên: Sapiens: Lược Sử Loài Người; danh sách giá: 160650;171600đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 2 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #23 — TIM_TAC_GIA — Không đạt
- Câu hỏi: Sách của Dale Carnegie có gì?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 80 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #24 — TIM_TAC_GIA — Không đạt
- Câu hỏi: J.K. Rowling viết cuốn nào?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 41 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #25 — TIM_TAC_GIA — Không đạt
- Câu hỏi: Sách của Nam Cao có gì hay?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 30 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #26 — TIM_TAC_GIA — Không đạt
- Câu hỏi: Tác giả Eric Ries có cuốn nào?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 0; latency: 32 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #27 — THE_LOAI — Một phần
- Câu hỏi: Gợi ý sách phiêu lưu
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 88 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400;125400;63200;160650đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Trộn Sapiens vào truyện phiêu lưu; xem V09.

## #28 — THE_LOAI — Không đạt
- Câu hỏi: Sách trinh thám nào hay không?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 106 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #29 — THE_LOAI — Một phần
- Câu hỏi: Tìm vài cuốn sách chủ đề phát triển bản thân
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 74 ms.
- Sách đầu tiên: Sức Mạnh Tiềm Thức; danh sách giá: 99000;154000;178200;145200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phát triển bản thân. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Gợi ý chung; chưa lưu đầy đủ tên để kiểm chứng mọi sản phẩm.

## #30 — THE_LOAI — Không đạt
- Câu hỏi: Có sách thiếu nhi nào không?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 24 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #31 — THE_LOAI — Một phần
- Câu hỏi: Sách kinh doanh bán chạy
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 74 ms.
- Sách đầu tiên: Từ Tốt Đến Vĩ Đại (Good to Great); danh sách giá: 110400;114750;94050;133400đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với kinh doanh. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Có sách kinh doanh; thứ tự bán chạy chưa đối chiếu đủ.

## #32 — THE_LOAI — Một phần
- Câu hỏi: Gợi ý sách văn học
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 54 ms.
- Sách đầu tiên: Vang Bóng Một Thời; danh sách giá: 78000;79200;124960;64600đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Có sách văn học; độ liên quan toàn bộ chưa xác minh.

## #33 — THE_LOAI — Đạt trong phạm vi quan sát
- Câu hỏi: Sách về lịch sử có gì?
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 68 ms.
- Sách đầu tiên: Lịch Sử Thế Giới Hiện Đại; danh sách giá: 145360;215600;160650;118750đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với lịch sử. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #34 — THE_LOAI — Đạt trong phạm vi quan sát
- Câu hỏi: Có những thể loại nào?
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 0; latency: 66 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): VinaBook hiện có các thể loại: Sách Chính trị – pháp luật, Sách Giáo trình, Sách Khoa học công nghệ – Kinh tế, Sách Truyện, tiểu thuyết, Sách Tâm lý, 
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #35 — LOC_GIA — Một phần
- Câu hỏi: Gợi ý sách dưới 100k
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 1; latency: 43 ms.
- Sách đầu tiên: Tắt Đèn; danh sách giá: 55200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 1 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Đúng giá nhưng chỉ một lựa chọn dù có nhiều sách dưới 100k.

## #36 — LOC_GIA — Một phần
- Câu hỏi: Sách giá dưới 50k
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 1; latency: 25 ms.
- Sách đầu tiên: Hiến Pháp Nước Cộng Hòa Xã Hội Chủ Nghĩa Việt Nam (Hiện Hành); danh sách giá: 45000đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 1 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Đúng giá; chỉ một lựa chọn dưới 50k.

## #37 — LOC_GIA — Đạt trong phạm vi quan sát
- Câu hỏi: Tìm sách từ 100k đến 200k
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 39 ms.
- Sách đầu tiên: Từ Tốt Đến Vĩ Đại (Good to Great); danh sách giá: 110400;103040;151300;108800đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #38 — LOC_GIA — Không đạt
- Câu hỏi: Có sách nào khoảng 150k không?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 33 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #39 — LOC_GIA — Không đạt
- Câu hỏi: Sách trên 200k
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 23 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #40 — LOC_GIA — Không đạt
- Câu hỏi: Sách rẻ hơn 100k
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 36 ms.
- Sách đầu tiên: Luật Đất Đai Và Văn Bản Hướng Dẫn Thi Hành; danh sách giá: 110000;110400;114750;118750đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Hiểu ngược rẻ hơn thành giá tối thiểu; cả bốn sách trên 100k.

## #41 — LOC_GIA — Không đạt
- Câu hỏi: Sách rẻ nhất là cuốn nào?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 91 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #42 — LOC_GIA — Không đạt
- Câu hỏi: Sách nào đang giảm giá nhiều nhất?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 56 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #43 — CHINH_SACH — Đạt trong phạm vi quan sát
- Câu hỏi: Phí ship bao nhiêu?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 61 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Phí giao hàng là 20.000đ và được miễn phí cho đơn từ 300.000đ. Thời gian giao dự kiến 2 đến 4 ngày làm việc.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #44 — CHINH_SACH — Đạt trong phạm vi quan sát
- Câu hỏi: Có thanh toán COD không?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 54 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #45 — CHINH_SACH — Đạt trong phạm vi quan sát
- Câu hỏi: Cho mình hỏi chính sách đổi trả
- HTTP: 200; intent: POLICY; số sách: 0; latency: 62 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): VinaBook hỗ trợ đổi trả trong 7 ngày nếu sách lỗi in, rách hoặc giao sai. Bạn giữ hóa đơn và liên hệ trang Hỗ trợ để được xử lý.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #46 — CHINH_SACH — Một phần
- Câu hỏi: Làm sao thanh toán qua VNPay?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 63 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.
- Nhận xét: Liệt kê phương thức, thiếu hướng dẫn VNPay.

## #47 — CHINH_SACH — Đạt trong phạm vi quan sát
- Câu hỏi: MoMo có hỗ trợ không?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 66 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #48 — CHINH_SACH — Một phần
- Câu hỏi: VietQR là gì?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 55 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.
- Nhận xét: Không giải thích VietQR là gì.

## #49 — CHINH_SACH — Đạt trong phạm vi quan sát
- Câu hỏi: Khi nào được miễn phí vận chuyển?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 55 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Phí giao hàng là 20.000đ và được miễn phí cho đơn từ 300.000đ. Thời gian giao dự kiến 2 đến 4 ngày làm việc.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #50 — CHINH_SACH — Một phần
- Câu hỏi: Cửa hàng ở đâu?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 57 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn có thể gửi yêu cầu tại trang Liên hệ hoặc email hotro@vinabook.vn. Không gửi mật khẩu, OTP hay thông tin thẻ qua nội dung hỗ trợ.
- Nhận xét: Chỉ đưa kênh liên hệ, chưa trả địa chỉ; không bịa địa chỉ.

## #51 — CHINH_SACH — Đạt trong phạm vi quan sát
- Câu hỏi: Liên hệ với shop kiểu gì?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 61 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn có thể gửi yêu cầu tại trang Liên hệ hoặc email hotro@vinabook.vn. Không gửi mật khẩu, OTP hay thông tin thẻ qua nội dung hỗ trợ.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #52 — CHINH_SACH — Một phần
- Câu hỏi: Hoàn tiền khi nào được nhận?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 60 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): VinaBook hỗ trợ đổi trả trong 7 ngày nếu sách lỗi in, rách hoặc giao sai. Bạn giữ hóa đơn và liên hệ trang Hỗ trợ để được xử lý.
- Nhận xét: Trả đổi trả thay vì thời điểm hoàn tiền.

## #53 — DON_HANG — Đạt trong phạm vi quan sát
- Câu hỏi: Đơn hàng của tôi ở đâu?
- HTTP: 200; intent: ORDER_SUPPORT; số sách: 0; latency: 58 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn cần đăng nhập để mình tra cứu đơn hàng. Sau khi đăng nhập, hãy hỏi lại “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Yêu cầu đăng nhập là phù hợp cho khách ẩn danh; chưa test đơn thật.

## #54 — DON_HANG — Đạt trong phạm vi quan sát
- Câu hỏi: Theo dõi đơn hàng giúp mình
- HTTP: 200; intent: ORDER_SUPPORT; số sách: 0; latency: 57 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn cần đăng nhập để mình tra cứu đơn hàng. Sau khi đăng nhập, hãy hỏi lại “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Yêu cầu đăng nhập là phù hợp cho khách ẩn danh; chưa test đơn thật.

## #55 — DON_HANG — Đạt trong phạm vi quan sát
- Câu hỏi: Mã đơn VNB12345 đang giao chưa?
- HTTP: 200; intent: ORDER_SUPPORT; số sách: 0; latency: 25 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn cần đăng nhập để mình tra cứu đơn hàng. Sau khi đăng nhập, hãy hỏi lại “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Yêu cầu đăng nhập là phù hợp cho khách ẩn danh; chưa test đơn thật.

## #56 — DON_HANG — Không đạt
- Câu hỏi: Làm sao hủy đơn?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 97 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #57 — DON_HANG — Đạt trong phạm vi quan sát
- Câu hỏi: Đơn của tôi bị trễ rồi
- HTTP: 200; intent: ORDER_SUPPORT; số sách: 0; latency: 54 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn cần đăng nhập để mình tra cứu đơn hàng. Sau khi đăng nhập, hãy hỏi lại “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Yêu cầu đăng nhập là phù hợp cho khách ẩn danh; chưa test đơn thật.

## #58 — DON_HANG — Đạt trong phạm vi quan sát
- Câu hỏi: Đơn hàng bao giờ thì đến?
- HTTP: 200; intent: ORDER_SUPPORT; số sách: 0; latency: 58 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn cần đăng nhập để mình tra cứu đơn hàng. Sau khi đăng nhập, hãy hỏi lại “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Yêu cầu đăng nhập là phù hợp cho khách ẩn danh; chưa test đơn thật.

## #59 — DON_HANG — Đạt trong phạm vi quan sát
- Câu hỏi: Mình muốn xem lịch sử đơn hàng
- HTTP: 200; intent: ORDER_SUPPORT; số sách: 0; latency: 68 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn cần đăng nhập để mình tra cứu đơn hàng. Sau khi đăng nhập, hãy hỏi lại “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Yêu cầu đăng nhập là phù hợp cho khách ẩn danh; chưa test đơn thật.

## #60 — DON_HANG — Không đạt
- Câu hỏi: Khi nào thì đơn được xác nhận?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 53 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #61 — THONG_TIN — Đạt trong phạm vi quan sát
- Câu hỏi: Dế Mèn Phiêu Lưu Ký giá bao nhiêu?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 27 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Dế Mèn Phiêu Lưu Ký” hiện có giá 50.400đ, giảm 10% từ 56.000đ
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #62 — THONG_TIN — Đạt trong phạm vi quan sát
- Câu hỏi: Tư Duy Nhanh Và Chậm còn hàng không?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 27 ms.
- Sách đầu tiên: Tư Duy Nhanh Và Chậm; danh sách giá: 151300đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Theo dữ liệu hiện tại, “Tư Duy Nhanh Và Chậm” còn 200 cuốn. Số lượng có thể thay đổi khi có đơn hàng mới.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #63 — THONG_TIN — Không đạt
- Câu hỏi: Sách Chí Phèo của tác giả nào?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 26 ms.
- Sách đầu tiên: Chí Phèo; danh sách giá: 49500đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Chí Phèo” hiện có giá 49.500đ, giảm 10% từ 55.000đ
- Nhận xét: Hỏi tác giả nhưng trả giá.

## #64 — THONG_TIN — Không đạt
- Câu hỏi: Không Gia Đình có nội dung gì?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 23 ms.
- Sách đầu tiên: Không Gia Đình; danh sách giá: 125400đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Không Gia Đình” hiện có giá 125.400đ, giảm 5% từ 132.000đ
- Nhận xét: Hỏi nội dung nhưng trả giá; xem V04.

## #65 — THONG_TIN — Đạt trong phạm vi quan sát
- Câu hỏi: Nhà Giả Kim giảm bao nhiêu phần trăm?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 26 ms.
- Sách đầu tiên: Nhà Giả Kim; danh sách giá: 63200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Nhà Giả Kim” hiện có giá 63.200đ, giảm 20% từ 79.000đ
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #66 — THONG_TIN — Không đạt
- Câu hỏi: Harry Potter còn bao nhiêu cuốn?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 0; latency: 58 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #67 — THONG_TIN — Đạt trong phạm vi quan sát
- Câu hỏi: Hoàng Tử Bê của ai viết?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 63 ms.
- Sách đầu tiên: Hoàng Tử Bé; danh sách giá: 69920đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Hoàng Tử Bé” của Antoine de Saint-Exupéry. Câu chuyện trong trẻo và sâu sắc về cậu hoàng tử nhỏ đến từ tiểu hành tinh B612. Tác phẩm văn học được yêu
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #68 — THONG_TIN — Không đạt
- Câu hỏi: Đắc Nhân Tâm có đánh giá tốt không?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 57 ms.
- Sách đầu tiên: Đắc Nhân Tâm; danh sách giá: 70520đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Đắc Nhân Tâm” hiện có giá 70.520đ, giảm 18% từ 86.000đ
- Nhận xét: Hỏi đánh giá nhưng trả giá; xem V05.

## #69 — SO_SANH — Đạt trong phạm vi quan sát
- Câu hỏi: So sánh Nhà Giả Kim và Hoàng Tử Bé
- HTTP: 200; intent: COMPARE_BOOKS; số sách: 2; latency: 28 ms.
- Sách đầu tiên: Hoàng Tử Bé; danh sách giá: 69920;63200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): So sánh nhanh: • Hoàng Tử Bé: 69.920đ, Sách thiếu nhi, còn hàng. Câu chuyện trong trẻo và sâu sắc về cậu hoàng tử nhỏ đến từ tiểu hành tinh B612. Tác 
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #70 — SO_SANH — Không đạt
- Câu hỏi: Khác nhau giữa Tư Duy Nhanh Và Chậm và Quẳng Gánh Lo Đi?
- HTTP: 200; intent: COMPARE_BOOKS; số sách: 1; latency: 25 ms.
- Sách đầu tiên: Tư Duy Nhanh Và Chậm; danh sách giá: 151300đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn hãy nêu rõ ít nhất hai tên sách muốn so sánh, ví dụ: “So sánh Nhà Giả Kim và Đắc Nhân Tâm”.
- Nhận xét: Chỉ nhận một tên đầy đủ; tên rút gọn không hiểu.

## #71 — SO_SANH — Không đạt
- Câu hỏi: Nên chọn Clean Code hay Khởi Nghiệp Tinh Gọn?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 70 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.
- Nhận xét: Clean Code bị hiểu là COD.

## #72 — SO_SANH — Không đạt
- Câu hỏi: So sánh Sherlock Holmes với Chí Phèo
- HTTP: 200; intent: COMPARE_BOOKS; số sách: 1; latency: 26 ms.
- Sách đầu tiên: Chí Phèo; danh sách giá: 49500đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn hãy nêu rõ ít nhất hai tên sách muốn so sánh, ví dụ: “So sánh Nhà Giả Kim và Đắc Nhân Tâm”.
- Nhận xét: Chỉ nhận Chí Phèo; không nhận Sherlock Holmes rút gọn.

## #73 — SO_SANH — Không đạt
- Câu hỏi: Cuốn nào hay hơn: Truyện Kiều hay Hoàng Tử Bé?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 63 ms.
- Sách đầu tiên: Truyện Kiều; danh sách giá: 64600đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Truyện Kiều” của Nguyễn Du. Kiệt tác thi ca của đại thi hào Nguyễn Du — 3254 câu lục bát kể về cuộc đời nàng Kiều. Đỉnh cao của văn học cổ điển Việt 
- Nhận xét: Cần so sánh nhưng chỉ trả một sách.

## #74 — SO_SANH — Không đạt
- Câu hỏi: Truyện Kiều khác Dế Mèn Phiêu Lưu Ký thế nào?
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 69 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400;118800;63200;133400đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Trả danh sách phiêu lưu thay vì so sánh.

## #75 — SAI_CHINH_TA — Một phần
- Câu hỏi: tìm sách Dế Mèn Phiêu Lưu Ký giùm nha
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 65 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400;63200;154560;133400đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Câu không có lỗi chính tả rõ; đúng sách đầu nhưng thêm sách khác.

## #76 — SAI_CHINH_TA — Không đạt
- Câu hỏi: có sách Harrry Poter không?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 99 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #77 — SAI_CHINH_TA — Đạt trong phạm vi quan sát
- Câu hỏi: sách Đắc Nhân Tâm giá bao nhêu?
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 54 ms.
- Sách đầu tiên: Đắc Nhân Tâm; danh sách giá: 70520đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Đắc Nhân Tâm” hiện có giá 70.520đ, giảm 18% từ 86.000đ
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #78 — SAI_CHINH_TA — Đạt trong phạm vi quan sát
- Câu hỏi: phí shíp sao vậy bạn
- HTTP: 200; intent: POLICY; số sách: 0; latency: 62 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Phí giao hàng là 20.000đ và được miễn phí cho đơn từ 300.000đ. Thời gian giao dự kiến 2 đến 4 ngày làm việc.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #79 — SAI_CHINH_TA — Không đạt
- Câu hỏi: Tôi muon mua Hary Poter
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 113 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #80 — KHONG_DAU — Một phần
- Câu hỏi: tim sach de men phieu luu ky
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 57 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400;133400;171600;63200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Không dấu tìm được sách đầu; vẫn có sách ngoài yêu cầu.

## #81 — KHONG_DAU — Đạt trong phạm vi quan sát
- Câu hỏi: co sach nha gia kim khong
- HTTP: 200; intent: PRODUCT_INFO; số sách: 1; latency: 59 ms.
- Sách đầu tiên: Nhà Giả Kim; danh sách giá: 63200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): “Nhà Giả Kim” hiện có giá 63.200đ, giảm 20% từ 79.000đ
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #82 — KHONG_DAU — Một phần
- Câu hỏi: goi y sach duoi 100k
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 1; latency: 39 ms.
- Sách đầu tiên: Tắt Đèn; danh sách giá: 55200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 1 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Không dấu và giá hoạt động nhưng chỉ một lựa chọn.

## #83 — KHONG_DAU — Không đạt
- Câu hỏi: sach hai cua nguyen nhat anh
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 27 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #84 — KHONG_DAU — Đạt trong phạm vi quan sát
- Câu hỏi: phi ship bao nhieu vay ban
- HTTP: 200; intent: POLICY; số sách: 0; latency: 50 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Phí giao hàng là 20.000đ và được miễn phí cho đơn từ 300.000đ. Thời gian giao dự kiến 2 đến 4 ngày làm việc.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #85 — NGOAI_NGU — Không đạt
- Câu hỏi: Do you have Harry Potter in stock?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 102 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #86 — NGOAI_NGU — Không đạt
- Câu hỏi: What books do you recommend for kids?
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 104 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #87 — NGOAI_NGU — Một phần
- Câu hỏi: こんにちは、本を探しています
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 118 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Fallback an toàn; chưa hỗ trợ tiếng Nhật hay giải thích giới hạn.

## #88 — VO_NGHIA — Đạt trong phạm vi quan sát
- Câu hỏi: asdfghjkl
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 87 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #89 — VO_NGHIA — Đạt trong phạm vi quan sát
- Câu hỏi: ??????
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 98 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #90 — VO_NGHIA — Một phần
- Câu hỏi: Tôi là người ngoài hành tinh
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 91 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: An toàn nhưng máy móc với câu ngoài phạm vi.

## #91 — VO_NGHIA — Một phần
- Câu hỏi: Bầu trời hôm nay xanh nhỉ?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 51 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Chào hỏi không tự nhiên với câu nói về thời tiết.

## #92 — VO_NGHIA — Một phần
- Câu hỏi: abc def ghi
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 57 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Chuỗi vô nghĩa bị coi là chào hỏi.

## #93 — VO_NGHIA — Đạt trong phạm vi quan sát
- Câu hỏi: 123456789
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 86 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Đúng sách được hỏi; nhãn PRODUCT_INFO vẫn chấp nhận được.

## #94 — CAU_DAI — Không đạt
- Câu hỏi: Mình đang tìm một cuốn sách hay về phát triển bản thân, ngân sách khoảng 100k, bạn gợi ý giúp mình vài cuốn được không?
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 46 ms.
- Sách đầu tiên: Đường Xưa Mây Trắng; danh sách giá: 145200;151300;154560;154000đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phát triển bản thân. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Khoảng 100k thành minPrice=100000; gợi ý 145–155k.

## #95 — CAU_DAI — Không đạt
- Câu hỏi: Mình muốn tặng sách cho em nhỏ 8 tuổi, nên chọn cuốn nào cho phù hợp?
- HTTP: 200; intent: COMPARE_BOOKS; số sách: 0; latency: 62 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn hãy nêu rõ ít nhất hai tên sách muốn so sánh, ví dụ: “So sánh Nhà Giả Kim và Đắc Nhân Tâm”.
- Nhận xét: 8 tuổi thành 8000đ, yêu cầu hai tên so sánh.

## #96 — CAU_DAI — Không đạt
- Câu hỏi: Mình thích truyện trinh thám kiểu Sherlock Holmes, có cuốn nào tương tự không?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 65 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #97 — CAU_DAI — Không đạt
- Câu hỏi: Trong số các sách tâm lý hiện có thì cuốn nào đáng đọc nhất bạn nhỉ?
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 57 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #98 — CAU_DAI — Không đạt
- Câu hỏi: Mình mới học lập trình, nên đọc Clean Code trước hay tìm cuốn khác dễ hơn?
- HTTP: 200; intent: POLICY; số sách: 0; latency: 63 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.
- Nhận xét: Hỏi sách lập trình lại được hướng dẫn COD.

## #99 — FOLLOW_UP — Một phần
- Câu hỏi: Gợi ý sách phiêu lưu
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 70 ms.
- Sách đầu tiên: Dế Mèn Phiêu Lưu Ký; danh sách giá: 50400;125400;63200;160650đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Gợi ý phiêu lưu trộn Sapiens.

## #100 — FOLLOW_UP — Một phần
- Câu hỏi: Còn cuốn khác không?
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 65 ms.
- Sách đầu tiên: Blockchain Fundamentals - Nền Tảng Công Nghệ Chuỗi Khối; danh sách giá: 133400;124960;50400;154560đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với phiêu lưu. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Có history nhưng runner không gửi ID; lệch chủ đề. Không quy lỗi loại trùng.

## #101 — FOLLOW_UP — Một phần
- Câu hỏi: Sách rẻ hơn được không?
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 4; latency: 66 ms.
- Sách đầu tiên: Doraemon - Tập 1; danh sách giá: 19800;23750;45000;49500đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 4 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Có giá rẻ nhưng chưa giữ chủ đề; thiếu context ID ở lượt gốc.

## #102 — FOLLOW_UP — Một phần
- Câu hỏi: Gợi ý sách dưới 100k
- HTTP: 200; intent: DISCOVER_BOOKS; số sách: 1; latency: 35 ms.
- Sách đầu tiên: Tắt Đèn; danh sách giá: 55200đ.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chọn 1 cuốn phù hợp với nhu cầu của bạn. Các gợi ý lấy trực tiếp từ catalog và đã được kiểm tra theo giá, thể loại, tồn kho hiện tại.
- Nhận xét: Đúng dưới 100k nhưng chỉ một sách.

## #103 — FOLLOW_UP — Đạt trong phạm vi quan sát
- Câu hỏi: So sánh các cuốn này
- HTTP: 200; intent: COMPARE_BOOKS; số sách: 0; latency: 58 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Bạn hãy nêu rõ ít nhất hai tên sách muốn so sánh, ví dụ: “So sánh Nhà Giả Kim và Đắc Nhân Tâm”.
- Nhận xét: Chỉ một sách trong ngữ cảnh: yêu cầu hai tên để so sánh là hợp lý.

## #104 — FOLLOW_UP — Không đạt
- Câu hỏi: Tìm Harry Potter
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 95 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.

## #105 — FOLLOW_UP — Không đạt
- Câu hỏi: Thêm Harry Potter vào giỏ
- HTTP: 200; intent: UNKNOWN; số sách: 0; latency: 90 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.
- Nhận xét: Không hiểu câu thêm giỏ; không chứng minh nút thêm giỏ hỏng.

## #106 — FOLLOW_UP — Đạt trong phạm vi quan sát
- Câu hỏi: Cảm ơn nhé
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 63 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Rất vui được giúp bạn. Hãy mô tả người đọc, chủ đề và ngân sách để mình chọn sách chính xác hơn.
- Nhận xét: Phản hồi liên quan, phù hợp yêu cầu quan sát được.

## #107 — BIEN_DO — Đạt trong phạm vi quan sát
- Câu hỏi:    
- HTTP: 400; intent: ; số sách: 0; latency: 33 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): 
- Nhận xét: HTTP 400 đúng cho chuỗi trắng, không phải lỗi chatbot.

## #108 — BIEN_DO — Đạt trong phạm vi quan sát
- Câu hỏi: truyện dài lắm phải không xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
- HTTP: 400; intent: ; số sách: 0; latency: 6 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): 
- Nhận xét: HTTP 400 đúng cho >1000 ký tự; câu đầy đủ trong kịch bản.

## #109 — BIEN_DO — Không đạt
- Câu hỏi: Sách  tế  nhị  giữa  khoảng  50  nghìn  và  120  nghìn  thì  có  cuốn  nào  không  bạn  ơi
- HTTP: 200; intent: SMALL_TALK; số sách: 0; latency: 42 ms.
- Phản hồi đã lưu (tối đa 150 ký tự): Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.
- Nhận xét: Không giải quyết yêu cầu trong câu hỏi; xem phản hồi bên dưới.
