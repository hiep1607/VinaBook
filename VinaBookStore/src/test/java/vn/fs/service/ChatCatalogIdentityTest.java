package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.fs.dto.chat.*;
import vn.fs.entity.*;
import vn.fs.repository.*;

class ChatCatalogIdentityTest {
    ChatIntentService language=new ChatIntentService();
    ChatRetrievalService data=new ChatRetrievalService(language);
    ProductRepository products=mock(ProductRepository.class);
    AiGateway ai=mock(AiGateway.class);
    ChatAnalyticsService analytics=mock(ChatAnalyticsService.class);
    ChatDialogueMemory.State state=new ChatDialogueMemory.State();
    LegacyChatOrchestrator chat;
    ChatToolRegistry registry;
    List<Product> catalog;

    @BeforeEach void setup() {
        catalog=List.of(
            book(3,"Hiến Pháp Năm 2013 (Sửa Đổi, Bổ Sung Năm 2025)","Quốc Hội Việt Nam","Bìa ghi sửa đổi năm 2025; chưa xác nhận mọi cập nhật sau đó."),
            book(4,"Luật Doanh Nghiệp","Quốc Hội Việt Nam","Văn bản luật, không phải cẩm nang khởi nghiệp từng bước."),
            book(5,"Bộ Luật Lao Động","Quốc Hội Việt Nam","Đây là văn bản luật, không phải sách phân tích tình huống."),
            book(6,"Cẩm Nang Pháp Luật Dành Cho Doanh Nghiệp","Tăng Bình & Ái Phương","Nội dung về tiền lương, thuế và bảo hiểm."),
            book(17,"AI - Sức Mạnh Của Trí Tuệ Nhân Tạo","Nguyễn Văn Lượng","Giới thiệu AI phổ thông; không phải giáo trình lập trình chuyên sâu."),
            book(18,"Blockchain Cơ Bản","Daniel Drescher","Daniel Drescher giải thích blockchain, không cần toán hay lập trình; không bảo đảm lợi nhuận đầu tư."),
            book(22,"Giáo Trình Lập Trình Java","Phạm Xuân Lâm (chủ biên)","Chưa xác nhận Java 21 hoặc Spring Boot."),
            book(44,"Giáo Trình Nhập Môn Trí Tuệ Nhân Tạo","Từ Minh Phương","Các phương pháp tìm kiếm, biểu diễn tri thức và học máy."),
            book(45,"Giáo Trình Kinh Tế Vĩ Mô","Lê Thanh Tùng","Đây là kinh tế vĩ mô, không phải giáo trình kinh tế vi mô."));
        when(products.findByStatusTrue()).thenReturn(catalog);
        RateRepository rates=mock(RateRepository.class);when(rates.averageRatings()).thenReturn(List.of());data.setRates(rates);
        ChatBuiltinTools builtins=new ChatBuiltinTools(products,data,language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        registry=new ChatToolRegistry(List.of(builtins.searchProducts(),builtins.recommendBooks(),builtins.getProducts(),builtins.getPolicy(),builtins.respond()));
        when(ai.provider()).thenReturn("gemini");when(ai.modelUsed()).thenReturn("fixture");
        when(analytics.record(any(),any(),anyString(),anyBoolean(),anyInt(),anyLong())).thenReturn("test");
        chat=new LegacyChatOrchestrator(ai,new ObjectMapper(),registry,analytics);chat.setCatalog(products,data);
    }

    @Test void exactTitleDoesNotIncludeAnotherBookSharingTwoWords() {
        assertThat(new ChatCatalogQuery(language,data).named("Cẩm Nang Pháp Luật Dành Cho Doanh Nghiệp đề cập những gì?",catalog))
            .extracting(Product::getProductId).containsExactly(6L);
    }
    @Test void comparisonPreservesTwoExplicitTitles() {
        assertThat(new ChatCatalogQuery(language,data).named("So sánh Luật Doanh Nghiệp với Cẩm Nang Pháp Luật Dành Cho Doanh Nghiệp",catalog))
            .extracting(Product::getProductId).containsExactly(4L,6L);
    }
    @Test void authorAndContentQuestionAnswersBothWithoutAnUnaskedPrice() {
        var response=turn("Bộ Luật Lao Động là sách phân tích tình huống hay văn bản luật? Ai là tác giả?");
        assertThat(response.getAnswer()).contains("Quốc Hội Việt Nam","không phải sách phân tích tình huống").doesNotContain("Giá sau giảm");
        verify(ai,never()).complete(anyString(),anyList(),anyString());
    }
    @Test void chiefEditorIsAnAuthorFactAndInventoryDoesNotHideIt() {
        assertThat(turn("Giáo Trình Lập Trình Java đang bán do ai chủ biên?").getAnswer()).contains("Phạm Xuân Lâm (chủ biên)");
    }
    @Test void currentEditionFollowupUsesThePreviouslyFocusedLawBook() {
        turn("Hiến Pháp Năm 2013 nói về gì?");
        var response=turn("Bản đó có phải bản mới nhất và còn đúng với pháp luật năm 2026 không?");
        assertThat(response.getExecution()).containsEntry("tool","getProducts");
        assertThat(response.getAnswer()).contains("chưa xác nhận mọi cập nhật").doesNotContain("Bạn muốn hỏi chính sách nào");
    }
    @Test void disputedAuthorDoesNotBecomeAMissingTitleSearch() {
        state.focus=18L;state.shortlist=new ArrayList<>(List.of(18L));
        var response=turn("Có phải đây là sách Don Tapscott, dạy đầu tư Bitcoin để có lãi không?");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(18L);
        assertThat(response.getAnswer()).contains("Daniel Drescher","không bảo đảm lợi nhuận");
    }
    @Test void anotherTitleComparedWithThatBookRetainsBothBooks() {
        state.focus=17L;state.shortlist=new ArrayList<>(List.of(17L));
        var response=turn("Nếu muốn học thuật toán AI thì cuốn đó hay Giáo Trình Nhập Môn Trí Tuệ Nhân Tạo sát hơn?");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(17L,44L);
        assertThat(response.getAnswer()).doesNotContain("Bạn gửi tên cuốn thứ hai");
    }
    @Test void microeconomicsQuestionDoesNotRecommendTheMacroBookAsAnExactMatch() {
        state.focus=45L;state.shortlist=new ArrayList<>(List.of(45L));
        var response=turn("Mình muốn học quyết định của từng người tiêu dùng, sách đó có phải kinh tế vi mô không?");
        assertThat(response.getProducts()).extracting(ChatProductDto::getId).containsExactly(45L);
        assertThat(response.getAnswer()).contains("không phải giáo trình kinh tế vi mô").doesNotContain("Sách đúng yêu cầu");
    }
    @Test void shippingQuestionAfterABookStillUsesStorePolicy() {
        state.focus=3L;state.shortlist=new ArrayList<>(List.of(3L));
        assertThat(turn("Phí giao hàng là bao nhiêu?").getAnswer()).contains("20.000đ");
    }
    @Test void authorAndPrerequisiteQuestionKeepsBothRequestedParts() {
        var response=turn("Blockchain Cơ Bản của ai, có cần giỏi toán hoặc lập trình trước không?");
        assertThat(response.getAnswer()).contains("Daniel Drescher","không cần toán hay lập trình");
    }
    @Test void genericPlannerQueryCannotIgnoreAnExactMicroeconomicsRequest() throws Exception {
        var response=discover("Chỉ tìm giáo trình kinh tế vi mô, không lấy vĩ mô.","{\"query\":\"kinh tế\"}");
        assertThat(response.products()).isEmpty();
    }
    @Test void excludingMacroeconomicsDoesNotExcludeARealMicroeconomicsBook() throws Exception {
        Product micro=book(46,"Giáo Trình Kinh Tế Vi Mô","Tác giả","Học kinh tế vi mô về quyết định của người tiêu dùng.");
        List<Product> withMicro=new ArrayList<>(catalog);withMicro.add(micro);when(products.findByStatusTrue()).thenReturn(withMicro);
        var response=discover("Chỉ tìm giáo trình kinh tế vi mô, không lấy vĩ mô.","{\"query\":\"kinh tế\",\"excludedQuery\":\"vĩ mô\"}");
        assertThat(response.products()).extracting(ChatProductDto::getId).containsExactly(46L);
    }
    @Test void aDisclaimerAboutSpringBootDoesNotConfirmTheRequiredTopic() throws Exception {
        var response=discover("Mình cần sách thực sự dạy Spring Boot, chỉ lấy cuốn đã xác nhận có nội dung đó.","{\"query\":\"Java\"}");
        assertThat(response.products()).isEmpty();
    }
    @Test void confirmedSpringBootBookIsAcceptedAndRetainedForMoreResults() throws Exception {
        Product spring=book(60,"Java Web","Tác giả","Hướng dẫn ứng dụng Spring Boot với Java.");
        List<Product> withSpring=new ArrayList<>(catalog);withSpring.add(spring);when(products.findByStatusTrue()).thenReturn(withSpring);
        var first=discover("Chỉ lấy sách dạy Spring Boot.","{\"query\":\"Java\"}");
        assertThat(first.products()).extracting(ChatProductDto::getId).containsExactly(60L);
        var next=discover("Còn cuốn nào khác cùng chủ đề không?","{\"scope\":\"more\"}");
        assertThat(next.products()).isEmpty();
    }
    @Test void aMicroTitleIsNotAnExactMentionOfTheMacroTitle() {
        assertThat(new ChatCatalogQuery(language,data).named("Giáo Trình Kinh Tế Vi Mô của ai?",catalog)).isEmpty();
    }
    @Test void switchingFromUnavailableSpringBootToJavaDropsTheOldRequirement() throws Exception {
        assertThat(discover("Chỉ lấy sách dạy Spring Boot.","{\"query\":\"Spring Boot\"}").products()).isEmpty();
        var response=discover("Bỏ yêu cầu Spring Boot, chuyển sang giáo trình Java đang bán nhé.",
            "{\"query\":\"Java\",\"scope\":\"continue\"}");
        assertThat(response.products()).extracting(ChatProductDto::getId).containsExactly(22L);
        assertThat(state.discoveryExact).containsExactly(22L);
        assertThat(state.discoveryNearby).isEmpty();
    }
    @Test void switchingFromProgrammingToMacroeconomicsDoesNotRetainTheOldSubject() throws Exception {
        discover("Chỉ lấy sách dạy Spring Boot.","{\"query\":\"Spring Boot\"}");
        var response=discover("Bỏ yêu cầu Spring Boot, đổi sang giáo trình kinh tế vĩ mô nhé.",
            "{\"query\":\"kinh tế\",\"scope\":\"continue\"}");
        assertThat(response.products()).extracting(ChatProductDto::getId).containsExactly(45L);
        assertThat(state.discoveryExact).containsExactly(45L);
        assertThat(state.discoveryNearby).isEmpty();
    }
    ChatTool.Result discover(String message,String arguments) throws Exception {
        ChatRequestDto request=new ChatRequestDto();request.setSessionId("identity-discovery-test");request.setMessage(message);
        request.setHistory(List.of());request.setCartItems(List.of());
        return registry.execute("searchProducts",new ObjectMapper().readTree(arguments),new ChatTool.Context(request,state));
    }
    Product book(long id,String name,String author,String description) {
        return new Product(id,name,author,10,100000D,0,"",description,LocalDate.now(),true,1,new Category(1L,"Sách"));
    }
    ChatResponseDto turn(String message) {
        ChatRequestDto request=new ChatRequestDto();request.setSessionId("identity-test");request.setMessage(message);
        request.setHistory(List.of());request.setCartItems(List.of());return chat.reply(request,state);
    }
}
