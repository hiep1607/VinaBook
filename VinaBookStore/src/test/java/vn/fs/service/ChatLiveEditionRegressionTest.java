package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Live title-alias and unverified physical-edition failures; no database or provider calls. */
class ChatLiveEditionRegressionTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ChatIntentService language=new ChatIntentService();
    final AiGateway ai=mock(AiGateway.class);
    final ProductRepository products=mock(ProductRepository.class);
    final ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    final List<Product> catalog=new ArrayList<>();
    ChatStructuredPipeline pipeline;
    String nextTool="searchProducts",nextMode="new";
    Map<String,Object> nextSet=Map.of("title","Sherlock Holmes"),nextArgs=Map.of();
    boolean failProvider;

    @BeforeEach void setup() throws Exception {
        catalog.add(book(26,"Sherlock Holmes Toàn Tập - Tập 1",215000));
        when(products.findByStatusTrue()).thenReturn(catalog);
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("test");when(ai.isConfigured()).thenReturn(true);
        var data=new ChatRetrievalService(language);
        var tools=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        var registry=new ChatToolRegistry(List.of(tools.searchProducts(),tools.getProducts(),tools.findSimilarProducts(),tools.respond()));
        var analytics=mock(ChatAnalyticsService.class);
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("test-message");
        pipeline=new ChatStructuredPipeline(ai,mapper,new ChatTurnUnderstanding(ai,mapper,registry),registry,
            new ChatBookNarrator(ai,mapper,products,language),analytics,products,data);
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenAnswer(call->{
            if(failProvider)throw new IllegalStateException("provider unavailable");
            JsonNode input=mapper.readTree((String)call.getArgument(2));
            if(input.has("question"))return "{\"intro\":\"\",\"closing\":\"\",\"reasons\":[],\"preferredProductId\":null}";
            ObjectNode plan=mapper.createObjectNode().put("contextVersion",input.path("contextVersion").asLong())
                .put("intent",nextTool.equals("getProducts")?"PRODUCT_INFO":"DISCOVER_BOOKS").put("contextMode",nextMode);
            plan.set("set",mapper.valueToTree(nextSet));plan.set("clear",mapper.createArrayNode());
            plan.put("evidence",ChatTurnContext.CURRENT_MESSAGE);plan.putNull("memoryNote");
            plan.set("calls",mapper.valueToTree(List.of(Map.of("tool",nextTool,"arguments",nextArgs))));
            return mapper.writeValueAsString(plan);
        });
    }

    static Product book(long id,String title,double price) {
        return new Product(id,title,"Arthur Conan Doyle",110,price,10,"hardcover-looking-image.jpg",
            "Truyện trinh thám về các vụ án và suy luận của thám tử.",LocalDate.now(),true,0,new Category(1L,"Truyện"));
    }
    ChatResponseDto send(String message) {
        var request=new ChatRequestDto();request.setMessage(message);request.setSessionId("edition-regression");
        request.setRequestId(UUID.randomUUID().toString());return pipeline.reply(request,state);
    }

    @Test void abbreviatedExistingTitleIsMatchedAndHardcoverFollowupDoesNotBecomeSimilarTitleSearch() {
        var first=send("Cho mình xem Sherlock Holmes.");
        assertThat(first.isFallback()).isFalse();
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(26L);
        assertThat(first.getExecution()).containsEntry("outcome","matched");
        assertThat(state.shortlist).containsExactly(26L);
        failProvider=true;
        var edition=send("Bản bìa cứng ấy.");
        assertThat(edition.isFallback()).isEqualTo(failProvider);
        if(failProvider)assertThat(edition.getExecution()).containsEntry("recovery","basic");
        assertThat(edition.getExecution()).containsEntry("tool","getProducts");
        assertThat(edition.getProducts()).extracting(ChatProductDto::getId).containsExactly(26L);
        assertThat(state.focus).isEqualTo(26L);
        assertThat(edition.getAnswer()).contains("bìa cứng","chưa có thông tin","ấn bản đang bán")
            .doesNotContain("tên gần giống","là bìa cứng");
        assertThat(edition.getActions()).noneMatch(a->a.getType().equals("CHECKOUT_PRODUCT"));
    }

    @Test void exactTitleWinsOverALongerTitleSharingTheSameWords() {
        catalog.add(book(27,"Sherlock Holmes",120000));
        assertThat(send("Cho mình xem Sherlock Holmes.").getProducts()).extracting(ChatProductDto::getId).containsExactly(27L);
    }

    @Test void aliasStillAppliesBudgetAndDoesNotTreatANonexistentLongerTitleAsTheKnownBook() {
        nextSet=Map.of("title","Sherlock Holmes","maxPrice",100000);
        assertThat(send("Cho mình xem Sherlock Holmes dưới 100k.").getProducts()).isEmpty();
        nextSet=Map.of("title","Sherlock Holmes Và Vụ Án Không Tồn Tại");
        assertThat(send("Cho mình xem Sherlock Holmes Và Vụ Án Không Tồn Tại.").getProducts()).isEmpty();
    }

    @Test void multipleAliasEditionsAreShownWithoutChoosingTheirCoverType() {
        catalog.add(book(27,"Sherlock Holmes Toàn Tập - Tập 2",180000));
        var first=send("Cho mình xem Sherlock Holmes.");
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(26L,27L);
        failProvider=true;
        var edition=send("Bản bìa cứng ấy.");
        assertThat(edition.isFallback()).isEqualTo(failProvider);
        if(failProvider)assertThat(edition.getExecution()).containsEntry("recovery","basic");
        assertThat(edition.getProducts()).isEmpty();
        assertThat(edition.getAnswer()).doesNotContain("là bìa cứng");
        assertThat(state.focus).isNull();
    }

    @Test void editionQuestionWithAnExplicitAliasWorksBeforeTheBookHasBeenDisplayed() {
        nextTool="getProducts";nextMode="current";nextSet=Map.of();
        nextArgs=Map.of("title","Sherlock Holmes","field","detail");
        var edition=send("Sherlock Holmes là bìa mềm hay bìa cứng?");
        assertThat(edition.isFallback()).isEqualTo(failProvider);
        if(failProvider)assertThat(edition.getExecution()).containsEntry("recovery","basic");
        assertThat(edition.getProducts()).extracting(ChatProductDto::getId).containsExactly(26L);
        assertThat(edition.getAnswer()).contains("chưa có thông tin","bìa cứng","bìa mềm");
    }

    @Test void explicitlyNamedCoverQuestionOverridesAnOlderFocusedBook() {
        catalog.add(book(57,"Đắc Nhân Tâm",100000));
        state.initialized=true;state.focus=57L;state.displayed.add(57L);state.shortlist.add(57L);
        failProvider=true;
        var edition=send("Sherlock Holmes bìa cứng không?");
        assertThat(edition.isFallback()).isEqualTo(failProvider);
        if(failProvider)assertThat(edition.getExecution()).containsEntry("recovery","basic");
        assertThat(edition.getProducts()).extracting(ChatProductDto::getId).containsExactly(26L);
        assertThat(state.focus).isEqualTo(26L);
        assertThat(edition.getAnswer()).contains("Sherlock Holmes","chưa có thông tin").doesNotContain("Đắc Nhân Tâm");
    }
}
