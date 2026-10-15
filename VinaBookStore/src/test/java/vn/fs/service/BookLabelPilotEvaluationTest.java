package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Frozen real catalog snapshot; no provider calls, runtime catalog or account data. */
class BookLabelPilotEvaluationTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ChatIntentService language=new ChatIntentService();
    final JsonNode snapshot=resource("catalog-before.json"),manifest=resource("labels.json"),cases=resource("queries.json");
    JsonNode resource(String file) {
        try(var in=getClass().getResourceAsStream("/book-label-pilot/"+file)) { return mapper.readTree(in); }
        catch(Exception ex) {throw new IllegalStateException(ex);}
    }
    record Variant(List<Product> cohort,List<Product> all,ChatRetrievalService data,BookKnowledgeService knowledge) implements AutoCloseable {
        public void close(){knowledge.stopIndexWorker();}
    }
    Variant variant(boolean after) { return variant(after,false); }
    Variant variant(boolean after,boolean facetRanking) { return variant(snapshot,manifest,after,facetRanking); }
    Variant variant(JsonNode snapshot,JsonNode manifest,boolean after,boolean facetRanking) {
        Set<Long> pilot=new LinkedHashSet<>();Map<Long,List<String>> added=new HashMap<>();
        Map<String,String> dimensions=new LinkedHashMap<>();manifest.path("dimensions").fields().forEachRemaining(e->dimensions.put(e.getKey(),e.getValue().asText()));
        for(JsonNode book:manifest.path("books")) {
            long id=book.path("productId").asLong();pilot.add(id);List<String> names=new ArrayList<>();
            book.path("labels").forEach(label->names.add(dimensions.get(label.path("dimension").asText())+": "+label.path("value").asText()));added.put(id,names);
        }
        List<Product> all=new ArrayList<>();Map<Long,BookProfile> profiles=new LinkedHashMap<>();
        for(JsonNode row:snapshot.path("books")) {
            Product p=new Product();p.setProductId(row.path("productId").asLong());p.setName(row.path("name").asText());p.setAuthor(row.path("author").asText());
            p.setDescription(row.path("description").asText());p.setPrice(row.path("price").asDouble());p.setDiscount(row.path("discount").asInt());
            p.setQuantity(row.path("quantity").asInt());p.setSold(row.path("sold").asInt());p.setStatus(row.path("status").asInt()==1);
            Category category=new Category();category.setCategoryName(row.path("category").asText());p.setCategory(category);all.add(p);
            JsonNode facts=row.path("profile");BookProfile profile=new BookProfile();profile.setProductId(p.getProductId());profile.setApproved(facts.path("approved").asInt()==1);
            profile.setSummary(facts.path("summary").asText(""));profile.setAudience(facts.path("audience").asText(""));profile.setReadingLevel(facts.path("readingLevel").asText(""));
            profile.setReadingStyle(facts.path("readingStyle").asText(""));profile.setLearningGoal(facts.path("learningGoal").asText(""));profile.setLanguage(facts.path("language").asText(""));
            String search=facts.path("searchText").asText("");if(after && added.containsKey(p.getProductId()))search+=" "+added.get(p.getProductId()).stream().map(BookKnowledgeService::topicSearchTerm).collect(Collectors.joining(" "));
            profile.setSearchText(search);profiles.put(p.getProductId(),profile);
        }
        BookProfileRepository repository=mock(BookProfileRepository.class);
        when(repository.findById(anyLong())).thenAnswer(inv->Optional.ofNullable(profiles.get(inv.getArgument(0))));
        when(repository.findAllById(any())).thenAnswer(inv->StreamSupport.stream(((Iterable<Long>)inv.getArgument(0)).spliterator(),false).map(profiles::get).filter(Objects::nonNull).collect(Collectors.toList()));
        BookTopicRepository topicRepository=mock(BookTopicRepository.class);
        BookTopicLinkRepository linkRepository=mock(BookTopicLinkRepository.class);
        Map<Long,BookTopic> topicRows=new LinkedHashMap<>();List<BookTopicLink> linkRows=new ArrayList<>();
        long topicSequence=1;
        for(JsonNode row:snapshot.path("books")) {
            long pid=row.path("productId").asLong();List<String> names=new ArrayList<>();
            row.path("topics").forEach(t->names.add(t.path("name").asText()));
            if(after)names.addAll(added.getOrDefault(pid,List.of()));
            for(String name:names) {
                BookTopic topic=new BookTopic();topic.setId(topicSequence++);topic.setName(name);topicRows.put(topic.getId(),topic);
                BookTopicLink link=new BookTopicLink();link.setProductId(pid);link.setTopicId(topic.getId());linkRows.add(link);
            }
        }
        when(linkRepository.findByProductIdIn(anyCollection())).thenAnswer(inv->{Collection<Long> ids=inv.getArgument(0);return linkRows.stream().filter(l->ids.contains(l.getProductId())).toList();});
        when(topicRepository.findAllById(any())).thenAnswer(inv->StreamSupport.stream(((Iterable<Long>)inv.getArgument(0)).spliterator(),false).map(topicRows::get).filter(Objects::nonNull).toList());
        BookEmbeddingGateway embeddings=mock(BookEmbeddingGateway.class);when(embeddings.available()).thenReturn(false);
        BookKnowledgeService knowledge=spy(new BookKnowledgeService(mock(ProductRepository.class),repository,topicRepository,linkRepository,
            mock(BookRelationRepository.class),embeddings,mock(AiGateway.class),mapper,language));
        if(!facetRanking)doAnswer(inv->legacyRank(inv.getArgument(0),inv.getArgument(1),profiles)).when(knowledge).rankByMeaning(anyString(),anyList());
        ChatRetrievalService data=new ChatRetrievalService(language);data.setKnowledge(knowledge);
        return new Variant(all.stream().filter(p->pilot.contains(p.getProductId())).toList(),all,data,knowledge);
    }
    /** Frozen pre-change lexical formula; both variants disable embeddings. */
    List<Product> legacyRank(String query,List<Product> candidates,Map<Long,BookProfile> profiles) {
        if(query==null || query.isBlank() || candidates.size()<2)return candidates;
        String q=language.normalize(query);
        return candidates.stream().sorted(Comparator.comparingDouble((Product p)->{
            BookProfile profile=profiles.get(p.getProductId());
            String content=language.normalize(profile==null?p.getName()+" "+p.getAuthor()+" "+p.getDescription():profile.getSearchText());
            double score=0;
            for(String token:q.split("\\s+"))if(token.length()>=3 && !Set.of("sach","cuon","quyen","cho","toi","minh","tim","ve").contains(token)
                && ChatCatalogQuery.word(content,token))score+=10;
            return score;
        }).reversed().thenComparing(Product::getSold,Comparator.reverseOrder()).thenComparing(Product::getProductId)).toList();
    }
    List<Long> search(Variant v,String query,Long ceiling) {
        return new ChatCatalogQuery(language,v.data()).search(query,new ChatSearchCriteriaDto(null,ceiling,"","","",List.of()),v.cohort()).stream().map(Product::getProductId).toList();
    }
    double reciprocalRank(List<Long> found,Set<Long> relevant) {
        for(int i=0;i<found.size();i++)if(relevant.contains(found.get(i)))return 1.0/(i+1);return 0;
    }
    @Test void compareFrozenQueriesWithOnlyLabelsChanged() throws Exception {
        List<Map<String,Object>> results=new ArrayList<>();
        try(Variant before=variant(false);Variant after=variant(true)) {
            assertThat(before.cohort()).hasSize(20);assertThat(before.all()).hasSize(56);
            for(JsonNode c:cases.path("cases")) {
                Set<Long> relevant=new LinkedHashSet<>();c.path("relevantIds").forEach(n->relevant.add(n.asLong()));
                List<Long> old=search(before,c.path("query").asText(),null),now=search(after,c.path("query").asText(),null);
                Map<String,Object> result=new LinkedHashMap<>();result.put("id",c.path("id").asText());result.put("group",c.path("group").asText());result.put("query",c.path("query").asText());
                result.put("relevantIds",relevant);result.put("beforeIds",old);result.put("afterIds",now);
                result.put("beforeHit1",!old.isEmpty()&&relevant.contains(old.get(0)));result.put("afterHit1",!now.isEmpty()&&relevant.contains(now.get(0)));
                result.put("beforeHit3",old.stream().limit(3).anyMatch(relevant::contains));result.put("afterHit3",now.stream().limit(3).anyMatch(relevant::contains));
                result.put("beforeRR",reciprocalRank(old,relevant));result.put("afterRR",reciprocalRank(now,relevant));results.add(result);
            }
        }
        Map<String,Object> output=new LinkedHashMap<>();output.put("scope",cases.path("scope").asText());output.put("providerCalls",0);output.put("queries",results);
        Map<String,Object> groups=new LinkedHashMap<>();
        for(String group:List.of("canonical","synonym","all")) {
            List<Map<String,Object>> rows=results.stream().filter(r->group.equals("all")||group.equals(r.get("group"))).toList();
            Map<String,Object> counts=new LinkedHashMap<>();counts.put("count",rows.size());
            for(String key:List.of("beforeHit1","afterHit1","beforeHit3","afterHit3"))counts.put(key,rows.stream().filter(r->Boolean.TRUE.equals(r.get(key))).count());
            for(String key:List.of("beforeRR","afterRR"))counts.put(key,rows.stream().mapToDouble(r->((Number)r.get(key)).doubleValue()).average().orElse(0));
            counts.put("hit1Regressions",rows.stream().filter(r->Boolean.TRUE.equals(r.get("beforeHit1"))&&!Boolean.TRUE.equals(r.get("afterHit1"))).map(r->r.get("id")).toList());groups.put(group,counts);
        }
        output.put("summary",groups);
        Path path=Path.of(System.getProperty("pilot.output","target/book-label-pilot-results.json"));Files.createDirectories(path.toAbsolutePath().getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),output);
        System.out.println("LABEL_PILOT_RESULTS "+mapper.writeValueAsString(groups));
    }
    @Test void labelsPreserveCriticalExclusionsAndPriceFilter() {
        try(Variant v=variant(true)) {
            assertThat(search(v,"học kỹ năng giao tiếp",null)).contains(57L).doesNotContain(32L,38L,50L,7L);
            assertThat(search(v,"quản lý chi tiêu cá nhân",null)).doesNotContain(43L);
            assertThat(search(v,"truyện tranh trinh thám",null)).contains(48L).doesNotContain(26L);
            assertThat(search(v,"lập trình Python",null)).isEmpty();
            assertThat(search(v,"Spring Boot",null)).isEmpty();
            for(Long id:search(v,"truyện trinh thám hoặc phiêu lưu",99999L))
                assertThat(v.data().effectivePrice(v.cohort().stream().filter(b->b.getProductId().equals(id)).findFirst().orElseThrow())).isLessThan(100000L);
            Product godfather=v.cohort().stream().filter(p->p.getProductId()==27L).findFirst().orElseThrow();
            assertThat(new ChatReadingFit(language).conflicts(Map.of("readingNeeds",List.of("gentle")),v.data().approvedReadingFacts(godfather))).isTrue();
        }
    }
    @Test void aliasesDoNotBecomeEvidenceForUnknownBookFactsOrChangeUnlabelledProfiles() {
        try(Variant before=variant(false);Variant after=variant(true)) {
            Product java=after.cohort().stream().filter(p->p.getProductId()==22L).findFirst().orElseThrow();
            Map<String,String> facts=after.data().approvedReadingFacts(java);
            assertThat(facts.getOrDefault("readingLevel","")).isEmpty();
            assertThat(ChatBookFacts.studyFeatures(Set.of("exercises","answerKey"),facts,language)).hasSize(2).allSatisfy(s->assertThat(s).contains("chưa có thông tin đã xác nhận"));
            Set<Long> ids=after.cohort().stream().map(Product::getProductId).collect(Collectors.toSet());
            for(Product product:after.all()) {
                Product old=before.all().stream().filter(p->p.getProductId().equals(product.getProductId())).findFirst().orElseThrow();
                assertThat(after.data().approvedReadingFacts(product)).isEqualTo(before.data().approvedReadingFacts(old));
                assertThat(after.data().effectivePrice(product)).isEqualTo(before.data().effectivePrice(old));
                if(!ids.contains(product.getProductId()))assertThat(after.data().searchableText(product)).isEqualTo(before.data().searchableText(old));
            }
        }
    }
}
