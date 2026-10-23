package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.entity.Category;
import vn.fs.entity.Product;

class ChatRagServiceTest {
	private ChatRetrievalService retrieval;
	private ChatCatalogQuery search;
	private List<Product> catalog;

	@BeforeEach
	void setUp() {
		retrieval = new ChatRetrievalService(new ChatIntentService());
		search = new ChatCatalogQuery(new ChatIntentService(),retrieval);
		Category law = new Category(1L, "Sách Chính trị – pháp luật");
		Category psychology = new Category(7L, "Sách Tâm lý");
		Category technology = new Category(2L, "Sách Khoa học công nghệ – Kinh tế");
		catalog = List.of(
				product(3L, "Hiến Pháp Nước Cộng Hòa Xã Hội Chủ Nghĩa Việt Nam", "Quốc Hội Việt Nam",
						45000D, "Văn bản pháp lý cao nhất, nền tảng hệ thống pháp luật.", law),
				product(5L, "Luật Lao Động Thực Hành", "Nguyễn Văn Phần",
						92000D, "Hướng dẫn hợp đồng, tiền lương và tranh chấp lao động.", law),
				product(6L, "Cẩm Nang Pháp Lý Cho Doanh Nghiệp", "Trương Thanh Đức",
						135000D, "Phòng ngừa rủi ro hợp đồng, thuế và sở hữu trí tuệ.", law),
				product(57L, "Đắc Nhân Tâm", "Dale Carnegie",
						86000D, "Nghệ thuật giao tiếp và ứng xử.", psychology),
				product(9L, "Clean Code - Mã Sạch Và Cách Viết", "Robert C. Martin",
						168000D, "Cẩm nang viết mã nguồn sạch cho lập trình viên.", technology));
	}

	@Test
	void retrievesRealLegalBooksInsteadOfGenericBestsellers() {
		ChatSearchCriteriaDto criteria = new ChatSearchCriteriaDto(null, null, "", "", "",
				List.of("pháp luật"));
        List<Product> result = search.search("pháp luật",criteria,catalog);

		assertThat(result).extracting(Product::getName)
				.contains("Hiến Pháp Nước Cộng Hòa Xã Hội Chủ Nghĩa Việt Nam",
						"Luật Lao Động Thực Hành", "Cẩm Nang Pháp Lý Cho Doanh Nghiệp")
				.doesNotContain("Đắc Nhân Tâm");
	}

	@Test void ranksOnlyProductsWithRealRatings() {
		vn.fs.repository.RateRepository rates = org.mockito.Mockito.mock(vn.fs.repository.RateRepository.class);
		org.mockito.Mockito.when(rates.averageRatings()).thenReturn(List.of(new Object[]{3L, 3.5D}, new Object[]{5L, 4.8D}, new Object[]{6L, 4.2D}));
		retrieval.setRates(rates);
		ChatSearchCriteriaDto criteria = new ChatSearchCriteriaDto(null, null, "", "", "rating", List.of());
		criteria.setMinRating(4D);
        assertThat(retrieval.rated(catalog,criteria))
				.extracting(Product::getProductId).containsExactly(5L, 6L);
	}

	@Test
	void rejectsBroadScienceMatchesWhenCatalogHasNoQuantumBook() {
        List<Product> result = search.search("Sách lượng tử cho người mới bắt đầu",ChatSearchCriteriaDto.empty(),catalog);

		assertThat(result).isEmpty();
	}

	@Test
	void prioritizesCommunicationBookWithoutTrustingAnInferredCategoryFilter() {
        List<Product> result = search.search("Tôi ngại giao tiếp và muốn tự tin hơn",ChatSearchCriteriaDto.empty(),catalog);

		assertThat(result).extracting(Product::getName).first().isEqualTo("Đắc Nhân Tâm");
	}

	@Test
	void conservativeFallbackDoesNotTurnQuantumIntoUnrelatedTechnologyBooks() {
		List<Product> result = retrieval.directMatches("Sách lượng tử cho người mới bắt đầu",
				ChatSearchCriteriaDto.empty(), catalog);

		assertThat(result).isEmpty();
	}

    // Writer identity/fact guards are exercised against ChatBookNarrator in
    // ChatNarrationGuardTest, rather than the retired JSON response parser.

	private Product product(Long id, String name, String author, Double price, String description,
			Category category) {
		return new Product(id, name, author, 100, price, 0, "cover.jpg", description,
				LocalDate.now(), true, 20, category);
	}
}
