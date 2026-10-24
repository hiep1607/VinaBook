package vn.fs.service;

import java.util.Arrays;

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
        String rawBookFacts = retrieval == null ? product.getName()+" "+product.getDescription() : retrieval.descriptiveText(product);
        // A disclaimer about Java 21 does not invalidate a verified Java title.
        String bookFacts = intents.normalize(product.getName()+". "+rawBookFacts);
        String text = bookFacts + " " + category;
        if(!matchesReadingPurpose(question,product) || !matchesFormat(question,product)
                || !matchesStorySubjects(question,product))return false;
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
            if (List.of("lap trinh","code","ma nguon","phan mem","thuat toan","cau truc du lieu","co so du lieu","java","python","javascript","backend","frontend","html","css","web development","sql","coding").stream().noneMatch(term->affirmed(bookFacts,term))) return false;
        } else if (word(q, "cong nghe") || word(q, "cntt")) {
            if (!Pattern.compile("\\b(lap trinh|cong nghe|tri tue nhan tao|dien tu|robot|ma nguon|phan mem|cau truc du lieu|co so du lieu|may tinh|java|python|javascript|backend|frontend|html|css|web development)\\b").matcher(bookFacts).find()) return false;
        }
        if (!aboutChildren(q) && Pattern.compile("\\b(tre em|thieu nhi|cho be)\\b").matcher(q).find()
                && !Pattern.compile("\\b(tre em|thieu nhi|cho be|co tich|dong thoai)\\b").matcher(text).find()) return false;
        if (word(q, "tieng anh") && !word(text, "tieng anh") && !word(text, "english")) return false;
        if ((word(q,"truyen") || word(q,"tieu thuyet") || word(q,"van hoc"))
                && (!Pattern.compile("\\b(truyen|tieu thuyet|van hoc|cau chuyen|dong thoai|co tich|hoi uc|hoi tuong|nhan vat)\\b").matcher(text).find()
                    || programmingRequest(bookFacts)
                    || ChatBookFacts.has(bookFacts,"doi nhan xu the|ky nang giao tiep|nghe thuat giao tiep")
                       && !ChatBookFacts.has(bookFacts,"tieu thuyet|truyen ngan|truyen dai|tac pham van hoc"))) return false;
        if ((q.contains("khong lay") || q.contains("loai tru")) && q.contains("kinh nghiem")
                && Pattern.compile("\\b(nang cao|chuyen sau|advanced)\\b").matcher(text).find()
                && !Pattern.compile("\\b(can ban|co ban|nhap mon|nguoi moi)\\b").matcher(text).find()) return false;
        return true;
    }

    /** Explicit subject evidence must describe content, not its shelf, audience or style. */
    boolean matchesStorySubjects(String question,Product product) {
        return matchesStorySubjects(question,product,false);
    }
    boolean matchesStorySubjects(String question,Product product,boolean interpretedAlternatives) {
        String q=intents.normalize(intents.discoveryQuery(question,interpretedAlternatives));
        String[] alternatives=q.split("\\b(?:hoac|or)\\b");
        if(alternatives.length>1 && Arrays.stream(alternatives).noneMatch(String::isBlank))
            return Arrays.stream(alternatives).anyMatch(branch->matchesStorySubjects(
                ChatBookFacts.has(branch,"ve|chu de|thich|quan tam")?branch:"sách về "+branch,product));
        // Apply purpose within each OR branch, so a reflective communication
        // mention cannot borrow eligibility from a different requested subject.
        if(!matchesCommunicationPurpose(q,product))return false;
        // These are synonym families, not product IDs. Each expressed subject is
        // mandatory; a high relevance score cannot make a missing subject true.
        List<List<String>> subjects=new ArrayList<>(List.of(
            List.of("loai vat","dong vat","muong thu"),
            List.of("noi dau","dau don","ton thuong","dau buon","mat mat"),
            List.of("ngheo","ngheo kho","ngheo doi","thieu thon","kho khan vat chat"),
            List.of("moi truong hoc","truong hoc","nha truong","o truong","den truong","lop hoc","hoc duong"),
            List.of("trao phung","cham biem","satire","satirical"),
            List.of("van hoa dan gian","truyen dan gian","truyen co tich","folklore","folktales"),
            List.of("mang may tinh","computer networking","network protocols"),
            List.of("co so du lieu","database","data modeling"),
            List.of("lam phat","inflation"),
            List.of("that nghiep","unemployment"),
            List.of("ty gia","exchange rates"),
            List.of("quan ly thoi gian","quan tri thoi gian","sap xep thoi gian","su dung thoi gian","time management"),
            List.of("ky nang hoc tap","phuong phap hoc","cach hoc hieu qua","hoc tap hieu qua","study skills","learning strategies")
        ));
        subjects.addAll(ChatCatalogQuery.subjectGroups());
        // Families overlap for recall. A single expressed concept uses their
        // union; independently expressed concepts still remain an AND.
        java.util.Map<String,java.util.Set<String>> required=new java.util.LinkedHashMap<>();
        for(List<String> subject:subjects)for(String term:subject)if(affirmed(q,term))
            required.computeIfAbsent(term,key->new java.util.LinkedHashSet<>()).addAll(subject);
        if(!matchesLiteraryOrigin(q,product))return false;
        java.util.Map<String,String> approved=retrieval==null?java.util.Map.of():retrieval.approvedReadingFacts(product);
        String source=product.getName()+". "+(retrieval==null?product.getDescription():
            approved.getOrDefault("summary","")+". "+approved.getOrDefault("learningGoal",""));
        String facts=intents.normalize(source);
        if(!new ChatSubjectEvidence(intents).matches(question,facts,subjects))return false;
        for(java.util.Set<String> subject:required.values())
            if(subject.stream().noneMatch(term->affirmed(facts,term)))return false;
        if(aboutChildren(q) && List.of("tre em","thieu nhi","co be","cau be","tuoi tho","thoi tho au")
                .stream().noneMatch(term->affirmed(facts,term)))return false;
        return true;
    }

    /** Literary origin is a classification, not the language of a translation
     * or the nationality of the reader. Canonical descriptions/topic text may
     * confirm it only through an explicit literature + origin phrase. */
    private boolean matchesLiteraryOrigin(String query,Product product) {
        String facts=intents.normalize(retrieval==null?product.getDescription():retrieval.searchableText(product));
        String form="(?:van hoc|van chuong|truyen|tieu thuyet|thi ca|tho)(?: (?:tranh|thieu nhi|hien thuc|co dien|hien dai))? ";
        for(String origin:List.of("viet nam|viet","nhat ban|nhat","trung quoc","han quoc","nga","phap","my","anh")) {
            Pattern phrase=Pattern.compile("\\b"+form+"(?:"+origin+")\\b");
            var requested=phrase.matcher(query);boolean required=false;
            while(requested.find())if(affirmed(query,requested.group())){required=true;break;}
            if(!required)continue;
            var classified=phrase.matcher(facts);boolean confirmed=false;
            while(classified.find())if(affirmed(facts,classified.group())){confirmed=true;break;}
            if(!confirmed)return false;
        }
        return true;
    }

    private boolean aboutChildren(String q) {
        return ChatBookFacts.has(q,"(?:truyen|cau chuyen|sach).*ve (?:tre em|co be|cau be)");
    }

    /** A broad literature/children shelf cannot prove the requested literary form. */
    boolean matchesFormat(String question,Product product) {
        String q=intents.normalize(question);
        String facts=intents.normalize(retrieval==null?product.getName()+". "+product.getDescription():retrieval.descriptiveText(product));
        List<String> forms=List.of("tieu thuyet","truyen ngan","truyen tranh");
        List<String> required=forms.stream().filter(form->affirmed(q,form)).toList();
        if(required.isEmpty())return true;
        boolean either=word(q,"hoac") || word(q,"or");
        return either?required.stream().anyMatch(form->affirmed(facts,form))
            :required.stream().allMatch(form->affirmed(facts,form));
    }

    boolean matchesReadingPurpose(String question, Product product) {
        String q=intents.normalize(intents.discoveryQuery(question));
        String[] alternatives=q.split("\\b(?:hoac|or)\\b");
        if(alternatives.length>1 && Arrays.stream(alternatives).noneMatch(String::isBlank))
            return Arrays.stream(alternatives).anyMatch(branch->matchesReadingPurpose(branch,product));
        if(!matchesCommunicationPurpose(q,product))return false;
        String facts=retrieval==null?product.getName()+" "+product.getDescription():retrieval.searchableText(product);
        boolean coding=programmingRequest(question) || ChatBookFacts.has(q,"java|python|javascript|spring boot|sql");
        if(!coding && ChatBookFacts.has(q,"nghi qua nhieu|lo ca ngay|hay lo ve|lo lang|lo au")
                && !ChatBookFacts.has(q,"truyen|tieu thuyet|van hoc|trinh tham")
                && !ChatBookFacts.has(intents.normalize(facts),"lo au|lo lang|giam lo|lo nghi|lo toan|giam ban tam|stress|cang thang"))return false;
        return coding
            || !ChatBookFacts.has(q,"nghi qua nhieu|lo ca ngay|hay lo|lo ve|lo au|lo lang|giao tiep|tam ly|cam xuc|phat trien ban than|ky nang song|quan ly tien ca nhan|tai chinh ca nhan")
            || !programmingRequest(facts);
    }

    private boolean matchesCommunicationPurpose(String query,Product product) {
        if(!affirmed(query,"giao tiep") || List.of("truyen","tieu thuyet","van hoc","suy ngam")
                .stream().anyMatch(form->affirmed(query,form)))return true;
        String approved=intents.normalize(product.getName()+" "+(retrieval==null?product.getDescription():
            retrieval.approvedReadingFacts(product).getOrDefault("summary","")));
        // Generic communication books need instruction in their core content.
        // A reflective learning goal or a broad shelf is insufficient evidence.
        return List.of("giao tiếp","ứng xử","đối nhân xử thế","thuyết phục")
                .stream().anyMatch(term->affirmed(approved,intents.normalize(term)))
            && List.of("kỹ năng giao tiếp","nghệ thuật giao tiếp","nguyên tắc","phương pháp","hướng dẫn","luyện tập","thực hành")
                .stream().anyMatch(term->affirmed(approved,intents.normalize(term)));
    }

    List<String> excludedLanguages(String question) {
        String q=intents.normalize(question);
        return LANGUAGES.stream().filter(language->Pattern.compile(
            "\\b(?:khong\\s+(?:(?:lay|chon|can|muon|goi y)\\s+)?|loai tru\\s+|bo qua\\s+)(?:sach\\s+(?:ve\\s+)?)?"
            +Pattern.quote(language)+"\\b").matcher(q).find()).toList();
    }

    boolean onlyLanguage(String question) {
        return Pattern.compile("\\b(?:chi\\s+(?:(?:lay|chon|can|muon|goi y|gioi thieu|tim)\\s+)?|only\\s+)(?:sach\\s+(?:ve\\s+)?)?(?:java|python|javascript|spring boot)\\b")
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

    boolean affirmed(String facts,String term) {
        for(String clause:facts.split("[,.!?;\\n]+"))
            if(word(clause,term) && !ChatBookFacts.has(clause,
                "chua (?:co|xac nhan|biet|du|the)|khong (?:phai|khang dinh|xac nhan|bao gom|co|lay|chon|muon|can|tap trung|dung|su dung|yeu cau|doi hoi|nen|mac dinh)|loai tru"))return true;
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
