package vn.fs.service;

import java.util.*;
import java.util.regex.*;
import vn.fs.entity.Product;

/** Soft reading needs are retained separately from genre and assessed only against approved facts. */
final class ChatReadingFit {
    private static final Pattern AGE_RANGE=Pattern.compile("\\b(\\d{1,2})\\s*(?:[-–—]|den)\\s*(\\d{1,2})\\s*tuoi\\b");
    private final ChatIntentService language;
    ChatReadingFit(ChatIntentService language) { this.language=language; }

    void capture(Map<String,Object> filters,String message) {
        String q=language.normalize(message);
        Set<String> needs=new LinkedHashSet<>(needs(filters));
        if(positive(q,"nhe nhang|thu gian|chua lanh"))needs.add("gentle");
        if(positive(q,"de doc|de hieu|it doc|moi doc|khong doc nhieu"))needs.add("accessible");
        if(positive(q,"ap dung cu the|cach ap dung|thuc hanh|phuong phap cu the"))needs.add("practical");
        if(ChatBookFacts.has(q,"khong can.*(?:de doc|de hieu)|bo.*(?:de doc|de hieu)"))needs.remove("accessible");
        if(ChatBookFacts.has(q,"khong can.*(?:nhe nhang|thu gian)|bo.*(?:nhe nhang|thu gian)"))needs.remove("gentle");
        if(ChatBookFacts.has(q,"khong can.*(?:ap dung cu the|thuc hanh)|bo.*(?:ap dung cu the|thuc hanh)"))needs.remove("practical");
        if(!needs.isEmpty())filters.put("readingNeeds",new ArrayList<>(needs));else filters.remove("readingNeeds");
        Matcher age=Pattern.compile("\\b(\\d{1,2})\\s*tuoi\\b").matcher(q);
        if(age.find())filters.put("readerAge",Integer.valueOf(age.group(1)));
    }

    private boolean positive(String q,String phrase) {
        return ChatBookFacts.has(q,phrase) && !ChatBookFacts.has(q,"(?:khong thich|khong muon|khong can|dung chon)[^,;.!?]*(?:"+phrase+")");
    }
    Set<String> needs(Map<String,Object> filters) {
        Set<String> result=new LinkedHashSet<>();
        if(filters.get("readingNeeds") instanceof Collection<?>)
            ((Collection<?>)filters.get("readingNeeds")).forEach(n->result.add(n.toString()));
        return result;
    }
    boolean active(Map<String,Object> filters) { return !needs(filters).isEmpty() || filters.containsKey("readerAge"); }
    String source(Map<String,String> facts,String... fields) {
        return language.normalize(Arrays.stream(fields).map(f->facts.getOrDefault(f,"")).reduce("",(a,b)->a+" "+b));
    }
    boolean conflicts(Map<String,Object> filters,Map<String,String> facts) {
        if(ageConflicts(filters,facts))return true;
        String text=source(facts,"summary","readingStyle","readingLevel","audience");
        boolean crimeRequested=ChatBookFacts.has(language.normalize(Objects.toString(filters.get("query"),"")),"trinh tham|tham tu|pha an");
        if(needs(filters).contains("gentle") && (ChatBookFacts.has(text,"mafia|the gioi ngam|bi kich|mau va|va mau|dau dau|day dut|chien tranh|ket thuc.*(?:rat buon|buon)")
                || !crimeRequested && ChatBookFacts.has(text,"ly ky|vu an")))return true;
        if(needs(filters).contains("accessible") && ChatBookFacts.has(source(facts,"readingLevel"),"nang cao|chuyen sau|advanced"))return true;
        // A children's category alone does not make a book unsuitable for an adult.
        // Exclude only a description explicitly targeting children exclusively.
        if(filters.get("readerAge") instanceof Number && ((Number)filters.get("readerAge")).intValue()>=18
                && ChatBookFacts.has(text,"the he nho|danh cho tre em|danh cho thieu nhi")
                && !ChatBookFacts.has(text,"nguoi lon|moi lua tuoi|moi do tuoi"))return true;
        return false;
    }
    boolean confirmed(String need,Map<String,String> facts) {
        return switch(need) {
            case "gentle" -> confirmedEvidence(source(facts,"summary","readingStyle"),"nhe nhang|thu gian|trong treo|hon nhien|dung di")
                && !ChatBookFacts.has(source(facts,"summary","readingStyle"),"bi kich|dau dau|day dut|vu an|mafia");
            case "accessible" -> confirmedEvidence(source(facts,"summary","readingLevel","readingStyle"),"de doc|de hieu|nguoi it doc|nguoi moi doc|ngon ngu don gian|ngon ngu gian di");
            case "practical" -> confirmedEvidence(source(facts,"summary","learningGoal","readingStyle"),"bai tap|vi du cu the|tinh huong thuc te|huong dan ap dung|cac buoc");
            default -> false;
        };
    }
    private boolean confirmedEvidence(String facts,String alternatives) {
        ChatExplicitFilters evidence=new ChatExplicitFilters(language);
        return Arrays.stream(alternatives.split("\\|")).anyMatch(term->evidence.affirmed(facts,term));
    }
    boolean ageConfirmed(Map<String,Object> filters,Map<String,String> facts) {
        if(!(filters.get("readerAge") instanceof Number))return true;
        int age=((Number)filters.get("readerAge")).intValue();
        String audience=source(facts,"audience");
        if(ChatBookFacts.has(audience,"moi lua tuoi|moi do tuoi"))return true;
        if(age>=18)return ChatBookFacts.has(audience,"nguoi lon|truong thanh");
        Matcher range=AGE_RANGE.matcher(audience);
        boolean hasRange=false;
        while(range.find()) {
            hasRange=true;
            if(age>=Integer.parseInt(range.group(1)) && age<=Integer.parseInt(range.group(2)))return true;
        }
        if(hasRange)return false;
        if(ChatBookFacts.has(audience,"\\b"+age+"\\s+tuoi\\b"))return true;
        Matcher from=Pattern.compile("\\btu (\\d{1,2}) tuoi (?:tro len|trở lên)").matcher(audience);
        return from.find() && age>=Integer.parseInt(from.group(1));
    }
    boolean ageConflicts(Map<String,Object> filters,Map<String,String> facts) {
        return filters.get("readerAge") instanceof Number
            && AGE_RANGE.matcher(source(facts,"audience")).find()
            && !ageConfirmed(filters,facts);
    }
    List<String> missing(Map<String,Object> filters,Map<String,String> facts) {
        List<String> result=new ArrayList<>();
        for(String need:needs(filters))if(!confirmed(need,facts))result.add(switch(need) {
            case "gentle"->"mức độ nhẹ nhàng";case "accessible"->"độ dễ đọc";default->"cách áp dụng cụ thể";
        });
        // Adult age alone is not a reading level; do not require every novel to list an adult age.
        if(filters.get("readerAge") instanceof Number && ((Number)filters.get("readerAge")).intValue()<18 && !ageConfirmed(filters,facts))result.add("độ tuổi đọc");
        return result;
    }
    int score(Map<String,Object> filters,Map<String,String> facts) {
        int result=0;
        for(String need:needs(filters))if(confirmed(need,facts))result+=10;
        String text=source(facts,"summary","readingStyle");
        // Familiar narrative content is a tie breaker, never proof of easy reading.
        if(needs(filters).contains("accessible")) {
            if(ChatBookFacts.has(text,"tuoi tho|tinh ban|hon nhien|trong treo"))result+=2;
            if(ChatBookFacts.has(text,"luc bat|thi ca|bi kich|dai tieu thuyet|tieu thuyet tu tuong|vu an|hoc bua"))result-=2;
        }
        if(needs(filters).contains("practical") && ChatBookFacts.has(text,"phuong phap thiet thuc"))result+=2;
        if(filters.get("readerAge") instanceof Number && ((Number)filters.get("readerAge")).intValue()<18 && ageConfirmed(filters,facts))result+=10;
        return result;
    }
    List<Product> shortlist(List<Product> candidates,Map<String,Object> filters,ChatRetrievalService data) {
        Set<String> requested=needs(filters);
        // A single optional ease/practical preference may be incidental to a
        // technical goal. Multiple reading needs and mood requests need closer fit.
        if(!requested.contains("gentle") && requested.size()<2)return candidates;
        Map<Long,Integer> coverage=new HashMap<>();
        for(Product book:candidates) {
            Map<String,String> facts=data.approvedReadingFacts(book);
            coverage.put(book.getProductId(),(int)requested.stream().filter(need->confirmed(need,facts)).count());
        }
        int strongest=coverage.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if(strongest==0)return candidates;
        return candidates.stream().filter(book->coverage.get(book.getProductId())==strongest).toList();
    }
    List<Product> prioritize(List<Product> candidates,Map<String,Object> filters,ChatRetrievalService data) {
        if(!active(filters))return candidates;
        Map<Long,Integer> scores=new HashMap<>();
        candidates.forEach(p->scores.put(p.getProductId(),score(filters,data.approvedReadingFacts(p))));
        List<Product> result=new ArrayList<>(candidates);
        result.sort(Comparator.comparingInt((Product p)->scores.get(p.getProductId())).reversed());
        return result;
    }
}
