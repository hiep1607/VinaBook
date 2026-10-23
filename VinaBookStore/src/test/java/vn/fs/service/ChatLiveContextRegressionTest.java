package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Conversation failures observed in the real 50-scenario run. No provider or DB writes. */
class ChatLiveContextRegressionTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ChatIntentService language=new ChatIntentService();
    final AiGateway ai=mock(AiGateway.class);
    final ProductRepository products=mock(ProductRepository.class);
    final RateRepository rates=mock(RateRepository.class);
    final ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    final List<Product> catalog=List.of(book(54,"Muôn Kiếp Nhân Sinh"),book(57,"Đắc Nhân Tâm"));
    ChatStructuredPipeline pipeline;
    String nextTool="getProducts",nextIntent="PRODUCT_INFO",nextMode="current";
    Map<String,Object> nextArgs=Map.of("reference","focus","field","detail"),nextSet=Map.of();
    boolean providerFailure;
    JsonNode input;

    @BeforeEach void setup() throws Exception {
        when(products.findByStatusTrue()).thenReturn(catalog);
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("test");when(ai.isConfigured()).thenReturn(true);
        when(rates.findByProductOrderByIdDesc(any())).thenReturn(List.of());
        var data=new ChatRetrievalService(language);
        var tools=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        tools.setRates(rates);
        var registry=new ChatToolRegistry(List.of(tools.getProducts(),tools.bookReviews(),tools.getPolicy(),tools.respond(),tools.recommendBooks(),tools.searchProducts()));
        var analytics=mock(ChatAnalyticsService.class);
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("test-message");
        pipeline=new ChatStructuredPipeline(ai,mapper,new ChatTurnUnderstanding(ai,mapper,registry),registry,
            new ChatBookNarrator(ai,mapper,products,language),analytics,products,data);
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenAnswer(call->{
            input=mapper.readTree((String)call.getArgument(2));
            if(providerFailure)throw new IllegalStateException("provider unavailable");
            if(input.has("question"))return "{\"intro\":\"\",\"closing\":\"\",\"reasons\":[],\"preferredProductId\":null}";
            ObjectNode plan=mapper.createObjectNode().put("contextVersion",input.path("contextVersion").asLong())
                .put("intent",nextIntent).put("contextMode",nextMode);
            plan.set("set",mapper.valueToTree(nextSet));plan.set("clear",mapper.createArrayNode());
            plan.put("evidence",ChatTurnContext.CURRENT_MESSAGE);plan.putNull("memoryNote");
            plan.set("calls",mapper.valueToTree(List.of(Map.of("tool",nextTool,"arguments",nextArgs))));
            return mapper.writeValueAsString(plan);
        });
    }

    static Product book(long id,String title) {
        return new Product(id,title,"Tác giả",220,86000.0,18,"cover", "Nội dung sách đã được duyệt.",
            LocalDate.now(),true,0,new Category(1L,"Sách"));
    }
    ChatResponseDto send(String text) {
        var request=new ChatRequestDto();request.setMessage(text);request.setSessionId("context-regression");
        request.setRequestId(UUID.randomUUID().toString());return pipeline.reply(request,state);
    }
    void readReviews() {
        nextTool="bookReviews";nextArgs=Map.of("title","Muôn Kiếp Nhân Sinh","mode","read");
        var response=send("Muôn Kiếp Nhân Sinh mấy sao, mọi người nhận xét gì?");
        assertThat(response.isFallback()).isEqualTo(providerFailure);assertThat(state.bookReadContext).containsEntry("mode","read");
    }

    @Test void choosesSecondBookAfterPolicyWithoutLosingOrderOrPreparingPurchase() {
        state.initialized=true;state.shortlist.addAll(List.of(54L,57L));state.displayed.addAll(state.shortlist);
        state.toolSearch.put("maxPrice",100000);
        nextTool="getPolicy";nextIntent="POLICY";nextArgs=Map.of();
        assertThat(send("Phí giao hàng bao nhiêu?").isFallback()).isEqualTo(providerFailure);
        providerFailure=true;
        var response=send("Quay lại mấy cuốn lúc nãy, mình chọn cuốn thứ hai.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L);
        assertThat(state.focus).isEqualTo(57L);assertThat(state.shortlist).containsExactly(54L,57L);
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        assertThat(response.getActions()).noneMatch(a->a.getType().equals("CHECKOUT_PRODUCT"));
    }

    void contentSelection() {
        state.initialized=true;state.shortlist.addAll(List.of(54L,57L));state.displayed.addAll(state.shortlist);
        state.lastTask="DISCOVER_BOOKS";state.toolSearch.put("maxPrice",100000);
        // If planning runs, it proposes the very bug: starting another discovery.
        nextTool="recommendBooks";nextIntent="DISCOVER_BOOKS";nextMode="new";nextArgs=Map.of();
        providerFailure=true;
    }

    @ParameterizedTest @ValueSource(strings={"cụ thể hơn","nói kỹ hơn","chi tiết hơn","đi sâu hơn","Bạn nói rõ hơn về nội dung đi"})
    void contentElaborationKeepsCurrentBooksEvenWhenProviderCannotPlan(String message) {
        contentSelection();
        var response=send(message);
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getExecution()).containsEntry("tool","getProducts");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(54L,57L);
        assertThat(response.getAnswer()).contains("Nội dung sách đã được duyệt","chưa có đủ dữ liệu")
            .doesNotContain("Mình chọn trước","70.520");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        assertThat(state.shortlist).containsExactly(54L,57L);
        assertThat(state.explanationRound).isEqualTo(1);
    }

    @Test void contentElaborationUsesFocusedBookAndCanExplicitlySwitchToSecondBook() throws Exception {
        contentSelection();state.focus=54L;
        var first=send("cụ thể hơn");
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(54L);
        var restored=mapper.readValue(mapper.writeValueAsBytes(state),ChatDialogueMemory.State.class);
        assertThat(restored.focus).isEqualTo(54L);assertThat(restored.shortlist).containsExactly(54L,57L);
        mapper.readerForUpdating(state).readValue(mapper.writeValueAsBytes(restored));
        var second=send("Nói kỹ hơn về cuốn thứ hai.");
        assertThat(second.isFallback()).isEqualTo(providerFailure);
        assertThat(second.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L);
        assertThat(state.focus).isEqualTo(57L);
        var again=send("chi tiết hơn");
        assertThat(again.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L);
        assertThat(state.shortlist).containsExactly(54L,57L);
        var other=send("Còn cuốn kia thì sao?");
        assertThat(other.isFallback()).isEqualTo(providerFailure);
        assertThat(other.getProducts()).extracting(ChatProductDto::getId).containsExactly(54L);
        assertThat(state.bookReadContext).containsEntry("field","explanation");
    }

    @Test void explicitContentFollowupReturnsToBookAfterShippingInsteadOfDiscoveringNewBooks() {
        contentSelection();state.focus=54L;state.lastTask="POLICY";
        var response=send("Quay lại sách lúc nãy, nói sâu hơn về nội dung cuốn đó.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getExecution()).containsEntry("tool","getProducts");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(54L);
    }

    @Test void contentFollowupDoesNotStealNewRecommendationsPolicyNegationOrMenus() {
        contentSelection();
        for(String message:List.of("Cho mình sách khác nữa.","Gợi ý sách có nội dung cụ thể hơn.",
                "Đừng nói kỹ hơn, tìm cuốn khác.","Cụ thể hơn về phí giao hàng.","Tìm sách dưới 50k.")) {
            var request=new ChatRequestDto();request.setMessage(message);
            assertThat(ChatLiveContext.plan(request,state,List.of(),mapper)).as(message).isNull();
        }
        var bare=new ChatRequestDto();bare.setMessage("cụ thể hơn");
        state.lastTask="POLICY";
        assertThat(ChatLiveContext.plan(bare,state,List.of(),mapper)).isNull();
        state.lastTask="DISCOVER_BOOKS";state.topicSuggestions.add("Truyện thư giãn");
        assertThat(ChatLiveContext.plan(bare,state,List.of(),mapper)).isNull();
    }

    @Test void elaborationWithoutAnyBookAsksForReferenceWithoutFetchingNewBooks() {
        state.initialized=true;providerFailure=true;
        var response=send("cụ thể hơn");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getProducts()).isEmpty();
        assertThat(response.getExecution()).containsEntry("tool","getProducts").containsEntry("status","NEEDS_INPUT");
        assertThat(response.getAnswer()).contains("cuốn");
    }

    @Test void introducingBookContentIsNotMistakenForCheckingWhetherItMentionsItsOwnTitle() {
        nextArgs=Map.of("title","Muôn Kiếp Nhân Sinh","field","detail");
        var response=send("Giới thiệu nội dung cuốn Muôn Kiếp Nhân Sinh.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getAnswer()).contains("Nội dung sách đã được duyệt")
            .doesNotContain("có đề cập đến nội dung đó");
    }

    @Test void genericNewBookFollowupKeepsReviewReadPurposeAcrossPolicyInterruption() {
        readReviews();nextTool="getPolicy";nextIntent="POLICY";nextArgs=Map.of();
        send("Phí vận chuyển bao nhiêu?");providerFailure=true;
        var response=send("Còn Đắc Nhân Tâm thì sao?");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getAnswer()).contains("Đắc Nhân Tâm","chưa có nhận xét hợp lệ").doesNotContain("nội dung đó");
        assertThat(response.getExecution()).containsEntry("tool","bookReviews");
        verify(rates).findByProductOrderByIdDesc(catalog.get(1));
    }

    @Test void ambiguousReviewRequestClarifiesReadOrWriteWithoutOpeningWriteForm() {
        state.initialized=true;state.focus=57L;state.displayed.add(57L);state.shortlist.add(57L);
        // A provider proposing write cannot turn an ambiguous request into authorization.
        nextTool="bookReviews";nextArgs=Map.of("reference","focus","mode","write");
        var response=send("Đánh giá cuốn này.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getAnswer()).contains("xem nhận xét","tự viết đánh giá");
        assertThat(response.getActions()).noneMatch(a->Set.of("WRITE_REVIEW","LOGIN").contains(a.getType()));
        assertThat(state.pendingReviewMode).isEqualTo("clarify");
        providerFailure=true;
        var read=send("Xem nhận xét nhé");
        assertThat(read.isFallback()).isEqualTo(providerFailure);
        assertThat(read.getAnswer()).contains("Đắc Nhân Tâm","chưa có nhận xét hợp lệ");
        assertThat(state.pendingReviewMode).isEmpty();
    }

    @Test void failedProviderDoesNotDiscardLastCompletedBookReadPurpose() {
        readReviews();var before=new LinkedHashMap<>(state.bookReadContext);long version=state.contextVersion;
        providerFailure=true;
        var failure=send("Tôi muốn biết thêm vài thông tin chưa rõ.");
        assertThat(failure.isFallback()).isTrue();assertThat(state.contextVersion)
            .as("answer=%s execution=%s filters=%s",failure.getAnswer(),failure.getExecution(),state.toolSearch).isEqualTo(version);
        assertThat(state.bookReadContext).isEqualTo(before);
        assertThat(send("Còn Đắc Nhân Tâm thì sao?").getExecution()).containsEntry("tool","bookReviews");
    }

    @Test void readOnlyComparisonIgnoresPlannerManufacturedDiscoveryPatch() {
        nextTool="getProducts";nextArgs=Map.of("field","table","reference","selection");
        nextIntent="COMPARE_BOOKS";nextMode="new";nextSet=Map.of("title","Muôn Kiếp Nhân Sinh");
        state.toolSearch.put("maxPrice",150000);
        var response=send("So sánh Muôn Kiếp Nhân Sinh và Đắc Nhân Tâm.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(54L,57L);
        assertThat(state.toolSearch).containsEntry("maxPrice",150000).doesNotContainKey("title");
    }

    @Test void failedExplicitComparisonPairCanBeRecoveredFromUserTextOnNextTurn() {
        state.initialized=true;
        state.recentDialogue.add(Map.of("user","So sánh Đắc Nhân Tâm và Muôn Kiếp Nhân Sinh.","assistant","Đang gặp trục trặc, thử lại nhé."));
        providerFailure=true;
        var response=send("So sánh hai cuốn đó giúp mình, chưa cần mua.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(57L,54L);
        assertThat(state.comparisonProducts).containsExactly(57L,54L);
    }

    @Test void assistantOnlyTitlesCannotSupplyAComparisonPair() {
        state.initialized=true;
        state.recentDialogue.add(Map.of("user","So sánh hai cuốn đó.","assistant","Muôn Kiếp Nhân Sinh và Đắc Nhân Tâm"));
        providerFailure=true;
        assertThat(send("So sánh hai cuốn đó giúp mình.").isFallback()).isTrue();
        assertThat(state.shortlist).isEmpty();assertThat(state.comparisonProducts).isEmpty();
    }

    @Test void allowingRelatedBooksAfterExhaustionExcludesAlreadyShownBooks() {
        Product exact=book(48,"Thám Tử Conan");exact.setDescription("Truyện tranh trinh thám phá án.");
        Product adjacent=book(52,"Chuyến phiêu lưu kỳ lạ");adjacent.setDescription("Truyện giả tưởng về phép thuật và một bí ẩn trong trường học.");
        when(products.findByStatusTrue()).thenReturn(List.of(exact,adjacent));
        nextTool="recommendBooks";nextIntent="DISCOVER_BOOKS";nextArgs=Map.of();nextMode="new";
        nextSet=Map.of("topics",List.of("trinh thám"),"topicMatch","any","limit",3,"maxPrice",150000);
        var first=send("Gợi ý 3 cuốn trinh thám, tối đa 150k mỗi cuốn.");
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(48L);
        nextMode="more";nextSet=Map.of("limit",3);
        var exhausted=send("Thêm 3 cuốn khác nữa.");
        assertThat(exhausted.getProducts()).isEmpty();assertThat(state.searchOutcome).isEqualTo("exhausted");
        nextMode="refine";nextSet=Map.of("match","allow_related");
        var expanded=send("Được, thêm sách gần chủ đề cũng được.");
        assertThat(expanded.isFallback()).isEqualTo(providerFailure);
        assertThat(expanded.getProducts()).extracting(ChatProductDto::getId).containsExactly(52L);
        assertThat(expanded.getAnswer()).contains("Gợi ý gần chủ đề");
        assertThat(state.toolSearch).containsEntry("maxPrice",150000).containsEntry("match","allow_related");
    }

    @Test void readPurposeSurvivesSerializationAndIsClearedForNewReadingNeed() throws Exception {
        readReviews();var restored=mapper.readValue(mapper.writeValueAsBytes(state),ChatDialogueMemory.State.class);
        assertThat(restored.bookReadContext).containsEntry("tool","bookReviews").containsEntry("mode","read");
        restored.pendingReviewMode="clarify";restored.pendingReviewProductId=54L;
        ObjectNode plan=mapper.createObjectNode().put("contextMode","new");
        plan.set("set",mapper.createObjectNode());plan.set("clear",mapper.createArrayNode());
        var request=new ChatRequestDto();request.setMessage("Đổi sang nhu cầu mới.");
        new ChatTurnContext(mapper).apply(plan,request,restored);assertThat(restored.bookReadContext).isEmpty();
        assertThat(restored.pendingReviewMode).isEmpty();assertThat(restored.pendingReviewProductId).isNull();
    }

    @Test void giftDirectionFollowupKeepsRecipientAndTheOriginalCollegeCircumstance() {
        Product one=book(81,"Kỹ năng sống A");one.setDescription("Sách kỹ năng sống và phát triển bản thân.");
        Product two=book(82,"Kỹ năng sống B");two.setDescription("Sách kỹ năng sống, ứng xử trong cuộc sống.");
        when(products.findByStatusTrue()).thenReturn(List.of(one,two));
        nextMode="new";nextIntent="DISCOVER_BOOKS";nextTool="respond";
        nextSet=Map.of("recipient","em họ","purpose","làm quà");
        nextArgs=Map.of("kind","clarify","topic","gift","suggestedTopics","Kỹ năng sống","suggestionSource","current");
        String circumstance="Em họ mình sắp vào đại học, mình muốn mua quà.";
        var first=send(circumstance);
        assertThat(first.isFallback()).isEqualTo(providerFailure);assertThat(first.getProducts()).isEmpty();
        state.recentDialogue.add(Map.of("user",circumstance,"assistant",first.getAnswer()));
        nextMode="refine";nextSet=Map.of();
        nextArgs=Map.of("kind","clarify","topic","preferences","suggestedTopics","Kỹ năng sống","suggestionSource","previous1");
        var followup=send("Chưa biết chọn gì, gợi ý vài hướng theo chuyện mình vừa kể đi.");
        assertThat(followup.isFallback()).isEqualTo(providerFailure);assertThat(followup.getProducts()).isEmpty();
        assertThat(state.toolSearch).containsEntry("recipient","em họ").containsEntry("purpose","làm quà")
            .doesNotContainKeys("readerAge","grade","readerGroup","topics");
        assertThat(input.path("confirmedConstraints").path("recipient").asText()).isEqualTo("em họ");
        assertThat(input.path("topicContextSources")).hasSize(1);
        assertThat(input.path("topicContextSources").get(0).path("text").asText()).isEqualTo(circumstance);
        assertThat(followup.getAnswer()).contains("sắp vào đại học");
    }

    String collegeGiftMenu() {
        List<Product> available=new ArrayList<>();
        for(int i=1;i<=2;i++) {
            Product detective=book(80+i,"Vụ án "+i);detective.setDescription("Truyện trinh thám về thám tử phá án.");available.add(detective);
            Product adventure=book(90+i,"Hành trình "+i);adventure.setDescription("Truyện phiêu lưu khám phá vùng đất mới.");available.add(adventure);
        }
        when(products.findByStatusTrue()).thenReturn(available);
        nextMode="new";nextIntent="DISCOVER_BOOKS";nextTool="respond";
        nextSet=Map.of("recipient","em họ","purpose","làm quà");
        nextArgs=Map.of("kind","clarify","topic","gift","suggestedTopics","Truyện trinh thám|Truyện phiêu lưu","suggestionSource","current");
        String circumstance="Em họ mình sắp vào đại học, mình muốn mua quà.";
        var first=send(circumstance);
        assertThat(first.getChips()).containsExactly("Truyện trinh thám","Truyện phiêu lưu");
        state.recentDialogue.add(Map.of("user",circumstance,"assistant",first.getAnswer()));
        nextMode="refine";nextSet=Map.of();
        nextArgs=Map.of("kind","clarify","topic","gift","suggestedTopics","Thiên văn học|Quản lý thời gian","suggestionSource","previous1");
        return circumstance;
    }

    @Test void unavailableNewDirectionsKeepVerifiedPreviousMenuForTheSameCollegeGift() {
        String circumstance=collegeGiftMenu();
        var response=send("Chưa biết chọn gì, gợi ý vài hướng theo chuyện mình vừa kể đi.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);assertThat(response.getProducts()).isEmpty();
        assertThat(response.getChips()).containsExactly("Truyện trinh thám","Truyện phiêu lưu");
        assertThat(response.getAnswer()).contains(circumstance,"các hướng mình vừa gợi ý")
            .doesNotContain("Bạn muốn đọc về điều gì");
        assertThat(state.toolSearch).containsEntry("recipient","em họ").containsEntry("purpose","làm quà");
        assertThat(state.preparedTopics).hasSize(2);
        assertThat(state.preparedTopics.values()).allMatch(p->p.rankedIds().size()>=2);
    }

    @Test void repeatedValidMenuIsReverifiedInsteadOfBeingRejectedAsItsOwnDuplicate() {
        collegeGiftMenu();
        nextArgs=Map.of("kind","clarify","topic","gift","suggestedTopics","Truyện trinh thám|Truyện phiêu lưu","suggestionSource","previous1");
        assertThat(send("Chưa biết chọn gì, gợi ý vài hướng theo chuyện mình vừa kể đi.").getChips())
            .containsExactly("Truyện trinh thám","Truyện phiêu lưu");
    }

    @Test void previousMenuCannotSurviveAChangedBudgetWithoutEnoughAffordableBooks() {
        collegeGiftMenu();nextSet=Map.of("maxPrice",30000);
        var response=send("Chưa biết chọn gì, gợi ý vài hướng dưới 30k theo chuyện mình vừa kể đi.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);assertThat(response.getChips()).isEmpty();
        assertThat(state.preparedTopics).isEmpty();
        assertThat(response.getAnswer()).contains("chưa tìm được","Người nhận quà");
    }

    @Test void changingRecipientInvalidatesPreviousDirectionsAndTheirPersonalContext() {
        collegeGiftMenu();nextSet=Map.of("recipient","mẹ");
        var response=send("Đổi người nhận thành mẹ mình, gợi ý vài hướng nhé.");
        assertThat(response.isFallback()).isEqualTo(providerFailure);assertThat(response.getChips()).isEmpty();
        assertThat(state.topicSuggestions).isEmpty();
        // The model may still quote the old user text, but it cannot reuse that menu.
        assertThat(state.preparedTopics).isEmpty();
    }
}
