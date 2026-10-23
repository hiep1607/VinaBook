package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.*;
import vn.fs.entity.Category;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

class ChatRecommendationIntakeTest {
    private final ChatIntentService language = new ChatIntentService();
    private final ChatRetrievalService retrieval = new ChatRetrievalService(language);
    private final ChatRecommendationIntake intake = new ChatRecommendationIntake(language, retrieval);
    private final List<Product> catalog = List.of(new Product(57L,"Đắc Nhân Tâm","Dale Carnegie",100,
        86000D,18,"cover.jpg","Nghệ thuật giao tiếp và phát triển bản thân.",LocalDate.now(),true,220,new Category(1L,"Kỹ năng")));

    @Test void emptyRecommendationAsksOnlyForReadingNeed() {
        var state=new ChatDialogueMemory.State();
        var result=intake.prepare(request("Gợi ý sách hay","s"),state,catalog);
        assertThat(result.question()).contains("chủ đề").doesNotContain("cho mình hay cho ai", "ngân sách");
        assertThat(ChatRecommendationIntake.ready(state)).isFalse();
    }
    @Test void budgetAloneDoesNotSatisfyReadingNeed() {
        var state=new ChatDialogueMemory.State();
        intake.prepare(request("Gợi ý sách dưới 150k","s"),state,catalog);
        var next=intake.prepare(request("Ngân sách 200k","s"),state,catalog);
        assertThat(next.question()).contains("chủ đề").doesNotContain("cho mình hay cho ai");
        assertThat(state.recommendationNeed).isEmpty();
        assertThat(state.recommendationReader).isEmpty();
    }
    @Test void topicOnlyIsReadyWithoutInventingARecipient() {
        var state=new ChatDialogueMemory.State();
        var result=intake.prepare(request("Gợi ý sách trinh thám","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(ChatRecommendationIntake.ready(state)).isTrue();
        assertThat(state.recommendationReader).isEmpty();
        assertThat(result.message()).isEqualTo("Gợi ý sách trinh thám");
    }
    @Test void readerOnlyAsksForNeedAndDoesNotInferFromGender() {
        var state=new ChatDialogueMemory.State();
        var result=intake.prepare(request("Gợi ý sách cho bạn nữ 20 tuổi","s"),state,catalog);
        assertThat(result.question()).contains("chủ đề gì").doesNotContain("tình yêu", "ngân sách");
        assertThat(state.recommendationNeed).isEmpty();
    }
    @Test void explicitSelfAndTopicRemainUsableWithoutBudget() {
        var state=new ChatDialogueMemory.State();
        var result=intake.prepare(request("Mình muốn đọc trinh thám","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(ChatRecommendationIntake.ready(state)).isTrue();
    }
    @Test void oldPendingRecipientQuestionResumesWithoutLosingBudget() {
        var state=new ChatDialogueMemory.State();
        state.recommendationNeed="giao tiếp";
        state.recommendationPending=true;
        state.recommendationTurns.add("Gợi ý sách giao tiếp dưới 150k");
        var result=intake.prepare(request("cho mình","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(result.message()).contains("giao tiếp", "150k", "cho mình");
        assertThat(state.recommendationPending).isFalse();
    }
    @Test void freeTextNeedCompletesPreviouslyNamedRecipient() {
        var state=new ChatDialogueMemory.State();
        intake.prepare(request("Gợi ý sách cho mẹ","s"),state,catalog);
        var result=intake.prepare(request("chăm sóc cây cảnh","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(result.message()).contains("cho mẹ", "cây cảnh");
    }
    @Test void uncertainAnswerNeverCountsAsNeed() {
        var state=new ChatDialogueMemory.State();
        intake.prepare(request("Gợi ý sách cho mình","s"),state,catalog);
        assertThat(intake.prepare(request("chưa biết nữa","s"),state,catalog).question()).contains("chủ đề gì");
    }
    @Test void assistantAndAccountPreferencesDoNotSatisfyIntake() {
        var state=new ChatDialogueMemory.State();
        state.clarificationFacts.put("subject","pháp luật");
        state.accountMemory.put("recipient","cho mình");
        var req=request("Gợi ý sách hay","s");
        req.setHistory(List.of(new ChatTurnDto("assistant","Mình muốn đọc sách giao tiếp")));
        assertThat(intake.prepare(req,state,catalog).question()).contains("chủ đề").doesNotContain("cho mình hay cho ai");
    }
    @Test void explicitCustomerHistorySuppliesReadingNeedWithoutRecipient() {
        var req=request("Gợi ý thêm sách","s");
        req.setHistory(List.of(new ChatTurnDto("user","Tìm sách giao tiếp")));
        var state=new ChatDialogueMemory.State();
        assertThat(intake.prepare(req,state,catalog).question()).isEmpty();
        assertThat(ChatRecommendationIntake.ready(state)).isTrue();
    }
    @Test void namedBookAndCommerceRemainUsableWithoutRecommendationIntake() {
        for(String text:List.of("Đắc Nhân Tâm nói về gì?","Tìm sách Đắc Nhân Tâm","mua cuốn này","trong giỏ hàng của tôi có gì","Phí giao hàng thế nào?","So sánh Đắc Nhân Tâm và Nhà Giả Kim"))
            assertThat(intake.prepare(request(text,"s"),new ChatDialogueMemory.State(),catalog).question()).isEmpty();
    }
    @Test void newRecipientDoesNotInheritPreviousReadersNeed() {
        var state=new ChatDialogueMemory.State();
        intake.prepare(request("Mình muốn đọc giao tiếp","s"),state,catalog);
        var result=intake.prepare(request("Gợi ý sách cho bé 8 tuổi","s"),state,catalog);
        assertThat(result.question()).contains("chủ đề gì");
        assertThat(state.recommendationNeed).isEmpty();
    }
    @Test void durableStateRetainsNeedAndBudgetWithoutRecipient() throws Exception {
        var state=new ChatDialogueMemory.State();
        intake.prepare(request("Gợi ý sách dưới 150k","s"),state,catalog);
        var mapper=new ObjectMapper();
        var restored=mapper.readValue(mapper.writeValueAsString(state),ChatDialogueMemory.State.class);
        assertThat(intake.prepare(request("giao tiếp","s"),restored,catalog).message()).contains("giao tiếp","150k");
    }
    @Test void configuredAiIsCalledAsSoonAsNeedIsKnownWithoutRecipient() {
        var ai=mock(AiGateway.class); when(ai.isConfigured()).thenReturn(true);
        var planner=mock(LegacyChatOrchestrator.class);
        when(planner.reply(any(),any())).thenReturn(new ChatResponseDto("Đã tìm sách.","gemini","model",false,List.of(),List.of()));
        var service=service(ai,mock(CurrentUserAccess.class)); service.setOrchestrator(planner);
        assertThat(service.reply(request("Gợi ý sách dưới 150k","intake-ai")).getProducts()).isEmpty();
        verifyNoInteractions(planner);
        service.reply(request("giao tiếp","intake-ai"));
        verify(planner).reply(argThat(r -> r.getMessage().contains("giao tiếp") && r.getMessage().contains("150k")),any());
    }
    @Test void localModeReturnsBooksImmediatelyWithTopicOnly() {
        var service=service(mock(AiGateway.class),mock(CurrentUserAccess.class));
        var result=service.reply(request("Gợi ý sách giao tiếp","intake-local"));
        assertThat(result.getProducts()).extracting("id").containsExactly(57L);
        assertThat(result.getAnswer()).doesNotContain("cho mình hay cho ai");
        assertThat(result.getCriteria().getMaxPrice()).isNull();
    }
    @Test void optionalRecipientAndBudgetAreStillRetained() {
        var state=new ChatDialogueMemory.State();
        var result=intake.prepare(request("Gợi ý truyện giải trí cho bé 8 tuổi, dưới 150k","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(state.recommendationReader).contains("be 8 tuoi");
        assertThat(result.message()).contains("bé 8 tuổi", "150k");
        assertThat(language.criteria(result.message(),catalog).getMaxPrice()).isEqualTo(150000L);
    }
    @Test void sessionsAndOwnersNeverShareRequiredFacts() {
        var access=mock(CurrentUserAccess.class);
        var service=service(mock(AiGateway.class),access);
        when(access.currentUserId()).thenReturn(1L);
        assertThat(service.reply(request("Mình muốn đọc giao tiếp","same-session")).getProducts()).isNotEmpty();
        assertThat(service.reply(request("Gợi ý sách","other-session")).getProducts()).isEmpty();
        when(access.currentUserId()).thenReturn(2L);
        assertThat(service.reply(request("Gợi ý sách","same-session")).getAnswer()).contains("chủ đề").doesNotContain("cho mình hay cho ai");
    }

    @Test void ageIsNotAnOptionalBudget() {
        assertThat(language.criteria("Gợi ý truyện giải trí cho bé khoảng 8 tuổi",List.of()).getMaxPrice()).isNull();
        assertThat(language.criteria("Gợi ý truyện cho bé khoảng 8 tuổi, dưới 150k",List.of()).getMaxPrice()).isEqualTo(150000L);
    }
    @Test void relatedBookIsReadyWithoutRecipientWhenAnchorIsKnown() {
        var state=new ChatDialogueMemory.State();
        var result=intake.prepare(request("Gợi ý sách tương tự Đắc Nhân Tâm","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(ChatRecommendationIntake.ready(state)).isTrue();
        assertThat(state.recommendationReader).isEmpty();
    }
    @Test void updatedOptionalBudgetReplacesOldBudgetDuringIntake() {
        var state=new ChatDialogueMemory.State();
        intake.prepare(request("Gợi ý sách khoảng 150k","s"),state,catalog);
        intake.prepare(request("Ngân sách tối đa 100k","s"),state,catalog);
        var result=intake.prepare(request("giao tiếp","s"),state,catalog);
        assertThat(result.question()).isEmpty();
        assertThat(state.recommendationReader).isEmpty();
        assertThat(result.message()).contains("100k").doesNotContain("150k");
        assertThat(language.criteria(result.message(),catalog).getMaxPrice()).isEqualTo(100000L);
        assertThat(language.criteria(result.message(),catalog).getMinPrice()).isNull();
    }
    private ChatService service(AiGateway ai,CurrentUserAccess access) {
        var repository=mock(ProductRepository.class); when(repository.findByStatusTrue()).thenReturn(catalog);
        var analytics=mock(ChatAnalyticsService.class);
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("message-id");
        return new ChatService(repository,ai,language,retrieval,new ChatPolicyService(language),mock(ChatOrderSupportService.class),analytics,access);
    }
    private ChatRequestDto request(String text,String session) {
        var req=new ChatRequestDto(); req.setMessage(text);req.setSessionId(session);return req;
    }
}
