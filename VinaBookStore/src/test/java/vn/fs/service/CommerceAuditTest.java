package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.dto.chat.*;
import vn.fs.repository.*;

class CommerceAuditTest {
    @Test void applicationAnswersQuestionWithExplicitlySeparateIllustration() throws Exception {
        AiGateway ai=mock(AiGateway.class);ProductRepository products=mock(ProductRepository.class);
        Product book=new Product();book.setProductId(57L);book.setName("Sách thử");book.setDescription("Nguyên tắc tránh chỉ trích và lên án khi giao tiếp.");
        when(products.findByStatusTrue()).thenReturn(List.of(book));
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn("{\"evidenceField\":\"summary\",\"evidence\":\"Nguyên tắc tránh chỉ trích và lên án khi giao tiếp.\",\"principle\":\"Tránh chỉ trích người đối diện khi giao tiếp.\",\"example\":\"Bạn có thể nói: Mình chưa hiểu lý do chọn cách đó, bạn giải thích thêm nhé?\"}");
        ChatBookNarrator narrator=new ChatBookNarrator(ai,new ObjectMapper(),products,new ChatIntentService());
        var product=new ChatProductDto(57L,"Sách thử","Tác giả","Kỹ năng",70520,86000,18,8,"");
        var base=new ChatTool.Result("Giới thiệu lặp",ChatIntent.PRODUCT_INFO,List.of(product),List.of(),"COMPLETED");
        var result=narrator.explain(base,"Lấy ví dụ khi bất đồng với đồng nghiệp thì mình nên nói câu gì?",new ChatDialogueMemory.State());
        assertThat(result.answer()).contains("Mình chưa hiểu lý do", "minh họa tự xây dựng", "không phải trích sách").doesNotContain("Giới thiệu lặp","Mục tiêu đọc:");
        assertThat(result.products()).isSameAs(base.products());assertThat(result.actions()).isSameAs(base.actions());
    }
    @Test void invalidEvidenceFallsBackToGroundedIllustrationInsteadOfInventedBookContent() throws Exception {
        AiGateway ai=mock(AiGateway.class);ProductRepository products=mock(ProductRepository.class);
        Product book=new Product();book.setProductId(57L);book.setName("Sách thử");book.setDescription("Nguyên tắc tránh chỉ trích và lên án khi giao tiếp.");
        when(products.findByStatusTrue()).thenReturn(List.of(book));
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn("{\"evidenceField\":\"summary\",\"evidence\":\"Không có trong nguồn\",\"principle\":\"Nội dung bịa\",\"example\":\"Ví dụ bịa từ chương bảy\"}");
        var narrator=new ChatBookNarrator(ai,new ObjectMapper(),products,new ChatIntentService());
        var base=new ChatTool.Result("Giới thiệu",ChatIntent.PRODUCT_INFO,List.of(new ChatProductDto(57L,"Sách thử","Tác giả","Kỹ năng",70520,86000,18,8,"")),List.of(),"COMPLETED");
        assertThat(narrator.explain(base,"Cho ví dụ khi bất đồng với đồng nghiệp",new ChatDialogueMemory.State()).answer())
            .contains("tiến độ","không phải trích sách").doesNotContain("Nội dung bịa","chương bảy");
    }
    @Test void applicationRouteDoesNotCapturePurchaseOrOrderInstructions() {
        var language=new ChatIntentService();
        assertThat(ChatBookApplication.requested("Cho ví dụ áp dụng ý vừa nói",language)).isTrue();
        assertThat(ChatBookApplication.requested("Ví dụ mua ngay hai cuốn thì sao",language)).isFalse();
        assertThat(ChatBookApplication.requested("Gợi ý sách có ví dụ thực tế",language)).isFalse();
    }
    @Test void pageReadsOwnedOrdersAndFetchesAllItemsOnce() {
        var access=mock(CurrentUserAccess.class);var users=mock(UserRepository.class);var orders=mock(OrderRepository.class);var details=mock(OrderDetailRepository.class);
        User owner=new User();owner.setUserId(42L);owner.setEmail("owner@test.invalid");
        when(access.currentEmail()).thenReturn(owner.getEmail());when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        Order order=new Order();order.setOrdersId(48L);order.setUser(owner);order.setStatus(3);order.setAmount(138800D);
        when(orders.findCustomerPage(eq(42L),eq(-1),eq("harry"),eq("%harry%"),any())).thenReturn(new PageImpl<>(List.of(order),PageRequest.of(0,20),41));
        Product book=new Product();book.setProductId(52L);book.setName("Harry Potter");book.setImage("cover.jpg");
        OrderDetail line=new OrderDetail();line.setOrder(order);line.setProduct(book);line.setQuantity(1);line.setPrice(118800D);
        when(details.findCustomerPageItems(List.of(48L))).thenReturn(List.of(line));
        when(orders.customerCounts(42L)).thenReturn(Collections.singletonList(new Object[]{3,41L,5690800D}));
        var service=new ChatOrderSupportService(access,users,orders);service.setDetails(details);
        var result=service.customerPage(0,20,-1,"harry");
        assertThat(result.get("total")).isEqualTo(41L);assertThat(result.get("counts")).isEqualTo(Map.of("3",41L,"-1",41L));
        assertThat(result.get("items").toString()).contains("Harry Potter", "cover.jpg").doesNotContain("owner@test.invalid");
        verify(details,times(1)).findCustomerPageItems(List.of(48L));verify(details,never()).findByOrder(any());
    }
    @Test void pageRequiresAuthenticationBeforeAnyRepositoryRead() {
        var access=mock(CurrentUserAccess.class);var users=mock(UserRepository.class);var orders=mock(OrderRepository.class);
        var service=new ChatOrderSupportService(access,users,orders);
        assertThatThrownBy(()->service.customerPage(0,20,-1,"")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(users,orders);
    }
}
