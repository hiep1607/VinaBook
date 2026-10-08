package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.entity.*;
import vn.fs.repository.*;
class RefundGatewayTest {
 PaymentGatewayService gateway=new PaymentGatewayService(mock(OrderRepository.class),new ObjectMapper());Order order=new Order();PaymentRefund refund=new PaymentRefund();
 @BeforeEach void setup(){ReflectionTestUtils.setField(gateway,"vnpayHashSecret","test-secret");ReflectionTestUtils.setField(gateway,"vnpayTmnCode","test-merchant");ReflectionTestUtils.setField(gateway,"momoPartnerCode","test-partner");order.setPaymentReference("VB9-original");refund.setProvider("VNPAY");refund.setAmount(10000);refund.setGatewayReference("refund123");}
 @Test void invalidVnpaySignatureNeverReportsSuccess(){Map<String,Object> response=new HashMap<>();response.put("vnp_ResponseCode","00");response.put("vnp_TransactionStatus","00");response.put("vnp_SecureHash","forged");assertThat(gateway.refundResult(order,refund,Map.of(),response)).isEqualTo("UNKNOWN");}
 @Test void momoRequiresExactAmountAndRefundIdentity(){refund.setProvider("MOMO");Map<String,Object> r=new HashMap<>(Map.of("partnerCode","test-partner","orderId","refund123","requestId","refund123","amount",10000,"resultCode",0));assertThat(gateway.refundResult(order,refund,Map.of(),r)).isEqualTo("SUCCEEDED");r.put("amount",20000);assertThat(gateway.refundResult(order,refund,Map.of(),r)).isEqualTo("UNKNOWN");r.put("amount",10000);r.put("orderId","original-order");assertThat(gateway.refundResult(order,refund,Map.of(),r)).isEqualTo("UNKNOWN");}
}
