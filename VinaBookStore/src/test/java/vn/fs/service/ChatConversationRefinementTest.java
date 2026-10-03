package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

class ChatConversationRefinementTest {
    ProductRepository products=mock(ProductRepository.class);
    AiGateway ai=mock(AiGateway.class);
    ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
    ChatAnalyticsService analytics=mock(ChatAnalyticsService.class);
    ChatIntentService language=new ChatIntentService();
    ObjectMapper mapper=new ObjectMapper();
    ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    ChatBuiltinTools builtins;
    ChatToolRegistry registry;
    ChatOrchestrator chat;
    List<Product> catalog;

    @BeforeEach void setup() {
        catalog=List.of(book(1,"Python Nhập Môn","Học lập trình Python cơ bản cho người mới."),
            book(2,"Python Thực Hành","Thực hành lập trình Python với bài tập và ví dụ."));
        when(products.findByStatusTrue()).thenReturn(catalog);
        ChatRetrievalService data=new ChatRetrievalService(language);
        RateRepository rates=mock(RateRepository.class);when(rates.averageRatings()).thenReturn(List.of());data.setRates(rates);
        builtins=new ChatBuiltinTools(products,data,language,orders,new ChatPolicyService(language));
        registry=new ChatToolRegistry(List.of(builtins.searchProducts(),builtins.recommendBooks(),builtins.getProducts(),builtins.getPolicy(),builtins.respond(),builtins.getMyOrders(),builtins.getOrderDetail(),builtins.getCart()));
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("fixture");
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("test");
        chat=new ChatOrchestrator(ai,mapper,registry,analytics);chat.setCatalog(products,data);
    }
    @Test void genderWithoutReadingPreferencesDoesNotRecommendAnArbitraryCatalog() {
        var response=turn("Có cuốn sách nào các bạn nữ đặc biệt ưa thích không?");
        assertThat(response.getProducts()).isEmpty();
        assertThat(response.getAnswer()).contains("giới tính","thống kê","chủ đề");
        assertThat(response.getExecution()).containsEntry("status","NEEDS_INPUT");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void knownSubjectDoesNotTriggerGenderClarification() {
        assertThat(ChatConversationSupport.audienceOnly("Gợi ý sách lập trình cho bạn gái",language)).isFalse();
        assertThat(ChatConversationSupport.audienceOnly("Tặng bạn trai một cuốn trinh thám",language)).isFalse();
    }
    @Test void pluralWhyExplainsEntireShortlistDespiteAFocusedBook() {
        selection();state.focus=2L;
        var response=turn("Tại sao lại là những cuốn đó?");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(response.getAnswer()).contains("Python Nhập Môn","Python Thực Hành").doesNotContain("Bạn đang nói đến cuốn sách nào");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void deeperExplanationAddsApprovedReaderFactsAndDoesNotRepeatTheSameAnswer() {
        selection();BookKnowledgeService knowledge=mock(BookKnowledgeService.class);builtins.setKnowledge(knowledge);
        when(knowledge.approvedReadingFacts(any())).thenAnswer(call->Map.of("summary",((Product)call.getArgument(0)).getDescription(),"audience","Người mới học lập trình","learningGoal","Thực hành giải quyết bài tập"));
        String first=turn("Tại sao lại là những cuốn đó?").getAnswer();
        String second=turn("Nói rõ hơn về những cuốn đó").getAnswer();
        assertThat(second).isNotEqualTo(first).contains("Người mới học lập trình","Thực hành giải quyết bài tập");
        assertThat(state.explanationRound).isEqualTo(2);
        assertThat(turn("Giải thích sâu hơn về những cuốn đó").getAnswer()).contains("điểm còn thiếu thông tin");
    }
    @Test void oldGenderBasedSuggestionIsAcknowledgedInsteadOfRationalized() {
        selection();state.toolSearch.put("query","sách các bạn nữ ưa thích");
        assertThat(turn("Tại sao lại là những cuốn đó?").getAnswer()).contains("Gợi ý trước chưa có đủ căn cứ");
    }
    @Test void explanationAfterEmptySearchKeepsTheGoalWithoutRepeatingAClarification() {
        state.emptySearch=true;state.toolSearch.put("query","Rèn thói quen làm việc");
        String first=turn("Vì sao chọn những cuốn đó?").getAnswer();
        String second=turn("Nói kỹ hơn về những cuốn đó").getAnswer();
        assertThat(first).contains("chưa có danh sách","Rèn thói quen làm việc");
        assertThat(second).isNotEqualTo(first).contains("Rèn thói quen làm việc").doesNotContain("Bạn đang nói đến cuốn sách nào");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void returnsAskForMissingReasonThenGuideWithoutAskingAgain() {
        var first=turn("Tôi muốn trả hàng");
        assertThat(first.getAnswer()).contains("Bạn muốn trả vì").doesNotContain("1. Mở");
        assertThat(first.getExecution()).containsEntry("status","NEEDS_INPUT");
        var next=turn("Sách bị rách");
        assertThat(next.getAnswer()).contains("rách","1. Mở mục Đơn hàng","2. Gửi yêu cầu","3. Giữ sách").doesNotContain("Bạn muốn trả vì");
        assertThat(next.getExecution()).containsEntry("status","COMPLETED");
    }
    @Test void changedMindAlreadyInMessageIsUsedAndAcceptanceIsNotPromised() {
        var response=turn("Tôi vừa mua 1 cuốn sách trên cửa hàng bạn nhưng tôi chán rồi, muốn trả hàng");
        assertThat(response.getAnswer()).contains("không còn hứng thú","chưa có điều khoản","Liên hệ","cửa hàng xác nhận").doesNotContain("Bạn muốn trả vì");
    }
    @Test void customReasonAlreadySuppliedIsNotAskedAgain() {
        assertThat(turn("Tôi muốn trả sách vì được tặng cuốn giống hệt").getAnswer()).contains("duoc tang cuon giong het","1. Mở").doesNotContain("Bạn muốn trả vì");
    }
    @Test void generalReturnsPolicyDoesNotStartAPersonalReturnRequest() {
        var response=turn("Chính sách đổi trả là gì?");
        assertThat(response.getAnswer()).contains("7 ngày").doesNotContain("Bạn muốn trả vì");
        assertThat(state.returnActive).isFalse();
    }
    @Test void shippingInterruptionPreservesThePendingReturnReason() {
        turn("Tôi muốn trả hàng");
        assertThat(turn("Phí giao hàng là bao nhiêu?").getAnswer()).contains("20.000đ");
        assertThat(turn("Giao sai cuốn").getAnswer()).contains("giao sai cuốn","2. Gửi yêu cầu");
    }
    @Test void returnFollowupAnswersTheNextStepInsteadOfRepeatingTheInitialInstructions() {
        String first=turn("Tôi muốn trả hàng vì sách bị rách").getAnswer();
        String second=turn("Tôi gửi lại sách về đâu?").getAnswer();
        assertThat(second).isNotEqualTo(first).contains("địa chỉ nhận lại","rách","phí gửi lại");
        assertThat(turn("Bao lâu được hoàn tiền?").getAnswer()).contains("thời gian hoặc cách hoàn tiền","rách").doesNotContain("1. Mở");
    }
    @Test void purchasedBooksUseAccountOrdersEvenIfCustomerCallsItTheCart() {
        when(orders.latestOrders()).thenReturn("Bạn cần đăng nhập để mình tra cứu đơn hàng.");
        var response=turn("Tìm trong giỏ hàng của tôi những cuốn sách đã mua");
        assertThat(response.getAnswer()).contains("Sách đã mua nằm trong mục Đơn hàng","đăng nhập").doesNotContain("Giỏ hàng của bạn đang trống");
        assertThat(response.getIntent()).isEqualTo(ChatIntent.ORDER_SUPPORT);
        verify(orders).latestOrders();
    }
    @Test void switchingBackToBookDiscoveryDoesNotTreatLaterSmallTalkAsReturnReason() {
        turn("Tôi muốn trả hàng");
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn("{\"tool\":\"searchProducts\",\"arguments\":{\"query\":\"Python\"}}");
        turn("Tìm sách Python");
        assertThat(state.returnActive).isFalse();
    }
    @Test void narrationCanBeLongerAndReceivesPreviousExplanations() throws Exception {
        selection();state.recentDialogue.add(Map.of("user","Vì sao chọn cuốn này?","assistant","Giải thích trước về mục tiêu học."));
        ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        var base=registry.execute("getProducts",mapper.readTree("{\"reference\":\"selection\",\"field\":\"explanation\"}"),new ChatTool.Context(request("Tại sao chọn những cuốn đó?"),state));
        String reason="Nội dung bắt đầu từ nền tảng Python, nên liên quan đến mục tiêu học lập trình mà bạn đang cân nhắc. Khi chọn sách, bạn có thể dùng trọng tâm cơ bản này để so với nhu cầu hiện tại của mình. Nếu bạn muốn một cuốn đặt nền tảng trước khi chuyển sang bài tập, đây là căn cứ nội dung để đưa vào danh sách lựa chọn. Mô tả chưa xác nhận các chi tiết ngoài phần nền tảng, nên những điểm đó cần được kiểm tra thêm trước khi quyết định.";
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(mapper.writeValueAsString(Map.of("reasons",List.of(Map.of("productId",1,"evidenceField","summary","evidence",catalog.get(0).getDescription(),"reason",reason)))));
        var narrated=narrator.explain(base,"Tại sao chọn những cuốn đó?",state);
        assertThat(narrated.answer()).contains(reason);
        verify(ai).complete(anyString(),anyList(),argThat(input->input.contains("Giải thích trước về mục tiêu học") && input.contains("explanationRound")));
        assertThat(narrated.products()).isSameAs(base.products());
    }
    @Test void narratorDoesNotReuseTheSameExplanationVerbatim() throws Exception {
        selection();ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        var base=registry.execute("getProducts",mapper.readTree("{\"reference\":\"selection\",\"field\":\"explanation\"}"),new ChatTool.Context(request("Vì sao chọn những cuốn đó?"),state));
        String previous="Phù hợp với mục tiêu học Python từ nền tảng cơ bản.";
        state.recentDialogue.add(Map.of("assistant",previous));
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(mapper.writeValueAsString(Map.of("reasons",List.of(Map.of("productId",1,"evidenceField","summary","evidence",catalog.get(0).getDescription(),"reason",previous)))));
        assertThat(narrator.explain(base,"Vì sao chọn những cuốn đó?",state).answer()).doesNotContain(previous);
    }
    @Test void narratorCannotClaimToKnowTheCustomersCurriculum() throws Exception {
        selection();ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        var base=registry.execute("getProducts",mapper.readTree("{\"reference\":\"selection\"}"),new ChatTool.Context(request("Gợi ý sách cho sinh viên"),state));
        String intro="Mình gửi bạn các tài liệu rất sát với chương trình học của bạn.";
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(mapper.writeValueAsString(Map.of("intro",intro,"reasons",List.of())));
        assertThat(narrator.present(base,"Gợi ý sách cho sinh viên",state).answer()).doesNotContain(intro);
    }
    private void selection(){state.shortlist=new ArrayList<>(List.of(1L,2L));state.displayed=new ArrayList<>(state.shortlist);state.toolSearch.put("query","Python");state.lastTask="DISCOVER_BOOKS";}
    private ChatResponseDto turn(String message){return chat.reply(request(message),state);}
    private ChatRequestDto request(String message){ChatRequestDto r=new ChatRequestDto();r.setSessionId("test-conversation-refinement");r.setMessage(message);return r;}
    private Product book(long id,String name,String description){return new Product(id,name,"Tác giả",10,90000d,0,"",description,LocalDate.now(),true,10,new Category(1L,"Giáo trình"));}
}
