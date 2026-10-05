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
    @Test void returnAbookAndMissingPagesThenTenDaysKeepTheReason() {
        assertThat(turn("Tôi muốn trả cuốn sách vừa mua.").getAnswer()).contains("Bạn muốn trả vì");
        assertThat(turn("Sách bị thiếu mấy trang ở giữa.").getAnswer()).contains("thiếu trang","1. Mở").doesNotContain("Bạn muốn trả vì");
        assertThat(turn("Tôi nhận được khoảng 10 ngày rồi, giờ phải làm sao?").getAnswer())
            .contains("10 ngày","vượt mốc 7 ngày","thiếu trang","Liên hệ").doesNotContain("Bạn muốn trả vì");
    }
    @Test void abbreviatedChangedMindReturnAndRefundRememberTheReason() {
        assertThat(turn("Tôi mua một cuốn rồi nhưng đọc thấy không hợp, muốn trả.").getAnswer())
            .contains("không còn hứng thú").doesNotContain("Bạn muốn trả vì");
        turn("Sách không bị lỗi gì, tôi chỉ không thích nội dung thôi.");
        assertThat(turn("Vậy tôi có được hoàn tiền không, và cần liên hệ ở đâu để hỏi?").getAnswer())
            .contains("không thích nội dung","cửa hàng xác nhận").doesNotContain("Bạn muốn trả vì");
    }
    @Test void demonstrativeSoDoDoesNotNameTheNovelButTheRealTitleStillWorks() {
        ChatRetrievalService data=new ChatRetrievalService(language);
        var query=new ChatCatalogQuery(language,data);
        var novel=book(3,"Số Đỏ","Tiểu thuyết trào phúng.");
        assertThat(query.named("Trong số đó, cuốn nào đúng chủ đề?",List.of(novel))).isEmpty();
        assertThat(query.named("Sách Số Đỏ của ai?",List.of(novel))).containsExactly(novel);
        assertThat(query.named("Tim sach So Do",List.of(novel))).containsExactly(novel);
        state.emptySearch=true;state.toolSearch.put("query","quản lý tiền cá nhân");
        assertThat(turn("Trong số đó, cuốn nào đúng chủ đề, cuốn nào chỉ gần giống?").getAnswer())
            .contains("chưa có danh sách","quản lý tiền cá nhân").doesNotContain("Số Đỏ");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void readingStyleRefinementDoesNotLoseSearchOrFallBackToGenericClarification() {
        selection();
        var response=turn("Tôi muốn đọc dễ hiểu, không muốn kiểu giáo trình tâm lý.");
        assertThat(state.toolSearch).containsEntry("query","python");
        assertThat(response.getIntent()).isEqualTo(ChatIntent.DISCOVER_BOOKS);
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).contains(1L,2L);
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void exactOnlyRefinementPreservesTheOriginalGoalAfterAnEmptySearch() {
        state.emptySearch=true;state.toolSearch.put("query","quản lý tiền cá nhân");
        turn("Vậy chỉ giữ những cuốn đúng yêu cầu thôi, không cần đủ 6.");
        assertThat(state.toolSearch).containsEntry("query","quản lý tiền cá nhân").containsEntry("match","exact_only");
    }
    @Test void exclusionsSurviveTopicSwitchAndMaximumCountIsNotAQuota() throws Exception {
        state.toolSearch.put("excludedQuery","Python Nhập Môn");
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\",\"scope\":\"new\"}"),new ChatTool.Context(request("Giới thiệu tối đa 5 cuốn Python"),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(result.answer()).doesNotContain("chưa đủ","Bạn muốn 5");
        assertThat(state.toolSearch).containsEntry("excludedQuery","Python Nhập Môn");
    }
    @Test void beginnerFictionDoesNotNeedTechnicalIntroductoryKeywords() throws Exception {
        Product fiction=book(3,"Truyện Mùa Hè","Tập truyện ngắn về tình bạn.");
        fiction.setCategory(new Category(3L,"Sách Văn học nghệ thuật"));
        when(products.findByStatusTrue()).thenReturn(List.of(fiction));
        var result=registry.execute("recommendBooks",mapper.readTree("{\"query\":\"văn học\",\"level\":\"beginner\"}"),new ChatTool.Context(request("Gợi ý văn học cho người ít đọc"),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(result.answer()).contains("chưa xác nhận mức đọc");
    }
    @Test void worryRecommendationsExcludeProgrammingEvenWhenItMentionsHabits() {
        Product coding=book(3,"Pragmatic Programmer","Thói quen nghề nghiệp của kỹ sư phần mềm và thợ code.");
        Product worry=book(4,"Quẳng Gánh Lo","Phương pháp vượt qua lo âu và tìm lại bình an.");
        var query=new ChatCatalogQuery(language,new ChatRetrievalService(language));
        var criteria=new ChatSearchCriteriaDto(null,null,"","","",List.of());
        assertThat(query.search("Tôi hay nghĩ quá nhiều, lo cả ngày",criteria,List.of(coding,worry))).containsExactly(worry);
        assertThat(query.nearby("lo âu",criteria,List.of(coding),List.of(),Set.of())).isEmpty();
    }
    @Test void gradeIsMandatoryAndWrongFictionMetadataDoesNotTurnAdviceIntoANovel() {
        var filter=new ChatExplicitFilters(language);
        assertThat(filter.matches("toán lớp 7",book(3,"Toán lớp 5","Bài tập toán cho lớp 5"))).isFalse();
        assertThat(filter.matches("toán lớp 7",book(4,"Toán lớp 7","Bài tập toán cho lớp 7"))).isTrue();
        Product advice=book(5,"Đắc Nhân Tâm","Nghệ thuật đối nhân xử thế và giao tiếp.");
        advice.setCategory(new Category(6L,"Sách Truyện, tiểu thuyết"));
        assertThat(filter.matches("truyện thư giãn",advice)).isFalse();
    }
    @Test void budgetOnlyFollowupPreservesFictionEvenWhenPlannerPreviouslyPutItInPreference() throws Exception {
        Product fiction=book(3,"Truyện Mùa Hè","Truyện ngắn về tình bạn.");
        fiction.setCategory(new Category(3L,"Sách Văn học nghệ thuật"));
        when(products.findByStatusTrue()).thenReturn(List.of(fiction,catalog.get(0)));
        registry.execute("recommendBooks",mapper.readTree("{\"query\":\"truyện nhẹ nhàng\",\"preferredQuery\":\"truyện nhẹ nhàng\"}"),new ChatTool.Context(request("Tôi thích truyện nhẹ nhàng"),state));
        assertThat(state.toolSearch).containsEntry("query","truyện");
        assertThat(turn("Khoảng 150 nghìn trở xuống, bạn chọn giúp tôi 2 cuốn nhé.").getProducts())
            .extracting(ChatProductDto::getId).containsExactly(3L);
    }
    @Test void explicitTopicSwitchWinsOverStillUnderBudgetAndAnIncorrectContinueScope() throws Exception {
        selection();state.toolSearch.put("maxPrice",100000L);state.toolSearch.put("excludedQuery","Đắc Nhân Tâm");
        Product fiction=book(3,"Truyện Mùa Hè","Truyện ngắn về tình bạn.");
        fiction.setCategory(new Category(6L,"Sách Truyện, tiểu thuyết"));
        when(products.findByStatusTrue()).thenReturn(List.of(fiction,catalog.get(0)));
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"truyện\",\"scope\":\"continue\"}"),new ChatTool.Context(request("Thôi đổi sang truyện để thư giãn nhé, vẫn dưới 100 nghìn."),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(state.toolSearch).containsEntry("maxPrice",100000L).containsEntry("excludedQuery","Đắc Nhân Tâm");
    }
    @Test void gradeInUserMessageCannotBeOmittedByPlannerOrRemovedByAReferenceFollowup() throws Exception {
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"toán\"}"),new ChatTool.Context(request("Tôi muốn tìm sách toán cho học sinh lớp 7."),state));
        assertThat(result.products()).isEmpty();assertThat(state.toolSearch).containsEntry("grade",7);
        assertThat(turn("Nếu không có đúng lớp thì nói giúp tôi, đừng chọn sách tiểu học hay đại học để thay nhé.").getProducts()).isEmpty();
        assertThat(state.toolSearch).containsEntry("grade",7);
    }
    @Test void ChildReaderRequirementSurvivesGenericPlannerQueryAndPriceFollowup() throws Exception {
        Product child=book(3,"Truyện Cho Bé","Truyện thiếu nhi về tình bạn.");
        child.setCategory(new Category(8L,"Sách thiếu nhi"));
        Product adult=book(4,"Truyện Kiều","Tác phẩm văn học cổ điển.");
        adult.setCategory(new Category(3L,"Sách Văn học nghệ thuật"));
        when(products.findByStatusTrue()).thenReturn(List.of(child,adult));
        var result=registry.execute("recommendBooks",mapper.readTree("{\"query\":\"truyện\"}"),new ChatTool.Context(request("Tôi muốn tìm truyện cho bé 8 tuổi, dễ đọc, dưới 100 nghìn."),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(state.toolSearch).containsEntry("readerGroup","children");
        assertThat(turn("Tăng ngân sách lên 150 nghìn, vẫn giữ điều kiện cũ").getProducts()).extracting(ChatProductDto::getId).containsExactly(3L);
        turn("Cửa hàng tính phí giao hàng thế nào?");
        assertThat(turn("Quay lại những cuốn vừa rồi, cuốn nào hợp với bé nhất?").getAnswer()).contains("chưa xác nhận độ tuổi đọc");
    }
    private void selection(){state.shortlist=new ArrayList<>(List.of(1L,2L));state.displayed=new ArrayList<>(state.shortlist);state.toolSearch.put("query","Python");state.lastTask="DISCOVER_BOOKS";}
    private ChatResponseDto turn(String message){return chat.reply(request(message),state);}
    private ChatRequestDto request(String message){ChatRequestDto r=new ChatRequestDto();r.setSessionId("test-conversation-refinement");r.setMessage(message);return r;}
    private Product book(long id,String name,String description){return new Product(id,name,"Tác giả",10,90000d,0,"",description,LocalDate.now(),true,10,new Category(1L,"Giáo trình"));}
}
