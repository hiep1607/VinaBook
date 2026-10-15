package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import vn.fs.entity.Product;
import vn.fs.dto.chat.ChatSearchCriteriaDto;

class BookFacetRankingEvaluationTest {
    final BookLabelPilotEvaluationTest fixture=new BookLabelPilotEvaluationTest();
    final ObjectMapper mapper=new ObjectMapper();
    List<Long> search(BookLabelPilotEvaluationTest.Variant v,String question,boolean full) {
        List<Product> books=full?v.all():v.cohort();
        return new ChatCatalogQuery(fixture.language,v.data()).search(question,
            new ChatSearchCriteriaDto(null,null,"","","",List.of()),books).stream().map(Product::getProductId).toList();
    }
    Map<String,Object> evaluate(String name,JsonNode cases,boolean full,
            BookLabelPilotEvaluationTest.Variant before,BookLabelPilotEvaluationTest.Variant after) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(JsonNode c:cases) {
            Set<Long> relevant=new LinkedHashSet<>();c.path("relevantIds").forEach(n->relevant.add(n.asLong()));
            List<Long> old=search(before,c.path("query").asText(),full),now=search(after,c.path("query").asText(),full);
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",c.path("id").asText());row.put("query",c.path("query").asText());row.put("relevantIds",relevant);
            row.put("beforeIds",old);row.put("afterIds",now);
            row.put("beforeHit1",!old.isEmpty()&&relevant.contains(old.get(0)));row.put("afterHit1",!now.isEmpty()&&relevant.contains(now.get(0)));
            row.put("beforeHit3",old.stream().limit(3).anyMatch(relevant::contains));row.put("afterHit3",now.stream().limit(3).anyMatch(relevant::contains));
            rows.add(row);
        }
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("name",name);summary.put("count",rows.size());
        for(String key:List.of("beforeHit1","afterHit1","beforeHit3","afterHit3"))summary.put(key,rows.stream().filter(r->Boolean.TRUE.equals(r.get(key))).count());
        summary.put("regressions",rows.stream().filter(r->Boolean.TRUE.equals(r.get("beforeHit1"))&&!Boolean.TRUE.equals(r.get("afterHit1"))).map(r->r.get("id")).toList());
        summary.put("cases",rows);return summary;
    }
    @Test void compareFrozenOldAndNovelQueriesWithLabelsHeldConstant() throws Exception {
        JsonNode novel=fixture.resource("ranking-novel.json");
        List<Map<String,Object>> groups=new ArrayList<>();
        try(var before=fixture.variant(true,false);var after=fixture.variant(true,true)) {
            groups.add(evaluate("original-20",fixture.cases.path("cases"),false,before,after));
            groups.add(evaluate("novel-20",novel.path("cases"),false,before,after));
            groups.add(evaluate("original-56",fixture.cases.path("cases"),true,before,after));
            groups.add(evaluate("novel-56",novel.path("cases"),true,before,after));
            groups.add(evaluate("unlabelled-controls-56",novel.path("unlabelledControls"),true,before,after));
        }
        Map<String,Object> result=Map.of("scope",novel.path("scope").asText(),"labelsChanged",false,"providerCalls",0,"groups",groups);
        Path out=Path.of(System.getProperty("ranking.output","target/book-facet-ranking-results.json"));
        Files.createDirectories(out.toAbsolutePath().getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),result);
        for(var group:groups) {
            System.out.println("FACET_RANKING "+mapper.writeValueAsString(new LinkedHashMap<>(group){{remove("cases");}}));
            assertThat((List<?>)group.get("regressions")).as((String)group.get("name")).isEmpty();
        }
        // Compare rankers under the same current eligibility rules. The historical
        // 27/30 baseline lives in the dated report; stronger filters change it.
        int baseline=((Number)groups.get(0).get("beforeHit1")).intValue();
        assertThat(((Number)groups.get(0).get("afterHit1")).intValue()).isGreaterThan(baseline);
    }
}
