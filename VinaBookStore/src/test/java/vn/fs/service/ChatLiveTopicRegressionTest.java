package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.ProductRepository;

/** Reproduce live subject failures with a broad planner query and catalog distractors. */
class ChatLiveTopicRegressionTest {
    final ChatIntentService language=new ChatIntentService();
    final ChatRetrievalService data=new ChatRetrievalService(language);
    final ObjectMapper mapper=new ObjectMapper();

    Product book(long id,String title,String description,String category) {
        return new Product(id,title,"Tác giả",20,80000D,0,"cover.jpg",description,
            LocalDate.now(),true,10,new Category(1L,category));
    }
    ChatTool.Result discover(String message,String broadQuery,List<Product> catalog,ChatDialogueMemory.State state) {
        ProductRepository products=mock(ProductRepository.class);when(products.findByStatusTrue()).thenReturn(catalog);
        ChatBuiltinTools tools=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        state.structuredTurnActive=true;state.contextMode="new";state.toolSearch.put("query",broadQuery);
        ChatRequestDto request=new ChatRequestDto();request.setMessage(message);request.setRequestId("topic-regression");
        return tools.searchProducts().execute(mapper.createObjectNode(),new ChatTool.Context(request,state));
    }
    @Test void interpretedOrKeepsAKnownGenreWhenTheOtherAlternativeIsMisspelled() {
        Product romance=book(1,"Chuyện tình","Truyện tình cảm kể về tình yêu của hai người trẻ.","Văn học");
        Product adventure=book(2,"Hành trình","Truyện phiêu lưu khám phá một vùng đất mới.","Truyện");
        Product law=book(3,"Luật","Các văn bản pháp luật.","Kiến thức");
        var result=discover("bạn gái tôi thích truyện tình cảm hay ohieeu lưu gì đó",
            "Truyện tình cảm hoặc Truyện phiêu lưu",List.of(romance,adventure,law),new ChatDialogueMemory.State());
        assertThat(result.products()).extracting(ChatProductDto::getId).contains(1L).doesNotContain(3L);
    }
    @Test void vagueQualifierDoesNotBecomeAnExtraRequiredSubject() {
        Product romance=book(1,"Chuyện tình","Truyện tình cảm về tình yêu.","Văn học");
        Product adventure=book(2,"Hành trình","Truyện phiêu lưu khám phá một vùng đất mới.","Truyện");
        var filters=new ChatExplicitFilters(language,data);
        for(Product book:List.of(romance,adventure))
            assertThat(filters.matchesStorySubjects("Tôi thích truyện tình cảm hay phiêu lưu gì đó",book)).isTrue();
        assertThat(filters.matchesStorySubjects("Tôi thích truyện tình cảm và phiêu lưu",romance)).isFalse();
        assertThat(language.discoveryQuery("Gợi ý sách hay")).isEqualTo("Gợi ý sách hay");
    }
    @Test void specialtyCannotBeReplacedWithFairytalesByThePlanner() {
        Product fairytale=book(1,"Kho Tàng Truyện Cổ Tích","Truyện cổ tích dân gian, kể chuyện thế giới thần tiên.","Văn học");
        var state=new ChatDialogueMemory.State();
        var result=discover("Tìm cho mình sách chuyên về thiết kế động cơ tên lửa.","cổ tích",List.of(fairytale),state);
        assertThat(result.products()).isEmpty();
        assertThat(state.discoveryExact).isEmpty();assertThat(state.searchOutcome).isEqualTo("no_match");
    }
    @Test void recipientInterestCannotBeReplacedWithGeneralScienceOrHistory() {
        Product science=book(1,"Lịch sử loài người","Khoa học và lịch sử phát triển của nhân loại.","Khoa học");
        var state=new ChatDialogueMemory.State();state.toolSearch.put("maxPrice",199999L);
        var result=discover("Mẹ thích làm vườn, khoảng dưới 200k.","khoa học",List.of(science),state);
        assertThat(result.products()).isEmpty();assertThat(state.searchOutcome).isEqualTo("no_match");
    }
    @Test void unknownSpecialtiesWorkWithoutAHandwrittenTopicDictionary() {
        Product apiary=book(1,"Kỹ thuật nuôi ong","Hướng dẫn nuôi ong tại đô thị.","Kỹ thuật");
        Product distractor=book(2,"Chuyện phố","Tiểu thuyết về cuộc sống đô thị.","Kỹ thuật");
        var result=discover("Tìm sách chuyên về nuôi ong đô thị.","nuôi ong",List.of(apiary,distractor),new ChatDialogueMemory.State());
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L);
    }
    @Test void realSpecialtyEvidenceCanUseDifferentWordOrder() {
        Product technical=book(1,"Kỹ thuật vũ trụ","Động cơ tên lửa: các nguyên tắc và hướng dẫn thiết kế.","Kỹ thuật");
        Product partial=book(2,"Thiết kế sáng tạo","Các mẫu thiết kế đồ vật trong đời sống.","Kỹ thuật");
        var result=discover("Tìm cho mình sách chuyên về thiết kế động cơ tên lửa.","thiết kế",List.of(technical,partial),new ChatDialogueMemory.State());
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L);
    }
    @Test void subjectEvidenceMustComeFromContentAndBeAffirmed() {
        ChatExplicitFilters filters=new ChatExplicitFilters(language,data);
        Product categoryOnly=book(1,"Cuốn sách A","Các câu chuyện trong đời sống.","Làm vườn");
        categoryOnly.setAuthor("Làm Vườn");
        Product negated=book(2,"Cuốn sách B","Hồi ký. Không bao gồm nội dung về làm vườn.","Văn học");
        Product correct=book(3,"Trồng cây","Hướng dẫn làm vườn cho người mới.","Nông nghiệp");
        assertThat(filters.matchesStorySubjects("Mẹ thích làm vườn, dưới 200k.",categoryOnly)).isFalse();
        assertThat(filters.matchesStorySubjects("Mẹ thích làm vườn, dưới 200k.",negated)).isFalse();
        assertThat(filters.matchesStorySubjects("Mẹ thích làm vườn, dưới 200k.",correct)).isTrue();
        assertThat(filters.matchesStorySubjects("Không thích làm vườn.",categoryOnly)).isTrue();
    }
    @Test void knownSynonymsAndReadingPreferencesKeepWorking() {
        ChatExplicitFilters filters=new ChatExplicitFilters(language,data);
        Product detective=book(1,"Vụ án","Truyện về một thám tử phá án.","Truyện");
        Product gentle=book(2,"Tuổi thơ","Câu chuyện hồn nhiên, trong trẻo.","Văn học");
        assertThat(filters.matchesStorySubjects("Tôi thích trinh thám.",detective)).isTrue();
        assertThat(filters.matchesStorySubjects("Tôi thích truyện nhẹ nhàng.",gentle)).isTrue();
    }
    @Test void sourceConstraintsAlsoPreserveKnownSynonymsWhenThePlannerBroadensItsQuery() {
        Product detective=book(1,"Vụ án","Truyện về một thám tử phá án.","Văn học");
        Product historical=book(2,"Truyện Xưa","Truyện về lịch sử chiến tranh.","Văn học");
        assertThat(new ChatExplicitFilters(language,data).matchesStorySubjects("Tôi thích trinh thám.",detective)).isTrue();
        assertThat(new ChatCatalogQuery(language,data).search("truyện",ChatSearchCriteriaDto.empty(),List.of(detective,historical)))
            .extracting(Product::getProductId).contains(1L);
        var result=discover("Tôi thích trinh thám.","văn học",List.of(detective,historical),new ChatDialogueMemory.State());
        assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(1L);
    }
    @Test void fullFrozenCatalogCannotSupplyUnsupportedSpecialties() {
        var fixture=new BookLabelPilotEvaluationTest();
        try(var catalog=fixture.variant(true,true)) {
            ProductRepository products=mock(ProductRepository.class);when(products.findByStatusTrue()).thenReturn(catalog.all());
            var tools=new ChatBuiltinTools(products,catalog.data(),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
            for(var test:List.of(
                Map.of("message","Tìm cho mình sách chuyên về thiết kế động cơ tên lửa.","query","cổ tích"),
                Map.of("message","Mẹ thích làm vườn, khoảng dưới 200k.","query","lịch sử"),
                Map.of("message","Tìm sách về động cơ tên lửa cho trẻ em.","query","cổ tích"),
                Map.of("message","Tìm sách về làm vườn và lịch sử.","query","lịch sử"),
                Map.of("message","Mẹ thích sách về làm vườn dễ đọc.","query","lịch sử"))) {
                var state=new ChatDialogueMemory.State();state.structuredTurnActive=true;state.contextMode="new";
                state.toolSearch.put("query",test.get("query"));state.toolSearch.put("maxPrice",199999L);
                ChatRequestDto request=new ChatRequestDto();request.setMessage(test.get("message"));
                assertThat(tools.searchProducts().execute(mapper.createObjectNode(),new ChatTool.Context(request,state)).products()).isEmpty();
            }
        }
    }
    @Test void unmappedAlternativeSubjectsStillNeedEvidenceInEitherBranch() {
        var filters=new ChatExplicitFilters(language,data);
        String question="Tìm sách chuyên về thiết kế động cơ tên lửa hoặc làm vườn.";
        assertThat(filters.matchesStorySubjects(question,book(1,"Vườn","Hướng dẫn làm vườn.","Kiến thức"))).isTrue();
        assertThat(filters.matchesStorySubjects(question,book(2,"Động cơ","Thiết kế động cơ tên lửa.","Kỹ thuật"))).isTrue();
        assertThat(filters.matchesStorySubjects(question,book(3,"Truyện","Câu chuyện cổ tích.","Văn học"))).isFalse();
    }
    @Test void audienceAndReadingQualifiersCannotBypassAnotherSpecialty() {
        var filters=new ChatExplicitFilters(language,data);
        Product stories=book(1,"Câu chuyện nhỏ","Truyện thiếu nhi nhẹ nhàng, dễ đọc.","Thiếu nhi");
        Product technical=book(2,"Kỹ thuật vũ trụ","Thiết kế động cơ tên lửa.","Kỹ thuật");
        Product garden=book(3,"Trồng cây","Hướng dẫn làm vườn.","Nông nghiệp");
        assertThat(filters.matchesStorySubjects("Tìm sách về động cơ tên lửa cho trẻ em.",stories)).isFalse();
        assertThat(filters.matchesStorySubjects("Tìm sách về động cơ tên lửa cho trẻ em.",technical)).isTrue();
        assertThat(filters.matchesStorySubjects("Mẹ thích sách về làm vườn dễ đọc.",stories)).isFalse();
        assertThat(filters.matchesStorySubjects("Mẹ thích sách về làm vườn dễ đọc.",garden)).isTrue();
        assertThat(filters.matchesStorySubjects("Tìm sách về làm vườn đơn giản cho người mới bắt đầu nhé ạ.",garden)).isTrue();
    }
    @Test void knownAndUnknownSubjectsBothRemainRequiredInAConjunction() {
        var filters=new ChatExplicitFilters(language,data);
        Product history=book(1,"Ngày xưa","Lịch sử của một quốc gia.","Lịch sử");
        Product garden=book(2,"Trồng cây","Hướng dẫn làm vườn.","Nông nghiệp");
        Product both=book(3,"Khu vườn xưa","Lịch sử làm vườn qua các thời kỳ.","Lịch sử");
        String question="Tìm sách về làm vườn và lịch sử.";
        assertThat(filters.matchesStorySubjects(question,history)).isFalse();
        assertThat(filters.matchesStorySubjects(question,garden)).isFalse();
        assertThat(filters.matchesStorySubjects(question,both)).isTrue();
        assertThat(filters.matchesStorySubjects("Tìm sách về lịch sử hàng không.",history)).isFalse();
    }
    @Test void oneKnownConceptUsesTheUnionOfItsOverlappingSynonymFamilies() {
        var filters=new ChatExplicitFilters(language,data);
        Product psychology=book(1,"Con người","Cảm xúc và hành vi của con người.","Kiến thức");
        assertThat(filters.matchesStorySubjects("Tìm sách về tâm lý.",psychology)).isTrue();
        assertThat(filters.matchesStorySubjects("Tìm sách về tâm lý và làm vườn.",psychology)).isFalse();
    }
    @Test void catalogSearchDoesNotUseSubstringFragmentsAsSubjectEvidence() {
        Product wrong=book(1,"Khởi đầu","Câu chuyện của Khôi và tên Minh.","Văn học");
        var criteria=ChatSearchCriteriaDto.empty();
        assertThat(new ChatCatalogQuery(language,data).search("Tìm sách chuyên về thiết kế động cơ tên lửa.",criteria,List.of(wrong))).isEmpty();
    }
    @Test void genericCommunicationNeedsInstructionInTheRealCatalog() {
        var fixture=new BookLabelPilotEvaluationTest();
        try(var catalog=fixture.variant(true,true)) {
            assertThat(catalog.all()).hasSize(56);
            ProductRepository products=mock(ProductRepository.class);when(products.findByStatusTrue()).thenReturn(catalog.all());
            var tools=new ChatBuiltinTools(products,catalog.data(),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
            var state=new ChatDialogueMemory.State();state.structuredTurnActive=true;state.contextMode="new";
            state.toolSearch.put("query","giao tiếp");state.toolSearch.put("maxPrice",199999L);
            ChatRequestDto request=new ChatRequestDto();request.setMessage("Lần này mình muốn sách giao tiếp, khoảng dưới 200k.");
            var result=tools.searchProducts().execute(mapper.createObjectNode(),new ChatTool.Context(request,state));
            assertThat(result.products()).extracting(ChatProductDto::getId).containsExactly(57L);
            assertThat(state.discoveryExact).containsExactly(57L);
        }
    }
    @Test void primaryCommunicationArtIsInstructionWhileMentionAndDisclaimerAreNot() {
        var filters=new ChatExplicitFilters(language,data);
        Product instruction=book(1,"Cách trò chuyện","Nghệ thuật giao tiếp và phát triển bản thân.","Sách");
        Product incidental=book(2,"Một câu chuyện","Truyện gợi suy ngẫm về giao tiếp và cách ứng xử.","Văn học");
        Product disclaimer=book(3,"Nhìn lại cuộc sống","Truyện về ứng xử. Không phải sách nghệ thuật giao tiếp.","Văn học");
        assertThat(filters.matches("Gợi ý sách giao tiếp",instruction)).isTrue();
        assertThat(filters.matches("Gợi ý sách giao tiếp",incidental)).isFalse();
        assertThat(filters.matches("Gợi ý sách giao tiếp",disclaimer)).isFalse();
        assertThat(filters.matches("truyện về giao tiếp",incidental)).isTrue();
    }
    @Test void communicationAndAdventureAlternativesKeepTheirOwnEvidence() {
        var fixture=new BookLabelPilotEvaluationTest();
        try(var catalog=fixture.variant(true,true)) {
            var filters=new ChatExplicitFilters(language,catalog.data());
            for(String connector:List.of("hoặc","hay")) {
                String question="Tìm sách giao tiếp "+connector+" phiêu lưu.";
                var ids=new ChatCatalogQuery(language,catalog.data()).search(language.discoveryQuery(question),
                    new ChatSearchCriteriaDto(null,199999L,"","","",List.of()),catalog.all())
                    .stream().map(Product::getProductId).toList();
                assertThat(ids).contains(57L,13L,52L).doesNotContain(32L,50L);
                for(Product book:catalog.all())if(Set.of(32L,50L).contains(book.getProductId()))
                    assertThat(filters.matchesStorySubjects(question,book)).isFalse();
            }
            Product reflective=catalog.all().stream().filter(p->p.getProductId().equals(50L)).findFirst().orElseThrow();
            assertThat(filters.matches("truyện về giao tiếp",reflective)).isTrue();
            assertThat(filters.matchesReadingPurpose("sách suy ngẫm về giao tiếp",reflective)).isTrue();
        }
    }
}
