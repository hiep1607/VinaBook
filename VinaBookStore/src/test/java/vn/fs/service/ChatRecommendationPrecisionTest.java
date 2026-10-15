package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.fs.entity.Product;
import vn.fs.dto.chat.ChatSearchCriteriaDto;

/** Reproduce the live failures against all 56 catalog books, including unlabelled controls. */
class ChatRecommendationPrecisionTest {
    final BookLabelPilotEvaluationTest fixture=new BookLabelPilotEvaluationTest();
    List<Long> search(BookLabelPilotEvaluationTest.Variant v,String query,Long budget) {
        return new ChatCatalogQuery(fixture.language,v.data()).search(query,
            new ChatSearchCriteriaDto(null,budget,"","","",List.of()),v.all())
            .stream().map(Product::getProductId).toList();
    }
    @Test void exactResultsRequireEveryStatedStorySubjectBeforeRanking() throws Exception {
        List<Map<String,Object>> evidence=new ArrayList<>();
        try(var v=fixture.variant(true,true)) {
            List<Long> animals=search(v,"truyện về thế giới loài vật",99999L);
            assertThat(animals).contains(13L).doesNotContain(51L,48L,37L);
            evidence.add(Map.of("query","truyện về thế giới loài vật","maxPrice",99999,"exactIds",animals));
            List<Long> poverty=search(v,"truyện tuổi thơ có cả nỗi đau và hoàn cảnh nghèo khó",99999L);
            assertThat(poverty).contains(29L).doesNotContain(50L,38L,51L);
            evidence.add(Map.of("query","truyện tuổi thơ có cả nỗi đau và hoàn cảnh nghèo khó","maxPrice",99999,"exactIds",poverty));
            List<Long> school=search(v,"truyện về trẻ em và môi trường học",null);
            assertThat(school).contains(38L).doesNotContain(13L,37L,50L);
            evidence.add(Map.of("query","truyện về trẻ em và môi trường học","exactIds",school));
            assertThat(search(v,"cuộc sống ở trường của cô bé",null)).contains(38L).doesNotContain(37L,50L);
            assertThat(search(v,"truyện về loài vật hoặc trường học",null)).contains(13L,38L);
            assertThat(search(v,"truyện về loài vật và trường học",null)).doesNotContain(13L,38L);
            assertThat(search(v,"truyện về loài vật",50000L)).doesNotContain(13L);
        }
        Path out=Path.of(System.getProperty("precision.output","target/chat-recommendation-precision.json"));
        Files.createDirectories(out.toAbsolutePath().getParent());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.toFile(),evidence);
    }
    @Test void negationAndAudienceDoNotBecomePositiveSubjectEvidence() {
        ChatExplicitFilters filters=new ChatExplicitFilters(fixture.language);
        Product story=new Product();story.setName("Một câu chuyện");story.setDescription("Truyện về tình bạn. Chưa xác nhận có nội dung về nghèo khó.");
        assertThat(filters.matchesStorySubjects("truyện về nghèo khó",story)).isFalse();
        assertThat(filters.matchesStorySubjects("truyện, không cần nghèo khó",story)).isTrue();
        try(var v=fixture.variant(true,true)) {
            Product adultStory=v.all().stream().filter(b->b.getProductId().equals(38L)).findFirst().orElseThrow();
            assertThat(new ChatExplicitFilters(fixture.language,v.data()).matches("truyện về trẻ em và môi trường học",adultStory)).isTrue();
        }
    }
}
