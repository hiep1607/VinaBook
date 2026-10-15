package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import vn.fs.entity.Product;

/** The same real retrieval code and full 56-book catalog, with only the 36 new label sets changed. */
class BookLabelExpansionEvaluationTest {
    final BookLabelPilotEvaluationTest fixture=new BookLabelPilotEvaluationTest();
    final BookFacetRankingEvaluationTest evaluation=new BookFacetRankingEvaluationTest();
    final ObjectMapper mapper=new ObjectMapper();
    final JsonNode snapshot=resource("catalog-before.json"),manifest=resource("labels.json"),queries=resource("queries.json");
    JsonNode resource(String name) {
        try(var in=getClass().getResourceAsStream("/book-label-expansion/"+name)){return mapper.readTree(in);}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    BookLabelPilotEvaluationTest.Variant variant(boolean after){return fixture.variant(snapshot,manifest,after,true);}
    @Test void compareFullCatalogWithOnlyTheRemainingLabelsChanged() throws Exception {
        var normal=mapper.createArrayNode();var synonyms=mapper.createArrayNode();
        queries.path("cases").forEach(c->{if(c.path("id").asText().startsWith("S"))synonyms.add(c);else normal.add(c);});
        List<Map<String,Object>> groups=new ArrayList<>();
        try(var before=variant(false);var after=variant(true)) {
            groups.add(evaluation.evaluate("remaining36-standard",normal,true,before,after));
            groups.add(evaluation.evaluate("remaining36-synonyms",synonyms,true,before,after));
            groups.add(evaluation.evaluate("pilot-original30",fixture.cases.path("cases"),true,before,after));
            JsonNode novel=fixture.resource("ranking-novel.json");
            groups.add(evaluation.evaluate("pilot-novel30",novel.path("cases"),true,before,after));
            groups.add(evaluation.evaluate("previous-outside-pilot8",novel.path("unlabelledControls"),true,before,after));
        }
        Path out=Path.of(System.getProperty("expansion.output","target/book-label-expansion-results.json"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),Map.of("scope",queries.path("scope").asText(),"providerCalls",0,"groups",groups));
        for(var group:groups){var brief=new LinkedHashMap<>(group);brief.remove("cases");System.out.println("LABEL_EXPANSION "+mapper.writeValueAsString(brief));}
        for(var group:groups.subList(0,2)) {
            assertThat(((Number)group.get("afterHit1")).intValue()).as((String)group.get("name"))
                .isGreaterThanOrEqualTo(((Number)group.get("beforeHit1")).intValue());
            assertThat(((Number)group.get("afterHit3")).intValue()).as((String)group.get("name"))
                .isGreaterThanOrEqualTo(((Number)group.get("beforeHit3")).intValue());
        }
        assertThat(((Number)groups.get(1).get("afterHit1")).intValue()).isGreaterThan(((Number)groups.get(1).get("beforeHit1")).intValue());
    }
    @Test void labelsDoNotReplaceFactsPricesStockOrTheOriginalPilotProfiles() {
        Set<Long> expanded=new HashSet<>();manifest.path("books").forEach(b->expanded.add(b.path("productId").asLong()));
        try(var before=variant(false);var after=variant(true)) {
            assertThat(after.cohort()).hasSize(36);assertThat(after.all()).hasSize(56);
            for(Product p:after.all()) {
                Product old=before.all().stream().filter(b->b.getProductId().equals(p.getProductId())).findFirst().orElseThrow();
                assertThat(after.data().approvedReadingFacts(p)).as(p.getName()).isEqualTo(before.data().approvedReadingFacts(old));
                assertThat(after.data().effectivePrice(p)).isEqualTo(before.data().effectivePrice(old));
                assertThat(p.getQuantity()).isEqualTo(old.getQuantity());
                if(!expanded.contains(p.getProductId()))assertThat(after.data().searchableText(p)).isEqualTo(before.data().searchableText(old));
            }
        }
    }
    @Test void expandedLabelsKeepObservedSubjectAndBudgetGuards() {
        try(var after=variant(true)) {
            var filters=new ChatExplicitFilters(fixture.language,after.data());
            List<Long> animals=evaluation.search(after,"truyện về thế giới loài vật",true);
            assertThat(animals).contains(13L).doesNotContain(36L,48L,51L);
            assertThat(evaluation.search(after,"truyện về trẻ em và môi trường học",true)).contains(38L).doesNotContain(13L,50L);
            Product blockchain=after.all().stream().filter(b->b.getProductId().equals(18L)).findFirst().orElseThrow();
            assertThat(filters.matches("học lập trình Java",blockchain)).isFalse();
            Product database=after.all().stream().filter(b->b.getProductId().equals(23L)).findFirst().orElseThrow();
            assertThat(ChatBookFacts.studyFeatures(Set.of("exercises","answerKey"),after.data().approvedReadingFacts(database),fixture.language))
                .allMatch(line->line.contains("chưa có thông tin đã xác nhận"));
        }
    }
}
