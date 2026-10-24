package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Semantic interpretation must happen before a catalog/cart read or a business tool. */
class ChatSemanticRoutingTest {
    final ObjectMapper mapper=new ObjectMapper();
    final AiGateway ai=mock(AiGateway.class);
    final ProductRepository products=mock(ProductRepository.class);
    final CartSyncService cart=mock(CartSyncService.class);
    final CurrentUserAccess access=mock(CurrentUserAccess.class);
    final ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    ChatStructuredPipeline pipeline;ChatTurnUnderstanding understanding;ObjectNode next;
    @BeforeEach void setup() throws Exception {
        var language=new ChatIntentService();var data=spy(new ChatRetrievalService(language));
        var tools=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        var registry=new ChatToolRegistry(List.of(tools.getProducts(),tools.respond(),tools.prepareCheckout(),tools.getCart()));
        understanding=new ChatTurnUnderstanding(ai,mapper,registry);
        pipeline=new ChatStructuredPipeline(ai,mapper,understanding,registry,new ChatBookNarrator(ai,mapper,products,language),mock(ChatAnalyticsService.class),products,data);
        pipeline.setAccess(access);pipeline.setSyncedCart(cart);when(access.currentEmail()).thenReturn("test@example.invalid");
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("same-model");
        when(products.findByStatusTrue()).thenReturn(List.of(new Product(57L,"Đắc Nhân Tâm","Dale Carnegie",20,86000.0,18,"cover","Sách giao tiếp và ứng xử.",LocalDate.now(),true,0,new Category(1L,"Kỹ năng"))));
        when(cart.chatItems()).thenReturn(List.of());
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenAnswer(call->{
            assertThat(call.<String>getArgument(0)).contains("Ý NGHĨA","reply");
            verifyNoInteractions(products,cart);
            return mapper.writeValueAsString(next);
        });
        next=mapper.createObjectNode().put("contextVersion",0).put("intent","SMALL_TALK").put("contextMode","current");
        next.set("set",mapper.createObjectNode());next.set("clear",mapper.createArrayNode());
        next.put("evidence",ChatTurnContext.CURRENT_MESSAGE);next.putNull("memoryNote");next.set("calls",mapper.createArrayNode());
        next.put("reply","Chào bạn! Mình giúp bạn tìm và so sánh sách, hiểu nội dung, cũng như hỗ trợ giỏ hàng và đơn của tài khoản.");
    }
    ChatResponseDto send(String message) {
        var request=new ChatRequestDto();request.setMessage(message);request.setSessionId("semantic-routing");request.setRequestId(UUID.randomUUID().toString());
        return pipeline.reply(request,state);
    }
    @ParameterizedTest @ValueSource(strings={"hi bạn có thể giúp gì cho tôi","Mình chưa rõ bạn hỗ trợ được những gì","Nếu muốn mua thì mình phải làm sao?"})
    void conversationUsesAiWithoutConsultingTheCatalogOrCart(String message) {
        var response=send(message);
        assertThat(response.getAnswer()).isEqualTo(next.path("reply").asText());
        assertThat(response.getExecution()).containsEntry("tool","conversation").containsEntry("dataAccess","none");
        assertThat(response.getProducts()).isEmpty();assertThat(response.getActions()).isEmpty();assertThat(response.getChips()).isEmpty();
        verifyNoInteractions(products,cart);
        verify(ai).completeStructured(anyString(),anyList(),anyString(),any());
    }
    @Test void explicitRecallRestoresTheServerOwnedFirstMenuAfterAiInterpretation() {
        state.topicMenus.add(new ArrayList<>(List.of("Trinh thám","Lịch sử")));
        state.topicMenus.add(new ArrayList<>(List.of("Khoa học","Lập trình")));
        state.topicSuggestions=new ArrayList<>(List.of("Khoa học","Lập trình"));
        state.toolSearch.put("maxPrice",100000);
        var response=send("Nhắc lại các chủ đề đầu tiên.");
        assertThat(response.getChips()).containsExactly("Trinh thám","Lịch sử");
        assertThat(state.topicSuggestions).containsExactly("Trinh thám","Lịch sử");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        assertThat(response.getExecution()).containsEntry("tool","respond").containsEntry("dataAccess","none");
        verify(ai).completeStructured(anyString(),anyList(),anyString(),any());verifyNoInteractions(products,cart);
    }
    @Test void quotedRecallTextForTranslationDoesNotOperateOnTheTopicMenus() {
        state.topicSuggestions=List.of("Khoa học","Lập trình");
        state.topicMenus.add(new ArrayList<>(state.topicSuggestions));
        next.put("intent","UNKNOWN").put("reply","Repeat the initial topics.");
        var response=send("Dịch câu 'Nhắc lại các chủ đề đầu tiên' sang tiếng Anh.");
        assertThat(response.getAnswer()).isEqualTo("Repeat the initial topics.");
        assertThat(response.getChips()).isEmpty();assertThat(state.topicSuggestions).containsExactly("Khoa học","Lập trình");
        verifyNoInteractions(products,cart);
    }
    @Test void pureVisibleComparisonUsesCanonicalReadArgumentsAfterAiInterpretation() throws Exception {
        state.shortlist=new ArrayList<>(List.of(57L,58L));state.displayed=new ArrayList<>(state.shortlist);
        next.put("intent","COMPARE_BOOKS").putNull("reply");
        next.set("calls",mapper.valueToTree(List.of(Map.of("tool","getProducts","arguments",
            Map.of("reference","focus","field","table","limit",2)))));
        var request=new ChatRequestDto();request.setMessage("So sánh hai cuốn vừa gợi ý giúp mình.");
        var plan=understanding.understand(request,state,List.of());
        assertThat(mapper.convertValue(plan.path("calls").get(0).path("arguments"),Map.class))
            .containsOnlyKeys("reference","field","count").containsEntry("reference","selection")
            .containsEntry("field","table").containsEntry("count",2);
        verify(ai).completeStructured(anyString(),anyList(),anyString(),any());verifyNoInteractions(products,cart);
    }
    @Test void translationOfProvidedTextDoesNotBecomeABookSearch() {
        next.put("intent","UNKNOWN").put("reply","Câu này nghĩa là: Bạn muốn tìm hiểu thêm điều gì về cuốn sách?");
        assertThat(send("Dịch câu 'What would you like to know about the book?' giúp mình").getAnswer()).contains("Bạn muốn tìm hiểu thêm");
        verifyNoInteractions(products,cart);
    }
    @Test void conversationalInterruptionPreservesThePendingBookAndTopicChoices() {
        state.focus=57L;state.shortlist=new ArrayList<>(List.of(57L));state.lastTask="PURCHASE";
        state.pendingQuestion="Bạn chọn địa chỉ nào?";state.toolSearch.put("maxPrice",100000);
        state.topicSuggestions=List.of("giao tiếp");state.bookReadContext.put("tool","getProducts");
        send("Cảm ơn, bạn nói chuyện dễ hiểu đấy");
        assertThat(state.focus).isEqualTo(57L);assertThat(state.shortlist).containsExactly(57L);
        assertThat(state.lastTask).isEqualTo("PURCHASE");assertThat(state.pendingQuestion).isEqualTo("Bạn chọn địa chỉ nào?");
        assertThat(state.topicSuggestions).containsExactly("giao tiếp");assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        assertThat(state.bookReadContext).containsEntry("tool","getProducts");verifyNoInteractions(products,cart);
    }
    @Test void stockReadCallsTheBackendOnlyAfterSemanticInterpretation() {
        next.put("intent","PRODUCT_INFO").putNull("reply");
        next.set("calls",mapper.valueToTree(List.of(Map.of("tool","getProducts","arguments",Map.of("title","Đắc Nhân Tâm","field","inventory")))));
        var response=send("Đắc Nhân Tâm còn hàng không?");
        assertThat(response.getAnswer()).contains("20");assertThat(response.getExecution()).containsEntry("tool","getProducts");
        verifyNoInteractions(cart);
        var order=inOrder(ai,products);order.verify(ai).completeStructured(anyString(),anyList(),anyString(),any());order.verify(products).findByStatusTrue();
    }
    @Test void aDirectReplyCannotAlsoPrepareAPurchase() {
        next.set("calls",mapper.valueToTree(List.of(Map.of("tool","prepareCheckout","arguments",Map.of("title","Đắc Nhân Tâm")))));
        var response=send("Bạn có thể giúp gì?");
        assertThat(response.getExecution()).containsEntry("status","FAILED");assertThat(state.contextVersion).isZero();
        verifyNoInteractions(products,cart);
    }
    @Test void incidentalDirectReplyMetadataCannotOverwriteReadingPreferences() {
        next.put("contextMode","new");next.with("set").set("topics",mapper.valueToTree(List.of("lịch sử")));
        state.toolSearch.put("maxPrice",100000);
        var response=send("Chào bạn");
        assertThat(response.getExecution()).containsEntry("status","COMPLETED").containsEntry("dataAccess","none");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000).doesNotContainKey("topics");
        verifyNoInteractions(products,cart);
    }
    @Test void authenticatedCartReadUsesTheServerSnapshotAfterInterpretation() {
        next.put("intent","UNKNOWN").putNull("reply");
        next.set("calls",mapper.valueToTree(List.of(Map.of("tool","getCart","arguments",Map.of()))));
        var owned=new ChatRequestDto.CartItem();owned.setProductId(57L);owned.setQuantity(2);
        when(cart.chatItems()).thenReturn(List.of(owned));
        var request=new ChatRequestDto();request.setMessage("Xem giỏ hàng");request.setSessionId("owned-cart");
        var forged=new ChatRequestDto.CartItem();forged.setProductId(57L);forged.setQuantity(99);request.setCartItems(List.of(forged));
        var response=pipeline.reply(request,state);
        assertThat(response.getAnswer()).contains("2 cuốn ×").doesNotContain("99 cuốn");
        verify(cart).chatItems();
    }
    @Test void aSimpleGreetingDuringAnOutageDoesNotReadTheCatalog() {
        doThrow(new IllegalStateException("provider timed out")).when(ai).completeStructured(anyString(),anyList(),anyString(),any());
        var response=send("Xin chào");
        assertThat(response.isFallback()).isTrue();assertThat(response.getAnswer()).contains("Chào bạn");
        assertThat(response.getExecution()).containsEntry("dataAccess","none");
        verifyNoInteractions(products,cart);
        verify(ai).beginStageBudget(15);verify(ai).endStageBudget();
    }
    @Test void readingIdeasDoNotRequireAStoreQueryMerelyBecauseTheyConcernBooks() {
        next.put("intent","DISCOVER_BOOKS").put("reply","Bạn có thể đọc về giao tiếp, lắng nghe hoặc cách tự tin khi trao đổi với đồng nghiệp. Đây là các hướng để cân nhắc, mình chưa tra sách cụ thể.");
        var response=send("Gợi mở vài hướng đọc cho mình, chưa cần tìm sách cụ thể");
        assertThat(response.getAnswer()).contains("lắng nghe");assertThat(response.getExecution()).containsEntry("tool","conversation");
        verifyNoInteractions(products,cart);
    }
}
