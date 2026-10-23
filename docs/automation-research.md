# Nghiên cứu và áp dụng tự động hóa VinaBook

Ngày nghiên cứu: 21/10/2026. Công cụ: REA `capture_browser_scenario`, Chrome headless tạm, chỉ truy cập trang công khai. REA thực hiện các bước điều hướng, chờ DOM và một lần mở FAQ được khai báo; không đăng nhập, gửi biểu mẫu hoặc mua hàng trên các dịch vụ tham khảo.

## Bằng chứng quan sát

| Nguồn | Bằng chứng REA | Điều quan sát được | Cách áp dụng vào VinaBook |
|---|---|---|---|
| [Shopify Flow](https://www.shopify.com/flow) | `ev_32a7b7872a73fbaa1a15efa8e0279b0241c0398da02db02c4e3d5f322d477d36`; mở FAQ: `ev_0e14e44c36f229a0ed315eb298a9fe8e5d84a780201ccf1f6cf6f5097488bd91` | Giao diện công khai giới thiệu tác vụ theo sự kiện hoặc lịch. FAQ sau khi mở giải thích sự kiện, điều kiện và hành động. Có luồng quản lý tồn kho. | Mỗi quy tắc có bật/tắt, điều kiện, tham số và lịch sử chạy; quản trị cảnh báo tồn kho theo ngưỡng. |
| [BookBub: thông báo sách mới](https://support.bookbub.com/articles/what-are-new-release-alerts/) | `ev_72f422decc1ab48d7e3b0625fc9ea133c000572fe15cf30f665379f452d2547e` | Tài liệu công khai mô tả thông báo tới người theo dõi tác giả khi có sách mới đủ điều kiện. | Cho khách theo dõi tác giả. VinaBook báo sách vừa được thêm vào danh mục, không coi ngày nhập catalog là ngày xuất bản. |
| [BookBub: wishlist và ưu đãi](https://help.bookbub.com/articles/can-bookbub-alert-me-when-theres-a-deal-on-a-book-i-want/) | `ev_eda31d418d3de0d371e91201d2e35c66de7025d98a64069aae0bc22589356e7b` | Trang hỗ trợ mô tả thông báo ưu đãi cho sách đã lưu trong wishlist; tính năng nguồn giới hạn tài khoản Hoa Kỳ. | Giá thực bán của sách trong wishlist giảm mới tạo thông báo; loại bỏ sách ngừng bán/hết hàng và chống lặp cùng sự kiện. |
| [Klaviyo: báo có hàng](https://help.klaviyo.com/hc/en-us/articles/360051612551) | `ev_a8a46152f4a06c5c08ebaeca6f423e89481f1ff8cb9e8812484cb65a7c81bb30` | Có danh sách chờ. Tài liệu phân biệt chuyển bước trong luồng với gửi thông điệp thành công; có tùy chọn chia đợt. | Giữ đăng ký chờ riêng với thông báo đã tạo. Số thông báo không được diễn giải thành số email gửi thành công. Thông báo không giữ chỗ sách. |
| [Klaviyo: nhắc giỏ hàng](https://help.klaviyo.com/hc/en-us/articles/115002779411) | `ev_6a72666606df81dcfbfc34c7f607d8c28cfd24b68e4b2377cb66fd16de818b61` | Tài liệu mô tả độ trễ sau hành vi giỏ/checkout và lọc người đã mua trước từng lần gửi. | Theo dõi giỏ không thay đổi, nhắc theo độ trễ cấu hình, kiểm tra giao dịch mua và tồn kho trước thông báo; dừng nhắc sau mua. |

## Ranh giới bằng chứng

- Đây là hành vi trình duyệt và nội dung công khai quan sát được. Không quan sát scheduler, database, thuật toán độc quyền hay tài khoản riêng của các nguồn. Thiết kế entity, chống trùng, JWT, kiểm tra tồn kho và giao diện VinaBook là quyết định triển khai riêng.
- Trang chính BookBub (`ev_126e6fb9993037e507e29f7b1180ac500a75245aa3d6a16fee4b7a0b8b6ffc2a`) và trang AfterShip (`ev_aec6cbe0f31dbaf69eb1c45796671f89ddf60f009f279e2ebde13f2d9e520fc1`) hiện bước xác minh bảo mật. Không vượt qua bước này; không coi chúng là bằng chứng luồng tài khoản.
- Các lần capture đầu có giới hạn số sự kiện mạng và đã bỏ bớt bản ghi. Kết luận chỉ dùng DOM/accessibility cuối đã thu được, không suy ra API hoặc tuyên bố mạng đầy đủ. Các capture tiếp theo chỉ yêu cầu DOM/accessibility để thu đúng dữ liệu cần thiết.
- Browser tạm do REA tạo đã được kết thúc sau mỗi capture. Bản chứng cứ nguyên gốc được giữ trong thư mục làm việc, không đưa vào source ứng dụng.

## Các luồng được triển khai

1. Hộp thông báo chung theo tài khoản, số chưa đọc, đọc tất cả, phân trang và tùy chọn nhận tin.
2. Báo có hàng dựa trên đăng ký theo dõi hiện có.
3. Báo giảm giá wishlist theo giá sau chiết khấu.
4. Thông báo khi trạng thái đơn thay đổi trong hệ thống.
5. Nhắc giỏ hàng theo thời gian không đổi; kiểm tra đã mua trước khi tạo/gửi.
6. Mời đánh giá sau giao hàng; bỏ qua cuốn đã đánh giá hoặc đơn có yêu cầu đổi trả.
7. Theo dõi tác giả, báo sách mới được thêm vào danh mục.
8. Admin bật/tắt quy tắc, chỉnh ngưỡng/độ trễ, chạy quét, xem lịch sử và nhu cầu sách đang chờ.
9. Tổng hợp tên sách được hỏi rõ ràng trong chatbot nhưng chưa tìm thấy; một lượt cho mỗi phiên/tên/ngày. Không lưu nguyên hội thoại hoặc danh tính người hỏi trong bảng tổng hợp.

Email là kênh tùy chọn, mặc định tắt; cần cấu hình máy gửi và bật ở hệ thống. Các bước kiểm thử dùng dữ liệu kiểm thử, không gửi email đến khách thật. Chi tiết thời gian quan sát ban đầu, điều kiện và vận hành nằm trong tài liệu tự động hóa của dự án.
