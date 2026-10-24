package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import vn.fs.dto.chat.ChatRequestDto;

class ChatTopicClarificationTest {
    ObjectMapper mapper=new ObjectMapper();
    ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    ObjectNode args=mapper.createObjectNode().put("kind","clarify").put("topic","preferences")
        .put("suggestionContext","Mình sắp đi làm lần đầu")
        .put("suggestedTopics","Giao tiếp công sở|Quản lý thời gian|Tài chính cá nhân");

    ChatTool.Result ask(String message) {
        var request=new ChatRequestDto();request.setMessage(message);
        return ChatTopicClarification.ask(args,new ChatTool.Context(request,state),topic->true);
    }
    void prior(String user,String assistant) {state.recentDialogue.add(Map.of("user",user,"assistant",assistant));}

    @Test void sourceIdRestoresActualPriorUserTextInsteadOfDiscardingParaphrasedQuote() {
        prior("Em gái mình sắp vào đại học nhưng chưa biết thích đọc gì","Có nhiều lựa chọn");
        args.put("suggestionSource","previous1").put("suggestionContext","Em ấy sắp vào đại học");
        var result=ask("Mua quà cho em ấy, bạn gợi ý vài chủ đề đi");
        assertThat(result.answer()).contains("Bạn có nhắc “Em gái mình sắp vào đại học nhưng chưa biết thích đọc gì”");
        assertThat(result.answer()).doesNotContain("Bạn có nhắc “Em ấy sắp vào đại học”");
    }
    @Test void unknownOrPrivateSourceIdDoesNotAuthorizeAnUnrelatedQuote() {
        prior("Mình sắp đi làm lần đầu, mail a@example.com","Mình sắp đi làm lần đầu");
        for(String id:List.of("previous1","previous4","unknown")) {
            args.put("suggestionSource",id);
            assertThat(ask("Chưa biết chủ đề nào cả").answer()).doesNotContain("Bạn có nhắc");
        }
    }
    @Test void offeredSourceIdsContainSpecificUserContextRatherThanGenericFollowupOrAssistantText() {
        prior("Em gái mình sắp vào đại học","Bạn là luật sư");
        var request=new ChatRequestDto();request.setMessage("Gợi ý vài hướng dựa trên hoàn cảnh mình đã kể đi");
        var sources=ChatTopicClarification.sources(request,state);
        assertThat(sources).containsExactly(Map.of("id","previous1","text","Em gái mình sắp vào đại học"));
    }
    @Test void simpleChoiceUsesOnlyRenderedOptionsAndDoesNotConfuseProductOrdinals() {
        var options=List.of("Giao tiếp công sở","Quản lý thời gian","Tài chính cá nhân");
        for(String choice:List.of("2","Hướng thứ hai nhé","mình chọn chủ đề số 2","Quản lý thời gian"))
            assertThat(ChatTopicClarification.confirmedChoice(choice,options)).isEqualTo("Quản lý thời gian");
        for(String other:List.of("cuốn thứ hai","hướng thứ hai dưới 100k","đổi sang trinh thám","mua cuốn này","4"))
            assertThat(ChatTopicClarification.confirmedChoice(other,options)).isEmpty();
        assertThat(ChatTopicClarification.confirmedChoice("2",List.of())).isEmpty();
    }
    @Test void currentSituationProducesOptionalChoicesWithoutBooksOrConfirmedConstraintChanges() {
        state.toolSearch.put("maxPrice",100000L);
        var result=ask("Mình sắp đi làm lần đầu, muốn mua sách mà chưa biết đọc gì");
        assertThat(result.answer()).contains("Bạn có nhắc “Mình sắp đi làm lần đầu”","cân nhắc","chủ đề khác");
        assertThat(result.chips()).containsExactly("Giao tiếp công sở","Quản lý thời gian","Tài chính cá nhân");
        assertThat(result.products()).isEmpty();assertThat(result.actions()).isEmpty();
        assertThat(result.status()).isEqualTo("NEEDS_INPUT");
        assertThat(state.toolSearch).containsOnlyKeys("maxPrice");
        assertThat(state.conversationNotes).isEmpty();
    }
    @Test void recentUserContextIsAcceptedEvenIfAssistantSaidSomethingElse() {
        prior("Mình sắp đi làm lần đầu","Bạn thử sách luật nhé");
        assertThat(ask("Mình muốn mua sách mà chưa biết chọn gì").answer()).contains("Bạn có nhắc","Giao tiếp công sở");
    }
    @Test void assistantOnlyStatementCannotBecomeCustomerContext() {
        prior("Mình muốn mua sách","Mình sắp đi làm lần đầu");
        var result=ask("Chưa biết chủ đề nào cả");
        assertThat(result.answer()).doesNotContain("Bạn có nhắc","đi làm","công sở");
        assertThat(result.chips()).startsWith("Truyện để thư giãn","Kỹ năng giao tiếp","Khám phá khoa học").hasSize(6);
    }
    @Test void fabricatedQuoteIsDiscardedRatherThanRejectingWholeTurn() {
        var result=ask("Mình muốn mua một cuốn sách");
        assertThat(result.answer()).contains("Nếu chưa có ý tưởng").doesNotContain("Bạn có nhắc");
        assertThat(result.products()).isEmpty();
    }
    @Test void realButGenericShoppingQuoteDoesNotPretendToPersonalize() {
        for(String generic:List.of("Bây giờ mình muốn tìm sách nhưng chưa có chủ đề hay mục đích gì cả.",
            "Mình chưa biết đọc gì", "mình muốn mua sách nhưng chưa có chủ đề hay mục đích cụ thể",
            "Nhu cầu mới: mình muốn mua sách, giá không giới hạn. Bạn gợi ý vài hướng để mình chọn nhé.", "Mình muốn mua sách hay", "Mình muốn mua sách làm quà", "Mình muốn mua sách dưới 100k", "Mình vẫn muốn mua quà cho em ấy nhưng chưa chọn được chủ đề khác; bạn gợi ý vài hướng dựa trên hoàn cảnh mình đã kể đi.")) {
            args.put("suggestionContext",generic);
            assertThat(ask(generic).answer()).doesNotContain("Bạn có nhắc","công sở");
        }
    }
    @Test void unspecifiedReadingNeedDoesNotAcceptAnArbitrarySingleGenreAsPersonalized() {
        for(String message:List.of("Mình muốn đọc sách nhưng chưa biết nên chọn chủ đề gì.",
                "Tôi nên đọc thể loại nào?", "Bạn tư vấn giúp mình nên đọc sách gì với.")) {
            args.put("suggestionContext",message).put("suggestedTopics","Sách thiếu nhi");
            var result=ask(message);
            assertThat(result.answer()).as(message).contains("Nếu chưa có ý tưởng").doesNotContain("Bạn có nhắc");
            assertThat(result.chips()).hasSizeGreaterThanOrEqualTo(3).doesNotContain("Sách thiếu nhi");
            var request=new ChatRequestDto();request.setMessage(message);
            assertThat(ChatTopicClarification.sources(request,new ChatDialogueMemory.State())).isEmpty();
        }
    }
    @Test void shouldInASpecificReadingNeedStillKeepsTheActualSubject() {
        String message="Mình nên đọc gì để học kỹ năng giao tiếp?";
        args.put("suggestionContext",message).put("suggestedTopics","Kỹ năng giao tiếp");
        assertThat(ask(message).chips()).contains("Kỹ năng giao tiếp");
        var request=new ChatRequestDto();request.setMessage(message);
        assertThat(ChatTopicClarification.sources(request,state)).containsExactly(Map.of("id","current","text",message));
    }
    @Test void recipientAloneDoesNotJustifyGuessingOneFavoriteGenre() {
        for(String message:List.of("t muốn tìm sách cho bạn gái tôi", "Mình muốn mua sách tặng bạn trai",
                "Tôi tìm sách cho vợ", "Tặng sách cho mẹ mình")) {
            args.put("suggestionContext",message).put("suggestedTopics","Truyện trinh thám");
            var result=ask(message);
            assertThat(result.answer()).as(message).doesNotContain("Bạn có nhắc");
            assertThat(result.chips()).hasSizeGreaterThanOrEqualTo(3);
        }
        String specific="Mình muốn tặng sách cho bạn gái đang học lập trình";
        var request=new ChatRequestDto();request.setMessage(specific);
        assertThat(ChatTopicClarification.sources(request,state)).isNotEmpty();
    }
    @Test void pureChoicesDoNotConsumeAdditionalBudgetOrBookCountClauses() {
        state.topicSuggestions=List.of("Trinh thám","Khoa học");
        assertThat(ChatTopicClarification.pureChoice("2",state)).isEqualTo("Khoa học");
        assertThat(ChatTopicClarification.pureChoice("2, chỉ dưới 50k",state)).isEmpty();
        assertThat(ChatTopicClarification.pureChoice("2, lấy 2 cuốn",state)).isEmpty();
        assertThat(ChatTopicClarification.pureChoice("cuốn thứ hai",state)).isEmpty();
        assertThat(ChatTopicClarification.pureChoice("Dịch câu chọn hướng thứ hai",state)).isEmpty();
    }
    @Test void staleContextOutsideLastFourTurnsIsNotUsed() {
        prior("Mình sắp đi làm lần đầu","Ừ");
        for(int i=0;i<4;i++)prior("Câu khác "+i,"Trả lời");
        assertThat(ask("Mình muốn mua sách").answer()).doesNotContain("Bạn có nhắc","công sở");
    }
    @Test void privateMessageIsNotQuotedEvenWhenSubstringLooksHarmless() {
        prior("Mình sắp đi làm lần đầu, mail của mình là a@example.com","Ừ");
        assertThat(ask("Mình muốn mua sách").answer()).doesNotContain("Bạn có nhắc");
    }
    @Test void malformedOrExcessiveChoicesFallBackToGenericSuggestions() {
        for(String choices:List.of("Chủ đề A|Chủ đề B|Chủ đề C|Chủ đề D|Chủ đề E|Chủ đề F|Chủ đề G", "Giao tiếp|<script>x</script>","x".repeat(65)+"|Kỹ năng sống")) {
            args.put("suggestedTopics",choices);
            assertThat(ask("Mình sắp đi làm lần đầu").answer()).doesNotContain("Bạn có nhắc","<script>");
        }
    }
    @Test void duplicateChoicesAreRenderedAsOneDirection() {
        args.put("suggestedTopics","Giao tiếp|giao tiếp");
        assertThat(ask("Mình sắp đi làm lần đầu").chips()).containsExactly("Giao tiếp");
    }
    @Test void giftWithoutSpecificContextAsksReadingDirectionAndDoesNotRequireAgeOrBudget() {
        args.remove(List.of("suggestionContext","suggestedTopics"));args.put("topic","gift");
        var result=ask("Mình muốn mua sách làm quà");
        assertThat(result.answer()).contains("Người nhận quà thích chủ đề gì").doesNotContain("tuổi","ngân sách");
        assertThat(result.products()).isEmpty();assertThat(result.chips()).hasSize(6);
    }
    @Test void recipientSituationCanSuggestTopicsWithoutAssertingTheirTastes() {
        args.put("topic","gift").put("suggestionContext","Bạn mình đang chuẩn bị đi làm");
        prior("Bạn mình đang chuẩn bị đi làm","Mình ghi nhận");
        var result=ask("Mình muốn tặng bạn ấy sách mà chưa biết chủ đề gì");
        assertThat(result.answer()).contains("Bạn mình đang chuẩn bị đi làm","cân nhắc").doesNotContain("bạn ấy thích");
        assertThat(result.products()).isEmpty();
    }

    @Test void topicMenuCommandsDoNotStealRelatedBookExpansionOrContextualSuggestionRequests() {
        for(String message:List.of("Được, thêm sách gần chủ đề cũng được.",
            "Chưa biết chọn gì, gợi ý vài hướng theo chuyện mình vừa kể đi.",
            "Đừng nhắc lại các chủ đề cũ nhé.","Nhắc lại các chủ đề, nhưng chỉ sách dưới 80k."))
            assertThat(ChatTopicClarification.menuCommand(message,state)).as(message).isEmpty();
        assertThat(ChatTopicClarification.menuCommand("Bạn nhắc lại các chủ đề lúc nãy nhé",state)).isEqualTo("recall");
        assertThat(ChatTopicClarification.menuCommand("Bạn vừa kể những hướng nào?",state)).isEqualTo("recall");
        assertThat(ChatTopicClarification.menuCommand("Cho mình thêm chủ đề khác nữa",state)).isEqualTo("more");
    }

    @Test void genericInvitationWithManyDirectionsDoesNotBecomeAQuotedPersonalCircumstance() {
        String message="Mình muốn mua sách nhưng chưa có ý tưởng, gợi ý nhiều hướng để mình chọn nhé.";
        args.put("suggestionContext",message);
        assertThat(ask(message).answer()).doesNotContain("Bạn có nhắc");
    }

    @Test void returningToFirstMenuMakesItTheLatestReferenceWithoutLosingOtherMenus() {
        state.topicMenus.add(new ArrayList<>(List.of("Trinh thám","Lịch sử")));
        state.topicMenus.add(new ArrayList<>(List.of("Khoa học","Lập trình")));
        var request=new ChatRequestDto();request.setMessage("Nhắc lại các chủ đề đầu tiên");
        assertThat(ChatTopicClarification.recall(new ChatTool.Context(request,state)).chips()).containsExactly("Trinh thám","Lịch sử");
        assertThat(ChatTopicClarification.lastMenu(state)).containsExactly("Trinh thám","Lịch sử");
        assertThat(state.topicMenus).contains(List.of("Khoa học","Lập trình"));
    }
}
