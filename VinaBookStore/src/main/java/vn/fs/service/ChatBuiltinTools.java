package vn.fs.service;

import static vn.fs.service.ChatTool.Parameter.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

/** Individual beans are discovered automatically; adding a capability does not change the orchestrator. */
@Configuration
public class ChatBuiltinTools {
    private final ProductRepository products;
    private final ChatRetrievalService data;
    private final ChatIntentService language;
    private final ChatOrderSupportService orders;
    private final ChatPolicyService policies;
    private OrderCancellationService cancellation;
    private BookKnowledgeService knowledge;
    private vn.fs.config.CurrentUserAccess access;
    private CustomerSupportService support;
    private vn.fs.repository.RateRepository rates;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSupport(CustomerSupportService value){support=value;}
    @org.springframework.beans.factory.annotation.Autowired
    public void setAccess(vn.fs.config.CurrentUserAccess value){access=value;}
    @org.springframework.beans.factory.annotation.Autowired
    public void setRates(vn.fs.repository.RateRepository value){rates=value;}
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setKnowledge(BookKnowledgeService value){knowledge=value;}
    @org.springframework.beans.factory.annotation.Autowired
    public void setCancellation(OrderCancellationService value){cancellation=value;}
    public ChatBuiltinTools(ProductRepository products, ChatRetrievalService data, ChatIntentService language,
            ChatOrderSupportService orders, ChatPolicyService policies) {
        this.products=products; this.data=data; this.language=language; this.orders=orders; this.policies=policies;
    }
    private ChatTool tool(String name, String description, Map<String,ChatTool.Parameter> parameters,
            BiFunction<JsonNode,ChatTool.Context,ChatTool.Result> handler) {
        return new ChatTool() {
            public String name() { return name; }
            public String description() { return description; }
            public Map<String,Parameter> parameters() { return parameters; }
            public Result execute(JsonNode arguments, Context context) { return handler.apply(arguments,context); }
        };
    }
    private Map<String,ChatTool.Parameter> referenceParameters() {
        Map<String,ChatTool.Parameter> p=new LinkedHashMap<>();
        p.put("productId", integer("ID xác thực trong context, không đoán",1,9007199254740991L));
        p.put("title", text("Tên sách cụ thể cần tìm, không thêm câu yêu cầu"));
        p.put("source", choice("Nguồn tham chiếu danh sách: kết quả hoặc giỏ thiết bị", "results","cart"));
        p.put("reference", choice("Tham chiếu danh sách đã hiển thị; other là cuốn còn lại trong cặp so sánh", "focus","first","second","third","last","selection","cheapest","most_expensive","other"));
        return p;
    }
    @Bean public ChatTool searchProducts() {
        Map<String,ChatTool.Parameter> p=new LinkedHashMap<>();
        p.put("query",text("Chủ đề/từ khóa sách, không chứa giá, rating hoặc số lượng"));
        p.put("title",text("Tên sách cụ thể cần khớp chính xác"));
        p.put("titleContains",text("Từ hoặc cụm từ độc lập có trong tên sách; AI không khớp chữ nằm trong Blockchain"));
        p.put("selection",choice("Chọn tập sách trước khi sắp xếp giá; bestsellers là top bán chạy có số bán > 0", "all","bestsellers"));
        p.put("scope",choice("new tìm mới; continue bổ sung bộ lọc; more giữ chủ đề và loại tất cả sách đã giới thiệu", "new","continue","more"));
        p.put("minPrice",integer("Giá tối thiểu VND; null xóa điều kiện",0,1000000000));
        p.put("maxPrice",integer("Giá tối đa VND; null xóa điều kiện",0,1000000000));
        p.put("minRating",number("Rating tối thiểu 0..5; null xóa điều kiện",0,5));
        p.put("category",text("Tên danh mục")); p.put("author",text("Tên tác giả"));
        p.put("sort",choice("Sắp xếp", "price-asc","price-desc","rating","best","new","sale"));
        p.put("limit",integer("Số cuốn khách yêu cầu; mặc định tối đa 3, số cụ thể theo khách",1,1000));
        p.put("match",choice("exact_only chỉ đúng yêu cầu; allow_related được bổ sung sách gần chủ đề khi thiếu", "exact_only","allow_related"));
        p.put("level",choice("Mức đọc đã nói; null xóa", "beginner","experienced"));
        p.put("totalBudget",integer("Tổng tiền cho cả nhóm sách; null xóa, khác maxPrice mỗi cuốn",1,1000000000));
        ChatReadingRequest.parameters(p);
        return tool("searchProducts","Tìm sách; lọc giá/rating, sách mới, bán chạy. Lọc toàn bộ trước khi giới hạn.",p,this::discover);
    }
    private ChatTool.Result discover(JsonNode a,ChatTool.Context c) {
            if(c.state().recommendationIntakeRequired && !ChatRecommendationIntake.ready(c.state())) {
                String q=language.normalize(c.request().getMessage());
                boolean specific=!ChatBookFacts.has(q,"goi y|tu van|nen doc|nen mua|tuong tu|cuon khac|sach khac")
                    && (!new ChatCatalogQuery(language,data).named(c.request().getMessage(),catalog()).isEmpty()
                        || !new ChatCatalogQuery(language,data).quoted(c.request().getMessage()).isBlank());
                if(!specific) {
                    c.state().recommendationPending=true;
                    return ChatTool.Result.clarify(ChatRecommendationIntake.question(c.state()));
                }
            }
            Map<String,Object> filters=new LinkedHashMap<>();
            ChatReadingRequest reading=new ChatReadingRequest(language);
            boolean acceptedRelated=reading.acceptsRelated(c.request().getMessage(),c.state().toolSearch);
            boolean another=acceptedRelated || language.wantsAnotherBook(c.request().getMessage())
                || "more".equals(a.path("scope").asText()) && !reading.contextPatch(c.request().getMessage());
            boolean continuing=acceptedRelated || "continue".equals(a.path("scope").asText()) || "more".equals(a.path("scope").asText())
                || ((language.moreInCurrentTopic(c.request().getMessage()) || language.refinesCurrentSearch(c.request().getMessage()))
                    && !c.state().toolSearch.isEmpty()) || reading.contextPatch(c.request().getMessage()) && !c.state().toolSearch.isEmpty();
            boolean explicitNewTopic=reading.explicitNewTopic(c.request().getMessage())
                || another && !language.moreInCurrentTopic(c.request().getMessage())
                && !reading.contextPatch(c.request().getMessage()) && !language.criteria(c.request().getMessage(),List.of()).getTopics().isEmpty();
            if(explicitNewTopic)continuing=false;
            if (continuing) filters.putAll(c.state().toolSearch);
            else {
                for(String field:List.of("excludedQuery","excludedLanguages"))
                    if(!explicitNewTopic && c.state().toolSearch.containsKey(field))filters.put(field,c.state().toolSearch.get(field));
                if(ChatBookFacts.has(language.normalize(c.request().getMessage()),"van duoi|van giu|giu.*gia"))
                    for(String field:List.of("minPrice","maxPrice"))
                        if(c.state().toolSearch.containsKey(field))filters.put(field,c.state().toolSearch.get(field));
                c.state().level="";c.state().recommendationBudget=null;
                c.state().discoverySeeds.clear();c.state().discoveryRequestedCount=null;
                c.state().discoveryEmptyRounds=0;
                c.state().comparisonProducts.clear();
                c.state().focus=null;
            }
            final boolean keepTopic=continuing && (another || reading.contextPatch(c.request().getMessage()) || reading.budgetPatch(c.request().getMessage()));
            a.fields().forEachRemaining(e->{ if (!Set.of("scope","level","totalBudget","purpose","recipient","preferredQuery","excludedQuery","readingNeed","readingNeedEvidence").contains(e.getKey())) {
                if(keepTopic && Set.of("query","title","titleContains","category","author").contains(e.getKey()))return;
                if(e.getValue().isNull()) filters.remove(e.getKey());
                else filters.put(e.getKey(),e.getValue().isNumber()?e.getValue().numberValue():e.getValue().asText());
            }});
            String message=c.request().getMessage();
            if(explicitNewTopic || !keepTopic && language.alternativeTopics(reading.subjectMessage(message)))
                filters.put("query",language.discoveryQuery(reading.subjectMessage(message)));
            String normalizedMessage=language.normalize(message);
            var grade=java.util.regex.Pattern.compile("\\blop\\s*(\\d{1,2})\\b").matcher(normalizedMessage);
            if(grade.find())filters.put("grade",Integer.valueOf(grade.group(1)));
            if(ChatBookFacts.has(normalizedMessage,"cho be|be [0-9]+ tuoi|tre em|thieu nhi")
                    && !ChatBookFacts.has(normalizedMessage,"khong.*(?:tre em|thieu nhi|cho be)"))filters.put("readerGroup","children");
            reading.apply(a,filters,message,continuing);
            ChatReadingFit fit=new ChatReadingFit(language);
            // A conversational genre is broader than a database shelf. Only keep a
            // hard category when the customer explicitly requests that category.
            String suppliedCategory=str(filters,"category");
            if(fit.active(filters) && !suppliedCategory.isBlank() && !normalizedMessage.contains(language.normalize(suppliedCategory))
                    && (!continuing || !suppliedCategory.equals(str(c.state().toolSearch,"category"))))filters.remove("category");
            if(ChatBookFacts.has(normalizedMessage,"bo gioi han gia|khong gioi han gia|bo bo loc gia")) {
                filters.remove("minPrice");filters.remove("maxPrice");
            }
            ChatExplicitFilters mandatory=new ChatExplicitFilters(language,data);
            String hardRequest=reading.hardMessage(message);
            if(!keepTopic && new ChatCatalogQuery(language,data).semanticConstraint(hardRequest))
                filters.put("requiredTopicQuery",language.discoveryQuery(reading.semanticMessage(message)));
            if(reading.clearsExclusions(message) && mandatory.hasRequiredLanguage(hardRequest))
                filters.put("requiredTopicQuery",language.discoveryQuery(reading.semanticMessage(message)));
            List<String> economicScopes=mandatory.requiredEconomicScopes(reading.semanticMessage(message));
            if(!economicScopes.isEmpty() && !keepTopic)filters.put("requiredEconomicScopes",economicScopes);
            if(mandatory.hasRequiredLanguage(reading.hardMessage(message)) && (!keepTopic || reading.clearsExclusions(message))) {
                filters.put("requiredLanguageQuery",reading.hardMessage(message));
                if(reading.clearsExclusions(message))filters.put("query",language.discoveryQuery(reading.hardMessage(message)));
            }
            Set<String> excludedLanguages=new LinkedHashSet<>();
            if(filters.get("excludedLanguages") instanceof Collection<?>)
                ((Collection<?>)filters.get("excludedLanguages")).forEach(value->excludedLanguages.add(value.toString()));
            excludedLanguages.addAll(mandatory.excludedLanguages(reading.activeMessage(message)));
            if(!excludedLanguages.isEmpty())filters.put("excludedLanguages",new ArrayList<>(excludedLanguages));
            String exclusions=excludedLanguages.stream().map(value->"không lấy "+value).collect(Collectors.joining(", "));
            boolean requestedBestsellers=ChatBookFacts.has(normalizedMessage,"ban chay|best seller|bestseller")
                    || (continuing && ("bestsellers".equals(str(c.state().toolSearch,"selection"))
                        || "best".equals(str(c.state().toolSearch,"sort"))));
            if(!requestedBestsellers) {
                if("bestsellers".equals(str(filters,"selection")))filters.remove("selection");
                if("best".equals(str(filters,"sort")))filters.remove("sort");
            }
            if(a.has("level"))c.state().level=a.hasNonNull("level")?a.path("level").asText():"";
            if(a.has("totalBudget"))c.state().recommendationBudget=a.hasNonNull("totalBudget")?integerLong(a,"totalBudget"):null;
            else if(filters.get("requestedTotalBudget") instanceof Number)c.state().recommendationBudget=((Number)filters.remove("requestedTotalBudget")).longValue();
            if(reading.clearsTotalBudget(message))c.state().recommendationBudget=null;
            Integer requested=language.bookCount(message);
            if(ChatBookFacts.has(normalizedMessage,"khong can du|khong can du so|khong can du so luong")) {
                requested=null;c.state().discoveryRequestedCount=null;
            }
            if(another && requested==null)c.state().discoveryRequestedCount=null;
            if(requested!=null)c.state().discoveryRequestedCount=normalizedMessage.matches("(?s).*\\b(?:toi da|khong qua|nhieu nhat)\\s+[0-9]+\\s+(?:cuon|quyen)\\b.*")?null:requested;
            boolean allRequested=ChatBookFacts.has(normalizedMessage,"tat ca|toan bo");
            if(allRequested && requested==null)c.state().discoveryRequestedCount=null;
            int count=requested!=null?requested:allRequested?Math.max(1,catalog().size()):continuing && c.state().discoveryRequestedCount!=null?c.state().discoveryRequestedCount:
                Math.min(3,intArg(a,"limit",continuing && filters.get("limit") instanceof Number?((Number)filters.get("limit")).intValue():3));
            if(count<1)return ChatTool.Result.clarify("Bạn muốn mình giới thiệu bao nhiêu cuốn? Mình có thể bắt đầu với vài lựa chọn phù hợp nhé.");
            count=Math.min(1000,count);filters.put("limit",count);
            if(ChatBookFacts.has(normalizedMessage,"chi tim|chi lay|chi dung chu de|chi dung the loai|dung the loai thoi|khong goi y gan|khong lay sach gan|khong lay chu de gan|chi giu.*dung yeu cau|chi.*dung yeu cau thoi"))filters.put("match","exact_only");
            else if(acceptedRelated || normalizedMessage.matches("(?s).*\\b(?:co the|chap nhan|duoc phep|mo rong)\\b.*\\b(?:gan chu de|gan the loai|tuong tu)\\b.*"))filters.put("match","allow_related");
            else if("allow_related".equals(str(filters,"match"))
                    && (!continuing || !"allow_related".equals(str(c.state().toolSearch,"match"))))filters.remove("match");
            filters.remove("relatedOffer");
            if(mandatory.onlyLanguage(reading.hardMessage(message))) {
                filters.put("match","exact_only");filters.remove("preferredQuery");
                String hard=reading.hardMessage(message);
                List<String> requiredLanguages=List.of("java","python","javascript","spring boot").stream()
                    .filter(value->ChatCatalogQuery.word(hard,value) && !excludedLanguages.contains(value)).collect(Collectors.toList());
                if(!requiredLanguages.isEmpty())filters.put("query","lập trình "+String.join(ChatBookFacts.has(hard,"hoac|or")?" hoặc ":" ",requiredLanguages));
            }
            Long min=longValue(filters.get("minPrice")), max=longValue(filters.get("maxPrice"));
            Long budget=c.state().recommendationBudget;
            if(budget!=null)max=max==null?budget:Math.min(max,budget);
            if(min!=null && max!=null && min>max) return ChatTool.Result.clarify("Khoảng giá đang bị ngược. Bạn muốn tìm từ bao nhiêu đến bao nhiêu tiền?");
            ChatSearchCriteriaDto criteria=new ChatSearchCriteriaDto(min,max,str(filters,"category"),str(filters,"author"),str(filters,"sort"),List.of());
            if(filters.get("minRating") instanceof Number) criteria.setMinRating(((Number)filters.get("minRating")).doubleValue());
            String query=str(filters,"query");
            if(!continuing || !filters.containsKey("semanticQuery"))filters.put("semanticQuery",reading.semanticMessage(message));
            else if(reading.contextPatch(message) && !reading.budgetPatch(message) && fit.active(filters))
                filters.put("semanticQuery",ChatDialogueInterpreter.clean(str(filters,"semanticQuery")+"; "+reading.semanticMessage(message),1200));
            if(!continuing)filters.put("semanticGoal",reading.goalRequest(message));
            String schoolConstraint=filters.containsKey("grade")?"lớp "+filters.get("grade"):"";
            String readerConstraint="children".equals(filters.get("readerGroup"))?"thiếu nhi":"";
            List<Product> allCatalog=catalog().stream().filter(b->mandatory.matches(exclusions,b)
                && mandatory.matches(schoolConstraint,b) && mandatory.matches(readerConstraint,b)
                && mandatory.matchesReadingPurpose(str(filters,"query")+" "+reading.semanticMessage(message),b)
                && mandatory.matchesFormat(str(filters,"requiredTopicQuery"),b)
                && !reading.excluded(filters,b,data) && !fit.conflicts(filters,readingFacts(b))).collect(Collectors.toList());
            List<Product> catalog=new ArrayList<>(allCatalog);
            List<String> requiredScopes=filters.get("requiredEconomicScopes") instanceof Collection<?>
                ?((Collection<?>)filters.get("requiredEconomicScopes")).stream().map(Object::toString).toList():List.of();
            catalog.removeIf(b->!mandatory.matchesEconomicScopes(requiredScopes,b)
                || !mandatory.matches(str(filters,"requiredLanguageQuery"),b));
            ChatCatalogQuery searchQuery=new ChatCatalogQuery(language,data);
            if(mandatory.programmingRequest(reading.hardMessage(message)) && !language.alternativeTopics(reading.subjectMessage(message))) {
                catalog=catalog.stream().filter(b->mandatory.matches(reading.hardMessage(message),b)).collect(Collectors.toList());
                if(!mandatory.programmingRequest(query))query=("lập trình "+query).trim();
                filters.put("query",query);
            }
            String quoted=searchQuery.quoted(message);
            if(!quoted.isBlank() && normalizedMessage.matches("(?s).*(?:co chu|chua tu|co tu|chua chu|tu khoa).*(?:trong ten|o ten|ten sach).*"))
                filters.put("titleContains",quoted);
            boolean literalTitle=quoted.length()>=12 && (c.request().getMessage().matches("(?is).*\\b(?:cuốn|quyển|sách)\\b.*")
                    || quoted.matches("(?is).*\\b(?:20\\d{2}|programming|quantum|advanced)\\b.*"));
            if(!str(filters,"titleContains").isBlank()) literalTitle=false;
            if(!str(filters,"title").isBlank()){quoted=str(filters,"title");literalTitle=true;}
            if (literalTitle && !another) {
                query=quoted;
                String normalizedTitle=language.normalize(quoted);
                List<Product> named=catalog.stream().filter(b->language.normalize(b.getName()).equals(normalizedTitle)).collect(Collectors.toList());
                List<Product> exact=searchQuery.search("",criteria,named);
                filters.put("query", quoted);
                filters.put("title",quoted);
                c.state().toolSearch=filters; c.state().criteria=criteria;
                c.state().missingTitle=named.isEmpty()?quoted:""; c.state().emptySearch=exact.isEmpty();
                c.state().displayed=ids(exact); c.state().shortlist=new ArrayList<>(ids(exact)); c.state().focus=null;
                c.state().discoverySeeds=ids(exact);c.state().shownProductIds.addAll(ids(exact));
                return books(named.isEmpty()?"Mình chưa tìm thấy đúng tên sách này trong danh mục sách hiện tại. Mình chưa thể xác nhận giá hoặc tồn kho.":exact.isEmpty()?"Mình tìm thấy tên sách nhưng sách chưa đáp ứng bộ lọc bạn yêu cầu.":"Mình tìm thấy đúng cuốn sách bạn hỏi:",exact,ChatIntent.PRODUCT_INFO,false);
            }
            if(!str(filters,"titleContains").isBlank()) {
                String titlePart=literalText(str(filters,"titleContains"));
                java.util.regex.Pattern titlePattern=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(titlePart)+"(?![\\p{L}\\p{N}])");
                catalog=catalog.stream().filter(b->titlePattern.matcher(literalText(b.getName())).find()).collect(Collectors.toList());
                query=""; filters.put("query","");
            } else if(!another && !Boolean.TRUE.equals(filters.get("semanticGoal")) && !normalizedMessage.matches("(?s).*(?:tuong tu|gan giong).*")) {
                List<Product> named=searchQuery.named(message,catalog);
                if(named.isEmpty() && !query.isBlank()) named=searchQuery.named(query,catalog);
                if(!named.isEmpty()) catalog=named;
                // Keep explicit topic constraints even when the planner omits query.
                if(query.isBlank() && searchQuery.semanticConstraint(message)) { query=message;filters.put("query",query); }
            }
            // An AI supplied category can be a user concept instead of a database category.
            // Apply it as a topic when no active product has that category name.
            String categoryFilter=str(filters,"category");
            if(!categoryFilter.isBlank() && catalog.stream().noneMatch(b->language.normalize(category(b)).contains(language.normalize(categoryFilter)))) {
                query=(query+" "+categoryFilter).trim();
                filters.remove("category");
                Double minRating=criteria.getMinRating();
                criteria=new ChatSearchCriteriaDto(min,max,"",str(filters,"author"),str(filters,"sort"),List.of());
                criteria.setMinRating(minRating);
                filters.put("query",query);
            }
            // Literal unknown topics must not silently return the whole catalog.
            if(!query.isBlank()) {
                String normalized=language.normalize(query);
                List<String> tokens=Arrays.stream(normalized.split("\\s+")).filter(t->!Set.of("sach","cuon","quyen","ve","cho","toi","minh").contains(t)).collect(Collectors.toList());
                ChatCatalogQuery search=new ChatCatalogQuery(language,data);
                boolean literalUnknown=query.matches("(?is).*\\b(?:20\\d{2}|advanced|quantum|mars)\\b.*");
                if(!search.semanticConstraint(query) && literalUnknown && !tokens.isEmpty()) catalog=catalog.stream().filter(b->{
                    String text=language.normalize(b.getName()+" "+b.getDescription()+" "+b.getAuthor()+" "+category(b));
                    return tokens.stream().allMatch(t->ChatCatalogQuery.word(text,t));
                }).collect(Collectors.toList());
            }
            List<Product> matches=levelEligible(searchQuery.search(query,criteria,catalog),c.state().level).stream()
                .filter(b->b.getQuantity()>0).collect(Collectors.toList());
            String semanticQuery=str(filters,"semanticQuery");
            // Planner wording and relevance scores must not dilute the user's topic.
            if(!str(filters,"requiredTopicQuery").isBlank())
                matches=searchQuery.search(str(filters,"requiredTopicQuery"),criteria,matches);
            if(Boolean.TRUE.equals(filters.get("semanticGoal"))) {
                final String goalQuery=query;
                List<Product> hardEligible=levelEligible(searchQuery.search("",criteria,catalog),c.state().level).stream()
                    .filter(b->b.getQuantity()>0 && mandatory.matches(goalQuery,b)).collect(Collectors.toList());
                BookKnowledgeService.SemanticRecall recall=data.semanticRecall(semanticQuery,hardEligible);
                if(recall.evaluated())matches.removeIf(b->!recall.evidence().containsKey(b.getProductId()));
            }
            if(fit.active(filters) && criteria.getSort().isBlank())matches=data.rankByMeaning(semanticQuery,matches);
            matches=reading.prioritize(matches,filters,data);
            if("exact_only".equals(str(filters,"match")) && fit.active(filters))
                matches.removeIf(b->!fit.missing(filters,readingFacts(b)).isEmpty());
            if(!matches.isEmpty())c.state().discoverySeeds=new ArrayList<>(ids(matches));
            List<Product> found=matches.stream().filter(b->!another || !c.state().shownProductIds.contains(b.getProductId())).collect(Collectors.toList());
            // Curate after excluding previously shown books, so "more" can still
            // offer the next best candidates without repeating the first shortlist.
            if(c.state().discoveryRequestedCount==null && !allRequested)
                found=fit.shortlist(found,filters,data);
            if(requestedBestsellers) {
                filters.put("selection","bestsellers");
                found=found.stream().filter(b->b.getSold()>0).sorted(Comparator.comparingInt(Product::getSold).reversed().thenComparing(Product::getProductId)).limit(count).collect(Collectors.toList());
                found=searchQuery.search("",criteria,found);
            }
            if(!requestedBestsellers && criteria.getSort().isBlank() && budget==null && !fit.active(filters)
                    && str(filters,"category").isBlank() && str(filters,"author").isBlank()
                    && str(filters,"title").isBlank() && str(filters,"titleContains").isBlank()
                    && (query.isBlank() || language.isBudgetOnlyDiscovery(query)
                        || language.normalize(query).replaceAll("\\b(?:sach|cuon|quyen|hay|goi|y|tim|doc|nao|vai|mot|chon|giup|toi|minh|cho|nhe)\\b","").trim().isEmpty())) {
                found=diverseGenres(found);
            }
            List<Product> exact=selectAffordable(found,count,budget,filters,reading);
            long spent=exact.stream().mapToLong(data::effectivePrice).sum();
            List<Product> nearby=new ArrayList<>();Map<Long,String> reasons=new LinkedHashMap<>();
            boolean expand="allow_related".equals(str(filters,"match")) && str(filters,"titleContains").isBlank()
                && !allRequested && exact.size()<count && (another || c.state().discoveryRequestedCount!=null || exact.isEmpty());
            if(expand) {
                Set<Long> excluded=new LinkedHashSet<>(c.state().shownProductIds);excluded.addAll(ids(matches));
                String nearbyQuery=Boolean.TRUE.equals(filters.get("semanticGoal"))?semanticQuery:query.isBlank()?categoryFilter:query;
                List<ChatCatalogQuery.Nearby> related=searchQuery.nearby(nearbyQuery,criteria,allCatalog,c.state().discoverySeeds,excluded,semanticQuery);
                List<Product> eligible=levelEligible(related.stream().map(ChatCatalogQuery.Nearby::book).collect(Collectors.toList()),c.state().level);
                nearby=selectAffordable(eligible,count-exact.size(),budget==null?null:budget-spent,filters,reading);
                Set<Long> selectedIds=new HashSet<>(ids(nearby));
                related.stream().filter(r->selectedIds.contains(r.book().getProductId())).forEach(r->reasons.put(r.book().getProductId(),r.reason()));
            }
            c.state().toolSearch=filters;c.state().criteria=criteria;c.state().missingTitle="";
            c.state().discoveryExact=ids(exact);c.state().discoveryNearby=ids(nearby);c.state().discoveryReasons=reasons;
            List<Product> selected=new ArrayList<>(exact);selected.addAll(nearby);
            c.state().emptySearch=selected.isEmpty();c.state().displayed=ids(selected);c.state().shortlist=new ArrayList<>(ids(selected));c.state().focus=null;
            c.state().shownProductIds.addAll(ids(selected));
            filters.put("preferredProductIds",selected.stream().filter(b->reading.preferred(filters,b,data)).map(Product::getProductId).collect(Collectors.toList()));
            return discoveryAnswer(exact,nearby,c,another,requestedBestsellers);
    }
    @Bean public ChatTool getProducts() {
        Map<String,ChatTool.Parameter> p=referenceParameters();p.put("count",integer("Số sách cần lấy khi nói hai/ba cuốn",1,8));
        p.put("field",choice("Nội dung khách cần; explanation giải thích lựa chọn hoặc đào sâu nội dung; table để so sánh", "detail","price","author","rating","inventory","table","bibliography","explanation"));
        return tool("getProducts","Chi tiết hoặc so sánh sách theo tên/tham chiếu; selection để so sánh danh sách.",p,(a,c)->{
            Long previousFocus=c.state().focus;
            List<Product> selected=resolve(a,c);
            String field=a.path("field").asText("detail");
            boolean explicitComparisonPair=false;
            if("table".equals(field)) {
                List<Product> mentioned=new ChatCatalogQuery(language,data).named(c.request().getMessage(),catalog());
                if(mentioned.size()>=2){selected=mentioned;explicitComparisonPair=true;}
                else if(mentioned.size()==1 && previousFocus!=null
                        && !previousFocus.equals(mentioned.get(0).getProductId())
                        && ChatConversationSupport.followupComparison(c.request().getMessage(),language)) {
                    List<Product> prior=byIds(List.of(previousFocus));
                    if(!prior.isEmpty()) {
                        selected=new ArrayList<>(prior);selected.addAll(mentioned);explicitComparisonPair=true;
                    }
                }
            }
            if(!explicitComparisonPair && a.hasNonNull("count") && selected.size()>intArg(a,"count",selected.size())) selected=new ArrayList<>(selected.subList(0,intArg(a,"count",selected.size())));
            if(selected.isEmpty()) {
                if("explanation".equals(field) && c.state().emptySearch) {
                    c.state().explanationRound++;
                    String request=ChatDialogueInterpreter.clean(Objects.toString(c.state().toolSearch.get("query"),""),240);
                    String answer=c.state().explanationRound==1
                        ?"Lượt trước mình chưa tìm được sách đủ phù hợp nên chưa có danh sách để giải thích lựa chọn. Các mô tả hiện có chưa cho mình đủ căn cứ để gắn một cuốn cụ thể với nhu cầu này."
                        :"Mình làm rõ thêm vì sao chưa đề xuất được sách: phần nội dung đã được xác nhận chưa đủ để chứng minh một lựa chọn phù hợp. Việc có sách trong kho chưa cho biết nó có đáp ứng mục tiêu đọc của bạn hay không.";
                    if(!request.isBlank())answer+="\n\nMình vẫn đang giữ yêu cầu bạn nêu: “"+request+"”.";
                    answer+=c.state().explanationRound==1
                        ?" Nếu bạn muốn mở rộng phạm vi, mình có thể tìm theo một chủ đề liên quan do bạn chọn."
                        :" Bạn có thể chọn một khía cạnh muốn ưu tiên hoặc một chủ đề rộng hơn; mình sẽ kiểm tra lại từ nội dung thực tế và nói rõ mức độ liên quan.";
                    return ChatTool.Result.text(answer,ChatIntent.PRODUCT_INFO);
                }
                if(a.hasNonNull("title") && !a.path("title").asText().isBlank()) {
                    c.state().missingTitle=a.path("title").asText();
                    c.state().emptySearch=true;
                    return new ChatTool.Result("Mình chưa tìm thấy sách tên “"+a.path("title").asText()
                            +"” trong danh mục sách hiện tại, nên chưa thể xác nhận giá hoặc tồn kho.",
                            ChatIntent.PRODUCT_INFO,List.of(),List.of(),"COMPLETED");
                }
                return missing(c,List.of());
            }
            c.state().missingTitle="";
            c.state().emptySearch=false;
            if(!"table".equals(field) && selected.stream().anyMatch(b->!c.state().shortlist.contains(b.getProductId()))) {
                c.state().shortlist=new ArrayList<>(ids(selected));
                c.state().comparisonProducts.clear();
            }
            if("table".equals(field) && selected.size()==1 && c.state().comparisonAnchor!=null
                    && !c.state().comparisonAnchor.equals(selected.get(0).getProductId())) {
                List<Product> pair=new ArrayList<>(byIds(List.of(c.state().comparisonAnchor)));
                pair.addAll(selected);selected=pair;
            }
            if("table".equals(field) && selected.size()==1) {
                c.state().focus=selected.get(0).getProductId();
                c.state().comparisonAnchor=selected.get(0).getProductId();
                return ChatTool.Result.clarify("Bạn muốn so sánh “"+selected.get(0).getName()+"” với cuốn nào nữa? Bạn gửi tên cuốn thứ hai nhé, để mình so sánh đúng cặp sách bạn đang cân nhắc.");
            }
            if("table".equals(field) && selected.size()>=2)c.state().comparisonAnchor=null;
            c.state().displayed=ids(selected);
            if ("table".equals(field) && selected.size() >= 2)
                c.state().shortlist=new ArrayList<>(c.state().displayed);
            if ("table".equals(field) && selected.size() >= 2) {
                c.state().comparisonProducts=new ArrayList<>(c.state().displayed);
                c.state().focus=null;
            }
            if(selected.size()==1)c.state().focus=selected.get(0).getProductId();
            if("explanation".equals(field))return explainSelection(selected,c);
            if (!"table".equals(field)) return productFacts(selected,field,c.request().getMessage(),c.state());
            if(!"detail".equals(field)) {
                Map<Long,Double> ratings=data.ratingSnapshot();
                StringBuilder answer=new StringBuilder("table".equals(field)?"| Tên sách | Tác giả | Giá | Rating |\n|---|---|---:|---:|":"");
                for(Product b:selected){
                    String rating=ratings.containsKey(b.getProductId())?String.format(Locale.ROOT,"%.1f/5",ratings.get(b.getProductId())):"Chưa có đánh giá";
                    if("table".equals(field))answer.append("\n| ").append(cell(b.getName())).append(" | ").append(cell(b.getAuthor())).append(" | ").append(money(data.effectivePrice(b))).append(" | ").append(rating).append(" |");
                    else answer.append("\n• “").append(b.getName()).append("”: ").append("price".equals(field)?money(data.effectivePrice(b)):"rating".equals(field)?rating:"Còn "+b.getQuantity()+" bản theo tồn kho hiện tại.");
                }
                if("table".equals(field)) {
                    answer.append("\n\nĐể chọn theo mục tiêu đọc, đây là nội dung catalog của từng cuốn:");
                    for(Product b:selected)answer.append("\n• “").append(b.getName()).append("”: ")
                        .append(ChatDialogueInterpreter.clean(description(b),400));
                    String firstGoal=selected.size()==2?readingGoal(selected.get(0)):"";
                    String secondGoal=selected.size()==2?readingGoal(selected.get(1)):"";
                    if(!firstGoal.isBlank() && !secondGoal.isBlank() && !firstGoal.equals(secondGoal)) {
                        answer.append("\nGợi ý chọn: Nếu bạn muốn ").append(firstGoal).append(", chọn “")
                            .append(selected.get(0).getName()).append("”; nếu muốn ").append(secondGoal)
                            .append(", chọn “").append(selected.get(1).getName()).append("”.");
                    } else if(ChatBookFacts.has(language.normalize(c.request().getMessage()),"nhe nhang|de doc"))
                        answer.append("\nNếu mô tả chưa nói rõ độ dài hoặc văn phong, mình chưa đủ căn cứ để khẳng định cuốn nào đọc nhẹ nhàng hơn.");
                }
                return new ChatTool.Result(answer.toString().trim(),selected.size()==1?ChatIntent.PRODUCT_INFO:ChatIntent.COMPARE_BOOKS,selected.stream().map(this::dto).collect(Collectors.toList()),List.of(),"COMPLETED");
            }
            return books(selected.size()==1?"Thông tin cuốn sách bạn đang hỏi:":"So sánh theo dữ liệu hiện có:",selected,
                    selected.size()==1?ChatIntent.PRODUCT_INFO:ChatIntent.COMPARE_BOOKS,true);
        });
    }
    private String cell(String value){return value==null?"Chưa có dữ liệu":value.replace("|","\\|").replaceAll("[\\r\\n]+"," ");}
    private String literalText(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    @Bean public ChatTool listCategories() {
        return tool("listCategories","Danh mục sách thực tế đang được bán tại cửa hàng.",Map.of(),(a,c)->{
            List<String> names=catalog().stream().map(this::category).filter(n->!n.isBlank()).distinct().sorted().collect(Collectors.toList());
            return ChatTool.Result.text(names.isEmpty()?"Hiện chưa có danh mục sách để hiển thị.":"Các danh mục hiện có:\n• "+String.join("\n• ",names),ChatIntent.DISCOVER_BOOKS);
        });
    }
    @Bean public ChatTool recommendBooks() {
        Map<String,ChatTool.Parameter> p=new LinkedHashMap<>(searchProducts().parameters());
        p.put("level",choice("Trình độ người đọc, không phải từ khóa tên sách","beginner","experienced"));
        p.put("totalBudget",integer("Tổng ngân sách cho tất cả sách được chọn, không phải giá mỗi cuốn",0,1000000000));
        return tool("recommendBooks","Tư vấn theo mục tiêu, trình độ và ngân sách; giữ bộ lọc, tách sách đúng chủ đề với sách gần chủ đề.",p,this::discover);
    }
    private List<Product> levelEligible(List<Product> candidates,String level) {
        return candidates.stream().filter(b->{
            String text=language.normalize(data.searchableText(b));
            if("beginner".equals(level))return !ChatBookFacts.has(text,"nang cao|chuyen sau|advanced")
                || ChatBookFacts.has(text,"co ban|can ban|nhap mon|nguoi moi|bat dau|beginner");
            return !"experienced".equals(level) || !ChatBookFacts.has(text,"co ban|can ban|nhap mon|nguoi moi")
                || ChatBookFacts.has(text,"nang cao|chuyen sau|advanced");
        }).collect(Collectors.toList());
    }
    private List<Product> takeAffordable(List<Product> candidates,int count,Long budget) {
        List<Product> selected=new ArrayList<>();long spent=0;
        for(Product book:candidates)if(selected.size()<count && (budget==null || spent+data.effectivePrice(book)<=budget)) {
            selected.add(book);spent+=data.effectivePrice(book);
        }
        return selected;
    }
    private List<Product> diverseGenres(List<Product> candidates) {
        Map<String,List<Product>> genres=new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparingInt(Product::getSold).reversed().thenComparing(Product::getProductId))
            .forEach(book->genres.computeIfAbsent(language.normalize(category(book)),key->new ArrayList<>()).add(book));
        List<Product> result=new ArrayList<>();
        for(int round=0;result.size()<candidates.size();round++)
            for(List<Product> books:genres.values())if(round<books.size())result.add(books.get(round));
        return result;
    }
    private List<Product> selectAffordable(List<Product> candidates,int count,Long budget,Map<String,Object> filters,ChatReadingRequest reading) {
        List<Product> ordered=reading.prioritize(candidates,filters,data);
        if(budget==null)return takeAffordable(ordered,count,null);
        List<Product> cheapest=new ArrayList<>(candidates);
        cheapest.sort(Comparator.comparingLong(data::effectivePrice).thenComparing(Product::getProductId));
        ordered=reading.prioritize(cheapest,filters,data);
        int feasible=takeAffordable(cheapest,count,budget).size();
        List<Product> selected=new ArrayList<>();long remaining=budget;
        // Honour preference only if the remaining cheapest choices can still fill the feasible count.
        for(Product book:ordered) {
            if(selected.size()>=feasible)break;
            long price=data.effectivePrice(book);
            int needed=feasible-selected.size()-1;
            Set<Long> chosen=new HashSet<>(ids(selected));chosen.add(book.getProductId());
            long reserve=cheapest.stream().filter(b->!chosen.contains(b.getProductId())).limit(needed).mapToLong(data::effectivePrice).sum();
            if(price+reserve<=remaining){selected.add(book);remaining-=price;}
        }
        return selected;
    }
    private ChatTool.Result discoveryAnswer(List<Product> exact,List<Product> nearby,ChatTool.Context c,boolean another,boolean bestsellers) {
        int total=exact.size()+nearby.size();Integer requested=c.state().discoveryRequestedCount;
        ChatReadingFit fit=new ChatReadingFit(language);
        boolean qualified=java.util.stream.Stream.concat(exact.stream(),nearby.stream())
            .anyMatch(b->!fit.missing(c.state().toolSearch,readingFacts(b)).isEmpty());
        if(total==0) {
            c.state().discoveryEmptyRounds++;
            String answer=another?"Mình đã kiểm tra lại các sách đang bán và loại những cuốn đã giới thiệu. Hiện chưa có cuốn nào khác đúng chủ đề"
                :"Mình chưa tìm được sách đang bán đáp ứng yêu cầu này";
            if(c.state().discoveryEmptyRounds>1)answer=another
                ?"Với yêu cầu đang giữ, mình vẫn chưa tìm được cuốn mới đúng chủ đề"
                :"Mình vẫn chưa tìm được cuốn đáp ứng yêu cầu đang giữ";
            answer+=!"allow_related".equals(str(c.state().toolSearch,"match"))?" trong các điều kiện bạn chọn.":" hoặc có nội dung đủ gần mà vẫn đáp ứng các điều kiện bạn chọn.";
            if(requested!=null)answer+=" Vì vậy mình chưa thể giới thiệu đủ "+requested+" cuốn khác nhau.";
            boolean strictLanguage=new ChatExplicitFilters(language,data).hasRequiredLanguage(str(c.state().toolSearch,"requiredLanguageQuery"));
            String next;
            if(!"allow_related".equals(str(c.state().toolSearch,"match")) && !strictLanguage) {
                c.state().toolSearch.put("relatedOffer",true);
                next=" Bạn muốn mình thử mở rộng sang sách có nội dung gần với hướng đọc này không? Mình sẽ giữ các điều kiện về giá, dạng sách và phần bạn muốn loại trừ, rồi nói rõ điểm khác biệt.";
            } else next=c.state().recommendationBudget!=null?" Bạn có thể tăng tổng ngân sách hoặc giảm số cuốn cần chọn để mình tìm một nhóm sách vừa với mức bạn muốn chi.":
                c.state().toolSearch.containsKey("maxPrice") || c.state().toolSearch.containsKey("minRating")?" Bạn có muốn nới điều kiện giá hoặc đánh giá một chút không? Như vậy mình có thể kiểm tra thêm lựa chọn, nhưng vẫn giữ chủ đề bạn cần.":
                " Bạn có thể cho mình một chủ đề khác muốn tìm; mình sẽ kiểm tra theo nhu cầu mới nhé.";
            return new ChatTool.Result(answer+next,ChatIntent.DISCOVER_BOOKS,List.of(),List.of(),"COMPLETED");
        }
        c.state().discoveryEmptyRounds=0;
        String lead;
        if(requested!=null)lead="Bạn muốn "+requested+" cuốn; mình tìm được "+exact.size()+(qualified?" lựa chọn theo nội dung bạn tìm":" cuốn đúng yêu cầu")+(nearby.isEmpty()?".":" và thêm "+nearby.size()+" cuốn có chủ đề hoặc nội dung gần với hướng đó.");
        else if(another && exact.isEmpty())lead="Những cuốn đúng chủ đề mình đã giới thiệu hết theo dữ liệu hiện tại. Có thêm "+nearby.size()+" cuốn gần với hướng đọc đó để bạn cân nhắc:";
        else if(exact.isEmpty())lead="Hiện mình chưa tìm được cuốn đúng chủ đề. Có "+nearby.size()+" lựa chọn gần với hướng đọc đó để bạn cân nhắc:";
        else lead=another?"Mình tìm thêm được "+exact.size()+" cuốn đúng chủ đề, đều khác những cuốn đã giới thiệu:":"Mình chọn được "+exact.size()+(qualified?" lựa chọn theo nội dung để bạn xem thử:":" cuốn phù hợp để bạn cân nhắc:");
        if(requested==null && !exact.isEmpty() && !nearby.isEmpty())lead+=" Mình cũng tìm được "+nearby.size()+" cuốn gần chủ đề và để riêng bên dưới.";
        if(bestsellers)lead+=" Nhóm đúng yêu cầu được chọn theo số lượng bán cao nhất rồi sắp theo thứ tự bạn chọn.";
        if(requested!=null && total<requested)lead+=" Tổng cộng có "+total+" lựa chọn; hiện chưa đủ "+requested+" cuốn khác nhau trong các điều kiện này.";
        if(requested!=null && total<requested && !"allow_related".equals(str(c.state().toolSearch,"match"))) {
            c.state().toolSearch.put("relatedOffer",true);
            lead+=" Mình giữ đúng yêu cầu trước; nếu bạn muốn mở rộng sang nội dung gần chủ đề, mình sẽ kiểm tra thêm và ghi rõ sự khác biệt.";
        }
        String preference=str(c.state().toolSearch,"preferredQuery");
        if(!preference.isBlank()) {
            int preferred=c.state().toolSearch.get("preferredProductIds") instanceof Collection<?>?((Collection<?>)c.state().toolSearch.get("preferredProductIds")).size():0;
            lead+=preferred==0?(fit.active(c.state().toolSearch)?" Phần ưu tiên “"+preference+"” chưa được xác nhận đầy đủ cho các cuốn này.":
                " Các lựa chọn này đáp ứng điều kiện bắt buộc, nhưng hiện chưa có cuốn đáp ứng phần ưu tiên “"+preference+"”."):
                " Có "+preferred+" cuốn đáp ứng phần ưu tiên “"+preference+"”"+(preferred<total?"; các cuốn còn lại là lựa chọn thay thế trong cùng điều kiện.":".");
        }
        StringBuilder answer=new StringBuilder(lead);List<ChatActionDto> actions=new ArrayList<>();List<ChatProductDto> dtos=new ArrayList<>();
        if(qualified)answer.append("\n\nCó vài điểm cần xem thêm trước khi quyết định; mình ghi rõ dưới từng cuốn.");
            if(fit.needs(c.state().toolSearch).contains("accessible") && qualified)
                answer.append(" Dữ liệu hiện chưa xác nhận mức đọc cho mọi cuốn.");
        if(!exact.isEmpty()) {
            answer.append(qualified?"\n\n**Sách theo nội dung bạn tìm:**":"\n\n**Sách đúng yêu cầu:**");
            for(Product book:exact) {
                ChatTool.Result part=discoveryBook(book,c.state(),false);
                answer.append(part.answer());
                List<String> missing=fit.missing(c.state().toolSearch,readingFacts(book));
                if(!missing.isEmpty())answer.append("\nChưa xác nhận ").append(String.join(", ",missing)).append(" cho cuốn này.");
                actions.addAll(part.actions());dtos.addAll(part.products());
            }
        }
        if(!nearby.isEmpty()) {
            answer.append("\n\n**Gợi ý gần chủ đề — các cuốn này chỉ đáp ứng một phần nhu cầu ban đầu:**");
            for(Product book:nearby) {
                ChatTool.Result part=discoveryBook(book,c.state(),true);
                answer.append(part.answer());
                actions.addAll(part.actions());dtos.addAll(part.products());
            }
        }
        if(c.state().recommendationBudget!=null)answer.append("\n\nTổng tiền sách: ").append(money(exact.stream().mapToLong(data::effectivePrice).sum()+nearby.stream().mapToLong(data::effectivePrice).sum())).append(". Chưa gồm phí giao hàng.");
        return new ChatTool.Result(answer.toString(),ChatIntent.DISCOVER_BOOKS,dtos,actions,"COMPLETED");
    }

    private ChatTool.Result discoveryBook(Product book,ChatDialogueMemory.State state,boolean nearby) {
        // Keep product facts/actions from the same catalog formatter, then add a
        // need-specific explanation using approved content rather than a sales claim.
        ChatTool.Result facts=books("",List.of(book),ChatIntent.PRODUCT_INFO,false);
        StringBuilder answer=new StringBuilder(facts.answer());
        Map<String,String> reading=readingFacts(book);
        String summary=reading.getOrDefault("summary","");
        String request=language.normalize(str(state.toolSearch,"requiredTopicQuery")+" "+str(state.toolSearch,"query"));
        String content=language.normalize(data.descriptiveText(book));
        ChatExplicitFilters verified=new ChatExplicitFilters(language,data);
        if(nearby)answer.append("\nMình đưa cuốn này vào như một hướng đọc mở rộng. ")
            .append(state.discoveryReasons.getOrDefault(book.getProductId(),"Nội dung chỉ đáp ứng một phần hướng đọc ban đầu."));
        else {
            String topic=List.of("Java","Python","JavaScript","Spring Boot","tiểu thuyết","truyện ngắn","truyện tranh","trinh thám","phiêu lưu","lập trình","tình yêu","lịch sử","khởi nghiệp","giao tiếp")
                .stream().filter(term->ChatCatalogQuery.word(request,language.normalize(term))
                    && verified.affirmed(content,language.normalize(term))).findFirst().orElse("");
            if(!topic.isBlank())answer.append("\nBạn đang tìm sách ").append(topic)
                .append("; mình chọn cuốn này dựa trên nội dung đã được xác nhận.");
        }
        if(!summary.isBlank())answer.append("\n").append(ChatDialogueInterpreter.clean(summary,550));
        else answer.append("\nHiện chưa có mô tả nội dung đủ để giải thích kỹ hơn về cuốn này.");
        Object ceiling=state.toolSearch.get("maxPrice");
        if(ceiling instanceof Number)answer.append("\nGiá ").append(money(data.effectivePrice(book)))
            .append(" nằm trong mức ").append(money(((Number)ceiling).longValue())).append(" mỗi cuốn bạn chọn.");
        ChatReadingFit fit=new ChatReadingFit(language);
        if(!fit.needs(state.toolSearch).isEmpty() && fit.needs(state.toolSearch).stream().anyMatch(need->fit.confirmed(need,reading))) {
            String evidence=reading.getOrDefault("readingLevel",reading.getOrDefault("readingStyle",""));
            if(!evidence.isBlank())answer.append("\nĐể bạn cân nhắc nhu cầu đọc, hồ sơ ghi: ").append(ChatDialogueInterpreter.clean(evidence,220));
        }
        String audience=language.normalize(str(state.toolSearch,"recipient")+" "+str(state.toolSearch,"semanticQuery"));
        if(ChatBookFacts.has(audience,"nguoi moi|moi bat dau") && reading.getOrDefault("readingLevel","").isBlank())
            answer.append("\nHồ sơ chưa xác nhận độ khó cho người mới. Bạn nên xem mục lục hoặc phần đọc thử trước khi chọn để biết có theo được cách trình bày không nhé.");
        return new ChatTool.Result(answer.toString(),ChatIntent.DISCOVER_BOOKS,facts.products(),facts.actions(),"COMPLETED");
    }

    @Bean public ChatTool findSimilarProducts() {
        return tool("findSimilarProducts","Tìm tên gần giống với sản phẩm chưa có trong catalog; luôn nói đây là sách khác.",Map.of("title",text("Tên sách chưa tìm thấy"),"limit",integer("Số cuốn khách muốn, mặc định tối đa 3",1,1000)),(a,c)->{
            String title=a.path("title").asText(c.state().missingTitle);
            if(title.isBlank())return missing();
            List<Product> all=catalog();
            List<Product> near=all.stream().filter(b->b.getQuantity()>0 && (!language.wantsAnotherBook(c.request().getMessage()) || !c.state().shownProductIds.contains(b.getProductId())))
                .map(b->Map.entry(b,similarity(title,b.getName()))).filter(e->e.getValue()>=0.25)
                .sorted(Map.Entry.<Product,Double>comparingByValue().reversed().thenComparing(e->e.getKey().getProductId())).limit(discoveryCount(a,c)).map(Map.Entry::getKey).collect(Collectors.toList());
            c.state().missingTitle=title;c.state().emptySearch=true;c.state().displayed=ids(near);c.state().shortlist=new ArrayList<>(ids(near));c.state().focus=null;
            c.state().shownProductIds.addAll(ids(near));
            c.state().discoveryExact.clear();c.state().discoveryNearby=ids(near);c.state().discoveryReasons.clear();
            return books(near.isEmpty()?"Mình chưa thấy tên nào đủ gần với “"+title+"”.":"Đây là các tên gần giống; chúng là sản phẩm khác với “"+title+"”:",near,ChatIntent.DISCOVER_BOOKS,false);
        });
    }
    @Bean public ChatTool findRelatedBooks() {
        Map<String,ChatTool.Parameter> parameters=referenceParameters();parameters.put("limit",integer("Số cuốn khách muốn, mặc định tối đa 3",1,1000));
        parameters.put("minPrice",integer("Giá tối thiểu VND",0,1000000000));
        parameters.put("maxPrice",integer("Giá tối đa VND",0,1000000000));
        parameters.put("minRating",number("Rating tối thiểu",0,5));
        return tool("findRelatedBooks","Tìm sách cùng chủ đề, cùng bộ hoặc nên đọc tiếp từ một cuốn đã xác định.",
                parameters,(a,c)->{
            if(c.state().recommendationIntakeRequired && !ChatRecommendationIntake.ready(c.state())) {
                c.state().recommendationPending=true;
                return ChatTool.Result.clarify(ChatRecommendationIntake.question(c.state()));
            }
            List<Product> selected=resolve(a,c);
            if(selected.isEmpty() && a.hasNonNull("productId")
                && Objects.equals(longValue(c.state().toolSearch.get("relatedSourceId")),Long.valueOf(integerLong(a,"productId")))
                && language.moreInCurrentTopic(c.request().getMessage())
                && !ChatBookFacts.has(language.normalize(c.request().getMessage()),"cuon do|cuon nay|thu hai|thu ba|dau tien|con lai"))
                selected=byIds(List.of(integerLong(a,"productId")));
            if(selected.size()!=1)return missing(c,selected);
            Product sourceBook=selected.get(0);Long source=sourceBook.getProductId();
            Map<Long,String> evidence=new LinkedHashMap<>(knowledge==null?Map.of():knowledge.relatedEvidence(List.of(source)));
            Set<Long> excluded=new LinkedHashSet<>(Set.of(source));
            if(language.wantsAnotherBook(c.request().getMessage()))excluded.addAll(c.state().shownProductIds);
            ChatSearchCriteriaDto stated=language.criteria(c.request().getMessage(),List.of());
            boolean continuing=source.equals(longValue(c.state().toolSearch.get("relatedSourceId")));
            ChatExplicitFilters mandatory=new ChatExplicitFilters(language,data);
            Set<String> excludedLanguages=new LinkedHashSet<>();
            if(continuing && c.state().toolSearch.get("excludedLanguages") instanceof Collection<?>)
                ((Collection<?>)c.state().toolSearch.get("excludedLanguages")).forEach(value->excludedLanguages.add(value.toString()));
            excludedLanguages.addAll(mandatory.excludedLanguages(c.request().getMessage()));
            String exclusions=excludedLanguages.stream().map(value->"không lấy "+value).collect(Collectors.joining(", "));
            String requiredQuery=mandatory.onlyLanguage(c.request().getMessage())?c.request().getMessage():
                continuing?str(c.state().toolSearch,"requiredRelatedQuery"):"";
            Long min=a.has("minPrice")?a.hasNonNull("minPrice")?Long.valueOf(integerLong(a,"minPrice")):null:stated.getMinPrice()!=null?stated.getMinPrice():continuing?c.state().criteria.getMinPrice():null;
            Long max=a.has("maxPrice")?a.hasNonNull("maxPrice")?Long.valueOf(integerLong(a,"maxPrice")):null:stated.getMaxPrice()!=null?stated.getMaxPrice():continuing?c.state().criteria.getMaxPrice():null;
            ChatSearchCriteriaDto criteria=new ChatSearchCriteriaDto(min,max,"","","",List.of());
            criteria.setMinRating(a.has("minRating")?a.hasNonNull("minRating")?a.path("minRating").asDouble():null:
                stated.getMinRating()!=null?stated.getMinRating():continuing?c.state().criteria.getMinRating():null);
            if(min!=null && max!=null && min>max)return ChatTool.Result.clarify("Khoảng giá đang bị ngược. Bạn muốn tìm từ bao nhiêu đến bao nhiêu tiền?");
            String sourceContent=data.descriptiveText(sourceBook);
            new ChatCatalogQuery(language,data).nearby(sourceContent,criteria,catalog(),List.of(source),excluded)
                .forEach(near->evidence.putIfAbsent(near.book().getProductId(),near.reason()));
            List<Product> candidates=new ChatCatalogQuery(language,data).search("",criteria,catalog()).stream().filter(b->b.getQuantity()>0 && !source.equals(b.getProductId()) && evidence.containsKey(b.getProductId())
                && mandatory.matches(exclusions,b) && mandatory.matches(requiredQuery,b)
                && (!language.wantsAnotherBook(c.request().getMessage()) || !c.state().shownProductIds.contains(b.getProductId()))).collect(Collectors.toList());
            int count=discoveryCount(a,c);List<Product> found=candidates.stream().limit(count).collect(Collectors.toList());
            c.state().displayed=ids(found);c.state().shortlist=new ArrayList<>(ids(found));c.state().focus=null;
            c.state().toolSearch=new LinkedHashMap<>(Map.of("query",sourceBook.getName(),"limit",count,"relatedSourceId",source));
            if(!excludedLanguages.isEmpty())c.state().toolSearch.put("excludedLanguages",new ArrayList<>(excludedLanguages));
            if(!requiredQuery.isEmpty())c.state().toolSearch.put("requiredRelatedQuery",requiredQuery);
            if(min!=null)c.state().toolSearch.put("minPrice",min);if(max!=null)c.state().toolSearch.put("maxPrice",max);
            if(criteria.getMinRating()!=null)c.state().toolSearch.put("minRating",criteria.getMinRating());
            c.state().criteria=criteria;c.state().level="";c.state().recommendationBudget=null;c.state().comparisonProducts.clear();
            c.state().missingTitle="";c.state().emptySearch=found.isEmpty();
            c.state().discoverySeeds=new ArrayList<>(List.of(source));c.state().discoveryExact.clear();c.state().discoveryNearby=ids(found);
            c.state().discoveryReasons=new LinkedHashMap<>();found.forEach(b->c.state().discoveryReasons.put(b.getProductId(),evidence.get(b.getProductId())));
            c.state().shownProductIds.add(source);c.state().shownProductIds.addAll(ids(found));
            String shortage=language.bookCount(c.request().getMessage())!=null && found.size()<count?" Hiện chỉ có "+found.size()+" cuốn liên quan, chưa đủ "+count+" cuốn khác nhau.":"";
            ChatTool.Result result=books(found.isEmpty()?"Mình chưa tìm thấy cuốn nào khác có liên kết hoặc nội dung đủ gần với “"+sourceBook.getName()+"” trong các sách đang bán.":
                    "Mình tìm được các lựa chọn có liên kết hoặc nội dung gần với “"+sourceBook.getName()+"”; đây là sách khác, bạn có thể cân nhắc:"+shortage,
                    found,ChatIntent.DISCOVER_BOOKS,false);
            String answer=result.answer();
            // Give each suggestion its actual relation/content evidence rather than implying identical coverage.
            for(Product book:found)answer+="\n\nVề “"+book.getName()+"”: "+evidence.get(book.getProductId());
            return new ChatTool.Result(answer,result.intent(),result.products(),result.actions(),result.status());
        });
    }
    private int discoveryCount(JsonNode arguments,ChatTool.Context context) {
        Integer requested=language.bookCount(context.request().getMessage());
        context.state().discoveryRequestedCount=requested;
        return requested==null?Math.min(3,intArg(arguments,"limit",3)):Math.max(1,Math.min(1000,requested));
    }
    @Bean public ChatTool updateSelection() {
        return tool("updateSelection","Bỏ sách khỏi danh sách đang cân nhắc, KHÔNG phải giỏ hàng. most_expensive là giá cao nhất.",referenceParameters(),(a,c)->{
            List<Product> selected=byIds(c.state().shortlist);
            List<Product> removed=resolve(a,c);
            if(removed.size()!=1 || !ids(selected).contains(removed.get(0).getProductId()))return missing();
            selected.removeIf(b->b.getProductId().equals(removed.get(0).getProductId()));
            c.state().shortlist=ids(selected); c.state().displayed=ids(selected);
            if(Objects.equals(c.state().focus,removed.get(0).getProductId()))c.state().focus=null;
            return books("Đã bỏ “"+removed.get(0).getName()+"” khỏi danh sách đang cân nhắc.",selected,ChatIntent.COMPARE_BOOKS,false);
        });
    }
    @Bean public ChatTool prepareCheckout() {
        Map<String,ChatTool.Parameter> p=referenceParameters();p.put("quantity",integer("Số cuốn muốn mua",1,99));
        return tool("prepareCheckout","Chuẩn bị mua một sách hoặc toàn bộ giỏ (source=cart, reference=selection). Chỉ mở form và báo giá; khách phải xác nhận trước khi tạo đơn.",p,(a,c)->{
            if("cart".equals(a.path("source").asText()) && !a.hasNonNull("title") && !a.hasNonNull("productId")
                    && (a.path("reference").asText().isBlank() || "selection".equals(a.path("reference").asText()))) {
                if(c.request().getCartItems().isEmpty())return ChatTool.Result.clarify("Giỏ hàng đang trống nên mình chưa có sách để chuẩn bị đơn. Bạn chọn sách trước khi đặt hàng nhé; mình có thể giúp tìm theo nhu cầu của bạn.");
                return new ChatTool.Result("Mình có thể giúp bạn đặt các sách trong giỏ. Bạn kiểm tra giỏ rồi chọn địa chỉ và phương thức thanh toán trong khung chat để mình tính đúng tổng tiền. Chưa có đơn nào được tạo; bạn sẽ xem lại trước khi xác nhận.",
                    ChatIntent.PURCHASE,List.of(),List.of(new ChatActionDto("CHECKOUT_CART","Đặt hàng từ giỏ",null,null,false)),"AWAITING_CONFIRMATION");
            }
            List<Product> selected=resolve(a,c); if(selected.size()!=1)return missing(c,selected);
            Product b=selected.get(0);int quantity=intArg(a,"quantity",1);
            if(b.getQuantity()<quantity)return ChatTool.Result.clarify("Số lượng muốn mua vượt tồn kho hiện tại nên mình chưa thể chuẩn bị đủ sách cho bạn. Bạn chọn số lượng ít hơn nhé.");
            c.state().focus=b.getProductId();
            c.state().displayed=List.of(b.getProductId());c.state().emptySearch=false;
            ChatActionDto action=new ChatActionDto("BUY_NOW","Tiếp tục đặt hàng",b.getProductId(),null,false);
            action.setQuantity(quantity);
            return new ChatTool.Result("Mình đã chọn “"+b.getName()+"” với số lượng "+quantity+" cuốn. Bạn bấm Tiếp tục đặt hàng để chọn thông tin giao hàng và xem tổng tiền; mình sẽ chỉ tạo đơn sau khi bạn xem tổng tiền và xác nhận.",ChatIntent.PURCHASE,List.of(dto(b)),List.of(action),"AWAITING_CONFIRMATION");
        });
    }
    @Bean public ChatTool prepareCartChange() {
        Map<String,ChatTool.Parameter> p=referenceParameters();
        p.put("operation",choice("Thao tác giỏ hàng", "add","remove","update"));p.put("quantity",integer("Số lượng thêm hoặc số lượng mới",1,99));
        return tool("prepareCartChange","Yêu cầu thêm/xóa/sửa số lượng trong giỏ trình duyệt. Chỉ tạo nút chờ xác nhận.",p,(a,c)->{
            List<Product> selected=resolve(a,c); if(selected.size()!=1)return missing(c,selected);
            Product b=selected.get(0);String operation=a.path("operation").asText("add");int quantity=intArg(a,"quantity",1);
            int inCart=c.request().getCartItems().stream().filter(i->b.getProductId().equals(i.getProductId())).mapToInt(ChatRequestDto.CartItem::getQuantity).sum();
            if(!"add".equals(operation) && inCart==0)return ChatTool.Result.clarify("Mình không thấy “"+b.getName()+"” trong giỏ hàng của bạn. Giỏ hàng chưa được thay đổi.");
            if(!"remove".equals(operation) && b.getQuantity()<("add".equals(operation)?quantity+inCart:quantity))return ChatTool.Result.clarify("Tổng số lượng trong giỏ sẽ vượt tồn kho hiện tại. Bạn chọn số lượng ít hơn để mình kiểm tra đủ sách cho đơn nhé.");
            String type="remove".equals(operation)?"REMOVE_FROM_CART":"update".equals(operation)?"UPDATE_CART_QUANTITY":"ADD_TO_CART";
            String label="remove".equals(operation)?"Xóa khỏi giỏ":"update".equals(operation)?"Đổi thành "+quantity+" cuốn":"Thêm "+quantity+" cuốn vào giỏ";
            ChatActionDto action=new ChatActionDto(type,label,b.getProductId(),null,true);action.setQuantity(quantity);
            c.state().focus=b.getProductId();c.state().displayed=List.of(b.getProductId());c.state().emptySearch=false;
            return new ChatTool.Result("Mình đã chuẩn bị thao tác với “"+b.getName()+"”. Bạn kiểm tra rồi xác nhận để mình sửa đúng sách và số lượng bạn muốn nhé. Giỏ hàng chưa được thay đổi.",ChatIntent.PRODUCT_INFO,List.of(dto(b)),List.of(action),"AWAITING_CONFIRMATION");
        });
    }
    @Bean public ChatTool getCart() {
        return tool("getCart","Xem giỏ thiết bị từ snapshot; giá lấy lại từ database.",Map.of(),(a,c)->{
            List<Product> books=byIds(c.request().getCartItems().stream().map(ChatRequestDto.CartItem::getProductId).collect(Collectors.toList()));
            if(books.isEmpty())return ChatTool.Result.text("Mình chưa thấy sách đang bán trong giỏ hàng của bạn; giỏ có thể đang trống hoặc sách đã ngừng bán. Bạn mở giỏ để kiểm tra nhé, hoặc cho mình biết nhu cầu để tìm cuốn khác.",ChatIntent.UNKNOWN);
            StringBuilder out=new StringBuilder("Mình đã kiểm tra giá hiện tại. Giỏ hàng của bạn đang có:");long total=0;
            for(Product b:books) {
                int quantity=c.request().getCartItems().stream().filter(i->b.getProductId().equals(i.getProductId())).findFirst().orElseThrow().getQuantity();
                total+=data.effectivePrice(b)*quantity;
                out.append("\n• “").append(b.getName()).append("” — ").append(quantity).append(" cuốn × ").append(money(data.effectivePrice(b)));
            }
            out.append("\nTạm tính tiền sách: ").append(money(total)).append(". Bạn có thể bấm Xem và sửa giỏ để điều chỉnh. Mình sẽ kiểm tra lại phí giao hàng và số sách còn bán khi bạn xem báo giá, để tổng tiền sát với đơn thực tế.");
            return new ChatTool.Result(out.toString(),ChatIntent.UNKNOWN,List.of(),List.of(
                new ChatActionDto("SHOW_CART","Xem và sửa giỏ",null,null,false),
                new ChatActionDto("CHECKOUT_CART","Đặt hàng từ giỏ",null,null,false)),"COMPLETED");
        });
    }
    @Bean public ChatTool prepareClearCart() {
        return tool("prepareClearCart","Xóa toàn bộ giỏ hàng, chỉ tạo đề xuất chờ xác nhận; không yêu cầu chọn sách.",Map.of(),(a,c)->{
            if(c.request().getCartItems().isEmpty())return ChatTool.Result.text("Giỏ hàng đang trống nên không có sách để xóa. Bạn cứ giữ nguyên giỏ nhé; khi muốn chọn sách mới, mình có thể giúp.",ChatIntent.UNKNOWN);
            return new ChatTool.Result("Bạn xác nhận xóa toàn bộ sách khỏi giỏ nhé. Mình chờ bạn đồng ý để tránh bỏ nhầm những cuốn bạn vẫn muốn mua. Giỏ hàng chưa được thay đổi.",ChatIntent.UNKNOWN,List.of(),List.of(new ChatActionDto("CLEAR_CART","Xóa toàn bộ giỏ",null,null,true)),"AWAITING_CONFIRMATION");
        });
    }
    @Bean public ChatTool prepareCancelOrder() {
        return tool("prepareCancelOrder","Chuẩn bị hủy đơn của chính khách đã đăng nhập. Không có orderId thì dùng đơn đang hỏi hoặc đơn gần nhất. Kiểm tra trạng thái/thanh toán, chờ khách xác nhận.",Map.of("orderId",integer("Mã đơn",1,9007199254740991L)),(a,c)->{
            String question=language.normalize(c.request().getMessage());
            if(question.matches("(?s).*\\b(?:lam sao|cach|huong dan|nhu the nao|the nao)\\b.*"))
                return ChatTool.Result.text(policies.answer(c.request().getMessage()),ChatIntent.POLICY);
            try {
                Long requested=a.hasNonNull("orderId")?Long.valueOf(integerLong(a,"orderId")):c.state().focusedOrderId;
                if(!a.hasNonNull("orderId") && ChatBookFacts.has(question,"gan nhat|moi nhat"))requested=null;
                Long id=cancellation.preview(requested);
                c.state().focusedOrderId=id;
                ChatActionDto action=new ChatActionDto("CANCEL_ORDER","Kiểm tra và hủy đơn #"+id,null,null,true);
                action.setOrderId(id);
                return new ChatTool.Result("Mình đã kiểm tra: đơn #"+id+" có thể hủy theo trạng thái hiện tại. Bạn bấm nút bên dưới để xem lại sách và tổng đơn, rồi xác nhận nếu đúng đơn muốn hủy nhé. Mình sẽ chỉ hủy sau khi bạn xác nhận.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_CONFIRMATION");
            } catch(org.springframework.web.server.ResponseStatusException e) {
                if(e.getStatus()==org.springframework.http.HttpStatus.UNAUTHORIZED)
                    return ChatTool.Result.text("Bạn cần đăng nhập để kiểm tra và hủy đơn của mình. Sau khi đăng nhập, hãy hỏi lại “Hủy đơn hàng giúp tôi” hoặc gửi mã đơn muốn hủy.",ChatIntent.ORDER_SUPPORT);
                return ChatTool.Result.text(e.getReason()==null?"Chưa thể hủy đơn này.":e.getReason(),ChatIntent.ORDER_SUPPORT);
            }
        });
    }
    private ChatTool.Result wishlistLogin() {
        return new ChatTool.Result("Bạn đăng nhập để mình mở đúng danh sách yêu thích của tài khoản trên thiết bị này nhé. Sau đó bạn có thể lưu sách để xem lại khi cần.",ChatIntent.PRODUCT_INFO,List.of(),
            List.of(new ChatActionDto("LOGIN","Đăng nhập",null,"/login",false)),"AWAITING_INPUT");
    }
    @Bean public ChatTool prepareReorder() {
        return tool("prepareReorder","Mua lại sách từ đơn cũ của chính khách: kiểm tra đơn, mở bước xem giá/tồn kho hiện tại, chờ xác nhận thêm giỏ. Không tạo đơn mới.",Map.of("orderId",integer("Mã đơn",1,9007199254740991L)),(a,c)->{
            try {
                Long requested=a.hasNonNull("orderId")?Long.valueOf(integerLong(a,"orderId")):c.state().focusedOrderId;
                if(!a.hasNonNull("orderId") && ChatBookFacts.has(language.normalize(c.request().getMessage()),"gan nhat|moi nhat"))requested=null;
                var preview=orders.reorderPreview(requested); Long id=((Number)preview.get("orderId")).longValue();c.state().focusedOrderId=id;
                var action=new ChatActionDto("REORDER_ORDER","Xem sách để mua lại đơn #"+id,null,null,true);action.setOrderId(id);
                return new ChatTool.Result("Mình có thể giúp bạn mua lại sách từ đơn #"+id+". Bạn bấm nút bên dưới để kiểm tra giá và tồn kho hiện tại, chỉnh số lượng rồi xác nhận thêm vào giỏ nhé. Bước này chưa đặt đơn mới.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_CONFIRMATION");
            } catch(org.springframework.web.server.ResponseStatusException e) {
                return new ChatTool.Result(e.getReason()==null?"Mình chưa mở được đơn đã mua. Bạn kiểm tra lại mã đơn nhé.":e.getReason(),ChatIntent.ORDER_SUPPORT,List.of(),
                    e.getStatus()==org.springframework.http.HttpStatus.UNAUTHORIZED?List.of(new ChatActionDto("LOGIN","Đăng nhập",null,"/login",false)):List.of(),"NEEDS_INPUT");
            }
        });
    }
    @Bean public ChatTool prepareReturnRequest() {
        return tool("prepareReturnRequest","Chuẩn bị biểu mẫu yêu cầu đổi trả cho đơn đã hoàn thành của chính khách. Cần mã đơn hoặc đơn đang hỏi; không tự chấp nhận/hoàn tiền. Khách xem lại rồi xác nhận gửi.",Map.of("orderId",integer("Mã đơn",1,9007199254740991L)),(a,c)->{
            Long id=a.hasNonNull("orderId")?Long.valueOf(integerLong(a,"orderId")):c.state().focusedOrderId;
            if(id==null)return new ChatTool.Result("Bạn muốn đổi trả sách trong đơn nào? Bạn chọn đơn đã nhận trong mục Đơn hàng hoặc gửi mã đơn nhé, để mình lấy đúng sách và giúp cửa hàng kiểm tra yêu cầu của bạn.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("VIEW_ORDERS","Chọn đơn đã nhận",null,"/orders",false)),"NEEDS_INPUT");
            try {
                support.returnOptions(id);c.state().focusedOrderId=id;
                var action=new ChatActionDto("CREATE_RETURN_REQUEST","Soạn yêu cầu đổi trả đơn #"+id,null,null,true);action.setOrderId(id);
                return new ChatTool.Result("Mình đã tìm thấy đơn #"+id+" đã hoàn thành. Bạn mở biểu mẫu bên dưới, chọn cuốn sách, điền ngày nhận và lý do để cửa hàng xem xét đúng trường hợp nhé. Bạn sẽ kiểm tra lại trước khi gửi; cửa hàng chưa chấp nhận đổi trả hay hoàn tiền ở bước này.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_INPUT");
            } catch(org.springframework.web.server.ResponseStatusException e) {
                return new ChatTool.Result(e.getReason()==null?"Mình chưa mở được đơn cần đổi trả. Bạn kiểm tra lại mã đơn nhé.":e.getReason(),ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("VIEW_ORDERS","Kiểm tra đơn hàng",null,"/orders",false)),"NEEDS_INPUT");
            }
        });
    }
    private ChatTool.Result supportLogin() {
        return new ChatTool.Result("Bạn đăng nhập để yêu cầu hỗ trợ được lưu đúng tài khoản và chỉ bạn xem được phản hồi nhé. Sau đó mình sẽ giúp bạn soạn nội dung để nhân viên hiểu rõ tình huống.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("LOGIN","Đăng nhập",null,"/login",false)),"NEEDS_INPUT");
    }
    @Bean public ChatTool prepareHumanSupport() {
        return tool("prepareHumanSupport","Mở bản nháp để nhờ nhân viên hỗ trợ. Khách xem/sửa tóm tắt và xác nhận gửi; phản hồi qua yêu cầu trên web, không hứa nhân viên đang trực tuyến.",Map.of("orderId",integer("Mã đơn nếu khách nói rõ",1,9007199254740991L)),(a,c)->{
            if(access==null || access.currentEmail()==null)return supportLogin();
            Long id=a.hasNonNull("orderId")?Long.valueOf(integerLong(a,"orderId")):null;
            if(id!=null)try { orders.reorderPreview(id); } catch(org.springframework.web.server.ResponseStatusException e){return ChatTool.Result.text(e.getReason(),ChatIntent.ORDER_SUPPORT);}
            var action=new ChatActionDto("CREATE_SUPPORT_REQUEST","Soạn yêu cầu cho nhân viên",null,null,true);action.setOrderId(id);
            return new ChatTool.Result("Mình sẽ giúp bạn chuyển điều cần hỗ trợ thành một yêu cầu cho nhân viên. Bạn mở bản nháp bên dưới, kiểm tra và bổ sung nội dung rồi xác nhận gửi nhé. Nhân viên sẽ phản hồi trong mục yêu cầu; mình chưa có thông tin để xác nhận ai đang trực tuyến hoặc thời gian trả lời cụ thể.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_INPUT");
        });
    }
    @Bean public ChatTool getSupportRequests() {
        return tool("getSupportRequests","Mở danh sách yêu cầu hỗ trợ/đổi trả và phản hồi của chính khách trong chat. Không đưa nội dung riêng tư của ticket vào prompt hoặc history.",Map.of(),(a,c)->{
            if(access==null || access.currentEmail()==null)return supportLogin();
            return new ChatTool.Result("Bạn bấm nút bên dưới để xem yêu cầu đã gửi, trạng thái mới nhất và phản hồi của nhân viên nhé. Danh sách chỉ lấy từ tài khoản đang đăng nhập của bạn.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("SHOW_SUPPORT_REQUESTS","Xem yêu cầu và phản hồi",null,null,false)),"COMPLETED");
        });
    }
    @Bean public ChatTool getWishlist() {
        return tool("getWishlist","Xem danh sách yêu thích đang dùng trên website/thiết bị của khách đăng nhập. Không suy ra từ sở thích đọc.",Map.of(),(a,c)->{
            if(access==null || access.currentEmail()==null)return wishlistLogin();
            List<Product> books=byIds(c.request().getWishlistProductIds());
            c.state().displayed=ids(books); c.state().shortlist=ids(books);
            if(books.size()==1)c.state().focus=books.get(0).getProductId();
            return books.isEmpty()?ChatTool.Result.text("Danh sách yêu thích trên thiết bị này đang trống hoặc các sách đã lưu không còn bán. Bạn tìm một cuốn muốn đọc rồi nhắn mình lưu vào yêu thích để dễ xem lại nhé.",ChatIntent.PRODUCT_INFO)
                :books("Đây là các sách bạn đã lưu vào yêu thích trên website ở thiết bị này. Giá dưới đây là giá hiện tại nhé:",books,ChatIntent.PRODUCT_INFO,false);
        });
    }
    @Bean public ChatTool prepareWishlistChange() {
        var parameters=referenceParameters(); parameters.put("operation",choice("Thao tác yêu thích","add","remove"));
        return tool("prepareWishlistChange","Chuẩn bị lưu/bỏ một sách khỏi yêu thích; dùng cùng danh sách local của website. Chỉ trả nút; chưa thay đổi dữ liệu.",parameters,(a,c)->{
            if(access==null || access.currentEmail()==null)return wishlistLogin();
            List<Product> selected=resolve(a,c);
            if(selected.size()!=1)return missing(c,selected);
            Product book=selected.get(0); boolean remove="remove".equals(a.path("operation").asText("add"));
            var action=new ChatActionDto(remove?"REMOVE_FROM_WISHLIST":"ADD_TO_WISHLIST",remove?"Bỏ yêu thích":"Lưu yêu thích",book.getProductId(),null,false);
            c.state().focus=book.getProductId();c.state().displayed=List.of(book.getProductId());
            return new ChatTool.Result("Bạn bấm “"+action.getLabel()+"” ở cuốn “"+book.getName()+"” nhé. Mình sẽ cập nhật cùng danh sách yêu thích trên website của tài khoản này, để bạn dễ tìm lại sách.",ChatIntent.PRODUCT_INFO,List.of(dto(book)),List.of(action),"AWAITING_INPUT");
        });
    }
    @Bean public ChatTool getMyOrders() {
        return tool("getMyOrders","Tra đơn của tài khoản đăng nhập; mode=latest xem đơn gần nhất; recent xem 3 đơn. Không nhận userId/email.",Map.of("mode",choice("Phạm vi đơn","latest","recent")),
                (a,c)->{
                    c.state().focusedOrderId=orders.latestOrderId();
                    return ChatTool.Result.text("latest".equals(a.path("mode").asText()) && c.state().focusedOrderId!=null
                        ?orders.orderById(c.state().focusedOrderId):orders.latestOrders(),ChatIntent.ORDER_SUPPORT);
                });
    }
    @Bean public ChatTool getOrderDetail() {
        return tool("getOrderDetail","Tra mã đơn, backend kiểm tra chủ sở hữu.",Map.of("orderId",integer("Mã đơn cần tra",1,9007199254740991L)),
                (a,c)->{
                    Long id=a.hasNonNull("orderId")?Long.valueOf(integerLong(a,"orderId")):c.state().focusedOrderId;
                    if(id==null) {
                        // No authenticated/focused order: return the account-aware lookup response.
                        return ChatTool.Result.text(orders.latestOrders(),ChatIntent.ORDER_SUPPORT);
                    }
                    String answer=orders.orderById(id);
                    if(answer.startsWith("Đơn #"))c.state().focusedOrderId=id;
                    if (answer.startsWith("Đơn #")) return new ChatTool.Result(answer,ChatIntent.ORDER_SUPPORT,List.of(),
                        List.of(new ChatActionDto("VIEW_ORDER","Xem chi tiết đơn #"+id,null,"/orders/"+id,false)),"COMPLETED");
                    return ChatTool.Result.text(answer,ChatIntent.ORDER_SUPPORT);
                });
    }
    @Bean public ChatTool getPolicy() {
        return tool("getPolicy","Chính sách vận chuyển, đổi trả, thanh toán của VinaBook.",Map.of(),
                (a,c)->new ChatTool.Result(policies.answer(c.request().getMessage(),c.state()),ChatIntent.POLICY,
                    List.of(),List.of(),c.state().returnAwaitingReason && policies.handlesReturn(c.request().getMessage(),c.state())?"NEEDS_INPUT":"COMPLETED"));
    }
    @Bean public ChatTool respond() {
        Map<String,ChatTool.Parameter> p=new LinkedHashMap<>();
        p.put("kind",choice("Loại phản hồi","greeting","clarify","unsupported","missing_product"));
        p.put("topic",choice("Nhu cầu đang làm rõ","preferences","product","comparison","gift","address"));
        p.put("scope",choice("Nhu cầu mới hay bổ sung câu hỏi trước","new","continue"));
        p.put("missing",choice("Một thông tin thực sự còn thiếu; auto nếu chưa biết chủ đề","auto","subject","purpose","recipient","level","budget","value"));
        for(String field:List.of("subject","purpose","recipient"))p.put(field,text("Thông tin "+field+" khách đã nói; không suy đoán, không chứa địa chỉ/số điện thoại"));
        p.put("level",choice("Trình độ khách đã nói","beginner","experienced"));
        p.put("budget",integer("Tổng ngân sách VND khách đã nói",0,1000000000));
        return tool("respond","Chào hỏi hoặc hỏi một thông tin còn thiếu. Giữ thông tin khách đã cho để tiếp tục; không bịa chức năng.",p,(a,c)->{
            if("greeting".equals(a.path("kind").asText()))return ChatTool.Result.text("Chào bạn! Mình có thể cùng bạn chọn sách, so sánh các cuốn đang cân nhắc hoặc hỗ trợ chuyện mua hàng. Bạn đang muốn tìm một cuốn để đọc, làm quà hay cần xem lại đơn hàng?",ChatIntent.SMALL_TALK);
            if("unsupported".equals(a.path("kind").asText()))return ChatTool.Result.clarify("Mình chưa có chức năng thực hiện yêu cầu này trong chat. Bạn nói thêm mục đích nhé; mình có thể hỗ trợ phần liên quan như tìm sách, giải đáp chính sách hoặc tra cứu đơn.");
            if("missing_product".equals(a.path("kind").asText()))return ChatTool.Result.clarify(c.state().missingTitle.isBlank()
                ?"Mình chưa xác định được cuốn sách cần kiểm tra. Bạn gửi lại tên sách hoặc tác giả nhé, để mình tìm đúng cuốn trước khi trả lời về giá hay nội dung."
                :"Mình vẫn chưa tìm thấy “"+c.state().missingTitle+"” trong danh mục sách hiện tại. Mình chưa có lịch sử ẩn/xóa sản phẩm để xác minh lần bạn thấy trước đó và không thể đoán giá khi chưa xác định được sách.");
            if("new".equals(a.path("scope").asText())) {
                c.state().clarificationFacts.clear();
                c.state().comparisonAnchor=null;
            }
            for(String field:List.of("subject","purpose","recipient","level","budget")) {
                if(a.has(field)) {
                    if(a.get(field).isNull())c.state().clarificationFacts.remove(field);
                    else if(a.get(field).isNumber())c.state().clarificationFacts.put(field,a.get(field).longValue());
                    else c.state().clarificationFacts.put(field,ChatDialogueInterpreter.clean(a.get(field).asText(),180));
                }
            }
            String topic=a.path("topic").asText();
            String missing=a.path("missing").asText("auto");
            Map<String,Object> facts=c.state().clarificationFacts;
            String userText=language.normalize(c.request().getMessage());
            ChatSearchCriteriaDto stated=language.criteria(c.request().getMessage(),catalog());
            if(ChatRecommendationIntake.ready(c.state()) && Set.of("","preferences","gift").contains(topic)
                && Set.of("budget","level","purpose","recipient","subject","auto").contains(missing)) {
                var search=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                search.put("query",new ChatReadingRequest(language).hardMessage(c.state().recommendationNeed))
                    .put("scope","new");
                if(stated.getMaxPrice()!=null)search.put("maxPrice",stated.getMaxPrice());
                if(stated.getMinPrice()!=null)search.put("minPrice",stated.getMinPrice());
                return recommendBooks().execute(search,c);
            }
            boolean explicitRecommendation=userText.matches("(?s).*\\b(?:goi y|tu van|nen doc|nen chon)\\b.*")
                && !stated.getTopics().isEmpty();
            if(explicitRecommendation && Set.of("","preferences","gift").contains(topic) && !"value".equals(missing)) {
                String subject=stated.getTopics().get(0);
                var search=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                search.put("query",subject).put("scope","new").put("limit",3);
                if(stated.getMaxPrice()!=null)search.put("maxPrice",stated.getMaxPrice());
                return searchProducts().execute(search,c);
            }
            if(topic.isBlank() && facts.containsKey("recipient"))topic="gift";
            // A supplied field must never be asked again. Once a subject is known,
            // optional preferences do not block a first grounded recommendation.
            if(facts.containsKey(missing))missing="auto";
            if(Set.of("","preferences","gift").contains(topic) && "auto".equals(missing) && !str(facts,"subject").isBlank()) {
                var search=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                search.put("query",str(facts,"subject")).put("scope","new");
                if(facts.containsKey("level"))search.put("level",str(facts,"level"));
                if(facts.containsKey("budget"))search.put("totalBudget",longValue(facts.get("budget")));
                return recommendBooks().execute(search,c);
            }
            if(Set.of("","preferences","gift").contains(topic) && !"auto".equals(missing)) {
                String question=switch(missing) {
                    case "purpose" -> "Bạn muốn đọc để giải trí hay để học một kỹ năng cụ thể? Biết mục đích sẽ giúp mình chọn nội dung sát với điều bạn đang cần.";
                    case "recipient" -> "Người nhận quà thuộc khoảng tuổi nào? Mình hỏi để cân nhắc nội dung và mức đọc phù hợp hơn cho người nhận.";
                    case "level" -> "Bạn mới bắt đầu hay đã có nền tảng về chủ đề này? Biết mức đọc sẽ giúp mình tránh chọn cuốn quá khó hoặc lặp lại điều bạn đã biết.";
                    case "budget" -> "Nếu đã có mức dự kiến, bạn muốn dành tối đa bao nhiêu tiền cho số sách này? Mình sẽ dùng để lọc thêm; bạn cũng có thể để mình gợi ý trước.";
                    case "value" -> "Bạn ưu tiên giá thấp, dễ đọc hay nội dung chuyên sâu để mình chọn cuốn đáng tiền theo nhu cầu của bạn?";
                    default -> "gift".equals(topic)?"Người nhận quà thích đọc chủ đề gì? Biết sở thích sẽ giúp mình chọn món quà hợp người nhận hơn.":"Bạn muốn đọc về chủ đề gì? Mình cần biết hướng đọc để chọn sách có nội dung liên quan, thay vì đưa một danh sách chung.";
                };
                return ChatTool.Result.clarify(question);
            }
            return ChatTool.Result.clarify(clarification(topic,c));
        });
    }
    private String clarification(String topic,ChatTool.Context c) {
        String q=language.normalize(c.request().getMessage());
        if("gift".equals(topic) || q.contains("lam qua") || q.contains("tang ban"))
            return "Người nhận quà thích đọc chủ đề gì? Biết sở thích sẽ giúp mình chọn món quà hợp người nhận hơn. Nếu chưa biết, bạn có thể chọn truyện nhẹ nhàng, kỹ năng sống hoặc sách khám phá.";
        if("comparison".equals(topic) || q.matches("(?s).*(?:so sanh|khac nhau|re hon|rating cao hon|phu hop.*hon|hai cuon).*") )
            return comparisonQuestion(c);
        if("address".equals(topic) || q.contains("dia chi"))
            return "Bạn muốn thêm địa chỉ mới hay chọn địa chỉ đã lưu? Khi đặt sách, bạn có thể chọn địa chỉ trong phần thông tin giao hàng.";
        if("product".equals(topic) || q.matches("(?s).*(?:cuon nay|cuon do|quyen nay|cuon dau|ton kho|bao nhieu ban|gio hang|vao gio|mua \\d+ cuon|gia bao nhieu).*") ) {
            if(c.state().emptySearch && c.state().displayed.isEmpty())
                return "Lượt tìm vừa rồi chưa có sách phù hợp nên chưa có cuốn để chọn. Bạn muốn đổi chủ đề hoặc nới điều kiện tìm kiếm nào? Mình sẽ giữ những điều bạn vẫn ưu tiên để tìm sát hơn.";
            return ChatVoice.ASK_PRODUCT;
        }
        return "Bạn muốn đọc về chủ đề gì? Mình cần biết hướng đọc để chọn sách có nội dung liên quan, thay vì đưa một danh sách chung. Ví dụ: truyện nhẹ nhàng, lập trình hoặc kỹ năng sống.";
    }
    private String comparisonQuestion(ChatTool.Context c) {
        List<Product> named=new ChatCatalogQuery(language,data).named(c.request().getMessage(),catalog());
        if(named.isEmpty() && c.state().focus!=null)named=byIds(List.of(c.state().focus));
        if(named.size()==1) {
            c.state().focus=named.get(0).getProductId();
            c.state().comparisonAnchor=named.get(0).getProductId();
            return "Bạn muốn so sánh “"+named.get(0).getName()+"” với cuốn nào nữa? Bạn gửi tên cuốn thứ hai nhé, để mình so sánh đúng cặp sách bạn đang cân nhắc.";
        }
        return ChatVoice.ASK_COMPARISON;
    }
    private ChatTool.Result missing(ChatTool.Context c,List<Product> candidates) {
        if(candidates.size()>1) {
            c.state().displayed=ids(candidates);c.state().shortlist=ids(candidates);c.state().focus=null;
            return new ChatTool.Result("Mình tìm thấy nhiều cuốn khớp yêu cầu:\n• "+candidates.stream().map(Product::getName).collect(Collectors.joining("\n• "))
                +"\nBạn chọn tên sách hoặc số thứ tự nào? Mình cần bạn chọn rõ để kiểm tra hoặc đặt đúng cuốn.",ChatIntent.PRODUCT_INFO,candidates.stream().map(this::dto).collect(Collectors.toList()),List.of(),"NEEDS_INPUT");
        }
        return ChatTool.Result.clarify(clarification("product",c));
    }
    private List<Product> catalog(){return products.findByStatusTrue().stream().filter(p->Boolean.TRUE.equals(p.getStatus())).collect(Collectors.toList());}
    private List<Product> byIds(List<Long> ids){return new ChatCatalogQuery(language,data).byIds(ids,catalog());}
    private List<Long> ids(List<Product> books){return books.stream().map(Product::getProductId).collect(Collectors.toList());}
    private List<Product> resolve(JsonNode a,ChatTool.Context c) {
        List<Product> catalog=catalog();ChatCatalogQuery search=new ChatCatalogQuery(language,data);
        List<Long> cartIds=c.request().getCartItems().stream().map(ChatRequestDto.CartItem::getProductId).collect(Collectors.toList());
        boolean cartSource="cart".equals(a.path("source").asText());
        String question=language.normalize(c.request().getMessage());
        String quoted=search.quoted(c.request().getMessage());
        if(!cartSource && !quoted.isBlank() && !ChatBookFacts.has(question,"trong ten|tu khoa|co chu")) {
            String title=language.normalize(quoted);
            return catalog.stream().filter(b->language.normalize(b.getName()).contains(title)).collect(Collectors.toList());
        }
        List<Product> mentioned=search.named(c.request().getMessage(),catalog);
        if(!cartSource && !mentioned.isEmpty())return mentioned;
        String reference=a.path("reference").asText("focus");
        String explicitReference="";
        if(ChatBookFacts.has(question,"(?:cuon|sach|quyen|tac pham|ban|tap|bo) (?:do|nay|ay|vua chon|ban chon|ban vua chon)|\\bno\\b"))explicitReference="focus";
        if(!cartSource && ChatBookFacts.has(question,"(?:cuon|sach|quyen) con lai"))explicitReference="other";
        if(ChatBookFacts.has(question,"(?:cuon|sach|quyen) (?:dau tien|dau|thu nhat|so 1)"))explicitReference="first";
        if(ChatBookFacts.has(question,"(?:cuon|sach|quyen) (?:thu hai|so 2)"))explicitReference="second";
        if(ChatBookFacts.has(question,"(?:cuon|sach|quyen) (?:thu ba|so 3)"))explicitReference="third";
        if(ChatBookFacts.has(question,"(?:cuon|sach|quyen)(?: nao)? re (?:hon|nhat)"))explicitReference="cheapest";
        if(ChatBookFacts.has(question,"(?:cuon|sach|quyen)(?: nao)? dat (?:hon|nhat)"))explicitReference="most_expensive";
        if(ChatConversationSupport.pluralReference(c.request().getMessage(),language))explicitReference="selection";
        // A model-supplied title/ID cannot override the reference the customer actually used.
        if(explicitReference.isBlank() && a.hasNonNull("title") && !a.path("title").asText().isBlank()) {
            String title=language.normalize(a.path("title").asText());
            return catalog.stream().filter(b->language.normalize(b.getName()).contains(title)).limit(8).collect(Collectors.toList());
        }
        if(explicitReference.isBlank() && a.hasNonNull("productId")) {
            long id=integerLong(a,"productId");
            // A planner cannot select an unseen product merely by inventing its ID.
            if(!c.state().displayed.contains(id) && !c.state().shortlist.contains(id)
                    && !Objects.equals(c.state().focus,id) && !Objects.equals(c.request().getActiveProductId(),id) && !cartIds.contains(id))return List.of();
            return search.byIds(List.of(id),catalog);
        }
        if(!explicitReference.isBlank())reference=explicitReference;
        List<Product> selected=search.byIds(cartSource?cartIds:c.state().shortlist,catalog);
        if("selection".equals(reference))return selected;
        if("other".equals(reference)) {
            List<Product> pair=search.byIds(c.state().comparisonProducts.isEmpty()?c.state().shortlist:c.state().comparisonProducts,catalog);
            return pair.size()==2 && c.state().focus!=null
                    ?pair.stream().filter(b->!b.getProductId().equals(c.state().focus)).collect(Collectors.toList()):List.of();
        }
        if("cheapest".equals(reference)||"most_expensive".equals(reference)) {
            Comparator<Product> order=Comparator.comparingLong(data::effectivePrice).thenComparing(Product::getProductId);
            if("most_expensive".equals(reference))order=order.reversed();
            return selected.stream().sorted(order).limit(1).collect(Collectors.toList());
        }
        List<Product> displayed=search.byIds(cartSource?cartIds:!c.state().comparisonProducts.isEmpty()?c.state().comparisonProducts:c.state().shortlist,catalog);
        int index="first".equals(reference)?0:"second".equals(reference)?1:"third".equals(reference)?2:"last".equals(reference)?displayed.size()-1:-1;
        if(!"focus".equals(reference))return index>=0&&index<displayed.size()?List.of(displayed.get(index)):List.of();
        Long focus=cartSource?null:c.state().focus!=null?c.state().focus:c.request().getActiveProductId();
        return focus!=null?search.byIds(List.of(focus),catalog):displayed.size()==1?displayed:List.of();
    }
    private ChatTool.Result books(String intro,List<Product> books,ChatIntent intent,boolean detail) {
        StringBuilder text=new StringBuilder(intro);List<ChatActionDto> actions=new ArrayList<>();
        Map<Long,Double> ratings=data.ratingSnapshot();
        for(Product b:books) {
            text.append("\n\n• “").append(b.getName()).append("” — ").append(money(data.effectivePrice(b)))
                .append(" · ").append(b.getAuthor()==null?"Chưa có tác giả":b.getAuthor())
                .append(" · ").append(ratings.containsKey(b.getProductId())?String.format(Locale.ROOT,"%.1f/5",ratings.get(b.getProductId())):"Chưa có đánh giá");
            if(detail)text.append("\n").append(ChatDialogueInterpreter.clean(description(b),650)).append("\nTồn kho: ").append(b.getQuantity());
            else if(intent==ChatIntent.DISCOVER_BOOKS)text.append("\n").append(ChatDialogueInterpreter.clean(description(b),200));
            actions.add(new ChatActionDto("VIEW_PRODUCT","Xem sách",b.getProductId(),"/product/"+b.getProductId(),false));
            if(b.getQuantity()>0)actions.add(new ChatActionDto("ADD_TO_CART","Thêm giỏ",b.getProductId(),null,true));
        }
        return new ChatTool.Result(text.toString(),intent,books.stream().map(this::dto).collect(Collectors.toList()),actions,books.isEmpty()?"NEEDS_INPUT":"COMPLETED");
    }

    @Bean public ChatTool bookReviews() {
        Map<String,ChatTool.Parameter> parameters=new LinkedHashMap<>(referenceParameters());
        parameters.put("mode",choice("Khách muốn xem đánh giá, viết/cập nhật, hoặc cần làm rõ ý định", "read","write","clarify"));
        parameters.put("index",integer("Số thứ tự khách chọn trong danh sách sách đang chờ đánh giá",1,8));
        return tool("bookReviews","Xem các nhận xét thật theo đúng sách; hoặc mở biểu mẫu để khách tự viết/cập nhật đánh giá sau khi đăng nhập. Không tự gửi đánh giá thay khách.",parameters,(a,c)->{
            int index=a.hasNonNull("index")?intArg(a,"index",1)-1:-1;
            List<Product> selected=a.hasNonNull("index")
                ?index>=0 && index<c.state().displayed.size()?byIds(List.of(c.state().displayed.get(index))):List.of():resolve(a,c);
            String question=language.normalize(c.request().getMessage());
            // Choosing read/write does not identify a book after a missing or ambiguous title.
            if(!c.state().pendingReviewMode.isBlank() && c.state().pendingReviewProductId==null
                    && !ChatCustomerActions.shortReviewMode(question).isEmpty()) selected=List.of();
            var subject=java.util.regex.Pattern.compile("\\b(?:sach|cuon|quyen)(?: sach)?\\s+(.+)").matcher(question);
            if(!a.hasNonNull("index") && new ChatCatalogQuery(language,data).quoted(c.request().getMessage()).isBlank() && subject.find()
                    && !subject.group(1).matches("(?:nay|do|ay|nao|vua|thu|so|dau|cuoi|re|dat|co|duoc|dang|may|bao)\\b.*")
                    && new ChatCatalogQuery(language,data).named(c.request().getMessage(),catalog()).isEmpty()) selected=List.of();
            String mode=a.path("mode").asText("read");
            if(selected.size()!=1) {
                c.state().pendingReviewMode=mode; c.state().pendingReviewProductId=null;
                if(selected.size()>1) {
                    c.state().displayed=ids(selected); c.state().shortlist=ids(selected); c.state().focus=null;
                    return new ChatTool.Result("Mình tìm thấy vài cuốn khớp tên bạn nói. Bạn chọn tên sách hoặc số thứ tự bên dưới để mình mở đúng phần đánh giá nhé.",
                        ChatIntent.PRODUCT_INFO,selected.stream().map(this::dto).collect(Collectors.toList()),List.of(),"NEEDS_INPUT");
                }
                return ChatTool.Result.clarify("Mình cần biết tên cuốn sách bạn muốn xem hoặc viết đánh giá, để lấy đúng nhận xét và tránh chọn nhầm. Bạn gửi tên sách hoặc chọn một cuốn trong danh sách gần nhất nhé.");
            }
            Product book=selected.get(0);
            c.state().focus=book.getProductId(); c.state().displayed=List.of(book.getProductId());
            ChatProductDto product=dto(book);
            if("clarify".equals(mode)) {
                c.state().pendingReviewMode=mode; c.state().pendingReviewProductId=book.getProductId();
                return new ChatTool.Result("Mình hiểu bạn đang hỏi về đánh giá của cuốn “"+book.getName()+"”. Bạn muốn xem nhận xét của độc giả hay muốn tự viết đánh giá? Mình hỏi để mở đúng phần bạn cần.",
                    ChatIntent.PRODUCT_INFO,List.of(product),List.of(new ChatActionDto("VIEW_PRODUCT","Mở trang sách",book.getProductId(),"/product/"+book.getProductId(),false)),"NEEDS_INPUT");
            }
            ChatCustomerActions.clearReview(c.state());
            if("write".equals(mode)) {
                if(access==null || access.currentUserId()==null)return new ChatTool.Result("Mình có thể giúp bạn viết đánh giá cho “"+book.getName()+"”. Bạn đăng nhập để nhận xét được lưu đúng tài khoản; sau đó bạn sẽ chọn số sao, xem lại nội dung và tự bấm gửi.",
                    ChatIntent.PRODUCT_INFO,List.of(product),List.of(new ChatActionDto("LOGIN","Đăng nhập để viết đánh giá",null,"/login",false)),"NEEDS_INPUT");
                boolean editing=rates!=null && rates.findByProductOrderByIdDesc(book).stream()
                    .anyMatch(rate->rate.getUser()!=null && Objects.equals(rate.getUser().getUserId(),access.currentUserId()));
                String label=editing?"Cập nhật đánh giá":"Viết đánh giá";
                return new ChatTool.Result("Mình hiểu bạn muốn chia sẻ cảm nhận về “"+book.getName()+"”. Bạn mở biểu mẫu bên dưới, chọn số sao và nhập nhận xét để người đọc khác tham khảo nhé. Mình chưa lưu gì; bạn sẽ chủ động bấm gửi sau khi xem lại.",
                    ChatIntent.PRODUCT_INFO,List.of(product),List.of(new ChatActionDto("WRITE_REVIEW",label,book.getProductId(),null,false)),"AWAITING_INPUT");
            }
            List<vn.fs.entity.Rate> reviews=rates==null?List.of():rates.findByProductOrderByIdDesc(book).stream()
                .filter(rate->rate.getRating()!=null && rate.getRating()>=1 && rate.getRating()<=5)
                .collect(Collectors.toList());
            ChatActionDto writeAction=access!=null && access.currentUserId()!=null
                ?new ChatActionDto("WRITE_REVIEW","Viết đánh giá",book.getProductId(),null,false)
                :new ChatActionDto("LOGIN","Đăng nhập để viết đánh giá",null,"/login",false);
            if(reviews.isEmpty())return new ChatTool.Result("Mình vừa kiểm tra đánh giá của “"+book.getName()+"”; hiện chưa có nhận xét hợp lệ nào để bạn tham khảo. Nếu bạn đã đọc cuốn này, bạn có thể chia sẻ cảm nhận của mình.",
                ChatIntent.PRODUCT_INFO,List.of(product),List.of(writeAction),"COMPLETED");
            double average=reviews.stream().mapToDouble(rate->rate.getRating()).average().orElse(0);
            long positive=reviews.stream().filter(rate->rate.getRating()>=4).count();
            long critical=reviews.stream().filter(rate->rate.getRating()<=2).count();
            StringBuilder answer=new StringBuilder("Mình đã kiểm tra ").append(reviews.size()).append(" đánh giá của “").append(book.getName())
                .append("”. Điểm trung bình là ").append(String.format(Locale.ROOT,"%.1f/5",average)).append(".");
            if(positive>0)answer.append(" Có ").append(positive).append(" đánh giá từ 4 sao trở lên");
            if(critical>0)answer.append(positive>0?" và ":" Có ").append(critical).append(" đánh giá từ 2 sao trở xuống");
            if(positive>0 || critical>0)answer.append(".");
            List<vn.fs.entity.Rate> comments=reviews.stream().filter(rate->rate.getComment()!=null && !rate.getComment().trim().isEmpty()).collect(Collectors.toList());
            List<vn.fs.entity.Rate> sample=new ArrayList<>(comments.stream().limit(5).collect(Collectors.toList()));
            if(sample.stream().noneMatch(rate->rate.getRating()>=4))comments.stream().filter(rate->rate.getRating()>=4).findFirst().ifPresent(sample::add);
            if(sample.stream().noneMatch(rate->rate.getRating()<=2))comments.stream().filter(rate->rate.getRating()<=2).findFirst().ifPresent(sample::add);
            answer.append(sample.isEmpty()?"\nHiện các đánh giá này chỉ có điểm sao, chưa có nhận xét bằng lời.":"\n\nMột số nhận xét để bạn tham khảo:");
            for(vn.fs.entity.Rate rate:sample) {
                String comment=ChatDialogueInterpreter.clean(rate.getComment().trim().replaceAll("[\\r\\n\\t]+"," "),260);
                answer.append("\n• ").append(String.format(Locale.ROOT,rate.getRating()==Math.rint(rate.getRating())?"%.0f/5 sao":"%.1f/5 sao",rate.getRating())).append(" — “").append(comment).append("”");
            }
            return new ChatTool.Result(answer.toString(),ChatIntent.PRODUCT_INFO,List.of(product),List.of(writeAction),"COMPLETED");
        });
    }
    private ChatProductDto dto(Product b){return new ChatProductDto(b.getProductId(),b.getName(),b.getAuthor(),category(b),data.effectivePrice(b),b.getPrice()==null?0:b.getPrice(),b.getDiscount(),b.getQuantity(),b.getImage());}
    private String description(Product b){return readingFacts(b).getOrDefault("summary",Objects.toString(b.getDescription(),""));}
    private ChatTool.Result explainSelection(List<Product> selected,ChatTool.Context c) {
        String original=Objects.toString(c.state().toolSearch.get("query"),"");
        if(ChatConversationSupport.audienceOnly(original,language))return ChatTool.Result.clarify(
            "Gợi ý trước chưa có đủ căn cứ về sở thích của người đọc. Mình không có thống kê sách được yêu thích theo giới tính nên không thể nói các cuốn đó đặc biệt hợp với các bạn nữ. Người bạn đang tìm sách cho thích chủ đề gì để mình chọn lại phù hợp hơn?");
        List<Long> current=ids(selected);
        c.state().explanationRound=current.equals(c.state().explanationProductIds)?c.state().explanationRound+1:1;
        c.state().explanationProductIds=new ArrayList<>(current);
        String opening=c.state().explanationRound==1?"Mình giải thích dựa trên nội dung của từng cuốn và nhu cầu bạn đã nêu:"
            :c.state().explanationRound==2?"Để bạn cân nhắc kỹ hơn, mình đi thêm vào nội dung và đối tượng đọc đã được xác nhận:"
            :"Mình sẽ làm rõ thêm căn cứ lựa chọn và những điểm còn thiếu thông tin để bạn dễ quyết định:";
        if(c.state().recentDialogue.stream().anyMatch(turn->turn.getOrDefault("assistant","").contains("Mình giải thích dựa trên nội dung của từng cuốn và nhu cầu bạn đã nêu:")) && c.state().explanationRound==1)
            opening=selected.size()==1?"Mình nói kỹ hơn về cuốn vừa chọn để bạn cân nhắc:"
                :"Với cả danh sách vừa gợi ý, mỗi cuốn có một trọng tâm để bạn cân nhắc:";
        StringBuilder answer=new StringBuilder(opening);
        for(Product book:selected) {
            Map<String,String> facts=readingFacts(book);
            List<String> missing=new ChatReadingFit(language).missing(c.state().toolSearch,facts);
            String summary=ChatDialogueInterpreter.clean(facts.getOrDefault("summary",""),c.state().explanationRound==1?450:900);
            answer.append("\n\n• “").append(book.getName()).append("”\n");
            if(c.state().discoveryNearby.contains(book.getProductId()))
                answer.append("Đây là lựa chọn gần với yêu cầu, để bạn cân nhắc bổ sung. ")
                    .append(c.state().discoveryReasons.getOrDefault(book.getProductId(),"Nội dung chỉ đáp ứng một phần hướng đọc ban đầu.")).append(" ");
            else if(c.state().discoveryExact.contains(book.getProductId()) && !original.isBlank())
                answer.append(missing.isEmpty()?"Cuốn này nằm trong hướng đọc bạn đang tìm. ":"Mình chọn cuốn này theo phần nội dung đã được mô tả. ");
            answer.append(summary.isBlank()?"Cửa hàng chưa có mô tả nội dung đủ để giải thích sự phù hợp của cuốn này.":summary);
            if(!missing.isEmpty())answer.append("\nChưa xác nhận ").append(String.join(", ",missing)).append(" cho cuốn này.");
            String goal=language.normalize(original+" "+str(c.state().toolSearch,"semanticQuery"));
            String content=language.normalize(facts.getOrDefault("summary",""));
            if(ChatBookFacts.has(language.normalize(c.request().getMessage()),"lap ngan sach|chi tieu hang thang")
                    && !ChatBookFacts.has(content,"lap ngan sach|chi tieu hang thang"))
                answer.append("\nChưa có thông tin đã duyệt xác nhận sách hướng dẫn lập ngân sách chi tiêu hàng tháng. Phần nội dung trên là phạm vi mình có thể xác nhận, nên mình chưa chọn cuốn này làm tài liệu cho mục tiêu lập ngân sách của bạn.");
            if(ChatBookFacts.has(goal,"giao tiep|ung xu") && ChatBookFacts.has(content,"doi nhan xu the|giao tiep")
                    && c.state().explanationRound>1 && !ChatBookFacts.has(content,"mot nguyen tac.*la|vi du cu the|tinh huong thuc te"))
                answer.append("\nĐiểm để cân nhắc là phạm vi ứng xử trong quan hệ với người khác. Mô tả chưa nêu từng nguyên tắc hoặc ví dụ, nên mình chưa thể xác nhận cuốn này hướng dẫn riêng về thuyết trình hay đàm phán.");
            if(new ChatReadingFit(language).needs(c.state().toolSearch).contains("accessible") && !missing.isEmpty()
                    && ChatBookFacts.has(content,"tuoi tho|tinh ban"))
                answer.append("\nNếu bạn muốn bắt đầu từ câu chuyện về ").append(ChatBookFacts.has(content,"tuoi tho")?"tuổi thơ":"tình bạn")
                    .append(", đây là điểm nội dung để cân nhắc khi xem thử.");
            String currentQuestion=language.normalize(c.request().getMessage());
            List<String> detailKeys=c.state().explanationRound>1?List.of("learningGoal","audience","readingLevel","readingStyle")
                :ChatBookFacts.has(currentQuestion,"do tuoi|tuoi ro|danh cho|doi tuong")?List.of("audience")
                :ChatBookFacts.has(currentQuestion,"de doc|muc doc")?List.of("readingLevel"):List.of();
            for(String key:detailKeys) {
                String value=facts.getOrDefault(key,"");
                if(!value.isBlank() && !summary.contains(value))answer.append("\n")
                    .append(switch(key){case "learningGoal"->"Mục tiêu đọc: ";case "audience"->"Đối tượng: ";case "readingLevel"->"Mức đọc: ";default->"Cách trình bày: ";})
                    .append(ChatDialogueInterpreter.clean(value,240));
            }
            if(c.state().explanationRound>1 && facts.getOrDefault("readingLevel","").isBlank() && facts.getOrDefault("readingStyle","").isBlank())
                answer.append("\nPhần mô tả chưa xác nhận mức đọc hoặc cách trình bày, nên nếu bạn đang cân nhắc hai điểm này thì cần thêm thông tin trước khi chọn.");
        }
        String selectionQuestion=language.normalize(c.request().getMessage());
        ChatReadingFit selectionFit=new ChatReadingFit(language);
        boolean asksReadability=ChatBookFacts.has(selectionQuestion,"cuon nao.*de doc.*nhat");
        boolean asksAge=ChatBookFacts.has(selectionQuestion,"cuon nao.*(?:do tuoi|tuoi ro).*nhat");
        if(asksReadability || asksAge && c.state().toolSearch.containsKey("readerAge")) {
            List<Product> confirmed=selected.stream().filter(b->asksReadability
                ?selectionFit.confirmed("accessible",readingFacts(b))
                :selectionFit.ageConfirmed(c.state().toolSearch,readingFacts(b))).collect(Collectors.toList());
            if(confirmed.size()==1) {
                Product chosen=confirmed.get(0);c.state().focus=chosen.getProductId();
                answer.append("\n\nTrong danh sách vừa xem, “").append(chosen.getName())
                    .append(asksReadability?"” là cuốn duy nhất có thông tin đã duyệt xác nhận độ dễ đọc. Đây là lựa chọn có căn cứ rõ hơn cho nhu cầu người ít đọc của bạn.":
                        "” là cuốn duy nhất có phân loại độ tuổi đã được đối chiếu phù hợp với tuổi bạn nêu.");
            }
        }
        if(c.state().explanationRound>2)answer.append("\n\nMình đã trình bày các dữ kiện nội dung hiện có. Nếu bạn muốn đi sâu vào một khía cạnh cụ thể, bạn cho mình biết điểm đang phân vân; phần mô tả chưa xác nhận thì mình sẽ nói rõ.");
        if(c.state().explanationRound==2 && selected.stream().allMatch(b->knowledge==null
                || knowledge.approvedReadingFacts(b).keySet().stream().noneMatch(key->Set.of("audience","learningGoal","readingStyle").contains(key))))
            answer.append("\n\nMô tả hiện chưa có mục lục hoặc ví dụ cụ thể, nên mình chưa thể chỉ ra từng bài tập hay phương pháp trong sách. Bạn muốn áp dụng vào tình huống nào để mình giúp đối chiếu nhu cầu với phần nội dung đã biết?");
        String readerRequest=language.normalize(Objects.toString(c.state().toolSearch.get("semanticQuery"),""));
        if(ChatBookFacts.has(readerRequest,"[0-9]+ tuoi") && selected.stream().anyMatch(b->knowledge==null
                || knowledge.approvedReadingFacts(b).getOrDefault("audience","").isBlank()))
            answer.append("\n\nCửa hàng chưa xác nhận độ tuổi đọc cho từng cuốn, nên mình chưa đủ căn cứ chọn cuốn hợp với bé nhất chỉ theo tuổi. Bạn có thể cân nhắc nội dung và xem thử cùng bé trước khi chọn; tuổi nhân vật không phải độ tuổi đọc của sách.");
        if("children".equals(c.state().toolSearch.get("readerGroup"))
                && ChatBookFacts.has(language.normalize(c.request().getMessage()),"cuon nao.*(?:hop|sat|nhat)")) {
            List<String> comics=selected.stream().filter(b->ChatBookFacts.has(language.normalize(readingFacts(b).getOrDefault("summary","")),"truyen tranh"))
                .map(Product::getName).collect(Collectors.toList());
            if(!comics.isEmpty())answer.append("\n\nTheo mô tả, ").append(comics.stream().map(name->"“"+name+"”").collect(Collectors.joining(" và ")))
                .append(" là truyện tranh. Bé thường tự đọc truyện chữ hay quen đọc truyện tranh hơn?");
        }
        return new ChatTool.Result(answer.toString(),ChatIntent.PRODUCT_INFO,selected.stream().map(this::dto).collect(Collectors.toList()),List.of(),"COMPLETED");
    }
    private ChatTool.Result productFacts(List<Product> selected,String field,String question,ChatDialogueMemory.State state) {
        Set<String> fields=ChatBookFacts.requested(question,selected,language);
        if(fields.isEmpty())fields.addAll(switch(field) {
            case "detail" -> List.of("author","price","inventory","description");
            case "bibliography" -> List.of("publisher","publicationYear","pageCount","isbn");
            default -> List.of(field);
        });
        Map<Long,Double> ratings=data.ratingSnapshot();
        List<String> answers=new ArrayList<>();
        List<ChatActionDto> actions=new ArrayList<>();
        for(Product book:selected) {
            List<String> facts=new ArrayList<>();
            if(fields.contains("author"))facts.add("Tác giả: "+(book.getAuthor()==null || book.getAuthor().isBlank()?"chưa có dữ liệu":book.getAuthor()));
            if(fields.contains("price"))facts.add("Giá sau giảm: "+money(data.effectivePrice(book)));
            if(fields.contains("inventory"))facts.add(book.getQuantity()>0?"Đang bán, còn "+book.getQuantity()+" bản theo tồn kho hiện tại.":"Cuốn này hiện hết hàng. Bạn có thể chọn Báo mình khi có hàng trên thẻ sách bên dưới; mình lưu thông báo vào tài khoản để bạn xem khi sách về nhé.");
            if(fields.contains("rating"))facts.add(ratings.containsKey(book.getProductId())?String.format(Locale.ROOT,"Rating: %.1f/5",ratings.get(book.getProductId())):"Chưa có đánh giá.");
            if(fields.contains("description"))facts.add(ChatDialogueInterpreter.clean(description(book),650));
            Map<String,String> reading=readingFacts(book);
            if(fields.contains("audience") && !reading.getOrDefault("audience","").isBlank())facts.add("Đối tượng đọc: "+reading.get("audience"));
            if(ChatConversationSupport.suitabilityQuestion(question,language)) {
                if(!fields.contains("audience") && !reading.getOrDefault("audience","").isBlank())facts.add("Đối tượng đọc: "+reading.get("audience"));
                if(!reading.getOrDefault("readingLevel","").isBlank())facts.add("Mức đọc: "+reading.get("readingLevel"));
                if(ChatBookFacts.has(language.normalize(question),"chua biet lap trinh")
                        && ChatBookFacts.has(language.normalize(reading.getOrDefault("readingLevel","")),"can.*ma nguon"))
                    facts.add("Bạn chưa biết lập trình thì mình chưa chọn cuốn này làm điểm bắt đầu. Nên học kiến thức nền tảng và thực hành mã trước, để theo được các ví dụ mà hồ sơ sách đã xác nhận.");
            }
            if(ChatBookFacts.has(language.normalize(question),"(?:hop|phu hop|danh cho|doc duoc).*(?:be|tre|tuoi)")) {
                Map<String,Object> ageRequest=new LinkedHashMap<>();
                ChatReadingFit readingFit=new ChatReadingFit(language);readingFit.capture(ageRequest,question);
                if(readingFit.ageConflicts(ageRequest,readingFacts(book)))facts.add(
                    "Tuổi bạn nêu nằm ngoài nhóm tuổi được nhà xuất bản phân loại trong hồ sơ. Vì vậy mình không xếp cuốn này vào gợi ý đúng độ tuổi bạn cần.");
            }
            if(new ChatReadingFit(language).needs(state.toolSearch).contains("gentle")
                    && ChatBookFacts.has(language.normalize(question),"thay vao|thay the|nen thay")
                    && ChatBookFacts.has(language.normalize(reading.getOrDefault("summary","")+" "+reading.getOrDefault("readingStyle","")),"ket thuc.*buon"))
                facts.add("Với yêu cầu truyện nhẹ nhàng trước đó, mình không đề nghị thay cuốn này vào danh sách: phần giới thiệu đã xác nhận kết thúc buồn, nên hướng đọc này khác với sở thích bạn nêu.");
            Set<String> bibliography=new LinkedHashSet<>(fields);
            bibliography.retainAll(ChatBookFacts.BIBLIOGRAPHY);
            if(!bibliography.isEmpty())facts.add(knowledge==null?ChatBookFacts.missingBibliography(bibliography):knowledge.approvedBibliography(book,bibliography));
            answers.add("Về “"+book.getName()+"”, mình có những thông tin này:\n"+String.join("\n",facts));
            actions.add(new ChatActionDto("VIEW_PRODUCT","Xem sách",book.getProductId(),"/product/"+book.getProductId(),false));
            if(book.getQuantity()>0)actions.add(new ChatActionDto("ADD_TO_CART","Thêm giỏ",book.getProductId(),null,true));
        }
        return new ChatTool.Result(String.join("\n\n",answers),selected.size()==1?ChatIntent.PRODUCT_INFO:ChatIntent.COMPARE_BOOKS,
                selected.stream().map(this::dto).collect(Collectors.toList()),actions,"COMPLETED");
    }
    private String category(Product b){return b.getCategory()==null?"":b.getCategory().getCategoryName();}
    private Map<String,String> readingFacts(Product b) {
        return knowledge==null?data.approvedReadingFacts(b):knowledge.approvedReadingFacts(b);
    }
    private String readingGoal(Product b) {
        String text=language.normalize(b.getName()+" "+b.getDescription());
        if(text.matches("(?s).*\\b(?:ma nguon|viet ma|lap trinh|clean code)\\b.*"))
            return "viết mã dễ đọc và dễ bảo trì";
        if(text.matches("(?s).*\\b(?:khoi nghiep|startup)\\b.*"))
            return "thử nghiệm ý tưởng và xây dựng startup";
        return "";
    }
    private static String str(Map<String,Object> m,String key){return Objects.toString(m.get(key),"");}
    private static Long longValue(Object o){return o instanceof Number?((Number)o).longValue():null;}
    private static int intArg(JsonNode a,String key,int fallback){return a.hasNonNull(key)?Math.toIntExact(integerLong(a,key)):fallback;}
    private static long integerLong(JsonNode a,String key){if(!a.path(key).isIntegralNumber())throw new IllegalArgumentException("Expected integer");return a.path(key).asLong();}
    private String money(long value){return String.format(new Locale("vi","VN"),"%,dđ",value);}
    private double similarity(String a,String b) {
        Set<String> left=new HashSet<>(Arrays.asList(language.normalize(a).split("\\W+"))), right=new HashSet<>(Arrays.asList(language.normalize(b).split("\\W+")));
        left.removeAll(Set.of("sach","cuon","quyen","voi","the","and","with"));right.removeAll(Set.of("sach","cuon","quyen","voi","the","and","with"));
        return (double)left.stream().filter(right::contains).count()/Math.max(1,Math.min(left.size(),right.size()));
    }
    private ChatTool.Result missing(){return ChatTool.Result.clarify("Bạn muốn chọn cuốn nào? Bạn nhắn tên sách hoặc số thứ tự trong danh sách gần nhất nhé.");}
}
