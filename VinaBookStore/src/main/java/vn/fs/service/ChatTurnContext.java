package vn.fs.service;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import vn.fs.dto.chat.*;

/** Validated user evidence updates the same filters used by retrieval and narration. */
final class ChatTurnContext {
    static final String CURRENT_MESSAGE="current_message";
    static final Set<String> TEXT = Set.of("query","purpose","recipient","preferredQuery","excludedQuery",
        "format","category","author","title","titleContains","sort","selection","match","level","readingNeed","readerGroup");
    static final Set<String> MONEY = Set.of("minPrice","maxPrice","totalBudget");
    static final Set<String> FLAGS = Set.of("minPriceExclusive","maxPriceExclusive","totalBudgetExclusive");
    static final Set<String> FIELDS;
    static {
        Set<String> fields=new LinkedHashSet<>(TEXT);fields.addAll(MONEY);fields.addAll(FLAGS);
        fields.addAll(Set.of("topics","topicMatch","limit","minRating","readerAge","grade"));FIELDS=Collections.unmodifiableSet(fields);
    }
    private static final Map<String,List<String>> ENUMS=Map.of(
        "topicMatch",List.of("any","all"),"sort",List.of("price-asc","price-desc","rating","best","new","sale"),
        "selection",List.of("all","bestsellers"),"match",List.of("exact_only","allow_related"),
        "level",List.of("beginner","experienced"),"readingNeed",List.of("gentle","accessible","practical"),
        "readerGroup",List.of("children","adult"));
    private final ObjectMapper mapper;
    private final ChatIntentService language=new ChatIntentService();
    ChatTurnContext(ObjectMapper mapper) { this.mapper=mapper; }

    ObjectNode schema() {
        ObjectNode fields=mapper.createObjectNode();
        for(String key:FIELDS) {
            ObjectNode spec=mapper.createObjectNode();
            if(TEXT.contains(key) || "topicMatch".equals(key))spec.put("type","string");
            else if(MONEY.contains(key) || Set.of("limit","readerAge","grade").contains(key))spec.put("type","integer");
            else if(FLAGS.contains(key))spec.put("type","boolean");
            else if("minRating".equals(key))spec.put("type","number");
            else spec.put("type","array").set("items",mapper.createObjectNode().put("type","string"));
            if(ENUMS.containsKey(key))spec.set("enum",mapper.valueToTree(ENUMS.get(key)));
            if(MONEY.contains(key)) {spec.put("minimum",0);spec.put("maximum",1_000_000_000);}
            if("limit".equals(key)) {spec.put("minimum",1);spec.put("maximum",1000);}
            if("minRating".equals(key)) {spec.put("minimum",0);spec.put("maximum",5);}
            if("readerAge".equals(key)) {spec.put("minimum",1);spec.put("maximum",120);}
            if("grade".equals(key)) {spec.put("minimum",1);spec.put("maximum",12);}
            // Fixed schemas may emit every property. Null explicitly means
            // "not mentioned"; only clear is allowed to remove a saved value.
            String type=spec.path("type").asText();
            spec.set("type",mapper.valueToTree(List.of(type,"null")));
            if(spec.has("enum"))((ArrayNode)spec.get("enum")).addNull();
            fields.set(key,spec);
        }
        ObjectNode object=mapper.createObjectNode().put("type","object").put("additionalProperties",false);
        object.set("properties",fields);return object;
    }

    void validate(JsonNode root,String message,long version) {
        if(!root.path("contextVersion").isIntegralNumber() || root.path("contextVersion").asLong()!=version)
            throw new IllegalArgumentException("Stale contextVersion");
        String mode=root.path("contextMode").asText();
        if(!Set.of("new","refine","more","current").contains(mode))throw new IllegalArgumentException("Invalid contextMode");
        JsonNode set=root.path("set"), clear=root.path("clear"), evidence=root.path("evidence");
        if(!set.isObject() || !clear.isArray() || !evidence.isObject())throw new IllegalArgumentException("Invalid context patch");
        Set<String> changed=new HashSet<>();
        set.fields().forEachRemaining(entry->{
            String field=entry.getKey();JsonNode value=entry.getValue();
            if(!FIELDS.contains(field) || value.isNull())throw new IllegalArgumentException("Unknown/null context field; use clear");
            if(!changed.add(field))throw new IllegalArgumentException("Duplicate field");
            String quote=quote(evidence,field,message);
            if(TEXT.contains(field) || "topicMatch".equals(field)) {
                if(!value.isTextual() || value.asText().isBlank() || value.asText().length()>240)
                    throw new IllegalArgumentException("Invalid context text");
                if(ENUMS.containsKey(field) && !ENUMS.get(field).contains(value.asText()))
                    throw new IllegalArgumentException("Invalid context enum");
            } else if(MONEY.contains(field)) {
                if(!value.isIntegralNumber() || value.asLong()<0 || value.asLong()>1_000_000_000L
                    || !ChatMoney.parse(language.normalize(quote)).contains(value.asLong()))throw new IllegalArgumentException("Price not supported by user evidence");
            } else if(FLAGS.contains(field)) {
                if(!value.isBoolean())throw new IllegalArgumentException("Invalid price bound");
            } else if("limit".equals(field)) {
                if(!value.isIntegralNumber() || value.asInt()<1 || value.asInt()>1000
                    || !Objects.equals(language.bookCount(quote),value.asInt()))
                    throw new IllegalArgumentException("Book count not supported by user evidence");
            } else if("minRating".equals(field)) {
                if(!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble()<0 || value.asDouble()>5)
                    throw new IllegalArgumentException("Invalid rating");
                String number=value.asText().replaceFirst("\\.0$","");
                if(!language.normalize(quote).replace(',','.').contains(number))
                    throw new IllegalArgumentException("Rating not supported by evidence");
            } else if(Set.of("readerAge","grade").contains(field)) {
                int ceiling="grade".equals(field)?12:120;
                if(!value.isIntegralNumber() || value.asInt()<1 || value.asInt()>ceiling
                    || !quote.matches("(?s).*\\b"+value.asInt()+"\\b.*"))
                    throw new IllegalArgumentException("Reader age/grade not supported by evidence");
            } else if("topics".equals(field)) {
                if(!value.isArray() || value.isEmpty() || value.size()>8)throw new IllegalArgumentException("Invalid topics");
                for(JsonNode topic:value)if(!topic.isTextual() || topic.asText().isBlank() || topic.asText().length()>100)
                    throw new IllegalArgumentException("Invalid topic");
            }
        });
        for(JsonNode field:clear) {
            if(!field.isTextual() || !FIELDS.contains(field.asText()) || !changed.add(field.asText()))
                throw new IllegalArgumentException("Invalid clear field");
            quote(evidence,field.asText(),message);
        }
        if(("current".equals(mode) || "more".equals(mode)) && !changed.isEmpty()
            && !("more".equals(mode) && changed.equals(Set.of("limit"))))
            throw new IllegalArgumentException("Use refine to change constraints");
        if("new".equals(mode) && !"DISCOVER_BOOKS".equals(root.path("intent").asText()))
            throw new IllegalArgumentException("New reading context requires discovery intent");
        if(set.has("topics") && set.has("query"))throw new IllegalArgumentException("Use topics OR query, not competing fields");
        JsonNode note=root.path("memoryNote");
        if(!note.isNull() && !note.isMissingNode()) {
            try {
                if(!note.isObject() || note.size()!=2 || !note.path("text").isTextual()
                    || note.path("text").asText().isBlank() || note.path("text").asText().length()>500)
                    throw new IllegalArgumentException("Invalid memory note");
                quote(note,"evidence",message);
            } catch(IllegalArgumentException invalidNote) {
                // Optional prose memory is not an execution precondition.
                // Discard an ungrounded note; never weaken set/clear validation.
                ((ObjectNode)root).putNull("memoryNote");
            }
        }
    }

    void omitUnmentioned(JsonNode root) {
        JsonNode set=root.path("set");
        if(!set.isObject())return;
        List<String> absent=new ArrayList<>();
        set.fields().forEachRemaining(entry->{
            if(!FIELDS.contains(entry.getKey()))throw new IllegalArgumentException("Unknown context field");
            if(entry.getValue().isNull() || entry.getValue().isTextual() && entry.getValue().asText().isBlank()
                    || "topics".equals(entry.getKey()) && entry.getValue().isArray() && entry.getValue().isEmpty())
                absent.add(entry.getKey());
        });
        ((ObjectNode)set).remove(absent);
    }

    // The provider points to the live input instead of copying it repeatedly.
    // Persist the actual source text, and still reject any other ungrounded quote.
    void resolveEvidence(JsonNode root,String message) {
        JsonNode evidence=root.path("evidence");
        if(evidence.isTextual() && CURRENT_MESSAGE.equals(evidence.asText())) {
            ObjectNode sources=mapper.createObjectNode();
            root.path("set").fieldNames().forEachRemaining(key->sources.put(key,message));
            if(root.path("clear").isArray())for(JsonNode key:root.path("clear"))
                if(key.isTextual())sources.put(key.asText(),message);
            ((ObjectNode)root).set("evidence",sources);evidence=sources;
        }
        if(evidence.isObject()) {
            ObjectNode quotedSources=(ObjectNode)evidence;
            List<String> references=new ArrayList<>();
            evidence.fields().forEachRemaining(entry->{
                if(entry.getValue().isTextual() && CURRENT_MESSAGE.equals(entry.getValue().asText()))references.add(entry.getKey());
            });
            references.forEach(key->quotedSources.put(key,message));
        }
        JsonNode note=root.path("memoryNote");
        if(note.isObject() && CURRENT_MESSAGE.equals(note.path("evidence").asText()))
            ((ObjectNode)note).put("evidence",message);
    }

    private String quote(JsonNode evidence,String field,String message) {
        JsonNode node=evidence.get(field);
        if(node==null || !node.isTextual() || node.asText().isBlank() || node.asText().length()>1200
            || !message.contains(node.asText()))throw new IllegalArgumentException("Evidence must quote current USER message for field "+field);
        return node.asText();
    }

    void apply(JsonNode plan,ChatRequestDto request,ChatDialogueMemory.State state) {
        String mode=plan.path("contextMode").asText();state.contextMode=mode;
        Set<String> patch=new HashSet<>();plan.path("set").fieldNames().forEachRemaining(patch::add);
        String question=language.normalize(request.getMessage());
        // Allowing related books after asking for more is still a request for
        // unseen choices. Refining the match policy must not replay the old list.
        state.excludePreviouslyShown="more".equals(mode) || "refine".equals(mode)
            && !patch.isEmpty() && Set.of("match","limit").containsAll(patch)
            && plan.path("clear").isEmpty()
            && (ChatBookFacts.has(question,"\\bthem\\b|\\bkhac\\b|\\btiep\\b")
                || "exhausted".equals(state.searchOutcome)
                    && "allow_related".equals(plan.path("set").path("match").asText()));
        Map<String,Object> filters=state.toolSearch;
        Set<String> identityFields=Set.of("query","topics","title","author","category","purpose","recipient","readerAge","grade","readerGroup");
        boolean changedIdentity=identityFields.stream().anyMatch(key->plan.path("set").has(key)
            && !Objects.equals(filters.get(key),mapper.convertValue(plan.path("set").get(key),Object.class)));
        for(JsonNode clear:plan.path("clear"))
            changedIdentity|=identityFields.contains(clear.asText()) && filters.containsKey(clear.asText());
        if("new".equals(mode)) {
            filters.clear();state.contextSources.clear();state.conversationNotes.clear();
            state.level="";state.recommendationBudget=null;state.recommendationNeed="";state.recommendationReader="";
            state.focus=null;state.comparisonProducts.clear();state.discoveryRequestedCount=null;
            state.bookReadContext.clear();
            ChatCustomerActions.clearReview(state);
            state.shortlist.clear();state.displayed.clear();
            state.discoverySeeds.clear();state.discoveryExact.clear();state.discoveryNearby.clear();
            state.searchOutcome="";state.pendingQuestion="";state.clarificationFacts.clear();state.topicSuggestions.clear();state.preparedTopics.clear();
            state.topicSuggestionContext="";
            state.topicMenus.clear();state.topicMenuPools.clear();state.topicMenuContext="";
        }
        for(JsonNode field:plan.path("clear")) {
            String key=field.asText();filters.remove(key);state.contextSources.remove(key);
            if(Set.of("query","topics","topicMatch","format").contains(key))filters.remove("requiredTopicQuery");
            if("topics".equals(key))filters.remove("query");
            if("query".equals(key)) {filters.remove("topics");filters.remove("topicMatch");}
            if("minPrice".equals(key))filters.remove("minPriceExclusive");
            if("maxPrice".equals(key))filters.remove("maxPriceExclusive");
            if("totalBudget".equals(key))filters.remove("totalBudgetExclusive");
            if("readingNeed".equals(key))filters.remove("readingNeeds");
        }
        plan.path("set").fields().forEachRemaining(entry->{
            String key=entry.getKey();
            if("query".equals(key)) {
                filters.remove("topics");filters.remove("topicMatch");
                state.contextSources.remove("topics");state.contextSources.remove("topicMatch");
            }
            if("topics".equals(key))state.contextSources.remove("query");
            if("minPrice".equals(key) && !plan.path("set").has("minPriceExclusive"))filters.remove("minPriceExclusive");
            if("maxPrice".equals(key) && !plan.path("set").has("maxPriceExclusive"))filters.remove("maxPriceExclusive");
            filters.put(key,mapper.convertValue(entry.getValue(),Object.class));
            state.contextSources.put(key,Map.of("turn",Objects.toString(request.getRequestId(),""),
                "quote",plan.path("evidence").path(key).asText()));
        });
        if(changedIdentity) {
            if(java.util.stream.Stream.of("recipient","readerAge","grade","readerGroup","purpose")
                    .anyMatch(key->plan.path("set").has(key) || java.util.stream.StreamSupport.stream(plan.path("clear").spliterator(),false)
                        .anyMatch(value->key.equals(value.asText())))) {
                state.topicMenus.clear();state.topicMenuPools.clear();state.topicMenuContext="";
            }
            state.topicSuggestions.clear();
            state.topicSuggestionContext="";
            if(state.preparedTopicChoice.isBlank())state.preparedTopics.clear();
        }
        if(filters.get("topics") instanceof List<?>) {
            List<?> topics=(List<?>)filters.get("topics");
            filters.put("query",topics.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(
                "any".equals(filters.get("topicMatch"))?" hoặc ":" và ")));
        }
        if(filters.containsKey("readingNeed"))filters.put("readingNeeds",List.of(filters.get("readingNeed")));
        if(filters.get("limit") instanceof Number)state.discoveryRequestedCount=((Number)filters.get("limit")).intValue();
        state.level=Objects.toString(filters.get("level"),"");
        state.recommendationBudget=filters.get("totalBudget") instanceof Number?((Number)filters.get("totalBudget")).longValue():null;
        state.recommendationNeed=Objects.toString(filters.get("query"),Objects.toString(filters.get("purpose"),""));
        if(state.recommendationNeed.isBlank())state.recommendationNeed=Objects.toString(filters.get("purpose"),"");
        state.recommendationReader=Objects.toString(filters.get("recipient"),"");
        state.recommendationPending=state.recommendationNeed.isBlank();
        filters.put("semanticQuery",Objects.toString(filters.get("query"),"")
            +(filters.containsKey("purpose")?"; mục đích: "+filters.get("purpose"):""));
        JsonNode note=plan.path("memoryNote");
        if(note.isObject())state.conversationNotes.add(Map.of("text",note.path("text").asText(),
            "evidence",note.path("evidence").asText(),"turn",Objects.toString(request.getRequestId(),"")));
        while(state.conversationNotes.size()>8)state.conversationNotes.remove(0);
    }
}
