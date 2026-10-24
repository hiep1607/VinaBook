package vn.fs.service;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

/** Interpret first; read store data only for validated capabilities, or reply without a tool. */
@Service
public class ChatStructuredPipeline implements ChatPlanner {
    private static final Logger log=LoggerFactory.getLogger(ChatStructuredPipeline.class);
    private static final Set<String> READS=Set.of("searchProducts","recommendBooks","getProducts",
        "findRelatedBooks","listCategories","getPolicy");
    private final AiGateway ai;
    private final ObjectMapper mapper;
    private final ChatTurnUnderstanding understanding;
    private final ChatToolRegistry registry;
    private final ChatBookNarrator narrator;
    private final ChatAnalyticsService analytics;
    private final ProductRepository products;
    private final ChatRetrievalService data;
    private CatalogDemandService catalogDemand;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCatalogDemand(CatalogDemandService value){catalogDemand=value;}
    private CartSyncService syncedCart;
    private vn.fs.config.CurrentUserAccess access;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setSyncedCart(CartSyncService value){syncedCart=value;}
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAccess(vn.fs.config.CurrentUserAccess value){access=value;}
    public ChatStructuredPipeline(AiGateway ai,ObjectMapper mapper,ChatTurnUnderstanding understanding,
            ChatToolRegistry registry,ChatBookNarrator narrator,ChatAnalyticsService analytics,
            ProductRepository products,ChatRetrievalService data) {
        this.ai=ai;this.mapper=mapper;this.understanding=understanding;this.registry=registry;
        this.narrator=narrator;this.analytics=analytics;this.products=products;this.data=data;
    }

    public ChatResponseDto reply(ChatRequestDto request,ChatDialogueMemory.State state) {
        long started=System.currentTimeMillis();ai.resetModelUsed();
        ai.beginTurnBudget(45);
        String stage="execution",tool="unresolved",status="FAILED";
        Map<String,Long> timings=new LinkedHashMap<>();long stageStarted=System.nanoTime();
        String failureStage="",preparedTopicStatus="";
        ChatTool.Result result;boolean fallback=false;
        boolean storeDataRead=false;
        try {
            // All work happens on a copy. Provider/DB failures cannot replace good memory.
            long baseVersion=state.contextVersion;
            ChatDialogueMemory.State working=mapper.readValue(mapper.writeValueAsBytes(state),ChatDialogueMemory.State.class);
            working.recentDialogue=new ArrayList<>(state.recentDialogue);
            working.preparedTopicChoice="";working.preparedTopicStatus="";
            if(working.recentDialogue.isEmpty()) {
                List<ChatTurnDto> history=request.getHistory();
                for(int i=Math.max(0,history.size()-12);i+1<history.size();i++)
                    if("user".equals(history.get(i).getRole()) && "assistant".equals(history.get(i+1).getRole()))
                        working.recentDialogue.add(Map.of("user",ChatDialogueInterpreter.clean(history.get(i).getContent(),1200),
                            "assistant",ChatDialogueInterpreter.clean(history.get(i+1).getContent(),1200)));
            }
            List<Product> catalog=null;
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="understanding";stageStarted=System.nanoTime();
            JsonNode plan;
            ChatTurnProgress.report("understanding");
            try {
                ai.beginStageBudget(15);
                try {plan=understanding.understand(request,working,List.of(),List.of());}
                finally {ai.endStageBudget();}
            } catch(IllegalStateException providerFailure) {
                if(Thread.currentThread().isInterrupted())throw providerFailure;
                if(new ChatIntentService().classify(request.getMessage())==ChatIntent.SMALL_TALK)
                    plan=understanding.basic(request,working,List.of());
                else {
                    catalog=products.findByStatusTrue();
                    storeDataRead=true;
                    initialize(request,working,catalog);
                    data.beginTurnRead(catalog);
                    plan=understanding.basic(request,working,catalog);
                }
                fallback=true;failureStage="understanding";
                log.info("chat_basic_recovery provider={}",ai.provider());
            }
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="execution";stageStarted=System.nanoTime();
            ChatTurnProgress.report("execution");
            boolean conversational=plan.hasNonNull("reply");
            if(!conversational) {
                boolean pureResponse=true;
                for(JsonNode call:plan.path("calls"))pureResponse&="respond".equals(call.path("tool").asText())
                    && (Set.of("greeting","unsupported","missing_product").contains(call.path("arguments").path("kind").asText())
                        || "recall".equals(call.path("arguments").path("menu").asText()));
                // AI selected discovery; this only resolves its confirmed label
                // to a server-owned preview, without choosing the task for AI.
                boolean discovery=false;
                for(JsonNode call:plan.path("calls"))discovery|=Set.of("recommendBooks","searchProducts").contains(call.path("tool").asText());
                if(discovery) {
                    String choice=ChatTopicClarification.choice(request.getMessage(),working);
                    String normalized=new ChatIntentService().normalize(choice);
                    JsonNode topics=plan.path("set").path("topics");
                    ChatIntentService language=new ChatIntentService();
                    boolean selectedTopic=topics.isArray() && topics.size()==1 && normalized.equals(language.normalize(topics.get(0).asText()));
                    boolean selectedQuery=normalized.equals(language.normalize(plan.path("set").path("query").asText()));
                    if(!choice.isBlank() && (selectedTopic || selectedQuery)) {
                        working.preparedTopicChoice=choice;
                        // A rendered topic selection refines this conversation;
                        // provider mode=new must not discard its verified pool.
                        ((com.fasterxml.jackson.databind.node.ObjectNode)plan).put("contextMode","refine");
                    }
                }
                boolean usesCart=false;
                for(JsonNode call:plan.path("calls"))usesCart|=Set.of("getCart","prepareCartChange","prepareClearCart").contains(call.path("tool").asText())
                    || "cart".equals(call.path("arguments").path("source").asText());
                if(usesCart && syncedCart!=null && access!=null && access.currentEmail()!=null)
                    request.setCartItems(syncedCart.chatItems());
                if(catalog==null && !pureResponse) {
                    catalog=products.findByStatusTrue();
                    storeDataRead=true;
                    initialize(request,working,catalog);
                    data.beginTurnRead(catalog);
                }
                new ChatTurnContext(mapper).apply(plan,request,working);
            }
            working.structuredTurnActive=true;
            working.recommendationIntakeRequired=false;
            List<ChatTool.Result> results=new ArrayList<>();List<String> executed=new ArrayList<>();
            boolean readBundle=true;
            for(JsonNode call:plan.path("calls"))readBundle &= READS.contains(call.path("tool").asText());
            for(JsonNode call:plan.path("calls")) {
                tool=call.path("tool").asText();stage="execution";
                result=registry.execute(tool,call.path("arguments"),new ChatTool.Context(request,working));
                results.add(result);executed.add(tool);status=result.status();
                if("FAILED".equals(status))throw new IllegalStateException("Capability failed");
                if(!readBundle && (!"COMPLETED".equals(status) || result.products().isEmpty()))break;
            }
            result=conversational?ChatTool.Result.text(plan.path("reply").asText().strip(),ChatIntent.valueOf(plan.path("intent").asText())):combine(results);
            tool=conversational?"conversation":String.join(",",executed);status=result.status();
            preparedTopicStatus=working.preparedTopicStatus;
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="narration";stageStarted=System.nanoTime();
            if(!fallback && result.intent()==ChatIntent.DISCOVER_BOOKS && !"hit".equals(working.preparedTopicStatus))
                result=narrator.present(result,request.getMessage(),working);
            else if(!fallback && Set.of(ChatIntent.PRODUCT_INFO,ChatIntent.COMPARE_BOOKS).contains(result.intent())
                && executed.contains("getProducts") && !result.products().isEmpty()
                && !ChatBookFacts.asksDirectFacts(request.getMessage(),new ChatIntentService())
                && !ChatBookEditionQuestions.active(request.getMessage())
                && (ChatBookApplication.requested(request.getMessage(),new ChatIntentService())
                    || ChatConversationSupport.explanation(request.getMessage(),new ChatIntentService())
                    || ChatBookFacts.has(new ChatIntentService().normalize(request.getMessage()),"noi dung|so sanh|nen chon|khac nhau|de doc")))
                result=narrator.explain(result,request.getMessage(),working);
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="commit";stageStarted=System.nanoTime();
            if(state.contextVersion!=baseVersion)throw new IllegalStateException("Context changed during turn");
            working.contextVersion=baseVersion+1;
            if(!conversational) {
                working.lastTask=result.intent().name();
                ChatLiveContext.remember(plan,result,working);
                working.pendingQuestion="NEEDS_INPUT".equals(status)?result.answer():"";
            }
            result.products().forEach(p->working.shownProductIds.add(p.getId()));
            working.toolHistory.add(Map.of("user",request.getMessage(),"tool",tool,"status",status));
            while(working.toolHistory.size()>6)working.toolHistory.remove(0);
            mapper.readerForUpdating(state).readValue(mapper.writeValueAsBytes(working));
            if(catalogDemand!=null && catalog!=null && result.products().isEmpty()
                    && Set.of(ChatIntent.PRODUCT_INFO,ChatIntent.DISCOVER_BOOKS).contains(result.intent())
                    && "COMPLETED".equals(status) && !working.missingTitle.isBlank()) {
                try {catalogDemand.observe(request.getSessionId(),request.getMessage(),working.missingTitle,catalog);}
                catch(RuntimeException unavailable) {
                    // Optional aggregate must never affect a successful customer reply.
                    log.debug("catalog_demand_unavailable type={}",unavailable.getClass().getSimpleName());
                }
            }
        } catch(Exception ex) {
            fallback=true;status="FAILED";failureStage=stage;
            log.warn("chat_structured_failure stage={} exceptionType={}",stage,ex.getClass().getSimpleName());
            String message="understanding".equals(stage)
                ?ex instanceof IllegalArgumentException
                    ?"Mình chưa xác định chắc ý bạn ở lượt này. Bạn nói rõ hơn điều muốn tìm hoặc cuốn đang nhắc tới nhé; mình vẫn giữ các yêu cầu trước đó để tiếp tục tư vấn."
                    :"Mình đang gặp trục trặc khi đọc yêu cầu này. Các yêu cầu trước đó vẫn được giữ; bạn thử gửi lại sau một chút để mình tiếp tục nhé."
                :"Mình chưa lấy được đầy đủ thông tin để trả lời lượt này. Các yêu cầu và lựa chọn trước đó vẫn được giữ; bạn thử lại sau một chút nhé.";
            result=ChatTool.Result.clarify(message);
        } finally {
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            data.endTurnRead();
            ai.endTurnBudget();
        }
        String messageId=analytics.record(request.getSessionId(),result.intent(),ai.provider(),fallback,
            result.products().size(),System.currentTimeMillis()-started);
        ChatResponseDto response=new ChatResponseDto(result.answer(),ai.provider(),ai.modelUsed(),fallback,
            result.products(),result.chips(),messageId,result.intent(),state.criteria,result.actions());
        Map<String,String> execution=new LinkedHashMap<>(Map.of("tool",tool,"status",status,"pipeline","structured-v1",
            "contextVersion",Long.toString(state.contextVersion),"outcome",state.searchOutcome));
        for(String measured:List.of("understanding","execution","narration"))
            execution.put(measured+"Ms",Long.toString(timings.getOrDefault(measured,0L)));
        if(!preparedTopicStatus.isBlank())execution.put("preparedTopic",preparedTopicStatus);
        if(fallback && "FAILED".equals(status))execution.put("failureStage",failureStage);
        else if(fallback)execution.put("recovery","basic");
        execution.put("dataAccess",storeDataRead?"tools":"none");
        if(Arrays.stream(tool.split(",")).anyMatch(name->Set.of("searchProducts","recommendBooks").contains(name)))
            execution.put("readingTopic",Objects.toString(state.toolSearch.get("query"),Objects.toString(state.toolSearch.get("purpose"),"")));
        response.setExecution(execution);
        log.info("chat_structured_timing understandingMs={} executionMs={} narrationMs={} failureStage={}",
            timings.getOrDefault("understanding",0L),timings.getOrDefault("execution",0L),timings.getOrDefault("narration",0L),failureStage);
        log.info("chat_structured_dispatch tool={} status={} outcome={} fallback={} elapsedMs={}",
            tool,status,state.searchOutcome,fallback,System.currentTimeMillis()-started);
        return response;
    }

    private void initialize(ChatRequestDto request,ChatDialogueMemory.State state,List<Product> catalog) {
        if(state.initialized)return;
        Set<Long> active=new HashSet<>();catalog.forEach(p->active.add(p.getProductId()));
        state.displayed=new ArrayList<>(request.getContextProductIds().stream().filter(active::contains).distinct().toList());
        state.shortlist=new ArrayList<>(state.displayed);state.initialized=true;
    }

    private ChatTool.Result combine(List<ChatTool.Result> results) {
        if(results.isEmpty())throw new IllegalArgumentException("Empty execution");
        String answer=results.stream().map(ChatTool.Result::answer).distinct().collect(java.util.stream.Collectors.joining("\n\n"));
        Map<Long,ChatProductDto> books=new LinkedHashMap<>();List<ChatActionDto> actions=new ArrayList<>();
        ChatIntent intent=results.get(0).intent();String status="COMPLETED";
        for(ChatTool.Result result:results) {
            result.products().forEach(p->books.putIfAbsent(p.getId(),p));actions.addAll(result.actions());
            if(!result.products().isEmpty())intent=result.intent();
            if(!"COMPLETED".equals(result.status()))status=result.status();
        }
        return new ChatTool.Result(answer,intent,new ArrayList<>(books.values()),actions,status,
            results.stream().flatMap(r->r.chips().stream()).distinct().limit(ChatTopicClarification.MAX_TOPICS).toList());
    }
}
