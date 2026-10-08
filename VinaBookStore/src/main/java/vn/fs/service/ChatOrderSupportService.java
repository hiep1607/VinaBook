package vn.fs.service;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.Order;
import vn.fs.entity.User;
import vn.fs.repository.OrderRepository;
import vn.fs.repository.UserRepository;

@Service
public class ChatOrderSupportService {
	private final CurrentUserAccess access;
	private final UserRepository users;
	private final OrderRepository orders;
	private vn.fs.repository.OrderDetailRepository details;
	@org.springframework.beans.factory.annotation.Autowired
	public void setDetails(vn.fs.repository.OrderDetailRepository details) { this.details=details; }

	@Transactional(readOnly = true)
	public Long latestOrderId() {
		String email=access.currentEmail();
		if(email==null)return null;
		User user=users.findByEmail(email).orElse(null);
		if(user==null)return null;
		return orders.findByUserOrderByOrdersIdDesc(user).stream().findFirst().map(Order::getOrdersId).orElse(null);
	}

    @Transactional(readOnly=true, noRollbackFor=org.springframework.web.server.ResponseStatusException.class)
    public java.util.Map<String,Object> reorderPreview(Long requestedId) {
        String email=access.currentEmail();
        if(email==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Bạn đăng nhập để mình mở đúng đơn đã mua của bạn nhé.");
        Long id=requestedId==null?latestOrderId():requestedId;
        Order order=id==null?null:orders.findById(id).orElse(null);
        if(order==null || order.getUser()==null || !email.equals(order.getUser().getEmail()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Mình chưa tìm thấy đơn này trong tài khoản của bạn. Bạn kiểm tra lại mã đơn nhé.");
        java.util.List<java.util.Map<String,Object>> items=new java.util.ArrayList<>();
        for(var line:details.findByOrder(order)) {
            java.util.Map<String,Object> item=new java.util.LinkedHashMap<>();
            item.put("productId",line.getProduct()==null?null:line.getProduct().getProductId());
            item.put("name",line.getProduct()==null?"Sách không còn thông tin":line.getProduct().getName());
            item.put("quantity",line.getQuantity());items.add(item);
        }
        return java.util.Map.of("orderId",id,"items",items);
    }

	public ChatOrderSupportService(CurrentUserAccess access, UserRepository users, OrderRepository orders) {
		this.access = access;
		this.users = users;
		this.orders = orders;
	}

    @Transactional(readOnly = true)
    public java.util.Map<String,Object> customerDetails(Long id) {
        String email = access.currentEmail();
        if (email == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Bạn đăng nhập để xem đúng đơn của mình nhé.");
        Order order = id == null || id < 1 ? null : orders.findById(id).orElse(null);
        if (order == null || order.getUser() == null || !email.equals(order.getUser().getEmail()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng này trong tài khoản của bạn.");
        java.util.Map<String,Object> result = new java.util.LinkedHashMap<>();
        result.put("id", order.getOrdersId()); result.put("date", order.getOrderDate());
        result.put("total", order.getAmount()); result.put("statusCode", order.getStatus());
        result.put("paymentMethod", order.getPaymentMethod() == null ? "COD" : order.getPaymentMethod());
        result.put("paymentStatus", order.getPaymentStatus() == null ? "UNPAID" : order.getPaymentStatus());
        result.put("paidAt", order.getPaidAt());
        String street = order.getAddress() == null ? "" : order.getAddress();
        String recipient = order.getUser().getName() == null ? "" : order.getUser().getName();
        var match = java.util.regex.Pattern.compile("\\s*\\(Người nhận: (.*?)\\)$").matcher(street);
        if (match.find()) { recipient = match.group(1); street = street.substring(0, match.start()); }
        result.put("shippingAddress", java.util.Map.of("name", recipient, "phone", order.getPhone() == null ? "" : order.getPhone(), "street", street));
        java.util.List<java.util.Map<String,Object>> items = new java.util.ArrayList<>();
        for (var line : details.findByOrder(order)) {
            var product = line.getProduct(); java.util.Map<String,Object> item = new java.util.LinkedHashMap<>();
            item.put("productId", product == null ? null : product.getProductId());
            item.put("name", product == null ? "Sách không còn thông tin" : product.getName());
            item.put("image", product == null ? "" : product.getImage());
            item.put("price", line.getPrice()); item.put("quantity", line.getQuantity()); items.add(item);
        }
        result.put("items", items);
        return result;
    }

	@Transactional(readOnly = true)
	public String latestOrders() {
		String email = access.currentEmail();
		if (email == null) return ChatVoice.ORDER_LOGIN;
		User user = users.findByEmail(email).orElse(null);
		if (user == null) return "Mình chưa xác định được tài khoản hiện tại nên chưa thể tra đúng đơn cho bạn. Bạn vui lòng đăng nhập lại rồi gửi yêu cầu nhé.";
		List<Order> list = orders.findByUserOrderByOrdersIdDesc(user);
		if (list.isEmpty()) return "Tài khoản của bạn chưa có đơn hàng nào để mình tra cứu. Nếu bạn mới chọn sách, bạn có thể xem giỏ và tiếp tục đặt hàng nhé.";
		StringBuilder answer = new StringBuilder("Mình tìm thấy các đơn gần nhất của bạn:\n");
		for (Order order : list.subList(0, Math.min(3, list.size()))) {
			answer.append("• #").append(order.getOrdersId()).append(" — ")
					.append(status(order.getStatus())).append(" — ")
					.append(NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(order.getAmount())).append("đ\n");
		}
		answer.append("Bạn mở mục Đơn hàng để xem đầy đủ thông tin từng đơn và tiếp tục thanh toán nếu cần nhé.");
		return answer.toString();
	}

	private String status(int value) {
		if (value == 1) return "Đang giao";
		if (value == 2) return "Hoàn thành";
		if (value == 3) return "Đã hủy";
		return "Đang xử lý";
	}

	@Transactional(readOnly = true)
	public String orderById(Long id) {
		String email = access.currentEmail();
		if (email == null) return ChatVoice.ORDER_LOGIN;
		User user = users.findByEmail(email).orElse(null);
		if (user == null) return "Mình chưa xác định được phiên đăng nhập hiện tại. Bạn đăng nhập lại để mình kiểm tra đúng đơn của bạn nhé.";
		Order order = orders.findByUserOrderByOrdersIdDesc(user).stream()
			.filter(o -> id.equals(o.getOrdersId())).findFirst().orElse(null);
		if (order == null) return "Mình không tìm thấy mã đơn này trong tài khoản của bạn. Bạn kiểm tra lại mã đơn trong mục Đơn hàng nhé, để mình tra đúng đơn; nếu bạn đã dùng tài khoản khác để mua, hãy đăng nhập tài khoản đó.";
		StringBuilder items=new StringBuilder();
		if(details!=null)for(var item:details.findByOrder(order)) {
			items.append("\n• ").append(item.getProduct()==null?"Sản phẩm không còn thông tin":item.getProduct().getName())
				.append(" — ").append(item.getQuantity()).append(" cuốn");
		}
		return "Đơn #" + id + ": " + status(order.getStatus())
			+ (order.getOrderDate()==null?"":". Ngày đặt: "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(order.getOrderDate())) + ". Tổng tiền "
			+ NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(order.getAmount())
			+ "đ. Phương thức: " + order.getPaymentMethod() + ". "
			+ ("PAID".equals(order.getPaymentStatus()) ? "Đã thanh toán." : "Chưa ghi nhận thanh toán.")
			+ items.toString()
			+ "\nMình chưa có dữ liệu vị trí vận chuyển trực tiếp nên chưa thể nói chính xác sách đang ở đâu. Bạn có thể mở mục Đơn hàng để xem chi tiết trạng thái hiện tại nhé.";
	}
}
