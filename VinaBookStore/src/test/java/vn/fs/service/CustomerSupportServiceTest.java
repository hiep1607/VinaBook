package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import javax.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
class CustomerSupportServiceTest {
    CurrentUserAccess access=mock(CurrentUserAccess.class);SupportRequestRepository requests=mock(SupportRequestRepository.class);
    OrderRepository orders=mock(OrderRepository.class);ChatOrderSupportService history=mock(ChatOrderSupportService.class);EntityManager em=mock(EntityManager.class);
    CustomerSupportService service=new CustomerSupportService(access,requests,orders,history,em);CustomerSupportService.Create input;Order order;
    @BeforeEach void setup(){
        when(access.currentUserId()).thenReturn(1L);when(em.find(User.class,1L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(new User());
        when(requests.findByOwnerIdAndRequestKey(any(),any())).thenReturn(Optional.empty());
        when(requests.saveAndFlush(any())).thenAnswer(call->{SupportRequest r=call.getArgument(0);r.setId(9L);return r;});
        input=new CustomerSupportService.Create();input.setRequestKey("support-test-key");input.setKind("RETURN");input.setOrderId(7L);input.setProductId(2L);
        input.setReceivedOn(LocalDate.now());input.setOutcome("EXCHANGE");input.setMessage("Sách bị thiếu trang, xin đổi sách.");input.setConfirmed(true);
        when(history.reorderPreview(7L)).thenReturn(Map.of("orderId",7L,"items",List.of(Map.of("productId",2L,"name","Sách thử","quantity",1))));
        order=new Order();order.setStatus(2);order.setOrderDate(new Date(System.currentTimeMillis()-86400000L));when(orders.findById(7L)).thenReturn(Optional.of(order));
    }
    @Test void returnCreatesOnlyAPendingTicketAndNeverChangesOrderOrStock(){
        var result=service.create(input);assertThat(result).containsEntry("id",9L).containsEntry("status","OPEN").doesNotContainKeys("ownerId","user","email","phone","address");
        verify(orders,never()).save(any());assertThat(order.getStatus()).isEqualTo(2);
    }
    @Test void duplicateConfirmationReturnsSameTicketExactlyOnce(){
        service.create(input);var saved=org.mockito.ArgumentCaptor.forClass(SupportRequest.class);verify(requests).saveAndFlush(saved.capture());
        when(requests.findByOwnerIdAndRequestKey(1L,input.getRequestKey())).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.create(input)).containsEntry("id",9L);verify(requests,times(1)).saveAndFlush(any());
        input.setMessage("Nội dung khác");assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void guestOrUnconfirmedRequestsCannotBeCreated(){
        input.setConfirmed(false);assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);
        input.setConfirmed(true);when(access.currentUserId()).thenReturn(null);assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);verify(requests,never()).saveAndFlush(any());
    }
    @Test void returnRequiresCompletedOwnedOrderAndARealBookInIt(){
        input.setProductId(999L);assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);
        input.setProductId(2L);order.setStatus(0);assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);
        order.setStatus(2);when(history.reorderPreview(7L)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);verify(requests,never()).saveAndFlush(any());
    }
    @Test void receiptDateMustBeBetweenOrderDateAndToday(){
        input.setReceivedOn(LocalDate.now().plusDays(1));assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);
        input.setReceivedOn(LocalDate.now().minusDays(10));assertThatThrownBy(()->service.create(input)).isInstanceOf(ResponseStatusException.class);verify(requests,never()).saveAndFlush(any());
    }
    @Test void supportNeedsNoReturnFieldsAndUsesJwtOwner(){
        input.setKind("SUPPORT");input.setOrderId(null);input.setProductId(null);input.setReceivedOn(null);input.setOutcome(null);
        service.create(input);var saved=org.mockito.ArgumentCaptor.forClass(SupportRequest.class);verify(requests).saveAndFlush(saved.capture());assertThat(saved.getValue().getOwnerId()).isEqualTo(1L);
    }
    @Test void personalHistoryAndRecoveryAreScopedToJwtOwner(){
        when(requests.findTop50ByOwnerIdOrderByIdDesc(1L)).thenReturn(List.of());assertThat(service.mine()).isEmpty();verify(requests).findTop50ByOwnerIdOrderByIdDesc(1L);
        assertThatThrownBy(()->service.byKey("another-key")).isInstanceOf(ResponseStatusException.class);verify(requests).findByOwnerIdAndRequestKey(1L,"another-key");
    }
    @Test void adminRoleAndFreshVersionAreRequiredForAReply(){
        var response=new CustomerSupportService.Response();response.setVersion(0L);response.setStatus("IN_REVIEW");response.setReply("Cửa hàng đang xem xét.");
        assertThatThrownBy(()->service.respond(9L,response)).isInstanceOf(ResponseStatusException.class);
        when(access.isAdmin()).thenReturn(true);var saved=new SupportRequest();saved.setId(9L);saved.setVersion(1L);when(em.find(SupportRequest.class,9L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(saved);
        assertThatThrownBy(()->service.respond(9L,response)).isInstanceOf(ResponseStatusException.class);
        response.setVersion(1L);assertThat(service.respond(9L,response)).containsEntry("status","IN_REVIEW").containsEntry("reply","Cửa hàng đang xem xét.");
    }
}
