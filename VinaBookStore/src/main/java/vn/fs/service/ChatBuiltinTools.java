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
    final ProductRepository products;
    final ChatRetrievalService data;
    final ChatIntentService language;
    final ChatOrderSupportService orders;
    final ChatPolicyService policies;
    OrderCancellationService cancellation;
    BookKnowledgeService knowledge;
    vn.fs.config.CurrentUserAccess access;
    CustomerSupportService support;
    vn.fs.repository.RateRepository rates;
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
    ChatTool tool(String name, String description, Map<String,ChatTool.Parameter> parameters,
            BiFunction<JsonNode,ChatTool.Context,ChatTool.Result> handler) {
        return new ChatTool() {
            public String name() { return name; }
            public String description() { return description; }
            public Map<String,Parameter> parameters() { return parameters; }
            public Result execute(JsonNode arguments, Context context) { return handler.apply(arguments,context); }
        };
    }
    Map<String,ChatTool.Parameter> referenceParameters() {
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
            if(c.state().structuredTurnActive)return structuredDiscovery(c);
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
                    if(c.state().toolSearch.containsKey(field) && (!explicitNewTopic
                        || "excludedQuery".equals(field) && !new ChatExplicitFilters(language,data)
                            .hasRequiredLanguage(str(c.state().toolSearch,field))))
                        filters.put(field,c.state().toolSearch.get(field));
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
            if(!continuing && language.entertainmentRequest(message) && "truyện".equals(language.discoveryQuery(message)))
                filters.put("entertainmentOnly",true);
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
            List<Product> allCatalog=catalog().stream()
                .filter(b->!Boolean.TRUE.equals(filters.get("entertainmentOnly")) || entertainmentContent(b))
                .filter(b->mandatory.matches(exclusions,b)
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
    /** Structured mode consumes confirmed filters once, without re-parsing the turn. */
    private ChatTool.Result structuredDiscovery(ChatTool.Context c) {
        return structuredDiscovery(c,com.fasterxml.jackson.databind.node.MissingNode.getInstance());
    }
    private ChatTool.Result structuredDiscovery(ChatTool.Context c,JsonNode clarification) {
        return structuredDiscovery(c,clarification,catalog());
    }
    private ChatTool.Result structuredDiscovery(ChatTool.Context c,JsonNode clarification,List<Product> currentCatalog) {
        ChatDialogueMemory.State state=c.state();Map<String,Object> filters=state.toolSearch;
        boolean more="more".equals(state.contextMode)||state.excludePreviouslyShown;
        String query=str(filters,"query"),title=str(filters,"title");
        boolean noReadingPurpose=str(filters,"purpose").isBlank() || Set.of("lam qua","qua tang","mua qua","tang","tang ban","tang nguoi than")
            .contains(language.normalize(str(filters,"purpose")));
        if(query.isBlank() && title.isBlank() && noReadingPurpose
                && str(filters,"author").isBlank() && str(filters,"category").isBlank()
                && str(filters,"titleContains").isBlank()) {
            state.recommendationPending=true;
            state.preparedTopics.clear();
            Map<Long,List<String>> labels=data.topicLabels(currentCatalog);
            return ChatTopicClarification.ask(clarification,c,topic->prepareTopic(topic,c,currentCatalog,labels),data.topicSuggestionNames(currentCatalog));
        }
        if(query.isBlank())query=str(filters,"purpose");
        ChatCatalogQuery search=new ChatCatalogQuery(language,data);
        ChatReadingRequest reading=new ChatReadingRequest(language);
        ChatReadingFit fit=new ChatReadingFit(language);
        ChatExplicitFilters mandatory=new ChatExplicitFilters(language,data);
        Long min=longValue(filters.get("minPrice")),max=longValue(filters.get("maxPrice"));
        if(min!=null && Boolean.TRUE.equals(filters.get("minPriceExclusive")))min++;
        if(max!=null && Boolean.TRUE.equals(filters.get("maxPriceExclusive")))max--;
        Long budget=state.recommendationBudget;
        if(budget!=null && Boolean.TRUE.equals(filters.get("totalBudgetExclusive")))budget--;
        if(budget!=null)max=max==null?budget:Math.min(max,budget);
        if(min!=null && max!=null && min>max)return ChatTool.Result.clarify("Khoảng giá này chưa hợp lý. Bạn cho mình mức thấp nhất và cao nhất muốn tìm nhé để mình lọc đúng hơn.");
        ChatSearchCriteriaDto criteria=new ChatSearchCriteriaDto(min,max,str(filters,"category"),str(filters,"author"),str(filters,"sort"),List.of());
        if(filters.get("minRating") instanceof Number)criteria.setMinRating(((Number)filters.get("minRating")).doubleValue());
        int count=filters.get("limit") instanceof Number?((Number)filters.get("limit")).intValue():3;
        state.discoveryRequestedCount=filters.containsKey("limit")?count:null;
        String hardQuery=(query+" "+str(filters,"purpose")).trim();
        List<Product> eligible=currentCatalog.stream().filter(b->b.getQuantity()>0
            && !reading.excluded(filters,b,data)
            && mandatory.matchesFormat(str(filters,"format"),b)
            && mandatory.matches(filters.containsKey("grade")?"lớp "+filters.get("grade"):"",b)
            && mandatory.matches("children".equals(filters.get("readerGroup"))?"thiếu nhi":"",b)
            && mandatory.matchesReadingPurpose(hardQuery,b)
            && !fit.conflicts(filters,readingFacts(b))).collect(Collectors.toList());
        if(language.normalize(str(filters,"format")).equals("truyen chu"))
            eligible.removeIf(b->!reading.proseStory(b,data));
        String queryWords=language.normalize(query);
        boolean generalEntertainment=ChatBookFacts.has(language.normalize(str(filters,"purpose")+" "+query),
                "giai tri|buon chan|do chan|doi gio|thu vi|giet thoi gian")
            && title.isBlank() && str(filters,"titleContains").isBlank() && str(filters,"author").isBlank() && str(filters,"category").isBlank()
            && queryWords.replaceAll("\\b(?:giai tri|buon chan|do chan|doi gio|thu vi|giet thoi gian|phu hop|hap dan|kham pha|sach|truyen|doc|de|cho|hay|mot|cuon|noi dung|tim|va|hoac)\\b","")
                .replaceAll("\\s+","").isBlank();
        if(queryWords.equals("truyen") || generalEntertainment)
            eligible.removeIf(b->!entertainmentContent(b));
        // Generic reading goals are not lexical subject terms. Once the
        // eligible story pool is established, do not search "giải trí" as a
        // keyword (which could match "trí tuệ" or exclude real stories).
        if(generalEntertainment)query="";
        if(!title.isBlank()) {
            String wanted=language.normalize(title);
            List<Product> exact=currentCatalog.stream().filter(b->language.normalize(b.getName()).equals(wanted)).toList();
            // Prefer a complete catalog title. A shorter customer title may
            // identify several catalog editions, but extra nonexistent title
            // words must not be discarded as a fuzzy two-word match.
            Set<Long> titleIds=(exact.isEmpty()?search.named(title,currentCatalog).stream()
                .filter(b->ChatCatalogQuery.word(language.normalize(b.getName()),wanted)):exact.stream())
                .map(Product::getProductId).collect(Collectors.toSet());
            eligible.removeIf(b->!titleIds.contains(b.getProductId()));
            query="";
        }
        if(!str(filters,"titleContains").isBlank()) {
            String wanted=literalText(str(filters,"titleContains"));
            var pattern=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(wanted)+"(?![\\p{L}\\p{N}])");
            eligible.removeIf(b->!pattern.matcher(literalText(b.getName())).find());query="";
        }
        List<Product> matched=ChatPreparedTopics.take(state,currentCatalog,data);
        boolean prepared=matched!=null;
        if(!prepared)matched=levelEligible(search.search(query,criteria,eligible),state.level);
        if(!state.preparedTopicChoice.isBlank()) {
            Map<Long,List<String>> topicLabels=data.topicLabels(currentCatalog);
            matched.removeIf(book->!preparedTopicEvidence(state.preparedTopicChoice,book,topicLabels));
        }
        // Validated source quotes retain narrow user subjects even if the planner
        // summarizes them as a broad shelf. Keep them across price-only refinements.
        boolean changedTopic=java.util.stream.Stream.of("query","topics")
            .map(key->state.contextSources.get(key)).filter(Objects::nonNull)
            .anyMatch(source->Objects.equals(source.get("turn"),Objects.toString(c.request().getRequestId(),"")));
        if("new".equals(state.contextMode) || changedTopic)
            filters.put("requiredTopicQuery",state.preparedTopicChoice.isBlank()
                ?ChatDialogueInterpreter.clean(reading.hardMessage(c.request().getMessage()),1200):state.preparedTopicChoice);
        String sourceTopic=str(filters,"requiredTopicQuery");
        if(!sourceTopic.isBlank()) {
            boolean alternatives=language.alternativeTopics(query);
            matched.removeIf(b->!mandatory.matchesStorySubjects(sourceTopic,b,alternatives));
        }
        // A conjunction must satisfy each branch; generic retrieval scores cannot
        // turn explicit AND into a union of loosely related subjects.
        if(!prepared && !generalEntertainment && filters.get("topics") instanceof List<?> && !"any".equals(filters.get("topicMatch")))
            for(Object topic:(List<?>)filters.get("topics"))matched=search.search(topic.toString(),criteria,matched);
        if("exact_only".equals(str(filters,"match")) || filters.containsKey("readingNeed"))
            matched.removeIf(b->!fit.missing(filters,readingFacts(b)).isEmpty());
        boolean bestsellers="bestsellers".equals(str(filters,"selection"));
        if(bestsellers) {
            matched=matched.stream().filter(b->b.getSold()>0)
                .sorted(Comparator.comparingInt(Product::getSold).reversed().thenComparing(Product::getProductId)).collect(Collectors.toList());
        }
        state.discoverySeeds=ids(matched);
        List<Product> unseen=matched.stream().filter(b->!more || !state.shownProductIds.contains(b.getProductId())).collect(Collectors.toList());
        List<Product> ranked=reading.prioritize(unseen,filters,data);
        List<Product> exact=selectAffordable(ranked,count,budget,filters,reading);
        List<Product> nearby=new ArrayList<>();Map<Long,String> reasons=new LinkedHashMap<>();
        if("allow_related".equals(str(filters,"match")) && exact.size()<count && title.isBlank()) {
            Set<Long> excluded=new LinkedHashSet<>(state.shownProductIds);excluded.addAll(ids(matched));
            List<ChatCatalogQuery.Nearby> related=search.nearby(query,criteria,eligible,state.discoverySeeds,excluded,str(filters,"semanticQuery"));
            List<Product> relatedBooks=related.stream().map(ChatCatalogQuery.Nearby::book)
                .filter(b->!filters.containsKey("readingNeed") || fit.missing(filters,readingFacts(b)).isEmpty()).collect(Collectors.toList());
            long spent=exact.stream().mapToLong(data::effectivePrice).sum();
            nearby=selectAffordable(relatedBooks,count-exact.size(),budget==null?null:budget-spent,filters,reading);
            Set<Long> selectedIds=new HashSet<>(ids(nearby));
            related.stream().filter(r->selectedIds.contains(r.book().getProductId())).forEach(r->reasons.put(r.book().getProductId(),r.reason()));
        }
        List<Product> selected=new ArrayList<>(exact);selected.addAll(nearby);
        state.criteria=criteria;state.discoveryExact=ids(exact);state.discoveryNearby=ids(nearby);state.discoveryReasons=reasons;
        state.emptySearch=selected.isEmpty();state.displayed=ids(selected);
        if(!selected.isEmpty()) {state.shortlist=new ArrayList<>(state.displayed);state.focus=null;}
        state.missingTitle=!title.isBlank() && matched.isEmpty()?title:"";
        state.shownProductIds.addAll(state.displayed);
        filters.put("preferredProductIds",selected.stream().filter(b->reading.preferred(filters,b,data)).map(Product::getProductId).toList());
        state.searchOutcome=selected.isEmpty()?(more && !matched.isEmpty()?"exhausted":"no_match"):
            !nearby.isEmpty() || state.discoveryRequestedCount!=null && selected.size()<count
                || selected.stream().anyMatch(b->!fit.missing(filters,readingFacts(b)).isEmpty())?"partial":"matched";
        ChatTool.Result result=discoveryAnswer(exact,nearby,c,more && "exhausted".equals(state.searchOutcome),bestsellers);
        if("exhausted".equals(state.searchOutcome))
            return new ChatTool.Result("Những cuốn đáp ứng yêu cầu hiện tại đã được giới thiệu hết. Mình vẫn giữ chủ đề và các điều kiện bạn đã chọn, nên chưa có cuốn mới để đưa thêm mà không lặp lại.",result.intent(),result.products(),result.actions(),result.status());
        return result;
    }

    private String topicSubject(String text) {
        return language.normalize(text).replaceFirst("^(?:truyen|sach)(?: ve)? ","")
            .replaceFirst("^(?:kham pha|ky nang) ","");
    }
    private boolean preparedTopicEvidence(String topic,Product book,Map<Long,List<String>> labels) {
        String subject=topicSubject(topic);
        if(labels.containsKey(book.getProductId())) {
            for(String label:labels.get(book.getProductId())) {
                if(label.matches("(?iu)^(?:Độc giả|Cách trình bày|Mức đọc|Dạng nội dung|Mục đích):.*"))continue;
                String clean=BookKnowledgeService.topicSearchTerm(label).split(" / ",2)[0];
                if(subject.equals("de thu gian")) {
                    if(language.normalize(clean).matches("^(?:truyen(?: .*)?|tieu thuyet(?: .*)?|dong thoai|co tich)$"))return true;
                } else if(subject.equals(topicSubject(clean)))return true;
            }
            return false;
        }
        if(subject.equals("de thu gian"))return entertainmentContent(book);
        // A shelf such as Văn học must not prove Thiên văn học. Complete
        // subject phrases in catalog content/approved labels are required here.
        return !subject.isBlank() && new ChatExplicitFilters(language,data)
            .affirmed(language.normalize(book.getName()+". "+data.searchableText(book)),subject);
    }

    private boolean prepareTopic(String topic,ChatTool.Context original,List<Product> currentCatalog,Map<Long,List<String>> labels) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
            ChatDialogueMemory.State preview=json.readValue(json.writeValueAsBytes(original.state()),ChatDialogueMemory.State.class);
            preview.preparedTopics.clear();preview.topicSuggestions.clear();preview.structuredTurnActive=true;
            ChatRequestDto request=new ChatRequestDto();request.setMessage(topic);request.setRequestId("topic-preview");
            var plan=json.createObjectNode().put("contextMode","refine");
            plan.set("set",json.createObjectNode().set("topics",json.valueToTree(List.of(topic))));
            plan.set("clear",json.createArrayNode());plan.set("evidence",json.createObjectNode().put("topics",topic));
            new ChatTurnContext(json).apply(plan,request,preview);
            Object requestedMatch=preview.toolSearch.put("match","exact_only");
            data.beginTopicPreparation();
            structuredDiscovery(new ChatTool.Context(request,preview),com.fasterxml.jackson.databind.node.MissingNode.getInstance(),currentCatalog);
            List<Product> ranked=new ChatCatalogQuery(language,data).byIds(preview.discoverySeeds,currentCatalog);
            ChatReadingFit fit=new ChatReadingFit(language);
            ranked=ranked.stream().filter(b->fit.missing(preview.toolSearch,readingFacts(b)).isEmpty()
                && preparedTopicEvidence(topic,b,labels)).toList();
            Long budget=preview.recommendationBudget;
            if(budget!=null && Boolean.TRUE.equals(preview.toolSearch.get("totalBudgetExclusive")))budget--;
            List<String> statedTopics=ChatTopicClarification.focusedTopics(original.request().getMessage());
            if(statedTopics.isEmpty())statedTopics=ChatTopicClarification.focusedTopics(original.state().topicSuggestionContext);
            int minimum=ChatTopicClarification.relevantTopic(topic,statedTopics)?1:2;
            if(selectAffordable(ranked,minimum,budget,preview.toolSearch,new ChatReadingRequest(language)).size()<minimum)return false;
            Set<Long> ids=new HashSet<>(ids(ranked));
            if(ChatTopicClarification.requestsMoreTopics(original.request().getMessage())
                    && original.state().topicMenuPools.values().stream().anyMatch(pool->new HashSet<>(pool).equals(ids)))return false;
            if(original.state().preparedTopics.values().stream().anyMatch(p->new HashSet<>(p.rankedIds()).equals(ids)))return false;
            if(requestedMatch==null)preview.toolSearch.remove("match");else preview.toolSearch.put("match",requestedMatch);
            original.state().preparedTopics.put(topic,ChatPreparedTopics.prepare(preview.toolSearch,ranked,data));
            return true;
        } catch(java.io.IOException ex) {
            throw new IllegalStateException("Cannot prepare topic choices",ex);
        } finally {data.endTopicPreparation();}
    }

    @Bean public ChatTool getProducts() {
        Map<String,ChatTool.Parameter> p=referenceParameters();p.put("count",integer("Số sách cần lấy khi nói hai/ba cuốn",1,8));
        p.put("field",choice("Nội dung khách cần; explanation giải thích lựa chọn hoặc đào sâu nội dung; table để so sánh", "detail","price","author","rating","inventory","table","bibliography","explanation"));
        return tool("getProducts","Chi tiết hoặc so sánh sách theo tên/tham chiếu; selection để so sánh danh sách.",p,(a,c)->{
            Long previousFocus=c.state().focus;
            List<Product> selected=resolve(a,c);
            String field=a.path("field").asText("detail");
            boolean namedRead=!new ChatCatalogQuery(language,data).named(c.request().getMessage(),catalog()).isEmpty();
            if(!namedRead && c.state().structuredTurnActive && c.state().emptySearch
                    && ChatConversationSupport.repairQuestion(c.request().getMessage(),c.state(),language)) {
                field="explanation";selected=List.of();
            }
            if(!namedRead && c.state().structuredTurnActive && c.state().emptySearch && "explanation".equals(field)
                    && "selection".equals(a.path("reference").asText()))selected=List.of();
            boolean explicitComparisonPair=false;
            if("table".equals(field)) {
                List<Product> mentioned=new ChatCatalogQuery(language,data).named(c.request().getMessage(),catalog());
                if(mentioned.isEmpty() && ChatConversationSupport.pluralReference(c.request().getMessage(),language)
                        && !ChatBookFacts.has(language.normalize(c.request().getMessage()),"\\bthu (?:nhat|hai|ba|tu|[1-9])\\b|\\b(?:cuon|quyen|sach) so [1-9]\\b")) {
                    List<Product> shown=byIds(c.state().shortlist);
                    if(shown.size()>=2) {
                        Integer requested=language.bookCount(c.request().getMessage());
                        int count=requested!=null && requested>=2?Math.min(requested,shown.size()):Math.min(shown.size(),8);
                        selected=new ArrayList<>(shown.subList(0,count));explicitComparisonPair=true;
                    }
                }
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
                    String request=ChatDialogueInterpreter.clean(Objects.toString(c.state().toolSearch.get("semanticQuery"),Objects.toString(c.state().toolSearch.get("query"),"")),400);
                    String answer=c.state().explanationRound==1
                        ?"Lượt trước mình chưa tìm được sách đủ phù hợp nên chưa có danh sách để giải thích lựa chọn. Các mô tả hiện có chưa cho mình đủ căn cứ để gắn một cuốn cụ thể với nhu cầu này."
                        :"Mình làm rõ thêm vì sao chưa đề xuất được sách: phần nội dung đã được xác nhận chưa đủ để chứng minh một lựa chọn phù hợp. Việc có sách trong kho chưa cho biết nó có đáp ứng mục tiêu đọc của bạn hay không.";
                    if(Boolean.TRUE.equals(c.state().toolSearch.get("emptyAfterMore")) || "exhausted".equals(c.state().searchOutcome))
                        answer="Ý mình là những cuốn phù hợp trong điều kiện đang giữ đã được giới thiệu trước đó. Khi bạn muốn xem thêm, mình loại các cuốn ấy để tránh lặp lại; hiện chưa còn lựa chọn mới đúng yêu cầu.";
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
            if(ChatBookFacts.asksDirectFacts(c.request().getMessage(),language)
                    || ChatBookEditionQuestions.active(c.request().getMessage()))
                return productFacts(selected,field,c.request().getMessage(),c.state());
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
            c.state().toolSearch.put("emptyAfterMore",another);
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
            ChatExplicitFilters mandatory=new ChatExplicitFilters(language,data);
            boolean contentUnavailable=strictLanguage && catalog().stream().noneMatch(book->book.getQuantity()>0
                && mandatory.matches(str(c.state().toolSearch,"requiredLanguageQuery"),book));
            if(contentUnavailable) {
                next=" Phần nội dung bạn yêu cầu hiện chưa có lựa chọn phù hợp trong danh mục đang bán, nên tăng ngân sách cũng chưa giúp tìm thêm sách. Bạn có thể chọn một ngôn ngữ lập trình hoặc hướng học khác để mình kiểm tra tiếp nhé.";
            } else if(!"allow_related".equals(str(c.state().toolSearch,"match")) && !strictLanguage) {
                c.state().toolSearch.put("relatedOffer",true);
                next=" Bạn muốn mình thử mở rộng sang sách có nội dung gần với hướng đọc này không? Mình sẽ giữ các điều kiện về giá, dạng sách và phần bạn muốn loại trừ, rồi nói rõ điểm khác biệt.";
            } else next=c.state().recommendationBudget!=null?" Bạn có thể tăng tổng ngân sách hoặc giảm số cuốn cần chọn để mình tìm một nhóm sách vừa với mức bạn muốn chi.":
                c.state().toolSearch.containsKey("maxPrice") || c.state().toolSearch.containsKey("minRating")?" Bạn có muốn nới điều kiện giá hoặc đánh giá một chút không? Như vậy mình có thể kiểm tra thêm lựa chọn, nhưng vẫn giữ chủ đề bạn cần.":
                " Bạn có thể cho mình một chủ đề khác muốn tìm; mình sẽ kiểm tra theo nhu cầu mới nhé.";
            return new ChatTool.Result(answer+next,ChatIntent.DISCOVER_BOOKS,List.of(),List.of(),"COMPLETED");
        }
        c.state().toolSearch.remove("emptyAfterMore");
        c.state().discoveryEmptyRounds=0;
        String lead;
        if(requested!=null)lead="Bạn muốn "+requested+" cuốn; mình tìm được "+exact.size()+(qualified?" lựa chọn theo nội dung bạn tìm":" cuốn đúng yêu cầu")+(nearby.isEmpty()?".":" và thêm "+nearby.size()+" cuốn có chủ đề hoặc nội dung gần với hướng đó.");
        else if(another && exact.isEmpty())lead="Những cuốn đúng chủ đề mình đã giới thiệu hết theo dữ liệu hiện tại. Có thêm "+nearby.size()+" cuốn gần với hướng đọc đó để bạn cân nhắc:";
        else if(exact.isEmpty())lead="Hiện mình chưa tìm được cuốn đúng chủ đề. Có "+nearby.size()+" lựa chọn gần với hướng đọc đó để bạn cân nhắc:";
        else {
            String opening=recommendationOpening(c.state(),fit);
            lead=another?"Mình chọn thêm "+exact.size()+" cuốn đúng chủ đề, đều khác những cuốn đã giới thiệu:":
                opening+(opening.isBlank()?"Mình":"mình")+" chọn trước "+exact.size()+(qualified?" cuốn để bạn xem điểm hợp và điều còn cần cân nhắc:":" cuốn để bạn dễ cân nhắc:");
        }
        if(requested==null && !another && !qualified && nearby.isEmpty() && !c.state().preparedTopicChoice.isBlank())
            lead="Ở chủ đề “"+c.state().preparedTopicChoice+"”, bạn có thể xem "+exact.size()+" cuốn này:";
        if(requested==null && !exact.isEmpty() && !nearby.isEmpty())lead+=" Mình cũng tìm được "+nearby.size()+" cuốn gần chủ đề và để riêng bên dưới.";
        if(bestsellers)lead+=" Nhóm đúng yêu cầu được chọn theo số lượng bán cao nhất rồi sắp theo thứ tự bạn chọn.";
        if(requested!=null && total<requested)lead+=" Tổng cộng có "+total+" lựa chọn; hiện chưa đủ "+requested+" cuốn khác nhau trong các điều kiện này.";
        if(requested!=null && total<requested && !"allow_related".equals(str(c.state().toolSearch,"match"))) {
            c.state().toolSearch.put("relatedOffer",true);
            lead+=" Mình giữ đúng yêu cầu trước; nếu bạn muốn mở rộng sang nội dung gần chủ đề, mình sẽ kiểm tra thêm và ghi rõ sự khác biệt.";
        }
        Object ceiling=c.state().toolSearch.get("maxPrice");
        if(ceiling instanceof Number) {
            lead=lead.replaceFirst(":$",".");
            lead+=(total==1?" Cuốn này":" Các cuốn đều")+" nằm trong mức "+money(((Number)ceiling).longValue())+" mỗi cuốn bạn chọn.";
        }
        String preference=str(c.state().toolSearch,"preferredQuery");
        if(!preference.isBlank()) {
            int preferred=c.state().toolSearch.get("preferredProductIds") instanceof Collection<?>?((Collection<?>)c.state().toolSearch.get("preferredProductIds")).size():0;
            lead+=preferred==0?(fit.active(c.state().toolSearch) || c.state().structuredTurnActive?" Phần ưu tiên “"+preference+"” chưa được xác nhận đầy đủ cho các cuốn này.":
                " Các lựa chọn này đáp ứng điều kiện bắt buộc, nhưng hiện chưa có cuốn đáp ứng phần ưu tiên “"+preference+"”."):
                " Có "+preferred+" cuốn đáp ứng phần ưu tiên “"+preference+"”"+(preferred<total?"; các cuốn còn lại là lựa chọn thay thế trong cùng điều kiện.":".");
        }
        StringBuilder answer=new StringBuilder(lead);List<ChatActionDto> actions=new ArrayList<>();List<ChatProductDto> dtos=new ArrayList<>();
        if(qualified)answer.append("\n\nCác lựa chọn này mới đáp ứng một phần mong muốn của bạn. Mình ghi rõ phần còn thiếu thông tin dưới từng cuốn để bạn chọn dễ hơn nhé.");
        if(!exact.isEmpty()) {
            if(qualified || !nearby.isEmpty())answer.append(qualified?"\n\n**Sách theo nội dung bạn tìm:**":"\n\n**Sách đúng yêu cầu:**");
            for(Product book:exact) {
                ChatTool.Result part=discoveryBook(book,c.state(),false);
                answer.append(part.answer());
                List<String> missing=fit.missing(c.state().toolSearch,readingFacts(book));
                if(!missing.isEmpty())answer.append("\nMình chưa đủ thông tin để xác nhận ").append(String.join(", ",missing)).append(" cho cuốn này.");
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

    private boolean entertainmentContent(Product book) {
        String facts=language.normalize(data.descriptiveText(book));
        ChatExplicitFilters evidence=new ChatExplicitFilters(language,data);
        return List.of("tiểu thuyết","truyện dài","truyện ngắn","truyện tranh","đồng thoại","cổ tích","phiêu lưu","trinh thám")
            .stream().anyMatch(form->evidence.affirmed(facts,language.normalize(form)));
    }

    private String recommendationOpening(ChatDialogueMemory.State state,ChatReadingFit fit) {
        if(language.entertainmentRequest(str(state.toolSearch,"semanticQuery")))
            return "Bạn đang muốn tìm một cuốn đọc cho đỡ chán; ";
        List<String> wishes=new ArrayList<>();
        for(String need:fit.needs(state.toolSearch))wishes.add(switch(need) {
            case "gentle" -> "nhẹ nhàng";case "accessible" -> "dễ đọc";default -> "có cách áp dụng cụ thể";
        });
        if(!wishes.isEmpty())return "Với mong muốn tìm sách "+String.join(" và ",wishes)+" của bạn, ";
        String request=language.normalize(str(state.toolSearch,"requiredTopicQuery")+" "+str(state.toolSearch,"query"));
        if(language.alternativeTopics(request))return "";
        String topic=List.of("Java","Python","JavaScript","Spring Boot","tiểu thuyết","truyện ngắn","truyện tranh","trinh thám","phiêu lưu","lập trình","tình yêu","lịch sử","khởi nghiệp","giao tiếp")
            .stream().filter(term->ChatCatalogQuery.word(request,language.normalize(term))).findFirst().orElse("");
        return topic.isBlank()?"":"Với hướng đọc "+topic+" bạn chọn, ";
    }

    private ChatTool.Result discoveryBook(Product book,ChatDialogueMemory.State state,boolean nearby) {
        ChatTool.Result facts=books("",List.of(book),ChatIntent.PRODUCT_INFO,false);
        StringBuilder answer=new StringBuilder(facts.answer());
        Map<String,String> reading=readingFacts(book);
        String summary=reading.getOrDefault("summary","");
        if(nearby)answer.append("\nCuốn này là một hướng đọc mở rộng: ")
            .append(state.discoveryReasons.getOrDefault(book.getProductId(),"Nội dung chỉ đáp ứng một phần hướng đọc ban đầu."));
        if(!summary.isBlank())answer.append("\n").append(ChatDialogueInterpreter.clean(summary,550));
        else answer.append("\nMình chưa có đủ thông tin về nội dung để giải thích kỹ hơn vì sao cuốn này hợp với bạn.");
        ChatReadingFit fit=new ChatReadingFit(language);
        Set<String> usedEvidence=new HashSet<>();
        for(String need:fit.needs(state.toolSearch)) {
            String evidence=fit.evidence(need,reading);
            if(evidence.isBlank() || summary.contains(evidence) || !usedEvidence.add(evidence))continue;
            String label=switch(need) {
                case "gentle" -> "Về giọng kể: ";case "accessible" -> "Về độ dễ đọc: ";default -> "Về việc áp dụng: ";
            };
            answer.append("\n").append(label).append(ChatDialogueInterpreter.clean(evidence,550));
        }
        String audience=language.normalize(str(state.toolSearch,"recipient")+" "+str(state.toolSearch,"semanticQuery"));
        if((ChatBookFacts.has(audience,"nguoi moi|moi bat dau") || "beginner".equals(state.level))
                && reading.getOrDefault("readingLevel","").isBlank())
            answer.append("\nMình chưa đủ thông tin để xác nhận độ khó cho người mới. Bạn nên xem mục lục hoặc đọc thử vài trang để biết cách trình bày có hợp với mình không nhé.");
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
    @Bean public ChatTool prepareCheckout(){return new ChatCustomerTools(this).prepareCheckout();}
    @Bean public ChatTool prepareCartChange(){return new ChatCustomerTools(this).prepareCartChange();}
    @Bean public ChatTool getCart(){return new ChatCustomerTools(this).getCart();}
    @Bean public ChatTool prepareClearCart(){return new ChatCustomerTools(this).prepareClearCart();}
    @Bean public ChatTool prepareCancelOrder(){return new ChatCustomerTools(this).prepareCancelOrder();}
    @Bean public ChatTool prepareReorder(){return new ChatCustomerTools(this).prepareReorder();}
    @Bean public ChatTool prepareReturnRequest(){return new ChatCustomerTools(this).prepareReturnRequest();}
    @Bean public ChatTool prepareHumanSupport(){return new ChatCustomerTools(this).prepareHumanSupport();}
    @Bean public ChatTool getSupportRequests(){return new ChatCustomerTools(this).getSupportRequests();}
    @Bean public ChatTool getWishlist(){return new ChatCustomerTools(this).getWishlist();}
    @Bean public ChatTool prepareWishlistChange(){return new ChatCustomerTools(this).prepareWishlistChange();}
    @Bean public ChatTool getMyOrders(){return new ChatCustomerTools(this).getMyOrders();}
    @Bean public ChatTool getOrderDetail(){return new ChatCustomerTools(this).getOrderDetail();}
    @Bean public ChatTool getPolicy() {
        return tool("getPolicy","Chính sách vận chuyển, đổi trả, thanh toán của VinaBook.",Map.of(),
                (a,c)->new ChatTool.Result(policies.answer(c.request().getMessage(),c.state()),ChatIntent.POLICY,
                    List.of(),List.of(),c.state().returnAwaitingReason && policies.handlesReturn(c.request().getMessage(),c.state())?"NEEDS_INPUT":"COMPLETED"));
    }
    @Bean public ChatTool respond() {
        Map<String,ChatTool.Parameter> p=new LinkedHashMap<>();
        p.put("kind",choice("Loại phản hồi","greeting","clarify","unsupported","missing_product"));
        p.put("topic",choice("Nhu cầu đang làm rõ","preferences","product","comparison","gift","address"));
        p.put("suggestedTopics",text("Tối đa 6 hướng chủ đề khác nhau để khách chọn, ngăn bằng |; chỉ gợi ý, không phải điều kiện tìm sách"));
        p.put("menu",choice("Xem các hướng đang có, thêm hướng khác, hoặc nhắc lại menu đã giới thiệu","browse","more","recall"));
        p.put("suggestionSource",choice("ID lời khách làm căn cứ, lấy từ topicContextSources","current","previous1","previous2","previous3","previous4"));
        p.put("suggestionContext",text("Trích ngắn tùy chọn 8–180 ký tự trong suggestionSource; backend lấy nguyên văn nguồn nếu trích chưa đúng"));
        p.put("scope",choice("Nhu cầu mới hay bổ sung câu hỏi trước","new","continue"));
        p.put("missing",choice("Một thông tin thực sự còn thiếu; auto nếu chưa biết chủ đề","auto","subject","purpose","recipient","level","budget","value"));
        for(String field:List.of("subject","purpose","recipient"))p.put(field,text("Thông tin "+field+" khách đã nói; không suy đoán, không chứa địa chỉ/số điện thoại"));
        p.put("level",choice("Trình độ khách đã nói","beginner","experienced"));
        p.put("budget",integer("Tổng ngân sách VND khách đã nói",0,1000000000));
        return tool("respond","Chào hỏi hoặc hỏi một thông tin còn thiếu. Giữ thông tin khách đã cho để tiếp tục; không bịa chức năng.",p,(a,c)->{
            if("recall".equals(a.path("menu").asText()))return ChatTopicClarification.recall(c);
            if(Set.of("browse","more").contains(a.path("menu").asText())) {
                List<Product> currentCatalog=catalog();
                Map<Long,List<String>> labels=data.topicLabels(currentCatalog);
                return ChatTopicClarification.ask(a,c,topic->prepareTopic(topic,c,currentCatalog,labels),data.topicSuggestionNames(currentCatalog));
            }
            if(ChatCustomerActions.keepCartUnchanged(language.normalize(c.request().getMessage())))
                return ChatTool.Result.text("Mình hiểu rồi, mình không xóa sách hay thay đổi giỏ hàng của bạn.",ChatIntent.UNKNOWN);
            if("greeting".equals(a.path("kind").asText()))return ChatTool.Result.text("Chào bạn! Mình có thể cùng bạn chọn sách, so sánh các cuốn đang cân nhắc hoặc hỗ trợ chuyện mua hàng. Bạn đang muốn tìm một cuốn để đọc, làm quà hay cần xem lại đơn hàng?",ChatIntent.SMALL_TALK);
            if("unsupported".equals(a.path("kind").asText()))return ChatTool.Result.clarify("Mình chưa có chức năng thực hiện yêu cầu này trong chat. Bạn nói thêm mục đích nhé; mình có thể hỗ trợ phần liên quan như tìm sách, giải đáp chính sách hoặc tra cứu đơn.");
            if("missing_product".equals(a.path("kind").asText()))return ChatTool.Result.clarify(c.state().missingTitle.isBlank()
                ?"Mình chưa xác định được cuốn sách cần kiểm tra. Bạn gửi lại tên sách hoặc tác giả nhé, để mình tìm đúng cuốn trước khi trả lời về giá hay nội dung."
                :"Mình vẫn chưa tìm thấy “"+c.state().missingTitle+"” trong danh mục sách hiện tại. Mình chưa có lịch sử ẩn/xóa sản phẩm để xác minh lần bạn thấy trước đó và không thể đoán giá khi chưa xác định được sách.");
            if(c.state().structuredTurnActive && c.state().emptySearch
                    && (ChatConversationSupport.explanation(c.request().getMessage(),language)
                        || ChatConversationSupport.repairQuestion(c.request().getMessage(),c.state(),language))) {
                var explain=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                    .put("field","explanation").put("reference","selection");
                return getProducts().execute(explain,c);
            }
            if(c.state().structuredTurnActive && Set.of("","preferences","gift").contains(a.path("topic").asText()))
                return structuredDiscovery(c,a);
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
    ChatTool.Result missing(ChatTool.Context c,List<Product> candidates) {
        if(candidates.size()>1) {
            c.state().displayed=ids(candidates);c.state().shortlist=ids(candidates);c.state().focus=null;
            return new ChatTool.Result("Mình tìm thấy nhiều cuốn khớp yêu cầu:\n• "+candidates.stream().map(Product::getName).collect(Collectors.joining("\n• "))
                +"\nBạn chọn tên sách hoặc số thứ tự nào? Mình cần bạn chọn rõ để kiểm tra hoặc đặt đúng cuốn.",ChatIntent.PRODUCT_INFO,candidates.stream().map(this::dto).collect(Collectors.toList()),List.of(),"NEEDS_INPUT");
        }
        return ChatTool.Result.clarify(clarification("product",c));
    }
    List<Product> catalog(){return data.catalog(products::findByStatusTrue).stream().filter(p->Boolean.TRUE.equals(p.getStatus())).collect(Collectors.toList());}
    List<Product> byIds(List<Long> ids){return new ChatCatalogQuery(language,data).byIds(ids,catalog());}
    List<Long> ids(List<Product> books){return books.stream().map(Product::getProductId).collect(Collectors.toList());}
    List<Product> resolve(JsonNode a,ChatTool.Context c) {
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
        if((explicitReference.isBlank() || "focus".equals(explicitReference)) && ChatConversationSupport.pluralReference(c.request().getMessage(),language))explicitReference="selection";
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
    ChatTool.Result books(String intro,List<Product> books,ChatIntent intent,boolean detail) {
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
            if("write".equals(mode) && c.state().pendingReviewMode.isBlank()
                    && "clarify".equals(ChatCustomerActions.reviewMode(question)))mode="clarify";
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
    ChatProductDto dto(Product b){return new ChatProductDto(b.getProductId(),b.getName(),b.getAuthor(),category(b),data.effectivePrice(b),b.getPrice()==null?0:b.getPrice(),b.getDiscount(),b.getQuantity(),b.getImage());}
    String description(Product b){return readingFacts(b).getOrDefault("summary",Objects.toString(b.getDescription(),""));}
    private ChatTool.Result explainSelection(List<Product> selected,ChatTool.Context c){return new ChatBookPresenter(this).explainSelection(selected,c);}
    private ChatTool.Result productFacts(List<Product> selected,String field,String question,ChatDialogueMemory.State state){return new ChatBookPresenter(this).productFacts(selected,field,question,state);}
    String category(Product b){return b.getCategory()==null?"":b.getCategory().getCategoryName();}
    Map<String,String> readingFacts(Product b) {
        return knowledge==null?data.approvedReadingFacts(b):knowledge.approvedReadingFacts(b);
    }
    String readingGoal(Product b) {
        String text=language.normalize(b.getName()+" "+b.getDescription());
        if(text.matches("(?s).*\\b(?:ma nguon|viet ma|lap trinh|clean code)\\b.*"))
            return "viết mã dễ đọc và dễ bảo trì";
        if(text.matches("(?s).*\\b(?:khoi nghiep|startup)\\b.*"))
            return "thử nghiệm ý tưởng và xây dựng startup";
        return "";
    }
    static String str(Map<String,Object> m,String key){return Objects.toString(m.get(key),"");}
    static Long longValue(Object o){return o instanceof Number?((Number)o).longValue():null;}
    static int intArg(JsonNode a,String key,int fallback){return a.hasNonNull(key)?Math.toIntExact(integerLong(a,key)):fallback;}
    static long integerLong(JsonNode a,String key){if(!a.path(key).isIntegralNumber())throw new IllegalArgumentException("Expected integer");return a.path(key).asLong();}
    String money(long value){return String.format(new Locale("vi","VN"),"%,dđ",value);}
    double similarity(String a,String b) {
        Set<String> left=new HashSet<>(Arrays.asList(language.normalize(a).split("\\W+"))), right=new HashSet<>(Arrays.asList(language.normalize(b).split("\\W+")));
        left.removeAll(Set.of("sach","cuon","quyen","voi","the","and","with"));right.removeAll(Set.of("sach","cuon","quyen","voi","the","and","with"));
        return (double)left.stream().filter(right::contains).count()/Math.max(1,Math.min(left.size(),right.size()));
    }
    ChatTool.Result missing(){return ChatTool.Result.clarify("Bạn muốn chọn cuốn nào? Bạn nhắn tên sách hoặc số thứ tự trong danh sách gần nhất nhé.");}
}
