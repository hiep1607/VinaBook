package vn.fs.service;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;

/** Conservative provider-outage recovery through the existing validated tools. */
final class ChatBasicPlan {
    static JsonNode create(ChatRequestDto request,ChatDialogueMemory.State state,List<Product> catalog,ObjectMapper mapper) {
        ChatIntentService language=new ChatIntentService();String q=language.normalize(request.getMessage());
        var criteria=language.criteria(request.getMessage(),catalog);
        ObjectNode plan=mapper.createObjectNode();
        plan.put("contextVersion",state.contextVersion).put("intent","DISCOVER_BOOKS").put("contextMode","refine");
        ObjectNode patch=mapper.createObjectNode();plan.set("set",patch);plan.set("clear",mapper.createArrayNode());
        plan.put("evidence",ChatTurnContext.CURRENT_MESSAGE);plan.putNull("memoryNote");
        ChatIntent intent=language.classify(request.getMessage());
        ChatCatalogQuery search=new ChatCatalogQuery(language,new ChatRetrievalService(language));
        List<Product> named=search.named(request.getMessage(),catalog);
        String lookup=search.quoted(request.getMessage());
        if(lookup.isBlank())lookup=search.explicitTitleLookup(request.getMessage());
        boolean explanation=ChatConversationSupport.explanation(request.getMessage(),language);
        if(intent==ChatIntent.PRODUCT_INFO || explanation || intent==ChatIntent.COMPARE_BOOKS
                || !lookup.isBlank() || !named.isEmpty() && !language.asksForBookSuggestions(request.getMessage())) {
            plan.put("intent",intent==ChatIntent.COMPARE_BOOKS?"COMPARE_BOOKS":"PRODUCT_INFO").put("contextMode","current");
            Map<String,Object> args=new LinkedHashMap<>();
            args.put("field",intent==ChatIntent.COMPARE_BOOKS?"table":explanation?"explanation":"detail");
            if(!lookup.isBlank())args.put("title",lookup);
            if(ChatConversationSupport.pluralReference(request.getMessage(),language))args.put("reference","selection");
            plan.set("calls",mapper.valueToTree(List.of(Map.of("tool","getProducts","arguments",args))));
            return plan;
        }
        if(intent==ChatIntent.SMALL_TALK) {
            plan.put("intent","SMALL_TALK").put("contextMode","current");
            plan.set("calls",mapper.valueToTree(List.of(Map.of("tool","respond","arguments",Map.of("kind","greeting")))));
            return plan;
        }
        ChatReadingRequest reading=new ChatReadingRequest(language);
        // Existing validators can enforce explicit exclusions. Other nuanced conditions
        // stay uncommitted and expose the ordinary retry flow instead of erasing intent.
        String residual=language.normalize(reading.semanticMessage(request.getMessage())).replaceAll("\\bkhong qua\\b","");
        boolean complex=ChatBookFacts.has(residual,"khong|loai tru|ngoai tru|bat buoc|phai co|chi lay|nguoi moi|lop [0-9]|tuoi");
        if(complex)throw new IllegalStateException("Provider unavailable for detailed reading constraints");
        List<String> topics=criteria.getTopics().stream().filter(t->!"lam qua".equals(language.normalize(t))).toList();
        String statedNeed=new ChatRecommendationIntake(language,new ChatRetrievalService(language)).need(request.getMessage(),catalog,false);
        boolean refinement=reading.contextPatch(request.getMessage()) || reading.budgetPatch(request.getMessage())
            || language.wantsAnotherBook(request.getMessage()) || ChatConversationSupport.moreBooks(request.getMessage(),state,language);
        boolean anchored=!topics.isEmpty() || !criteria.getCategory().isBlank() || !criteria.getAuthor().isBlank() || !statedNeed.isBlank()
            || refinement && state.toolSearch.values().stream().anyMatch(value->value!=null && !value.toString().isBlank());
        if(!anchored) {
            throw new IllegalStateException("Provider unavailable for an unanchored request");
        }
        if(reading.explicitNewTopic(request.getMessage()))plan.put("contextMode","new");
        constraints(request,catalog,patch,mapper);
        if(!topics.isEmpty())patch.set("topics",mapper.valueToTree(topics));
        else if(!statedNeed.isBlank())patch.put("query",reading.semanticMessage(statedNeed));
        if(!criteria.getCategory().isBlank())patch.put("category",criteria.getCategory());
        if(!criteria.getAuthor().isBlank())patch.put("author",criteria.getAuthor());
        if(language.wantsAnotherBook(request.getMessage()))plan.put("contextMode","more");
        plan.set("calls",mapper.valueToTree(List.of(Map.of("tool","recommendBooks","arguments",Map.of()))));
        return plan;
    }
    static void constraints(ChatRequestDto request,List<Product> catalog,ObjectNode patch,ObjectMapper mapper) {
        ChatIntentService language=new ChatIntentService();String q=language.normalize(request.getMessage());
        var criteria=language.criteria(request.getMessage(),catalog);var reading=new ChatReadingRequest(language);
        Map<String,Object> parsed=new LinkedHashMap<>();reading.apply(mapper.createObjectNode(),parsed,request.getMessage(),false);
        if(reading.isTotalBudget(request.getMessage())) {
            if(parsed.get("requestedTotalBudget") instanceof Number amount)patch.put("totalBudget",amount.longValue());
            if(ChatBookFacts.has(q,"duoi") && !ChatBookFacts.has(q,"khong qua|toi da"))patch.put("totalBudgetExclusive",true);
        } else {
            if(criteria.getMinPrice()!=null){patch.put("minPrice",criteria.getMinPrice());if(ChatBookFacts.has(q,"tren"))patch.put("minPriceExclusive",true);}
            if(criteria.getMaxPrice()!=null){patch.put("maxPrice",criteria.getMaxPrice());if(ChatBookFacts.has(q,"duoi"))patch.put("maxPriceExclusive",true);}
        }
        for(String key:List.of("excludedQuery","preferredQuery"))if(parsed.get(key) instanceof String value && !value.isBlank())patch.put(key,value);
        if(criteria.getMinRating()!=null)patch.put("minRating",criteria.getMinRating());
        if(!criteria.getSort().isBlank())patch.put("sort",criteria.getSort());
        Integer count=language.bookCount(request.getMessage());if(count!=null && count>0 && count<=1000)patch.put("limit",count);
    }
}
