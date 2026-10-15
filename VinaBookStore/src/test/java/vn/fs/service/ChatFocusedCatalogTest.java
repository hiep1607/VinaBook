package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.fs.entity.Product;

/** Full approved catalog: exact matches must prove the expressed subject, not just its shelf. */
class ChatFocusedCatalogTest {
    final BookLabelExpansionEvaluationTest fixture=new BookLabelExpansionEvaluationTest();
    final BookFacetRankingEvaluationTest evaluation=new BookFacetRankingEvaluationTest();
    @Test void narrowSubjectsExcludeBroadlyRelatedBooksAcrossTheFullCatalog() {
        try(var v=fixture.variant(true)) {
            assertThat(evaluation.search(v,"truyện trào phúng phê phán xã hội Việt Nam",true))
                .contains(11L).doesNotContain(39L,40L,12L);
            assertThat(evaluation.search(v,"folklore Việt Nam để tìm hiểu văn hóa dân gian",true))
                .contains(35L).doesNotContain(10L,14L,24L);
            assertThat(evaluation.search(v,"giáo trình về lạm phát, thất nghiệp và tỷ giá",true))
                .contains(45L).doesNotContain(47L,42L,43L);
            assertThat(evaluation.search(v,"computer networking",true)).contains(21L)
                .doesNotContain(18L,19L,22L);
        }
    }
    @Test void aliasesNegationAndAlternativesKeepTheirMeaning() {
        var language=fixture.fixture.language;
        ChatExplicitFilters filter=new ChatExplicitFilters(language);
        Product satire=new Product();satire.setName("Tác phẩm A");satire.setDescription("Tiểu thuyết châm biếm xã hội.");
        Product folklore=new Product();folklore.setName("Tác phẩm B");folklore.setDescription("Sưu tầm truyện dân gian và văn hóa dân gian.");
        Product unknown=new Product();unknown.setName("Tác phẩm C");unknown.setDescription("Truyện hiện thực. Chưa xác nhận có yếu tố trào phúng.");
        assertThat(filter.matches("truyện satire",satire)).isTrue();
        assertThat(filter.matches("truyện trào phúng",unknown)).isFalse();
        assertThat(filter.matches("truyện, không cần trào phúng",unknown)).isTrue();
        assertThat(filter.matches("trào phúng hoặc folklore",satire)).isTrue();
        assertThat(filter.matches("trào phúng hoặc folklore",folklore)).isTrue();
        assertThat(filter.matches("trào phúng và folklore",satire)).isFalse();
        assertThat(filter.matches("trào phúng và folklore",folklore)).isFalse();
    }
    @Test void vietnameseLiteratureNeverUsesForeignComicsToFillAdditionalSlots() {
        try(var v=fixture.variant(true)) {
            List<Long> ids=evaluation.search(v,"văn học Việt Nam",true);
            assertThat(ids).contains(10L,11L,12L,13L,39L,40L);
            assertThat(v.all().stream().filter(p->ids.contains(p.getProductId())).map(Product::getName))
                .noneMatch(name->name.contains("Conan")||name.contains("Doraemon")||name.contains("Harry Potter"));
        }
    }
    @Test void translationLanguageAndVietnameseReadersDoNotProveLiteraryOrigin() {
        ChatExplicitFilters filter=new ChatExplicitFilters(fixture.fixture.language);
        Product foreign=new Product();foreign.setName("Tác phẩm nước ngoài");
        foreign.setDescription("Truyện tranh Nhật Bản, bản dịch tiếng Việt dành cho độc giả Việt Nam.");
        Product unknown=new Product();unknown.setName("Tác phẩm chưa phân loại");
        unknown.setDescription("Truyện tranh. Chưa xác nhận thuộc văn học Việt Nam.");
        assertThat(filter.matches("văn học Việt Nam",foreign)).isFalse();
        assertThat(filter.matches("văn học Nhật Bản",foreign)).isTrue();
        assertThat(filter.matches("văn học Việt Nam",unknown)).isFalse();
        assertThat(filter.matches("văn học Việt Nam hoặc truyện tranh Nhật Bản",foreign)).isTrue();
        assertThat(filter.matches("văn học, không cần văn học Việt Nam",foreign)).isTrue();
    }

}
