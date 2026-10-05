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
        String rawBookFacts = retrieval == null ? product.getName()+" "+product.getDescription() : retrieval.searchableText(product);
        // A disclaimer about Java 21 does not invalidate a verified Java title.
        String bookFacts = intents.normalize(product.getName()+". "+rawBookFacts);
        String text = bookFacts + " " + category;
        if(!matchesReadingPurpose(question,product))return false;
        if(ChatBookFacts.has(q,"quan ly tien ca nhan|tai chinh ca nhan|quan ly chi tieu")
                && !ChatBookFacts.has(q,"blockchain|bitcoin|tien ma hoa|tien dien tu")
                && ChatBookFacts.has(bookFacts,"blockchain|bitcoin|chuoi khoi|tien ma hoa"))return false;
        var grade=Pattern.compile("\\blop\\s*(\\d{1,2})\\b").matcher(q);
        if(grade.find() && !word(bookFacts,"lop "+grade.group(1)))return false;
        List<String> excluded=excludedLanguages(question);
        if(excluded.stream().anyMatch(l->word(text,l)))return false;
        List<String> languages = new ArrayList<>();
        for (String language : LANGUAGES)
            if (word(q, language) && !excluded.contains(language)) languages.add(language);
        if (!languages.isEmpty()) {
            boolean either = word(q, "hoac") || word(q, "or");
            if (either ? languages.stream().noneMatch(l -> affirmed(bookFacts,l))
                       : languages.stream().anyMatch(l -> !affirmed(bookFacts,l))) return false;
        }
        if(!matchesEconomicScopes(requiredEconomicScopes(question),product))return false;
        if (programmingRequest(question)) {
            if (!Pattern.compile("\\b(lap trinh|code|ma nguon|phan mem|thuat toan|cau truc du lieu|co so du lieu|java|python|javascript|backend|frontend|html|css|web development|sql|coding)\\b").matcher(bookFacts).find()) return false;
        } else if (word(q, "cong nghe") || word(q, "cntt")) {
            if (!Pattern.compile("\\b(lap trinh|cong nghe|tri tue nhan tao|dien tu|robot|ma nguon|phan mem|cau truc du lieu|co so du lieu|may tinh|java|python|javascript|backend|frontend|html|css|web development)\\b").matcher(bookFacts).find()) return false;
        }
        if (Pattern.compile("\\b(tre em|thieu nhi|cho be)\\b").matcher(q).find()
                && !Pattern.compile("\\b(tre em|thieu nhi|cho be|co tich|dong thoai)\\b").matcher(text).find()) return false;
        if (word(q, "tieng anh") && !word(text, "tieng anh") && !word(text, "english")) return false;
        if ((word(q,"truyen") || word(q,"tieu thuyet") || word(q,"van hoc"))
                && (!Pattern.compile("\\b(truyen|tieu thuyet|van hoc)\\b").matcher(text).find()
                    || programmingRequest(bookFacts)
                    || ChatBookFacts.has(bookFacts,"doi nhan xu the|ky nang giao tiep|nghe thuat giao tiep")
                       && !ChatBookFacts.has(bookFacts,"tieu thuyet|truyen ngan|truyen dai|tac pham van hoc"))) return false;
        if ((q.contains("khong lay") || q.contains("loai tru")) && q.contains("kinh nghiem")
                && Pattern.compile("\\b(nang cao|chuyen sau|advanced)\\b").matcher(text).find()
                && !Pattern.compile("\\b(can ban|co ban|nhap mon|nguoi moi)\\b").matcher(text).find()) return false;
        return true;
    }

    boolean matchesReadingPurpose(String question, Product product) {
        String q=intents.normalize(question);
        String facts=retrieval==null?product.getName()+" "+product.getDescription():retrieval.searchableText(product);
        boolean coding=programmingRequest(question) || ChatBookFacts.has(q,"java|python|javascript|spring boot|sql");
        if(!coding && ChatBookFacts.has(q,"nghi qua nhieu|lo ca ngay|hay lo ve|lo lang|lo au")
                && !ChatBookFacts.has(q,"truyen|tieu thuyet|van hoc|trinh tham")
                && !ChatBookFacts.has(intents.normalize(facts),"lo au|lo lang|giam lo|lo nghi|lo toan|giam ban tam|stress|cang thang"))return false;
        return coding
            || !ChatBookFacts.has(q,"nghi qua nhieu|lo ca ngay|hay lo|lo ve|lo au|lo lang|giao tiep|tam ly|cam xuc|phat trien ban than|ky nang song|quan ly tien ca nhan|tai chinh ca nhan")
            || !programmingRequest(facts);
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

    boolean hasRequiredLanguage(String question) {
        String q=intents.normalize(question);
        List<String> excluded=excludedLanguages(question);
        return LANGUAGES.stream().anyMatch(term->word(q,term) && !excluded.contains(term));
    }

    List<String> requiredEconomicScopes(String question) {
        String q=economicText(question);
        return List.of("microeconomics","macroeconomics").stream().filter(term->word(q,term)
            && !Pattern.compile("\\b(?:khong\\s+(?:(?:lay|chon|can|muon)\\s+)?|loai tru\\s+)(?:(?:giao trinh|sach)(?: ve)?\\s+)?(?:kinh te\\s+)?"+term+"\\b").matcher(q).find()).toList();
    }

    boolean matchesEconomicScopes(List<String> scopes,Product product) {
        String facts=economicText(product.getName()+". "+(retrieval==null?product.getDescription():retrieval.searchableText(product)));
        return scopes.stream().allMatch(term->affirmed(facts,term));
    }

    private String economicText(String text) {
        // Folding accents makes “vi mô” and “vĩ mô” identical. Preserve their meaning first.
        return intents.normalize(text.replaceAll("(?iu)\\bvĩ\\s+mô\\b"," macroeconomics ")
            .replaceAll("(?iu)\\bvi\\s+mô\\b"," microeconomics "));
    }

    private boolean affirmed(String facts,String term) {
        for(String clause:facts.split("[,.!?;\\n]+"))
            if(word(clause,term) && !ChatBookFacts.has(clause,
                "chua (?:co|xac nhan|biet|du|the)|khong (?:phai|khang dinh|xac nhan|bao gom|co)"))return true;
        return false;
    }

    boolean programmingRequest(String question) {
        return Pattern.compile("\\b(lap trinh|code|coding|ma nguon|viet ma|ky su phan mem|phat trien phan mem|backend|frontend|web development)\\b")
                .matcher(intents.normalize(question)).find();
    }

    private boolean word(String text, String term) {
        return Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(text).find();
    }
}
