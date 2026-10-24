package vn.fs.service;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import vn.fs.dto.chat.ChatIntent;

/** Soft topic choices; their source is user text, never a confirmed search patch. */
final class ChatTopicClarification {
    static final int MAX_TOPICS=6;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ChatTopicClarification.class);
    private ChatTopicClarification() {}

    static ChatTool.Result ask(JsonNode args,ChatTool.Context context,java.util.function.Predicate<String> prepare) {
        return ask(args,context,prepare,List.of());
    }

    static ChatTool.Result ask(JsonNode args,ChatTool.Context context,java.util.function.Predicate<String> prepare,List<String> catalogTopics) {
        List<String> previousTopics=List.copyOf(context.state().topicSuggestions);
        String previousQuote=context.state().topicSuggestionContext;
        // Each rendered menu is freshly verified. Old previews must not make
        // their own still-valid book pools look like duplicate new options.
        context.state().preparedTopics.clear();
        String quote=args.path("suggestionContext").asText("").strip();
        String sourceId=args.path("suggestionSource").asText("");
        if(!sourceId.isBlank()) {
            String source=sources(context.request(),context.state()).stream()
                .filter(s->sourceId.equals(s.get("id"))).map(s->s.get("text")).findFirst().orElse("");
            quote=!quote.isBlank() && literal(source).contains(literal(quote))
                ?quote:ChatDialogueInterpreter.clean(source,180);
        }
        List<String> topics=topics(args.path("suggestedTopics").asText(""));
        boolean grounded=!topics.isEmpty() && grounded(quote,context);
        List<String> focused=focusedTopics(context.request().getMessage());
        if(focused.isEmpty() && grounded)focused=focusedTopics(quote);
        if(!focused.isEmpty()) {
            List<String> matching=new ArrayList<>(focused);
            for(String topic:topics)if(relevantTopic(topic,focused) && !matching.contains(topic))matching.add(topic);
            topics=matching;
            if(!grounded) {
                quote=ChatDialogueInterpreter.clean(context.request().getMessage(),180);
                grounded=grounded(quote,context);
            }
        }
        log.info("chat_topic_clarification contextual={} choices={} quoteChars={}",grounded,topics.size(),quote.length());
        boolean gift="gift".equals(args.path("topic").asText())
            || new ChatIntentService().normalize(context.request().getMessage()).matches("(?s).*\\b(?:lam qua|qua tang|tang|mua qua)\\b.*")
            || new ChatIntentService().normalize(Objects.toString(context.state().toolSearch.get("purpose"),""))
                .matches("(?s).*\\b(?:lam qua|qua tang|tang|mua qua)\\b.*");
        if(!grounded && focused.isEmpty())topics=gift
            ?List.of("Truyện để thư giãn","Kỹ năng sống","Khám phá khoa học")
            :List.of("Truyện để thư giãn","Kỹ năng giao tiếp","Khám phá khoa học");
        // Only verified topics become visible choices. Preparation runs on
        // isolated copies; these proposals never alter confirmed constraints.
        List<String> verified=new ArrayList<>();
        boolean more=requestsMoreTopics(context.request().getMessage());
        Set<String> offered=new HashSet<>();
        if(more)context.state().topicMenus.forEach(menu->menu.forEach(topic->offered.add(new ChatIntentService().normalize(topic))));
        for(String topic:topics)if(!offered.contains(new ChatIntentService().normalize(topic)) && prepare.test(topic))verified.add(topic);
        List<String> additional=new ArrayList<>(catalogTopics);
        additional.addAll(List.of("Truyện phiêu lưu","Kỹ năng sống","Khoa học","Truyện trinh thám","Tâm lý","Lập trình","Lịch sử","Kinh tế"));
        if(!grounded && focused.isEmpty() && verified.size()<MAX_TOPICS)for(String topic:additional) {
            if(verified.size()>=MAX_TOPICS)break;
            if(!topics.contains(topic) && !offered.contains(new ChatIntentService().normalize(topic)) && prepare.test(topic))verified.add(topic);
        }
        boolean reused=false;
        if(!more && verified.isEmpty() && !previousTopics.isEmpty() && !"new".equals(context.state().contextMode)
                && clarificationContinuation(context.request().getMessage())) {
            for(String topic:previousTopics)if(prepare.test(topic))verified.add(topic);
            if(!verified.isEmpty()) {
                reused=true;
                if(!previousQuote.isBlank() && grounded(previousQuote,context)) {
                    quote=previousQuote;grounded=true;
                }
            }
        }
        topics=List.copyOf(verified);
        context.state().topicSuggestions=new ArrayList<>(topics);
        context.state().topicSuggestionContext=grounded?quote:"";
        rememberMenu(context.state(),topics,grounded?quote:"");
        String opening=grounded
            ?"Bạn có nhắc “"+quote+"”. Có những hướng này để bạn cân nhắc:"
            :gift?"Người nhận quà thích chủ đề gì? Nếu chưa rõ, bạn có thể xem các hướng này:"
            :"Nếu chưa có ý tưởng, bạn có thể bắt đầu từ các hướng này:";
        if(reused)opening=(grounded?"Với chuyện “"+quote+"”, ":"")+"các hướng mình vừa gợi ý vẫn là:";
        String answer;
        if(topics.isEmpty() && more)answer="Mình chưa tìm được hướng khác đủ sách và phù hợp với các điều kiện đang giữ. Bạn có thể quay lại các chủ đề đã gợi ý hoặc cho mình biết một hướng muốn tìm riêng.";
        else if(topics.isEmpty())answer=(grounded?"Bạn có nhắc “"+quote+"”. ":"")
            +"Mình chưa tìm được vài cuốn đủ phù hợp để gợi ý sẵn một chủ đề trong các điều kiện hiện tại. "
            +(gift?"Người nhận quà muốn đọc về điều gì hoặc giải quyết vấn đề nào? ":"Bạn muốn đọc về điều gì hoặc giải quyết vấn đề nào? ")
            +"Biết rõ hơn hướng đọc sẽ giúp mình kiểm tra sách sát nhu cầu của bạn nhé.";
        else {
            if(topics.size()==1)opening=opening.replace("vài chủ đề liên quan","một chủ đề liên quan")
                .replace("vài hướng để bạn tham khảo","một hướng để bạn tham khảo")
                .replace("vài hướng:","hướng này:").replace("Có những hướng này","Có một hướng này");
            answer=(more?"Có thêm những hướng này:":opening)+numbered(topics)
                +(more?"\nBạn muốn xem sách ở hướng nào?":"\nBạn chọn tên hoặc số thứ tự nhé; cũng có thể hỏi thêm chủ đề khác.");
        }
        return new ChatTool.Result(answer,ChatIntent.UNKNOWN,List.of(),List.of(),"NEEDS_INPUT",topics);
    }

    static List<String> lastMenu(ChatDialogueMemory.State state) {
        return state.topicMenus.isEmpty()?state.topicSuggestions:state.topicMenus.get(state.topicMenus.size()-1);
    }

    static List<String> focusedTopics(String message) {
        ChatIntentService language=new ChatIntentService();String q=language.normalize(message);
        ChatExplicitFilters evidence=new ChatExplicitFilters(language);
        return language.criteria(message,List.of()).getTopics().stream()
            .filter(topic->!Set.of("lam qua","thieu nhi","giao trinh").contains(language.normalize(topic)))
            .filter(topic->evidence.affirmed(q,language.normalize(topic))).toList();
    }
    static boolean relevantTopic(String topic,List<String> focused) {
        ChatIntentService language=new ChatIntentService();String q=language.normalize(topic);
        return focused.stream().anyMatch(value->{
            String need=language.normalize(value);
            return ChatCatalogQuery.word(q,need) || ChatCatalogQuery.subjectGroups().stream()
                .anyMatch(group->group.stream().anyMatch(term->ChatCatalogQuery.word(need,term))
                    && group.stream().anyMatch(term->ChatCatalogQuery.word(q,term)));
        });
    }

    static String choice(String message,ChatDialogueMemory.State state) {
        String selected=confirmedChoice(message,state.topicSuggestions);
        if(!selected.isBlank())return selected;
        String q=new ChatIntentService().normalize(message);
        // Bare book ordinals after a book list must never select an archived topic.
        if(ChatBookFacts.has(q,"\\b(?:huong|chu de|lua chon)\\b")
            || lastMenu(state).stream().anyMatch(topic->q.equals(new ChatIntentService().normalize(topic))))
            return confirmedChoice(message,lastMenu(state));
        return "";
    }

    static boolean requestsMoreTopics(String message) {
        String q=new ChatIntentService().normalize(message);
        return ChatBookFacts.has(q,"(?:them|nhieu hon|khac)(?: cac| vai| nhieu| nhung)? (?:chu de|huong|the loai)|(?:chu de|huong|the loai)(?: nao)? (?:khac|nao nua)|con (?:chu de|huong|the loai) nao");
    }

    static String menuCommand(String message,ChatDialogueMemory.State state) {
        String q=new ChatIntentService().normalize(message);
        if(!ChatBookFacts.has(q,"\\b(?:chu de|huong|the loai)\\b"))return "";
        if(ChatBookFacts.has(q,"(?:khong|dung|chua)(?: \\S+){0,3} (?:nhac lai|ke lai|xem lai|quay lai|gioi thieu|goi y)"))return "";
        // New constraints or a book/action question must still pass through full understanding.
        if(!ChatMoney.parse(q).isEmpty() || ChatBookFacts.has(q,"\\b(?:mua ngay|them vao gio|xoa|huy|don hang|loai tru|dung lay|bo gioi han)\\b"))return "";
        if(ChatBookFacts.has(q,"nhac lai|ke lai|xem lai|quay lai|vua (?:gioi thieu|goi y)|luc nay|truoc do")
            || ChatBookFacts.has(q,"(?:nhung|cac) (?:chu de|huong|the loai).*vua (?:ke|noi)|vua (?:ke|noi).*(?:chu de|huong|the loai)"))return "recall";
        if(requestsMoreTopics(message))return state.topicMenuContext.isBlank()?"more":"";
        if(q.matches("(?s)(?:(?:minh|toi) chua biet doc gi[, ]*)?(?:(?:ban|shop) )?(?:gioi thieu|goi y|liet ke|cho (?:minh|toi) xem) (?:cac |nhung |vai |nhieu )?(?:chu de|the loai)(?: dang co| hien co| trong shop| sach)?(?: di| nhe)?[.!?]*"))return "browse";
        return "";
    }

    static boolean confirmedRecall(String message,ChatDialogueMemory.State state) {
        if(state.topicMenus.isEmpty() && state.topicSuggestions.isEmpty())return false;
        String q=new ChatIntentService().normalize(message);
        return "recall".equals(menuCommand(message,state)) && q.matches(
            "(?:(?:ban|shop|minh|toi) )?(?:nhac lai|ke lai|xem lai|quay lai) (?:cac |nhung )?(?:chu de|huong|the loai)(?: sach)?"
            +"(?: (?:dau tien|ban dau|luc nay|truoc do|vua goi y|da goi y|da gioi thieu))?"
            +"(?: (?:giup minh|giup toi|nhe|di|voi))*[.!?]*");
    }

    static ChatTool.Result recall(ChatTool.Context context) {
        var state=context.state();
        List<String> menu=lastMenu(state);
        String q=new ChatIntentService().normalize(context.request().getMessage());
        if(!state.topicMenus.isEmpty() && ChatBookFacts.has(q,"dau tien|ban dau"))menu=state.topicMenus.get(0);
        if(menu.isEmpty())return ChatTool.Result.clarify("Mình chưa gợi ý danh sách chủ đề nào trong cuộc trò chuyện này. Bạn muốn xem các hướng đang có trong cửa hàng không?");
        state.topicSuggestions=new ArrayList<>(menu);
        state.topicSuggestionContext=state.topicMenuContext;
        rememberMenu(state,menu,state.topicMenuContext);
        // Recall quotes the actual menu; catalog facts are rechecked upon selection.
        return new ChatTool.Result("Mình đã gợi ý các chủ đề này:"+numbered(menu),ChatIntent.UNKNOWN,List.of(),List.of(),"NEEDS_INPUT",menu);
    }

    static void rememberMenu(ChatDialogueMemory.State state,List<String> topics,String context) {
        state.topicMenuHistoryVersion=1;
        if(topics.isEmpty())return;
        if(state.topicMenus.isEmpty() || !lastMenu(state).equals(topics))state.topicMenus.add(new ArrayList<>(topics));
        while(state.topicMenus.size()>4)state.topicMenus.remove(0);
        Set<String> retained=new HashSet<>();state.topicMenus.forEach(retained::addAll);
        state.topicMenuPools.keySet().retainAll(retained);
        for(String topic:topics) {
            ChatPreparedTopics.Selection prepared=state.preparedTopics.get(topic);
            if(prepared!=null)state.topicMenuPools.put(topic,new ArrayList<>(prepared.rankedIds()));
        }
        state.topicMenuContext=context;
    }

    private static String numbered(List<String> topics) {
        StringBuilder text=new StringBuilder();
        for(int i=0;i<topics.size();i++)text.append("\n").append(i+1).append(". ").append(topics.get(i));
        return text.toString();
    }

    static List<Map<String,String>> sources(vn.fs.dto.chat.ChatRequestDto request,ChatDialogueMemory.State state) {
        List<Map<String,String>> sources=new ArrayList<>();
        addSource(sources,"current",request.getMessage());
        for(int i=1;i<=4 && i<=state.recentDialogue.size();i++)
            addSource(sources,"previous"+i,state.recentDialogue.get(state.recentDialogue.size()-i).getOrDefault("user",""));
        return sources;
    }

    private static boolean clarificationContinuation(String message) {
        String q=new ChatIntentService().normalize(message);
        return !hasSpecificContext(message) && ChatBookFacts.has(q,
            "chua biet|goi y.*(?:huong|chu de)|(?:huong|chu de).*(?:nao|gi)|theo.*(?:vua ke|vua noi)");
    }

    private static void addSource(List<Map<String,String>> sources,String id,String text) {
        if(text!=null && !text.isBlank() && !ChatService.privateText(text) && hasSpecificContext(text))
            sources.add(Map.of("id",id,"text",ChatDialogueInterpreter.clean(text,599)));
    }

    static String confirmedChoice(String message,List<String> options) {
        if(options==null || options.isEmpty() || options.size()>MAX_TOPICS)return "";
        ChatIntentService language=new ChatIntentService();
        String q=language.normalize(message).replaceAll("[.!?]","").strip();
        String[] clauses=q.split("[,;]",2);
        if(clauses.length==2 && (!ChatMoney.parse(clauses[1]).isEmpty() || language.bookCount(clauses[1])!=null)
                && !ChatBookFacts.has(clauses[1],"neu|gia su|thay vao|hay|doi chu de"))q=clauses[0].strip();
        q=q.replaceAll("\\bdau tien\\b","1").replaceAll("\\bthu nhat\\b","1");
        for(String option:options)if(q.equals(language.normalize(option))
            || q.matches("(?:(?:minh|toi|em) )?(?:chon|xem|lay)(?: chu de| huong)? "+java.util.regex.Pattern.quote(language.normalize(option))+"(?: nhe| di| a)?"))return option;
        var match=java.util.regex.Pattern.compile(
            "^(?:(?:minh|toi|em|t) )?(?:(?:chon|lay|xem) )?(?:(?:huong|chu de|lua chon) )?(?:(?:thu|so) )?(1|2|3|4|5|6|mot|hai|ba|tu|bon|nam|sau|nhat)(?: (?:nhe|di|a))?$").matcher(q);
        if(!match.matches())return "";
        int index=switch(match.group(1)){case "1","mot","nhat"->0;case "2","hai"->1;case "3","ba"->2;case "4","tu","bon"->3;case "5","nam"->4;default->5;};
        return index<options.size()?options.get(index):"";
    }

    private static boolean grounded(String quote,ChatTool.Context context) {
        if(quote.length()<8 || quote.length()>180 || ChatService.privateText(quote) || !hasSpecificContext(quote)
                || quote.contains("\n") || quote.contains("\r"))return false;
        List<String> users=new ArrayList<>();users.add(context.request().getMessage());
        List<Map<String,String>> turns=context.state().recentDialogue;
        for(int i=Math.max(0,turns.size()-4);i<turns.size();i++)
            users.add(turns.get(i).getOrDefault("user",""));
        return users.stream().filter(Objects::nonNull).filter(s->!ChatService.privateText(s))
            .anyMatch(s->literal(s).contains(literal(quote)));
    }

    private static boolean hasSpecificContext(String quote) {
        // A quote of "I want books but don't know what" is real user evidence,
        // but carries no context for personalized suggestions.
        String detail=new ChatIntentService().normalize(quote)
            .replaceAll("\\b(?:gioi thieu|nhac lai|liet ke|ke lai|quay lai|xem lai|danh sach|y tuong|goi y)\\b"," ")
            .replaceAll("\\b(?:nhieu|cac|dang|nhi|roi)\\b"," ")
            .replaceAll("\\b(?:cho|tang) (?:ban gai|ban trai|nguoi yeu|vo|chong|bo|me|ong|ba|anh|chi|em|con|chau|ban)(?: (?:cua )?(?:toi|minh|ay))?\\b"," ")
            .replaceAll("\\b(?:theo (?:chuyen|dieu|hoan canh)|chuyen|dieu|hoan canh)(?: (?:minh|toi|em))? (?:vua|da) (?:ke|noi|nhac)(?: (?:o tren|luc nay|truoc do))?\\b"," ")
            .replaceAll("\\b[0-9]+(?:[.,][0-9]+)*\\s*(?:trieu|nghin|ngan|dong|vnd|tr|k|d)?\\b"," ")
            .replaceAll("\\b(?:bay gio|hien tai|luc nay|cu the|gioi han|chinh xac|chu de|muc dich|huong doc|y tuong|nhu cau|yeu cau|ngu canh|bat ky|gi cung|the loai|ngan sach|dua tren|hoan canh|o tren|nhu the|vua tim)\\b"," ")
            .replaceAll("\\b(?:toi|minh|ban|em|t|muon|can|tim|mua|chon|sach|cuon|quyen|doc|de|ve|cho|mot|vai|nhung|chua|khong|ko|biet|ro|co|nao|gi|ca|hay|hoac|va|la|thi|ma|nen|voi|duoc|phu|hop|thu|goi|y|tu|van|giup|nhe|di|ra|den|dau|bat|bo|toan|bo|cu|truoc|them|lam|qua|tang|nguoi|nhan|duoi|tren|khoang|tam|toi da|toi thieu|gia|van|ay|khac|huong|ke|noi|thich|doi|sang|chuyen|vua|da|thieu|moi)\\b"," ")
            .replaceAll("[^\\p{L}\\p{N}]","");
        return !detail.isBlank();
    }

    private static List<String> topics(String raw) {
        if(raw.length()>240)return List.of();
        String[] parts=raw.split("\\|",-1);
        if(parts.length<1 || parts.length>MAX_TOPICS)return List.of();
        Map<String,String> result=new LinkedHashMap<>();
        for(String part:parts) {
            String topic=part.strip();
            if(topic.length()<3 || topic.length()>64 || ChatService.privateText(topic)
                || !topic.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ,/–-]*"))return List.of();
            result.putIfAbsent(literal(topic),topic);
        }
        return List.copyOf(result.values());
    }

    private static String literal(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").strip();
    }
}
