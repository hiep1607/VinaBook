package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;
import vn.fs.config.CurrentUserAccess;

class ChatToolsTest {
    ObjectMapper mapper=new ObjectMapper();
    ProductRepository products=mock(ProductRepository.class);
    RateRepository rates=mock(RateRepository.class);
    AiGateway ai=mock(AiGateway.class);
    CurrentUserAccess access=mock(CurrentUserAccess.class);
    ChatAnalyticsService analytics=mock(ChatAnalyticsService.class);
    ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
    OrderCancellationService cancellation=mock(OrderCancellationService.class);
    ChatIntentService language=new ChatIntentService();
    ChatRetrievalService data=new ChatRetrievalService(language);
    ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    ChatToolRegistry registry;
    ChatOrchestrator orchestrator;
    ChatBuiltinTools builtins;
    @BeforeEach void setup() {
        data.setRates(rates);
        when(products.findByStatusTrue()).thenReturn(List.of(book(1,190000),book(2,95000),book(3,90000)));
        when(rates.averageRatings()).thenReturn(List.of(new Object[]{1L,4.8},new Object[]{2L,4.5},new Object[]{3L,4.0}));
        builtins=new ChatBuiltinTools(products,data,language,orders,new ChatPolicyService(language));
        builtins.setCancellation(cancellation);
        registry=new ChatToolRegistry(List.of(builtins.searchProducts(),builtins.recommendBooks(),builtins.getProducts(),builtins.updateSelection(),
            builtins.prepareCartChange(),builtins.prepareCheckout(),builtins.prepareCancelOrder(),builtins.getCart(),builtins.getMyOrders(),builtins.getOrderDetail(),builtins.getPolicy(),builtins.respond(),builtins.findRelatedBooks()));
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("fixture");when(ai.isConfigured()).thenReturn(true);
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("event");
        orchestrator=new ChatOrchestrator(ai,mapper,registry,analytics);
        orchestrator.setCatalog(products,data);
    }
    ChatTool.Result run(String tool,String arguments) throws Exception {
        return registry.execute(tool,mapper.readTree(arguments),new ChatTool.Context(request("s","test"),state));
    }
    @Test void filtersRatingIndependentlyOfPriceAndSortsBeforeLimit() throws Exception {
        var r=run("searchProducts","{\"query\":\"Python\",\"maxPrice\":200000,\"minRating\":4.5,\"sort\":\"price-asc\",\"limit\":1}");
        assertThat(r.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(state.criteria.getMaxPrice()).isEqualTo(200000);
        assertThat(state.criteria.getMinRating()).isEqualTo(4.5);
    }
    @Test void continuationRetainsTopicAndExplicitNullClearsPrice() throws Exception {
        run("searchProducts","{\"query\":\"Python\",\"maxPrice\":100000}");
        run("searchProducts","{\"scope\":\"continue\",\"minRating\":4.5}");
        assertThat(state.displayed).containsExactly(2L);
        run("searchProducts","{\"scope\":\"continue\",\"maxPrice\":null}");
        assertThat(state.displayed).containsExactly(1L,2L);
        assertThat(state.criteria.getMaxPrice()).isNull();
    }
    @Test void unknownTopicDoesNotReturnAllBooks() throws Exception {
        assertThat(run("searchProducts","{\"query\":\"Advanced Mars 2099\"}").products()).isEmpty();
    }
    @Test void recommendationBudgetIsTotalAndRetainsReaderLevel() throws Exception {
        var r=run("recommendBooks","{\"query\":\"Python\",\"level\":\"beginner\",\"totalBudget\":200000,\"limit\":2}");
        assertThat(r.products()).extracting(ChatProductDto::getId).containsExactly(3L,2L);
        assertThat(r.answer()).contains("185.000đ","Chưa gồm phí giao hàng");
        assertThat(state.level).isEqualTo("beginner");
        assertThat(run("recommendBooks","{\"scope\":\"continue\",\"limit\":1}").products()).hasSize(1);
        assertThat(state.recommendationBudget).isEqualTo(200000L);
    }
    @Test void experiencedReaderDoesNotGetOnlyBeginnerBooks() throws Exception {
        assertThat(run("recommendBooks","{\"query\":\"Python\",\"level\":\"experienced\"}").products()).isEmpty();
    }
    @Test void adventureRecommendationExcludesBeginnerTechnologyBooks() throws Exception {
        Product adventure=new Product(11L,"Dế Mèn Phiêu Lưu Ký","Tô Hoài",10,80000D,0,"",
            "Cuộc phiêu lưu của Dế Mèn, phù hợp người mới bắt đầu.",LocalDate.now(),true,10,new Category(11L,"Thiếu nhi"));
        Product technology=new Product(12L,"AI Cơ Bản","Tác giả",10,100000D,0,"",
            "Giới thiệu trí tuệ nhân tạo cho người mới bắt đầu.",LocalDate.now(),true,10,new Category(12L,"Công nghệ"));
        when(products.findByStatusTrue()).thenReturn(List.of(adventure,technology));
        assertThat(run("recommendBooks","{\"query\":\"sách phiêu lưu\",\"level\":\"beginner\"}").products())
            .extracting(ChatProductDto::getId).containsExactly(11L);
    }
    @Test void factualPriceDoesNotInvokeNarrator() {
        ChatBookNarrator narrator=mock(ChatBookNarrator.class);orchestrator.setNarrator(narrator);
        plan("{\"tool\":\"getProducts\",\"arguments\":{\"title\":\"Python 1\",\"field\":\"price\"}}");
        var r=orchestrator.reply(request("s","Giá cuốn Python 1?"),state);
        assertThat(r.getAnswer()).contains("190.000đ").doesNotContain("Tồn kho");
        verifyNoInteractions(narrator);
    }
    @Test void recommendationNarrationReceivesTheVerifiedSelection() {
        ChatBookNarrator narrator=mock(ChatBookNarrator.class);orchestrator.setNarrator(narrator);
        when(narrator.present(any(),anyString(),any())).thenAnswer(call->call.getArgument(0));
        plan("{\"tool\":\"recommendBooks\",\"arguments\":{\"query\":\"Python\",\"limit\":2}}");
        var response=orchestrator.reply(request("s","Gợi ý hai sách Python"),state);
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getProducts()).hasSize(2);
        verify(ai,times(1)).complete(anyString(),anyList(),anyString());
        verify(narrator).present(argThat(result->result.products().size()==2),eq("Gợi ý hai sách Python"),same(state));
    }
    @Test void structuredCasePlanExecutesMatchingRead() {
        plan("{\"cases\":[\"BOOK_DETAIL\"],\"calls\":[{\"tool\":\"getProducts\",\"arguments\":{\"title\":\"Python 1\",\"field\":\"price\"}}]}");
        var r=orchestrator.reply(request("s","Giá cuốn Python 1?"),state);
        assertThat(r.getAnswer()).contains("190.000đ");
        assertThat(r.getExecution()).containsEntry("tool","getProducts");
    }
    @Test void standalonePolicyDoesNotFollowAnUnrelatedProductPlan() {
        plan("{\"cases\":[\"POLICY\"],\"calls\":[{\"tool\":\"getProducts\",\"arguments\":{\"title\":\"Python 1\",\"field\":\"price\"}}]}");
        var r=orchestrator.reply(request("s","Phí giao hàng?"),state);
        assertThat(r.isFallback()).isFalse();
        assertThat(r.getAnswer()).contains("20.000đ");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
        assertThat(r.getActions()).isEmpty();
    }
    @Test void combinesBookFactAndPolicyInOneTurn() {
        plan("{\"cases\":[\"BOOK_DETAIL\",\"POLICY\"],\"calls\":[{\"tool\":\"getProducts\",\"arguments\":{\"title\":\"Python 1\",\"field\":\"price\"}},{\"tool\":\"getPolicy\",\"arguments\":{}}]}");
        var r=orchestrator.reply(request("s","Giá cuốn Python 1 và phí giao hàng?"),state);
        assertThat(r.getAnswer()).contains("190.000đ","Phí giao hàng");
        assertThat(r.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
    }
    @Test void comparisonTableUsesActualPricesAndRatings() throws Exception {
        run("searchProducts","{\"limit\":2}");
        var r=run("getProducts","{\"reference\":\"selection\",\"field\":\"table\"}");
        assertThat(r.answer()).contains("| Tên sách | Tác giả | Giá | Rating |","190.000đ","95.000đ","4.8/5","4.5/5");
    }
    @Test void twoExplicitTitlesDoNotAskForASecondBookAgain() {
        Product code=book(1,151200);code.setName("Clean Code - Mã Sạch Và Cách Viết");code.setDescription("Cách viết mã dễ đọc và bảo trì.");code.setCategory(new Category(1L,"Công nghệ"));
        Product startup=book(2,108800);startup.setName("Khởi Nghiệp Tinh Gọn (The Lean Startup)");startup.setDescription("Thử nghiệm và học hỏi khi xây dựng startup.");startup.setCategory(new Category(2L,"Kinh doanh"));
        when(products.findByStatusTrue()).thenReturn(List.of(code,startup));
        plan("{\"calls\":[{\"tool\":\"getProducts\",\"arguments\":{\"title\":\"Clean Code\",\"field\":\"table\"}},{\"tool\":\"getProducts\",\"arguments\":{\"title\":\"Khởi Nghiệp Tinh Gọn\",\"field\":\"table\"}}]}");
        var result=orchestrator.reply(request("s","Nên chọn Clean Code hay Khởi Nghiệp Tinh Gọn?"),state);
        assertThat(result.isFallback()).isFalse();
        assertThat(result.getAnswer()).contains("Clean Code","Khởi Nghiệp Tinh Gọn","Cách viết mã","startup","Gợi ý chọn:","viết mã dễ đọc","xây dựng startup")
            .doesNotContain("cuốn thứ hai");
        assertThat(result.getAnswer().split("\\| Tên sách \\|",-1)).hasSize(2);
        assertThat(result.getProducts()).extracting(ChatProductDto::getId).containsExactlyInAnyOrder(1L,2L);
    }
    @Test void anotherBookExcludesPreviouslyShownRecommendation() {
        Product first=book(1,50000);first.setName("Dế Mèn Phiêu Lưu Ký");first.setDescription("Cuộc phiêu lưu của Dế Mèn.");
        Product second=book(2,60000);second.setName("Hành Trình Mạo Hiểm");second.setDescription("Chuyến phiêu lưu và khám phá.");
        when(products.findByStatusTrue()).thenReturn(List.of(first,second));
        plan("{\"tool\":\"recommendBooks\",\"arguments\":{\"query\":\"phiêu lưu\",\"limit\":1}}");
        var initial=orchestrator.reply(request("s","Gợi ý sách phiêu lưu"),state);
        var another=orchestrator.reply(request("s","Còn cuốn khác không?"),state);
        assertThat(initial.getProducts()).hasSize(1);
        assertThat(another.isFallback()).isFalse();
        assertThat(another.getProducts()).hasSize(1);
        assertThat(another.getProducts().get(0).getId()).isNotEqualTo(initial.getProducts().get(0).getId());
        verify(ai,times(1)).complete(anyString(),anyList(),anyString());
    }
    @Test void childGiftGetsCatalogSuggestionsWithoutAskingTheKnownAge() {
        Product child=book(1,75000);child.setName("10 Vạn Câu Hỏi Vì Sao");child.setCategory(new Category(2L,"Thiếu nhi"));
        when(products.findByStatusTrue()).thenReturn(List.of(child));
        var result=orchestrator.reply(request("s","Mình muốn tặng sách cho em nhỏ 8 tuổi, nên chọn cuốn nào cho phù hợp?"),state);
        assertThat(result.isFallback()).isFalse();
        assertThat(result.getAnswer()).contains("8 tuổi","10 Vạn Câu Hỏi Vì Sao").doesNotContain("thích đọc chủ đề gì");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void explicitTopicAndBudgetDoNotTriggerAnotherGenericQuestion() {
        Product affordable=book(1,90000);affordable.setName("Kỹ Năng Sống");affordable.setDescription("Phát triển bản thân và kỹ năng sống.");
        Product costly=book(2,120000);costly.setName("Thói Quen Tốt");costly.setDescription("Phát triển bản thân và thói quen.");
        when(products.findByStatusTrue()).thenReturn(List.of(affordable,costly));
        plan("{\"tool\":\"respond\",\"arguments\":{\"kind\":\"clarify\",\"topic\":\"preferences\",\"missing\":\"purpose\"}}");
        var result=orchestrator.reply(request("s","Gợi ý sách phát triển bản thân, ngân sách khoảng 100k"),state);
        assertThat(result.isFallback()).isFalse();
        assertThat(result.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(result.getAnswer()).doesNotContain("chủ đề gì","bao nhiêu tiền");
    }
    @Test void orderFollowupRechecksOwnershipWithFocusedId() throws Exception {
        when(orders.latestOrderId()).thenReturn(7L);
        when(orders.orderById(7L)).thenReturn("Đơn #7: Đang xử lý");
        run("getMyOrders","{\"mode\":\"latest\"}");
        assertThat(state.focusedOrderId).isEqualTo(7L);
        when(orders.orderById(7L)).thenReturn("Mình không tìm thấy mã đơn này trong tài khoản của bạn.");
        assertThat(run("getOrderDetail","{}").answer()).contains("không tìm thấy");
        verify(orders,times(2)).orderById(7L);
    }
    @Test void conceptualCategoryUsesTopicWithoutKeepingImpossibleCategory() throws Exception {
        assertThat(run("searchProducts","{\"category\":\"Công nghệ thông tin\",\"sort\":\"price-asc\",\"limit\":1}").products())
            .extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(state.criteria.getCategory()).isEmpty();
    }
    @Test void continuationKeepsLimitAndClearsOldMissingTitle() throws Exception {
        state.missingTitle="Mars 2099";
        run("searchProducts","{\"query\":\"Python\",\"limit\":1}");
        assertThat(state.missingTitle).isEmpty();
        assertThat(run("searchProducts","{\"scope\":\"continue\",\"sort\":\"price-asc\"}").products())
            .extracting(ChatProductDto::getId).containsExactly(3L);
    }
    @Test void alternativeTopicsAreUnionNotIntersection() throws Exception {
        assertThat(run("searchProducts","{\"query\":\"Java hoặc Python\",\"maxPrice\":100000}").products())
            .extracting(ChatProductDto::getId).containsExactly(2L,3L);
    }
    @Test void vagueNeedAsksForPreferencesRatherThanAnUnseenBook() throws Exception {
        var result=run("respond","{\"kind\":\"clarify\"}");
        assertThat(result.answer()).contains("chủ đề").doesNotContain("số thứ tự","ngân sách");
    }
    @Test void detailPreservesSelectionThenRemoveHighestPrice() throws Exception {
        run("searchProducts","{\"sort\":\"price-asc\"}");
        run("getProducts","{\"reference\":\"second\"}");
        assertThat(state.focus).isEqualTo(2L);
        var r=run("updateSelection","{\"reference\":\"most_expensive\"}");
        assertThat(r.products()).extracting(ChatProductDto::getId).containsExactly(3L,2L);
        assertThat(r.answer()).contains("danh sách đang cân nhắc");
    }
    @Test void comparisonPairBecomesTheSelectionForCheapestFollowup() throws Exception {
        run("getProducts","{\"title\":\"Python 1\",\"field\":\"table\"}");
        run("getProducts","{\"title\":\"Python 2\",\"field\":\"table\"}");
        assertThat(state.shortlist).containsExactly(1L, 2L);
        assertThat(run("getProducts","{\"reference\":\"cheapest\",\"field\":\"price\"}").products())
            .extracting(ChatProductDto::getId).containsExactly(2L);
    }
    @Test void fabricatedProductIdCannotBeSelected() throws Exception {
        assertThat(run("prepareCheckout","{\"productId\":1}").status()).isEqualTo("NEEDS_INPUT");
    }
    @Test void checkoutGuidesToProductWithoutCreatingOrder() throws Exception {
        var r=run("prepareCheckout","{\"title\":\"Python 1\",\"quantity\":3}");
        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.actions().get(0).getRoute()).isEqualTo("/product/1?quantity=3");
        assertThat(r.answer()).contains("3 cuốn", "chat chưa đặt đơn");
        assertThat(r.answer()).doesNotContain("đặt hàng thành công");
        verifyNoInteractions(orders);
    }
    @Test void cartUpdateGuidesToCartPage() throws Exception {
        ChatRequestDto request=request("s","Sửa cuốn Python 2 thành 2 bản");
        ChatRequestDto.CartItem item=new ChatRequestDto.CartItem();item.setProductId(2L);item.setQuantity(1);request.setCartItems(List.of(item));
        var r=registry.execute("prepareCartChange",mapper.readTree("{\"title\":\"Python 2\",\"operation\":\"update\",\"quantity\":2}"),new ChatTool.Context(request,state));
        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.actions().get(0).getType()).isEqualTo("SHOW_CART");
        assertThat(r.actions().get(0).getRoute()).isEqualTo("/cart");
    }
    @Test void rejectsUnknownToolsAndArgumentsBeforeExecution() {
        assertThatThrownBy(()->run("createOrder","{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->run("getMyOrders","{\"userId\":99}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->run("searchProducts","{\"minRating\":6}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->run("prepareCheckout","{\"quantity\":1.5}")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orders);
    }
    @Test void multiStepSearchThenPrepareUsesResultReference() {
        plan("{\"calls\":[{\"tool\":\"searchProducts\",\"arguments\":{\"sort\":\"price-asc\",\"limit\":1}},{\"tool\":\"prepareCartChange\",\"arguments\":{\"reference\":\"first\"}}]}");
        var r=orchestrator.reply(request("s","Tìm cuốn rẻ nhất rồi thêm vào giỏ"),state);
        assertThat(r.getExecution().get("status")).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(r.getProducts()).extracting(ChatProductDto::getId).containsExactly(3L);
    }
    @Test void invalidSecondCallDoesNotChangeSelection() {
        plan("{\"calls\":[{\"tool\":\"searchProducts\",\"arguments\":{}},{\"tool\":\"createOrder\",\"arguments\":{}}]}");
        var r=orchestrator.reply(request("s","test"),state);
        assertThat(r.isFallback()).isTrue();assertThat(state.displayed).isEmpty();
    }
    @Test void emptySearchCannotFallThroughToAnOldFocus() {
        state.focus=1L;
        plan("{\"calls\":[{\"tool\":\"searchProducts\",\"arguments\":{\"query\":\"Mars 2099\"}},{\"tool\":\"prepareCheckout\",\"arguments\":{}}]}");
        var r=orchestrator.reply(request("s","test"),state);
        assertThat(r.getProducts()).isEmpty();assertThat(r.getActions()).isEmpty();
    }
    @Test void requestRetryAndOwnerIsolationWorkThroughEntryPoint() {
        ChatService chat=new ChatService(products,ai,language,data,new ChatPolicyService(language),orders,analytics,access);
        chat.setOrchestrator(orchestrator);
        plan("{\"tool\":\"searchProducts\",\"arguments\":{}}");
        when(access.currentUserId()).thenReturn(7L);chat.reply(request("same","Tìm sách"));
        plan("{\"tool\":\"updateSelection\",\"arguments\":{\"reference\":\"most_expensive\"}}");
        ChatRequestDto r=request("same","Bỏ cuốn giá cao nhất");r.setRequestId("retry");
        var first=chat.reply(r);assertThat(chat.reply(r)).isSameAs(first);
        when(access.currentUserId()).thenReturn(8L);
        assertThat(chat.reply(r).getProducts()).isEmpty();
    }
    @Test void arbitraryModelProseCannotBecomeAnActionReceipt() {
        plan("Đã đặt hàng thành công!");
        var r=orchestrator.reply(request("s","test"),state);
        assertThat(r.isFallback()).isTrue();assertThat(r.getActions()).isEmpty();
        assertThat(r.getAnswer()).doesNotContain("đặt hàng thành công");
    }
    @Test void registrationCanExtendWithoutChangingDispatcher() throws Exception {
        ChatTool custom=new ChatTool() {
            public String name(){return "newFeature";}public String description(){return "A new read capability";}
            public Map<String,Parameter> parameters(){return Map.of();}
            public Result execute(com.fasterxml.jackson.databind.JsonNode a,Context c){return Result.text("ok",ChatIntent.UNKNOWN);}
        };
        var extended=new ChatToolRegistry(List.of(custom));
        assertThat(extended.execute("newFeature",mapper.readTree("{}"),new ChatTool.Context(request("s","test"),state)).answer()).isEqualTo("ok");
        assertThatThrownBy(()->new ChatToolRegistry(List.of(custom,custom))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cartReferenceUsesBrowserCartInsteadOfPageFocus() throws Exception {
        ChatRequestDto request=request("s","Bỏ cuốn đầu trong giỏ");request.setActiveProductId(1L);
        ChatRequestDto.CartItem item=new ChatRequestDto.CartItem();item.setProductId(3L);item.setQuantity(2);request.setCartItems(List.of(item));
        var result=registry.execute("prepareCartChange",mapper.readTree("{\"source\":\"cart\",\"reference\":\"first\",\"operation\":\"remove\"}"),new ChatTool.Context(request,state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(result.actions().get(0).getRoute()).isEqualTo("/cart");
        var cart=registry.execute("getCart",mapper.readTree("{}"),new ChatTool.Context(request,state));
        assertThat(cart.answer()).contains("180.000đ");
    }
    @Test void narratorRejectsUnsupportedEvidenceAndActionClaims() throws Exception {
        ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        var base=run("getProducts","{\"title\":\"Python 1\"}");
        plan("{\"reasons\":[{\"productId\":1,\"evidence\":\"Không có đoạn này trong mô tả\",\"reason\":\"Rất hay\"}]}");
        assertThat(narrator.explain(base,"Sách thế nào?")).isSameAs(base);
        plan("{\"reasons\":[{\"productId\":1,\"evidence\":\"Học lập trình Python cơ bản\",\"reason\":\"Đã đặt hàng thành công\"}]}");
        assertThat(narrator.explain(base,"Sách thế nào?")).isSameAs(base);
    }
    @Test void narratorCanExplainVerifiedDescriptionWithoutChangingFacts() throws Exception {
        ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        var base=run("getProducts","{\"title\":\"Python 1\"}");
        plan("{\"reasons\":[{\"productId\":1,\"evidence\":\"Học lập trình Python cơ bản\",\"reason\":\"Phù hợp để bắt đầu học lập trình từ nền tảng.\"}]}");
        var explained=narrator.explain(base,"Sách thế nào?");
        assertThat(explained.answer()).contains("Phù hợp để bắt đầu");
        assertThat(explained.products()).isSameAs(base.products());
        assertThat(explained.actions()).isSameAs(base.actions());
    }
    @Test void exactTitleDoesNotReturnUnrelatedBooksOrIgnorePrice() throws Exception {
        plan("{\"tool\":\"searchProducts\",\"arguments\":{}}");
        assertThat(orchestrator.reply(request("s","Tìm cuốn Python 2"),state).getProducts())
            .extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(run("searchProducts","{\"title\":\"Python 2\",\"maxPrice\":90000}").products()).isEmpty();
        assertThat(state.missingTitle).isEmpty();
    }
    @Test void titleSubstringIsLiteralAndDoesNotSearchDescription() {
        Product aiBook=book(4,50000);aiBook.setName("AI thực hành");
        Product other=book(5,60000);other.setName("Truyện ngắn");other.setDescription("AI xuất hiện trong mô tả");
        Product accented=book(6,65000);accented.setName("Đại Việt sử ký");
        Product embedded=book(7,60000);embedded.setName("Blockchain Fundamentals");
        when(products.findByStatusTrue()).thenReturn(List.of(aiBook,other,accented,embedded));
        plan("{\"tool\":\"searchProducts\",\"arguments\":{}}");
        assertThat(orchestrator.reply(request("s","Tìm sách có chữ \"AI\" trong tên."),state).getProducts())
            .extracting(ChatProductDto::getId).containsExactly(4L);
        assertThat(orchestrator.reply(request("s","Tìm sách có chữ \"chủ đề không tồn tại\" trong tên."),state).getProducts()).isEmpty();
    }
    @Test void broadTechnologyCategoryCannotSatisfyBusinessTopic() throws Exception {
        Product code=book(1,50000);code.setCategory(new Category(1L,"Sách Khoa học công nghệ – Kinh tế"));
        Product business=book(2,60000);business.setName("Khởi nghiệp tinh gọn");business.setDescription("Xây dựng doanh nghiệp");business.setCategory(code.getCategory());
        when(products.findByStatusTrue()).thenReturn(List.of(code,business));
        assertThat(run("searchProducts","{\"query\":\"kinh doanh\"}").products()).extracting(ChatProductDto::getId).containsExactly(2L);
    }
    @Test void omittedTechnologyQueryStillKeepsUserTopicWithRating() {
        Product fiction=book(1,50000);fiction.setName("Bố Già");fiction.setDescription("Tiểu thuyết");
        when(products.findByStatusTrue()).thenReturn(List.of(fiction,book(2,60000)));
        plan("{\"tool\":\"searchProducts\",\"arguments\":{\"maxPrice\":250000,\"minRating\":4.5}}");
        assertThat(orchestrator.reply(request("s","Tìm sách công nghệ dưới 250000 rating từ 4.5"),state).getProducts())
            .extracting(ChatProductDto::getId).containsExactly(2L);
    }
    @Test void bestsellerSelectionPrecedesPriceSorting() throws Exception {
        Product popular=book(1,190000);popular.setSold(100);
        Product next=book(2,95000);next.setSold(80);
        Product cheap=book(3,10000);cheap.setSold(1);
        when(products.findByStatusTrue()).thenReturn(List.of(cheap,next,popular));
        assertThat(registry.execute("searchProducts",mapper.readTree("{\"selection\":\"bestsellers\",\"sort\":\"price-asc\",\"limit\":2}"),
            new ChatTool.Context(request("s","Chọn 2 cuốn bán chạy rồi sắp giá tăng dần"),state)).products())
            .extracting(ChatProductDto::getId).containsExactly(2L,1L);
    }
    @Test void cartCannotRemoveAnAbsentBookOrAddBeyondCombinedStock() throws Exception {
        var absent=run("prepareCartChange","{\"title\":\"Python 2\",\"operation\":\"remove\"}");
        assertThat(absent.actions()).isEmpty();assertThat(absent.answer()).contains("không thấy");
        ChatRequestDto request=request("s","Thêm 3 cuốn Python 2");
        ChatRequestDto.CartItem item=new ChatRequestDto.CartItem();item.setProductId(2L);item.setQuantity(8);request.setCartItems(List.of(item));
        var excess=registry.execute("prepareCartChange",mapper.readTree("{\"title\":\"Python 2\",\"operation\":\"add\",\"quantity\":3}"),new ChatTool.Context(request,state));
        assertThat(excess.actions()).isEmpty();assertThat(excess.answer()).contains("vượt tồn kho");
    }
    @Test void orderTotalCannotBeAnsweredByCartEvenWhenPlannerChoosesCart() {
        state.focusedOrderId=7L;when(orders.orderById(7L)).thenReturn("Đơn #7: Tổng tiền 200.000đ");
        plan("{\"tool\":\"getCart\",\"arguments\":{}}");
        var response=orchestrator.reply(request("s","Tổng tiền đơn này bao nhiêu?"),state);
        assertThat(response.getAnswer()).contains("200.000đ").doesNotContain("Giỏ hàng");
        assertThat(response.getExecution().get("tool")).isEqualTo("getOrderDetail");
        assertThat(state.lastTask).isEqualTo("ORDER_SUPPORT");
        state.focusedOrderId=null;when(orders.latestOrders()).thenReturn("Bạn cần đăng nhập để tra cứu đơn hàng.");
        assertThat(orchestrator.reply(request("s","Tổng tiền đơn đó?"),state).getAnswer()).contains("đăng nhập");
    }
    @Test void clarificationAsksForTheMissingTaskInput() {
        plan("{\"tool\":\"respond\",\"arguments\":{\"kind\":\"clarify\"}}");
        assertThat(orchestrator.reply(request("s","So sánh hai cuốn này"),state).getAnswer()).contains("tên hai cuốn");
        assertThat(orchestrator.reply(request("s","Thêm cuốn này vào giỏ"),state).getAnswer()).contains("tên sách").doesNotContain("ngân sách");
        assertThat(orchestrator.reply(request("s","Mua sách làm quà cho bạn"),state).getAnswer()).contains("Người nhận quà");
    }
    @Test void returnAndCancellationQuestionsHaveSpecificGuidance() {
        ChatPolicyService policy=new ChatPolicyService(language);
        assertThat(policy.answer("Tôi có thể đổi sách sau khi nhận không?")).contains("7 ngày","giao sai");
        assertThat(policy.answer("Đổi sách giao sai thế nào?")).contains("7 ngày").doesNotContain("20.000đ");
        assertThat(policy.answer("Làm sao để hủy đơn?")).contains("mã đơn","yêu cầu xác nhận","COD").doesNotContain("chính sách nào");
    }
    @Test void missingTitleDoesNotHijackNewTextbookOrCartRequest() {
        state.missingTitle="Tên không tồn tại";
        Product textbook=book(2,95000);textbook.setName("Giáo Trình Lập Trình Java");
        when(products.findByStatusTrue()).thenReturn(List.of(textbook));
        plan("{\"tool\":\"prepareCartChange\",\"arguments\":{\"title\":\"Giáo Trình Lập Trình Java\",\"operation\":\"add\"}}");
        var reply=orchestrator.reply(request("s","Thêm 1 cuốn Giáo Trình Lập Trình Java vào giỏ"),state);
        assertThat(reply.getActions()).hasSize(1);
        assertThat(reply.getAnswer()).doesNotContain("vẫn chưa tìm thấy");
    }
    @Test void cancellationHowToIsPolicyEvenIfPlannerChoosesAction() {
        OrderCancellationService cancellation=mock(OrderCancellationService.class);
        builtins.setCancellation(cancellation);
        var response=builtins.prepareCancelOrder().execute(mapper.createObjectNode(),
            new ChatTool.Context(request("s","Làm sao để hủy đơn?"),state));
        assertThat(response.intent()).isEqualTo(ChatIntent.POLICY);
        assertThat(response.answer()).contains("COD","xác nhận");
        assertThat(response.actions()).isEmpty();
        verifyNoInteractions(cancellation);
    }
    @Test void knownSubjectDoesNotTriggerGenericPreferenceQuestion() throws Exception {
        var r=run("respond","{\"kind\":\"clarify\",\"topic\":\"preferences\",\"subject\":\"Python\",\"missing\":\"subject\"}");
        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.products()).hasSize(3);
    }
    @Test void giftAsksOneMissingFieldAndKeepsSuppliedBudget() throws Exception {
        var r=run("respond","{\"kind\":\"clarify\",\"topic\":\"gift\",\"recipient\":\"bạn 20 tuổi\",\"budget\":150000,\"missing\":\"subject\"}");
        assertThat(r.answer()).contains("chủ đề").doesNotContain("tuổi","bao nhiêu tiền");
        var next=run("respond","{\"kind\":\"clarify\",\"topic\":\"gift\",\"scope\":\"continue\",\"subject\":\"Python\"}");
        assertThat(next.products()).hasSize(1);
        assertThat(state.recommendationBudget).isEqualTo(150000L);
    }
    @Test void newNeedDiscardsOldClarificationFacts() throws Exception {
        run("respond","{\"budget\":10000,\"topic\":\"gift\"}");
        state.comparisonAnchor=2L;
        var r=run("respond","{\"scope\":\"new\",\"topic\":\"preferences\",\"subject\":\"Python\"}");
        assertThat(r.products()).hasSize(3);
        assertThat(state.recommendationBudget).isNull();
        assertThat(state.comparisonAnchor).isNull();
    }
    @Test void singleBookComparisonAsksForSecondBookWithoutFabricatingOne() throws Exception {
        var r=run("getProducts","{\"title\":\"Python 2\",\"field\":\"table\"}");
        assertThat(r.status()).isEqualTo("NEEDS_INPUT");
        assertThat(r.answer()).contains("Python 2","cuốn thứ hai");
        assertThat(state.focus).isEqualTo(2L);
        assertThat(r.actions()).isEmpty();
    }
    @Test void comparisonReplyCombinesSecondBookWithPendingFirstBook() throws Exception {
        run("getProducts","{\"title\":\"Python 2\",\"field\":\"table\"}");
        var r=run("getProducts","{\"title\":\"Python 3\",\"field\":\"table\"}");
        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.products()).extracting(ChatProductDto::getId).containsExactly(2L,3L);
        assertThat(r.answer()).contains("95.000đ","90.000đ");
    }
    @Test void ambiguousPurchaseOffersRealCandidatesThenAcceptsOrdinal() throws Exception {
        var r=run("prepareCheckout","{\"title\":\"Python\",\"quantity\":2}");
        assertThat(r.status()).isEqualTo("NEEDS_INPUT");
        assertThat(r.products()).hasSize(3);assertThat(r.actions()).isEmpty();
        var next=run("prepareCheckout","{\"reference\":\"second\",\"quantity\":2}");
        assertThat(next.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(next.actions().get(0).getRoute()).isEqualTo("/product/2?quantity=2");
        assertThat(next.answer()).contains("2 cuốn");
    }
    @Test void emptyResultsAskToChangeSearchInsteadOfSelectingNonexistentBook() throws Exception {
        state.emptySearch=true;
        var r=run("prepareCheckout","{}");
        assertThat(r.answer()).contains("chưa có sách phù hợp").doesNotContain("số thứ tự");
        assertThat(r.actions()).isEmpty();
    }
    @Test void shortReplyReceivesPendingQuestionAndKnownFactsInPlannerContext() {
        plan("{\"tool\":\"respond\",\"arguments\":{\"kind\":\"clarify\",\"subject\":\"Python\",\"missing\":\"budget\"}}");
        orchestrator.reply(request("s","Chọn sách Python trong ngân sách giúp tôi"),state);
        assertThat(state.pendingQuestion).contains("bao nhiêu tiền");
        when(ai.complete(anyString(),anyList(),anyString())).thenAnswer(invocation->{
            var input=mapper.readTree((String)invocation.getArgument(2));
            assertThat(input.path("context").path("pendingQuestion").asText()).contains("bao nhiêu tiền");
            assertThat(input.path("context").path("clarificationFacts").path("subject").asText()).isEqualTo("Python");
            return "{\"tool\":\"recommendBooks\",\"arguments\":{\"query\":\"Python\",\"totalBudget\":150000}}";
        });
        var r=orchestrator.reply(request("s","Dưới 150k"),state);
        assertThat(r.getProducts()).hasSize(1);assertThat(state.pendingQuestion).isEmpty();
        assertThat(state.clarificationFacts).isEmpty();
    }
    @Test void providerFailureDoesNotBlameMissingUserInformationOrLoseQuestion() {
        state.pendingQuestion="Bạn muốn dành bao nhiêu tiền?";
        when(ai.complete(anyString(),anyList(),anyString())).thenThrow(new IllegalStateException());
        var r=orchestrator.reply(request("s","150k"),state);
        assertThat(r.isFallback()).isTrue();assertThat(r.getAnswer()).contains("lỗi xử lý").doesNotContain("nói rõ");
        assertThat(state.pendingQuestion).contains("bao nhiêu tiền");
    }
    @Test void invalidPlanIsRepairedOnceBeforeAnyToolExecutes() {
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(
            "{\"calls\":[{\"tool\":\"searchProducts\",\"arguments\":{\"query\":\"Python\"}},{\"tool\":\"respond\",\"arguments\":{\"missing\":\"second_book\"}}]}",
            "{\"tool\":\"respond\",\"arguments\":{\"kind\":\"clarify\",\"topic\":\"comparison\"}}");
        var r=orchestrator.reply(request("s","So sánh hai cuốn"),state);
        assertThat(r.isFallback()).isFalse();assertThat(state.toolSearch).isEmpty();
        assertThat(r.getAnswer()).contains("tên hai cuốn");
        verify(ai,times(2)).complete(anyString(),anyList(),anyString());
    }
    @Test void comparisonFocusSurvivesCheaperStockAndOtherBookFollowups() throws Exception {
        state.shortlist=new ArrayList<>(List.of(1L,2L));
        state.displayed=new ArrayList<>(state.shortlist);
        registry.execute("getProducts",mapper.readTree("{\"reference\":\"selection\",\"field\":\"table\"}"),new ChatTool.Context(request("s","So sánh hai cuốn"),state));
        var cheapest=orchestrator.reply(request("s","Cuốn nào rẻ hơn?"),state);
        assertThat(cheapest.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(cheapest.getAnswer()).contains("95.000đ");
        var followup=request("s","Cuốn đó còn hàng không?");followup.setActiveProductId(1L);
        var stock=orchestrator.reply(followup,state);
        assertThat(stock.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(stock.getAnswer()).contains("còn 10 bản");
        var other=orchestrator.reply(request("s","Nội dung cuốn còn lại?"),state);
        assertThat(other.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(state.comparisonProducts).containsExactly(1L,2L);
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void knownAuthorAndAvailabilityAreNotHiddenByBibliographyPlan() throws Exception {
        Product prince=book(1,70000);prince.setName("Hoàng Tử Bé");prince.setAuthor("Antoine de Saint-Exupéry");
        when(products.findByStatusTrue()).thenReturn(List.of(prince));
        var answer=registry.execute("getProducts",mapper.readTree("{\"title\":\"Hoàng Tử Bé\",\"field\":\"bibliography\"}"),
                new ChatTool.Context(request("s","Hoàng Tử Bé do tác giả nào viết và hiện còn bán không?"),state));
        assertThat(answer.answer()).contains("Antoine de Saint-Exupéry","Đang bán","10 bản").doesNotContain("Chưa có dữ liệu");
    }
    @Test void missingBibliographyNamesOnlyTheRequestedUnknownFields() throws Exception {
        var answer=registry.execute("getProducts",mapper.readTree("{\"title\":\"Python 1\",\"field\":\"bibliography\"}"),
                new ChatTool.Context(request("s","Sách Python 1 có bao nhiêu trang và ISBN là gì?"),state));
        assertThat(answer.answer()).contains("số trang, ISBN").doesNotContain("nhà xuất bản");
    }
    @Test void unknownNamedBookIsReportedInsteadOfAskingForItsNameAgain() throws Exception {
        var answer=registry.execute("getProducts",mapper.readTree("{\"title\":\"Nhật Ký Atlantis 2099\"}"),
                new ChatTool.Context(request("s","Giá Nhật Ký Atlantis 2099?"),state));
        assertThat(answer.answer()).contains("chưa tìm thấy","Nhật Ký Atlantis 2099").doesNotContain("nhắn tên");
    }
    @Test void alternativesDoNotProduceABundleTotalWithoutExplicitBudget() throws Exception {
        assertThat(run("recommendBooks","{\"query\":\"Python\",\"limit\":2}").answer()).doesNotContain("Tổng tiền sách");
    }
    @Test void startupRecommendationsExcludeUnrelatedBusinessTechnology() throws Exception {
        Product startup=book(1,120000);startup.setName("Khởi Nghiệp Tinh Gọn");startup.setDescription("Kiểm chứng ý tưởng và xây dựng startup.");
        Product blockchain=book(2,110000);blockchain.setName("Blockchain Fundamentals");blockchain.setDescription("Ứng dụng công nghệ blockchain cho doanh nghiệp và kinh doanh.");
        when(products.findByStatusTrue()).thenReturn(List.of(startup,blockchain));
        assertThat(run("recommendBooks","{\"query\":\"khởi nghiệp\"}").products()).extracting(ChatProductDto::getId).containsExactly(1L);
    }
    @Test void shippingScenariosReturnConcreteFeesAndTotals() {
        var r=orchestrator.reply(request("s","Đơn 290k và 310k thì phí ship thế nào?"),state);
        assertThat(r.getAnswer()).contains("Tiền sách 290.000đ: phí giao hàng 20.000đ; tổng thanh toán 310.000đ",
                "Tiền sách 310.000đ: miễn phí giao hàng; tổng thanh toán 310.000đ");
        assertThat(r.getIntent()).isEqualTo(ChatIntent.POLICY);
    }
    @Test void orderAccessQuestionDoesNotFallIntoFocusedProductContext() {
        state.focus=1L;state.shortlist=List.of(1L);
        when(orders.latestOrders()).thenReturn("Bạn cần đăng nhập để xem đơn hàng của mình.");
        var result=orchestrator.reply(request("s","Chưa đăng nhập thì có xem được đơn hàng của tôi không?"),state);
        assertThat(result.getIntent()).isEqualTo(ChatIntent.ORDER_SUPPORT);
        assertThat(result.getAnswer()).contains("đăng nhập").doesNotContain("cuốn sách");
    }
    @Test void purchaseUsesConversationReferenceDespiteConflictingModelTitle() {
        state.shortlist=new ArrayList<>(List.of(1L,2L));state.displayed=new ArrayList<>(state.shortlist);state.focus=2L;
        plan("{\"tool\":\"prepareCartChange\",\"arguments\":{\"title\":\"Python 1\",\"operation\":\"add\",\"quantity\":2}}");
        var response=orchestrator.reply(request("s","Tôi muốn mua 2 bản cuốn đó."),state);
        assertThat(response.getExecution()).containsEntry("tool","prepareCheckout");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(response.getAnswer()).contains("Python 2","2 cuốn").doesNotContain("xác nhận thêm");
        assertThat(response.getActions()).extracting(ChatActionDto::getType).containsExactly("VIEW_PRODUCT");
        assertThat(response.getActions().get(0).getRoute()).isEqualTo("/product/2?quantity=2");
        assertThat(response.getActions().get(0).getQuantity()).isEqualTo(2);
    }
    @Test void namedBookAndOrdinalOverrideConflictingModelIds() throws Exception {
        state.shortlist=new ArrayList<>(List.of(1L,2L));state.displayed=new ArrayList<>(state.shortlist);state.focus=1L;
        var named=registry.execute("getProducts",mapper.readTree("{\"productId\":1,\"field\":\"price\"}"),
            new ChatTool.Context(request("s","Giá Python 2?"),state));
        assertThat(named.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        var ordinal=registry.execute("getProducts",mapper.readTree("{\"productId\":2,\"field\":\"price\"}"),
            new ChatTool.Context(request("s","Giá cuốn đầu tiên?"),state));
        assertThat(ordinal.products()).extracting(ChatProductDto::getId).containsExactly(1L);
    }
    @Test void doNotGuessInstructionStillLooksUpTheProvidedTitle() {
        ChatService chat=new ChatService(products,ai,language,data,new ChatPolicyService(language),orders,analytics,access);
        chat.setOrchestrator(orchestrator);
        var response=chat.reply(request("s","Cửa hàng có sách tên “Nhật Ký Atlantis 2099” không? Nếu không có, đừng đoán giá."));
        assertThat(response.getAnswer()).contains("chưa tìm thấy","Nhật Ký Atlantis 2099").doesNotContain("cho mình tên sách");
        assertThat(response.getProducts()).isEmpty();
    }
    @Test void sourceCodeGoalKeepsProgrammingBooksEvenWhenModelQueryIsVague() {
        Product clean=book(1,150000);clean.setName("Clean Code");clean.setDescription("Viết mã nguồn dễ đọc và bảo trì.");
        Product practical=book(2,130000);practical.setName("The Pragmatic Programmer");practical.setDescription("Từ thợ code thành kỹ sư phần mềm, công cụ và thói quen nghề nghiệp.");
        Product law=book(3,120000);law.setName("Tìm Hiểu Bộ Luật Dân Sự");law.setDescription("Nội dung pháp luật dễ hiểu.");
        when(products.findByStatusTrue()).thenReturn(List.of(clean,practical,law));
        plan("{\"tool\":\"recommendBooks\",\"arguments\":{\"query\":\"dễ hiểu dễ bảo trì\",\"limit\":3}}");
        var response=orchestrator.reply(request("s","Mã nguồn của tôi ngày càng rối, tôi muốn đọc gì để viết dễ hiểu và dễ bảo trì hơn?"),state);
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactlyInAnyOrder(1L,2L);
    }
    @Test void ordinaryDiscoveryCannotSilentlyBecomeBestsellers() throws Exception {
        Product quiet=book(1,190000);quiet.setSold(0);
        Product popular=book(2,95000);popular.setSold(100);
        when(products.findByStatusTrue()).thenReturn(List.of(popular,quiet));
        var response=registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\",\"selection\":\"bestsellers\",\"sort\":\"best\"}"),
            new ChatTool.Context(request("s","Gợi ý sách Python đang bán"),state));
        assertThat(response.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(response.answer()).doesNotContain("số lượng bán cao nhất");
        assertThat(state.toolSearch).doesNotContainKeys("selection","sort");
        var continued=registry.execute("searchProducts",mapper.readTree("{\"scope\":\"continue\",\"selection\":\"bestsellers\",\"maxPrice\":200000}"),
            new ChatTool.Context(request("s","Chỉ lấy dưới 200k"),state));
        assertThat(continued.products()).hasSize(2);
    }
    @Test void cancellationLoginResponseRetainsTheCancellationRequest() {
        when(cancellation.preview(null)).thenThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.UNAUTHORIZED,"Bạn cần đăng nhập để hủy đơn hàng."));
        plan("{\"tool\":\"getMyOrders\",\"arguments\":{}}");
        var response=orchestrator.reply(request("s","Hủy đơn hàng giúp tôi"),state);
        assertThat(response.getAnswer()).contains("đăng nhập","Hủy đơn hàng giúp tôi").doesNotContain("đang ở đâu");
        assertThat(response.getActions()).isEmpty();
        assertThat(response.getExecution()).containsEntry("tool","prepareCancelOrder");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void directPurchaseCannotBeReducedToASearchResult() {
        plan("{\"tool\":\"searchProducts\",\"arguments\":{\"title\":\"Python 2\"}}");
        var response=orchestrator.reply(request("s","Tôi muốn mua 2 bản Python 2."),state);
        assertThat(response.getExecution()).containsEntry("tool","prepareCheckout");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(response.getActions().get(0).getRoute()).isEqualTo("/product/2?quantity=2");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void excessivePurchaseQuantityAsksForAValidQuantity() {
        var response=orchestrator.reply(request("s","Tôi muốn mua 100 bản Python 2."),state);
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getAnswer()).contains("1 đến 99");
        assertThat(response.getActions()).isEmpty();
    }
    @Test void unknownPurchaseTitleNeverFallsBackToThePreviousFocusedBook() {
        state.focus=2L;state.shortlist=new ArrayList<>(List.of(2L));
        var response=orchestrator.reply(request("s","Tôi muốn mua sách “Nhật Ký Atlantis 2099”."),state);
        assertThat(response.getProducts()).isEmpty();
        assertThat(response.getActions()).isEmpty();
        assertThat(response.getAnswer()).contains("chưa tìm thấy");
    }
    @Test void purchaseQuantityCanBeWrittenInVietnameseWords() {
        var response=orchestrator.reply(request("s","Tôi muốn mua hai bản Python 2."),state);
        assertThat(response.getExecution()).containsEntry("tool","prepareCheckout");
        assertThat(response.getActions().get(0).getQuantity()).isEqualTo(2);
    }
    @Test void repeatedMoreSurvivesPolicyInterruptionThenUsesNearbyWithoutRepeating() {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám và phá án.",50000),
            discoveryBook(2,"Hồ Sơ B","Trinh thám và phá án.",60000),
            discoveryBook(3,"Bí Mật C","Câu chuyện bí ẩn và ly kỳ.",70000),
            discoveryBook(4,"Chuyện Tình D","Tình yêu và gia đình.",80000)));
        plan("{\"tool\":\"searchProducts\",\"arguments\":{\"query\":\"trinh thám\",\"limit\":1}}");
        var first=orchestrator.reply(request("s","Gợi ý sách trinh thám"),state);
        orchestrator.reply(request("s","Phí giao hàng?"),state);
        var second=orchestrator.reply(request("s","Còn cuốn nào khác cùng chủ đề?"),state);
        var nearby=orchestrator.reply(request("s","Còn cuốn nào khác không?"),state);
        var exhausted=orchestrator.reply(request("s","Còn cuốn nào khác không?"),state);
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(second.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(nearby.getProducts()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(nearby.getAnswer()).contains("đã giới thiệu hết","Gợi ý gần chủ đề","bí ẩn");
        assertThat(exhausted.getProducts()).isEmpty();
        assertThat(exhausted.getAnswer()).contains("chưa có cuốn nào khác");
        assertThat(state.shownProductIds).containsExactly(1L,2L,3L);
        verify(ai,times(1)).complete(anyString(),anyList(),anyString());
    }
    @Test void newSearchKeepsTheConversationWideExclusionSet() throws Exception {
        state.shownProductIds.add(2L);
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\",\"limit\":1,\"scope\":\"new\"}"),
            new ChatTool.Context(request("s","Gợi ý Python"),state));
        var more=registry.execute("searchProducts",mapper.readTree("{\"query\":\"chủ đề sai\",\"scope\":\"more\"}"),
            new ChatTool.Context(request("s","Còn cuốn nào khác cùng chủ đề?"),state));
        assertThat(more.products()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(state.toolSearch).containsEntry("query","Python");
        assertThat(state.shownProductIds).contains(1L,2L,3L);
    }
    @Test void explicitSixGetsExactThenNearbyAndAnHonestShortage() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám và phá án.",50000),
            discoveryBook(2,"Hồ Sơ B","Trinh thám và phá án.",60000),
            discoveryBook(3,"Bí Mật C","Câu chuyện bí ẩn.",70000),
            discoveryBook(4,"Bí Mật D","Câu chuyện ly kỳ.",80000),
            discoveryBook(5,"Bí Mật E","Câu chuyện mật mã.",90000),
            discoveryBook(6,"Chuyện Tình F","Tình yêu và gia đình.",90000)));
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"trinh thám\",\"limit\":3}"),
            new ChatTool.Context(request("s","Cho mình 6 cuốn sách trinh thám"),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L,3L,4L,5L);
        assertThat(result.answer()).contains("6 cuốn","2 cuốn đúng yêu cầu","3 cuốn có chủ đề","chưa đủ 6","Sách đúng yêu cầu","Gợi ý gần chủ đề");
        assertThat(state.discoveryExact).containsExactly(1L,2L);
        assertThat(state.discoveryNearby).containsExactly(3L,4L,5L);
    }
    @Test void defaultIsAtMostFiveAndExplicitNineIsNotCappedAtFiveOrEight() throws Exception {
        List<Product> catalog=new ArrayList<>();for(int i=1;i<=10;i++)catalog.add(book(i,50000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        assertThat(run("searchProducts","{\"query\":\"lập trình\",\"limit\":8}").products()).hasSize(5);
        var requested=registry.execute("recommendBooks",mapper.readTree("{\"query\":\"lập trình\",\"limit\":3}"),
            new ChatTool.Context(request("s","Cho mình chín cuốn sách lập trình"),state));
        assertThat(requested.products()).hasSize(9);
        assertThat(requested.answer()).contains("9 cuốn đúng yêu cầu");
    }
    @Test void exactOnlyPersistsUntilTheCustomerAllowsNearbyBooks() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám và phá án.",50000),
            discoveryBook(2,"Bí Mật B","Câu chuyện bí ẩn.",60000)));
        var first=registry.execute("searchProducts",mapper.readTree("{\"query\":\"trinh thám\"}"),
            new ChatTool.Context(request("s","Cho 6 cuốn trinh thám, chỉ đúng thể loại thôi"),state));
        assertThat(first.products()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(first.answer()).contains("chưa đủ 6").doesNotContain("Gợi ý gần chủ đề");
        var more=orchestrator.reply(request("s","Còn cuốn nào khác?"),state);
        assertThat(more.getProducts()).isEmpty();
        assertThat(more.getAnswer()).contains("chưa có cuốn nào khác");
        var expanded=orchestrator.reply(request("s","Còn cuốn nào khác tương tự? Có thể gợi ý gần chủ đề nhé."),state);
        assertThat(expanded.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(expanded.getAnswer()).contains("Gợi ý gần chủ đề");
    }
    @Test void nearbyRespectsPriceRatingStockAndTotalBudget() throws Exception {
        Product out=discoveryBook(5,"Bí Mật E","Bí ẩn.",10000);out.setQuantity(0);
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám.",40000),
            discoveryBook(2,"Bí Mật B","Bí ẩn.",30000),
            discoveryBook(3,"Bí Mật C","Bí ẩn.",110000),
            discoveryBook(4,"Bí Mật D","Bí ẩn.",20000),out,
            discoveryBook(6,"Bí Mật F","Bí ẩn.",35000)));
        when(rates.averageRatings()).thenReturn(List.of(new Object[]{1L,4.8},new Object[]{2L,4.5},new Object[]{3L,4.9},new Object[]{4L,4.0},new Object[]{5L,4.9},new Object[]{6L,4.8}));
        var result=registry.execute("recommendBooks",mapper.readTree("{\"query\":\"trinh thám\",\"minRating\":4.5,\"maxPrice\":100000,\"totalBudget\":80000}"),
            new ChatTool.Context(request("s","Chọn 6 cuốn trinh thám"),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(result.answer()).contains("70.000đ","chưa đủ 6");
    }
    @Test void explicitNewTopicInMoreUsesTheNewTopic() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám.",50000),
            discoveryBook(2,"Chuyện Tình B","Tình yêu và lãng mạn.",60000)));
        run("searchProducts","{\"query\":\"trinh thám\"}");
        plan("{\"tool\":\"searchProducts\",\"arguments\":{\"query\":\"trinh thám\",\"scope\":\"more\"}}");
        assertThat(orchestrator.reply(request("s","Gợi ý thêm vài cuốn về tình yêu, chuyển sang chủ đề này nhé."),state).getProducts())
            .extracting(ChatProductDto::getId).containsExactly(2L);
    }
    @Test void broadSelfHelpDoesNotMistakeProgrammingHabitsForAnExactMatch() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Thói Quen Nghề Lập Trình","Tư duy, thói quen nghề nghiệp cho kỹ sư phần mềm.",100000),
            discoveryBook(2,"Sống Bình An","Kỹ năng sống, vượt qua lo âu.",60000)));
        assertThat(run("searchProducts","{\"query\":\"phát triển bản thân\"}").products())
            .extracting(ChatProductDto::getId).containsExactly(2L);
    }
    @Test void nearbyAdventureDoesNotTreatEveryMetaphoricalJourneyAsRelated() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Dế Mèn","Cuộc phiêu lưu của Dế Mèn.",50000),
            discoveryBook(2,"Harry Potter","Khám phá thế giới phép thuật của phù thủy.",60000),
            discoveryBook(3,"Nhà Giả Kim","Hành trình đi tìm kho báu của một chàng chăn cừu.",70000),
            discoveryBook(4,"Không Gia Đình","Hành trình lang thang qua nhiều vùng đất.",80000),
            discoveryBook(5,"Sapiens","Hành trình phát triển của nhân loại từ thời tiền sử.",90000),
            discoveryBook(6,"Muôn Kiếp","Hành trình trải nghiệm của linh hồn qua nhiều kiếp sống.",90000)));
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"phiêu lưu\"}"),
            new ChatTool.Context(request("s","Giới thiệu 6 cuốn phiêu lưu"),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L,3L,4L);
        assertThat(result.answer()).contains("1 cuốn đúng yêu cầu","3 cuốn có chủ đề","chưa đủ 6");
    }
    @Test void moreWithoutANewCountStartsABatchOfAtMostFive() throws Exception {
        List<Product> catalog=new ArrayList<>();for(int i=1;i<=14;i++)catalog.add(book(i,50000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\"}"),
            new ChatTool.Context(request("s","Gợi ý 6 cuốn lập trình"),state));
        var more=orchestrator.reply(request("s","Còn cuốn nào khác?"),state);
        assertThat(more.getProducts()).hasSize(5);
        assertThat(more.getAnswer()).doesNotContain("Bạn muốn 6 cuốn");
    }
    @Test void nearbyUsesContentRatherThanWordsInTheAuthorNameOrGenericFamily() throws Exception {
        Product love=discoveryBook(1,"Mắt Biếc","Câu chuyện tình yêu lãng mạn.",50000);
        Product family=discoveryBook(2,"Không Gia Đình","Khát khao yêu thương và tình thân.",60000);
        Product falseMatch=discoveryBook(3,"Hồ Sơ Tội Phạm","Gia đình mafia tranh giành quyền lực.",70000);falseMatch.setAuthor("Tình Thân");
        Product friendship=discoveryBook(4,"Dế Mèn","Phiêu lưu, tình bạn và lòng dũng cảm.",80000);
        Product firstLove=discoveryBook(5,"Hoa Vàng","Những rung động đầu đời của tuổi thiếu niên.",90000);
        BookKnowledgeService knowledge=mock(BookKnowledgeService.class);data.setKnowledge(knowledge);
        when(knowledge.searchableText(any())).thenAnswer(call->{Product b=call.getArgument(0);return b.getName()+" "+b.getAuthor()+" "+b.getDescription();});
        when(knowledge.approvedDescription(any())).thenAnswer(call->((Product)call.getArgument(0)).getDescription());
        when(knowledge.rankByMeaning(anyString(),anyList())).thenAnswer(call->call.getArgument(1));
        when(products.findByStatusTrue()).thenReturn(List.of(love,family,falseMatch,friendship,firstLove));
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"tình yêu\"}"),
            new ChatTool.Context(request("s","Cho 6 cuốn về tình yêu"),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L,5L);
        assertThat(result.answer()).contains("tình thân","chưa đủ 6");
    }
    @Test void narrationDoesNotAddUnconfirmedSchoolAgeOrReadingDifficulty() throws Exception {
        var base=run("searchProducts","{\"query\":\"Python\",\"limit\":1}");
        plan("{\"reasons\":[{\"productId\":1,\"evidence\":\"Học lập trình Python cơ bản\",\"reason\":\"Phù hợp với học sinh tiểu học, văn phong nhẹ nhàng.\"}]}");
        assertThat(new ChatBookNarrator(ai,mapper,products,language).present(base,"Gợi ý Python",state)).isSameAs(base);
    }
    @Test void localModeUsesTheSameDiscoveryRulesAndDoesNotRepeat() {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám.",50000),
            discoveryBook(2,"Bí Mật B","Câu chuyện bí ẩn.",60000)));
        when(ai.isConfigured()).thenReturn(false);
        ChatService service=new ChatService(products,ai,language,data,new ChatPolicyService(language),orders,analytics,access);
        service.setTools(registry);
        var first=service.reply(request("local","Gợi ý 6 cuốn trinh thám"));
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(first.getAnswer()).contains("Gợi ý gần chủ đề","chưa đủ 6");
        service.reply(request("local","Phí giao hàng?"));
        var more=service.reply(request("local","Còn cuốn nào khác không?"));
        assertThat(more.getProducts()).isEmpty();
        assertThat(more.getAnswer()).contains("chưa có cuốn nào khác");
    }
    @Test void discoveryNarrationKeepsVerifiedGroupsFactsAndActions() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám và phá án.",50000),
            discoveryBook(2,"Bí Mật B","Câu chuyện bí ẩn và ly kỳ.",60000)));
        var base=registry.execute("searchProducts",mapper.readTree("{\"query\":\"trinh thám\"}"),
            new ChatTool.Context(request("s","Cho 6 cuốn trinh thám"),state));
        plan("{\"intro\":\"Nếu bạn thích theo dõi manh mối, mình sẽ giúp bạn cân nhắc các hướng đọc.\",\"reasons\":[{\"productId\":2,\"evidence\":\"Câu chuyện bí ẩn và ly kỳ\",\"reason\":\"Có yếu tố bí ẩn để bạn cân nhắc khi muốn mở rộng hướng đọc.\"}],\"closing\":\"Bạn muốn mình so sánh nội dung các lựa chọn này không?\"}");
        var narrated=new ChatBookNarrator(ai,mapper,products,language).present(base,"Cho 6 cuốn trinh thám",state);
        assertThat(narrated.answer()).contains("Nếu bạn thích","chưa đủ 6","Sách đúng yêu cầu","Gợi ý gần chủ đề","50.000đ","60.000đ","Có yếu tố bí ẩn","so sánh nội dung");
        assertThat(narrated.products()).isSameAs(base.products());assertThat(narrated.actions()).isSameAs(base.actions());
        plan("{\"intro\":\"Đã đặt hàng thành công\",\"reasons\":[]}");
        assertThat(new ChatBookNarrator(ai,mapper,products,language).present(base,"test",state)).isSameAs(base);
    }
    @Test void explicitSimilarityAnchorOverridesThePreviousTopicAndPrice() throws Exception {
        Product detective=discoveryBook(1,"Hồ Sơ A","Trinh thám.",30000);
        Product code=discoveryBook(2,"Clean Code","Viết mã nguồn và kỹ năng lập trình.",150000);
        Product practical=discoveryBook(3,"Pragmatic Programmer","Công cụ cho kỹ sư phần mềm và lập trình.",130000);
        when(products.findByStatusTrue()).thenReturn(List.of(detective,code,practical));
        run("searchProducts","{\"query\":\"trinh thám\",\"maxPrice\":50000}");
        var related=orchestrator.reply(request("s","Gợi ý thêm những cuốn khác tương tự Clean Code nhé."),state);
        assertThat(related.isFallback()).isFalse();
        assertThat(related.getExecution()).containsEntry("tool","findRelatedBooks");
        assertThat(related.getProducts()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(related.getAnswer()).contains("Clean Code","nội dung gần","lập trình");
        assertThat(state.criteria.getMaxPrice()).isNull();
        var more=orchestrator.reply(request("s","Còn cuốn nào khác cùng chủ đề không?"),state);
        assertThat(more.getProducts()).isEmpty();assertThat(more.getAnswer()).contains("Clean Code","cuốn nào khác");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void relatedBooksRetainTheirAnchorAcrossBatchesAndRespectExplicitPrice() {
        List<Product> catalog=new ArrayList<>();Product source=book(1,100000);source.setName("Clean Code");catalog.add(source);
        for(int i=2;i<=8;i++)catalog.add(book(i,i==8?200000:50000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        var first=orchestrator.reply(request("s","Gợi ý sách tương tự Clean Code, dưới 100k"),state);
        assertThat(first.getProducts()).hasSize(5);
        var more=orchestrator.reply(request("s","Còn cuốn nào khác cùng chủ đề?"),state);
        assertThat(more.getProducts()).extracting(ChatProductDto::getId).containsExactly(7L);
        assertThat(state.toolSearch).containsEntry("relatedSourceId",1L).containsEntry("maxPrice",100000L);
    }
    @Test void orTopicsAreAUnionWithTheSamePriceLimit() {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám.",30000),
            discoveryBook(2,"Mắt Biếc","Tình yêu và lãng mạn.",80000),
            discoveryBook(3,"Hồ Sơ Đắt","Trinh thám.",200000),
            discoveryBook(4,"Chuyện Tình Trinh Thám","Tình yêu và trinh thám.",90000),
            discoveryBook(5,"Lịch Sử","Lịch sử thế giới.",60000)));
        var result=orchestrator.reply(request("s","Gợi ý trinh thám hoặc tình yêu, mỗi cuốn dưới 150k, tối đa 5 cuốn"),state);
        assertThat(result.getProducts()).extracting(ChatProductDto::getId).containsExactlyInAnyOrder(1L,2L,4L);
        assertThat(result.getAnswer()).contains("3 cuốn phù hợp").doesNotContain("chưa đủ 5");
        assertThat(state.discoveryExact).containsExactlyInAnyOrder(1L,2L,4L);
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void separateTopicGroupsShareOneFiveBookBudget() {
        List<Product> catalog=new ArrayList<>();for(int i=1;i<=6;i++)catalog.add(book(i,50000));
        catalog.add(discoveryBook(7,"Hồ Sơ A","Trinh thám.",50000));catalog.add(discoveryBook(8,"Hồ Sơ B","Trinh thám.",50000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        var result=orchestrator.reply(request("s","Gợi ý vài cuốn lập trình và vài cuốn trinh thám, tổng cộng không quá 5 cuốn nhé."),state);
        assertThat(result.getProducts()).hasSize(5).extracting(ChatProductDto::getId).containsExactly(1L,7L,2L,8L,3L);
        assertThat(state.toolSearch.get("query").toString()).contains("hoac");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void aBookRequiredToCombineTopicsStillUsesIntersection() throws Exception {
        when(products.findByStatusTrue()).thenReturn(List.of(
            discoveryBook(1,"Hồ Sơ A","Trinh thám.",50000),discoveryBook(2,"Chuyện Tình B","Tình yêu.",50000),
            discoveryBook(3,"Kết Hợp","Trinh thám và tình yêu.",50000)));
        assertThat(run("searchProducts","{\"query\":\"trinh thám và tình yêu\"}").products())
            .extracting(ChatProductDto::getId).containsExactly(3L);
    }
    @Test void strictPythonCannotBeExpandedToJavaOrGeneralProgramming() throws Exception {
        Product java=book(1,50000);java.setName("Java Cơ Bản");java.setDescription("Học lập trình Java.");
        Product general=book(2,50000);general.setName("Clean Code");general.setDescription("Viết mã nguồn sạch.");
        when(products.findByStatusTrue()).thenReturn(List.of(java,general));
        var first=registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\"}"),
            new ChatTool.Context(request("s","Gợi ý sách lập trình, không lấy Java, chỉ lấy Python nhé."),state));
        assertThat(first.products()).isEmpty();assertThat(state.toolSearch).containsEntry("match","exact_only");
        assertThat(orchestrator.reply(request("s","Còn cuốn nào khác không?"),state).getProducts()).isEmpty();
    }
    @Test void exclusionsAlsoApplyToNearbyBooksAndSurviveContinuation() throws Exception {
        Product java=book(1,50000);java.setName("Java Cơ Bản");java.setDescription("Học lập trình Java.");
        Product general=book(2,50000);general.setName("Clean Code");general.setDescription("Viết mã nguồn sạch và lập trình.");
        when(products.findByStatusTrue()).thenReturn(List.of(java,general));
        var first=registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\"}"),
            new ChatTool.Context(request("s","Gợi ý sách Python, không lấy Java; có thể gợi ý gần chủ đề."),state));
        assertThat(first.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(state.toolSearch.get("excludedLanguages")).isEqualTo(List.of("java"));
        assertThat(orchestrator.reply(request("s","Còn cuốn nào khác?"),state).getProducts()).isEmpty();
    }
    @Test void clearingPriceKeepsExplicitBookCountEvenIfThePlannerSaysNew() throws Exception {
        List<Product> catalog=new ArrayList<>();for(int i=1;i<=8;i++)catalog.add(book(i,i<=3?50000:150000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\",\"maxPrice\":100000}"),
            new ChatTool.Context(request("s","Gợi ý 6 cuốn lập trình dưới 100k"),state));
        var expanded=registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\",\"scope\":\"new\",\"maxPrice\":100000}"),
            new ChatTool.Context(request("s","Bỏ giới hạn giá, giữ nguyên chủ đề lập trình nhé."),state));
        assertThat(expanded.products()).hasSize(6);assertThat(state.criteria.getMaxPrice()).isNull();
        assertThat(state.discoveryRequestedCount).isEqualTo(6);
    }
    @Test void narratorCannotAddPythonApplicabilityUnsupportedByDescription() throws Exception {
        var base=run("searchProducts","{\"query\":\"Python\",\"limit\":1}");
        Product general=book(1,50000);general.setDescription("Cách viết mã nguồn sạch và dễ bảo trì.");
        when(products.findByStatusTrue()).thenReturn(List.of(general));
        plan("{\"reasons\":[{\"productId\":1,\"evidence\":\"Cách viết mã nguồn sạch\",\"reason\":\"Áp dụng cho bất kỳ ngôn ngữ nào, bao gồm Python.\"}]}");
        assertThat(new ChatBookNarrator(ai,mapper,products,language).present(base,"Gợi ý Python",state)).isSameAs(base);
    }
    @Test void narratorDoesNotGreetAgainOrAddAnAvailabilityClaim() throws Exception {
        var base=run("searchProducts","{\"query\":\"Python\",\"limit\":1}");
        state.toolHistory.add(Map.of("user","Gợi ý sách","tool","searchProducts","status","COMPLETED"));
        plan("{\"intro\":\"Chào bạn, mình sẽ giúp bạn chọn sách.\",\"reasons\":[]}");
        ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        assertThat(narrator.present(base,"Còn cuốn nào khác?",state)).isSameAs(base);
        plan("{\"intro\":\"Mình chưa tìm thấy sách trong danh mục.\",\"reasons\":[]}");
        assertThat(narrator.present(base,"Gợi ý sách",state)).isSameAs(base);
    }
    @Test void narratorDoesNotAddUnconfirmedCharacterDevelopmentOrNarrativePace() throws Exception {
        Product detective=discoveryBook(1,"Hồ Sơ A","Truyện trinh thám về những vụ án hóc búa.",50000);
        when(products.findByStatusTrue()).thenReturn(List.of(detective));
        var base=run("searchProducts","{\"query\":\"trinh thám\"}");
        plan("{\"reasons\":[{\"productId\":1,\"evidence\":\"những vụ án hóc búa\",\"reason\":\"Lối kể chuyện nhanh, kịch tính, gắn với quá trình trưởng thành của nhân vật.\"}]}");
        assertThat(new ChatBookNarrator(ai,mapper,products,language).present(base,"Gợi ý trinh thám",state)).isSameAs(base);
    }
    @Test void localModeRespectsAnExplicitSimilarityAnchor() {
        Product code=book(1,50000);code.setName("Clean Code");code.setDescription("Viết mã nguồn và lập trình.");
        when(products.findByStatusTrue()).thenReturn(List.of(code,book(2,60000)));
        when(ai.isConfigured()).thenReturn(false);
        ChatService service=new ChatService(products,ai,language,data,new ChatPolicyService(language),orders,analytics,access);
        service.setTools(registry);
        var result=service.reply(request("anchor-local","Gợi ý thêm những cuốn khác tương tự Clean Code"));
        assertThat(result.getProducts()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(result.getExecution()).containsEntry("tool","findRelatedBooks");
    }
    @Test void similarityRecommendationsAlsoRetainTheJavaExclusion() {
        Product code=book(1,50000);code.setName("Clean Code");code.setDescription("Viết mã nguồn và lập trình.");
        Product java=book(2,50000);java.setName("Java Cơ Bản");java.setDescription("Học lập trình Java.");
        when(products.findByStatusTrue()).thenReturn(List.of(code,java,book(3,60000)));
        var first=orchestrator.reply(request("s","Gợi ý sách tương tự Clean Code, không lấy Java nhé."),state);
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(orchestrator.reply(request("s","Còn cuốn nào khác cùng chủ đề?"),state).getProducts()).isEmpty();
        assertThat(state.toolSearch.get("excludedLanguages")).isEqualTo(List.of("java"));
    }
    @Test void pythonPreferenceDoesNotBecomeAMandatoryLanguageAndExclusionStillWins() throws Exception {
        Product general=book(2,70000);general.setName("Clean Code");general.setDescription("Viết mã nguồn và lập trình.");
        Product java=book(3,60000);java.setName("Java căn bản");java.setDescription("Lập trình Java.");
        when(products.findByStatusTrue()).thenReturn(List.of(general,java,book(1,100000)));
        var result=registry.execute("recommendBooks",mapper.readTree("{\"query\":\"lập trình Python\",\"preferredQuery\":\"Python\",\"excludedQuery\":\"Java\"}"),
            new ChatTool.Context(request("s","Gợi ý 3 cuốn lập trình, ưu tiên Python, không lấy Java."),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(result.answer()).contains("ưu tiên","Python","chưa đủ 3");
        assertThat(state.toolSearch.get("query").toString()).doesNotContain("python");
    }
    @Test void preferenceCannotReduceTheAffordableBookCount() throws Exception {
        Product preferred=book(1,200000);
        Product general=book(2,80000);general.setName("Clean Code");general.setDescription("Lập trình, viết mã nguồn.");
        Product other=book(3,70000);other.setName("Thuật toán");other.setDescription("Lập trình thuật toán.");
        when(products.findByStatusTrue()).thenReturn(List.of(preferred,general,other));
        var result=registry.execute("recommendBooks",mapper.readTree("{\"query\":\"lập trình\",\"preferredQuery\":\"Python\",\"totalBudget\":160000}"),
            new ChatTool.Context(request("s","Chọn 2 cuốn lập trình, ưu tiên Python, tổng dưới 160k."),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L,2L);
        assertThat(result.answer()).contains("150.000đ","chưa có cuốn đáp ứng phần ưu tiên");
    }
    @Test void omittedTotalBudgetIsRecoveredWithoutTreatingItAsThePriceOfEachBook() throws Exception {
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\",\"maxPrice\":200000}"),
            new ChatTool.Context(request("s","Chọn 2 cuốn Python, tổng dưới 200k."),state));
        assertThat(state.recommendationBudget).isEqualTo(200000);
        assertThat(state.toolSearch).doesNotContainKey("maxPrice");
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L,2L);
    }
    @Test void totalBookCountIsNotMisreadAsTotalBudget() throws Exception {
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\",\"maxPrice\":100000}"),
            new ChatTool.Context(request("s","Tìm tổng cộng 2 cuốn Python, mỗi cuốn dưới 100k."),state));
        assertThat(state.recommendationBudget).isNull();
        assertThat(state.criteria.getMaxPrice()).isEqualTo(100000);
    }
    @Test void clearingPreferenceRetainsMandatoryTopicPriceAndCount() throws Exception {
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\",\"maxPrice\":100000}"),
            new ChatTool.Context(request("s","Gợi ý 6 cuốn lập trình dưới 100k, ưu tiên Python."),state));
        registry.execute("searchProducts",mapper.readTree("{\"scope\":\"continue\",\"preferredQuery\":null}"),
            new ChatTool.Context(request("s","Bỏ ưu tiên Python, giữ yêu cầu cũ."),state));
        assertThat(state.toolSearch).doesNotContainKey("preferredQuery").containsEntry("query","lap trinh");
        assertThat(state.discoveryRequestedCount).isEqualTo(6);assertThat(state.criteria.getMaxPrice()).isEqualTo(100000);
    }
    @Test void clearingExclusionDoesNotImmediatelyReintroduceIt() throws Exception {
        Product java=book(3,50000);java.setName("Java căn bản");java.setDescription("Lập trình Java.");
        when(products.findByStatusTrue()).thenReturn(List.of(book(1,50000),java));
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\"}"),
            new ChatTool.Context(request("s","Tìm sách lập trình, không lấy Java."),state));
        var result=registry.execute("searchProducts",mapper.readTree("{\"scope\":\"continue\",\"excludedQuery\":null}"),
            new ChatTool.Context(request("s","Bỏ loại trừ Java, tìm lại với yêu cầu cũ."),state));
        assertThat(state.toolSearch).doesNotContainKeys("excludedQuery","excludedLanguages");
        assertThat(result.products()).extracting(ChatProductDto::getId).contains(3L);
    }
    @Test void plannerCannotInventReadingPurposeOrRecipient() throws Exception {
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"Python\",\"purpose\":\"ôn thi\",\"recipient\":\"bé 8 tuổi\",\"preferredQuery\":\"Java\"}"),
            new ChatTool.Context(request("s","Tìm sách Python."),state));
        assertThat(state.toolSearch).doesNotContainKeys("purpose","recipient","preferredQuery");
    }
    @Test void complexReadingRequestUsesThePlannerInsteadOfTheSimplePriceRoute() {
        plan("{\"tool\":\"recommendBooks\",\"arguments\":{\"query\":\"lập trình\",\"preferredQuery\":\"Python\",\"totalBudget\":200000}}");
        var result=orchestrator.reply(request("s","Tìm 2 cuốn lập trình, ưu tiên Python, tổng dưới 200k."),state);
        assertThat(result.isFallback()).isFalse();assertThat(state.recommendationBudget).isEqualTo(200000);
        verify(ai).complete(contains("readingRequest"),anyList(),contains("ưu tiên Python"));
    }
    @Test void unknownOrdinaryTopicDoesNotSilentlyReturnTheCatalog() throws Exception {
        assertThat(run("searchProducts","{\"query\":\"nuôi ong lấy mật\"}").products()).isEmpty();
    }
    @Test void emptyNarrationKeepsTheVerifiedOutcomeAndTheCurrentPriceConstraint() throws Exception {
        var base=run("searchProducts","{\"query\":\"nuôi ong\",\"maxPrice\":50000}");
        state.recentDialogue.add(Map.of("user","Tìm sách nuôi ong dưới 50k","assistant","Mình đang tìm theo nhu cầu đó."));
        plan("{\"reasons\":[],\"closing\":\"Bạn có muốn mình thử nới khoảng giá đang chọn không?\"}");
        var result=new ChatBookNarrator(ai,mapper,products,language).present(base,"Còn cuốn nào khác?",state);
        assertThat(result.answer()).contains("chưa tìm được sách","nới điều kiện giá").doesNotContain("Khi bạn muốn chuyển");
        assertThat(result.products()).isEmpty();assertThat(result.status()).isEqualTo("COMPLETED");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void vectorRecallMustRespectPriceStockAndTopicExclusionAndRemainLabelledNearby() throws Exception {
        Product eligible=discoveryBook(1,"Tập Trung","Rèn sự tập trung trong công việc.",50000);
        Product expensive=discoveryBook(2,"Quản Lý Thời Gian","Rèn thói quen.",200000);
        Product outOfStock=discoveryBook(3,"Chuyên Tâm","Rèn thói quen.",50000);outOfStock.setQuantity(0);
        Product excluded=discoveryBook(4,"Thiền","Thiền định để tập trung.",50000);
        when(products.findByStatusTrue()).thenReturn(List.of(eligible,expensive,outOfStock,excluded));
        BookKnowledgeService knowledge=mock(BookKnowledgeService.class);data.setKnowledge(knowledge);
        when(knowledge.searchableText(any())).thenAnswer(call->{Product p=call.getArgument(0);return p.getName()+" "+p.getDescription();});
        when(knowledge.approvedDescription(any())).thenAnswer(call->((Product)call.getArgument(0)).getDescription());
        when(knowledge.rankByMeaning(anyString(),anyList())).thenAnswer(call->call.getArgument(1));
        when(knowledge.semanticEvidence(anyString(),anyList())).thenAnswer(call->{
            List<Product> candidates=call.getArgument(1);Map<Long,String> evidence=new LinkedHashMap<>();
            candidates.forEach(p->evidence.put(p.getProductId(),"Hồ sơ đã duyệt: "+p.getDescription()));return evidence;
        });
        var result=registry.execute("searchProducts",mapper.readTree("{\"query\":\"hay xao nhãng khi làm việc\",\"maxPrice\":100000,\"excludedQuery\":\"thiền\"}"),
            new ChatTool.Context(request("s","Tìm sách vì hay xao nhãng khi làm việc, dưới 100k, không lấy thiền."),state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(state.discoveryExact).isEmpty();assertThat(state.discoveryNearby).containsExactly(1L);
        assertThat(result.answer()).contains("chỉ đáp ứng một phần","Hồ sơ đã duyệt");
        verify(knowledge).semanticEvidence(anyString(),argThat(books->books.size()==1 && books.get(0).getProductId().equals(1L)));
    }
    @Test void localFallbackKeepsSoftPreferenceSeparateFromMandatoryTopic() {
        Product general=book(2,70000);general.setName("Clean Code");general.setDescription("Lập trình, viết mã nguồn.");
        when(products.findByStatusTrue()).thenReturn(List.of(book(1,90000),general));when(ai.isConfigured()).thenReturn(false);
        ChatService service=new ChatService(products,ai,language,data,new ChatPolicyService(language),orders,analytics,access);service.setTools(registry);
        var result=service.reply(request("soft-local","Tìm 2 cuốn lập trình, ưu tiên Python, tổng dưới 170k."));
        assertThat(result.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(result.getAnswer()).contains("160.000đ","ưu tiên");
    }
    @Test void clearingOnlyPreferenceKeepsExclusionEvenWithWrongNewScopeAndNullExclusion() throws Exception {
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\",\"maxPrice\":100000}"),
            new ChatTool.Context(request("s","Tìm 3 cuốn lập trình, ưu tiên Python, không lấy Java, dưới 100k."),state));
        registry.execute("searchProducts",mapper.readTree("{\"scope\":\"new\",\"query\":\"lập trình\",\"preferredQuery\":null,\"excludedQuery\":null}"),
            new ChatTool.Context(request("s","Bỏ ưu tiên Python, giữ các điều kiện còn lại và tìm lại."),state));
        assertThat(state.toolSearch).containsEntry("excludedQuery","java").containsEntry("maxPrice",100000);
        assertThat(state.discoveryRequestedCount).isEqualTo(3);assertThat(state.toolSearch).doesNotContainKey("preferredQuery");
    }
    @Test void budgetIncreaseAndReferenceToOldPreferenceDoNotResetTheReadingRequest() throws Exception {
        registry.execute("recommendBooks",mapper.readTree("{\"query\":\"lập trình\",\"totalBudget\":200000}"),
            new ChatTool.Context(request("s","Chọn 3 cuốn lập trình, ưu tiên Python, không lấy Java, tổng dưới 200k."),state));
        registry.execute("recommendBooks",mapper.readTree("{\"scope\":\"new\",\"query\":\"lập trình\",\"totalBudget\":400000}"),
            new ChatTool.Context(request("s","Tăng tổng ngân sách lên 400k, chọn 4 cuốn."),state));
        assertThat(state.toolSearch).containsEntry("preferredQuery","python").containsEntry("excludedQuery","java");
        registry.execute("searchProducts",mapper.readTree("{\"scope\":\"new\",\"preferredQuery\":\"cũ\",\"totalBudget\":null}"),
            new ChatTool.Context(request("s","Bỏ giới hạn tổng ngân sách, giữ chủ đề và ưu tiên cũ."),state));
        assertThat(state.recommendationBudget).isNull();assertThat(state.discoveryRequestedCount).isEqualTo(4);
        assertThat(state.toolSearch).containsEntry("preferredQuery","python").containsEntry("excludedQuery","java");
    }
    @Test void explicitOnlyPythonSurvivesAnIncorrectlyBroadPlannerQueryOnTheNextTurn() throws Exception {
        registry.execute("searchProducts",mapper.readTree("{\"query\":\"lập trình\",\"preferredQuery\":\"Python\"}"),
            new ChatTool.Context(request("s","Gợi ý 6 cuốn lập trình, chỉ lấy Python, không lấy Java và không gợi ý gần chủ đề."),state));
        assertThat(state.toolSearch.get("query").toString()).contains("python");assertThat(state.toolSearch).doesNotContainKey("preferredQuery");
        var next=orchestrator.reply(request("s","Còn cuốn nào khác không?"),state);
        assertThat(next.getProducts()).isEmpty();
    }
    @Test void mixedDiscoveryAndShippingDoesNotTurnPerBookPriceIntoAnOrderSubtotal() throws Exception {
        var result=registry.execute("getPolicy",mapper.createObjectNode(),new ChatTool.Context(
            request("s","Gợi ý sách lập trình, mỗi cuốn dưới 150k, và cho mình biết phí giao hàng."),state));
        assertThat(result.answer()).contains("20.000đ","300.000đ").doesNotContain("Tiền sách 150.000đ","tổng thanh toán");
    }
    @Test void narratorCannotSuggestRemovingAnUnspecifiedLengthConstraint() throws Exception {
        var base=run("searchProducts","{\"query\":\"nuôi ong\"}");
        plan("{\"reasons\":[],\"intro\":\"Bạn có thể nới lỏng yêu cầu về độ dài để mình có thêm nhiều gợi ý.\"}");
        assertThat(new ChatBookNarrator(ai,mapper,products,language).present(base,"Còn cuốn nào khác?",state)).isSameAs(base);
    }
    @Test void aReadingGoalUsesTheWholeEligibleCatalogInsteadOfAnIncidentalTitlePhrase() {
        Product lean=discoveryBook(1,"Khởi Nghiệp Tinh Gọn","Khởi nghiệp bằng thử nghiệm ý tưởng doanh nghiệp.",80000);
        Product law=discoveryBook(2,"Luật Doanh Nghiệp","Pháp luật cho doanh nghiệp và người khởi nghiệp.",70000);
        when(products.findByStatusTrue()).thenReturn(List.of(lean,law));
        BookKnowledgeService knowledge=mock(BookKnowledgeService.class);data.setKnowledge(knowledge);
        when(knowledge.searchableText(any())).thenAnswer(call->{Product p=call.getArgument(0);return p.getName()+" "+p.getDescription();});
        when(knowledge.rankByMeaning(anyString(),anyList())).thenAnswer(call->call.getArgument(1));
        when(knowledge.semanticRecall(anyString(),anyList())).thenReturn(new BookKnowledgeService.SemanticRecall(true,Map.of(1L,"Thử nghiệm ý tưởng")));
        var result=orchestrator.reply(request("s","Mình muốn biết cách kiểm chứng ý tưởng trước khi bỏ nhiều tiền mở một doanh nghiệp."),state);
        assertThat(result.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        verify(knowledge).semanticRecall(anyString(),argThat(books->books.size()==2));
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void clearingReadingConstraintsUsesAPatchEvenIfThePlannerWouldFail() throws Exception {
        run("recommendBooks","{\"query\":\"Python\",\"totalBudget\":100000}");
        when(ai.complete(anyString(),anyList(),anyString())).thenThrow(new IllegalStateException("unavailable"));
        var result=orchestrator.reply(request("s","Bỏ giới hạn tổng ngân sách, giữ chủ đề và ưu tiên cũ."),state);
        assertThat(result.isFallback()).isFalse();assertThat(state.recommendationBudget).isNull();
        assertThat(result.getProducts()).hasSize(3);
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void semanticGoalMatchComesBeforeGeneralNeighborButExplicitPriceOrderStillWins() {
        Product broad=discoveryBook(1,"Quản Trị","Các nguyên lý quản trị doanh nghiệp.",40000);
        Product specific=discoveryBook(2,"Tinh Gọn","Thử nghiệm startup trước khi đầu tư.",90000);
        BookKnowledgeService knowledge=mock(BookKnowledgeService.class);data.setKnowledge(knowledge);
        when(knowledge.searchableText(any())).thenAnswer(call->((Product)call.getArgument(0)).getDescription());
        when(knowledge.approvedDescription(any())).thenAnswer(call->((Product)call.getArgument(0)).getDescription());
        when(knowledge.rankByMeaning(anyString(),anyList())).thenAnswer(call->call.getArgument(1));
        when(knowledge.semanticEvidence(anyString(),anyList())).thenReturn(Map.of(2L,"Hồ sơ đã duyệt: Thử nghiệm startup"));
        ChatCatalogQuery query=new ChatCatalogQuery(language,data);
        assertThat(query.nearby("kiểm chứng ý tưởng",ChatSearchCriteriaDto.empty(),List.of(broad,specific),List.of(),Set.of()))
            .extracting(n->n.book().getProductId()).containsExactly(2L,1L);
        var price=new ChatSearchCriteriaDto(null,null,"","","price-asc",List.of());
        assertThat(query.nearby("kiểm chứng ý tưởng",price,List.of(broad,specific),List.of(),Set.of()))
            .extracting(n->n.book().getProductId()).containsExactly(1L,2L);
    }
    private Product discoveryBook(long id,String name,String description,double price) {
        Product product=book(id,price);product.setName(name);product.setDescription(description);
        product.setCategory(new Category(3L,"Tiểu thuyết"));return product;
    }
    private void plan(String p){when(ai.complete(anyString(),anyList(),anyString())).thenReturn(p);}
    private ChatRequestDto request(String session,String message){ChatRequestDto r=new ChatRequestDto();r.setSessionId(session);r.setMessage(message);return r;}
    private Product book(long id,double price){return new Product(id,"Python "+id,"Tác giả",10,price,0,"","Học lập trình Python cơ bản cho người mới.",LocalDate.now(),true,10,new Category(1L,"Giáo trình"));}
}
