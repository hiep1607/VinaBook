package vn.fs.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.*;
import javax.transaction.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Editorial book knowledge and typed links. Publication swaps the complete search record into use. */
@Service
public class BookKnowledgeService {
    private static final Set<String> RELATION_TYPES = Set.of("SAME_SERIES", "NEXT_VOLUME", "READ_BEFORE");
    private final ProductRepository products;
    private final BookProfileRepository profiles;
    private final BookTopicRepository topics;
    private final BookTopicLinkRepository links;
    private final BookRelationRepository relations;
    private final BookEmbeddingGateway embeddings;
    private final AiGateway ai;
    private final ObjectMapper mapper;
    private final ChatIntentService language;
    private final ThreadLocal<Map<Long,BookProfile>> chatReadProfiles=new ThreadLocal<>();
    private final ThreadLocal<Map<Long,List<String>>> chatReadTopics=new ThreadLocal<>();
    private final ExecutorService indexWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64),job->{Thread thread=new Thread(job,"book-index");thread.setDaemon(true);return thread;},
        new ThreadPoolExecutor.DiscardPolicy());

    public BookKnowledgeService(ProductRepository products, BookProfileRepository profiles,
            BookTopicRepository topics, BookTopicLinkRepository links, BookRelationRepository relations,
            BookEmbeddingGateway embeddings, AiGateway ai, ObjectMapper mapper, ChatIntentService language) {
        this.products=products; this.profiles=profiles; this.topics=topics; this.links=links;
        this.relations=relations; this.embeddings=embeddings; this.ai=ai; this.mapper=mapper; this.language=language;
    }

    public ProfileInput get(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BookProfile profile = profiles.findById(id).orElse(null);
        ProfileInput view = new ProfileInput();
        view.setProductId(id);
        view.setPublished(Boolean.TRUE.equals(product.getStatus()));
        view.setSemanticReady(profile != null && profile.getEmbeddingJson() != null);
        view.setSummary(profile == null ? product.getDescription() : profile.getSummary());
        if (profile != null) {
            view.setAudience(profile.getAudience()); view.setReadingLevel(profile.getReadingLevel());
            view.setReadingStyle(profile.getReadingStyle()); view.setLearningGoal(profile.getLearningGoal());
            view.setLanguage(profile.getLanguage()); view.setPublisher(profile.getPublisher());
            view.setPublicationYear(profile.getPublicationYear()); view.setPageCount(profile.getPageCount());
            view.setIsbn(profile.getIsbn()); view.setSourceNote(profile.getSourceNote());
            view.setTopics(links.findByProductId(id).stream().map(link -> topics.findById(link.getTopicId())
                    .map(BookTopic::getName).orElse(null)).filter(Objects::nonNull).collect(Collectors.toList()));
            view.setRelations(relations.findBySourceProductId(id).stream().map(edge -> {
                RelationInput r = new RelationInput(); r.setTargetProductId(edge.getTargetProductId());
                r.setType(edge.getRelationType()); r.setEvidence(edge.getEvidence()); return r;
            }).collect(Collectors.toList()));
        } else if (product.getCategory() != null) view.setTopics(List.of(product.getCategory().getCategoryName()));
        return view;
    }

    /** The returned text is only a suggestion. An admin must review and publish it. */
    public ProfileInput suggest(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ProfileInput suggestion = get(id);
        if (!ai.isConfigured()) return suggestion;
        String prompt = "Bạn tạo BẢN NHÁP hồ sơ sách chỉ từ tên, tác giả, danh mục và mô tả do cửa hàng cung cấp. "
                + "Trả JSON thuần gồm summary,audience,readingLevel,readingStyle,learningGoal,topics (mảng chuỗi). "
                + "Không suy đoán ISBN, nhà xuất bản, số trang, năm xuất bản. Không đưa dữ kiện không có căn cứ. "
                + "Mỗi chủ đề phải được hỗ trợ bởi mô tả, tên hoặc danh mục.";
        try {
            String raw = ai.complete(prompt, List.of(), mapper.writeValueAsString(Map.of(
                    "name", safe(product.getName()), "author", safe(product.getAuthor()),
                    "category", product.getCategory()==null?"":safe(product.getCategory().getCategoryName()),
                    "description", safe(product.getDescription()))));
            JsonNode json = mapper.readTree(raw.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
            if (json.isObject()) {
                suggestion.setSummary(text(json,"summary",suggestion.getSummary()));
                suggestion.setAudience(text(json,"audience",null));
                suggestion.setReadingLevel(text(json,"readingLevel",null));
                suggestion.setReadingStyle(text(json,"readingStyle",null));
                suggestion.setLearningGoal(text(json,"learningGoal",null));
                if (json.path("topics").isArray()) {
                    List<String> names = new ArrayList<>();
                    for (JsonNode item : json.path("topics")) if (item.isTextual()) names.add(item.asText());
                    if (!names.isEmpty()) suggestion.setTopics(names);
                }
            }
            return suggestion;
        } catch (Exception ex) {
            return suggestion;
        }
    }

    /** Bounded admin task for old catalog rows created before semantic indexing existed. */
    public Map<String,Object> reindexExisting() {
        long remaining=profiles.countByApprovedTrueAndEmbeddingJsonIsNull();
        if (!embeddings.available()) return Map.of("configured",false,"indexed",0,"remaining",remaining);
        int indexed=0;Long failed=null;
        List<BookProfile> pending=new ArrayList<>();List<BookEmbeddingGateway.Document> documents=new ArrayList<>();
        for (BookProfile profile:profiles.findByApprovedTrueAndEmbeddingJsonIsNull(PageRequest.of(0,10))) {
            Product product=products.findById(profile.getProductId()).orElse(null);
            if (product == null || !Boolean.TRUE.equals(product.getStatus()) || profile.getSearchText()==null || profile.getSearchText().isBlank()) continue;
            pending.add(profile);documents.add(new BookEmbeddingGateway.Document(product.getName(),profile.getSearchText()));
        }
        if(!pending.isEmpty())try {
            List<List<Double>> vectors=embeddings.documents(documents);
            if(vectors.size()!=pending.size())throw new IllegalStateException("Incomplete batch");
            for(int i=0;i<pending.size();i++) {
                BookProfile profile=pending.get(i);
                indexed+=profiles.storeEmbeddingIfCurrent(profile.getProductId(),documents.get(i).text(),mapper.writeValueAsString(vectors.get(i)));
            }
        } catch(Exception ex){failed=pending.get(0).getProductId();}
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("configured",true); result.put("indexed",indexed);
        result.put("remaining",profiles.countByApprovedTrueAndEmbeddingJsonIsNull());
        if (failed!=null) result.put("failedProductId",failed);
        return result;
    }

    @Transactional
    public ProfileInput publish(Long id, ProfileInput input) {
        Product product = products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<String> topicNames = normalizeTopics(input.getTopics());
        String summary = bounded(input.getSummary(), 4000);
        if (summary.isBlank() || topicNames.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần tóm tắt và ít nhất một chủ đề đã duyệt trước khi xuất bản.");
        List<RelationInput> requestedRelations = input.getRelations()==null?List.of():input.getRelations();
        for (RelationInput relation : requestedRelations) {
            if (relation.getTargetProductId() == null || id.equals(relation.getTargetProductId())
                    || !products.existsById(relation.getTargetProductId()) || !RELATION_TYPES.contains(relation.getType()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quan hệ giữa các sách không hợp lệ.");
        }
        String searchText = composeText(product,summary,input.getAudience(),input.getReadingLevel(),
                input.getReadingStyle(),input.getLearningGoal(),topicNames);
        BookProfile profile = profiles.findById(id).orElseGet(BookProfile::new);
        profile.setProductId(id); profile.setSummary(summary);
        profile.setAudience(bounded(input.getAudience(),160));
        profile.setReadingLevel(bounded(input.getReadingLevel(),80));
        profile.setReadingStyle(bounded(input.getReadingStyle(),160));
        profile.setLearningGoal(bounded(input.getLearningGoal(),200));
        profile.setLanguage(bounded(input.getLanguage(),80));
        profile.setPublisher(bounded(input.getPublisher(),160));
        profile.setPublicationYear(input.getPublicationYear());
        profile.setPageCount(input.getPageCount());
        profile.setIsbn(bounded(input.getIsbn(),40));
        profile.setSourceNote(bounded(input.getSourceNote(),1000));
        if (profile.getPublicationYear()!=null && (profile.getPublicationYear()<1450 || profile.getPublicationYear()>2100))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Năm xuất bản không hợp lệ.");
        if (profile.getPageCount()!=null && profile.getPageCount()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Số trang không hợp lệ.");
        profile.setSearchText(searchText); profile.setEmbeddingJson(null);
        profile.setApproved(true); profile.setUpdatedAt(LocalDateTime.now());
        profiles.save(profile);
        links.deleteByProductId(id);
        links.flush();
        for (String name : topicNames) {
            BookTopic topic = topics.findByNameIgnoreCase(name).orElseGet(() -> {
                BookTopic created = new BookTopic(); created.setName(name); return topics.save(created);
            });
            BookTopicLink link = new BookTopicLink(); link.setProductId(id); link.setTopicId(topic.getId());
            link.setEvidence("Hồ sơ sách được quản trị viên duyệt"); links.save(link);
        }
        relations.deleteBySourceProductId(id);
        relations.flush();
        for (RelationInput relation : requestedRelations) {
            BookRelation edge = new BookRelation(); edge.setSourceProductId(id);
            edge.setTargetProductId(relation.getTargetProductId()); edge.setRelationType(relation.getType());
            edge.setEvidence(bounded(relation.getEvidence(),250)); relations.save(edge);
        }
        product.setStatus(true); products.save(product);
        queueIndexAfterCommit(id);
        return get(id);
    }

    /** Rebuild the published search document when editorial product fields change. */
    @Transactional
    public void refreshProduct(Product product) {
        BookProfile profile = profiles.findById(product.getProductId()).orElse(null);
        if (profile == null || !profile.isApproved()) return;
        List<String> topicNames = links.findByProductId(product.getProductId()).stream()
                .map(link -> topics.findById(link.getTopicId()).map(BookTopic::getName).orElse(""))
                .filter(s -> !s.isBlank()).collect(Collectors.toList());
        String updated = composeText(product,profile.getSummary(),profile.getAudience(),
                profile.getReadingLevel(),profile.getReadingStyle(),profile.getLearningGoal(),topicNames);
        if (updated.equals(profile.getSearchText())) return;
        profile.setEmbeddingJson(null);
        profile.setSearchText(updated); profile.setUpdatedAt(LocalDateTime.now()); profiles.save(profile);
        queueIndexAfterCommit(product.getProductId());
    }

    private void queueIndexAfterCommit(Long id) {
        // Without a committed transaction (unit tests) the periodic worker picks up the pending row.
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())return;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit(){indexWorker.execute(()->indexProfile(id));}
            });
    }

    boolean indexProfile(Long id) {
        if(!embeddings.available())return false;
        BookProfile profile=profiles.findById(id).filter(BookProfile::isApproved).orElse(null);
        Product product=products.findById(id).orElse(null);
        if(profile==null || product==null || !Boolean.TRUE.equals(product.getStatus())
            || profile.getSearchText()==null || profile.getSearchText().isBlank())return false;
        if(profile.getEmbeddingJson()!=null)return true;
        String currentText=profile.getSearchText();
        try {
            String vector=mapper.writeValueAsString(embeddings.document(product.getName(),currentText));
            // An edit or invalidation while the HTTP request is running must win over the old index.
            return profiles.storeEmbeddingIfCurrent(id,currentText,vector)==1;
        } catch(Exception ex){return false;}
    }

    @org.springframework.scheduling.annotation.Scheduled(initialDelay=15000,fixedDelay=60000)
    public void retryPendingIndexes(){
        Map<String,Object> result=reindexExisting();
        if(((Number)result.get("remaining")).longValue()>0 || ((Number)result.get("indexed")).intValue()>0)
            org.slf4j.LoggerFactory.getLogger(BookKnowledgeService.class).info("book_index indexed={} remaining={} failed={}",result.get("indexed"),result.get("remaining"),result.containsKey("failedProductId"));
    }

    @javax.annotation.PreDestroy
    public void stopIndexWorker(){indexWorker.shutdownNow();}

    @Transactional
    public void invalidateProfile(Long productId) {
        profiles.findById(productId).ifPresent(profile -> {
            profile.setApproved(false);
            profile.setEmbeddingJson(null);
            profile.setUpdatedAt(LocalDateTime.now());
            profiles.save(profile);
        });
    }

    private String composeText(Product product,String summary,String audience,String level,String style,
            String goal,List<String> topicNames) {
        return String.join(" ", List.of(safe(product.getName()),safe(product.getAuthor()),
                product.getCategory()==null?"":safe(product.getCategory().getCategoryName()),
                safe(product.getDescription()),safe(summary),bounded(audience,160),bounded(level,80),
                bounded(style,160),bounded(goal,200),topicNames.stream().map(BookKnowledgeService::topicSearchTerm).collect(Collectors.joining(" ")))).trim();
    }

    /** Editorial facet headings are UI labels, not subject words in retrieval. */
    static String topicSearchTerm(String topic) {
        return topic==null?"":topic.replaceFirst("(?iu)^(?:Thể loại|Chủ đề|Mục đích|Dạng nội dung|Độc giả|Cách trình bày|Mức đọc):\\s*", "");
    }

    /** One ephemeral read scope per chat turn. The database remains the source;
     * no profile survives the turn or crosses threads/accounts. Price and stock
     * are never cached here. Missing/draft profiles still use the old fallback. */
    public void beginChatRead(List<Product> catalog) {
        chatReadProfiles.remove();chatReadTopics.remove();
        Map<Long,BookProfile> snapshot=new HashMap<>();
        catalog.forEach(product->snapshot.put(product.getProductId(),null));
        profiles.findAllById(snapshot.keySet()).forEach(profile->snapshot.put(profile.getProductId(),profile));
        chatReadProfiles.set(snapshot);
    }
    public void endChatRead() { chatReadProfiles.remove();chatReadTopics.remove(); }
    private Optional<BookProfile> readingProfile(Long id) {
        Map<Long,BookProfile> snapshot=chatReadProfiles.get();
        return (snapshot!=null && snapshot.containsKey(id)?Optional.ofNullable(snapshot.get(id)):profiles.findById(id))
            .filter(BookProfile::isApproved);
    }

    public String searchableText(Product product) {
        return readingProfile(product.getProductId())
                .map(BookProfile::getSearchText).filter(s -> s != null && !s.isBlank())
                .orElseGet(() -> safe(product.getName())+" "+safe(product.getAuthor())+" "+safe(product.getDescription()));
    }

    public String approvedDescription(Product product) {
        return readingProfile(product.getProductId())
                .map(p -> String.join(" ", safe(p.getSummary()),safe(p.getAudience()),safe(p.getReadingLevel()),
                        safe(p.getReadingStyle()),safe(p.getLearningGoal())).trim())
                .filter(s -> !s.isBlank()).orElseGet(() -> safe(product.getDescription()));
    }

    public Map<String,String> approvedReadingFacts(Product product) {
        BookProfile profile=readingProfile(product.getProductId()).orElse(null);
        Map<String,String> facts=new LinkedHashMap<>();
        facts.put("summary",profile==null?safe(product.getDescription()):safe(profile.getSummary()));
        if(profile!=null) {
            facts.put("audience",safe(profile.getAudience()));facts.put("readingLevel",safe(profile.getReadingLevel()));
            facts.put("readingStyle",safe(profile.getReadingStyle()));facts.put("learningGoal",safe(profile.getLearningGoal()));
            facts.put("language",safe(profile.getLanguage()));
        }
        facts.values().removeIf(String::isBlank);return facts;
    }

    /** Bibliographic facts are visible to chat only after an editor approves the profile. */
    public String approvedBibliography(Product product) {
        BookProfile profile = profiles.findById(product.getProductId())
                .filter(BookProfile::isApproved).orElse(null);
        if (profile == null) return "Chưa có thông tin thư mục đã duyệt cho cuốn này.";
        List<String> facts = new ArrayList<>();
        if (profile.getPublisher() != null && !profile.getPublisher().isBlank())
            facts.add("Nhà xuất bản: " + profile.getPublisher());
        if (profile.getPublicationYear() != null)
            facts.add("Năm xuất bản: " + profile.getPublicationYear());
        if (profile.getPageCount() != null)
            facts.add("Số trang: " + profile.getPageCount());
        if (profile.getIsbn() != null && !profile.getIsbn().isBlank())
            facts.add("ISBN: " + profile.getIsbn());
        return facts.isEmpty() ? "Chưa có thông tin thư mục đã duyệt cho cuốn này."
                  : String.join("; ", facts) + ".";
    }

    public String approvedBibliography(Product product, Set<String> requested) {
        BookProfile profile = readingProfile(product.getProductId()).orElse(null);
        if (profile == null) return ChatBookFacts.missingBibliography(requested);
        List<String> facts = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        for (String field : requested) {
            Object value = switch (field) {
                case "publisher" -> profile.getPublisher();
                case "publicationYear" -> profile.getPublicationYear();
                case "pageCount" -> profile.getPageCount();
                case "isbn" -> profile.getIsbn();
                default -> null;
            };
            String label = switch (field) {
                case "publisher" -> "Nhà xuất bản";
                case "publicationYear" -> "Năm xuất bản";
                case "pageCount" -> "Số trang";
                default -> "ISBN";
            };
            if (value == null || value.toString().isBlank()) missing.add(field);
            else facts.add(label + ": " + value);
        }
        if (!missing.isEmpty()) facts.add(ChatBookFacts.missingBibliography(missing));
        return String.join("; ", facts);
    }

    public List<Product> rankByMeaning(String query, List<Product> candidates) {
        return rankByMeaning(query,candidates,true);
    }
    List<Product> rankByMeaning(String query,List<Product> candidates,boolean useEmbedding) {
        if (query == null || query.isBlank() || candidates.size() < 2) return candidates;
        Map<Long,BookProfile> known = profiles.findAllById(candidates.stream().map(Product::getProductId)
                .collect(Collectors.toList())).stream().filter(BookProfile::isApproved)
                .collect(Collectors.toMap(BookProfile::getProductId,p -> p));
        Map<Long,List<String>> facets=readingTopics(known.keySet());
        BookRelevanceScorer editorial=new BookRelevanceScorer(language);
        List<Double> vector = null;
        if (useEmbedding && embeddings.available() && known.values().stream().anyMatch(p -> p.getEmbeddingJson()!=null)) {
            try { vector = embeddings.query(query); } catch (Exception ignored) { /* lexical/graph search remains available */ }
        }
        final List<Double> queryVector = vector;
        List<Product> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((Product p) -> score(query,p,known.get(p.getProductId()),queryVector)
                +editorial.score(query,p,known.get(p.getProductId()),facets.getOrDefault(p.getProductId(),List.of())))
                .reversed().thenComparing(Product::getSold,Comparator.reverseOrder()).thenComparing(Product::getProductId));
        return ranked;
    }

    /** Two batch reads, restricted to approved candidate profiles; no per-book topic queries. */
    private Map<Long,List<String>> rankingTopics(Set<Long> approvedIds) {
        if(approvedIds.isEmpty())return Map.of();
        List<BookTopicLink> matched=links.findByProductIdIn(approvedIds);
        Set<Long> ids=matched.stream().map(BookTopicLink::getTopicId).collect(Collectors.toSet());
        if(ids.isEmpty())return Map.of();
        Map<Long,String> names=topics.findAllById(ids).stream()
            .collect(Collectors.toMap(BookTopic::getId,BookTopic::getName));
        Map<Long,List<String>> result=new HashMap<>();
        for(BookTopicLink link:matched) {
            String name=names.get(link.getTopicId());
            if(approvedIds.contains(link.getProductId()) && name!=null)
                result.computeIfAbsent(link.getProductId(),id->new ArrayList<>()).add(name);
        }
        return result;
    }

    private Map<Long,List<String>> readingTopics(Set<Long> ids) {
        Map<Long,BookProfile> profiles=chatReadProfiles.get();
        if(profiles==null)return rankingTopics(ids);
        Map<Long,List<String>> all=chatReadTopics.get();
        if(all==null) {
            Set<Long> approved=profiles.entrySet().stream().filter(e->e.getValue()!=null && e.getValue().isApproved())
                .map(Map.Entry::getKey).collect(Collectors.toSet());
            all=rankingTopics(approved);chatReadTopics.set(all);
        }
        Map<Long,List<String>> result=new HashMap<>();
        for(Long id:ids)result.put(id,all.getOrDefault(id,List.of()));
        return result;
    }
    public Map<Long,List<String>> topicLabels(List<Product> catalog) {
        Set<Long> approved=catalog.stream().filter(p->Boolean.TRUE.equals(p.getStatus()) && p.getQuantity()>0)
            .filter(p->readingProfile(p.getProductId()).isPresent()).map(Product::getProductId).collect(Collectors.toSet());
        return readingTopics(approved);
    }

    /** Planning hints from approved labels, never a promise of a constraint match. */
    public List<String> topicSuggestionNames(List<Product> catalog) {
        Set<Long> approved=catalog.stream().filter(p->Boolean.TRUE.equals(p.getStatus()) && p.getQuantity()>0)
            .filter(p->readingProfile(p.getProductId()).isPresent()).map(Product::getProductId).collect(Collectors.toSet());
        Set<String> shelves=catalog.stream().filter(p->p.getCategory()!=null)
            .map(p->language.normalize(p.getCategory().getCategoryName())).collect(Collectors.toSet());
        Map<String,Set<Long>> books=new TreeMap<>();
        readingTopics(approved).forEach((id,names)->names.forEach(raw->{
            if(raw.matches("(?iu)^(?:Độc giả|Cách trình bày|Mức đọc|Dạng nội dung|Mục đích):.*"))return;
            String name=topicSearchTerm(raw).split(" / ",2)[0].strip();
            if(name.length()>=3 && name.length()<=64 && name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ,/–-]*"))
                books.computeIfAbsent(name,key->new HashSet<>()).add(id);
        }));
        return books.entrySet().stream().filter(e->e.getValue().size()>=2)
            .sorted(Comparator.<Map.Entry<String,Set<Long>>>comparingInt(e->shelves.contains(language.normalize(e.getKey()))?1:0)
                .thenComparing(Comparator.<Map.Entry<String,Set<Long>>>comparingInt(e->e.getValue().size()).reversed()).thenComparing(Map.Entry::getKey))
            .limit(60).map(Map.Entry::getKey).toList();
    }

    /** Vector matches broaden recall only as explicitly labelled nearby choices. */
    public Map<Long,String> semanticEvidence(String query,List<Product> candidates) {
        return semanticRecall(query,candidates).evidence();
    }
    public record SemanticRecall(boolean evaluated,Map<Long,String> evidence) {}
    public SemanticRecall semanticRecall(String query,List<Product> candidates) {
        if(query==null || query.isBlank() || !embeddings.available())return new SemanticRecall(false,Map.of());
        List<BookProfile> known=profiles.findAllById(candidates.stream().map(Product::getProductId).toList()).stream()
            .filter(p->p.isApproved() && p.getEmbeddingJson()!=null && p.getSummary()!=null && !p.getSummary().isBlank()).toList();
        if(known.isEmpty())return new SemanticRecall(false,Map.of());
        try {
            List<Double> vector=embeddings.query(query);
            Map<Long,Double> scores=new LinkedHashMap<>();
            for(BookProfile profile:known) {
                double similarity=cosine(vector,profile.getEmbeddingJson());
                if(similarity>=0.66)scores.put(profile.getProductId(),similarity);
            }
            double best=scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            scores.values().removeIf(value->value<best-0.035);
            Map<Long,String> result=new LinkedHashMap<>();
            scores.entrySet().stream().sorted(Map.Entry.<Long,Double>comparingByValue().reversed()).limit(20).forEach(item->{
                BookProfile profile=known.stream().filter(p->p.getProductId().equals(item.getKey())).findFirst().orElseThrow();
                result.put(item.getKey(),"Gợi ý theo nội dung gần với cách bạn diễn đạt. Hồ sơ đã duyệt: "+ChatDialogueInterpreter.clean(profile.getSummary(),180));
            });
            return new SemanticRecall(true,result);
        } catch(Exception ex){return new SemanticRecall(false,Map.of());}
    }

    private double cosine(List<Double> vector,String json) {
        try {
            JsonNode values=mapper.readTree(json);
            if(!values.isArray() || values.size()!=vector.size())return -1;
            double dot=0,left=0,right=0;
            for(int i=0;i<vector.size();i++) {
                if(!values.get(i).isNumber() || !Double.isFinite(values.get(i).asDouble()))return -1;
                double a=vector.get(i),b=values.get(i).asDouble();dot+=a*b;left+=a*a;right+=b*b;
            }
            return left>0 && right>0?dot/Math.sqrt(left*right):-1;
        } catch(Exception ex){return -1;}
    }

    private double score(String query, Product product, BookProfile profile, List<Double> vector) {
        String q=language.normalize(query);
        String content=language.normalize(profile==null?searchableText(product):profile.getSearchText());
        double result=0;
        for (String token : q.split("\\s+")) if(token.length()>=3 && !Set.of("sach","cuon","quyen","cho","toi","minh","tim","ve").contains(token)
                && ChatCatalogQuery.word(content,token)) result+=10;
        if (vector!=null && profile!=null && profile.getEmbeddingJson()!=null) {
            try {
                JsonNode values=mapper.readTree(profile.getEmbeddingJson());
                if(values.size()==vector.size()) {
                    double dot=0,left=0,right=0;
                    for(int i=0;i<vector.size();i++) { double a=vector.get(i),b=values.get(i).asDouble(); dot+=a*b;left+=a*a;right+=b*b; }
                    if(left>0 && right>0) result+=100*dot/Math.sqrt(left*right);
                }
            } catch (Exception ignored) { /* malformed old vector gets no semantic score */ }
        }
        return result;
    }

    public List<Product> related(Long sourceId) {
        LinkedHashSet<Long> ids = relations.findBySourceProductId(sourceId).stream()
                .map(BookRelation::getTargetProductId).collect(Collectors.toCollection(LinkedHashSet::new));
        relations.findByTargetProductId(sourceId).stream().map(BookRelation::getSourceProductId).forEach(ids::add);
        List<Long> topicIds = links.findByProductId(sourceId).stream().map(BookTopicLink::getTopicId).collect(Collectors.toList());
        if (!topicIds.isEmpty()) links.findByTopicIdIn(topicIds).stream().map(BookTopicLink::getProductId)
                .filter(id -> !sourceId.equals(id)).forEach(ids::add);
        return ids.stream().map(id -> products.findById(id).orElse(null)).filter(Objects::nonNull)
                .filter(p -> Boolean.TRUE.equals(p.getStatus())).limit(8).collect(Collectors.toList());
    }

    public Map<Long,String> relatedEvidence(List<Long> seeds) {
        Map<Long,String> found=new LinkedHashMap<>();
        for(Long id:seeds.stream().distinct().limit(8).collect(Collectors.toList())) {
            if(!profiles.findById(id).map(BookProfile::isApproved).orElse(false))continue;
            Product source=products.findById(id).orElse(null);
            if(source==null || !Boolean.TRUE.equals(source.getStatus()))continue;
            relations.findBySourceProductId(id).forEach(edge->found.put(edge.getTargetProductId(),"Có liên kết cùng bộ hoặc đọc tiếp trong dữ liệu cửa hàng."));
            relations.findByTargetProductId(id).forEach(edge->found.put(edge.getSourceProductId(),"Có liên kết cùng bộ hoặc đọc tiếp trong dữ liệu cửa hàng."));
            String broad=source.getCategory()==null?"":language.normalize(source.getCategory().getCategoryName());
            for(BookTopicLink link:links.findByProductId(id)) {
                BookTopic topic=topics.findById(link.getTopicId()).orElse(null);
                if(topic==null || language.normalize(topic.getName()).equals(broad))continue;
                for(BookTopicLink other:links.findByTopicIdIn(List.of(topic.getId())))
                    if(!id.equals(other.getProductId()) && profiles.findById(other.getProductId()).map(BookProfile::isApproved).orElse(false))
                        found.putIfAbsent(other.getProductId(),"Có chung chủ đề “"+topic.getName()+"” trong hồ sơ sách đã duyệt.");
            }
        }
        return found;
    }

    private List<String> normalizeTopics(List<String> values) {
        if(values==null) return List.of();
        Map<String,String> unique=new LinkedHashMap<>();
        values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank())
                .map(s -> bounded(s,120)).forEach(s -> unique.putIfAbsent(s.toLowerCase(Locale.ROOT),s));
        return unique.values().stream().limit(20).collect(Collectors.toList());
    }
    private String bounded(String value,int max){return value==null?"":value.trim().substring(0,Math.min(max,value.trim().length()));}
    private String safe(String value){return value==null?"":value;}
    private String text(JsonNode node,String key,String fallback){return node.path(key).isTextual()?bounded(node.path(key).asText(),4000):fallback;}

    @Data public static class ProfileInput {
        private Long productId;
        private boolean published;
        private boolean semanticReady;
        private String summary,audience,readingLevel,readingStyle,learningGoal,language,publisher,isbn,sourceNote;
        private Integer publicationYear,pageCount;
        private List<String> topics = new ArrayList<>();
        private List<RelationInput> relations = new ArrayList<>();
    }
    @Data public static class RelationInput {
        private Long targetProductId;
        private String type,evidence;
    }
}
