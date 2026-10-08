# Giao diện mã giảm giá và hỗ trợ đổi trả

Hai trang dùng cùng kiểu tiêu đề, màu xanh đậm/cam, nền sáng, viền, nút, input và trạng thái tải/lỗi/trống với admin hiện có. Không thêm thư viện. Sidebar voucher được đánh dấu đang mở.

Mã giảm giá: bảng ưu đãi/điều kiện/thời gian/trạng thái, tìm mã và lọc dữ liệu đã tải; form tạo/sửa riêng, chia nhóm rõ ràng, giữ mã đã tạo không đổi tên và nội dung nhập khi lưu lỗi. Khi tải lại thất bại không báo đã cập nhật danh sách. Trang dùng API hiện có, không tạo chương trình thật tự động.

Hỗ trợ: chọn yêu cầu trong danh sách và xử lý ở panel chi tiết, nhãn trạng thái tiếng Việt, thông tin đơn/sách/ngày nhận rõ ràng. Bản nháp phản hồi giữ khi chuyển qua các yêu cầu trong cùng trang; bộ lọc/phân trang vẫn tải theo server. Hội thoại và hoàn tiền mở theo nhu cầu, cùng giao diện xanh, giữ xác nhận/đối soát giao dịch. Hủy request khi rời trang. Màn hình nhỏ xếp dọc.

5 kịch bản mã giảm giá:
1. Xem danh sách với mã đang áp dụng/sắp bắt đầu/hết hạn/tắt; điều kiện và ngày phải đúng dữ liệu.
2. Tìm và lọc không có kết quả rồi xóa bộ lọc; danh sách trở lại mà không gọi API ghi dữ liệu.
3. Chọn Sửa: form có đúng giá trị/ngày, mã không đổi tên; thay bản nháp không sửa dữ liệu danh sách trước lưu.
4. Tạo/sửa mã, bấm Lưu lặp khi request chậm: chỉ một request; lỗi giữ nội dung và thông báo của server.
5. Mạng lỗi rồi thử lại; màn hình 390px và 1440px không tràn trang, bảng có cuộn riêng, nút/label đọc được.

5 kịch bản hỗ trợ:
1. Chọn lần lượt yêu cầu hỗ trợ/đổi trả: panel hiển thị đúng mã/nội dung/đơn; chọn lại giữ bản nháp chưa lưu trong cùng trang.
2. Lọc/phân trang: API nhận đúng trạng thái/trang, không hiển thị hội thoại của phiếu trước.
3. Lưu phản hồi với version hiện tại, bấm lặp và mô phỏng xung đột: không ghi đè âm thầm, vẫn giải thích tải lại.
4. Mở hội thoại/hoàn tiền và thu gọn: widget đúng yêu cầu/đơn, yêu cầu đã đóng không gửi tin; mở phần hoàn tiền chưa thực hiện giao dịch.
5. Kiểm tra tải/lỗi/trống, bàn phím và màn hình 390px/1440px: danh sách và panel đọc được, nút có focus, form không bị cắt.

Ảnh kiểm tra dùng component Angular thật với dữ liệu mẫu trong một workspace QA riêng, tất cả API được chặn tại interceptor. Không sửa AuthGuard hay phiên đăng nhập của ứng dụng thật.

Kết quả kiểm tra: 15 test thuộc voucher, support queue và payment refunds đạt; compiler kiểu dữ liệu/template đạt. Build production cuối đạt (hash 8a194c50d0a8d02b358d), bundle khởi động ES5 1,20 MB, ES2017 1,04 MB. Module admin tải khi vào /admin; HttpClient và cấu hình Toastr nằm ở root để màn đăng nhập vẫn dùng đúng provider. URL và các guard con giữ nguyên. Còn cảnh báo CSS của trang chủ admin cũ (10,05 KB so với ngưỡng khuyến nghị 10 KB), không có lỗi build.

Đã xem component thật tại 1440px và 390px: trang không tràn ngang; riêng bảng mã cuộn trong khung. Đã mở sửa mã, hội thoại và dữ liệu hoàn tiền trong harness cô lập. Header/sidebar/logo giữ phong cách hiện có; hai menu có tên truy cập khi thu gọn. Dev server 4300 biên dịch thành công. Chưa kiểm tra dữ liệu thật trong phiên Chrome của người dùng; ảnh dùng dữ liệu mẫu có nhãn nhận diện.
