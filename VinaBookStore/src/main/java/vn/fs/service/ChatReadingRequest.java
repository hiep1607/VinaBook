package vn.fs.service;

import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.JsonNode;
import vn.fs.entity.Product;

/** Structured reading context is a view of toolSearch, which remains the durable source. */
final class ChatReadingRequest {
    private static final List<String> FIELDS=List.of("purpose","recipient","preferredQuery","excludedQuery");
    private static final Pattern PREFERENCE=Pattern.compile("\\b(?:uu tien|thich hon|neu co thi chon)\\s+([^,;.!?]+)");
    private static final Pattern EXCLUSION=Pattern.compile("\\b(?:khong lay|khong chon|loai tru|tranh sach|bo qua)\\s+([^,;.!?]+)");
    private static final Pattern TOTAL=Pattern.compile("\\b(?:tong(?: ngan sach| cong| tien| chi)?|ngan sach cho ca(?: [0-9]+ cuon)?|ca [0-9]+ cuon|cho tat ca)\\s*(?:(?:la|duoi|toi da|khong qua|khoang|tam|cho [0-9]+ cuon)\\s+)*([0-9]+(?:[.,][0-9]+)*\\s*(?:trieu|nghin|ngan|dong|vnd|lit|tr|k|d)?\\b)(?!\\s*(?:cuon|quyen))");
    private final ChatIntentService language;
    ChatReadingRequest(ChatIntentService language) { this.language=language; }

    static void parameters(Map<String,ChatTool.Parameter> parameters) {
        parameters.put("purpose",ChatTool.Parameter.text("Mục tiêu đọc khách đã nói, dùng cụm từ trong lời khách; null bỏ mục tiêu"));
        parameters.put("recipient",ChatTool.Parameter.text("Người đọc/người nhận khách đã nói; không đoán tuổi, null bỏ"));
        parameters.put("preferredQuery",ChatTool.Parameter.text("Sở thích ưu tiên, có thể không đáp ứng; tuyệt đối không gộp vào query bắt buộc; null bỏ"));
        parameters.put("excludedQuery",ChatTool.Parameter.text("Chủ đề/ngôn ngữ phải loại trừ, dùng cụm từ khách nói; null bỏ loại trừ"));
    }

    void apply(JsonNode arguments,Map<String,Object> filters,String message,boolean continuing) {
        String q=language.normalize(message);
        for(String field:FIELDS)if(arguments.has(field)) {
            if(arguments.get(field).isNull()) {
                // A planner may clear a constraint only when the current turn explicitly removes it.
                boolean clear=switch(field) {
                    case "preferredQuery" -> ChatBookFacts.has(q,"bo uu tien|khong can uu tien|khong uu tien nua");
                    case "excludedQuery" -> clearsExclusions(message);
                    case "purpose" -> ChatBookFacts.has(q,"bo muc tieu|khong can muc tieu|doi muc tieu");
                    default -> ChatBookFacts.has(q,"doi nguoi doc|khong tang|khong lam qua");
                };
                if(clear)filters.remove(field);
            } else {
                String value=arguments.path(field).asText().trim();
                if(!value.isBlank() && !referenceValue(value) && (q.contains(language.normalize(value))
                    || continuing && value.equals(filters.get(field))))filters.put(field,value);
            }
        }
        Matcher preferred=PREFERENCE.matcher(q);
        if(preferred.find()) {
            String value=cleanClause(preferred.group(1));
            if(!value.isBlank() && !referenceValue(value))filters.put("preferredQuery",value);
        }
        Matcher excluded=EXCLUSION.matcher(q);
        if(!clearsExclusions(message) && excluded.find()) {
            String value=cleanClause(excluded.group(1));
            String old=Objects.toString(filters.get("excludedQuery"),"");
            if(!value.isBlank())filters.put("excludedQuery",continuing && !old.isBlank() && !old.contains(value)?old+", "+value:value);
        }
        if(ChatBookFacts.has(q,"bo uu tien|khong can uu tien|khong uu tien nua"))filters.remove("preferredQuery");
        if(clearsExclusions(message)) {
            filters.remove("excludedQuery");filters.remove("excludedLanguages");
        }
        String query=Objects.toString(filters.get("query"),"");
        for(String field:List.of("preferredQuery","excludedQuery")) {
            String value=Objects.toString(filters.get(field),"");
            for(String term:value.split("[,;]"))if(!term.isBlank())
                query=language.normalize(query).replaceAll("\\b"+Pattern.quote(language.normalize(term.trim()))+"\\b"," ").trim();
        }
        if(filters.containsKey("preferredQuery") || filters.containsKey("excludedQuery")) {
            if(query.isBlank() && new ChatExplicitFilters(language).programmingRequest(message))query="lập trình";
            filters.put("query",query);
        }
        if(isTotalBudget(message)) {
            Matcher total=TOTAL.matcher(q);
            if(total.find() && !arguments.has("totalBudget")) {
                List<Long> amounts=ChatMoney.parse("duoi "+total.group(1));
                if(!amounts.isEmpty())filters.put("requestedTotalBudget",amounts.get(0));
            }
            if(!q.matches("(?s).*\\b(?:moi cuon|mot cuon|gia moi|gia tung)\\b.*")) {
                filters.remove("maxPrice");filters.remove("minPrice");
            }
        }
    }

    String hardMessage(String message) {
        String q=language.normalize(message);
        return PREFERENCE.matcher(q).replaceAll(" ");
    }

    String semanticMessage(String message) {
        return message.replaceAll("(?iu)\\b(?:ưu tiên|uu tien|thích hơn|thich hon|nếu có thì chọn|neu co thi chon|không lấy|khong lay|không chọn|khong chon|loại trừ|loai tru)\\s+[^,;.!?]+"," ")
            .replaceAll("\\s+"," ").trim();
    }

    boolean needsAnalysis(String message) {
        String q=language.normalize(message);
        return PREFERENCE.matcher(q).find() || isTotalBudget(message)
            || clearsTotalBudget(message) || ChatBookFacts.has(q,"bo uu tien|bo loai tru|khong can uu tien|khong loai tru nua");
    }

    boolean contextPatch(String message) {
        String q=language.normalize(message);
        return ChatBookFacts.has(q,"giu.*(?:dieu kien|yeu cau|chu de|uu tien|bo loc)|uu tien cu|bo uu tien|bo loai tru|khong can uu tien|khong loai tru nua")
            || q.matches("(?s).*\\b(?:tang|giam|doi|bo)\\b.*\\b(?:ngan sach|muc gia|gioi han gia)\\b.*");
    }

    boolean goalRequest(String message) {
        return language.normalize(message).matches("(?s).*\\b(?:muon (?:biet|hieu|hoc cach|ren|cai thien|doc cau chuyen)|gap kho khan|hay tri hoan|kho (?:doc|sua|tap trung)|nen doc gi|can suy luan)\\b.*");
    }

    boolean clearsTotalBudget(String message) {
        return ChatBookFacts.has(language.normalize(message),"bo gioi han tong ngan sach|bo tong ngan sach|khong gioi han tong ngan sach|khong can gioi han tong");
    }

    private boolean referenceValue(String value) {
        return language.normalize(value).matches("(?:cu|nhu cu|truoc|cu truoc|nhu truoc|ban dau|nhu ban dau)(?: nhe)?");
    }

    boolean isTotalBudget(String message) {
        return TOTAL.matcher(language.normalize(message)).find();
    }

    boolean clearsExclusions(String message) {
        return ChatBookFacts.has(language.normalize(message),"bo loai tru|khong loai tru nua|khong can loai tru");
    }

    boolean excluded(Map<String,Object> filters,Product book,ChatRetrievalService data) {
        String text=language.normalize(data.descriptiveText(book));
        String exclusion=Objects.toString(filters.get("excludedQuery"),"");
        for(String term:exclusion.split("[,;]")) {
            String value=language.normalize(term).replaceFirst("^(?:sach ve |sach |ve )","").trim();
            if(!value.isBlank() && ChatCatalogQuery.word(text,value))return true;
        }
        return false;
    }

    boolean preferred(Map<String,Object> filters,Product book,ChatRetrievalService data) {
        String preference=language.normalize(Objects.toString(filters.get("preferredQuery"),""))
            .replaceFirst("^(?:sach ve |sach |ve )","").trim();
        return !preference.isBlank() && ChatCatalogQuery.word(language.normalize(data.searchableText(book)),preference);
    }

    List<Product> prioritize(List<Product> books,Map<String,Object> filters,ChatRetrievalService data) {
        List<Product> result=new ArrayList<>(books);
        if(filters.containsKey("preferredQuery"))result.sort(Comparator.comparing((Product b)->!preferred(filters,b,data)));
        return result;
    }

    Map<String,Object> context(ChatDialogueMemory.State state) {
        Map<String,Object> required=new LinkedHashMap<>();
        for(String field:List.of("query","title","category","author","minPrice","maxPrice","minRating","excludedQuery","excludedLanguages","match"))
            if(state.toolSearch.containsKey(field))required.put(field,state.toolSearch.get(field));
        Map<String,Object> result=new LinkedHashMap<>();result.put("required",required);
        result.put("preferred",Objects.toString(state.toolSearch.get("preferredQuery"),""));
        result.put("purpose",Objects.toString(state.toolSearch.get("purpose"),""));
        result.put("recipient",Objects.toString(state.toolSearch.get("recipient"),""));
        result.put("readingLevel",state.level);result.put("totalBudget",state.recommendationBudget);
        result.put("requestedCount",state.discoveryRequestedCount);result.put("relatedSourceId",state.toolSearch.get("relatedSourceId"));
        return result;
    }

    private String cleanClause(String value) {
        return value.split("\\b(?:nhung|con|tong|gia moi|moi cuon|duoi|tren|toi da|chi lay|khong lay|khong chon)\\b",2)[0]
            .replaceAll("\\b(?:nhe|thoi|nua)\\b","").replaceAll("\\s+"," ").trim();
    }
}
