package vn.fs.service;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

/** Understand -> validate/merge -> execute backend capabilities -> grounded narration. */
@Service
public class ChatStructuredPipeline {
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
    public ChatStructuredPipeline(AiGateway ai,ObjectMapper mapper,ChatTurnUnderstanding understanding,
            ChatToolRegistry registry,ChatBookNarrator narrator,ChatAnalyticsService analytics,
            ProductRepository products,ChatRetrievalService data) {
        this.ai=ai;this.mapper=mapper;this.understanding=understanding;this.registry=registry;
        this.narrator=narrator;this.analytics=analytics;this.products=products;this.data=data;
    }

    ChatResponseDto reply(ChatRequestDto request,ChatDialogueMemory.State state) {
        long started=System.currentTimeMillis();ai.resetModelUsed();
        ai.beginTurnBudget(45);
        String stage="execution",tool="unresolved",status="FAILED";
        Map<String,Long> timings=new LinkedHashMap<>();long stageStarted=System.nanoTime();
        String failureStage="",preparedTopicStatus="";
        ChatTool.Result result;boolean fallback=false;
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
            List<Product> catalog=products.findByStatusTrue();
            if(!working.initialized) {
                Set<Long> active=new HashSet<>();catalog.forEach(p->active.add(p.getProductId()));
                working.displayed=new ArrayList<>(request.getContextProductIds().stream().filter(active::contains).distinct().toList());
                working.shortlist=new ArrayList<>(working.displayed);working.initialized=true;
            }
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="understanding";stageStarted=System.nanoTime();
            data.beginTurnRead(catalog);
            JsonNode plan=understanding.understand(request,working,facts(request,working,catalog),
                ChatTopicClarification.choice(request.getMessage(),working).isBlank()
                    ?data.topicSuggestionNames(catalog):List.of());
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="execution";stageStarted=System.nanoTime();
            new ChatTurnContext(mapper).apply(plan,request,working);
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
            result=combine(results);tool=String.join(",",executed);status=result.status();
            preparedTopicStatus=working.preparedTopicStatus;
            timings.merge(stage,(System.nanoTime()-stageStarted)/1_000_000L,Long::sum);
            stage="narration";stageStarted=System.nanoTime();
            if(result.intent()==ChatIntent.DISCOVER_BOOKS && !"hit".equals(working.preparedTopicStatus))
                result=narrator.present(result,request.getMessage(),working);
            else if(Set.of(ChatIntent.PRODUCT_INFO,ChatIntent.COMPARE_BOOKS).contains(result.intent())
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
            working.contextVersion=baseVersion+1;working.lastTask=result.intent().name();
            ChatLiveContext.remember(plan,result,working);
            working.pendingQuestion="NEEDS_INPUT".equals(status)?result.answer():"";
            result.products().forEach(p->working.shownProductIds.add(p.getId()));
            working.toolHistory.add(Map.of("user",request.getMessage(),"tool",tool,"status",status));
            while(working.toolHistory.size()>6)working.toolHistory.remove(0);
            mapper.readerForUpdating(state).readValue(mapper.writeValueAsBytes(working));
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
        if(fallback)execution.put("failureStage",failureStage);
        response.setExecution(execution);
        log.info("chat_structured_timing understandingMs={} executionMs={} narrationMs={} failureStage={}",
            timings.getOrDefault("understanding",0L),timings.getOrDefault("execution",0L),timings.getOrDefault("narration",0L),failureStage);
        log.info("chat_structured_dispatch tool={} status={} outcome={} fallback={} elapsedMs={}",
            tool,status,state.searchOutcome,fallback,System.currentTimeMillis()-started);
        return response;
    }

    private List<Map<String,Object>> facts(ChatRequestDto request,ChatDialogueMemory.State state,List<Product> catalog) {
        Set<Long> ids=new LinkedHashSet<>(state.displayed);ids.addAll(state.shortlist);ids.addAll(state.comparisonProducts);
        if(state.focus!=null)ids.add(state.focus);
        if(request.getActiveProductId()!=null)ids.add(request.getActiveProductId());
        if(request.getCartItems()!=null)request.getCartItems().forEach(item->ids.add(item.getProductId()));
        // A named book is grounded even before it has been displayed. Include
        // current names and recent USER mentions so a failed comparison does
        // not make its explicitly named pair invisible to the next turn.
        ChatIntentService language=new ChatIntentService();
        List<String> mentions=new ArrayList<>();mentions.add(request.getMessage());
        state.recentDialogue.stream().skip(Math.max(0,state.recentDialogue.size()-2))
            .forEach(turn->mentions.add(turn.getOrDefault("user","")));
        Set<Long> namedIds=new LinkedHashSet<>();
        for(String mention:mentions) {
            String normalized=language.normalize(mention);
            catalog.stream().filter(p->p.getName()!=null && language.normalize(p.getName()).length()>=4
                && ChatLiveContext.containsTitle(normalized,language.normalize(p.getName())))
                .forEach(p->namedIds.add(p.getProductId()));
        }
        namedIds.addAll(ids);ids.clear();ids.addAll(namedIds);
        List<Map<String,Object>> facts=new ArrayList<>();
        for(Long id:ids)catalog.stream().filter(p->id.equals(p.getProductId()) && Boolean.TRUE.equals(p.getStatus())).findFirst().ifPresent(p->{
            if(facts.size()>=16)return;
            Map<String,Object> fact=new LinkedHashMap<>();
            fact.put("id",p.getProductId());fact.put("title",p.getName());fact.put("author",p.getAuthor());
            fact.put("salePriceVnd",data.effectivePrice(p));fact.put("stockStatus",p.getQuantity()>0?"in_stock":"out_of_stock");
            facts.add(fact);
        });
        return facts;
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
