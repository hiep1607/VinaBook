package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;
import vn.fs.config.CurrentUserAccess;

/** Real validation, canonical memory, catalog retrieval and writer guards in one flow. */
class ChatStructuredPipelineTest {
    ObjectMapper mapper=new ObjectMapper();
    ChatIntentService language=new ChatIntentService();
    ChatRetrievalService data=spy(new ChatRetrievalService(language));
    ProductRepository products=mock(ProductRepository.class);
    AiGateway ai=mock(AiGateway.class);
    ChatAnalyticsService analytics=mock(ChatAnalyticsService.class);
    ChatToolRegistry registry;
    ChatStructuredPipeline pipeline;
    ChatTurnUnderstanding understanding;
    ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    List<Product> catalog=new ArrayList<>();
    ObjectNode nextSet;List<String> nextClear=List.of();String nextMode="new",nextIntent="DISCOVER_BOOKS",nextTool="recommendBooks";
    ObjectNode nextArgs;boolean badEvidence,badNarration,badNoteOnly,referencedEvidence;
    String writerOverride;boolean writerFailure;
    AtomicInteger reads=new AtomicInteger(),writes=new AtomicInteger(),preparations=new AtomicInteger();
    JsonNode writerInput,lastUnderstandingInput;

    @BeforeEach void setup() {
        catalog.add(book(1,"Vụ án A","Truyện trinh thám về một vụ mất tích, có suy luận phá án.",80000));
        catalog.add(book(2,"Vụ án B","Truyện trinh thám về điều tra một vụ án.",60000));
        catalog.add(book(3,"Vụ án C","Truyện trinh thám xoay quanh bí ẩn ở một thị trấn.",20000));
        catalog.add(book(4,"Vụ án D","Truyện trinh thám khám phá một vụ án.",100000));
        catalog.add(book(5,"Hành trình A","Truyện phiêu lưu khám phá một vùng đất mới.",40000));
        catalog.add(book(6,"Luật đất đai","Văn bản pháp luật về đất đai.",10000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        when(ai.isConfigured()).thenReturn(true);when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("test");
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("message");
        ChatBuiltinTools tools=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        ChatTool checkout=new ChatTool() {
            public String name(){return "prepareCheckout";}
            public String description(){return "Chuẩn bị mua";}
            public Map<String,Parameter> parameters(){return Map.of("reference",Parameter.choice("Cuốn","focus","second"));}
            public Result execute(JsonNode args,Context context){
                preparations.incrementAndGet();
                return new Result("Bạn xem lại cuốn đã chọn rồi xác nhận trong biểu mẫu nhé.",ChatIntent.PURCHASE,
                    List.of(),List.of(new ChatActionDto("CHECKOUT_PRODUCT","Xem lại",context.state().focus,null,true)),"COMPLETED");
            }
        };
        registry=new ChatToolRegistry(List.of(tools.searchProducts(),tools.recommendBooks(),tools.getProducts(),tools.respond(),tools.getPolicy(),checkout));
        understanding=new ChatTurnUnderstanding(ai,mapper,registry);
        ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,products,language);
        pipeline=new ChatStructuredPipeline(ai,mapper,understanding,registry,narrator,analytics,products,data);
        nextSet=mapper.createObjectNode();nextArgs=mapper.createObjectNode();
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenAnswer(invocation->{
            JsonNode input=mapper.readTree((String)invocation.getArgument(2));
            if(input.has("question")) {
                writes.incrementAndGet();writerInput=input;
                if(writerFailure)throw new IllegalStateException("writer timed out");
                if(writerOverride!=null)return writerOverride;
                if(badNarration)return """
                    {"intro":"","closing":"","reasons":[{"productId":1,"evidenceField":"summary","evidence":"Nội dung không tồn tại trong catalog","reason":"Cuốn này có bài tập thực hành."}],"preferredProductId":null}
                    """;
                return """
                    {"intro":"","closing":"","reasons":[],"preferredProductId":null}
                    """;
            }
            reads.incrementAndGet();lastUnderstandingInput=input;
            ObjectNode plan=mapper.createObjectNode();
            plan.put("contextVersion",input.path("contextVersion").asLong());plan.put("intent",nextIntent);plan.put("contextMode",nextMode);
            plan.set("set",nextSet);plan.set("clear",mapper.valueToTree(nextClear));
            ObjectNode evidence=mapper.createObjectNode();
            String message=input.path("message").asText();
            String source=referencedEvidence?ChatTurnContext.CURRENT_MESSAGE:message;
            nextSet.fieldNames().forEachRemaining(key->evidence.put(key,badEvidence?"lời chatbot tự nói":source));
            nextClear.forEach(key->evidence.put(key,source));
            if(referencedEvidence && !badEvidence)plan.put("evidence",ChatTurnContext.CURRENT_MESSAGE);
            else plan.set("evidence",evidence);
            plan.set("memoryNote",mapper.valueToTree(Map.of("text","Ghi chú: "+message,"evidence",badNoteOnly?"khách chưa từng nói thế này":source)));
            plan.set("calls",mapper.valueToTree(List.of(Map.of("tool",nextTool,"arguments",nextArgs))));
            return mapper.writeValueAsString(plan);
        });
    }
    Product book(long id,String title,String description,double price) {
        return new Product(id,title,"Tác giả",20,price,0,"cover.jpg",description,LocalDate.now(),true,0,new Category(1L,"Văn học"));
    }
    ChatRequestDto request(String message) {
        var request=new ChatRequestDto();request.setMessage(message);request.setSessionId("structured-test");
        request.setRequestId(UUID.randomUUID().toString());return request;
    }
    ChatResponseDto send(String message) {return pipeline.reply(request(message),state);}
    List<Long> ids(ChatResponseDto response) {return response.getProducts().stream().map(ChatProductDto::getId).toList();}
    void topics(String... values) {nextSet.set("topics",mapper.valueToTree(values));nextSet.put("topicMatch","any");}
    void current(String tool,Map<String,Object> args) {
        nextMode="current";nextSet=mapper.createObjectNode();nextClear=List.of();nextTool=tool;nextArgs=mapper.valueToTree(args);
    }

    @Test void applicationQuestionWithoutContentKeywordsReachesTheNarrator() {
        String summary="Nguyên tắc tránh chỉ trích và lên án khi giao tiếp.";
        catalog.clear();catalog.add(book(57,"Sách giao tiếp",summary,70520));
        state.focus=57L;state.displayed=List.of(57L);state.shortlist=List.of(57L);state.initialized=true;
        current("getProducts",Map.of("field","explanation"));nextIntent="PRODUCT_INFO";
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn("{\"evidenceField\":\"summary\",\"evidence\":\"Nguyên tắc tránh chỉ trích và lên án khi giao tiếp.\",\"principle\":\"Tránh chỉ trích người đối diện khi giao tiếp.\",\"example\":\"Bạn thử nói: Mình muốn hiểu lý do của cách làm này, bạn giải thích thêm nhé?\"}");
        var result=send("Lấy ví dụ khi bất đồng với đồng nghiệp thì mình nên nói câu gì?");
        assertThat(result.isFallback()).isFalse();assertThat(result.getAnswer()).contains("Mình muốn hiểu lý do","minh họa tự xây dựng","không phải trích sách").doesNotContain("Mục tiêu đọc:");
        assertThat(ids(result)).containsExactly(57L);assertThat(result.getExecution()).containsEntry("pipeline","structured-v1");
        verify(ai,times(1)).complete(anyString(),anyList(),anyString());
    }
    @Test void conversationRefinesClearsAndContinuesWithoutLosingCanonicalConstraints() {
        topics("phiêu lưu","trinh thám");nextSet.put("maxPrice",100000);nextSet.put("maxPriceExclusive",true);
        var first=send("Phiêu lưu hay trinh thám dưới 100k");
        assertThat(first.isFallback()).isFalse();assertThat(first.getProducts()).hasSize(3).allMatch(p->p.getPrice()<100000);
        nextMode="refine";nextSet=mapper.createObjectNode().put("preferredQuery","truyện chữ");
        send("T thích truyện chữ hơn");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000).containsEntry("preferredQuery","truyện chữ").doesNotContainKey("excludedQuery");
        nextSet=mapper.createObjectNode();topics("trinh thám");
        send("Chỉ trinh thám thôi");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000).containsEntry("preferredQuery","truyện chữ");
        nextSet=mapper.createObjectNode();nextClear=List.of("maxPrice");
        send("Giá không quan trọng nữa");
        assertThat(state.toolSearch).containsEntry("query","trinh thám").doesNotContainKey("maxPrice").doesNotContainKey("maxPriceExclusive");
        nextMode="more";nextClear=List.of();
        var more=send("Tiếp");
        assertThat(ids(more)).containsExactly(4L);
        more=send("Thêm nữa");
        assertThat(more.getProducts()).isEmpty();assertThat(state.searchOutcome).isEqualTo("exhausted");
        assertThat(more.getAnswer()).contains("đã được giới thiệu hết");
        assertThat(state.contextVersion).isEqualTo(6);
    }
    @Test void orAndAndHaveDifferentCatalogResults() {
        topics("phiêu lưu","trinh thám");
        assertThat(ids(send("Phiêu lưu hay trinh thám"))).contains(5L).doesNotContain(6L);
        nextSet.put("topicMatch","all");
        assertThat(send("Vừa phiêu lưu vừa trinh thám").getProducts()).isEmpty();
        assertThat(state.searchOutcome).isEqualTo("no_match");
    }
    @Test void purposeIsEnoughWithoutBudgetOrRecipientAndGenericFictionNeedsEvidence() {
        nextSet.put("query","truyện");nextSet.put("purpose","đọc cho đỡ chán");
        var response=send("T muốn đọc cho đỡ chán, có cuốn thú vị không");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).hasSize(3);
        assertThat(ids(response)).doesNotContain(6L);assertThat(state.toolSearch).doesNotContainKeys("recipient","maxPrice","readingNeeds");
        assertThat(reads.get()).isEqualTo(1);assertThat(writes.get()).isEqualTo(1);
    }
    @Test void bundleBudgetAllowsUnequalPricesAndReportsShortfall() {
        topics("trinh thám");nextSet.put("totalBudget",100000);nextSet.put("limit",2);
        var response=send("Chọn 2 cuốn trinh thám tổng không quá 100k");
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getProducts().stream().mapToLong(ChatProductDto::getPrice).sum()).isLessThanOrEqualTo(100000);
        assertThat(response.getProducts()).anyMatch(p->p.getPrice()>50000);
        nextSet.put("limit",4);
        response=send("Chọn 4 cuốn trinh thám tổng không quá 100k");
        assertThat(response.getProducts()).hasSize(2);assertThat(state.searchOutcome).isEqualTo("partial");
        assertThat(response.getAnswer()).contains("chưa đủ 4");
    }
    @Test void invalidEvidenceIsRepairedOnceAndDoesNotMutateGoodMemory() {
        state.toolSearch.put("query","trinh thám");state.contextVersion=7;
        nextMode="refine";nextSet.put("query","pháp luật");badEvidence=true;
        var response=send("T muốn phiêu lưu");
        assertThat(response.isFallback()).isTrue();assertThat(reads.get()).isEqualTo(2);
        assertThat(state.contextVersion).isEqualTo(7);assertThat(state.toolSearch).containsOnlyKeys("query").containsEntry("query","trinh thám");
        assertThat(state.conversationNotes).isEmpty();assertThat(writes.get()).isZero();
    }
    @Test void inventedProductIdCannotPrepareAnAction() {
        current("getProducts",Map.of("productId",9999,"field","price"));
        var response=send("Giá cuốn này");
        assertThat(response.isFallback()).isTrue();assertThat(preparations.get()).isZero();assertThat(state.contextVersion).isZero();
    }
    @Test void retrievalFailurePreservesConfirmedContextAndShownOrder() {
        topics("trinh thám");send("Trinh thám");var before=new LinkedHashMap<>(state.toolSearch);
        var displayed=new ArrayList<>(state.displayed);long version=state.contextVersion;
        nextMode="refine";nextSet=mapper.createObjectNode().put("preferredQuery","truyện chữ");
        when(products.findByStatusTrue()).thenReturn(catalog).thenThrow(new IllegalStateException("DB offline"));
        var response=send("T thích truyện chữ hơn");
        assertThat(response.isFallback()).isTrue();assertThat(state.toolSearch).isEqualTo(before);
        assertThat(state.displayed).isEqualTo(displayed);assertThat(state.contextVersion).isEqualTo(version);
    }
    @Test void writerGetsFactsEvidenceUnknownsAndCannotInventUnsupportedContent() {
        topics("trinh thám");badNarration=true;
        var response=send("Trinh thám");
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getAnswer()).doesNotContain("bài tập thực hành");
        JsonNode packet=writerInput.path("answerPacket");
        assertThat(packet.path("result").path("books").get(0).path("unknown").toString()).contains("readingLevel");
        assertThat(packet.path("confirmedNeed").path("query").asText()).isEqualTo("trinh thám");
        assertThat(packet.path("responseRequirements").path("mayExecuteActions").asBoolean()).isFalse();
    }
    @Test void ordinalDetailThenPurchaseKeepsFocusedBookAndOnlyPreparesCheckout() {
        topics("trinh thám");var first=send("Trinh thám");long second=first.getProducts().get(1).getId();
        current("getProducts",Map.of("reference","second","field","price"));nextIntent="PRODUCT_INFO";
        int previousWrites=writes.get();
        assertThat(ids(send("Giá cuốn thứ hai"))).containsExactly(second);
        assertThat(state.focus).isEqualTo(second);assertThat(writes.get()).isEqualTo(previousWrites);
        current("prepareCheckout",Map.of("reference","focus"));nextIntent="PURCHASE";
        var purchase=send("Mua cuốn này");
        assertThat(purchase.isFallback()).isFalse();assertThat(preparations.get()).isEqualTo(1);
        assertThat(purchase.getActions().get(0).getProductId()).isEqualTo(second);
    }
    @Test void snapshotsPersistEvidenceSemanticNotesAndVersionWithoutTransientFlags() throws Exception {
        topics("trinh thám");send("Trinh thám");
        String json=mapper.writeValueAsString(state);
        var restored=mapper.readValue(json,ChatDialogueMemory.State.class);
        assertThat(restored.contextVersion).isEqualTo(1);
        assertThat(restored.contextSources.get("topics").get("quote")).isEqualTo("Trinh thám");
        assertThat(restored.conversationNotes).hasSize(1);assertThat(restored.structuredTurnActive).isFalse();
        assertThat(restored.shownProductIds).isEqualTo(state.shownProductIds);
    }
    @Test void staleVersionAndUnsupportedPriceAreRejected() throws Exception {
        var req=request("Trinh thám dưới 100k");topics("trinh thám");nextSet.put("maxPrice",200000);
        assertThat(send(req.getMessage()).isFallback()).isTrue();
        assertThat(state.contextVersion).isZero();
        nextSet=mapper.createObjectNode();
        String stale= """
            {"contextVersion":99,"intent":"DISCOVER_BOOKS","contextMode":"more","set":{},"clear":[],"evidence":{},"memoryNote":null,"calls":[{"tool":"recommendBooks","arguments":{}}]}
            """;
        assertThatThrownBy(()->understanding.validate(stale,req,state,List.of())).hasMessageContaining("Stale");
    }
    @Test void configuredServiceUsesStructuredPipelineBeforeLegacyIntakeAndDeduplicatesRetries() {
        var service=new ChatService(products,ai,language,data,new ChatPolicyService(language),mock(ChatOrderSupportService.class),analytics,mock(CurrentUserAccess.class));
        service.setStructuredPipeline(pipeline);service.setTools(registry);
        topics("trinh thám");var req=request("Trinh thám");
        var first=service.reply(req);var duplicate=service.reply(req);
        assertThat(first.getExecution()).containsEntry("pipeline","structured-v1");
        assertThat(duplicate).isSameAs(first);assertThat(reads.get()).isEqualTo(1);
    }
    @Test void exclusiveTotalBudgetIsAppliedToWholeBundle() {
        topics("trinh thám");nextSet.put("totalBudget",100000);nextSet.put("totalBudgetExclusive",true);nextSet.put("limit",2);
        var response=send("2 cuốn trinh thám tổng dưới 100k");
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getProducts().stream().mapToLong(ChatProductDto::getPrice).sum()).isLessThan(100000);
    }
    @Test void statedGradeMustMatchCatalogEvidenceAndIsKeptOnPriceRefinement() {
        catalog.add(book(7,"Toán 7","Bài tập toán lớp 7.",50000));
        catalog.add(book(8,"Toán 5","Bài tập toán lớp 5.",30000));
        topics("toán");nextSet.put("grade",7);
        assertThat(ids(send("Sách toán lớp 7"))).containsExactly(7L);
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPrice",100000);
        assertThat(ids(send("Không quá 100k"))).containsExactly(7L);
        assertThat(state.toolSearch).containsEntry("grade",7);
    }
    @Test void clearingTotalBudgetRemovesItsExclusiveFlagOnly() {
        topics("trinh thám");nextSet.put("totalBudget",100000);nextSet.put("totalBudgetExclusive",true);
        send("Trinh thám tổng dưới 100k");
        nextMode="refine";nextSet=mapper.createObjectNode();nextClear=List.of("totalBudget");
        send("Bỏ tổng ngân sách đi");
        assertThat(state.toolSearch).containsEntry("query","trinh thám").doesNotContainKeys("totalBudget","totalBudgetExclusive");
        assertThat(state.recommendationBudget).isNull();
    }
    @Test void nullPropertiesFromFixedModelSchemaAreNoOpsRatherThanInventedDefaultsOrClears() {
        topics("trinh thám");nextSet.putNull("grade");nextSet.putNull("recipient");nextSet.putNull("maxPrice");
        nextArgs.putNull("orderId");nextArgs.putNull("quantity");
        var response=send("Trinh thám");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isNotEmpty();
        assertThat(state.toolSearch).doesNotContainKeys("grade","recipient","maxPrice");
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPrice",100000);
        send("Dưới 100 nghìn");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        nextSet=mapper.createObjectNode().putNull("maxPrice");
        send("Giữ nguyên nhé");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);
    }
    @Test void discoveryArgumentsCannotCompeteWithEvidenceBackedContext() throws Exception {
        String message="Trinh thám dưới 100k";topics("trinh thám");nextArgs.put("maxPrice",100000);
        assertThat(send(message).isFallback()).isTrue();
        assertThat(state.contextVersion).isZero();assertThat(state.toolSearch).isEmpty();
        assertThat(writes.get()).isZero();assertThat(preparations.get()).isZero();
    }
    @Test void sparseProviderMapStillRejectsUnknownFieldsAndWrongTypesBeforeExecution() {
        topics("trinh thám");nextSet.put("inventedFilter","yes");
        assertThat(send("Trinh thám").isFallback()).isTrue();assertThat(state.contextVersion).isZero();
        nextSet=mapper.createObjectNode().put("maxPrice","100000");
        assertThat(send("Trinh thám dưới 100k").isFallback()).isTrue();
        assertThat(state.contextVersion).isZero();assertThat(state.toolSearch).isEmpty();assertThat(writes.get()).isZero();
    }
    @Test void prosePreferenceUsesVerifiedLiteraryFormAndKeepsComicsAsAlternatives() {
        catalog.get(4).setDescription("Truyện dài phiêu lưu khám phá một vùng đất mới.");
        catalog.add(book(7,"Vụ án tranh","Truyện tranh trinh thám về các vụ án.",15000));
        topics("trinh thám","phiêu lưu");nextSet.put("preferredQuery","truyện chữ");
        var response=send("Trinh thám hoặc phiêu lưu, thích truyện chữ hơn");
        assertThat(ids(response)).startsWith(5L);
        assertThat(response.getAnswer()).contains("Có 1 cuốn đáp ứng phần ưu tiên").doesNotContain("chưa có cuốn đáp ứng phần ưu tiên");
        assertThat(((Collection<?>)state.toolSearch.get("preferredProductIds")).stream()
            .map(value->((Number)value).longValue()).toList()).containsExactly(5L);
    }
    @Test void clarificationCannotStartACompetingIntakeOrReaskOptionalFieldsInStructuredMode() {
        topics("trinh thám");nextTool="respond";nextArgs.put("kind","clarify").put("topic","preferences").put("missing","recipient");
        var response=send("Trinh thám");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isNotEmpty();
        assertThat(response.getIntent()).isEqualTo(ChatIntent.DISCOVER_BOOKS);
        assertThat(state.clarificationFacts).isEmpty();assertThat(state.toolSearch).containsEntry("query","trinh thám");
    }
    @Test void shortExplanationAfterExhaustionDistinguishesShownBooksFromNoMatchingBooks() {
        topics("trinh thám");nextSet.put("limit",5);send("5 cuốn trinh thám");
        nextMode="more";nextSet=mapper.createObjectNode();send("Tiếp");
        assertThat(state.searchOutcome).isEqualTo("exhausted");
        current("respond",Map.of("kind","clarify","topic","product"));
        var response=send("?");
        assertThat(response.isFallback()).isFalse();assertThat(response.getAnswer()).contains("đã được giới thiệu trước đó");
        assertThat(response.getAnswer()).doesNotContain("chưa có sách phù hợp");
        assertThat(state.toolSearch).containsEntry("query","trinh thám");
        current("getProducts",Map.of("reference","selection","field","detail"));
        assertThat(send("?").getAnswer()).contains("đã được giới thiệu trước đó");
    }
    @Test void exhaustingMoreDoesNotLoseLastSelectionForOrdinalReferenceButNewNeedClearsIt() {
        topics("trinh thám");nextSet.put("limit",5);send("5 cuốn trinh thám");
        List<Long> previous=new ArrayList<>(state.shortlist);
        nextMode="more";nextSet=mapper.createObjectNode();send("Tiếp");
        assertThat(state.displayed).isEmpty();assertThat(state.shortlist).isEqualTo(previous);
        current("getProducts",Map.of("reference","second","field","price"));
        assertThat(ids(send("Giá cuốn thứ hai"))).containsExactly(previous.get(1));
        assertThat(lastUnderstandingInput.path("productFacts").size()).isGreaterThanOrEqualTo(previous.size());
        nextMode="new";nextIntent="DISCOVER_BOOKS";nextTool="recommendBooks";nextArgs=mapper.createObjectNode();
        nextSet=mapper.createObjectNode();topics("thiên văn");send("Đổi sang thiên văn");
        assertThat(state.shortlist).isEmpty();assertThat(state.focus).isNull();
    }
    @Test void writerCannotInventLiteraryFormWhenOnlyDetectiveTopicIsKnown() {
        ChatReadingClaims claims=new ChatReadingClaims(language);
        var facts=Map.of("summary","Các câu chuyện trinh thám với suy luận phá án.");
        assertThat(claims.unsupported("Đây là lựa chọn hợp ưu tiên truyện chữ của bạn.",facts)).isTrue();
        assertThat(claims.unsupported("Sách là một tiểu thuyết trinh thám.",facts)).isTrue();
        assertThat(claims.unsupported("Nội dung tập trung vào suy luận phá án.",facts)).isFalse();
        assertThat(claims.unsupported("Hợp ưu tiên truyện chữ của bạn.",Map.of("summary","Truyện dài phiêu lưu."))).isFalse();
    }
    @Test void ungroundedOptionalNoteIsDiscardedWithoutRejectingValidConstraints() {
        topics("trinh thám");badNoteOnly=true;
        var response=send("Trinh thám");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isNotEmpty();
        assertThat(state.toolSearch).containsEntry("query","trinh thám");assertThat(state.conversationNotes).isEmpty();
        assertThat(reads.get()).isEqualTo(1);
    }
    @Test void contextualChoicesPrepareCatalogOnCopiesAndOnlyConfirmedChoiceBecomesFilter() {
        catalog.add(book(7,"Giao tiếp công sở A","Sách kỹ năng giao tiếp công sở.",50000));
        catalog.add(book(8,"Giao tiếp công sở B","Sách kỹ năng giao tiếp công sở.",60000));
        catalog.add(book(9,"Quản lý thời gian A","Hướng dẫn quản lý thời gian.",50000));
        catalog.add(book(10,"Quản lý thời gian B","Hướng dẫn quản lý thời gian.",60000));
        catalog.add(book(11,"Tài chính cá nhân A","Hướng dẫn tài chính cá nhân và quản lý chi tiêu.",50000));
        catalog.add(book(12,"Tài chính cá nhân B","Hướng dẫn tài chính cá nhân và quản lý chi tiêu.",60000));
        state.recentDialogue.add(Map.of("user","Mình sắp đi làm lần đầu","assistant","Bạn cần hỗ trợ gì?"));
        nextTool="respond";nextArgs.put("kind","clarify").put("topic","preferences")
            .put("suggestionContext","Mình sắp đi làm lần đầu")
            .put("suggestedTopics","Giao tiếp công sở|Quản lý thời gian|Tài chính cá nhân");
        nextSet.put("maxPrice",100000);
        var response=send("Muốn mua sách dưới 100k mà chưa biết đọc gì");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isEmpty();
        assertThat(response.getChips()).containsExactly("Giao tiếp công sở","Quản lý thời gian","Tài chính cá nhân");
        assertThat(response.getAnswer()).contains("Mình sắp đi làm lần đầu");
        assertThat(state.toolSearch).doesNotContainKeys("query","topics");
        assertThat(((Number)state.toolSearch.get("maxPrice")).longValue()).isEqualTo(100000L);
        assertThat(writes.get()).isZero();assertThat(preparations.get()).isZero();
        nextMode="refine";nextTool="recommendBooks";nextArgs=mapper.createObjectNode();
        nextSet=mapper.createObjectNode();topics("kỹ năng giao tiếp công sở");
        response=send("Giao tiếp công sở nhé");
        assertThat(response.isFallback()).isFalse();assertThat(ids(response)).containsExactly(7L,8L);
        assertThat(response.getChips()).isEmpty();assertThat(((Number)state.toolSearch.get("maxPrice")).longValue()).isEqualTo(100000L);
        assertThat(state.toolSearch.get("query").toString()).contains("giao tiếp công sở");
        assertThat(state.toolSearch.get("query").toString()).doesNotContain("thời gian","tài chính");
    }
    @Test void ordinalTopicSelectionConfirmsExactlyRenderedChoiceWithoutAskingAiAgain() {
        state.topicSuggestions.addAll(List.of("Giao tiếp công sở","Quản lý thời gian","Tài chính cá nhân"));
        state.toolSearch.put("maxPrice",100000L);
        catalog.add(book(7,"Quản lý thời gian hiệu quả","Sách hướng dẫn quản lý thời gian và lập kế hoạch công việc.",50000));
        catalog.add(book(8,"Thói quen làm việc","Phương pháp quản lý công việc. Các ví dụ đi theo thời gian.",40000));
        nextSet=mapper.createObjectNode(); // A broad mock AI plan must not decide the selected topic.
        var response=send("Hướng thứ hai nhé");
        assertThat(response.isFallback()).isFalse();assertThat(reads.get()).isZero();
        assertThat(ids(response)).containsExactly(7L);
        assertThat(state.toolSearch.get("query")).isEqualTo("Quản lý thời gian");
        assertThat(((Number)state.toolSearch.get("maxPrice")).longValue()).isEqualTo(100000L);
        assertThat(state.topicSuggestions).isEmpty();
    }
    @Test void studySkillChoiceNeedsContentEvidenceRatherThanStudentAudienceOrLooseWords() {
        catalog.add(book(7,"Kỹ năng học tập","Hướng dẫn kỹ năng học tập và phương pháp học hiệu quả.",50000));
        catalog.add(book(8,"Giáo trình kỹ năng lập trình","Giáo trình cho sinh viên học tập. Giới thiệu kỹ năng lập trình.",40000));
        topics("kỹ năng học tập");
        var response=send("Kỹ năng học tập");
        assertThat(response.isFallback()).isFalse();assertThat(ids(response)).containsExactly(7L);
    }
    @Test void knownTopicStillSearchesRatherThanShowingNewSoftSuggestions() {
        topics("trinh thám");nextTool="respond";nextArgs.put("kind","clarify").put("topic","preferences")
            .put("suggestionContext","Mình sắp đi làm lần đầu").put("suggestedTopics","Giao tiếp công sở|Quản lý thời gian");
        var response=send("Trinh thám nhé");
        assertThat(response.getProducts()).isNotEmpty();assertThat(response.getChips()).isEmpty();
        assertThat(response.getAnswer()).doesNotContain("Bạn có nhắc","công sở");
    }
    @Test void ungroundedSuggestionOnlyFallsBackToGenericQuestionAndDoesNotFailPipeline() {
        nextTool="respond";nextArgs.put("kind","clarify").put("topic","preferences")
            .put("suggestionContext","Bạn là luật sư mới tốt nghiệp").put("suggestedTopics","Luật dân sự|Luật hình sự");
        var response=send("Tôi muốn mua sách mà chưa biết chủ đề");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isEmpty();
        assertThat(response.getAnswer()).doesNotContain("luật sư","Luật dân sự","Luật hình sự");
        assertThat(response.getChips()).containsExactly("Truyện để thư giãn","Truyện trinh thám");assertThat(state.toolSearch).doesNotContainKeys("topics","query","purpose");
    }
    @Test void giftAloneAsksReadingDirectionRatherThanShowingArbitraryBooksOrRequiringRecipient() {
        nextSet.put("purpose","làm quà");nextTool="respond";nextArgs.put("kind","clarify").put("topic","gift");
        var response=send("Mình muốn tìm sách làm quà");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isEmpty();
        assertThat(response.getAnswer()).contains("chủ đề").doesNotContain("tuổi","ngân sách");
        nextMode="refine";nextTool="recommendBooks";nextArgs=mapper.createObjectNode();nextSet=mapper.createObjectNode();topics("trinh thám");
        assertThat(send("Truyện trinh thám").getProducts()).isNotEmpty();
    }
    @Test void blankOptionalDefaultsDoNotClearConfirmedValuesOrRequireFabricatedEvidence() {
        topics("trinh thám");nextSet.put("preferredQuery","truyện chữ");send("Trinh thám, thích truyện chữ hơn");
        nextMode="refine";nextSet=mapper.createObjectNode().put("query","").put("recipient","").put("preferredQuery","");
        nextSet.set("topics",mapper.createArrayNode());
        var response=send("Giữ nguyên nhé");
        assertThat(response.isFallback()).isFalse();assertThat(state.toolSearch).containsEntry("query","trinh thám").containsEntry("preferredQuery","truyện chữ");
        assertThat(state.toolSearch).doesNotContainKey("recipient");
    }
    @Test void understandingEndsWithCurrentMessageAndBoundsHistoricalText() {
        for(int i=0;i<8;i++)state.recentDialogue.add(Map.of("user","u".repeat(1500),"assistant","a".repeat(3000)));
        topics("trinh thám");send("Trinh thám");
        List<String> keys=new ArrayList<>();lastUnderstandingInput.fieldNames().forEachRemaining(keys::add);
        assertThat(keys.get(keys.size()-1)).isEqualTo("message");
        assertThat(lastUnderstandingInput.path("message").asText()).isEqualTo("Trinh thám");
        assertThat(lastUnderstandingInput.path("recentConversation")).hasSize(4);
        for(JsonNode turn:lastUnderstandingInput.path("recentConversation")) {
            assertThat(turn.path("previousUser").asText()).hasSizeLessThanOrEqualTo(600);
            assertThat(turn.path("previousAssistant").asText()).hasSizeLessThanOrEqualTo(400);
        }
    }
    @Test void generalEntertainmentPurposeNeverFallsThroughToTextbooksWhenModelUsesDifferentGenericQueries() {
        catalog.add(book(8,"Nhập môn AI","Giáo trình về trí tuệ nhân tạo và học máy.",5000));
        for(String query:List.of("","giải trí","sách thú vị")) {
            nextSet=mapper.createObjectNode().put("purpose","đọc giải trí");
            if(!query.isBlank())nextSet.put("query",query);
            var response=send("Mình đang chán, có cuốn nào thú vị để đọc giải trí không?");
            assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isNotEmpty();
            assertThat(ids(response)).doesNotContain(6L,8L).allMatch(id->id>=1&&id<=5);
            assertThat(state.toolSearch).doesNotContainKey("readingNeed");
        }
    }
    @Test void explicitScienceSubjectForEntertainmentIsPreservedInsteadOfForcingFiction() {
        catalog.add(book(8,"Khám phá khoa học","Kiến thức khoa học phổ thông về vũ trụ.",5000));
        nextSet.put("query","khoa học").put("purpose","đọc giải trí");
        assertThat(ids(send("Mình muốn đọc khoa học để giải trí"))).contains(8L);
    }

    @Test void currentMessageReferencesPersistOriginalSourceAndKeepMoneyValidation() {
        referencedEvidence=true;topics("trinh thám");nextSet.put("maxPrice",100000);
        String message="Mình tìm trinh thám, tối đa 100k nhé.";
        assertThat(send(message).isFallback()).isFalse();
        assertThat(reads.get()).isEqualTo(1);
        assertThat(state.contextSources.get("maxPrice").get("quote")).isEqualTo(message);
        assertThat(state.conversationNotes.get(0).get("evidence")).isEqualTo(message);
        var before=new LinkedHashMap<>(state.toolSearch);long version=state.contextVersion;
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPrice",200000);
        assertThat(send("Vẫn tối đa 100k").isFallback()).isTrue();
        assertThat(state.toolSearch).isEqualTo(before);assertThat(state.contextVersion).isEqualTo(version);
    }
    @Test void evidenceReferenceDoesNotAcceptHistoricalOrArbitrarySourceNames() {
        referencedEvidence=true;badEvidence=true;topics("trinh thám");
        assertThat(send("Trinh thám").isFallback()).isTrue();
        assertThat(state.contextVersion).isZero();assertThat(state.contextSources).isEmpty();
    }
    @Test void contradictoryPriceRangeAsksForCorrectionThenKeepsTopic() {
        topics("trinh thám");nextSet.put("minPrice",300000).put("maxPrice",100000);
        var response=send("Trinh thám, giá từ 300k đến 100k");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isEmpty();
        assertThat(response.getAnswer()).contains("Khoảng giá");
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPrice",150000);nextClear=List.of("minPrice");
        response=send("Gõ nhầm: tối đa 150k, không có giá tối thiểu");
        assertThat(response.getProducts()).isNotEmpty();
        assertThat(state.toolSearch).containsEntry("query","trinh thám").containsEntry("maxPrice",150000).doesNotContainKey("minPrice");
    }
    @Test void perBookAndBundleCeilingsAreBothEnforcedFromOneCurrentMessage() {
        referencedEvidence=true;topics("trinh thám");
        nextSet.put("maxPrice",70000).put("maxPriceExclusive",true).put("totalBudget",90000)
            .put("totalBudgetExclusive",true).put("limit",2);
        var response=send("Chọn 2 cuốn trinh thám, mỗi cuốn dưới 70k, tổng dưới 90k");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getProducts()).allMatch(p->p.getPrice()<70000);
        assertThat(response.getProducts().stream().mapToLong(ChatProductDto::getPrice).sum()).isLessThan(90000);
        assertThat(state.toolSearch).containsEntry("maxPrice",70000).containsEntry("totalBudget",90000);
    }
    @Test void unknownExercisesAndAnswersAreAddressedDirectlyWithoutAnotherWriterCall() {
        topics("trinh thám");send("Trinh thám");int previousWrites=writes.get();
        current("getProducts",Map.of("reference","first","field","explanation"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đầu tiên có bài tập thực hành kèm đáp án không?");
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getAnswer()).contains("Về bài tập thực hành", "Về đáp án hoặc lời giải", "chưa có thông tin đã xác nhận");
        assertThat(response.getAnswer()).doesNotContain("Cuốn này nằm trong hướng đọc");
        assertThat(writes.get()).isEqualTo(previousWrites);
    }
    @Test void exercisesDoNotImplyAnAnswerKeyAndNegatedDataIsNotPositiveEvidence() {
        var facts=Map.of("summary","Có bài tập thực hành cuối chương; chưa có thông tin xác nhận đáp án.");
        List<String> answer=ChatBookFacts.studyFeatures(Set.of("exercises","answerKey"),facts,language);
        assertThat(answer.get(0)).contains("thông tin đã xác nhận có nêu");
        assertThat(answer.get(1)).contains("chưa có thông tin đã xác nhận");
        answer=ChatBookFacts.studyFeatures(Set.of("exercises","answerKey"),Map.of("summary","Chưa xác nhận sách có bài tập hoặc đáp án."),language);
        assertThat(answer).allMatch(line->line.contains("chưa có thông tin đã xác nhận"));
    }
    @Test void detectiveSolutionsAreNotEvidenceOfExerciseAnswerKeys() {
        var facts=Map.of("summary","Nhân vật tìm lời giải cho vụ án bí ẩn.");
        assertThat(ChatBookFacts.studyFeatures(Set.of("answerKey"),facts,language).get(0)).contains("chưa có thông tin đã xác nhận");
    }
    @Test void verifiedExerciseSolutionsAreQuotedFromTheCorrectFeature() {
        var facts=Map.of("summary","Phần bài tập có đáp án và lời giải ở cuối sách.");
        assertThat(ChatBookFacts.studyFeatures(Set.of("exercises","answerKey"),facts,language))
            .hasSize(2).allMatch(line->line.contains("thông tin đã xác nhận có nêu"));
    }
    @Test void structuredNarrationCannotDuplicateTheLeadOrRemoveBudgetCaveats() {
        topics("trinh thám");nextSet.put("maxPrice",100000);
        writerOverride="""
            {"intro":"Mình hiểu hướng đọc bạn đang tìm.","closing":"","reasons":[],"preferredProductId":null}
            """;
        var response=send("Trinh thám, tối đa 100k");
        assertThat(response.getAnswer()).doesNotContain("Mình hiểu hướng đọc bạn đang tìm.").contains("100.000đ mỗi cuốn");
        assertThat(response.getProducts()).isNotEmpty();
    }
    @Test void communicationSkillRequiresInstructionEvidenceAndReportsTooFewBooks() {
        catalog.add(book(8,"Nguyên tắc ứng xử","Nguyên tắc giao tiếp và ứng xử trong quan hệ với người khác.",70000));
        catalog.add(book(9,"Một vé tuổi thơ","Truyện dài về tuổi thơ và suy ngẫm cách ứng xử với trẻ em.",71000));
        catalog.add(book(10,"Giảm lo âu","Nguyên tắc xử lý lo âu trong đời sống và công việc.",90000));
        BookKnowledgeService verified=mock(BookKnowledgeService.class);
        // Broad search metadata can mention communication without proving it is the primary content.
        when(verified.searchableText(any())).thenAnswer(inv->((Product)inv.getArgument(0)).getDescription()+" giao tiếp");
        when(verified.approvedDescription(any())).thenAnswer(inv->((Product)inv.getArgument(0)).getDescription());
        when(verified.approvedReadingFacts(any())).thenAnswer(inv->Map.of("summary",((Product)inv.getArgument(0)).getDescription()));
        when(verified.rankByMeaning(anyString(),anyList())).thenAnswer(inv->inv.getArgument(1));
        data.setKnowledge(verified);
        topics("kỹ năng giao tiếp");nextSet.put("limit",2).put("maxPrice",150000).put("totalBudget",200000);
        var response=send("2 cuốn kỹ năng giao tiếp, mỗi cuốn tối đa 150k, tổng 200k");
        assertThat(ids(response)).containsExactly(8L);assertThat(state.searchOutcome).isEqualTo("partial");
        assertThat(response.getAnswer()).contains("chưa đủ 2");
        nextSet=mapper.createObjectNode().put("query","giao tiếp").put("purpose","học kỹ năng giao tiếp");
        assertThat(ids(send("Mình muốn học kỹ năng giao tiếp"))).containsExactly(8L);
    }
    @Test void communicationSkillEvidenceDoesNotTurnAlternativeGenresIntoAnd() {
        catalog.add(book(8,"Nguyên tắc ứng xử","Nguyên tắc giao tiếp và ứng xử trong quan hệ với người khác.",70000));
        topics("kỹ năng giao tiếp","phiêu lưu");
        assertThat(ids(send("Sách kỹ năng giao tiếp hoặc phiêu lưu"))).contains(8L,5L);
    }
    @Test void beginnerLevelCannotBeLostOrInventedByNarration() {
        catalog.add(book(8,"Giáo trình Java","Tài liệu học lập trình Java. Chưa có mục lục để xác nhận các chủ đề cụ thể.",90000));
        topics("Java");nextSet.put("level","beginner");
        writerOverride="""
            {"intro":"","closing":"","reasons":[{"productId":8,"evidenceField":"summary",
            "evidence":"Tài liệu học lập trình Java.","reason":"Đây là tài liệu phù hợp để bắt đầu quá trình nghiên cứu ngôn ngữ này."}],"preferredProductId":null}
            """;
        var response=send("Java cho người mới bắt đầu");
        assertThat(response.getAnswer()).contains("chưa đủ thông tin để xác nhận độ khó cho người mới")
            .doesNotContain("phù hợp để bắt đầu quá trình");
        ChatReadingClaims claims=new ChatReadingClaims(language);
        assertThat(claims.unsupported("Giúp bạn tiếp cận các khái niệm nền tảng.",Map.of("summary","Tài liệu học Java."))).isTrue();
        assertThat(claims.unsupported("Phù hợp với người mới bắt đầu.",Map.of("summary","Tài liệu nhập môn Java dành cho người mới."))).isFalse();
    }
    @Test void pluralDemonstrativeOverridesSingularFocusAndKeepsOrdinalOrder() {
        topics("trinh thám");nextSet.put("limit",2);
        List<Long> selected=ids(send("2 cuốn trinh thám"));
        current("getProducts",Map.of("reference","focus","field","table"));nextIntent="COMPARE_BOOKS";
        var compared=send("So sánh hai cuốn đó giúp mình, chưa cần mua.");
        assertThat(compared.isFallback()).isFalse();assertThat(ids(compared)).isEqualTo(selected);
        assertThat(compared.getAnswer()).contains("| Tên sách |");assertThat(preparations.get()).isZero();
        current("getProducts",Map.of("reference","second","field","price"));nextIntent="PRODUCT_INFO";
        assertThat(ids(send("Giá cuốn thứ hai là bao nhiêu?"))).containsExactly(selected.get(1));
    }

    @Test void formatQuestionOverridesGenericExplanationAndKeepsBookFocus() {
        catalog.add(book(8,"Hành trình loài vật","Truyện dài về thế giới loài vật.",50000));
        topics("loài vật");send("Truyện về loài vật");int previousWrites=writes.get();
        current("getProducts",Map.of("reference","first","field","explanation"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đó là truyện chữ hay truyện tranh? Vì sao?");
        assertThat(ids(response)).containsExactly(8L);
        assertThat(response.getAnswer()).contains("đây là truyện chữ (truyện dài)","ấn bản");
        assertThat(response.getAnswer()).doesNotContain("Mục tiêu đọc:","Để bạn cân nhắc kỹ hơn");
        assertThat(writes.get()).isEqualTo(previousWrites);
        assertThat(state.toolSearch).containsEntry("query","loài vật");
    }
    @Test void communicationQuestionDoesNotConfuseStorytellingWithSkillsTraining() {
        catalog.add(book(8,"Chuyện trường học","Cô bé đến trường học trong toa xe điện và được thầy hiệu trưởng lắng nghe.",50000));
        topics("trường học");send("Truyện về trường học");int previousWrites=writes.get();
        current("getProducts",Map.of("reference","first","field","explanation"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đó có phải sách dạy kỹ năng giao tiếp không?");
        assertThat(ids(response)).containsExactly(8L);
        assertThat(response.getAnswer()).contains("chưa có căn cứ xếp cuốn này vào sách dạy kỹ năng giao tiếp", "Cô bé");
        assertThat(response.getAnswer()).doesNotContain("Tác giả:","Mình giải thích dựa trên");
        assertThat(writes.get()).isEqualTo(previousWrites);
        assertThat(state.toolSearch).containsEntry("query","trường học");
    }
    @Test void specificFactAnswersDoNotInferFormatOrTrainingFromAudienceAndStyle() {
        assertThat(ChatBookFacts.asksDirectFacts("Nội dung truyện tranh này nói về gì?",language)).isFalse();
        assertThat(ChatBookFacts.asksDirectFacts("Cuốn đó có phải truyện tranh không?",language)).isTrue();
        assertThat(ChatBookFacts.directFacts(Set.of("format"),Map.of("audience","Độc giả văn học", "readingStyle","Kể chuyện sinh động"),language))
            .singleElement().asString().contains("chưa có thông tin xác nhận rõ");
        assertThat(ChatBookFacts.directFacts(Set.of("format"),Map.of("summary","Truyện tranh chuyển thể từ tiểu thuyết."),language))
            .singleElement().asString().contains("chưa có thông tin xác nhận rõ");
        assertThat(ChatBookFacts.directFacts(Set.of("format"),Map.of("summary","Bộ truyện tranh về các chuyến phiêu lưu."),language))
            .singleElement().asString().contains("Đây là truyện tranh");
        assertThat(ChatBookFacts.directFacts(Set.of("communicationInstruction"),Map.of("summary","Nguyên tắc giao tiếp và ứng xử."),language))
            .singleElement().asString().startsWith("Có,");
        assertThat(ChatBookFacts.directFacts(Set.of("communicationInstruction"),Map.of("summary","Chưa xác nhận nội dung hướng dẫn giao tiếp.","readingStyle","Kể chuyện về ứng xử"),language))
            .singleElement().asString().contains("chưa có căn cứ");
    }
    @Test void additionalStorySubjectsRemainConjunctiveAndKeepOriginalBudget() {
        catalog.add(book(8,"Tuổi thơ A","Truyện dài hồi tưởng ký ức tuổi thơ.",60000));
        catalog.add(book(9,"Tuổi thơ B","Truyện dài về tuổi thơ, nỗi đau và nghèo khó.",95000));
        catalog.add(book(10,"Tuổi thơ C","Truyện dài về tuổi thơ, nỗi đau và nghèo khó.",110000));
        topics("truyện tuổi thơ");nextSet.put("maxPrice",100000).put("maxPriceExclusive",true);
        send("Truyện tuổi thơ dưới 100k");
        nextMode="refine";nextSet=mapper.createObjectNode();
        nextSet.set("topics",mapper.valueToTree(List.of("truyện tuổi thơ","nỗi đau","nghèo khó")));nextSet.put("topicMatch","all");
        var response=send("Mình muốn có cả nỗi đau và hoàn cảnh nghèo khó.");
        assertThat(ids(response)).containsExactly(9L);
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        assertThat(state.discoveryExact).containsExactly(9L);
    }

    @Test void specificTechnologyQuestionsAnswerEachTopicWithoutRepeatingTheWholeSummary() {
        catalog.add(book(8,"Nền tảng mạng","Tài liệu giới thiệu mô hình OSI, TCP/IP và Internet. Chưa xác nhận có cập nhật chuẩn mạng mới.",50000));
        state.focus=8L;state.initialized=true;current("getProducts",Map.of("productId",8,"field","explanation"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đó có dạy TCP/IP, Wi-Fi 6 và 5G không?");
        assertThat(ids(response)).containsExactly(8L);
        assertThat(response.getAnswer()).contains("TCP/IP", "Wi-Fi 6", "5G", "chưa có thông tin đã xác nhận");
        assertThat(response.getAnswer()).doesNotContain("Tác giả:", "Giá sau giảm:");
        assertThat(writes.get()).isZero();
        assertThat(state.focus).isEqualTo(8L);
    }
    @Test void genericContainsQuestionDoesNotInferFrameworkFromProgrammingDomain() {
        catalog.add(book(8,"Lập trình dịch vụ","Tài liệu về lập trình dịch vụ và thiết kế hệ thống.",50000));
        state.focus=8L;state.initialized=true;current("getProducts",Map.of("productId",8,"field","detail"));nextIntent="PRODUCT_INFO";
        var response=send("Sách có Kubernetes không?");
        assertThat(response.getAnswer()).contains("Kubernetes", "chưa có thông tin đã xác nhận");
        assertThat(writes.get()).isZero();
    }
    @Test void illustrationAndAgeQuestionsStateEditionLimitsDirectly() {
        catalog.add(book(8,"Truyện dân gian","Công trình sưu tầm truyện dân gian. Chưa xác định tập cụ thể. Không mặc định đây là bản minh họa dành riêng cho trẻ nhỏ.",50000));
        state.focus=8L;state.initialized=true;current("getProducts",Map.of("productId",8,"field","explanation"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đó là bản minh họa cho bé 5 tuổi hả?");
        assertThat(response.getAnswer()).contains("minh họa", "5 tuổi", "chưa có thông tin");
        assertThat(writes.get()).isZero();
    }
    @Test void broadPlannerTopicCannotDiluteExplicitSatireRequirement() {
        catalog.add(book(8,"Trào phúng A","Tiểu thuyết trào phúng châm biếm những điều lố bịch trong xã hội.",50000));
        catalog.add(book(9,"Hiện thực B","Truyện ngắn về bi kịch người nông dân trong xã hội cũ.",50000));
        topics("truyện");
        var response=send("Muốn đọc truyện trào phúng phê phán xã hội Việt Nam.");
        assertThat(ids(response)).containsExactly(8L);
        assertThat(state.discoveryExact).containsExactly(8L);
        assertThat(state.discoveryNearby).isEmpty();
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPrice",60000);
        assertThat(ids(send("Chỉ lấy cuốn dưới 60k nhé."))).containsExactly(8L);
    }
    @Test void confirmedTopicIsNotInferredFromAudienceOrUnconfirmedSentence() {
        catalog.add(book(8,"Nền tảng dữ liệu","Giới thiệu thiết kế cơ sở dữ liệu. Chưa xác nhận hướng dẫn SQL. Không bao gồm Kubernetes.",50000));
        state.focus=8L;state.initialized=true;current("getProducts",Map.of("productId",8,"field","detail"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đó có hướng dẫn SQL và Kubernetes không?");
        assertThat(response.getAnswer()).contains("SQL", "Kubernetes", "chưa có thông tin đã xác nhận");
        assertThat(response.getAnswer()).doesNotContain("đã xác nhận có đề cập");
    }

    @Test void ellipticalDetailFollowupKeepsFocusAndAnswersTheNamedTopic() {
        catalog.add(book(8,"Nền tảng mạng","Tài liệu giới thiệu TCP/IP và Internet.",50000));
        state.focus=8L;state.initialized=true;
        current("getProducts",Map.of("reference","focus","field","explanation"));nextIntent="PRODUCT_INFO";
        var response=send("Thế còn Bluetooth 5.3 thì sao?");
        assertThat(ids(response)).containsExactly(8L);
        assertThat(response.getAnswer()).contains("Bluetooth 5.3", "chưa có thông tin đã xác nhận");
        assertThat(response.getAnswer()).doesNotContain("Tác giả:","Giá sau giảm:");
        assertThat(writes.get()).isZero();
    }
    @Test void contentTopicsDoNotLoseBasicOrUppercaseAiToGenericWordFiltering() {
        catalog.add(book(8,"Kiến thức A","Tài liệu giới thiệu Java cơ bản và AI.",50000));
        state.focus=8L;state.initialized=true;
        current("getProducts",Map.of("reference","focus","field","detail"));nextIntent="PRODUCT_INFO";
        var response=send("Cuốn đó có dạy Java cơ bản và AI không?");
        assertThat(response.getAnswer()).contains("Java cơ bản", "AI", "đã xác nhận có đề cập");
        assertThat(writes.get()).isZero();
    }
    @Test void uncertainUnderstandingAsksForClarificationAndNamesOnlyTheFailedStage() {
        state.toolSearch.put("query","trinh thám");state.contextVersion=8;
        nextMode="refine";nextSet.put("query","phiêu lưu");badEvidence=true;
        var response=send("T muốn phiêu lưu");
        assertThat(response.isFallback()).isTrue();
        assertThat(response.getAnswer()).contains("chưa xác định chắc ý bạn","vẫn giữ các yêu cầu trước đó");
        assertThat(response.getExecution()).containsEntry("failureStage","understanding").containsEntry("status","FAILED");
        assertThat(state.contextVersion).isEqualTo(8);assertThat(state.toolSearch).containsOnlyKeys("query");
        verify(ai).endTurnBudget();
    }
    @Test void providerOutageUsesARetryMessageWithoutPretendingTheUserWasUnclear() {
        state.toolSearch.put("query","trinh thám");
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenThrow(new IllegalStateException("provider unavailable"));
        var response=send("Giữ hướng trinh thám nhé");
        assertThat(response.getAnswer()).contains("trục trặc khi đọc yêu cầu").doesNotContain("nói rõ hơn");
        assertThat(response.getExecution()).containsEntry("failureStage","understanding");
        assertThat(state.toolSearch).containsEntry("query","trinh thám");assertThat(state.contextVersion).isZero();
    }
    @Test void databaseFailureReportsExecutionStageAndPreservesTheLastSelection() {
        topics("trinh thám");send("Trinh thám");
        var before=mapper.valueToTree(state);var selected=new ArrayList<>(state.shortlist);
        nextMode="refine";nextSet=mapper.createObjectNode().put("preferredQuery","truyện chữ");
        when(products.findByStatusTrue()).thenReturn(catalog).thenThrow(new IllegalStateException("DB unavailable"));
        var response=send("Mình thích truyện chữ hơn");
        assertThat(response.getExecution()).containsEntry("failureStage","execution").containsEntry("status","FAILED");
        assertThat(response.getAnswer()).contains("chưa lấy được đầy đủ thông tin");
        assertThat((JsonNode)mapper.valueToTree(state)).isEqualTo(before);assertThat(state.shortlist).isEqualTo(selected);
    }
    @Test void failedOptionalWriterKeepsRetrievedBooksAndCommitsContextWithoutRetryingUnderstanding() {
        topics("trinh thám");nextSet.put("maxPrice",100000);writerFailure=true;
        var response=send("Trinh thám, tối đa 100k");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isNotEmpty();
        assertThat(response.getAnswer()).contains("100.000đ mỗi cuốn").doesNotContain("chưa xử lý được");
        assertThat(state.contextVersion).isEqualTo(1);assertThat(state.toolSearch).containsEntry("maxPrice",100000);
        assertThat(reads.get()).isEqualTo(1);assertThat(writes.get()).isEqualTo(1);
        assertThat(response.getExecution()).doesNotContainKey("failureStage");
        for(String name:List.of("understandingMs","executionMs","narrationMs"))
            assertThat(Long.parseLong(response.getExecution().get(name))).isGreaterThanOrEqualTo(0);
        verify(ai).beginStageBudget(8);verify(ai).endStageBudget();
    }
    @Test void malformedWriterJsonAlsoKeepsTheSuccessfulCatalogResult() {
        topics("trinh thám");writerOverride="{bad JSON";
        var response=send("Trinh thám");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).hasSize(3);
        assertThat(state.contextVersion).isEqualTo(1);assertThat(reads.get()).isEqualTo(1);
    }
    @Test void extendedConversationKeepsExclusionsOrdinalsAndBudgetAcrossPoliciesThenResetsOnNewTopic() {
        topics("trinh thám");nextSet.put("maxPrice",100000);nextSet.put("limit",3);
        send("3 cuốn trinh thám, mỗi cuốn tối đa 100k");
        nextMode="refine";nextSet=mapper.createObjectNode().put("excludedQuery","Vụ án A");
        var filtered=send("Bỏ Vụ án A nhé");assertThat(ids(filtered)).doesNotContain(1L);
        List<Long> selection=ids(filtered);assertThat(selection).hasSize(3);
        current("getProducts",Map.of("reference","second","field","price"));nextIntent="PRODUCT_INFO";
        var second=send("Giá cuốn thứ hai bao nhiêu?");assertThat(ids(second)).containsExactly(selection.get(1));
        for(String message:List.of("Phí giao hàng?","Thanh toán khi nhận hàng được không?","Bao lâu nhận được?","Nếu sách lỗi thì sao?")) {
            current("getPolicy",Map.of());nextIntent="POLICY";send(message);
            assertThat(state.toolSearch).containsEntry("maxPrice",100000).containsEntry("excludedQuery","Vụ án A");
            assertThat(state.shortlist).isEqualTo(selection);
        }
        current("getProducts",Map.of("reference","second","field","author"));nextIntent="PRODUCT_INFO";
        assertThat(ids(send("Quay lại cuốn thứ hai vừa chọn, tác giả là ai?"))).containsExactly(selection.get(1));
        nextMode="refine";nextIntent="DISCOVER_BOOKS";nextTool="recommendBooks";nextArgs=mapper.createObjectNode();
        nextSet=mapper.createObjectNode().put("maxPrice",60000);
        var cheaper=send("Giảm còn tối đa 60k, giữ phần đã loại");
        assertThat(cheaper.getProducts()).allMatch(p->p.getPrice()<=60000);assertThat(ids(cheaper)).doesNotContain(1L);
        nextMode="new";nextSet=mapper.createObjectNode();topics("phiêu lưu");
        var changed=send("Đổi hẳn sang phiêu lưu, bỏ mức giá cũ");
        assertThat(ids(changed)).containsExactly(5L);
        assertThat(state.toolSearch).doesNotContainKeys("maxPrice","excludedQuery","limit");
        assertThat(state.contextVersion).isEqualTo(10);
    }

    void topicCatalog() {
        catalog.add(book(7,"Hành trình B","Truyện phiêu lưu khám phá một vùng đất mới.",50000));
        catalog.add(book(8,"Khoa học A","Sách khoa học tìm hiểu thế giới vật lý.",60000));
        catalog.add(book(9,"Khoa học B","Sách khoa học tìm hiểu thế giới vật lý.",70000));
    }
    ChatResponseDto offer(String labels) {
        nextTool="respond";
        nextArgs=mapper.createObjectNode().put("kind","clarify").put("topic","preferences")
            .put("suggestedTopics",labels).put("suggestionSource","current");
        return send("Mình chuẩn bị nghỉ hè, muốn mua sách mà chưa biết chọn chủ đề nào"
            +(nextSet.has("maxPrice")?", không quá "+nextSet.path("maxPrice").asLong()+" đồng":""));
    }
    @Test void threeShownDirectionsEachHavePreparedBooksWithoutPollutingDiscoveryMemory() {
        topicCatalog();nextSet.put("maxPrice",100000);
        var response=offer("Truyện trinh thám|Truyện phiêu lưu|Khoa học");
        assertThat(response.isFallback()).isFalse();assertThat(response.getProducts()).isEmpty();
        assertThat(response.getChips()).containsExactly("Truyện trinh thám","Truyện phiêu lưu","Khoa học");
        assertThat(state.preparedTopics).hasSize(3);
        assertThat(state.preparedTopics.values()).allMatch(p->p.rankedIds().size()>=2);
        assertThat(state.displayed).isEmpty();assertThat(state.shortlist).isEmpty();
        assertThat(state.shownProductIds).isEmpty();assertThat(state.discoverySeeds).isEmpty();
        assertThat(state.toolSearch).doesNotContainKeys("query","topics");assertThat(writes.get()).isZero();
    }
    @Test void cachedChoiceSkipsBothAiCallsAndRetrievalRankingAndUsesSelectedTopicOnly() {
        topicCatalog();nextSet.put("maxPrice",100000);
        offer("Truyện trinh thám|Truyện phiêu lưu|Khoa học");
        int beforeReads=reads.get();clearInvocations(data);
        var response=send("Hướng thứ hai nhé");
        assertThat(response.isFallback()).isFalse();assertThat(ids(response)).containsExactly(5L,7L);
        assertThat(response.getExecution()).containsEntry("preparedTopic","hit");
        assertThat(reads.get()).isEqualTo(beforeReads);assertThat(writes.get()).isZero();
        verify(data,never()).rankByMeaning(anyString(),anyList());
        assertThat(state.toolSearch).containsEntry("query","Truyện phiêu lưu");
        assertThat(state.preparedTopics).isEmpty();assertThat(state.topicSuggestions).isEmpty();
        assertThat(state.shownProductIds).containsExactlyInAnyOrder(5L,7L);
    }
    @Test void thirdDirectionUsesItsOwnPreparedPoolAndKnownPrices() {
        topicCatalog();offer("Truyện trinh thám|Truyện phiêu lưu|Khoa học");
        var response=send("3");
        assertThat(ids(response)).containsExactly(8L,9L);
        assertThat(response.getExecution()).containsEntry("preparedTopic","hit");
        assertThat(response.getProducts().stream().map(ChatProductDto::getPrice).toList()).containsExactly(60000L,70000L);
    }
    @Test void unsupportedAndSingleBookTopicsNeverBecomeVisibleChips() {
        var response=offer("Truyện trinh thám|Truyện phiêu lưu|Thiên văn học");
        assertThat(response.getChips()).containsExactly("Truyện trinh thám");
        assertThat(response.getAnswer()).doesNotContain("• Truyện phiêu lưu","• Thiên văn học");
        assertThat(state.preparedTopics).containsOnlyKeys("Truyện trinh thám");
        assertThat(send("1").getExecution()).containsEntry("preparedTopic","hit");
    }
    @Test void noAvailableTopicAsksWithoutFakeChoicesOrRelaxingConstraints() {
        catalog.clear();nextSet.put("maxPrice",10000);
        var response=offer("Khoa học|Quản lý thời gian");
        assertThat(response.isFallback()).isFalse();assertThat(response.getChips()).isEmpty();
        assertThat(response.getAnswer()).contains("chưa tìm được vài cuốn","Bạn muốn đọc về điều gì");
        assertThat(state.preparedTopics).isEmpty();assertThat(state.toolSearch).containsEntry("maxPrice",10000);
    }
    @Test void exclusivePriceBoundsApplyBeforeOfferingAndPreparingTopics() {
        nextSet.put("maxPrice",60000).put("maxPriceExclusive",true);
        assertThat(offer("Truyện trinh thám|Truyện phiêu lưu").getChips()).isEmpty();
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPriceExclusive",false);
        // Evidence for a boolean boundary does not parse money; existing maxPrice remains.
        assertThat(offer("Truyện trinh thám|Truyện phiêu lưu").getChips()).containsExactly("Truyện trinh thám");
    }
    @Test void totalBudgetMustFitAtLeastTwoBooksBeforeTopicIsOffered() {
        nextSet.put("totalBudget",70000);
        nextTool="respond";nextArgs=mapper.createObjectNode().put("kind","clarify").put("topic","preferences")
            .put("suggestedTopics","Truyện trinh thám|Truyện phiêu lưu").put("suggestionSource","current");
        assertThat(send("Mình chuẩn bị nghỉ hè, muốn mua sách tổng 70k mà chưa biết chủ đề").getChips()).isEmpty();
        nextMode="refine";nextSet=mapper.createObjectNode();nextSet.put("totalBudget",80000);
        // Reuse the actual amount as current user evidence through a dedicated request.
        nextArgs.put("suggestionSource","previous1");
        state.recentDialogue.add(Map.of("user","Mình chuẩn bị nghỉ hè","assistant","Ừ"));
        var response=send("Tổng không quá 80k, mình chưa biết chọn chủ đề");
        assertThat(response.getChips()).containsExactly("Truyện trinh thám");
        response=send("Truyện trinh thám");
        assertThat(response.getExecution()).containsEntry("preparedTopic","hit");
        assertThat(response.getProducts().stream().mapToLong(ChatProductDto::getPrice).sum()).isLessThanOrEqualTo(80000);
    }
    @Test void expiredPreviewFallsBackToFreshRetrieval() {
        offer("Truyện trinh thám");
        var saved=state.preparedTopics.get("Truyện trinh thám");
        state.preparedTopics.put("Truyện trinh thám",new ChatPreparedTopics.Selection(
            System.currentTimeMillis()-ChatPreparedTopics.TTL_MS-1,saved.constraints(),saved.rankedIds(),saved.revisions()));
        var response=send("1");
        assertThat(response.getExecution()).containsEntry("preparedTopic","miss");
        assertThat(response.getProducts()).isNotEmpty();assertThat(writes.get()).isEqualTo(1);
    }
    @Test void priceChangeInvalidatesPreviewAndFreshResultKeepsBudget() {
        nextSet.put("maxPrice",100000);offer("Truyện trinh thám");
        catalog.get(0).setPrice(150000.0);
        var response=send("1");
        assertThat(response.getExecution()).containsEntry("preparedTopic","miss");
        assertThat(ids(response)).doesNotContain(1L);assertThat(response.getProducts()).allMatch(b->b.getPrice()<=100000);
    }
    @Test void stockChangeInvalidatesPreviewAndNeverServesUnavailableBook() {
        offer("Truyện trinh thám");catalog.get(0).setQuantity(0);
        var response=send("1");
        assertThat(response.getExecution()).containsEntry("preparedTopic","miss");
        assertThat(ids(response)).doesNotContain(1L);assertThat(response.getProducts()).isNotEmpty();
    }
    @Test void unpublishedBookAndChangedContentInvalidatePreparedPool() {
        offer("Truyện trinh thám");catalog.get(0).setStatus(false);
        catalog.get(1).setDescription("Tài liệu hướng dẫn nấu ăn");catalog.get(1).setName("Nấu ăn");
        var response=send("1");
        assertThat(response.getExecution()).containsEntry("preparedTopic","miss");
        assertThat(ids(response)).doesNotContain(1L,2L);
    }
    @Test void additionalConstraintUsesAiAndFreshSearchRatherThanStalePreparedPool() {
        topicCatalog();offer("Truyện trinh thám|Truyện phiêu lưu|Khoa học");
        nextMode="refine";nextSet=mapper.createObjectNode().put("maxPrice",30000);topics("trinh thám");
        nextTool="recommendBooks";nextArgs=mapper.createObjectNode();
        var response=send("Hướng thứ nhất nhưng chỉ dưới 30k");
        assertThat(reads.get()).isEqualTo(2);assertThat(ids(response)).containsExactly(3L);
        assertThat(response.getExecution()).doesNotContainEntry("preparedTopic","hit");assertThat(state.preparedTopics).isEmpty();
    }
    @Test void duplicateBookPoolsDoNotFillMenuWithEquivalentTopics() {
        var response=offer("Truyện trinh thám|Trinh thám|Điều tra phá án");
        assertThat(response.getChips()).containsExactly("Truyện trinh thám");
    }
    @Test void preparedPoolsSurviveStateSerializationAndStayOwnedByConversation() throws Exception {
        offer("Truyện trinh thám");
        ChatDialogueMemory.State restored=mapper.readValue(mapper.writeValueAsBytes(state),ChatDialogueMemory.State.class);
        assertThat(restored.preparedTopics).hasSize(1);
        var request=request("1");var response=pipeline.reply(request,restored);
        assertThat(response.getExecution()).containsEntry("preparedTopic","hit");
        assertThat(state.preparedTopics).hasSize(1);
        ChatDialogueMemory.State other=new ChatDialogueMemory.State();
        assertThat(other.preparedTopics).isEmpty();assertThat(other.topicSuggestions).isEmpty();
    }
    @Test void newNeedDiscardsPreviousPreparedChoices() {
        offer("Truyện trinh thám");nextMode="new";nextSet=mapper.createObjectNode();topics("phiêu lưu");
        nextTool="recommendBooks";nextArgs=mapper.createObjectNode();send("Nhu cầu mới: phiêu lưu");
        assertThat(state.preparedTopics).isEmpty();assertThat(state.topicSuggestions).isEmpty();
    }
    @Test void requestedOneBookCanChooseTopicWithMultipleAvailableBooks() {
        nextSet.put("limit",1);nextTool="respond";
        nextArgs=mapper.createObjectNode().put("kind","clarify").put("topic","preferences")
            .put("suggestedTopics","Truyện trinh thám").put("suggestionSource","current");
        var offered=send("Mình chuẩn bị nghỉ hè, muốn 1 cuốn nhưng chưa biết chủ đề");
        assertThat(offered.getChips()).containsExactly("Truyện trinh thám");
        var selected=send("1");assertThat(selected.getProducts()).hasSize(1);
        assertThat(selected.getExecution()).containsEntry("preparedTopic","hit");
    }

    @Test void topicsExcludedByCustomerAreNotPreparedOrOffered() {
        nextSet.put("excludedQuery","trinh thám");
        var response=offer("Truyện trinh thám|Truyện phiêu lưu");
        assertThat(response.getChips()).isEmpty();assertThat(state.preparedTopics).isEmpty();
        assertThat(state.toolSearch).containsEntry("excludedQuery","trinh thám");
    }
    @Test void ratingConstraintIsCheckedWhenPreparingAndAgainWhenRatingsChange() {
        RateRepository rates=mock(RateRepository.class);data.setRates(rates);
        when(rates.averageRatings()).thenReturn(List.of(new Object[]{1L,4.8},new Object[]{2L,4.6},new Object[]{3L,3.0}));
        nextSet.put("minRating",4.5);nextTool="respond";
        nextArgs=mapper.createObjectNode().put("kind","clarify").put("topic","preferences")
            .put("suggestedTopics","Truyện trinh thám").put("suggestionSource","current");
        var offered=send("Mình chuẩn bị nghỉ hè, muốn sách từ 4.5 sao nhưng chưa biết chủ đề");
        assertThat(offered.getChips()).containsExactly("Truyện trinh thám");
        when(rates.averageRatings()).thenReturn(List.of(new Object[]{1L,4.8},new Object[]{2L,4.2},new Object[]{3L,3.0}));
        var selected=send("1");assertThat(selected.getExecution()).containsEntry("preparedTopic","miss");
        assertThat(ids(selected)).containsExactly(1L);
    }

    @Test void scienceMentionInSpiritualBookCannotProveScienceTopicWithoutAnApprovedSubjectLabel() {
        topicCatalog();
        catalog.add(book(10,"Hành trình tâm linh","Hành trình tâm linh có những trao đổi về khoa học.",50000));
        doReturn(Map.of(8L,List.of("Chủ đề: khoa học"),9L,List.of("Chủ đề: khoa học"),
            10L,List.of("Thể loại: tâm linh","Chủ đề: khoa học và tâm linh")))
            .when(data).topicLabels(anyList());
        var offered=offer("Khoa học");assertThat(offered.getChips()).containsExactly("Khoa học");
        var selected=send("1");assertThat(ids(selected)).containsExactly(8L,9L);
        assertThat(selected.getExecution()).containsEntry("preparedTopic","hit");
    }
    @Test void editorialLabelChangeInvalidatesPreviewEvenWhenSummaryStaysUnchanged() {
        topicCatalog();
        doReturn(Map.of(8L,List.of("Chủ đề: khoa học"),9L,List.of("Chủ đề: khoa học")))
            .when(data).topicLabels(anyList());
        offer("Khoa học");
        doReturn(Map.of(8L,List.of("Chủ đề: khoa học"),9L,List.of("Thể loại: tâm linh")))
            .when(data).topicLabels(anyList());
        assertThat(send("1").getExecution()).containsEntry("preparedTopic","miss");
    }

    String sixTopicCatalog() {
        topicCatalog();
        for(int i=0;i<2;i++) {
            catalog.add(book(20+i,"Lịch sử "+i,"Sách lịch sử Việt Nam qua các thời kỳ.",50000));
            catalog.add(book(30+i,"Học lập trình "+i,"Hướng dẫn lập trình và phát triển phần mềm.",50000));
            catalog.add(book(40+i,"Kinh tế "+i,"Sách kinh tế về thị trường và doanh nghiệp.",50000));
        }
        return "Truyện trinh thám|Truyện phiêu lưu|Khoa học|Lịch sử|Lập trình|Kinh tế";
    }

    @Test void sixVerifiedTopicsCanBeChosenByFourthAndSixthOrdinal() {
        String labels=sixTopicCatalog();
        for(String topic:List.of("Lịch sử","Lập trình","Kinh tế"))
            assertThat(new ChatCatalogQuery(language,data).search(topic,ChatSearchCriteriaDto.empty(),catalog)).as(topic).hasSize(2);
        var offered=offer(labels);
        assertThat(offered.getChips()).hasSize(6);
        assertThat(offered.getAnswer()).contains("4. Lịch sử","6. Kinh tế").doesNotContain("ít nhất 2 cuốn");
        assertThat(send("Mình chọn chủ đề thứ tư.").getProducts()).extracting(ChatProductDto::getId).containsExactly(20L,21L);
        assertThat(send("Chủ đề thứ sáu nhé").getProducts()).extracting(ChatProductDto::getId).containsExactly(40L,41L);
    }

    @Test void recallingTopicsAfterSelectionAndPolicyUsesExactSavedMenuWithoutProvider() throws Exception {
        var offered=offer(sixTopicCatalog());List<String> menu=List.copyOf(offered.getChips());
        send("Hướng thứ hai nhé");
        // Simulate recreating the conversation cache from durable JSON.
        state=mapper.readValue(mapper.writeValueAsBytes(state),ChatDialogueMemory.State.class);
        nextMode="current";nextTool="getPolicy";nextArgs=mapper.createObjectNode();nextSet=mapper.createObjectNode();
        send("Phí giao hàng bao nhiêu?");
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenThrow(new IllegalStateException("AI offline"));
        int before=reads.get();
        var recalled=send("Bạn nhắc lại các chủ đề lúc nãy được không?");
        assertThat(recalled.isFallback()).isFalse();assertThat(recalled.getChips()).containsExactlyElementsOf(menu);
        assertThat(recalled.getProducts()).isEmpty();assertThat(recalled.getActions()).isEmpty();
        assertThat(state.toolSearch).containsEntry("query","Truyện phiêu lưu");
        assertThat(recalled.getAnswer()).doesNotContain("Bạn có nhắc","ít nhất 2","Bạn chọn");
        assertThat(reads.get()).isEqualTo(before);
    }

    @Test void bareBookOrdinalAfterSelectionDoesNotPickArchivedTopic() {
        offer(sixTopicCatalog());send("Hướng thứ hai nhé");
        assertThat(ChatTopicClarification.choice("2",state)).isEmpty();
        assertThat(ChatTopicClarification.choice("cuốn thứ hai",state)).isEmpty();
        assertThat(ChatTopicClarification.choice("chủ đề thứ tư",state)).isEqualTo("Lịch sử");
    }

    @Test void genericMenuUsesMoreCatalogDirectionsWithoutInventingPersonalContext() {
        sixTopicCatalog();
        doReturn(List.of("Truyện trinh thám","Truyện phiêu lưu","Khoa học","Lịch sử","Lập trình","Kinh tế"))
            .when(data).topicSuggestionNames(anyList());
        var offered=send("Mình chưa biết đọc gì, bạn giới thiệu các chủ đề đang có đi.");
        assertThat(offered.isFallback()).isFalse();assertThat(offered.getChips()).hasSize(6);
        assertThat(offered.getAnswer()).doesNotContain("Bạn có nhắc","công sở");
        assertThat(offered.getProducts()).isEmpty();assertThat(state.toolSearch).doesNotContainKeys("topics","query");
        assertThat(reads.get()).isZero();
    }

    @Test void moreTopicsNeverReplaysNamesAndExhaustionStillAllowsRecall() {
        var offered=offer(sixTopicCatalog());
        state.topicMenuContext="";
        doReturn(List.copyOf(offered.getChips())).when(data).topicSuggestionNames(anyList());
        var extra=send("Cho mình thêm chủ đề khác nữa.");
        assertThat(extra.isFallback()).isFalse();
        assertThat(extra.getChips()).doesNotContainAnyElementsOf(offered.getChips());
        if(extra.getChips().isEmpty()) {
            assertThat(extra.getAnswer()).contains("chưa tìm được hướng khác");
            assertThat(send("Nhắc lại các chủ đề lúc nãy").getChips()).containsExactlyElementsOf(offered.getChips());
        }
    }

    @Test void newRecipientClearsTopicMenuRatherThanReusingOldGiftSuggestions() {
        offer(sixTopicCatalog());
        nextMode="new";nextSet=mapper.createObjectNode().put("recipient","mẹ");
        nextTool="respond";nextArgs=mapper.createObjectNode().put("kind","clarify").put("topic","gift");
        catalog.clear();send("Giờ mua sách tặng mẹ, mẹ mình chưa biết muốn đọc gì");
        assertThat(state.topicMenus).isEmpty();assertThat(state.topicMenuContext).isEmpty();
        assertThat(send("Nhắc lại các chủ đề lúc nãy").getChips()).isEmpty();
    }

    @Test void noSavedTopicMenuDoesNotInventWhatWasPreviouslySaid() {
        var recalled=send("Bạn vừa giới thiệu những chủ đề nào nhỉ?");
        assertThat(recalled.isFallback()).isFalse();assertThat(recalled.getChips()).isEmpty();
        assertThat(recalled.getAnswer()).contains("chưa gợi ý danh sách chủ đề");
        assertThat(recalled.getProducts()).isEmpty();
    }

}
