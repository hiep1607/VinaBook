package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ChatCommandGuardRegressionTest {
    @Test void negativeAndConditionalCommandsRequireInterpretation() {
        var language=new ChatIntentService();
        for(String message:java.util.List.of("Chưa thêm cuốn này vào giỏ", "Không cần mua ngay cuốn này",
                "Chưa xóa hết giỏ", "Nếu thêm cuốn này vào giỏ thì sao?", "Nút mua ngay ở đâu?", "Mua ngay là gì?"))
            assertThat(ChatCustomerActions.coreTool(language.normalize(message))).isEmpty();
    }
    @Test void refusingAnOrdinalDoesNotSelectIt() {
        var state=new ChatDialogueMemory.State();state.shortlist=new java.util.ArrayList<>(java.util.List.of(1L,2L));
        var request=new vn.fs.dto.chat.ChatRequestDto();request.setMessage("Không chọn cuốn thứ hai");
        assertThat(ChatLiveContext.plan(request,state,java.util.List.of(),new com.fasterxml.jackson.databind.ObjectMapper())).isNull();
        assertThat(state.focus).isNull();
    }
    @Test void wordAndShorthandCountsDoNotDefaultToOne() {
        assertThat(ChatCustomerActions.quantity("them vao gio so luong hai").value()).isEqualTo(2L);
        assertThat(ChatCustomerActions.quantity("doi so luong thanh hai").value()).isEqualTo(2L);
        assertThat(ChatCustomerActions.quantity("them dac nhan tam vao gio x2").value()).isEqualTo(2L);
        assertThat(ChatCustomerActions.quantity("them vao gio so luong vai").error()).isNotBlank();
    }
    @Test void compoundPolicyRequestCannotDropReadingChanges() {
        assertThat(ChatCustomerActions.coreTool("bo gioi han gia, roi cho biet phi ship")).isEmpty();
    }
    @Test void theOtherBookInheritsInventoryInsteadOfBecomingAContentTopic() {
        var state=new ChatDialogueMemory.State();state.focus=1L;
        state.comparisonProducts=new java.util.ArrayList<>(java.util.List.of(1L,2L));
        state.bookReadContext.put("tool","getProducts");state.bookReadContext.put("field","inventory");
        var request=new vn.fs.dto.chat.ChatRequestDto();request.setMessage("Còn cuốn kia thì sao?");
        var plan=ChatLiveContext.plan(request,state,java.util.List.of(),new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(plan.path("calls").get(0).path("arguments").path("reference").asText()).isEqualTo("other");
        assertThat(plan.path("calls").get(0).path("arguments").path("field").asText()).isEqualTo("inventory");
        assertThat(ChatBookDetailQuestions.active(request.getMessage(),new ChatIntentService())).isFalse();
    }
    @Test void decimalRatingIsADiscoveryConstraint() {
        assertThat(ChatCustomerActions.reviewMode("minh muon sach tu 4.5 sao nhung chua biet chu de")).isEmpty();
    }
    @Test void timeWordGioDoesNotMeanShoppingCart() {
        var language=new ChatIntentService();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Giờ mua thêm quà cho mẹ mình nữa."))).isEmpty();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Giờ mình muốn thêm quà cho mẹ."))).isEmpty();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Giờ tôi muốn thêm quà cho mẹ."))).isEmpty();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Thêm Đắc Nhân Tâm vào giỏ giúp mình."))).isEqualTo("prepareCartChange");
        assertThat(ChatCustomerActions.coreTool(language.normalize("Giỏ của mình có gì?"))).isEqualTo("getCart");
    }
    @Test void shippingTotalsAndNaturalOrderIdsUseTheirGroundedTools() {
        var language=new ChatIntentService();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Đơn tiền sách 299.999đ, 300.000đ và 300.001đ thì mỗi đơn hết bao nhiêu cả ship?"))).isEqualTo("getPolicy");
        assertThat(ChatCustomerActions.coreArguments("getOrderDetail","ma don cua minh la 999999999999").path("orderId").asLong()).isEqualTo(999999999999L);
    }
}
