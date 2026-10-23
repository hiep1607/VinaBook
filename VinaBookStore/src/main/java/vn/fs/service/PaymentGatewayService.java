package vn.fs.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.entity.Order;
import vn.fs.repository.OrderRepository;

@Service
public class PaymentGatewayService {

	private static final String COD = "COD";
	private static final String BANK_QR = "BANK_QR";
	private static final String VNPAY = "VNPAY";
	private static final String MOMO = "MOMO";
	private static final String PAID = "PAID";
	private static final String PENDING = "PENDING";
	private static final String FAILED = "FAILED";

	private final OrderRepository orderRepository;
	private final ObjectMapper objectMapper;
	@PersistenceContext private EntityManager entityManager;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@Value("${payment.frontend-url:http://localhost:4200}")
	private String frontendUrl;

	@Value("${payment.backend-url:http://localhost:8080}")
	private String backendUrl;

	@Value("${payment.bank.id:}")
	private String bankId;

	@Value("${payment.bank.account-no:}")
	private String bankAccountNo;

	@Value("${payment.bank.account-name:}")
	private String bankAccountName;

	@Value("${payment.bank.template:compact2}")
	private String bankTemplate;

	@Value("${payment.vnpay.tmn-code:}")
	private String vnpayTmnCode;

	@Value("${payment.vnpay.hash-secret:}")
	private String vnpayHashSecret;

	@Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
	private String vnpayPayUrl;

	@Value("${payment.momo.partner-code:}")
	private String momoPartnerCode;

	@Value("${payment.momo.access-key:}")
	private String momoAccessKey;

	@Value("${payment.momo.secret-key:}")
	private String momoSecretKey;

	@Value("${payment.momo.create-url:https://test-payment.momo.vn/v2/gateway/api/create}")
	private String momoCreateUrl;

	public PaymentGatewayService(OrderRepository orderRepository, ObjectMapper objectMapper) {
		this.orderRepository = orderRepository;
		this.objectMapper = objectMapper;
	}

    @Value("${payment.refunds.enabled:false}") private boolean refundsEnabled;
    @Value("${payment.vnpay.refund-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") private String vnpayRefundUrl;
    @Value("${payment.momo.refund-url:https://test-payment.momo.vn/v2/gateway/api/refund}") private String momoRefundUrl;
    public String refundUnavailable(Order order) {
        if (!refundsEnabled) return "Cửa hàng chưa bật hoàn tiền qua cổng thanh toán.";
        if (!PAID.equals(paymentStatus(order))) return "Đơn này chưa được xác nhận thanh toán.";
        if (!notBlank(order.getPaymentReference()) || !notBlank(order.getPaymentTransactionId())) return "Thiếu mã giao dịch gốc để hoàn tiền. Cần đối soát với cổng thanh toán.";
        if (VNPAY.equals(paymentMethod(order))) {
            if (!vnpayConfigured()) return "Chưa cấu hình VNPay.";
            if (order.getPaymentGatewayDate()==null || !order.getPaymentGatewayDate().matches("[0-9]{14}")) return "Đơn cũ thiếu thời điểm tạo giao dịch VNPay. Nhân viên cần đối soát trên cổng thanh toán.";
        } else if (MOMO.equals(paymentMethod(order))) {
            if (!momoConfigured() || !order.getPaymentTransactionId().matches("[0-9]+")) return "Thiếu cấu hình hoặc mã giao dịch MoMo hợp lệ.";
        } else return "Hoàn tiền tự động chỉ áp dụng với VNPay và MoMo. COD/chuyển khoản cần đối soát riêng.";
        return "";
    }
    public String refund(Order order,vn.fs.entity.PaymentRefund refund) {
        try {
            String ref=refund.getGatewayReference(), description="Refund VB"+order.getOrdersId()+" R"+refund.getId();
            Map<String,Object> payload=new LinkedHashMap<>(); String url;
            if (VNPAY.equals(refund.getProvider())) {
                url=vnpayRefundUrl;
                payload.put("vnp_RequestId",ref);payload.put("vnp_Version","2.1.0");payload.put("vnp_Command","refund");payload.put("vnp_TmnCode",vnpayTmnCode);
                payload.put("vnp_TransactionType",refund.getAmount()==Math.round(order.getAmount())?"02":"03");
                payload.put("vnp_TxnRef",order.getPaymentReference());payload.put("vnp_Amount",String.valueOf(Math.multiplyExact(refund.getAmount(),100)));
                payload.put("vnp_TransactionNo",order.getPaymentTransactionId());payload.put("vnp_TransactionDate",order.getPaymentGatewayDate());
                payload.put("vnp_CreateBy","staff"+refund.getStaffId());payload.put("vnp_CreateDate",vnpayDate(0));payload.put("vnp_IpAddr","127.0.0.1");payload.put("vnp_OrderInfo",description);
                payload.put("vnp_SecureHash",hmac("HmacSHA512",vnpayHashSecret,pipe(payload,"vnp_RequestId","vnp_Version","vnp_Command","vnp_TmnCode","vnp_TransactionType","vnp_TxnRef","vnp_Amount","vnp_TransactionNo","vnp_TransactionDate","vnp_CreateBy","vnp_CreateDate","vnp_IpAddr","vnp_OrderInfo")));
            } else {
                url=momoRefundUrl;payload.put("partnerCode",momoPartnerCode);payload.put("orderId",ref);payload.put("requestId",ref);payload.put("amount",refund.getAmount());
                payload.put("transId",Long.parseLong(order.getPaymentTransactionId()));payload.put("lang","vi");payload.put("description",description);
                payload.put("signature",hmac("HmacSHA256",momoSecretKey,"accessKey="+momoAccessKey+"&amount="+refund.getAmount()+"&description="+description+"&orderId="+ref+"&partnerCode="+momoPartnerCode+"&requestId="+ref+"&transId="+order.getPaymentTransactionId()));
            }
            return refundResult(order,refund,payload,postRefund(url,payload));
        } catch (Exception ex) { if(ex instanceof InterruptedException)Thread.currentThread().interrupt(); return "UNKNOWN"; }
    }
    // An uncertain transport response must never trigger another refund request.
    protected Map<String,Object> postRefund(String url,Map<String,Object> payload) throws Exception {
        URI endpoint=URI.create(url);if(!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getUserInfo()!=null)throw new IllegalStateException("Refund endpoint must use HTTPS");
        HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(35)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
        HttpResponse<String> response=httpClient.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("Uncertain refund response");
        return objectMapper.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
    }
    String refundResult(Order order,vn.fs.entity.PaymentRefund refund,Map<String,Object> request,Map<String,Object> response) {
        if(VNPAY.equals(refund.getProvider())) {
            String signature=hmac("HmacSHA512",vnpayHashSecret,pipe(response,"vnp_ResponseId","vnp_Command","vnp_ResponseCode","vnp_Message","vnp_TmnCode","vnp_TxnRef","vnp_Amount","vnp_BankCode","vnp_PayDate","vnp_TransactionNo","vnp_TransactionType","vnp_TransactionStatus","vnp_OrderInfo"));
            if(!secureEquals(signature,string(response,"vnp_SecureHash")) || !"refund".equals(string(response,"vnp_Command"))
                || !vnpayTmnCode.equals(string(response,"vnp_TmnCode")) || !order.getPaymentReference().equals(string(response,"vnp_TxnRef"))
                || !string(request,"vnp_TransactionType").equals(string(response,"vnp_TransactionType"))
                || !string(request,"vnp_OrderInfo").equals(string(response,"vnp_OrderInfo"))
                || parseLong(string(response,"vnp_Amount"),-1)!=refund.getAmount()*100) return "UNKNOWN";
            if(!"00".equals(string(response,"vnp_ResponseCode")))return "UNKNOWN";
            String status=string(response,"vnp_TransactionStatus");return "00".equals(status)?"SUCCEEDED":"09".equals(status)?"FAILED":"PENDING";
        }
        if(!momoPartnerCode.equals(string(response,"partnerCode")) || !refund.getGatewayReference().equals(string(response,"orderId"))
            || !refund.getGatewayReference().equals(string(response,"requestId")) || parseLong(string(response,"amount"),-1)!=refund.getAmount())return "UNKNOWN";
        String result=string(response,"resultCode");return "0".equals(result)?"SUCCEEDED":("7000".equals(result)||"7002".equals(result))?"PENDING":"UNKNOWN";
    }
    private String pipe(Map<String,?> fields,String...names) {return java.util.Arrays.stream(names).map(name->string(fields,name)).collect(java.util.stream.Collectors.joining("|"));}

	public Map<String, Object> availableMethods() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put(COD, method(true, "Thanh toán khi nhận hàng", "Thanh toán cho nhân viên giao hàng."));
		result.put(BANK_QR, method(bankConfigured(), "Chuyển khoản QR", bankConfigured()
				? "Quét mã VietQR và chờ cửa hàng xác nhận."
				: "Chuyển khoản ngân hàng tạm thời chưa khả dụng."));
		result.put(VNPAY, method(vnpayConfigured(), "VNPay", vnpayConfigured()
				? "Thanh toán trên cổng VNPay Sandbox."
				: "VNPay Sandbox tạm thời chưa khả dụng."));
		result.put(MOMO, method(momoConfigured(), "MoMo", momoConfigured()
				? "Thanh toán trên cổng MoMo Test."
				: "MoMo Test tạm thời chưa khả dụng."));
		return result;
	}

	@Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
	public Map<String, Object> start(Order order, String clientIp) {
		order = locked(order);
		String method = paymentMethod(order);
		if (COD.equals(method)) {
			throw new IllegalArgumentException("Đơn COD không cần tạo giao dịch trực tuyến");
		}
		if (PAID.equals(paymentStatus(order))) {
			return orderStatus(order);
		}
		if (order.getStatus() == 2 || order.getStatus() == 3) {
			throw new IllegalStateException("Đơn hàng đã kết thúc, không thể thanh toán lại");
		}
		if (gatewayInFlight(order)) {
			if (VNPAY.equals(method) && !vnpayConfigured() || MOMO.equals(method) && !momoConfigured()) {
				throw new IllegalStateException("Phương thức thanh toán này tạm thời chưa khả dụng.");
			}
			if (!notBlank(order.getPaymentCheckoutUrl())) {
				throw new IllegalStateException("Giao dịch đang chờ xác nhận. Vui lòng kiểm tra trạng thái hoặc liên hệ cửa hàng trước khi thanh toán lại.");
			}
			Map<String,Object> response = orderStatus(order);
			response.put("redirectUrl", order.getPaymentCheckoutUrl());
			return response;
		}

		if (BANK_QR.equals(method)) {
			if (!bankConfigured()) {
				throw new IllegalStateException("Chuyển khoản QR chưa được cấu hình");
			}
			order.setPaymentReference("VB" + order.getOrdersId());
			order.setPaymentStatus(PENDING);
			orderRepository.save(order);
			return orderStatus(order);
		}
		if (VNPAY.equals(method)) {
			return startVnpay(order, clientIp);
		}
		if (MOMO.equals(method)) {
			return startMomo(order);
		}
		throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
	}

	public Map<String, Object> orderStatus(Order order) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("orderId", order.getOrdersId());
		result.put("amount", Math.round(order.getAmount()));
		result.put("paymentMethod", paymentMethod(order));
		result.put("paymentStatus", paymentStatus(order));
		result.put("paymentReference", order.getPaymentReference());
		result.put("paymentTransactionId", order.getPaymentTransactionId());
		result.put("paidAt", order.getPaidAt());
		result.put("orderStatus", order.getStatus());
		boolean methodAvailable = COD.equals(paymentMethod(order)) || BANK_QR.equals(paymentMethod(order)) && bankConfigured()
				|| VNPAY.equals(paymentMethod(order)) && vnpayConfigured() || MOMO.equals(paymentMethod(order)) && momoConfigured();
		boolean active = order.getStatus() == 0 || order.getStatus() == 1;
		result.put("methodAvailable", methodAvailable);
		result.put("canStart", active && methodAvailable && !COD.equals(paymentMethod(order)) && !PAID.equals(paymentStatus(order))
				&& (!gatewayInFlight(order) || notBlank(order.getPaymentCheckoutUrl())));
		result.put("canPoll", active && !COD.equals(paymentMethod(order)) && PENDING.equals(paymentStatus(order)));
		if (BANK_QR.equals(paymentMethod(order)) && bankConfigured() && active && !PAID.equals(paymentStatus(order))) {
			String content = order.getPaymentReference() == null ? "VB" + order.getOrdersId() : order.getPaymentReference();
			result.put("qrUrl", buildBankQrUrl(order, content));
			result.put("bankId", bankId);
			result.put("bankAccountNo", bankAccountNo);
			result.put("bankAccountName", bankAccountName);
			result.put("transferContent", content);
		}
		return result;
	}

	@Transactional
	public Order markBankTransferPaid(Order order) {
		order = locked(order);
		if (!BANK_QR.equals(paymentMethod(order))) {
			throw new IllegalArgumentException("Chỉ đơn chuyển khoản QR mới cần xác nhận thủ công");
		}
		if (!PAID.equals(paymentStatus(order)) && order.getStatus() != 0 && order.getStatus() != 1) {
			throw new IllegalArgumentException("Đơn hàng đã kết thúc. Vui lòng đối soát trước khi xác nhận thanh toán.");
		}
		markPaid(order, order.getPaymentReference());
		return order;
	}

	@Transactional
	public CallbackResult handleVnpay(Map<String, String> params) {
		String reference = params.get("vnp_TxnRef");
		if (!verifyVnpay(params)) {
			return new CallbackResult(null, false, "97", "Invalid signature");
		}
		if (!notBlank(reference)) {
			return new CallbackResult(null, false, "01", "Order not found");
		}
		Optional<Order> found = orderRepository.findByPaymentReference(reference);
		if (!found.isPresent()) {
			return new CallbackResult(null, false, "01", "Order not found");
		}
		Order order = locked(found.get());
		if (!VNPAY.equals(paymentMethod(order)) || !reference.equals(order.getPaymentReference())) {
			return new CallbackResult(null, false, "01", "Invalid payment method");
		}
		long callbackAmount = parseLong(params.get("vnp_Amount"), -1L);
		if (callbackAmount != Math.round(order.getAmount() * 100)) {
			return new CallbackResult(order, false, "04", "Invalid amount");
		}
		boolean success = "00".equals(params.get("vnp_ResponseCode"))
				&& "00".equals(params.get("vnp_TransactionStatus"));
		if (success) {
			markPaid(order, params.get("vnp_TransactionNo"));
		} else if (!PAID.equals(paymentStatus(order))) {
			order.setPaymentStatus(FAILED);
			orderRepository.save(order);
		}
		return new CallbackResult(order, success, "00", "Confirm Success");
	}

	@Transactional
	public CallbackResult handleMomo(Map<String, ?> params) {
		String reference = string(params, "orderId");
		if (!verifyMomo(params)) {
			return new CallbackResult(null, false, "97", "Invalid signature");
		}
		if (!notBlank(reference)) {
			return new CallbackResult(null, false, "01", "Order not found");
		}
		Optional<Order> found = orderRepository.findByPaymentReference(reference);
		if (!found.isPresent()) {
			return new CallbackResult(null, false, "01", "Order not found");
		}
		Order order = locked(found.get());
		if (!MOMO.equals(paymentMethod(order)) || !reference.equals(order.getPaymentReference()) || !reference.equals(string(params, "requestId"))) {
			return new CallbackResult(null, false, "01", "Invalid payment reference");
		}
		long callbackAmount = parseLong(string(params, "amount"), -1L);
		if (callbackAmount != Math.round(order.getAmount())) {
			return new CallbackResult(order, false, "04", "Invalid amount");
		}
		boolean success = "0".equals(string(params, "resultCode"));
		if (success) {
			markPaid(order, string(params, "transId"));
		} else if (!PAID.equals(paymentStatus(order))) {
			order.setPaymentStatus(FAILED);
			orderRepository.save(order);
		}
		return new CallbackResult(order, success, "0", "Success");
	}

	public String frontendResultUrl(CallbackResult result, String gateway) {
		String base = frontendUrl.replaceAll("/+$", "") + "/payment/result";
		if (result.getOrder() == null) {
			return base + "?gateway=" + encode(gateway) + "&gatewayError=invalid";
		}
		return base + "?orderId=" + result.getOrder().getOrdersId() + "&gateway=" + encode(gateway);
	}

	private Map<String, Object> startVnpay(Order order, String clientIp) {
		if (!vnpayConfigured()) {
			throw new IllegalStateException("VNPay Sandbox chưa được cấu hình");
		}
		long amount = Math.round(order.getAmount());
		if (amount < 5000) {
			throw new IllegalArgumentException("VNPay yêu cầu số tiền tối thiểu 5.000đ");
		}
		String reference = "VB" + order.getOrdersId() + "-" + System.currentTimeMillis();
		Map<String, String> fields = new TreeMap<>();
		fields.put("vnp_Version", "2.1.0");
		fields.put("vnp_Command", "pay");
		fields.put("vnp_TmnCode", vnpayTmnCode);
		fields.put("vnp_Amount", String.valueOf(amount * 100));
		fields.put("vnp_CurrCode", "VND");
		fields.put("vnp_TxnRef", reference);
		fields.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrdersId());
		fields.put("vnp_OrderType", "other");
		fields.put("vnp_Locale", "vn");
		fields.put("vnp_ReturnUrl", backendUrl.replaceAll("/+$", "") + "/api/payments/vnpay/return");
		fields.put("vnp_IpAddr", normalizeIp(clientIp));
		fields.put("vnp_CreateDate", vnpayDate(0));
        order.setPaymentGatewayDate(fields.get("vnp_CreateDate"));
		fields.put("vnp_ExpireDate", vnpayDate(15 * 60 * 1000L));
		String signData = canonicalUrlEncoded(fields);
		String secureHash = hmac("HmacSHA512", vnpayHashSecret, signData);
		order.setPaymentReference(reference);
		order.setPaymentStatus(PENDING);
		String redirectUrl = vnpayPayUrl + "?" + signData + "&vnp_SecureHash=" + secureHash;
		order.setPaymentCheckoutUrl(redirectUrl);
		orderRepository.save(order);
		Map<String, Object> response = orderStatus(order);
		response.put("redirectUrl", redirectUrl);
		return response;
	}

	private Map<String, Object> startMomo(Order order) {
		if (!momoConfigured()) {
			throw new IllegalStateException("MoMo Test chưa được cấu hình");
		}
		long amount = Math.round(order.getAmount());
		if (amount < 1000 || amount > 50000000) {
			throw new IllegalArgumentException("MoMo chỉ nhận giao dịch từ 1.000đ đến 50.000.000đ");
		}
		String reference = "VB" + order.getOrdersId() + "-" + System.currentTimeMillis();
		String requestId = reference;
		String redirectUrl = backendUrl.replaceAll("/+$", "") + "/api/payments/momo/return";
		String ipnUrl = backendUrl.replaceAll("/+$", "") + "/api/payments/momo/ipn";
		String orderInfo = "Thanh toan don hang " + order.getOrdersId();
		String extraData = Base64.getEncoder().encodeToString(
				("{\"orderId\":" + order.getOrdersId() + "}").getBytes(StandardCharsets.UTF_8));
		String rawSignature = "accessKey=" + momoAccessKey + "&amount=" + amount + "&extraData=" + extraData
				+ "&ipnUrl=" + ipnUrl + "&orderId=" + reference + "&orderInfo=" + orderInfo
				+ "&partnerCode=" + momoPartnerCode + "&redirectUrl=" + redirectUrl
				+ "&requestId=" + requestId + "&requestType=payWithMethod";
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("partnerCode", momoPartnerCode);
		payload.put("partnerName", "VinaBook");
		payload.put("storeId", "VinaBookStore");
		payload.put("requestType", "payWithMethod");
		payload.put("ipnUrl", ipnUrl);
		payload.put("redirectUrl", redirectUrl);
		payload.put("orderId", reference);
		payload.put("amount", amount);
		payload.put("lang", "vi");
		payload.put("orderInfo", orderInfo);
		payload.put("requestId", requestId);
		payload.put("extraData", extraData);
		payload.put("signature", hmac("HmacSHA256", momoSecretKey, rawSignature));

		order.setPaymentReference(reference);
		order.setPaymentStatus(PENDING);
		order.setPaymentCheckoutUrl(null);
		orderRepository.save(order);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(momoCreateUrl))
					.timeout(Duration.ofSeconds(20))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
					.build();
			HttpResponse<String> gatewayResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			Map<String, Object> body = objectMapper.readValue(gatewayResponse.body(), new TypeReference<Map<String, Object>>() { });
			if (gatewayResponse.statusCode() < 200 || gatewayResponse.statusCode() >= 300
					|| !"0".equals(string(body, "resultCode")) || string(body, "payUrl").isEmpty()) {
				throw new IllegalStateException(string(body, "message").isEmpty()
						? "MoMo không tạo được giao dịch" : string(body, "message"));
			}
			order.setPaymentCheckoutUrl(string(body, "payUrl"));
			orderRepository.save(order);
			Map<String, Object> response = orderStatus(order);
			response.put("redirectUrl", string(body, "payUrl"));
			return response;
		} catch (Exception ex) {
			// A lost create response does not prove that the gateway rejected the payment.
			order.setPaymentStatus(PENDING);
			orderRepository.save(order);
			if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
			if (ex instanceof IllegalStateException) {
				throw (IllegalStateException) ex;
			}
			throw new IllegalStateException("Không kết nối được MoMo Test", ex);
		}
	}

	private String buildBankQrUrl(Order order, String content) {
		return "https://img.vietqr.io/image/" + encode(bankId) + "-" + encode(bankAccountNo) + "-"
				+ encode(bankTemplate) + ".png?amount=" + Math.round(order.getAmount())
				+ "&addInfo=" + encode(content) + "&accountName=" + encode(bankAccountName);
	}

	private boolean verifyVnpay(Map<String, String> params) {
		if (!vnpayConfigured() || params.get("vnp_SecureHash") == null
				|| !vnpayTmnCode.equals(params.get("vnp_TmnCode"))) {
			return false;
		}
		Map<String, String> signed = new TreeMap<>();
		params.forEach((key, value) -> {
			if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)
					&& value != null && !value.isEmpty()) {
				signed.put(key, value);
			}
		});
		String expected = hmac("HmacSHA512", vnpayHashSecret, canonicalUrlEncoded(signed));
		return secureEquals(expected, params.get("vnp_SecureHash"));
	}

	private boolean verifyMomo(Map<String, ?> params) {
		if (!momoConfigured() || string(params, "signature").isEmpty()
				|| !momoPartnerCode.equals(string(params, "partnerCode"))) {
			return false;
		}
		String rawSignature = "accessKey=" + momoAccessKey + "&amount=" + string(params, "amount")
				+ "&extraData=" + string(params, "extraData") + "&message=" + string(params, "message")
				+ "&orderId=" + string(params, "orderId") + "&orderInfo=" + string(params, "orderInfo")
				+ "&orderType=" + string(params, "orderType") + "&partnerCode=" + string(params, "partnerCode")
				+ "&payType=" + string(params, "payType") + "&requestId=" + string(params, "requestId")
				+ "&responseTime=" + string(params, "responseTime") + "&resultCode=" + string(params, "resultCode")
				+ "&transId=" + string(params, "transId");
		return secureEquals(hmac("HmacSHA256", momoSecretKey, rawSignature), string(params, "signature"));
	}

	private void markPaid(Order order, String gatewayTransactionId) {
		if (!PAID.equals(paymentStatus(order))) {
			order.setPaymentStatus(PAID);
			order.setPaidAt(new Date());
			order.setPaymentCheckoutUrl(null);
			if (gatewayTransactionId != null && !gatewayTransactionId.trim().isEmpty()) {
				order.setPaymentTransactionId(gatewayTransactionId.trim());
			}
			orderRepository.save(order);
		}
	}

	private boolean gatewayInFlight(Order order) {
		return (VNPAY.equals(paymentMethod(order)) || MOMO.equals(paymentMethod(order))) && PENDING.equals(paymentStatus(order))
				&& notBlank(order.getPaymentReference()) && !order.getPaymentReference().startsWith("CHAT-");
	}

	private Order locked(Order order) {
		if (entityManager == null) return order; // Unit tests use detached fixtures.
		Order current = entityManager.find(Order.class, order.getOrdersId(), LockModeType.PESSIMISTIC_WRITE);
		if (current == null) throw new IllegalArgumentException("Không tìm thấy đơn hàng.");
		entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
		return current;
	}

	private Map<String, Object> method(boolean available, String label, String description) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("available", available);
		value.put("label", label);
		value.put("description", description);
		return value;
	}

	private boolean bankConfigured() {
		return notBlank(bankId) && notBlank(bankAccountNo) && notBlank(bankAccountName);
	}

	private boolean vnpayConfigured() {
		return notBlank(vnpayTmnCode) && notBlank(vnpayHashSecret);
	}

	private boolean momoConfigured() {
		return notBlank(momoPartnerCode) && notBlank(momoAccessKey) && notBlank(momoSecretKey);
	}

	private String paymentMethod(Order order) {
		return order.getPaymentMethod() == null ? COD : order.getPaymentMethod();
	}

	private String paymentStatus(Order order) {
		return order.getPaymentStatus() == null ? "UNPAID" : order.getPaymentStatus();
	}

	private String canonicalUrlEncoded(Map<String, String> fields) {
		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> field : new TreeMap<>(fields).entrySet()) {
			if (field.getValue() == null || field.getValue().isEmpty()) {
				continue;
			}
			if (result.length() > 0) {
				result.append('&');
			}
			result.append(encode(field.getKey())).append('=').append(encode(field.getValue()));
		}
		return result.toString();
	}

	private String vnpayDate(long offsetMs) {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
		format.setTimeZone(TimeZone.getTimeZone("GMT+7"));
		return format.format(new Date(System.currentTimeMillis() + offsetMs));
	}

	private String normalizeIp(String ip) {
		if (ip == null || ip.trim().isEmpty()) {
			return "127.0.0.1";
		}
		String first = ip.contains(",") ? ip.substring(0, ip.indexOf(',')).trim() : ip.trim();
		return first.matches("[0-9a-fA-F:.]+") ? first : "127.0.0.1";
	}

	private String hmac(String algorithm, String secret, String data) {
		try {
			Mac mac = Mac.getInstance(algorithm);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
			byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				hex.append(String.format("%02x", value));
			}
			return hex.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("Không thể ký yêu cầu thanh toán", ex);
		}
	}

	private boolean secureEquals(String first, String second) {
		if (first == null || second == null) {
			return false;
		}
		return MessageDigest.isEqual(first.toLowerCase().getBytes(StandardCharsets.UTF_8),
				second.toLowerCase().getBytes(StandardCharsets.UTF_8));
	}

	private String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private String string(Map<String, ?> map, String key) {
		Object value = map.get(key);
		return value == null ? "" : String.valueOf(value);
	}

	private long parseLong(String value, long fallback) {
		try {
			return Long.parseLong(value);
		} catch (Exception ex) {
			return fallback;
		}
	}

	private boolean notBlank(String value) {
		return value != null && !value.trim().isEmpty();
	}

	public static class CallbackResult {
		private final Order order;
		private final boolean success;
		private final String responseCode;
		private final String message;

		public CallbackResult(Order order, boolean success, String responseCode, String message) {
			this.order = order;
			this.success = success;
			this.responseCode = responseCode;
			this.message = message;
		}

		public Order getOrder() { return order; }
		public boolean isSuccess() { return success; }
		public String getResponseCode() { return responseCode; }
		public String getMessage() { return message; }
	}
}
