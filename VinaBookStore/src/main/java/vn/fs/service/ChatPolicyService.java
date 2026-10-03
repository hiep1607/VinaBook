package vn.fs.service;

import org.springframework.stereotype.Service;

@Service
public class ChatPolicyService {
	private final ChatIntentService intents;
	private StoreKnowledgeService store;
	@org.springframework.beans.factory.annotation.Autowired(required=false)
	public void setStore(StoreKnowledgeService store) { this.store = store; }

	public ChatPolicyService(ChatIntentService intents) {
		this.intents = intents;
	}

    boolean handlesReturn(String question, ChatDialogueMemory.State state) {
        String q=intents.normalize(question);
        if(contains(q,"tra hang","tra sach","doi tra","doi sach","hoan tien"))return true;
        if(!state.returnActive)return false;
        return !returnReason(q).isBlank() || contains(q,"gui lai","gui tra","dia chi tra","tra o dau","lam sao tra","lam the nao","buoc tiep","huong dan tra","tra nhu nao")
            || state.returnAwaitingReason && q.length()<180
                && !ChatBookFacts.has(q,"tim|goi y|sach ve|chu de|mua|gio hang|don hang|phi ship|phi giao|thanh toan|chao|cam on");
    }

    String answer(String question, ChatDialogueMemory.State state) {
        if(!handlesReturn(question,state))return answer(question);
        String q=intents.normalize(question);
        boolean general=contains(q,"chinh sach","dieu kien","quy dinh")
            && !ChatBookFacts.has(q,"toi muon|minh muon|muon tra|muon doi|can tra|can doi|vua mua|da mua|da nhan|sach bi");
        if(general && !state.returnAwaitingReason)return returnsPolicy();
        state.returnActive=true;
        String reason=returnReason(q);
        if(!reason.isBlank())state.returnReason=reason;
        else if(state.returnAwaitingReason && !ChatBookFacts.has(q,"khong biet|chua ro|huong dan|lam sao|lam the nao|buoc tiep|gui lai|tra hang|tra sach|doi tra|doi sach|hoan tien"))
            state.returnReason=ChatDialogueInterpreter.clean(question,180);
        if(state.returnReason.isBlank()) {
            state.returnAwaitingReason=true;
            return "Mình sẽ hướng dẫn theo tình trạng sách để bạn biết bước tiếp theo. Bạn muốn trả vì sách bị lỗi, giao sai hay vì không còn nhu cầu đọc?";
        }
        state.returnAwaitingReason=false;
        String reasonText=state.returnReason;
        if(contains(q,"gui lai","gui tra","dia chi tra","tra o dau"))return "Bạn đừng gửi sách về một địa chỉ tự đoán nhé. Với yêu cầu trả vì “"+reasonText
            +"”, hãy gửi mã đơn, ngày nhận và tình trạng sách qua trang Liên hệ để cửa hàng xác nhận địa chỉ nhận lại và cách gửi. Giữ sách và hóa đơn trong lúc chờ phản hồi."
            +"\n\nMình chưa có hướng dẫn đã được xác nhận về địa chỉ nhận hàng trả hoặc bên chịu phí gửi lại, nên cần cửa hàng cung cấp các thông tin đó."
            +"\n\nChính sách hiện hành: "+returnsPolicy();
        if(contains(q,"hoan tien","nhan lai tien","bao lau co tien"))return "Với lý do “"+reasonText
            +"”, việc hoàn tiền cần cửa hàng xác nhận sau khi kiểm tra yêu cầu. Chính sách hiện hành: "+returnsPolicy()
            +"\n\nMình chưa có điều khoản xác nhận thời gian hoặc cách hoàn tiền cho trường hợp của bạn. Bạn gửi mã đơn và lý do qua trang Liên hệ, yêu cầu cửa hàng xác nhận có nhận trả hay không, hình thức xử lý và thời gian dự kiến trước khi gửi lại sách.";
        boolean changedMind=reasonText.equals("không còn nhu cầu hoặc không thích nội dung");
        String opening=changedMind
            ?"Mình hiểu là bạn không còn hứng thú với cuốn vừa mua. Mình chưa có điều khoản xác nhận đổi trả chỉ vì thay đổi sở thích, nên cần cửa hàng xem xét trước khi kết luận có nhận trả hay không."
            :"Với lý do “"+reasonText+"”, bạn nên gửi thông tin để cửa hàng đối chiếu điều kiện đổi trả; mình chưa thể xác nhận yêu cầu đã được chấp nhận.";
        return opening+"\n\nChính sách đang áp dụng: "+returnsPolicy()
            +"\n\nBạn có thể làm theo các bước này:"
            +"\n1. Mở mục Đơn hàng, lấy mã đơn và tên cuốn cần trả. Sách đã mua nằm trong đơn hàng, không phải giỏ hiện tại."
            +"\n2. Gửi yêu cầu qua trang Liên hệ, nêu lý do “"+reasonText+"”, ngày nhận sách và tình trạng hiện tại; nếu sách có lỗi, gửi kèm ảnh để cửa hàng kiểm tra."
            +"\n3. Giữ sách và hóa đơn, chờ cửa hàng xác nhận điều kiện và cách gửi lại trước khi gửi hàng. Việc đổi sách hoặc hoàn tiền cần được cửa hàng xác nhận."
            +(store==null?"":"\n\n"+store.text("support"));
    }

    private String returnsPolicy() {
        return store==null?"VinaBook hỗ trợ đổi trả trong 7 ngày nếu sách lỗi in, rách hoặc giao sai. Bạn giữ hóa đơn và liên hệ trang Hỗ trợ để được xử lý.":store.text("returns");
    }

    private String returnReason(String q) {
        if(ChatBookFacts.has(q,"loi in|in loi|thieu trang|mat trang|nguoc trang"))return "sách lỗi in hoặc thiếu trang";
        if(ChatBookFacts.has(q,"sach loi|sach bi loi|cuon bi loi") && !ChatBookFacts.has(q,"khong bi loi|khong co loi"))return "sách có lỗi";
        if(ChatBookFacts.has(q,"bi rach|sach rach|bia rach|bi hong|hu hong|mop|uot|rach"))return "sách bị rách hoặc hư hỏng";
        if(ChatBookFacts.has(q,"giao sai|sai cuon|nham cuon|khong dung sach|sai sach"))return "giao sai cuốn đã đặt";
        if(ChatBookFacts.has(q,"chan|khong thich|doi y|khong con nhu cau|khong muon doc|khong hop|noi dung khong hay"))return "không còn nhu cầu hoặc không thích nội dung";
        if(ChatBookFacts.has(q,"mua nham|mua trung|dat nham|dat trung"))return "đặt nhầm hoặc mua trùng cuốn sách";
        if(contains(q,"tra hang","tra sach","doi tra","doi sach","hoan tien")) {
            var reason=java.util.regex.Pattern.compile("\\b(?:boi vi|vi|ly do la)\\s+(.+)$").matcher(q);
            if(reason.find())return ChatDialogueInterpreter.clean(reason.group(1),180);
        }
        return "";
    }

	public String answer(String question) {
		String q = intents.normalize(question);
		if (contains(q, "huy don", "huy dat hang")) {
			if (store != null) return store.text("cancellation");
			return "Bạn có thể nhắn “hủy đơn gần nhất” hoặc “hủy đơn #mã đơn”. Mình sẽ kiểm tra tài khoản và trạng thái rồi yêu cầu xác nhận. Đơn COD chưa thanh toán, đang chờ xử lý có thể hủy; đơn đang giao, đã hoàn thành hoặc thanh toán trực tuyến cần liên hệ cửa hàng. Chưa có đơn nào bị hủy chỉ vì bạn hỏi chính sách.";
		}
		if (contains(q, "xac nhan don", "don duoc xac nhan"))
			return "Bạn có thể xem trạng thái xác nhận trong mục Đơn hàng. Nếu muốn mình kiểm tra một đơn cụ thể, bạn gửi mã đơn hoặc yêu cầu tra đơn gần nhất nhé.";
		if (contains(q, "doi sach", "doi tra", "tra hang", "tra sach", "hoan tien", "sach loi", "sach rach", "giao sai")) {
			if (store != null) return store.text("returns");
			return "VinaBook hỗ trợ đổi trả trong 7 ngày nếu sách lỗi in, rách hoặc giao sai. Bạn giữ hóa đơn và liên hệ trang Hỗ trợ để được xử lý.";
		}
		if (contains(q, "phi ship", "phi giao", "van chuyen", "giao hang", "freeship", "bao lau")) {
			String rule = store != null ? store.text("shipping")
						: "Phí giao hàng là 20.000đ và được miễn phí cho đơn từ 300.000đ. Thời gian giao dự kiến 2 đến 4 ngày làm việc.";

            // A price cap for each recommendation is not an order subtotal.
            if(ChatBookFacts.has(q,"moi cuon|gia moi|gia tung") && ChatBookFacts.has(q,"goi y|tim|gioi thieu|tu van"))return rule;
			java.util.List<Long> subtotals=ChatMoney.parse(q).stream().distinct().limit(4).collect(java.util.stream.Collectors.toList());
			if(subtotals.isEmpty())return rule;
			StringBuilder answer=new StringBuilder(rule);
			for(long subtotal:subtotals) {
				long fee=store!=null?store.shipping(subtotal):subtotal<300000?20000:0;
				answer.append("\n• Tiền sách ").append(money(subtotal)).append(": ")
						.append(fee==0?"miễn phí giao hàng":"phí giao hàng "+money(fee))
						.append("; tổng thanh toán ").append(money(subtotal+fee)).append(".");
			}
			return answer.toString();
		}
		if (contains(q, "thanh toan", "cod", "vietqr", "vnpay", "momo", "chuyen khoan")) {
			if (store != null) return store.text("payment");
			return "Bạn luôn có thể thanh toán COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.";
		}
		if (contains(q, "lien he", "hotline", "email", "cua hang")) {
			if (store != null) return store.text("support");
			return "Bạn có thể gửi yêu cầu tại trang Liên hệ hoặc email hotro@vinabook.vn. Không gửi mật khẩu, OTP hay thông tin thẻ qua nội dung hỗ trợ.";
		}
		return "Mình có thể giải đáp về giao hàng, thanh toán, đổi trả và cách đặt hàng. Bạn muốn hỏi chính sách nào?";
	}

	private boolean contains(String q, String... values) {
		for (String value : values) if (q.contains(value)) return true;
		return false;
	}
	private String money(long value) { return String.format(new java.util.Locale("vi","VN"),"%,dđ",value); }
}
