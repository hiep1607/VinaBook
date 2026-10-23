package vn.fs.service;

import java.util.*;
import java.util.regex.*;

/** Answers a closed content question from approved content; absence never means "no". */
final class ChatBookDetailQuestions {
    private ChatBookDetailQuestions() {}
    private static final Pattern CONTENT=Pattern.compile("(?iu)\\b(?:dạy|day|đề cập(?: đến)?|de cap(?: den)?|bao gồm|bao gom|hướng dẫn|huong dan|nói về|noi ve|trình bày|trinh bay|giới thiệu|gioi thieu|có chương|co chuong|có phần|co phan|có nội dung|co noi dung|học về|hoc ve)\\s+(.+?)\\s*(?:không|khong|ko|hả|ha|ạ)?[?!.]*$");
    private static final Pattern CONTAINS=Pattern.compile("(?iu)\\bcó\\s+(.+?)\\s+(?:không|ko|hả)[?!.]*$|\\bco\\s+(.+?)\\s+(?:khong|ko|ha)[?!.]*$");
    private static final Pattern FOLLOW_UP=Pattern.compile("(?iu)\\b(?:còn|con)\\s+(.+?)\\s+(?:thì sao|thi sao)[?!.]*$");
    private static final Pattern AGE=Pattern.compile("(?iu)\\b([0-9]{1,2})\\s*(?:tuổi|tuoi)\\b");

    static boolean active(String question,ChatIntentService language) {
        return active(question,language,List.of());
    }
    static boolean active(String question,ChatIntentService language,Collection<String> bookTitles) {
        String q=language.normalize(question);
        return illustrationQuestion(q) || AGE.matcher(question).find() || !topics(question,language,bookTitles).isEmpty();
    }
    private static boolean illustrationQuestion(String q) {
        return ChatBookFacts.has(q,"minh hoa|co tranh|kem tranh")
            && ChatBookFacts.has(q,"co|la|phai|ban|khong|ha");
    }
    private static List<String> topics(String question,ChatIntentService language,Collection<String> bookTitles) {
        String q=language.normalize(question);
        if(ChatBookFacts.has(q,"tim|goi y|liet ke|gio hang|dat hang|don hang|bai tap|dap an|loi giai|minh hoa|giao tiep|truyen chu|truyen tranh|can.*(?:toan|lap trinh)|dau tu.*(?:lai|loi nhuan)"))return List.of();
        Matcher matcher=CONTENT.matcher(question);
        String topic="";
        if(matcher.find())topic=matcher.group(1);
        else {
            matcher=CONTAINS.matcher(question);
            if(matcher.find())topic=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
            else {
                matcher=FOLLOW_UP.matcher(question);
                if(matcher.find()) {
                    topic=matcher.group(1);
                    String candidate=language.normalize(topic).replaceAll("^(?:cuon|sach|quyen)\\s+", "").trim();
                    for(String title:bookTitles) {
                        if(title==null)continue;
                        String name=language.normalize(title).replaceAll("\\s*\\([^)]*\\)", "").trim();
                        String shortName=name.split("\\s+[-–:]\\s+",2)[0];
                        if(candidate.equals(name) || shortName.length()>=4 && candidate.equals(shortName))return List.of();
                    }
                }
            }
        }
        if(topic.isBlank() || topic.length()>160)return List.of();
        String normalized=language.normalize(topic);
        if(normalized.matches("(?:(?:nhung|cac) )?(?:noi dung|chu de|van de)(?: chinh| co ban)?(?: (?:gi|nao))?"))return List.of();
        String introducedTitle=normalized.replaceFirst("^(?:noi dung |tom tat )?(?:cua )?(?:cuon|sach|quyen|tac pham)\\s+","").trim();
        if(Set.of("noi dung","noi dung sach","noi dung cuon nay","noi dung cuon do","chi tiet hon","cu the hon").contains(normalized))return List.of();
        for(String title:bookTitles) {
            if(title!=null && introducedTitle.equals(language.normalize(title)))return List.of();
        }
        if(normalized.matches("(?:cuon|sach|quyen)(?: sach)? (?:kia|nay|do|ay|con lai)"))return List.of();
        if(ChatBookFacts.has(normalized,"^phai|con hang|bao nhieu|moi nhat|hien hanh|tap nao|tron bo")
                || Set.of("giá","gia","bán","ban","sao","gi","nhung gi","the nao").contains(normalized)
                || normalized.equals("ai") && !topic.equals("AI"))return List.of();
        return Arrays.stream(topic.split("(?iu)\\s*(?:,|;|\\bvà\\b|\\bva\\b|\\bhoặc\\b|\\bhoac\\b|\\band\\b|\\bor\\b)\\s*"))
            .map(String::trim).filter(t->!t.isBlank()).distinct().limit(6).toList();
    }
    static List<String> answers(String question,Map<String,String> facts,ChatIntentService language) {
        return answers(question,facts,language,"");
    }
    static List<String> answers(String question,Map<String,String> facts,ChatIntentService language,String bookTitle) {
        List<String> answers=new ArrayList<>();
        String q=language.normalize(question);
        String source=facts.getOrDefault("summary","")+"\n"+facts.getOrDefault("learningGoal","");
        ChatExplicitFilters confirmed=new ChatExplicitFilters(language);
        if(illustrationQuestion(q)) {
            String evidence=evidence(source,"minh họa",language,confirmed);
            answers.add(evidence.isBlank()?"Về bản minh họa: mình chưa có thông tin đã xác nhận cho đúng ấn bản đang bán, nên chưa thể khẳng định sách có tranh minh họa."
                :"Về minh họa, thông tin đã xác nhận có nêu: “"+evidence+"”.");
        }
        Matcher age=AGE.matcher(question);
        if(age.find()) {
            String audience=facts.getOrDefault("audience","");
            answers.add(AGE.matcher(audience).find()?"Độ tuổi trong hồ sơ đã duyệt: "+ChatDialogueInterpreter.clean(audience,350)
                :"Về độ tuổi bạn hỏi ("+age.group(1)+" tuổi): mình chưa có thông tin đã xác nhận về độ tuổi đọc của đúng ấn bản này, nên chưa thể nói chắc là phù hợp.");
        }
        for(String topic:topics(question,language,bookTitle==null?List.of():List.of(bookTitle))) {
            String evidence=evidence(source,topic,language,confirmed);
            answers.add(evidence.isBlank()?"Về “"+topic+"”: mình chưa có thông tin đã xác nhận cuốn này có đề cập đến nội dung đó, nên chưa thể khẳng định là có hay không."
                :"Về “"+topic+"”: thông tin đã xác nhận có đề cập: “"+evidence+"”. Phần này xác nhận chủ đề được nhắc tới, chưa cho biết mức độ hướng dẫn chi tiết.");
        }
        return answers;
    }
    private static String evidence(String source,String topic,ChatIntentService language,ChatExplicitFilters confirmed) {
        String term=normalized(topic,language);
        for(String sentence:source.split("[.!?;\\n]+"))
            if(confirmed.affirmed(normalized(sentence,language),term))return ChatDialogueInterpreter.clean(sentence.trim(),350);
        return "";
    }
    private static String normalized(String text,ChatIntentService language) {
        return language.normalize(text).replaceAll("\\bwi[ -]?fi\\b","wifi")
            .replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();
    }
}
