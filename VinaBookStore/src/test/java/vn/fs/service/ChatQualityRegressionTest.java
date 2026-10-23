package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Real current pipeline and tools; unavailable AI must not erase simple known intent. */
class ChatQualityRegressionTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ChatIntentService language=new ChatIntentService();
    final ChatRetrievalService data=new ChatRetrievalService(language);
    final ProductRepository products=mock(ProductRepository.class);
    final AiGateway ai=mock(AiGateway.class);
    final ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    ChatBuiltinTools tools;ChatStructuredPipeline pipeline;
    List<Product> catalog;
    @BeforeEach void setup() {
        catalog=List.of(
            book(57,"Đắc Nhân Tâm","Dale Carnegie bàn về giao tiếp và ứng xử. Nguyên tắc tránh chỉ trích và lên án.",70520,"Kỹ năng sống"),
            book(1,"Truyện A","Tiểu thuyết hiện thực về gia đình và cuộc sống.",80000,"Truyện, tiểu thuyết"),
            book(2,"Truyện B","Tiểu thuyết phiêu lưu về hành trình của một người trẻ.",60000,"Truyện, tiểu thuyết"),
            book(3,"Conan","Truyện tranh trinh thám về thám tử phá án.",25000,"Truyện, tiểu thuyết"),
            book(4,"Luật doanh nghiệp","Văn bản pháp luật dành cho doanh nghiệp.",100000,"Pháp luật"));
        when(products.findByStatusTrue()).thenReturn(catalog);
        when(ai.isConfigured()).thenReturn(true);when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("same-model");
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenThrow(new IllegalStateException("HTTP 503"));
        var access=mock(CurrentUserAccess.class);
        tools=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));tools.setAccess(access);
        var registry=new ChatToolRegistry(List.of(tools.searchProducts(),tools.recommendBooks(),tools.getProducts(),tools.respond(),tools.prepareCheckout(),tools.getPolicy()));
        var understanding=new ChatTurnUnderstanding(ai,mapper,registry);
        pipeline=new ChatStructuredPipeline(ai,mapper,understanding,registry,new ChatBookNarrator(ai,mapper,products,language),mock(ChatAnalyticsService.class),products,data);
    }
    Product book(long id,String name,String summary,double price,String category) {
        return new Product(id,name,"Tác giả",20,price,0,"cover.jpg",summary,LocalDate.now(),true,0,new Category(id,category));
    }
    ChatRequestDto request(String message) {var r=new ChatRequestDto();r.setMessage(message);r.setRequestId(UUID.randomUUID().toString());r.setSessionId("quality-test");return r;}
    ChatResponseDto send(String message){return pipeline.reply(request(message),state);}

    @ParameterizedTest @ValueSource(strings={"Đắc Nhân Tâm nói về những nội dung gì?","Đắc Nhân Tâm bao gồm các nội dung chính nào?","Đắc Nhân Tâm nói về chủ đề gì?"})
    void ordinaryContentQuestionUsesTheSummary(String question) {
        var response=send(question);
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L);
        assertThat(response.getAnswer()).contains("giao tiếp").doesNotContain("có đề cập đến nội dung đó");
    }
    @Test void namedBookWinsOverAnEmptyOldSearch() {
        state.emptySearch=true;state.lastTask="DISCOVER_BOOKS";state.toolSearch.put("query","chăm sóc cây cảnh");
        var response=send("Đắc Nhân Tâm nói về những nội dung gì?");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L);
        assertThat(response.getAnswer()).contains("giao tiếp").doesNotContain("cây cảnh","Lượt trước mình chưa tìm");
        assertThat(state.emptySearch).isFalse();
    }
    @Test void guestCanChangeQuantityWithoutReinterpretingThePurchase() {
        assertThat(send("Mua 2 cuốn Đắc Nhân Tâm").getActions().get(0).getQuantity()).isEqualTo(2);
        var response=send("Đổi thành 3 cuốn nhé");
        assertThat(response.getExecution()).containsEntry("tool","prepareCheckout").containsEntry("status","AWAITING_INPUT");
        assertThat(response.getActions()).singleElement().satisfies(action->{assertThat(action.getType()).isEqualTo("LOGIN");assertThat(action.getProductId()).isEqualTo(57L);assertThat(action.getQuantity()).isEqualTo(3);});
        assertThat(state.focus).isEqualTo(57L);
        verify(ai,times(2)).completeStructured(anyString(),anyList(),anyString(),any());
    }
    @Test void quantityValidationAndPurchaseRefusalRemainEffective() {
        send("Mua 2 cuốn Đắc Nhân Tâm");
        assertThat(send("Đổi thành 0 cuốn").getAnswer()).contains("từ 1 cuốn");
        var retry=send("Đổi thành 3 cuốn");
        assertThat(retry.getActions()).singleElement().satisfies(action->{
            assertThat(action.getType()).isEqualTo("LOGIN");assertThat(action.getQuantity()).isEqualTo(3);
        });
        var response=send("Mình chưa muốn mua, giải thích rõ nội dung cuốn này");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L);
        assertThat(response.getExecution()).containsEntry("tool","getProducts");
        assertThat(response.getActions()).noneMatch(a->"BUY_NOW".equals(a.getType()) || "LOGIN".equals(a.getType()));
    }
    @Test void simpleExclusionsAndPerBookBudgetSurviveProviderFailure() {
        var response=send("Tìm 2 cuốn tiểu thuyết dưới 100k mỗi cuốn, không lấy truyện tranh");
        assertThat(response.getProducts()).as("answer=%s filters=%s execution=%s",response.getAnswer(),state.toolSearch,response.getExecution())
            .hasSize(2).allMatch(p->p.getPrice()<100000 && p.getId()!=3L);
        assertThat(response.getExecution()).containsEntry("recovery","basic");
        assertThat(state.toolSearch).containsKey("excludedQuery");
    }
    @Test void choosingAnOrdinalTopicKeepsTheCombinedBudget() {
        state.topicSuggestions=List.of("tiểu thuyết");state.topicMenus.add(List.of("tiểu thuyết"));
        var response=send("Chọn hướng đầu tiên, 2 cuốn tổng tiền sách không quá 160k");
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getProducts().stream().mapToLong(ChatProductDto::getPrice).sum()).isLessThanOrEqualTo(160000);
        assertThat(state.toolSearch).containsEntry("totalBudget",160000).containsEntry("limit",2);
    }
    @Test void explicitCommunicationNeedCannotBeReplacedByUnrelatedGiftTopics() {
        state.structuredTurnActive=true;
        var r=request("Mình tặng sách cho bạn mới đi làm, hay ngại giao tiếp. Bạn gợi ý vài hướng đọc nhé.");
        var args=mapper.createObjectNode().put("kind","clarify").put("topic","gift")
            .put("suggestionSource","current").put("suggestionContext","Mình tặng sách cho bạn mới đi làm")
            .put("suggestedTopics","truyện tuổi thơ|pháp luật doanh nghiệp");
        var result=tools.respond().execute(args,new ChatTool.Context(r,state));
        assertThat(result.chips()).contains("giao tiếp").doesNotContain("truyện tuổi thơ","pháp luật doanh nghiệp");
        assertThat(state.toolSearch).doesNotContainKey("topics");
    }
    @Test void nonexistentTitleDoesNotTurnIntoATopicMenu() {
        var response=send("Có sách Hành Tinh Kẹo Mây Của Gấu Xanh không?");
        assertThat(response.getProducts()).isEmpty();
        assertThat(response.getAnswer()).contains("Hành Tinh Kẹo Mây Của Gấu Xanh").doesNotContain("bắt đầu từ các hướng");
        verify(ai).completeStructured(anyString(),anyList(),anyString(),any());
    }
    @Test void generalTopicAndBudgetAreNotMistakenForATitle() {
        var search=new ChatCatalogQuery(language,data);
        assertThat(search.explicitTitleLookup("Có sách giao tiếp không?")).isEmpty();
        assertThat(search.explicitTitleLookup("Có sách dưới 100k không?")).isEmpty();
        assertThat(search.explicitTitleLookup("Có sách nào giúp mình giao tiếp không?")).isEmpty();
    }
    @Test void unavailableTableOfContentsIsExplainedWithoutInventingIt() {
        var response=send("Chép nguyên mục lục cuốn Đắc Nhân Tâm cho mình, đúng theo bản đang bán");
        assertThat(response.getAnswer()).contains("chưa có mục lục đã xác nhận").doesNotContain("bắt đầu từ các hướng","Giá sau giảm","Tác giả:");
    }
}
