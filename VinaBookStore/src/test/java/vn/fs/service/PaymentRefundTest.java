package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
class PaymentRefundTest {
 CurrentUserAccess access=mock(CurrentUserAccess.class);OrderRepository orders=mock(OrderRepository.class);PaymentRefundRepository rows=mock(PaymentRefundRepository.class);
 PaymentGatewayService gateway=mock(PaymentGatewayService.class);EntityManager em=mock(EntityManager.class);PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
 PaymentRefundService service=new PaymentRefundService(access,orders,rows,gateway,em,manager);Order order=new Order();List<PaymentRefund> saved=new ArrayList<>();
 @BeforeEach void setup(){order.setOrdersId(9L);order.setAmount(100000d);order.setPaymentMethod("VNPAY");User u=new User();u.setUserId(3L);order.setUser(u);when(access.isAdmin()).thenReturn(true);when(access.currentUserId()).thenReturn(1L);when(orders.findById(9L)).thenReturn(Optional.of(order));when(em.find(Order.class,9L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(rows.findByOrderIdOrderByIdDesc(9L)).thenAnswer(c->saved);when(rows.findByOrderIdAndRequestKey(any(),any())).thenAnswer(c->saved.stream().filter(r->r.getRequestKey().equals(c.getArgument(1))).findFirst());when(rows.saveAndFlush(any())).thenAnswer(c->{PaymentRefund r=c.getArgument(0);if(r.getId()==null){r.setId(1L);saved.add(r);}return r;});when(em.find(PaymentRefund.class,1L,LockModeType.PESSIMISTIC_WRITE)).thenAnswer(c->saved.get(0));when(gateway.refundUnavailable(order)).thenReturn("");when(gateway.refund(eq(order),any())).thenReturn("UNKNOWN");}
 PaymentRefundService.Input input(){var i=new PaymentRefundService.Input();i.setAmount(60000L);i.setReason("Khách trả sách bị lỗi");i.setRequestKey("refund-stable-key");i.setConfirmed(true);return i;}
 @Test void reservesAndCommitsBeforeGatewayAndRepeatedSubmitNeverSendsTwice(){service.confirm(9L,input());service.confirm(9L,input());var inOrder=inOrder(manager,gateway);inOrder.verify(manager).commit(any());inOrder.verify(gateway).refund(eq(order),any());verify(gateway,times(1)).refund(any(),any());assertThat(saved).hasSize(1);}
 @Test void uncertainRefundKeepsLimitAndAnotherRequestCannotExceedPaid(){service.confirm(9L,input());var next=input();next.setRequestKey("refund-another-key");assertThatThrownBy(()->service.confirm(9L,next)).isInstanceOf(ResponseStatusException.class);verify(gateway,times(1)).refund(any(),any());assertThat(service.preview(9L,true)).containsEntry("remaining",40000L).containsEntry("refunded",0L);}
 @Test void successIsVisibleToOwnerWithoutStaffReasonOrReferences(){when(gateway.refund(eq(order),any())).thenReturn("SUCCEEDED");service.confirm(9L,input());when(access.ownsUserId(3L)).thenReturn(true);Map<String,Object> result=service.preview(9L,false);assertThat(result).containsEntry("refunded",60000L).doesNotContainKeys("available","remaining");assertThat(result.toString()).doesNotContain("Khách trả sách","gatewayReference","staffId");}
 @Test void wrongRoleOrOwnerCannotReadOrCreateRefund(){when(access.isAdmin()).thenReturn(false);assertThatThrownBy(()->service.confirm(9L,input())).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.preview(9L,false)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(gateway);}
 @Test void disabledGatewayOrUnconfirmedInputNeverSendsMoney(){when(gateway.refundUnavailable(order)).thenReturn("Chưa cấu hình");assertThatThrownBy(()->service.confirm(9L,input())).isInstanceOf(ResponseStatusException.class);var i=input();i.setConfirmed(false);assertThatThrownBy(()->service.confirm(9L,i)).isInstanceOf(ResponseStatusException.class);verify(gateway,never()).refund(any(),any());verify(rows,never()).saveAndFlush(any());}
}
