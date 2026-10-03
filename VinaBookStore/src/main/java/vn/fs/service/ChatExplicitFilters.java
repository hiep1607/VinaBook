package vn.fs.service;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import vn.fs.entity.Product;

/** Mandatory user constraints are evaluated independently from relevance scores. */
final class ChatExplicitFilters {
    private static final List<String> LANGUAGES=List.of("java", "python", "javascript", "spring boot");
    private final ChatIntentService intents;
    private final ChatRetrievalService retrieval;
    ChatExplicitFilters(ChatIntentService intents) { this(intents, null); }
    ChatExplicitFilters(ChatIntentService intents, ChatRetrievalService retrieval) {
        this.intents = intents;
        this.retrieval = retrieval;
    }

    boolean matches(String question, Product product) {
        String q = intents.normalize(question);
        String name = intents.normalize(product.getName());
        String category = product.getCategory() == null ? "" : intents.normalize(product.getCategory().getCategoryName());
        String bookFacts = retrieval == null ? name + " " + intents.normalize(product.getDescription())
                : intents.normalize(retrieval.searchableText(product));
        String text = bookFacts + " " + category;
        List<String> excluded=excludedLanguages(question);
        if(excluded.stream().anyMatch(l->word(text,l)))return false;
        List<String> languages = new ArrayList<>();
        for (String language : LANGUAGES)
            if (word(q, language) && !excluded.contains(language)) languages.add(language);
        if (!languages.isEmpty()) {
            boolean either = word(q, "hoac") || word(q, "or");
            if (either ? languages.stream().noneMatch(l -> word(text, l))
                       : languages.stream().anyMatch(l -> !word(text, l))) return false;
        }
        if (programmingRequest(question)) {
            if (!Pattern.compile("\\b(lap trinh|code|ma nguon|phan mem|thuat toan|cau truc du lieu|co so du lieu|java|python|javascript|backend|frontend|html|css|web development|sql|coding)\\b").matcher(bookFacts).find()) return false;
        } else if (word(q, "cong nghe") || word(q, "cntt")) {
            if (!Pattern.compile("\\b(lap trinh|cong nghe|tri tue nhan tao|dien tu|robot|ma nguon|phan mem|cau truc du lieu|co so du lieu|may tinh|java|python|javascript|backend|frontend|html|css|web development)\\b").matcher(bookFacts).find()) return false;
        }
        if (Pattern.compile("\\b(tre em|thieu nhi|cho be)\\b").matcher(q).find()
                && !Pattern.compile("\\b(tre em|thieu nhi|cho be|co tich|dong thoai)\\b").matcher(text).find()) return false;
        if (word(q, "tieng anh") && !word(text, "tieng anh") && !word(text, "english")) return false;
        if ((word(q,"truyen") || word(q,"tieu thuyet"))
                && !Pattern.compile("\\b(truyen|tieu thuyet|van hoc)\\b").matcher(text).find()) return false;
        if ((q.contains("khong lay") || q.contains("loai tru")) && q.contains("kinh nghiem")
                && Pattern.compile("\\b(nang cao|chuyen sau|advanced)\\b").matcher(text).find()
                && !Pattern.compile("\\b(can ban|co ban|nhap mon|nguoi moi)\\b").matcher(text).find()) return false;
        return true;
    }

    List<String> excludedLanguages(String question) {
        String q=intents.normalize(question);
        return LANGUAGES.stream().filter(language->Pattern.compile(
            "\\b(?:khong\\s+(?:(?:lay|chon|can|muon|goi y)\\s+)?|loai tru\\s+|bo qua\\s+)(?:sach\\s+(?:ve\\s+)?)?"
            +Pattern.quote(language)+"\\b").matcher(q).find()).toList();
    }

    boolean onlyLanguage(String question) {
        return Pattern.compile("\\b(?:chi\\s+(?:(?:lay|chon|can|muon)\\s+)?|only\\s+)(?:sach\\s+(?:ve\\s+)?)?(?:java|python|javascript|spring boot)\\b")
            .matcher(intents.normalize(question)).find();
    }

    boolean programmingRequest(String question) {
        return Pattern.compile("\\b(lap trinh|code|coding|ma nguon|viet ma|ky su phan mem|phat trien phan mem|backend|frontend|web development)\\b")
                .matcher(intents.normalize(question)).find();
    }

    private boolean word(String text, String term) {
        return Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(text).find();
    }
}
