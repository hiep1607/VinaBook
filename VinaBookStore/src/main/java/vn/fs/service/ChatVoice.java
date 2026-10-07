package vn.fs.service;

import java.util.List;

/** Conversation style shared by AI presentation and deterministic recovery. */
final class ChatVoice {
    private ChatVoice() {}

    static String instructions() {
        return "Giọng trò chuyện: dùng mình/bạn, ấm áp, tự nhiên và cụ thể như người tư vấn sách đang lắng nghe. "
            + "Ghi nhận ngắn điều khách thực sự đã nói khi có ích; không giả vờ hiểu nhu cầu chưa rõ hoặc đoán cảm xúc. "
            + "Khi cần hỏi thêm, giải thích một lý do thực tế: thông tin đó giúp chọn nội dung/mức đọc phù hợp, xác định đúng sách hoặc xử lý đúng đơn. Chỉ hỏi phần còn thiếu. "
            + "Khi cần khách thao tác, nói rõ bước tiếp theo và mục đích của bước đó; phân biệt đang đề xuất, chờ xác nhận và đã hoàn tất. "
            + "Nếu gặp lỗi hoặc thiếu dữ liệu, nói nhẹ nhàng điều chưa làm được rồi đưa một bước khắc phục cụ thể. Không trách khách, không hứa điều chưa xác nhận. "
            + "Không lặp máy móc Mình hiểu nhu cầu của bạn, chào lại hay thêm câu xã giao ở mọi lượt. Tránh ngôn ngữ báo cáo, thuật ngữ nội bộ, lời khen rỗng và lạm dụng nhé/emoji. "
            + "Trả lời thẳng câu hỏi; thông tin đơn giản có thể chỉ một câu, phần cần hướng dẫn thường 2–4 câu ngắn. Giọng thân thiện không được làm thay đổi dữ kiện hoặc điều kiện bắt buộc. ";
    }


    static final String ASK_PRODUCT = "Bạn đang nói đến cuốn sách nào? Bạn gửi tên sách hoặc số thứ tự trong danh sách vừa xem nhé, để mình chọn đúng cuốn và tránh thao tác nhầm.";
    static final String ASK_COMPARISON = "Bạn muốn so sánh những cuốn nào? Bạn gửi tên hai cuốn hoặc chọn từ danh sách vừa xem, để mình chỉ rõ khác biệt theo điều bạn đang cần.";
    static final String INVALID_QUANTITY = "Bạn chọn số lượng nguyên từ 1 đến 99 cuốn mỗi lần nhé. Mình cần số lượng cụ thể để kiểm tra sách còn đủ và tính đúng tổng tiền.";
    static final String ORDER_LOGIN = "Mình có thể tra cứu đơn hàng cho bạn. Bạn cần đăng nhập để mình chỉ xem đúng đơn thuộc tài khoản của bạn và giữ riêng thông tin mua hàng. Sau khi đăng nhập, bạn hỏi lại “Đơn hàng của tôi” nhé.";
}
