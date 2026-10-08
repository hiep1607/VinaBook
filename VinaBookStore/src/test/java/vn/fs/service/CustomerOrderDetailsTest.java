package vn.fs.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
class CustomerOrderDetailsTest {
    final CurrentUserAccess access = mock(CurrentUserAccess.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderDetailRepository details = mock(OrderDetailRepository.class);
    final ChatOrderSupportService service = new ChatOrderSupportService(access, mock(UserRepository.class), orders);
    CustomerOrderDetailsTest() { service.setDetails(details); }
    Order ownOrder() {
        when(access.currentEmail()).thenReturn("owner@example.com");
        var user = new User(); user.setEmail("owner@example.com"); user.setName("Tên mới");
        var order = new Order(); order.setOrdersId(7L); order.setUser(user); order.setAmount(120000d);
        order.setAddress("Hà Nội (Người nhận: Người nhận cũ)"); order.setPhone("0900000000");
        when(orders.findById(7L)).thenReturn(Optional.of(order)); when(details.findByOrder(order)).thenReturn(List.of());
        return order;
    }
    @Test void anonymousCannotRead() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.customerDetails(7L)).getRawStatusCode()); verifyNoInteractions(orders);
    }
    @Test void anotherOwnerIsIndistinguishableFromMissingOrder() {
        ownOrder(); when(access.currentEmail()).thenReturn("other@example.com");
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.customerDetails(7L)).getRawStatusCode()); verifyNoInteractions(details);
    }
    @Test void invalidAndMissingIdsAreNotFound() {
        ownOrder(); when(orders.findById(99L)).thenReturn(Optional.empty());
        for (Long id : new Long[]{0L,-1L,99L}) assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.customerDetails(id)).getRawStatusCode());
    }
    @Test void responseUsesSavedRecipientAndNeverSerializesAccount() {
        ownOrder(); var result = service.customerDetails(7L);
        assertEquals(java.util.Map.of("name","Người nhận cũ","phone","0900000000","street","Hà Nội"), result.get("shippingAddress"));
        assertFalse(result.containsKey("user")); assertFalse(result.containsKey("token")); assertFalse(result.containsKey("paymentTransactionId"));
    }
    @Test void keepsHistoricalPriceAndHandlesRemovedBook() {
        var order=ownOrder(); var line=new OrderDetail(); line.setPrice(50000d); line.setQuantity(2);
        when(details.findByOrder(order)).thenReturn(List.of(line));
        var item=((List<java.util.Map<String,Object>>)service.customerDetails(7L).get("items")).get(0);
        assertEquals(50000d,item.get("price")); assertNull(item.get("productId")); assertEquals("Sách không còn thông tin",item.get("name"));
    }
}
