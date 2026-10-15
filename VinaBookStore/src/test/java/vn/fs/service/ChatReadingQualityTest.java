package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

class ChatReadingQualityTest {
    final ChatIntentService language=new ChatIntentService();
    final ChatReadingFit fit=new ChatReadingFit(language);
    final ChatReadingClaims claims=new ChatReadingClaims(language);
    final ObjectMapper mapper=new ObjectMapper();

    @Test void gentleAdultRequestRejectsSuspenseTragedyAndExclusiveChildTargeting() {
        Map<String,Object> f=new LinkedHashMap<>();
        fit.capture(f,"Bạn ấy 22 tuổi, thích truyện nhẹ nhàng, không thích sách dạy thành công.");
        assertThat(f).containsEntry("readerAge",22);
        assertThat(fit.conflicts(f,Map.of("summary","Những vụ án ly kỳ của thám tử."))).isTrue();
        assertThat(fit.conflicts(f,Map.of("summary","Câu chuyện trong veo mà đau đáu về cậu bé."))).isTrue();
        assertThat(fit.conflicts(f,Map.of("summary","Cổ tích dân gian cho thế hệ nhỏ."))).isTrue();
        assertThat(fit.conflicts(f,Map.of("summary","Câu chuyện tuổi thơ trong trẻo, dành cho cả trẻ em và người lớn."))).isFalse();
    }
    @Test void shortStoriesAndPictureBooksDoNotProveReadabilityOrEducationalEffects() {
        assertThat(fit.confirmed("accessible",Map.of("summary","Truyện ngắn về tình bạn."))).isFalse();
        assertThat(claims.unsupported("Câu chuyện dễ dàng theo dõi, không bị choáng ngợp.",Map.of("summary","Câu chuyện người nông dân."))).isTrue();
        assertThat(claims.unsupported("Giúp trẻ dễ dàng làm quen và phát triển ngôn ngữ.",Map.of("summary","Bộ truyện tranh tuổi thơ."))).isTrue();
    }
    @Test void ApprovedLevelAndStyleCanSupportClaimsEvenWhenSummaryDoesNotRepeatThem() {
        var facts=Map.of("summary","Câu chuyện về tình bạn.","readingLevel","Ngôn ngữ đơn giản, dễ đọc.","readingStyle","Nhẹ nhàng, gần gũi.");
        assertThat(claims.unsupported("Câu chuyện dễ đọc với cách kể nhẹ nhàng.",facts)).isFalse();
        assertThat(fit.missing(Map.of("readingNeeds",List.of("accessible","gentle")),facts)).isEmpty();
        assertThat(claims.unsupported("Phần mô tả chưa xác nhận độ dễ đọc hoặc phong cách.",Map.of("summary","Câu chuyện về tình bạn."))).isFalse();
    }
    @Test void ChildAudienceDoesNotConfirmSpecificAgeAndCharacterAgeIsNotEvidence() {
        Map<String,Object> f=Map.of("readerAge",8);
        assertThat(fit.ageConfirmed(f,Map.of("summary","Cậu bé tám tuổi phiêu lưu.","audience","Thiếu nhi"))).isFalse();
        assertThat(fit.ageConfirmed(f,Map.of("audience","Trẻ từ 6 đến 9 tuổi"))).isTrue();
        assertThat(fit.ageConfirmed(Map.of("readerAge",5),Map.of("audience","Trẻ từ 6 đến 9 tuổi"))).isFalse();
    }
    @Test void PublisherAgeRangesAcceptTypographyAndRejectOutOfRangeOrPartialDigits() {
        for(String dash:List.of("-","–","—"," đến ")) {
            var audience=Map.of("audience","Nhi đồng 6"+dash+"11 tuổi theo phân loại của NXB.");
            assertThat(fit.ageConfirmed(Map.of("readerAge",8),audience)).isTrue();
            assertThat(fit.ageConfirmed(Map.of("readerAge",5),audience)).isFalse();
            assertThat(fit.ageConfirmed(Map.of("readerAge",12),audience)).isFalse();
        }
        assertThat(fit.ageConfirmed(Map.of("readerAge",8),Map.of("audience","Độc giả từ 18 tuổi trở lên."))).isFalse();
        assertThat(fit.ageConfirmed(Map.of("readerAge",8),Map.of("audience","Thiếu niên 11–15 tuổi."))).isFalse();
        assertThat(fit.ageConfirmed(Map.of("readerAge",8),Map.of("audience","Trẻ từ 6 tuổi trở lên."))).isTrue();
    }
    @Test void KnownPublisherAgeMismatchIsExcludedButUnknownAgeRemainsQualified() {
        var request=Map.<String,Object>of("readerAge",8);
        assertThat(fit.conflicts(request,Map.of("audience","Thiếu niên 11–15 tuổi theo NXB."))).isTrue();
        assertThat(fit.conflicts(request,Map.of("audience","Nhi đồng 6–11 tuổi theo NXB."))).isFalse();
        assertThat(fit.conflicts(request,Map.of("audience","Độc giả trẻ và người lớn."))).isFalse();
    }
    @Test void ShortChaptersDoNotProveBeginnerFitAndStorytellingIsNotEmotionManagement() {
        assertThat(claims.unsupported("Với cấu trúc nhiều chương ngắn, đây là gợi ý phù hợp để bạn làm quen dần với thói quen đọc sách.",
            Map.of("summary","Câu chuyện tuổi thơ.","readingStyle","Nhiều chương ngắn."))).isTrue();
        assertThat(claims.unsupported("Sách tập trung vào quản lý cảm xúc cá nhân.",
            Map.of("summary","Nguyên tắc giao tiếp và ứng xử.","readingStyle","Lối viết trò chuyện và kể chuyện."))).isTrue();
    }
    @Test void AVerifiedSinglePreferenceBecomesTheFocusForTheNextSingularQuestion() throws Exception {
        Product first=book(1,"Truyện Xưa","Câu chuyện hồn nhiên về tuổi thơ.","Sách Văn học nghệ thuật");
        Product chosen=book(2,"Bạn Bè","Câu chuyện hồn nhiên về tình bạn.","Sách Văn học nghệ thuật");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(first,chosen));
        AiGateway ai=mock(AiGateway.class);
        var narrator=new ChatBookNarrator(ai,mapper,repo,language);
        var tools=new ChatBuiltinTools(repo,new ChatRetrievalService(language),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        var state=new ChatDialogueMemory.State();state.shortlist=new ArrayList<>(List.of(1L,2L));state.displayed=List.of(1L,2L);
        ChatRequestDto req=new ChatRequestDto();req.setMessage("Trong các cuốn đó, cuốn nào nên chọn?");
        var base=tools.getProducts().execute(mapper.readTree("{\"reference\":\"selection\",\"field\":\"explanation\"}"),new ChatTool.Context(req,state));
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(mapper.writeValueAsString(Map.of("preferredProductId",2,"reasons",List.of(
            Map.of("productId",2,"evidenceField","summary","evidence",chosen.getDescription(),"reason","Nội dung về tình bạn là điểm để bạn cân nhắc theo hướng đọc ban đầu.")))));
        assertThat(narrator.explain(base,req.getMessage(),state).answer()).contains("mình nghiêng về");
        assertThat(state.focus).isEqualTo(2L);
        req.setMessage("Giải thích kỹ hơn vì sao chọn cuốn ấy nhé.");
        var next=tools.getProducts().execute(mapper.readTree("{\"reference\":\"selection\",\"field\":\"explanation\"}"),new ChatTool.Context(req,state));
        assertThat(next.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(next.answer()).doesNotContain("Bạn đang nói đến cuốn sách nào");
    }
    @Test void SoftNeedsSurvivePricePatchAndExplicitRemovalWorks() {
        Map<String,Object> f=new LinkedHashMap<>();
        fit.capture(f,"Tìm truyện dễ đọc, nhẹ nhàng.");
        fit.capture(f,"Dưới 100 nghìn nhé.");
        assertThat(fit.needs(f)).containsExactly("gentle","accessible");
        fit.capture(f,"Không cần nhẹ nhàng nữa.");
        assertThat(fit.needs(f)).containsExactly("accessible");
    }
    @Test void ExplicitGentleDetectiveRequestKeepsGenreCandidatesButDoesNotConfirmTheirMood() {
        var f=Map.<String,Object>of("query","trinh thám","readingNeeds",List.of("gentle"));
        var facts=Map.of("summary","Những vụ án ly kỳ của một thám tử.");
        assertThat(fit.conflicts(f,facts)).isFalse();
        assertThat(fit.missing(f,facts)).containsExactly("mức độ nhẹ nhàng");
        assertThat(fit.conflicts(f,Map.of("summary","Câu chuyện mafia và thế giới ngầm."))).isTrue();
    }
    @Test void DiscoveryUsesPurposeAcrossShelvesAndDoesNotFillDefaultSlotsWithPoorMoodMatches() throws Exception {
        Product gentle=book(3,"Vé Tuổi Thơ","Thế giới hồn nhiên của tuổi thơ, dành cho cả trẻ em và người lớn.","Sách Văn học nghệ thuật");
        Product suspense=book(1,"Thám tử","Những vụ án ly kỳ.","Sách Truyện, tiểu thuyết");
        Product unknown=book(2,"Truyện Ngắn Xưa","Tập truyện ngắn về một thời đã xa.","Sách Văn học nghệ thuật");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(suspense,unknown,gentle));
        ChatRetrievalService data=new ChatRetrievalService(language);
        ChatBuiltinTools tools=new ChatBuiltinTools(repo,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        ChatRequestDto req=new ChatRequestDto();req.setMessage("Tìm truyện nhẹ nhàng cho bạn 22 tuổi");
        var state=new ChatDialogueMemory.State();
        var result=tools.recommendBooks().execute(mapper.readTree("{\"query\":\"truyện\",\"category\":\"Sách Truyện, tiểu thuyết\",\"preferredQuery\":\"truyện nhẹ nhàng\"}"),new ChatTool.Context(req,state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(3L);
        assertThat(state.toolSearch).doesNotContainKey("category");
        assertThat(result.answer()).doesNotContain("chưa được xác nhận đầy đủ");
        assertThat(new ChatReadingRequest(language).preferred(state.toolSearch,gentle,data)).isTrue();
    }
    @Test void UnconfirmedBeginnerBooksAreQualifiedInsteadOfLabeledFullyMatching() throws Exception {
        Product p=book(1,"Truyện Ngắn","Truyện ngắn về tình bạn.","Sách Văn học nghệ thuật");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(p));
        ChatBuiltinTools tools=new ChatBuiltinTools(repo,new ChatRetrievalService(language),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        ChatRequestDto req=new ChatRequestDto();req.setMessage("Gợi ý văn học cho người ít đọc");
        var result=tools.recommendBooks().execute(mapper.readTree("{\"query\":\"văn học\"}"),new ChatTool.Context(req,new ChatDialogueMemory.State()));
        assertThat(result.answer()).contains("chưa đủ thông tin để xác nhận độ dễ đọc","Sách theo nội dung bạn tìm").doesNotContain("Sách đúng yêu cầu");
    }
    @Test void NarratorRejectsAgeFitIntroAndInventedEaseButKeepsVerifiedLimitations() throws Exception {
        Product p=book(1,"Truyện Bé","Truyện thiếu nhi về tình bạn.","Sách thiếu nhi");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(p));
        AiGateway ai=mock(AiGateway.class);ChatBookNarrator narrator=new ChatBookNarrator(ai,mapper,repo,language);
        ChatBuiltinTools tools=new ChatBuiltinTools(repo,new ChatRetrievalService(language),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        ChatRequestDto req=new ChatRequestDto();req.setMessage("Tìm truyện cho bé 8 tuổi, dễ đọc");
        var state=new ChatDialogueMemory.State();
        var base=tools.recommendBooks().execute(mapper.readTree("{\"query\":\"truyện\"}"),new ChatTool.Context(req,state));
        String intro="Mình chọn những cuốn phù hợp với độ tuổi của bé.";
        String reason="Truyện giúp trẻ dễ dàng làm quen và phát triển ngôn ngữ.";
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(mapper.writeValueAsString(Map.of("intro",intro,"reasons",List.of(Map.of("productId",1,"evidenceField","summary","evidence",p.getDescription(),"reason",reason)))));
        assertThat(narrator.present(base,req.getMessage(),state).answer()).contains("chưa đủ thông tin để xác nhận độ dễ đọc, độ tuổi đọc").doesNotContain(intro,reason);
    }
    @Test void EmptyResultDoesNotInviteAnAlreadyExhaustedNearbySearch() {
        AiGateway ai=mock(AiGateway.class);
        var narrator=new ChatBookNarrator(ai,mapper,mock(ProductRepository.class),language);
        var base=new ChatTool.Result("Không còn cuốn khác đúng hoặc gần chủ đề.",ChatIntent.DISCOVER_BOOKS,List.of(),List.of(),"COMPLETED");
        assertThat(narrator.present(base,"Có cuốn nào gần chủ đề không?",new ChatDialogueMemory.State())).isSameAs(base);
        verifyNoInteractions(ai);
    }
    @Test void SemanticNeedCanUseAParaphraseButMustQuoteTheCustomersCurrentMessage() throws Exception {
        ChatReadingRequest reading=new ChatReadingRequest(language);
        Map<String,Object> f=new LinkedHashMap<>();
        reading.apply(mapper.readTree("{\"readingNeed\":\"gentle\",\"readingNeedEvidence\":\"đọc xong thấy nhẹ đầu\"}"),f,"Muốn đọc xong thấy nhẹ đầu, không phải nghĩ căng quá.",false);
        assertThat(fit.needs(f)).containsExactly("gentle");
        Map<String,Object> invented=new LinkedHashMap<>();
        reading.apply(mapper.readTree("{\"readingNeed\":\"gentle\",\"readingNeedEvidence\":\"Tôi muốn thư giãn\"}"),invented,"Tìm sách toán lớp 7.",false);
        assertThat(fit.needs(invented)).isEmpty();
    }
    @Test void PracticalMarketingDoesNotConfirmConcreteExercisesAndEaseParaphrasesAreChecked() {
        var facts=Map.of("summary","Những phương pháp thiết thực để vượt qua lo âu.");
        assertThat(fit.confirmed("practical",facts)).isFalse();
        assertThat(claims.unsupported("Nội dung có công cụ cụ thể để áp dụng.",facts)).isTrue();
        assertThat(claims.unsupported("Cuốn truyện giúp người ít đọc dễ hình dung và tiếp nhận bài học.",Map.of("summary","Cuộc phiêu lưu của một chú dế."))).isTrue();
        assertThat(claims.unsupported("Có thể kích thích tư duy cho trẻ qua các vụ án.",Map.of("summary","Bộ truyện tranh trinh thám."))).isTrue();
    }
    @Test void AShortDescriptionCannotProveTheAbsenceOfLessonsOrPureEntertainment() {
        var facts=Map.of("summary","Câu chuyện hồn nhiên về tuổi thơ.");
        assertThat(claims.unsupported("Tác phẩm không đề cập tới bài học thành công.",facts)).isTrue();
        assertThat(claims.unsupported("Mang đến trải nghiệm giải trí thuần túy.",facts)).isTrue();
        assertThat(claims.unsupported("Mô tả chưa nêu các bài tập trong sách.",facts)).isFalse();
    }
    @Test void LocalFallbackUnderstandsAChildSearchAndPreservesItAcrossShippingAndWhy() {
        Product child=book(1,"Truyện Bé","Truyện tranh thiếu nhi về tình bạn.","Sách thiếu nhi");
        Product adult=book(2,"Thi Ca Xưa","Tác phẩm thi ca cổ điển.","Sách Văn học nghệ thuật");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(child,adult));
        AiGateway ai=mock(AiGateway.class);when(ai.isConfigured()).thenReturn(false);
        ChatRetrievalService data=new ChatRetrievalService(language);
        ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
        ChatBuiltinTools builtins=new ChatBuiltinTools(repo,data,language,orders,new ChatPolicyService(language));
        ChatToolRegistry registry=new ChatToolRegistry(List.of(builtins.recommendBooks(),builtins.getProducts()));
        ChatService service=new ChatService(repo,ai,language,data,new ChatPolicyService(language),orders,mock(ChatAnalyticsService.class),mock(vn.fs.config.CurrentUserAccess.class));service.setTools(registry);
        var first=localTurn(service,"Tôi muốn tìm truyện cho bé 8 tuổi, dễ đọc, dưới 100 nghìn.");
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        localTurn(service,"Cửa hàng tính phí giao hàng thế nào?");
        var why=localTurn(service,"Quay lại những cuốn vừa rồi, cuốn nào hợp với bé nhất?");
        assertThat(why.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(why.getAnswer()).contains("chưa xác nhận độ tuổi đọc","Bé thường tự đọc");
    }
    @Test void LocalFallbackCanSwitchToRelaxingFictionWithBudgetAndExclusionsIntact() {
        Product gentle=book(1,"Tuổi Thơ","Câu chuyện hồn nhiên về tuổi thơ.","Sách Văn học nghệ thuật");
        Product advice=book(2,"Đắc Nhân Tâm","Nghệ thuật giao tiếp.","Sách Tâm lý");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(gentle,advice));
        AiGateway ai=mock(AiGateway.class);when(ai.isConfigured()).thenReturn(false);
        ChatRetrievalService data=new ChatRetrievalService(language);
        ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
        ChatBuiltinTools builtins=new ChatBuiltinTools(repo,data,language,orders,new ChatPolicyService(language));
        ChatService service=new ChatService(repo,ai,language,data,new ChatPolicyService(language),orders,mock(ChatAnalyticsService.class),mock(vn.fs.config.CurrentUserAccess.class));
        service.setTools(new ChatToolRegistry(List.of(builtins.recommendBooks())));
        localTurn(service,"Tìm sách phát triển bản thân cho mình, đừng giới thiệu Đắc Nhân Tâm.");
        var switched=localTurn(service,"Thôi đổi sang truyện để thư giãn nhé, vẫn dưới 100 nghìn.");
        assertThat(switched.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(switched.getAnswer()).doesNotContain("Bạn muốn mình giúp tìm sách");
    }
    @Test void GiftDiscoveryKeepsTasteAndNeverTurnsAnUnselectedGiftIntoCheckout() {
        Product gentle=book(1,"Tuổi Thơ","Câu chuyện hồn nhiên về tuổi thơ dành cho người lớn.","Sách Văn học nghệ thuật");
        Product sad=book(2,"Mắt Biếc","Câu chuyện có kết thúc rất buồn.","Sách Văn học nghệ thuật");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(gentle,sad));
        AiGateway ai=mock(AiGateway.class);when(ai.isConfigured()).thenReturn(false);
        ChatRetrievalService data=new ChatRetrievalService(language);
        ChatOrderSupportService orders=mock(ChatOrderSupportService.class);
        ChatBuiltinTools builtins=new ChatBuiltinTools(repo,data,language,orders,new ChatPolicyService(language));
        ChatService service=new ChatService(repo,ai,language,data,new ChatPolicyService(language),orders,mock(ChatAnalyticsService.class),mock(vn.fs.config.CurrentUserAccess.class));
        service.setTools(new ChatToolRegistry(List.of(builtins.recommendBooks(),builtins.getProducts())));
        var first=localTurn(service,"Mua sách tặng bạn nữ 22 tuổi, bạn ấy thích truyện nhẹ nhàng, không thích sách dạy thành công.");
        assertThat(first.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(first.getActions()).noneMatch(a->"checkout".equals(a.getType()));
        var why=localTurn(service,"Tại sao lại là những cuốn đó?");
        assertThat(why.getProducts()).extracting(ChatProductDto::getId).containsExactly(1L);
        assertThat(ChatConversationSupport.giftDiscovery("Mua cuốn này để tặng bạn nhé",language)).isFalse();
    }
    @Test void NamedDifferentBookComparisonUsesPreviousFocusAndSkipsInactiveAnchor() throws Exception {
        Product first=book(1,"Clean Code","Các nguyên tắc đặt tên, viết hàm và kiểm thử mã nguồn.","Sách lập trình");
        Product second=book(2,"The Pragmatic Programmer","Thực hành và tư duy làm việc của lập trình viên.","Sách lập trình");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(first,second));
        var tools=new ChatBuiltinTools(repo,new ChatRetrievalService(language),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        var state=new ChatDialogueMemory.State();state.focus=1L;
        ChatRequestDto req=new ChatRequestDto();req.setMessage("Còn The Pragmatic Programmer có khác gì?");
        var result=tools.getProducts().execute(mapper.readTree("{\"field\":\"table\",\"productId\":2}"),new ChatTool.Context(req,state));
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L,2L);
        assertThat(result.answer()).doesNotContain("với cuốn nào nữa");
        state.focus=999L;
        var missing=tools.getProducts().execute(mapper.readTree("{\"field\":\"table\",\"productId\":2}"),new ChatTool.Context(req,state));
        assertThat(missing.products()).isEmpty();
        assertThat(missing.answer()).contains("với cuốn nào nữa");
    }
    @Test void ExplicitPublisherChildRangeDoesNotBecomeAdultGiftAndSadEndingIsNotGentle() {
        assertThat(fit.conflicts(Map.of("readerAge",22),Map.of("audience","Nhi đồng 6–11 tuổi theo NXB."))).isTrue();
        assertThat(fit.conflicts(Map.of("readerAge",22),Map.of("audience","Thiếu nhi và người lớn."))).isFalse();
        assertThat(fit.conflicts(Map.of("readingNeeds",List.of("gentle")),Map.of("summary","Truyện tình có kết thúc rất buồn."))).isTrue();
    }
    @Test void UnmentionedTopicsDoNotProveAbsenceAndWorryDoesNotBecomeProgrammingAdvice() {
        var facts=Map.of("summary","Truyện tranh về tình bạn và các bảo bối.");
        assertThat(claims.unsupported("Đây là lựa chọn thuần túy giải trí và hoàn toàn không liên quan đến hướng dẫn thành công.",facts)).isTrue();
        assertThat(claims.unsupported("Giọng kể này tránh xa các chủ đề về thành công hay kỹ năng.",facts)).isTrue();
        var programming=book(9,"Lập Trình Viên","Bàn về trách nhiệm nghề nghiệp của lập trình viên.","Lập trình");
        assertThat(new ChatExplicitFilters(language,new ChatRetrievalService(language)).matches(
            "Mình hay lo về công việc và tiền bạc, muốn cách áp dụng cụ thể.",programming)).isFalse();
    }
    @Test void UniqueConfirmedReadabilitySelectsFocusWithoutDependingOnAiChoice() throws Exception {
        Product unknown=book(1,"Truyện Xưa","Câu chuyện tuổi thơ.","Văn học");
        Product easy=book(2,"Vé Tuổi Thơ","Những trò chơi tuổi thơ qua góc nhìn người lớn.","Văn học");
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(unknown,easy));
        var tools=new ChatBuiltinTools(repo,new ChatRetrievalService(language),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        BookKnowledgeService knowledge=mock(BookKnowledgeService.class);tools.setKnowledge(knowledge);
        when(knowledge.approvedReadingFacts(unknown)).thenReturn(Map.of("summary",unknown.getDescription()));
        when(knowledge.approvedReadingFacts(easy)).thenReturn(Map.of("summary",easy.getDescription(),"readingLevel","NXB nhận xét dễ đọc."));
        var state=new ChatDialogueMemory.State();state.shortlist=new ArrayList<>(List.of(1L,2L));state.toolSearch.put("readingNeeds",List.of("accessible"));
        ChatRequestDto req=new ChatRequestDto();req.setMessage("Trong các cuốn đó, cuốn nào có căn cứ là dễ đọc nhất?");
        var result=tools.getProducts().execute(mapper.readTree("{\"reference\":\"selection\",\"field\":\"explanation\"}"),new ChatTool.Context(req,state));
        assertThat(result.answer()).contains("duy nhất có thông tin đã duyệt xác nhận độ dễ đọc");
        assertThat(state.focus).isEqualTo(2L);
        AiGateway ai=mock(AiGateway.class);
        var narrator=new ChatBookNarrator(ai,mapper,repo,language);narrator.setKnowledge(knowledge);
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(mapper.writeValueAsString(Map.of(
            "preferredProductId",2,"reasons",List.of(Map.of("productId",2,"evidenceField","readingLevel",
                "evidence","NXB nhận xét dễ đọc.","reason","Thông tin mức đọc đã duyệt là căn cứ để cân nhắc hướng đọc ban đầu.")))));
        var narrated=narrator.explain(result,req.getMessage(),state);
        assertThat(narrated.answer()).contains("duy nhất có thông tin đã duyệt xác nhận độ dễ đọc").doesNotContain("mình nghiêng về");
        req.setMessage("Giải thích vì sao bạn chọn cuốn ấy");
        var next=tools.getProducts().execute(mapper.readTree("{\"field\":\"explanation\"}"),new ChatTool.Context(req,state));
        assertThat(next.products()).extracting(ChatProductDto::getId).containsExactly(2L);
        assertThat(claims.unsupported("Các chương ngắn giúp bạn không cảm thấy bị áp lực khi đọc.",Map.of("readingStyle","Nhiều chương ngắn."))).isTrue();
    }
    @Test void WorryPurposeRequiresContentEvidenceUnlessCustomerExplicitlyChoosesFiction() {
        Product story=book(1,"Sherlock Holmes","Thám tử điều tra các vụ án bằng quan sát và suy luận.","Trinh thám");
        Product worry=book(2,"Quẳng Gánh Lo","Nguyên tắc xử lý lo âu và giảm bận tâm công việc, tiền bạc.","Tâm lý");
        var eligibility=new ChatExplicitFilters(language,new ChatRetrievalService(language));
        String request="Mình hay lo về công việc và tiền bạc, muốn cách áp dụng cụ thể.";
        assertThat(new ChatReadingRequest(language).goalRequest(request)).isTrue();
        assertThat(eligibility.matchesReadingPurpose(request,story)).isFalse();
        assertThat(eligibility.matchesReadingPurpose(request,worry)).isTrue();
        assertThat(eligibility.matchesReadingPurpose("Tìm truyện trinh thám để thư giãn khi lo lắng",story)).isTrue();
        assertThat(claims.unsupported("Sách tránh hoàn toàn các nội dung dạy kỹ năng hay thành công.",Map.of("summary","Câu chuyện về tuổi thơ."))).isTrue();
        assertThat(claims.unsupported("Sách bàn về đầu tư thay vì lập ngân sách chi tiêu.",Map.of("summary","Triết lý đầu tư giá trị và chiến lược dài hạn."))).isTrue();
    }
    ChatResponseDto localTurn(ChatService service,String message) {
        ChatRequestDto req=new ChatRequestDto();req.setMessage(message);req.setSessionId("reading-local-test");return service.reply(req);
    }
    Product book(long id,String name,String description,String category) {
        return new Product(id,name,"Tác giả",10,90000d,0,"",description,LocalDate.now(),true,10,new Category(1L,category));
    }
}
