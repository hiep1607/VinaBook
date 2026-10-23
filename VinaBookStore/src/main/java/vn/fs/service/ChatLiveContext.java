package vn.fs.service;

import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.fs.dto.chat.*;

/** Server-owned references for unambiguous, read-only follow-ups. */
final class ChatLiveContext {
    private static final ChatIntentService LANGUAGE=new ChatIntentService();
    private ChatLiveContext() {}

    static boolean containsTitle(String message,String title) {
        if(message==null || title==null || title.isBlank())return false;
        return Pattern.compile("(?<![\\p{L}\\p{N}])"+Pattern.quote(title)+"(?![\\p{L}\\p{N}])")
            .matcher(message).find();
    }

    static JsonNode plan(ChatRequestDto request,ChatDialogueMemory.State state,
            List<Map<String,Object>> facts,ObjectMapper mapper) {
        String q=LANGUAGE.normalize(request.getMessage());
        if("clarify".equals(ChatCustomerActions.reviewMode(q))
                && !ChatBookFacts.has(q,"(?:khong|dung|chua)(?: can| muon)? (?:danh gia|nhan xet|review)")) {
            // Bare "review this book" is ambiguous even if the model proposes
            // writing. Ask read vs write before opening a form or a login flow.
            return current(mapper,state,"PRODUCT_INFO","bookReviews",Map.of("reference","focus","mode","clarify"));
        }
        if(!state.pendingReviewMode.isBlank() && state.pendingReviewProductId!=null
                && "read".equals(ChatCustomerActions.shortReviewMode(q)))
            return current(mapper,state,"PRODUCT_INFO","bookReviews",Map.of("productId",state.pendingReviewProductId,"mode","read"));
        JsonNode content=contentFollowup(request,state,facts,mapper);
        if(content!=null)return content;
        if(state.shortlist.size()<2 && ChatBookFacts.has(q,"so sanh|khac nhau")
                && ChatBookFacts.has(q,"hai (?:cuon|quyen|sach) (?:do|nay|ay)|(?:cac|nhung) (?:cuon|quyen|sach) (?:do|nay|ay)")) {
            // Recover an explicitly named pair from a prior USER comparison,
            // including when the provider failed before the pair was displayed.
            // Never invent the pair from an assistant answer or old preferences.
            for(int i=state.recentDialogue.size()-1;i>=Math.max(0,state.recentDialogue.size()-2);i--) {
                String prior=LANGUAGE.normalize(state.recentDialogue.get(i).getOrDefault("user",""));
                if(!ChatBookFacts.has(prior,"so sanh|khac nhau"))continue;
                List<Map<String,Object>> named=facts.stream().filter(f->containsTitle(prior,LANGUAGE.normalize(Objects.toString(f.get("title"),""))))
                    .sorted(Comparator.comparingInt(f->prior.indexOf(LANGUAGE.normalize(Objects.toString(f.get("title"),"")))))
                    .toList();
                if(named.size()==2) {
                    state.shortlist=new ArrayList<>(named.stream().map(f->((Number)f.get("id")).longValue()).toList());
                    state.comparisonProducts=new ArrayList<>(state.shortlist);
                    return current(mapper,state,"COMPARE_BOOKS","getProducts",Map.of("reference","selection","field","table"));
                }
                break;
            }
        }
        // A selection statement identifies a book; it does not authorize buying it.
        if(ChatBookFacts.has(q,"\\bchon\\b")
                && !ChatBookFacts.has(q,"(?:khong|chua|dung)(?: muon| can)? chon|neu .*chon|gia su.*chon")
                && !ChatBookFacts.has(q,"mua|dat|gio hang|them|xoa|bo|thanh toan|danh gia|review|nhan xet")) {
            String reference=ordinal(q);
            List<Long> ordered=state.comparisonProducts.isEmpty()?state.shortlist:state.comparisonProducts;
            int index=switch(reference){case "first"->0;case "second"->1;case "third"->2;case "last"->ordered.size()-1;default->-1;};
            if(index>=0 && index<ordered.size())
                return current(mapper,state,"PRODUCT_INFO","getProducts",Map.of("reference",reference,"field","detail"));
        }
        if(!state.bookReadContext.isEmpty()
                && q.matches("(?:con|the con) (?:cuon|sach|quyen)(?: sach)? (?:kia|con lai|do|nay|ay) (?:thi sao|the nao)(?: (?:nhi|nhe|a))?[?.!]*")) {
            boolean other=ChatBookFacts.has(q,"kia|con lai");
            List<Long> ordered=state.comparisonProducts.isEmpty()?state.shortlist:state.comparisonProducts;
            if(!other || state.focus!=null && ordered.stream().anyMatch(id->!id.equals(state.focus))) {
                String reference=other?"other":"focus";
                String tool=state.bookReadContext.getOrDefault("tool","");
                if("bookReviews".equals(tool) && "read".equals(state.bookReadContext.get("mode")))
                    return current(mapper,state,"PRODUCT_INFO",tool,Map.of("reference",reference,"mode","read"));
                String field=state.bookReadContext.getOrDefault("field","");
                if("getProducts".equals(tool) && Set.of("price","author","rating","inventory","bibliography","detail","explanation").contains(field))
                    return current(mapper,state,"PRODUCT_INFO",tool,Map.of("reference",reference,"field",field));
            }
        }
        // Ellipsis changes the book while keeping the last completed read task.
        // Restrict this to an exact catalog title and a plain "what about" form.
        if(!state.bookReadContext.isEmpty()
                && q.matches("(?:con|the con) .+ (?:thi sao|the nao)(?: (?:nhi|nhe|a))?[?.!]*")) {
            List<Map<String,Object>> named=facts.stream().filter(f->containsTitle(q,LANGUAGE.normalize(Objects.toString(f.get("title"),""))))
                .toList();
            if(named.size()==1) {
                String title=Objects.toString(named.get(0).get("title"),"");
                String tool=state.bookReadContext.getOrDefault("tool","");
                if("bookReviews".equals(tool) && "read".equals(state.bookReadContext.get("mode")))
                    return current(mapper,state,"PRODUCT_INFO",tool,Map.of("title",title,"mode","read"));
                String field=state.bookReadContext.getOrDefault("field","");
                if("getProducts".equals(tool) && Set.of("price","author","rating","inventory","bibliography","detail","explanation").contains(field))
                    return current(mapper,state,"PRODUCT_INFO",tool,Map.of("title",title,"field",field));
            }
        }
        return null;
    }

    private static JsonNode contentFollowup(ChatRequestDto request,ChatDialogueMemory.State state,
            List<Map<String,Object>> facts,ObjectMapper mapper) {
        String originalQuestion=LANGUAGE.normalize(request.getMessage());
        List<Map<String,Object>> named=facts.stream()
            .filter(f->containsTitle(originalQuestion,LANGUAGE.normalize(Objects.toString(f.get("title"),"")))).toList();
        String q=originalQuestion;
        // Inspect the request outside catalog titles, whose words are not commands.
        for(Map<String,Object> book:named)q=q.replace(LANGUAGE.normalize(Objects.toString(book.get("title"),""))," ");
        q=q.replaceAll("\\b(?:chua|khong)(?: can| muon)? mua\\b"," ");
        if(ChatBookFacts.has(q,"tim|goi y|de xuat|liet ke|sach khac|cuon khac|quyen khac|them sach|them cuon|doi sang"
                +"|gia|ngan sach|duoi [0-9]|toi da [0-9]|mua|gio hang|don hang|thanh toan|giao hang|van chuyen|phi ship|doi tra"
                +"|danh gia|nhan xet|review|(?:dung|khong|chua)(?: can| muon)? (?:noi|giai thich|phan tich|di sau|cu the|chi tiet)"))return null;
        boolean elaboration=ChatBookFacts.has(q,"cu the hon|chi tiet hon|noi ky hon|noi ro hon|giai thich them|phan tich them|sau hon");
        boolean explicitBook=!named.isEmpty() || ChatConversationSupport.pluralReference(q,LANGUAGE)
            || ChatBookFacts.has(q,"(?:cuon|quyen|sach|tac pham) (?:nay|do|ay|kia|luc nay|vua|thu|dau|so|cuoi)|noi dung sach");
        boolean contentQuestion=ChatBookFacts.has(q,"noi dung|tom tat|cot truyen|nhan vat|thong diep|noi ve|ke ve|co chuong|co phan|muc luc");
        if(!elaboration && !(explicitBook && contentQuestion))return null;
        // A bare follow-up belongs to the most recent task. Do not turn a policy
        // explanation or a topic menu into a read of an older book selection.
        if(!explicitBook && (!state.topicSuggestions.isEmpty()
                || !Set.of("","DISCOVER_BOOKS","PRODUCT_INFO","COMPARE_BOOKS").contains(state.lastTask)))return null;
        if(!explicitBook && !q.matches("(?:ban |minh muon |toi muon |cho minh |cho toi )?"
                +"(?:cu the hon|chi tiet hon|noi ky hon|noi ro hon|giai thich them|phan tich them|(?:di |noi )?sau hon)"
                +"(?: (?:ve )?noi dung)?(?: (?:di|nhe|nhi|duoc khong|giup minh|giup toi))*[.!?]*"))return null;
        String reference=ordinal(q);
        if(reference.isBlank())reference=!named.isEmpty()?"focus":ChatConversationSupport.pluralReference(q,LANGUAGE)?"selection"
            :state.focus!=null || state.shortlist.size()==1 || request.getActiveProductId()!=null?"focus":"selection";
        Map<String,Object> arguments=new LinkedHashMap<>();
        arguments.put("reference",reference);arguments.put("field",ChatBookFacts.has(q,"muc luc")?"detail":"explanation");
        if(named.size()==1)arguments.put("title",named.get(0).get("title"));
        return current(mapper,state,"PRODUCT_INFO","getProducts",arguments);
    }

    private static String ordinal(String q) {
        if(ChatBookFacts.has(q,"(?:cuon|quyen|sach) (?:dau tien|dau|thu nhat|so 1)"))return "first";
        if(ChatBookFacts.has(q,"(?:cuon|quyen|sach) (?:thu hai|so 2)"))return "second";
        if(ChatBookFacts.has(q,"(?:cuon|quyen|sach) (?:thu ba|so 3)"))return "third";
        if(ChatBookFacts.has(q,"(?:cuon|quyen|sach) cuoi"))return "last";
        return "";
    }

    private static ObjectNode current(ObjectMapper mapper,ChatDialogueMemory.State state,
            String intent,String tool,Map<String,?> arguments) {
        ObjectNode plan=mapper.createObjectNode();
        plan.put("contextVersion",state.contextVersion).put("intent",intent).put("contextMode","current");
        plan.set("set",mapper.createObjectNode());plan.set("clear",mapper.createArrayNode());
        plan.put("evidence",ChatTurnContext.CURRENT_MESSAGE);plan.putNull("memoryNote");
        plan.set("calls",mapper.valueToTree(List.of(Map.of("tool",tool,"arguments",arguments))));
        return plan;
    }

    static void remember(JsonNode plan,ChatTool.Result result,ChatDialogueMemory.State state) {
        if(!"COMPLETED".equals(result.status()))return;
        for(JsonNode call:plan.path("calls")) {
            String tool=call.path("tool").asText();JsonNode args=call.path("arguments");
            if(Set.of("searchProducts","recommendBooks","findRelatedBooks").contains(tool)) {
                state.bookReadContext.clear();
            } else if(!result.products().isEmpty() && "getProducts".equals(tool)) {
                state.bookReadContext.clear();state.bookReadContext.put("tool",tool);
                state.bookReadContext.put("field",args.path("field").asText("detail"));
            } else if(!result.products().isEmpty() && "bookReviews".equals(tool)) {
                state.bookReadContext.clear();
                if("read".equals(args.path("mode").asText("read"))) {
                    state.bookReadContext.put("tool",tool);state.bookReadContext.put("mode","read");
                }
            }
        }
    }
}
