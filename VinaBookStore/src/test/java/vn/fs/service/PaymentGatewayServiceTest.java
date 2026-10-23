package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.entity.Order;
import vn.fs.repository.OrderRepository;
import vn.fs.service.PaymentGatewayService.CallbackResult;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

	@Mock
	private OrderRepository orderRepository;

	private PaymentGatewayService service;

	@BeforeEach
	void setUp() {
		service = new PaymentGatewayService(orderRepository, new ObjectMapper());
		ReflectionTestUtils.setField(service, "bankId", "970422");
		ReflectionTestUtils.setField(service, "bankAccountNo", "123456789");
		ReflectionTestUtils.setField(service, "bankAccountName", "VINABOOK TEST");
		ReflectionTestUtils.setField(service, "bankTemplate", "compact2");
		ReflectionTestUtils.setField(service, "vnpayTmnCode", "TESTCODE");
		ReflectionTestUtils.setField(service, "vnpayHashSecret", "test-secret");
		ReflectionTestUtils.setField(service, "momoPartnerCode", "");
		ReflectionTestUtils.setField(service, "momoAccessKey", "");
		ReflectionTestUtils.setField(service, "momoSecretKey", "");
	}

	@Test
	void methodsExposeAvailabilityButNeverCredentials() {
		String json = service.availableMethods().toString();

		assertThat(json).contains("BANK_QR={available=true")
				.contains("VNPAY={available=true")
				.contains("MOMO={available=false")
				.doesNotContain("test-secret")
				.doesNotContain("123456789");
	}

	@Test
	void bankQrIsBoundToOrderAmountAndReference() {
		Order order = order(57L, 70520D, "BANK_QR", "PENDING", "VB57");

		Map<String, Object> status = service.orderStatus(order);

		assertThat(status.get("transferContent")).isEqualTo("VB57");
		assertThat(status.get("amount")).isEqualTo(70520L);
		assertThat(status.get("qrUrl").toString())
				.contains("amount=70520")
				.contains("addInfo=VB57");
	}

	@Test
	void invalidVnpaySignatureNeverChangesPaymentStatus() {
		Order order = order(58L, 100000D, "VNPAY", "PENDING", "VB58-1");
		Map<String, String> callback = new LinkedHashMap<>();
		callback.put("vnp_TxnRef", "VB58-1");
		callback.put("vnp_Amount", "10000000");
		callback.put("vnp_ResponseCode", "00");
		callback.put("vnp_TransactionStatus", "00");
		callback.put("vnp_SecureHash", "invalid");

		CallbackResult result = service.handleVnpay(callback);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.getResponseCode()).isEqualTo("97");
		assertThat(order.getPaymentStatus()).isEqualTo("PENDING");
		verify(orderRepository, never()).findByPaymentReference("VB58-1");
		verify(orderRepository, never()).save(order);
	}

	private Order order(Long id, Double amount, String method, String status, String reference) {
		Order order = new Order();
		order.setOrdersId(id);
		order.setAmount(amount);
		order.setPaymentMethod(method);
		order.setPaymentStatus(status);
		order.setPaymentReference(reference);
		return order;
	}

    @Test void terminalOrdersNeverExposeAUsableBankQr() {
        Order order = order(57L, 70520D, "BANK_QR", "PENDING", "VB57");
        order.setStatus(3);
        Map<String,Object> status = service.orderStatus(order);
        assertThat(status).containsEntry("canStart", false).containsEntry("canPoll", false).doesNotContainKey("qrUrl");
        assertThatThrownBy(() -> service.start(order,"127.0.0.1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.markBankTransferPaid(order)).isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository,never()).save(order);
    }

    @Test void codAndPaidOrdersDoNotOfferAnotherPayment() {
        Order cod=order(1L,120000D,"COD","UNPAID",null);
        assertThat(service.orderStatus(cod)).containsEntry("canStart",false).containsEntry("canPoll",false);
        Order paid=order(2L,120000D,"BANK_QR","PAID","VB2");
        assertThat(service.orderStatus(paid)).containsEntry("canStart",false).doesNotContainKey("qrUrl");
    }

    @Test void missingProviderConfigurationDisablesStartingButKeepsStatusVisible() {
        Order order=order(1L,120000D,"MOMO","PENDING",null);
        assertThat(service.orderStatus(order)).containsEntry("methodAvailable",false).containsEntry("canStart",false);
    }

    @Test void bankConfirmationIsIdempotentAndKeepsTheOriginalReceipt() {
        Order order=order(2L,120000D,"BANK_QR","PAID","VB2");order.setPaymentTransactionId("receipt-original");
        service.markBankTransferPaid(order);
        assertThat(order.getPaymentTransactionId()).isEqualTo("receipt-original");verify(orderRepository,never()).save(order);
    }

    @Test void signedVnpayCallbackCannotPayAnOrderUsingAnotherMethod() {
        Order order=order(58L,100000D,"BANK_QR","PENDING","VB58-1");
        when(orderRepository.findByPaymentReference("VB58-1")).thenReturn(Optional.of(order));
        Map<String,String> callback=vnpayCallback("10000000");
        assertThat(service.handleVnpay(callback).isSuccess()).isFalse();verify(orderRepository,never()).save(order);
    }

    @Test void signedVnpayCallbackRejectsWrongAmount() {
        Order order=order(58L,100000D,"VNPAY","PENDING","VB58-1");
        when(orderRepository.findByPaymentReference("VB58-1")).thenReturn(Optional.of(order));
        assertThat(service.handleVnpay(vnpayCallback("9900000")).getResponseCode()).isEqualTo("04");
        assertThat(order.getPaymentStatus()).isEqualTo("PENDING");verify(orderRepository,never()).save(order);
    }

    @Test void aLateFailureDoesNotDowngradeAnAlreadyPaidOrder() {
        Order order=order(58L,100000D,"VNPAY","PAID","VB58-1");order.setPaymentTransactionId("original");
        when(orderRepository.findByPaymentReference("VB58-1")).thenReturn(Optional.of(order));
        Map<String,String> callback=vnpayCallback("10000000");callback.put("vnp_ResponseCode","24");signVnpay(callback);
        service.handleVnpay(callback);
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");assertThat(order.getPaymentTransactionId()).isEqualTo("original");
        verify(orderRepository,never()).save(order);
    }

    private Map<String,String> vnpayCallback(String amount) {
        Map<String,String> callback=new LinkedHashMap<>();callback.put("vnp_TmnCode","TESTCODE");
        callback.put("vnp_TxnRef","VB58-1");callback.put("vnp_Amount",amount);callback.put("vnp_ResponseCode","00");
        callback.put("vnp_TransactionStatus","00");callback.put("vnp_TransactionNo","1234");signVnpay(callback);return callback;
    }

    @Test void aValidVnpayCallbackRecordsOneReceipt() {
        Order order=order(58L,100000D,"VNPAY","PENDING","VB58-1");
        when(orderRepository.findByPaymentReference("VB58-1")).thenReturn(Optional.of(order));
        assertThat(service.handleVnpay(vnpayCallback("10000000")).isSuccess()).isTrue();
        service.handleVnpay(vnpayCallback("10000000"));
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");assertThat(order.getPaymentTransactionId()).isEqualTo("1234");
        verify(orderRepository).save(order);
    }

    @Test void reopeningPendingVnpayReusesTheStoredSession() {
        Order order=order(58L,100000D,"VNPAY","PENDING","VB58-1");order.setPaymentCheckoutUrl("https://sandbox.vnpayment.vn/existing");
        assertThat(service.start(order,"127.0.0.1")).containsEntry("redirectUrl","https://sandbox.vnpayment.vn/existing");
        assertThat(order.getPaymentReference()).isEqualTo("VB58-1");verify(orderRepository,never()).save(order);
    }

    @Test void uncertainPendingGatewayDoesNotStartASecondTransaction() {
        Order order=order(58L,100000D,"VNPAY","PENDING","VB58-1");
        assertThat(service.orderStatus(order)).containsEntry("canStart",false);
        assertThatThrownBy(() -> service.start(order,"127.0.0.1")).isInstanceOf(IllegalStateException.class).hasMessageContaining("chờ xác nhận");
        verify(orderRepository,never()).save(order);
    }

    @Test void initialVnpayStartStoresTheUrlAndLaterReusesIt() {
        ReflectionTestUtils.setField(service,"vnpayPayUrl","https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(service,"backendUrl","http://localhost:8080");
        Order order=order(58L,100000D,"VNPAY","PENDING","CHAT-draft-token");
        Map<String,Object> first=service.start(order,"127.0.0.1");
        assertThat(service.start(order,"127.0.0.1").get("redirectUrl")).isEqualTo(first.get("redirectUrl"));
        assertThat(order.getPaymentCheckoutUrl()).isEqualTo(first.get("redirectUrl"));verify(orderRepository).save(order);
    }

    @Test void momoCallbackRequiresTheOriginalRequestId() {
        Order order=order(58L,100000D,"MOMO","PENDING","VB58-1");
        when(orderRepository.findByPaymentReference("VB58-1")).thenReturn(Optional.of(order));
        assertThat(service.handleMomo(momoCallback("other-request", "100000")).isSuccess()).isFalse();
        verify(orderRepository,never()).save(order);
    }

    @Test void momoCallbackRequiresTheExactAmountBeforeRecordingPayment() {
        Order order=order(58L,100000D,"MOMO","PENDING","VB58-1");
        when(orderRepository.findByPaymentReference("VB58-1")).thenReturn(Optional.of(order));
        assertThat(service.handleMomo(momoCallback("VB58-1", "99000")).getResponseCode()).isEqualTo("04");
        assertThat(service.handleMomo(momoCallback("VB58-1", "100000")).isSuccess()).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");verify(orderRepository).save(order);
    }

    private Map<String,String> momoCallback(String requestId, String amount) {
        ReflectionTestUtils.setField(service,"momoPartnerCode","PARTNER");
        ReflectionTestUtils.setField(service,"momoAccessKey","access");ReflectionTestUtils.setField(service,"momoSecretKey","secret");
        Map<String,String> params=new LinkedHashMap<>();params.put("partnerCode","PARTNER");params.put("orderId","VB58-1");
        params.put("requestId",requestId);params.put("amount",amount);params.put("resultCode","0");params.put("transId","1234");
        String raw="accessKey=access&amount="+amount+"&extraData=&message=&orderId=VB58-1&orderInfo=&orderType=&partnerCode=PARTNER&payType=&requestId="+requestId+"&responseTime=&resultCode=0&transId=1234";
        params.put("signature",ReflectionTestUtils.invokeMethod(service,"hmac","HmacSHA256","secret",raw));return params;
    }
    private void signVnpay(Map<String,String> callback) {
        callback.remove("vnp_SecureHash");
        String canonical=ReflectionTestUtils.invokeMethod(service,"canonicalUrlEncoded",callback);
        callback.put("vnp_SecureHash",ReflectionTestUtils.invokeMethod(service,"hmac","HmacSHA512","test-secret",canonical));
    }
}
