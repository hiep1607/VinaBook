package vn.fs.service;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import java.util.*;import org.junit.jupiter.api.Test;
import vn.fs.config.CurrentUserAccess;import vn.fs.dto.chat.*;import vn.fs.entity.Product;import vn.fs.repository.ProductRepository;
class CustomerFeatureChatTest {
 ProductRepository products=mock(ProductRepository.class);AiGateway ai=mock(AiGateway.class);ChatOrderSupportService orders=mock(ChatOrderSupportService.class);ChatIntentService intents=new ChatIntentService();ChatService service=new ChatService(products,ai,intents,new ChatRetrievalService(intents),new ChatPolicyService(intents),orders,mock(ChatAnalyticsService.class),mock(CurrentUserAccess.class));
 ChatRequestDto input(String text){ChatRequestDto r=new ChatRequestDto();r.setSessionId(UUID.randomUUID().toString());r.setMessage(text);return r;}
 @Test void voucherQuestionDoesNotTriggerBookRecommendation(){var r=service.reply(input("Dùng mã giảm giá thế nào?"));assertThat(r.getProducts()).isEmpty();assertThat(r.getAnswer()).contains("Mã giảm giá","tổng tiền");assertThat(r.getActions()).anyMatch(a->"/cart".equals(a.getRoute()));verifyNoInteractions(ai);}
 @Test void restockQuestionShowsSelectedOutOfStockBook(){Product p=new Product();p.setProductId(7L);p.setStatus(true);p.setQuantity(0);p.setName("Cuốn đang chờ");p.setPrice(100000d);when(products.findById(7L)).thenReturn(Optional.of(p));var request=input("Báo mình khi có hàng");request.setActiveProductId(7L);var reply=service.reply(request);assertThat(reply.getProducts()).hasSize(1);assertThat(reply.getAnswer()).contains("Báo mình khi có hàng");}
 @Test void notificationsQuestionLinksToAccountPage(){var reply=service.reply(input("Thông báo của tôi"));assertThat(reply.getActions()).anyMatch(a->"/notifications".equals(a.getRoute()));assertThat(reply.getProducts()).isEmpty();}
 @Test void refundStatusUsesOwnerAwareOrderLookup(){when(orders.orderById(9L)).thenReturn("Đơn #9: Hoàn tiền: đang xử lý");var reply=service.reply(input("Kiểm tra hoàn tiền đơn #9"));verify(orders).orderById(9L);assertThat(reply.getActions()).anyMatch(a->"/orders/9".equals(a.getRoute()));}
 @Test void foreignOrderNeverGetsPrivateDetailsLink(){when(orders.orderById(9L)).thenReturn("Không tìm thấy trong tài khoản của bạn");var reply=service.reply(input("Trạng thái hoàn tiền đơn #9"));assertThat(reply.getActions()).isEmpty();}
}
