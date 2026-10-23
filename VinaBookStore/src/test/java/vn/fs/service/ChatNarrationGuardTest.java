package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Exercise the actual writer boundary, not just the claim-word matcher. */
class ChatNarrationGuardTest {
    ObjectMapper mapper=new ObjectMapper();
    AiGateway ai=mock(AiGateway.class);
    ChatBookNarrator narrator;
    ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    String summary="Tập hợp truyện cổ tích và văn hóa dân gian Việt Nam.";
    ChatTool.Result base;

    @BeforeEach void setup() {
        Product book=new Product(35L,"Kho truyện xưa","Tác giả",10,120000.0,0,"cover.jpg",summary,
            LocalDate.now(),true,0,new Category(1L,"Văn học"));
        ProductRepository repo=mock(ProductRepository.class);when(repo.findByStatusTrue()).thenReturn(List.of(book));
        narrator=new ChatBookNarrator(ai,mapper,repo,new ChatIntentService());
        base=new ChatTool.Result("Mình tìm được sách theo nội dung bạn hỏi.\n\n• “Kho truyện xưa” — Tác giả\n"+summary
            +"\nMình chưa có thông tin xác nhận độ tuổi đọc.",ChatIntent.DISCOVER_BOOKS,
            List.of(new ChatProductDto(35L,"Kho truyện xưa","Tác giả","Văn học",120000,120000,0,10,"cover.jpg")),
            List.of(),"COMPLETED");
    }
    void writer(String intro,String reason,String closing) throws Exception {
        String json=mapper.writeValueAsString(Map.of("intro",intro,"closing",closing,"preferredProductId",0,
            "reasons",reason.isBlank()?List.of():List.of(Map.of("productId",35,"evidenceField","summary","evidence",summary,"reason",reason))));
        when(ai.complete(anyString(),anyList(),anyString())).thenReturn(json);
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenReturn(json);
    }
    @ParameterizedTest @ValueSource(strings={
        "Đây là nguồn tư liệu có giá trị cao.",
        "Đây là nguồn tư liệu cơ bản để bắt đầu tìm hiểu.",
        "Một lựa chọn lý tưởng để khám phá.",
        "Bạn sẽ có nền tảng để bắt đầu nghiên cứu.",
        "Đây là tác phẩm đáng đọc."
    }) void unverifiedPraiseAndStarterFitCannotEnterAnyWriterSlot(String claim) throws Exception {
        writer(claim,claim,claim);
        assertThat(narrator.present(base,"Tìm văn hóa dân gian",state).answer()).isEqualTo(base.answer());
        state.structuredTurnActive=true;
        assertThat(narrator.present(base,"Tìm văn hóa dân gian",state).answer()).isEqualTo(base.answer());
    }
    @Test void groundedReasonReplacesSummaryOnceAndKeepsLimitsAndUsefulQuestion() throws Exception {
        String reason="Nội dung tập hợp truyện cổ tích Việt Nam, nối với hướng văn hóa dân gian bạn muốn tìm.";
        String closing="Bạn muốn đọc truyện hay tìm hiểu phong tục nhiều hơn?";
        writer("",reason,closing);state.structuredTurnActive=true;
        var result=narrator.present(base,"Tìm văn hóa dân gian",state);
        assertThat(result.answer()).containsOnlyOnce(reason).doesNotContain(summary)
            .contains("chưa có thông tin xác nhận độ tuổi đọc",closing);
        assertThat(result.products()).isSameAs(base.products());assertThat(result.actions()).isSameAs(base.actions());
    }
    @Test void approvedBeginnerLevelIsAllowedInReasonButNotInFreeClosing() throws Exception {
        BookKnowledgeService knowledge=mock(BookKnowledgeService.class);
        when(knowledge.approvedReadingFacts(any())).thenReturn(Map.of("summary",summary,"readingLevel","Cơ bản, dành cho người mới bắt đầu."));
        narrator.setKnowledge(knowledge);
        String reason="Tài liệu cơ bản để bắt đầu tìm hiểu truyện cổ tích Việt Nam.";
        writer("",reason,reason);
        assertThat(narrator.present(base,"Tìm văn hóa dân gian",state).answer()).containsOnlyOnce(reason);
    }
    @Test void longDiscoverySummaryIsReplacedWithoutDroppingEditionLimits() throws Exception {
        summary="Công trình sưu tầm, biên soạn truyện cổ tích Việt Nam, giới thiệu truyện kể và văn hóa dân gian trong đời sống. "
            +"Nội dung tập hợp nhiều câu chuyện từ các vùng miền trong hệ thống truyện dân gian. "
            +"Chưa xác định tập cụ thể hoặc trọn bộ. Không mặc định là bản minh họa dành riêng cho trẻ nhỏ.";
        setup();
        String reason="Nội dung sưu tầm truyện cổ tích Việt Nam, nối với hướng văn hóa dân gian bạn muốn tìm.";
        writer("",reason,"");state.structuredTurnActive=true;
        assertThat(narrator.present(base,"Tìm văn hóa dân gian",state).answer()).containsOnlyOnce(reason)
            .doesNotContain("Nội dung tập hợp nhiều câu chuyện")
            .contains("Chưa xác định tập cụ thể hoặc trọn bộ.","Không mặc định là bản minh họa dành riêng cho trẻ nhỏ.");
    }
    @Test void writerFailureKeepsTheVerifiedAnswerAndProductReceipts() {
        state.structuredTurnActive=true;
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenThrow(new IllegalStateException("timeout"));
        assertThat(narrator.present(base,"Tìm văn hóa dân gian",state)).isSameAs(base);
    }
    @Test void writerCannotInventProductIdsOrChangeDatabasePrice() {
        state.structuredTurnActive=true;
        when(ai.completeStructured(anyString(),anyList(),anyString(),any())).thenReturn("{\"reasons\":[{\"productId\":999,\"evidenceField\":\"summary\",\"evidence\":\"Tập hợp truyện cổ tích và văn hóa dân gian Việt Nam.\",\"reason\":\"Sách được model tự tạo.\"}]}");
        var result=narrator.present(base,"Tìm văn hóa dân gian",state);
        assertThat(result.products()).extracting(vn.fs.dto.chat.ChatProductDto::getId).containsExactly(35L);
        assertThat(result.products().get(0).getPrice()).isEqualTo(120000);
        assertThat(result.answer()).doesNotContain("model tự tạo","999");
    }
    @Test void repeatedReasonAndClosingDoNotReappearOnTheNextTurn() throws Exception {
        String reason="Nội dung tập hợp truyện cổ tích Việt Nam, nối với hướng văn hóa dân gian bạn muốn tìm.";
        String closing="Bạn muốn đọc truyện hay tìm hiểu phong tục nhiều hơn?";
        state.recentDialogue.add(Map.of("user","Tìm dân gian","assistant",reason+" "+closing));
        writer("",reason,closing);
        assertThat(narrator.present(base,"Nói tiếp",state).answer()).isEqualTo(base.answer());
    }
}
