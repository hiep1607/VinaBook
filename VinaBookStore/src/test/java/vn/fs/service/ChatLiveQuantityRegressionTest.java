package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Replays the real quantity/login failures through real validation and preparation tools. */
class ChatLiveQuantityRegressionTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ChatIntentService language=new ChatIntentService();
    final ProductRepository products=mock(ProductRepository.class);
    final ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
    final CurrentUserAccess access=mock(CurrentUserAccess.class);
    final AiGateway ai=mock(AiGateway.class);
    final ChatAnalyticsService analytics=mock(ChatAnalyticsService.class);
    final ChatRetrievalService data=new ChatRetrievalService(language);
    final ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    Product book;
    ChatToolRegistry registry;
    ChatTurnUnderstanding understanding;
    ChatStructuredPipeline pipeline;

    @BeforeEach void setup() {
        book=new Product(57L,"Đắc Nhân Tâm","Dale Carnegie",220,86000d,18,"cover.jpg","Sách kỹ năng giao tiếp.",LocalDate.now(),true,0,new Category(1L,"Kỹ năng sống"));
        when(products.findByStatusTrue()).thenReturn(List.of(book));
        when(access.currentEmail()).thenReturn("quantity-test@example.test");
        when(access.currentUserId()).thenReturn(22L);
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("test");when(ai.isConfigured()).thenReturn(true);
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("message");
        ChatBuiltinTools builtins=new ChatBuiltinTools(products,data,language,orders,new ChatPolicyService(language));
        builtins.setAccess(access);
        registry=new ChatToolRegistry(List.of(builtins.prepareCartChange(),builtins.prepareClearCart(),builtins.prepareCheckout(),builtins.getCart(),builtins.getMyOrders(),builtins.getOrderDetail(),builtins.getPolicy(),builtins.getProducts(),builtins.respond()));
        understanding=new ChatTurnUnderstanding(ai,mapper,registry);
        pipeline=new ChatStructuredPipeline(ai,mapper,understanding,registry,new ChatBookNarrator(ai,mapper,products,language),analytics,products,data);
    }
    ChatRequestDto request(String message) {
        ChatRequestDto request=new ChatRequestDto();request.setMessage(message);request.setSessionId("quantity-regression");request.setRequestId(UUID.randomUUID().toString());return request;
    }
    ChatTool.Result prepare(String message,Map<String,Object> args) {
        return registry.execute("prepareCartChange",mapper.valueToTree(args),new ChatTool.Context(request(message),state));
    }
    ChatRequestDto.CartItem cart(int quantity) {
        ChatRequestDto.CartItem item=new ChatRequestDto.CartItem();item.setProductId(57L);item.setQuantity(quantity);return item;
    }
    @Test void actual999RequestUsesCustomerQuantityInsteadOfPlannerDefaultOne() {
        var result=prepare("Thêm 999 cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",1));
        assertThat(result.status()).isEqualTo("NEEDS_INPUT");
        assertThat(result.answer()).contains("999","220","vượt tồn kho","chưa được thay đổi");
        assertThat(result.actions()).isEmpty();assertThat(state.focus).isNull();
        verifyNoInteractions(orders);
    }
    @Test void registryAllowsLargeProposalsSoHandlerCanExplainActualStock() {
        var result=prepare("Thêm Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",999));
        assertThat(result.answer()).contains("999","220");assertThat(result.actions()).isEmpty();
    }
    @Test void validExplicitCountOverridesPlannerAndStillRequiresConfirmation() {
        var result=prepare("Thêm 3 cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",1));
        assertThat(result.status()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getQuantity()).isEqualTo(3);
        assertThat(result.actions().get(0).isRequiresConfirmation()).isTrue();
        assertThat(result.answer()).contains("chưa được thay đổi");verifyNoInteractions(orders);
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void refusingRemovalAcknowledgesWithoutAssumingCartContentsOrCallingProvider(int copies) {
        ChatRequestDto request=request("Đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ nhé.");
        if(copies>0)request.setCartItems(List.of(cart(copies)));
        var response=pipeline.reply(request,state);
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getExecution()).containsEntry("tool","respond");
        assertThat(response.getAnswer()).isEqualTo("Mình hiểu rồi, mình không xóa sách hay thay đổi giỏ hàng của bạn.");
        assertThat(response.getActions()).isEmpty();assertThat(response.getProducts()).isEmpty();
        if(copies==0)assertThat(request.getCartItems()).isEmpty();
        else assertThat(request.getCartItems().get(0).getQuantity()).isEqualTo(copies);
        verify(ai,never()).completeStructured(anyString(),anyList(),anyString(),any());verifyNoInteractions(orders);
    }
    @Test void greetingPlanForExplicitCartRefusalStillAcknowledgesTheActualMessage() {
        var result=registry.execute("respond",mapper.valueToTree(Map.of("kind","greeting")),
            new ChatTool.Context(request("Đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ nhé."),state));
        assertThat(result.answer()).contains("không xóa","hay thay đổi giỏ hàng").doesNotContain("Chào bạn");
        assertThat(result.actions()).isEmpty();assertThat(result.status()).isEqualTo("COMPLETED");
        verifyNoInteractions(ai,orders);
    }
    @Test void affirmativeCartAdditionStillPreparesConfirmationWithoutChangingSnapshot() {
        ChatRequestDto request=request("Thêm 2 cuốn Đắc Nhân Tâm vào giỏ nhé.");request.setCartItems(List.of(cart(1)));
        var response=pipeline.reply(request,state);
        assertThat(response.isFallback()).isFalse();assertThat(response.getActions()).hasSize(1);
        var action=response.getActions().get(0);
        assertThat(action.getType()).isEqualTo("ADD_TO_CART");assertThat(action.getQuantity()).isEqualTo(2);
        assertThat(action.isRequiresConfirmation()).isTrue();assertThat(request.getCartItems().get(0).getQuantity()).isEqualTo(1);
        verify(ai,never()).completeStructured(anyString(),anyList(),anyString(),any());verifyNoInteractions(orders);
    }
    @ParameterizedTest @ValueSource(strings={
        "Đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ, thêm 2 cuốn khác vào nhé.",
        "Đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ và thêm sách khác vào giỏ.",
        "Nếu mình nói đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ thì sao?",
        "Đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ có được không?",
        "Tại sao đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ?",
        "Đừng xóa cuốn Đắc Nhân Tâm khỏi giỏ rồi mua ngay cuốn khác."
    })
    void compoundConditionalAndQuestionMessagesStillRequireInterpretation(String message) throws Exception {
        ObjectNode plan=mapper.createObjectNode().put("contextVersion",0).put("intent","PRODUCT_INFO").put("contextMode","current");
        plan.set("set",mapper.createObjectNode());plan.set("clear",mapper.createArrayNode());
        plan.put("evidence","current_message");plan.putNull("memoryNote");
        plan.set("calls",mapper.valueToTree(List.of(Map.of("tool","getCart","arguments",Map.of()))));
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenReturn(mapper.writeValueAsString(plan));
        var actual=understanding.understand(request(message),state,List.of());
        assertThat(actual.path("calls").get(0).path("tool").asText()).isEqualTo("getCart");
        verify(ai).completeStructured(anyString(),anyList(),anyString(),any());verifyNoInteractions(orders);
    }
    @Test void writtenCountIsPreserved() {
        var result=prepare("Thêm mười hai cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",1));
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getQuantity()).isEqualTo(12);
    }
    @ParameterizedTest @ValueSource(strings={"0","-2","2,5","1.5","999999999999999999999999999"})
    void invalidCountsNeverBecomeOne(String count) {
        var result=prepare("Thêm "+count+" cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",1));
        assertThat(result.status()).isEqualTo("NEEDS_INPUT");assertThat(result.actions()).isEmpty();
    }
    @Test void conflictingExplicitCountsAskForOneFinalCount() {
        var result=prepare("Thêm 2 cuốn hay 3 cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",1));
        assertThat(result.answer()).contains("nhiều số lượng","xác nhận");assertThat(result.actions()).isEmpty();
    }
    @Test void thousandsSeparatorDoesNotBecomeFractionOrOne() {
        var result=prepare("Thêm 1.000 cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm","quantity",1));
        assertThat(result.answer()).contains("1000","220");assertThat(result.actions()).isEmpty();
    }
    @Test void availableStockDoesNotBypassCartContractOf99() {
        var result=prepare("Thêm 150 cuốn Đắc Nhân Tâm vào giỏ.",Map.of("title","Đắc Nhân Tâm"));
        assertThat(result.answer()).contains("150","99");assertThat(result.actions()).isEmpty();
    }
    @Test void stockGuardIncludesAlreadyPresentCartCopies() {
        book.setQuantity(4);ChatRequestDto request=request("Thêm 3 cuốn Đắc Nhân Tâm vào giỏ.");request.setCartItems(List.of(cart(2)));
        var result=registry.execute("prepareCartChange",mapper.valueToTree(Map.of("title","Đắc Nhân Tâm","quantity",1)),new ChatTool.Context(request,state));
        assertThat(result.answer()).contains("giỏ hiện đã có 2","chỉ còn 4","vượt tồn kho");assertThat(result.actions()).isEmpty();
        assertThat(request.getCartItems().get(0).getQuantity()).isEqualTo(2);
    }
    @Test void explicitUpdateFinalCountDoesNotDefaultToOne() {
        ChatRequestDto request=request("Đổi số lượng Đắc Nhân Tâm thành 2 trong giỏ hàng.");request.setCartItems(List.of(cart(5)));
        var result=registry.execute("prepareCartChange",mapper.valueToTree(Map.of("operation","update","quantity",1)),new ChatTool.Context(request,state));
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getType()).isEqualTo("UPDATE_CART_QUANTITY");
        assertThat(result.actions().get(0).getQuantity()).isEqualTo(2);assertThat(request.getCartItems().get(0).getQuantity()).isEqualTo(5);
    }
    @Test void hugeIntegerCannotOverflowCartTotalIntoAcceptedAction() {
        ChatRequestDto request=request("Thêm 9223372036854775807 cuốn Đắc Nhân Tâm vào giỏ.");request.setCartItems(List.of(cart(2)));
        var result=registry.execute("prepareCartChange",mapper.valueToTree(Map.of("title","Đắc Nhân Tâm")),new ChatTool.Context(request,state));
        assertThat(result.actions()).isEmpty();assertThat(result.answer()).contains("220");
    }
    @Test void titleYearIsNotAQuantity() {
        assertThat(ChatCustomerActions.quantity(language.normalize("Mua ngay cuốn 1984 giúp tôi.")).explicit()).isFalse();
    }
    @Test void numericTitleCannotEraseAnEqualExplicitCount() {
        book.setName("1984");
        var result=prepare("Thêm 1984 cuốn 1984 vào giỏ.",Map.of("title",book.getName(),"quantity",1));
        assertThat(result.actions()).isEmpty();assertThat(result.answer()).contains("1984","220");
    }
    @Test void quantityAfterNumericTitleRemainsExplicit() {
        book.setName("1984");
        var result=prepare("Thêm cuốn 1984 vào giỏ số lượng 2.",Map.of("title",book.getName(),"quantity",1));
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getQuantity()).isEqualTo(2);
    }
    @Test void explicitUpdateFromOldCountUsesOnlyFinalCount() {
        ChatRequestDto request=request("Đổi số lượng Đắc Nhân Tâm từ 3 cuốn thành 2 cuốn trong giỏ.");request.setCartItems(List.of(cart(3)));
        var result=registry.execute("prepareCartChange",mapper.valueToTree(Map.of("operation","update","quantity",1)),new ChatTool.Context(request,state));
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getType()).isEqualTo("UPDATE_CART_QUANTITY");
        assertThat(result.actions().get(0).getQuantity()).isEqualTo(2);assertThat(request.getCartItems().get(0).getQuantity()).isEqualTo(3);
    }
    @Test void numberWithUnitInsideBookTitleDoesNotBecomeRequestedCount() {
        book.setName("Ba Cuốn Sách Về Cuộc Sống");
        var result=prepare("Thêm Ba Cuốn Sách Về Cuộc Sống vào giỏ.",Map.of("title",book.getName(),"quantity",3));
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getQuantity()).isEqualTo(1);
    }
    @Test void explicitCountOutsideNumericBookTitleIsPreserved() {
        book.setName("Ba Cuốn Sách Về Cuộc Sống");
        var result=prepare("Thêm 2 cuốn Ba Cuốn Sách Về Cuộc Sống vào giỏ.",Map.of("title",book.getName(),"quantity",1));
        assertThat(result.actions()).hasSize(1);assertThat(result.actions().get(0).getQuantity()).isEqualTo(2);
    }
    @Test void checkoutLargeCountGetsStockResponseBeforeAnyAction() {
        var result=registry.execute("prepareCheckout",mapper.valueToTree(Map.of("title","Đắc Nhân Tâm","quantity",1)),new ChatTool.Context(request("Mua ngay 999 cuốn Đắc Nhân Tâm."),state));
        assertThat(result.answer()).contains("999","220");assertThat(result.actions()).isEmpty();verifyNoInteractions(orders);
    }
    @Test void guestBuyNowGetsLoginEvenWhenProviderIsUnavailable() {
        when(access.currentEmail()).thenReturn(null);when(access.currentUserId()).thenReturn(null);
        var response=pipeline.reply(request("Mua ngay 1 cuốn Đắc Nhân Tâm giúp mình."),state);
        assertThat(response.isFallback()).isFalse();assertThat(response.getAnswer()).contains("đăng nhập","chưa có đơn hàng");
        assertThat(response.getActions()).extracting(ChatActionDto::getType).containsExactly("LOGIN");
        verify(ai,never()).completeStructured(anyString(),anyList(),anyString(),any());verifyNoInteractions(orders);
    }
    @Test void authenticatedBuyNowKeepsCustomerCountAndConfirmation() {
        var response=pipeline.reply(request("Mua ngay 2 cuốn Đắc Nhân Tâm giúp mình."),state);
        assertThat(response.isFallback()).isFalse();assertThat(response.getActions()).extracting(ChatActionDto::getType).containsExactly("BUY_NOW");
        assertThat(response.getActions().get(0).getQuantity()).isEqualTo(2);assertThat(response.getAnswer()).contains("Xác nhận đặt hàng");verifyNoInteractions(orders);
    }
    @Test void guestOrderStatusDoesNotCallOrderDatabaseOrProvider() {
        when(access.currentEmail()).thenReturn(null);when(access.currentUserId()).thenReturn(null);
        var response=pipeline.reply(request("Đơn hàng của tôi đến đâu rồi?"),state);
        assertThat(response.isFallback()).isFalse();assertThat(response.getActions()).extracting(ChatActionDto::getType).containsExactly("LOGIN");
        assertThat(response.getAnswer()).contains("đăng nhập");verifyNoInteractions(orders);verify(ai,never()).completeStructured(anyString(),anyList(),anyString(),any());
    }
    @Test void directPolicyReadKeepsBookConstraintsAndDoesNotNeedProvider() {
        state.toolSearch.put("query","trinh thám");state.toolSearch.put("maxPrice",100000);
        var response=pipeline.reply(request("Phí ship bao nhiêu và có COD không?"),state);
        assertThat(response.isFallback()).isFalse();assertThat(response.getExecution()).containsEntry("tool","getPolicy");
        assertThat(state.toolSearch).containsEntry("query","trinh thám").containsEntry("maxPrice",100000);
        verify(ai,never()).completeStructured(anyString(),anyList(),anyString(),any());
    }
    @Test void shoppingAdviceAndReturnOrderCommandsDoNotUseQuickPolicyOrPurchaseRoute() {
        assertThat(ChatCustomerActions.coreTool(language.normalize("Tìm sách làm quà cho bạn thích trinh thám."))).isEmpty();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Tôi muốn gửi yêu cầu đổi trả đơn #123."))).isEmpty();
        assertThat(ChatCustomerActions.coreTool(language.normalize("Chưa cần mua cuốn này."))).isEmpty();
    }
    @Test void readPlanCannotEraseReadingConstraintsWhenModelEmitsNewMode() throws Exception {
        ObjectNode plan=mapper.createObjectNode().put("contextVersion",0).put("intent","PRODUCT_INFO").put("contextMode","new");
        plan.set("set",mapper.valueToTree(Map.of("title","Đắc Nhân Tâm")));plan.set("clear",mapper.createArrayNode());plan.put("evidence","current_message");plan.putNull("memoryNote");
        plan.set("calls",mapper.valueToTree(List.of(Map.of("tool","getProducts","arguments",Map.of("title","Đắc Nhân Tâm","field","price")))));
        state.toolSearch.put("query","trinh thám");state.toolSearch.put("maxPrice",100000);
        var valid=understanding.validate(mapper.writeValueAsString(plan),request("Giá Đắc Nhân Tâm bao nhiêu?"),state,List.of());
        assertThat(valid.path("contextMode").asText()).isEqualTo("current");assertThat(valid.path("set").isEmpty()).isTrue();
        new ChatTurnContext(mapper).apply(valid,request("Giá Đắc Nhân Tâm bao nhiêu?"),state);
        assertThat(state.toolSearch).containsEntry("query","trinh thám").containsEntry("maxPrice",100000);
    }
}
