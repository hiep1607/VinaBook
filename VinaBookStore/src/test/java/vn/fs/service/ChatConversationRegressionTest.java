package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Real intake, local routing, tools and conversation memory exercised together. */
class ChatConversationRegressionTest {
    ChatIntentService language=new ChatIntentService();
    ChatRetrievalService data=new ChatRetrievalService(language);
    ProductRepository products=mock(ProductRepository.class);
    AiGateway ai=mock(AiGateway.class);
    ChatAnalyticsService analytics=mock(ChatAnalyticsService.class);
    ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
    CurrentUserAccess access=mock(CurrentUserAccess.class);
    ChatService service;
    ChatOrchestrator orchestrator;
    ChatToolRegistry registry;

    @BeforeEach void setup() {
        List<Product> catalog=new ArrayList<>();
        for(int i=1;i<=4;i++)catalog.add(book(i,"Vụ án "+i,"Truyện trinh thám về một vụ án và suy luận phá án.",50000+i*10000));
        for(int i=5;i<=8;i++)catalog.add(book(i,"Hành trình "+i,"Truyện phiêu lưu khám phá vùng đất bí ẩn.",50000+i*10000));
        catalog.add(book(9,"Luật Đất Đai","Văn bản pháp luật về đất đai.",40000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        ChatPolicyService policies=new ChatPolicyService(language);
        ChatBuiltinTools tools=new ChatBuiltinTools(products,data,language,orders,policies);
        registry=new ChatToolRegistry(List.of(tools.searchProducts(),tools.recommendBooks(),tools.getProducts(),tools.getPolicy(),tools.respond()));
        service=new ChatService(products,ai,language,data,policies,orders,analytics,access);
        service.setTools(registry);
        orchestrator=new ChatOrchestrator(ai,new ObjectMapper(),registry,analytics);
        orchestrator.setCatalog(products,data);
        when(ai.provider()).thenReturn("local");when(ai.modelUsed()).thenReturn("");
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("test");
    }
    Product book(long id,String title,String description,double price) {
        return new Product(id,title,"Tác giả",20,price,0,"cover.jpg",description,LocalDate.now(),true,0,new Category(1L,"Văn học"));
    }
    ChatRequestDto request(String message) {
        ChatRequestDto request=new ChatRequestDto();request.setSessionId("regression");request.setMessage(message);return request;
    }
    List<Long> ids(ChatResponseDto response) { return response.getProducts().stream().map(ChatProductDto::getId).toList(); }

    @Test void originalCustomerConversationRetainsSubjectAndFindsUnseenBooks() {
        var mood=service.reply(request("t đang buồn chán bạn có cuốn nào thú vị ko"));
        assertThat(mood.getIntent()).isEqualTo(ChatIntent.DISCOVER_BOOKS);
        assertThat(mood.getProducts()).hasSize(3).noneMatch(b->b.getId()==9L);
        assertThat(mood.getAnswer()).contains("đỡ chán").doesNotContain("Bạn đang hỏi cuốn sách nào", "chủ đề gì");
        var genres=service.reply(request("có cuốn nào về phiêu lưu hay trinh thám gì đó"));
        assertThat(genres.getProducts()).hasSize(3);
        assertThat(ids(genres)).anyMatch(id->id<=4).anyMatch(id->id>=5 && id<=8).doesNotContain(9L);
        var question=service.reply(request("?"));
        assertThat(question.getAnswer()).contains("nội dung").doesNotContain("chủ đề gì", "làm quà");
        var more=service.reply(request("tiếp"));
        assertThat(more.getProducts()).isNotEmpty();
        Set<Long> shown=new HashSet<>(ids(mood));shown.addAll(ids(genres));
        assertThat(ids(more)).doesNotContainAnyElementsOf(shown).doesNotContain(9L);
        assertThat(more.getAnswer()).doesNotContain("chủ đề gì", "làm quà");
    }
    @Test void casualOrPreservesPriceCeilingAcrossShortContinuation() {
        var first=service.reply(request("có cuốn nào về phiêu lưu hay trinh thám dưới 100k không"));
        assertThat(first.getProducts()).hasSize(3).allMatch(b->b.getPrice()<=100000);
        var more=service.reply(request("thêm nữa"));
        assertThat(more.getProducts()).isNotEmpty().allMatch(b->b.getPrice()<=100000);
        assertThat(ids(more)).doesNotContainAnyElementsOf(ids(first));
    }
    @Test void orAndAndRemainDifferentAndAdjectiveHayDoesNotBecomeOr() {
        var or=service.reply(request("Gợi ý sách trinh thám hay phiêu lưu"));
        assertThat(or.getProducts()).isNotEmpty();
        var and=service.reply(request("Gợi ý sách vừa trinh thám vừa phiêu lưu"));
        assertThat(and.getProducts()).isEmpty();
        assertThat(language.alternativeTopics("Gợi ý sách hay về trinh thám")).isFalse();
        assertThat(language.alternativeTopics("Sách Java hay Python")).isTrue();
    }
    @Test void entertainmentRequiresStoryEvidenceRatherThanAWordOrBroadShelf() {
        List<Product> catalog=new ArrayList<>(products.findByStatusTrue());
        catalog.add(book(10,"Chiêm Nghiệm","Câu chuyện và quan điểm về kiếp sống, nhân quả qua góc nhìn tâm linh.",30000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        var result=service.reply(request("t đang buồn chán bạn có cuốn nào thú vị ko, xem tất cả nhé"));
        assertThat(ids(result)).hasSize(8).doesNotContain(9L,10L);
        assertThat(language.discoveryQuery("Tìm sách về tài chính để giải trí")).contains("tài chính");
    }
    @Test void moreWithoutPreviousRecommendationDoesNotInventSubject() {
        var result=service.reply(request("tiếp"));
        assertThat(result.getProducts()).isEmpty();
    }
    @Test void shortContinuationAfterPolicyDoesNotSwitchBackToBooks() {
        service.reply(request("Gợi ý sách trinh thám"));
        service.reply(request("Phí giao hàng thế nào?"));
        var result=service.reply(request("tiếp"));
        assertThat(result.getProducts()).isEmpty();
    }
    @Test void questionAfterEmptySearchExplainsExistingRequestWithoutErasingIt() {
        service.reply(request("Gợi ý sách Python dưới 100k"));
        var question=service.reply(request("?"));
        assertThat(question.getAnswer()).contains("Python").doesNotContain("chủ đề gì", "làm quà");
        var next=service.reply(request("Còn cuốn khác không?"));
        assertThat(next.getProducts()).isEmpty();
        assertThat(next.getAnswer()).doesNotContain("chủ đề gì");
    }
    @Test void emptyMoreExplanationDescribesExhaustedChoicesRatherThanMissingBookEvidence() {
        service.reply(request("Gợi ý tất cả sách trinh thám"));
        assertThat(service.reply(request("tiếp")).getProducts()).isEmpty();
        var why=service.reply(request("?"));
        assertThat(why.getAnswer()).contains("đã được giới thiệu", "tránh lặp lại", "trinh thám")
            .doesNotContain("chủ đề gì", "chưa đủ để chứng minh", "chưa cho mình đủ căn cứ");
        var state=new ChatDialogueMemory.State();state.toolSearch.put("query","trinh thám");state.lastTask="POLICY";
        assertThat(ChatConversationSupport.repairQuestion("?",state,language)).isFalse();
    }
    @Test void bookReferenceAndComparisonStillReferToDisplayedBooks() {
        service.reply(request("Gợi ý sách trinh thám"));
        var detail=service.reply(request("Nội dung cuốn thứ hai"));
        assertThat(ids(detail)).containsExactly(2L);
        var compare=service.reply(request("Trong các cuốn này cuốn nào rẻ hơn?"));
        assertThat(compare.getProducts()).isNotEmpty();
        assertThat(compare.getIntent()).isEqualTo(ChatIntent.COMPARE_BOOKS);
    }
    @Test void plannerPathUnderstandsCasualSuggestionsAndShortContinuationsWithoutModelCall() {
        var state=new ChatDialogueMemory.State();
        var first=orchestrator.reply(request("có cuốn nào về phiêu lưu hay trinh thám gì đó"),state);
        assertThat(first.isFallback()).isFalse();assertThat(first.getProducts()).hasSize(3);
        var more=orchestrator.reply(request("tiếp"),state);
        assertThat(more.isFallback()).isFalse();assertThat(more.getProducts()).isNotEmpty();
        assertThat(ids(more)).doesNotContainAnyElementsOf(ids(first));
        var why=orchestrator.reply(request("?"),state);
        assertThat(why.isFallback()).isFalse();assertThat(why.getAnswer()).doesNotContain("chủ đề gì");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void plannerPathUsesEntertainmentPurposeWithoutInventingGentlePreference() {
        var state=new ChatDialogueMemory.State();
        var result=orchestrator.reply(request("t đang buồn chán bạn có cuốn nào thú vị ko"),state);
        assertThat(result.isFallback()).isFalse();assertThat(result.getProducts()).hasSize(3).noneMatch(b->b.getId()==9L);
        assertThat(state.toolSearch).containsEntry("query","truyện").doesNotContainKey("readingNeeds");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
}
