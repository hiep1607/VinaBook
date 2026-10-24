package vn.fs.service;

import java.util.*;
import java.util.regex.*;

/** User-stated subjects remain eligibility constraints when a planner uses a broader shelf. */
final class ChatSubjectEvidence {
    private static final Pattern SUBJECT=Pattern.compile("\\b(?:(?:sach|cuon|quyen|giao trinh)(?: chuyen| chuyen sau)? ve|chuyen ve|chu de|(?:yeu )?thich|quan tam(?: den)?)\\s+([^,;.!?]+)");
    private static final Set<String> FILLER=Set.of("sach","cuon","quyen","truyen","ve","cho","toi","minh","mot","cac","nhung","va","co","ca","de","the","gioi","noi","dung","chuyen","sau","tim","hieu","doc","hoc","nhe","nha","thoi","di","a","oi","nhi","hon");
    private static final String READING_QUALIFIERS="nhe nhang|thu gian|de doc|de hieu|ap dung cu the|thuc hanh|chua lanh|giai tri|giet thoi gian|don gian|nguoi moi bat dau|nguoi moi|moi bat dau|de bat dau|ky nang";
    private static final String AUDIENCE="\\b(?:danh )?cho (?:tre em|thieu nhi|be(?: \\d{1,2} tuoi)?|tre(?: \\d{1,2} tuoi)?|nguoi lon|nguoi moi(?: bat dau)?|sinh vien|hoc sinh|me|bo|ban)(?: hon)?\\b";
    private final ChatIntentService language;
    ChatSubjectEvidence(ChatIntentService language) { this.language=language; }

    boolean matches(String question,String facts,List<List<String>> knownSubjects) {
        String q=language.normalize(question);
        Matcher subject=SUBJECT.matcher(q);
        while(subject.find()) {
            String before=q.substring(Math.max(0,subject.start()-24),subject.start());
            if(before.matches("(?s).*\\b(?:khong|chang|chua|bo|loai tru)(?: \\S+){0,3} $"))continue;
            String phrase=subject.group(1).replaceFirst("\\b(?:duoi|tren|khoang|tam|ngan sach|gia|voi gia)\\b(?=[^,;]*\\d).*$","")
                .replaceAll(AUDIENCE," ")
                .replaceAll("\\d[\\d.,]*\\s*(?:k|nghin|ngan|dong|vnd)?\\b", " ").trim()
                .replaceFirst("^(?:(?:sach|cuon|quyen|noi dung|chu de|the loai)(?: ve)? )+","");
            phrase=phrase.replaceFirst("\\b(?:gi do|nao do|gi cung duoc|cung duoc)(?: nhe| a| thoi)?$","").strip();
            if(phrase.isBlank())continue;
            if(phrase.matches("(?:nay|do|ay|khac|cu|truoc)(?: nhe| nha| thoi)?"))continue;
            // Remove only concepts covered by the existing synonym/reading rules.
            // One broad topic or audience must never discard another specialty in
            // the same request (e.g. a technical subject for children).
            List<String> covered=new ArrayList<>(knownSubjects.stream().flatMap(Collection::stream).distinct().toList());
            covered.addAll(List.of("truyen chu","truyen tranh","tieu thuyet","truyen ngan","truyen dai","van hoc","thieu nhi","tre em","cho be"));
            covered.sort(Comparator.comparingInt(String::length).reversed());
            for(String term:covered)phrase=phrase.replaceAll("\\b"+Pattern.quote(term)+"\\b"," ");
            phrase=phrase.replaceAll("\\b(?:"+READING_QUALIFIERS+")(?: hon)?\\b"," ");
            List<String> terms=Arrays.stream(phrase.split("[^\\p{L}\\p{N}]+"))
                .filter(t->!t.isBlank() && !FILLER.contains(t)).distinct().toList();
            if(terms.isEmpty())continue;
            ChatExplicitFilters evidence=new ChatExplicitFilters(language);
            if(terms.stream().anyMatch(term->!evidence.affirmed(facts,term)))return false;
        }
        return true;
    }
}
