package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
class ChatOrderSupportServiceTest {
    CurrentUserAccess access=mock(CurrentUserAccess.class);
    OrderRepository orders=mock(OrderRepository.class); UserRepository users=mock(UserRepository.class);
    OrderDetailRepository details=mock(OrderDetailRepository.class);
    ChatOrderSupportService service=new ChatOrderSupportService(access,users,orders);
    @Test void reorderReadsOwnedOrderWithoutOldPricesOrPrivateContactData() {
        service.setDetails(details); when(access.currentEmail()).thenReturn("buyer@test.invalid");
        var user=new User();user.setEmail("buyer@test.invalid");
        var order=new Order();order.setOrdersId(7L);order.setUser(user);when(orders.findById(7L)).thenReturn(Optional.of(order));
        var product=new Product();product.setProductId(1L);product.setName("Sách thử");
        when(details.findByOrder(order)).thenReturn(List.of(new OrderDetail(1L,2,90000D,product,order)));
        var result=service.reorderPreview(7L);
        assertThat(result).containsEntry("orderId",7L).doesNotContainKeys("email","address","phone","price","total");
        assertThat((List<?>)result.get("items")).hasSize(1);verify(orders,never()).save(any());
    }
    @Test void reorderRejectsGuestOrAnotherOwner() {
        assertThatThrownBy(()->service.reorderPreview(7L)).isInstanceOf(ResponseStatusException.class);
        when(access.currentEmail()).thenReturn("other@test.invalid");
        var user=new User();user.setEmail("buyer@test.invalid");var order=new Order();order.setUser(user);
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        assertThatThrownBy(()->service.reorderPreview(7L)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(details);
    }
}
