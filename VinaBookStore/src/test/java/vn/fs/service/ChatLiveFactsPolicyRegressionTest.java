package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.entity.*;
import vn.fs.repository.ProductRepository;

/** Regressions from the live 50-scenario run; assertions use verified store facts. */
class ChatLiveFactsPolicyRegressionTest {
    final ChatIntentService language=new ChatIntentService();
    final ObjectMapper mapper=new ObjectMapper();
    final ProductRepository products=mock(ProductRepository.class);
    final StoreKnowledgeService store=mock(StoreKnowledgeService.class);
    final ChatPolicyService policies=new ChatPolicyService(language);
    Product book;
    ChatBuiltinTools tools;

    @BeforeEach void setup() {
        book=new Product(57L,"Đắc Nhân Tâm","Dale Carnegie",220,86000d,18,
            "cover.jpg","Sách hướng dẫn kỹ năng giao tiếp và ứng xử.",LocalDate.now(),true,0,new Category(1L,"Kỹ năng"));
        when(products.findByStatusTrue()).thenReturn(List.of(book));
        when(store.text("payment")).thenReturn("Các phương thức thanh toán đang khả dụng: Thanh toán khi nhận hàng.");
        when(store.text("shipping")).thenReturn("Phí giao hàng 20.000đ, miễn phí khi tiền sách từ 300.000đ. Thời gian giao dự kiến 2–4 ngày làm việc.");
        when(store.text("returns")).thenReturn("Sách lỗi in, rách hoặc giao sai được hỗ trợ đổi trả trong 7 ngày kể từ khi nhận hàng.");
        when(store.shipping(anyLong())).thenAnswer(i->(long)i.getArgument(0)<300000?20000L:0L);
        policies.setStore(store);
        tools=new ChatBuiltinTools(products,new ChatRetrievalService(language),language,
            mock(ChatOrderSupportService.class),policies);
    }

    ChatTool.Result facts(String message,String field) throws Exception {
        ChatRequestDto request=new ChatRequestDto();request.setMessage(message);request.setActiveProductId(57L);
        return tools.getProducts().execute(mapper.valueToTree(Map.of("productId",57,"field",field)),
            new ChatTool.Context(request,new ChatDialogueMemory.State()));
    }

    @Test void answersAllRequestedCommercialFieldsIncludingDiscount() throws Exception {
        var result=facts("Đắc Nhân Tâm của ai, giá sau giảm bao nhiêu, đang giảm mấy phần trăm và còn hàng không?","detail");
        assertThat(result.answer()).contains("Dale Carnegie","70.520đ","18%","220 bản");
    }

    @Test void aZeroDiscountIsReportedRatherThanOmitted() throws Exception {
        book.setDiscount(0);
        var result=facts("Đắc Nhân Tâm giảm mấy phần trăm?","detail");
        assertThat(result.answer()).contains("không giảm giá");
    }

    @Test void aBookFollowupIsNotAQuestionAboutWhetherTheBookMentionsItsOwnTitle() throws Exception {
        var result=facts("Còn Đắc Nhân Tâm thì sao?","rating");
        assertThat(result.answer()).contains("Chưa có đánh giá").doesNotContain("nội dung đó");
    }

    @Test void aTechnologyFollowupRemainsAContentQuestion() {
        var answer=ChatBookDetailQuestions.answers("Thế còn Bluetooth 5.3 thì sao?",
            Map.of("summary","Tài liệu mạng về TCP/IP."),language,"Mạng máy tính");
        assertThat(answer).singleElement().asString().contains("Bluetooth 5.3","chưa có thông tin đã xác nhận");
    }

    @Test void multiPolicyQuestionAnswersEveryRequestedTopicWithoutOpeningAReturnCase() {
        var state=new ChatDialogueMemory.State();
        var answer=policies.answer("Shop nhận thanh toán cách nào, phí ship bao nhiêu, sách lỗi thì đổi thế nào?",state);
        assertThat(answer).contains("Thanh toán khi nhận hàng","20.000đ","300.000đ","7 ngày");
        assertThat(state.returnActive).isFalse();assertThat(state.returnAwaitingReason).isFalse();
        verify(store).text("payment");verify(store).text("shipping");verify(store).text("returns");
    }

    @Test void shippingBoundaryQuestionCalculatesAllThreeTotals() {
        var answer=policies.answer("Đơn tiền sách 299.999đ, 300.000đ và 300.001đ thì mỗi đơn hết bao nhiêu cả ship?");
        assertThat(answer).contains("Tiền sách 299.999đ: phí giao hàng 20.000đ; tổng thanh toán 319.999đ",
            "Tiền sách 300.000đ: miễn phí giao hàng; tổng thanh toán 300.000đ",
            "Tiền sách 300.001đ: miễn phí giao hàng; tổng thanh toán 300.001đ");
    }
}
