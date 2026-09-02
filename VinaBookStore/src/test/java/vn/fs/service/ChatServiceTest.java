package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatTurnDto;
import vn.fs.entity.Category;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;
import vn.fs.config.CurrentUserAccess;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	@Mock
	private ProductRepository products;

	@Mock
	private AiGateway ai;
	@Mock private ChatOrderSupportService orderSupport;
	@Mock private ChatAnalyticsService analytics;
	@Mock private CurrentUserAccess access;

	private ChatService service;

	@BeforeEach
	void setUp() {
		ChatIntentService intentService = new ChatIntentService();
		ObjectMapper mapper = new ObjectMapper();
		ChatRetrievalService retrieval = new ChatRetrievalService(intentService);
		service = new ChatService(products, ai, intentService,
				new ChatQueryPlannerService(ai, mapper, intentService), retrieval,
				new ChatGroundingService(mapper, retrieval), new ChatPolicyService(intentService),
				orderSupport, analytics, access);
		when(analytics.record(any(), any(), anyString(), anyBoolean(), anyInt(), anyLong()))
				.thenReturn("message-id");
		when(products.findByStatusTrue()).thenReturn(List.of(
				product(57L, "Đắc Nhân Tâm", "Dale Carnegie", "Kỹ năng", 86000D, 18, 220),
				product(58L, "Nhà Giả Kim", "Paulo Coelho", "Tiểu thuyết", 79000D, 20, 230)));
	}

	@Test
	void localModeUsesStorePolicyAndNeverNeedsApiKey() {
		when(ai.isConfigured()).thenReturn(false);
		ChatResponseDto response = service.reply(request("Phí giao hàng thế nào?"));

		assertThat(response.isFallback()).isFalse();
		assertThat(response.getProvider()).isEqualTo("local");
		assertThat(response.getAnswer()).contains("20.000đ").contains("300.000đ");
		assertThat(response.getProducts()).isEmpty();
	}

	@Test
	void policyQuestionContainingBookWordDoesNotReturnProductCards() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Sách lỗi có được đổi trả không?"));

		assertThat(response.getAnswer()).contains("7 ngày");
		assertThat(response.getProducts()).isEmpty();
	}

	@Test
	void productQuestionReturnsGroundedProductCards() {
		when(ai.isConfigured()).thenReturn(false);
		ChatResponseDto response = service.reply(request("Tìm sách của Paulo Coelho"));

		assertThat(response.getProducts()).isNotEmpty();
		assertThat(response.getProducts().get(0).getName()).isEqualTo("Nhà Giả Kim");
		assertThat(response.getProducts().get(0).getPrice()).isEqualTo(63200L);
	}

	@Test
	void exactBookQuestionOnlyReturnsTheMentionedBook() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Nhà Giả Kim nói về điều gì?"));

		assertThat(response.getProducts()).extracting("name").containsExactly("Nhà Giả Kim");
	}

	@Test
	void comparisonReturnsOnlyBooksNamedInTheQuestion() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("So sánh Nhà Giả Kim và Đắc Nhân Tâm"));

		assertThat(response.getProducts()).extracting("name")
				.containsExactlyInAnyOrder("Nhà Giả Kim", "Đắc Nhân Tâm");
	}

	@Test
	void naturalDiscoveryQuestionWithWordsInBetweenReturnsSuggestions() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Giúp tôi tìm vài cuốn sách chủ đề phiêu lưu"));

		assertThat(response.getProducts()).isNotEmpty();
		assertThat(response.getChips()).containsExactly("Còn cuốn khác không?", "So sánh các cuốn này", "Sách rẻ hơn");
	}

	@Test
	void followUpComparisonUsesProductsFromPreviousAnswer() {
		when(ai.isConfigured()).thenReturn(false);
		ChatRequestDto request = request("So sánh các cuốn này");
		request.setContextProductIds(List.of(57L, 58L));

		ChatResponseDto response = service.reply(request);

		assertThat(response.getProducts()).extracting("name")
				.containsExactly("Đắc Nhân Tâm", "Nhà Giả Kim");
		assertThat(response.getAnswer()).contains("So sánh nhanh");
	}

	@Test
	void followUpAlternativeExcludesPreviouslyShownProduct() {
		when(ai.isConfigured()).thenReturn(false);
		ChatRequestDto request = request("Còn cuốn khác không?");
		request.setContextProductIds(List.of(57L));

		ChatResponseDto response = service.reply(request);

		assertThat(response.getProducts()).extracting("name").containsExactly("Nhà Giả Kim");
	}

	@Test
	void followUpAlternativeKeepsPreviousTopicAndBudget() {
		when(ai.isConfigured()).thenReturn(false);
		ChatRequestDto request = request("Sách rẻ hơn");
		request.setContextProductIds(List.of(57L));
		request.setHistory(List.of(new ChatTurnDto("user", "Tìm sách giao tiếp dưới 150k")));

		ChatResponseDto response = service.reply(request);

		assertThat(response.getCriteria().getTopics()).containsExactly("giao tiếp");
		assertThat(response.getCriteria().getMaxPrice()).isEqualTo(150000L);
		assertThat(response.getCriteria().getSort()).isEqualTo("price-asc");
		assertThat(response.getProducts()).isEmpty();
	}

	@Test
	void categoryListQuestionDoesNotAttachArbitraryBestsellers() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Có những thể loại nào?"));

		assertThat(response.getProducts()).isEmpty();
	}

	@Test
	void readingNeedUsesTopicInsteadOfGenericBestsellers() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Tôi nên đọc gì để cải thiện giao tiếp?"));

		assertThat(response.getProducts()).extracting("name").containsExactly("Đắc Nhân Tâm");
	}

	@Test
	void unrelatedConversationDoesNotRepeatGenericSuggestions() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Bạn là ai?"));

		assertThat(response.getProducts()).isEmpty();
		assertThat(response.getIntent()).isEqualTo(vn.fs.dto.chat.ChatIntent.SMALL_TALK);
	}

	@Test
	void exactStockQuestionReadsQuantityFromCatalog() {
		when(ai.isConfigured()).thenReturn(false);

		ChatResponseDto response = service.reply(request("Cuốn Nhà Giả Kim còn chính xác bao nhiêu bản?"));

		assertThat(response.getAnswer())
				.contains("Nhà Giả Kim")
				.contains("100 cuốn")
				.contains("có thể thay đổi");
		assertThat(response.getProducts().get(0).getQuantity()).isEqualTo(100);
	}

	@Test
	void configuredProviderIsUsedWithoutExposingCredentials() {
		when(ai.isConfigured()).thenReturn(true);
		when(ai.provider()).thenReturn("openai");
		when(ai.model()).thenReturn("configured-model");
		when(ai.complete(anyString(), anyList(), anyString())).thenReturn(
				"{\"intent\":\"DISCOVER_BOOKS\",\"searchTerms\":[],\"topics\":[],\"requestedCount\":1}",
				"{\"intro\":\"Mình đã kiểm tra catalog.\",\"recommendations\":[{\"productId\":57,\"reason\":\"Phù hợp để bắt đầu.\"}],\"closing\":\"\"}");

		ChatResponseDto response = service.reply(request("Gợi ý sách hay"));

		assertThat(response.isFallback()).isFalse();
		assertThat(response.getProvider()).isEqualTo("openai");
		assertThat(response.getModel()).isEqualTo("configured-model");
		assertThat(response.getAnswer()).contains("Đắc Nhân Tâm").contains("70.520đ");
	}

	@Test
	void discoveryCardsFollowBookNamesActuallyMentionedByAi() {
		when(ai.isConfigured()).thenReturn(true);
		when(ai.provider()).thenReturn("gemini");
		when(ai.model()).thenReturn("configured-model");
		when(ai.complete(anyString(), anyList(), anyString()))
				.thenReturn(
						"{\"intent\":\"DISCOVER_BOOKS\",\"searchTerms\":[],\"topics\":[],\"requestedCount\":1}",
						"{\"intro\":\"Mình đã kiểm tra catalog.\",\"recommendations\":[{\"productId\":58,\"reason\":\"Phù hợp để bắt đầu đọc.\"}],\"closing\":\"\"}");

		ChatResponseDto response = service.reply(request("Gợi ý sách hay"));

		assertThat(response.getProducts()).extracting("name").containsExactly("Nhà Giả Kim");
	}

	private ChatRequestDto request(String message) {
		ChatRequestDto request = new ChatRequestDto();
		request.setMessage(message);
		return request;
	}

	private Product product(Long id, String name, String author, String categoryName,
			Double price, int discount, int sold) {
		String description = "Nhà Giả Kim".equals(name)
				? "Hành trình phiêu lưu và khám phá vận mệnh."
				: "Nghệ thuật giao tiếp và phát triển bản thân.";
		return new Product(id, name, author, 100, price, discount, "cover.jpg", description,
				LocalDate.now(), true, sold, new Category(id, categoryName));
	}
}
