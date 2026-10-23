package vn.fs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.Order;
import vn.fs.repository.OrderRepository;
import vn.fs.service.PaymentGatewayService;

class PaymentApiTest {
    private final OrderRepository orders=mock(OrderRepository.class);
    private final PaymentGatewayService payments=mock(PaymentGatewayService.class);
    private final CurrentUserAccess access=mock(CurrentUserAccess.class);
    private final PaymentApi api=new PaymentApi(orders,payments,access);

    @Test void manualBankConfirmationRequiresAdminEvenOutsideTheSecurityFilter() {
        assertThat(api.markPaid(7L).getStatusCodeValue()).isEqualTo(403);
        verifyNoInteractions(orders,payments);
    }
    @Test void manualConfirmationReturnsPaymentDataWithoutSerializingTheAccountEntity() {
        Order order=new Order();order.setOrdersId(7L);
        when(access.isAdmin()).thenReturn(true);when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(payments.markBankTransferPaid(order)).thenReturn(order);
        Map<String,Object> status=Map.of("orderId",7L,"paymentStatus","PAID");when(payments.orderStatus(order)).thenReturn(status);
        assertThat(api.markPaid(7L).getBody()).isSameAs(status);
    }
    @Test void aCustomerCannotOpenAnotherOwnersGateway() {
        Order order=new Order();order.setOrdersId(7L);vn.fs.entity.User user=new vn.fs.entity.User();user.setUserId(3L);order.setUser(user);
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        assertThat(api.start(7L,new MockHttpServletRequest()).getStatusCodeValue()).isEqualTo(403);verifyNoInteractions(payments);
    }
    @Test void invalidManualConfirmationKeepsTheOrderUnpaid() {
        Order order=new Order();when(access.isAdmin()).thenReturn(true);when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(payments.markBankTransferPaid(order)).thenThrow(new IllegalArgumentException("Đơn đã kết thúc"));
        assertThat(api.markPaid(7L).getStatusCodeValue()).isEqualTo(400);verify(payments,never()).orderStatus(any());
    }
}
