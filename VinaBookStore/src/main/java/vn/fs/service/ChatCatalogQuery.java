package vn.fs.service;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;

/** Eligibility over the entire catalog precedes sorting and limiting; relevance cannot hide cheaper matches. */
final class ChatCatalogQuery {
    private final ChatIntentService language;
    private final ChatRetrievalService data;
    ChatCatalogQuery(ChatIntentService language, ChatRetrievalService data) { this.language = language; this.data = data; }
    List<Product> named(String question, List<Product> catalog) {
        String q = language.normalize(question);
        record Mention(Product book,int start,int end) {}
        List<Mention> exact=new ArrayList<>();
        for(Product product:catalog) {
            if(!titleMention(question,product.getName(),language))continue;
            String name=language.normalize(product.getName());
            String shortName=name.replaceAll("\\s*\\([^)]*\\)","").trim();
            String leading=shortName.split("\\s+[-–:]\\s+",2)[0];
            for(String variant:new LinkedHashSet<>(List.of(name,shortName,leading))) {
                if(variant.length()<4)continue;
                Matcher match=Pattern.compile("\\b"+Pattern.quote(variant)+"\\b").matcher(q);
                if(match.find()){exact.add(new Mention(product,match.start(),match.end()));break;}
            }
        }
        if(!exact.isEmpty())return exact.stream()
            .filter(m->exact.stream().noneMatch(other->!other.book().getProductId().equals(m.book().getProductId())
                && other.start()<=m.start() && other.end()>=m.end() && other.end()-other.start()>m.end()-m.start()))
            .sorted(Comparator.comparingInt(Mention::start)).map(Mention::book).toList();
        return catalog.stream().filter(p -> {
            if(!titleMention(question,p.getName(),language))return false;
            String name = language.normalize(p.getName());
            String shortName = name.replaceAll("\\s*\\([^)]*\\)", "").trim();
            String leadingTitle = shortName.split("\\s+[-–:]\\s+", 2)[0];
            return name.length() > 3 && (q.contains(name) || q.contains(shortName)
                    || (leadingTitle.length() >= 8 && leadingTitle.contains(" ") && word(q, leadingTitle))
                    || containsDistinctiveTitlePhrase(q, name)
                    || (q.contains("harry potter") && name.startsWith("harry potter"))
                    || (word(q,"conan") && word(name,"conan"))
                    || (word(q,"doraemon") && word(name,"doraemon")));
        }).sorted(Comparator.comparingInt(p -> q.indexOf(language.normalize(p.getName()).split(" \\(")[0])))
                .collect(Collectors.toList());
    }
    static boolean titleMention(String question,String title,ChatIntentService language) {
        String q=language.normalize(question), name=language.normalize(title);
        String rawQuestion=question.toLowerCase(Locale.ROOT),rawTitle=title.toLowerCase(Locale.ROOT);
        if(rawTitle.contains("vĩ mô") && rawQuestion.contains("vi mô") && !rawQuestion.contains("vĩ mô"))return false;
        if(rawTitle.contains("vi mô") && rawQuestion.contains("vĩ mô") && !rawQuestion.contains("vi mô"))return false;
        // Accent folding must not turn the demonstrative “số đó” into the title “Số Đỏ”.
        if(name.equals("so do") && !question.toLowerCase(Locale.ROOT).contains("số đỏ")
                && ChatBookFacts.has(q,"trong so do|so do cuon nao|so do sach nao"))return false;
        return true;
    }
    private boolean containsDistinctiveTitlePhrase(String question, String title) {
        String[] words = title.split("\\s+");
        for (int i = 0; i + 1 < words.length; i++) {
            String phrase = words[i] + " " + words[i + 1];
            if (phrase.length() >= 12 && question.contains(phrase)) return true;
        }
        return false;
    }
    String quoted(String question) {
        Matcher m = Pattern.compile("[\"“]([^\"”]{1,180})[\"”]").matcher(question);
        return m.find() ? m.group(1) : "";
    }
    List<Product> byIds(List<Long> ids, List<Product> catalog) {
        return ids.stream().distinct().flatMap(id -> catalog.stream().filter(p -> id.equals(p.getProductId())))
                .collect(Collectors.toList());
    }
    List<Product> search(String query, ChatSearchCriteriaDto c, List<Product> catalog) {
        String q = language.normalize(query);
        String[] alternatives=q.split("\\b(?:hoac|or)\\b");
        if(alternatives.length>1 && Arrays.stream(alternatives).noneMatch(String::isBlank)) {
            List<List<Product>> branches=Arrays.stream(alternatives).map(branch->search(branch,c,catalog)).toList();
            Map<Long,Product> union=new LinkedHashMap<>();
            int longest=branches.stream().mapToInt(List::size).max().orElse(0);
            // Keep each requested topic represented when a single shared limit is applied.
            for(int index=0;index<longest;index++)for(List<Product> branch:branches)
                if(index<branch.size())union.putIfAbsent(branch.get(index).getProductId(),branch.get(index));
            return c.getSort().isEmpty()?new ArrayList<>(union.values()):search("",c,new ArrayList<>(union.values()));
        }
        ChatExplicitFilters mandatory = new ChatExplicitFilters(language, data);
        List<Product> found = catalog.stream().filter(p -> Boolean.TRUE.equals(p.getStatus()))
                .filter(p -> c.getMinPrice() == null || data.effectivePrice(p) >= c.getMinPrice())
                .filter(p -> c.getMaxPrice() == null || data.effectivePrice(p) <= c.getMaxPrice())
                .filter(p -> c.getAuthor().isEmpty() || language.normalize(c.getAuthor()).equals(language.normalize(p.getAuthor())))
                .filter(p -> c.getCategory().isEmpty() || category(p).contains(language.normalize(c.getCategory())))
                .filter(p -> mandatory.matches(query, p)).collect(Collectors.toList());
        // Human concepts do not necessarily correspond to the database's broad categories.
        for (List<String> group : GROUPS) {
            if (group.stream().anyMatch(t -> word(q, t)))
                found = found.stream().filter(p -> group.stream().anyMatch(t -> word(content(p), t))).collect(Collectors.toList());
        }
        if(!q.isBlank() && !semanticConstraint(query) && c.getAuthor().isEmpty() && c.getCategory().isEmpty()
                && !language.isBudgetOnlyDiscovery(query)) {
            String topical=q.replaceAll("\\d[\\d.,]*\\s*(?:trieu|nghin|ngan|dong|vnd|lit|tr|k|d)?\\b"," ");
            List<String> terms=Arrays.stream(topical.replaceAll("[^\\p{L}\\p{N}]+"," ").split("\\s+"))
                .filter(t->!t.isBlank() && !Set.of("sach","cuon","quyen","ve","cho","toi","minh","tim","goi","y","muon","mot","vai","nhung","de","va","cua","co","duoc",
                    "hay","nao","khac","con","khong","nhe","giup","chon","it","duoi","tren","tu","den","gia","ngan","tam","khoang","da","moi","ban","chay","nhat","thap","cao","danh","rating").contains(t)).distinct().toList();
            if(!terms.isEmpty())found.removeIf(p->{
                String text=content(p);
                return !word(text,q) && terms.stream().filter(t->word(text,t)).count()<Math.ceil(terms.size()*0.6);
            });
        }
        if(ChatBookFacts.has(q,"phat trien ban than|ky nang song|self help") && !mandatory.programmingRequest(query))
            found.removeIf(p->mandatory.programmingRequest(data.searchableText(p))
                && !ChatBookFacts.has(content(p),"phat trien ban than|ky nang song|lo au|lo lang|doi nhan xu the"));
        Comparator<Product> order = Comparator.comparing(Product::getProductId);
        if ("price-asc".equals(c.getSort())) order = Comparator.comparingLong(data::effectivePrice).thenComparing(Product::getProductId);
        if ("price-desc".equals(c.getSort())) order = Comparator.comparingLong(data::effectivePrice).reversed().thenComparing(Product::getProductId);
        if ("new".equals(c.getSort())) order = Comparator.comparing(Product::getEnteredDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Product::getProductId);
        if ("sale".equals(c.getSort())) { found.removeIf(p -> p.getDiscount() <= 0); order = Comparator.comparingInt(Product::getDiscount).reversed(); }
        if ("best".equals(c.getSort())) {
            found.removeIf(p -> p.getSold() <= 0);
            order = Comparator.comparingInt(Product::getSold).reversed().thenComparing(Product::getProductId);
        }
        found.sort(order);
        if (c.getSort().isEmpty() && !q.isBlank()) found = data.rankByMeaning(query, found);
        return data.rated(found, c);
    }
    boolean semanticConstraint(String query) {
        String q = language.normalize(query);
        return new ChatExplicitFilters(language,data).programmingRequest(query)
                || q.matches(".*\\b(java|python|javascript|spring boot|tieng anh|cong nghe|cntt|thieu nhi|tre em|cho be|truyen|tieu thuyet|van hoc)\\b.*")
                || GROUPS.stream().anyMatch(g -> g.stream().anyMatch(t -> word(q, t)));
    }
    record Nearby(Product book,String reason) {}
    List<Nearby> nearby(String query,ChatSearchCriteriaDto criteria,List<Product> catalog,List<Long> seeds,Set<Long> excluded) {
        return nearby(query,criteria,catalog,seeds,excluded,query);
    }
    List<Nearby> nearby(String query,ChatSearchCriteriaDto criteria,List<Product> catalog,List<Long> seeds,Set<Long> excluded,String semanticQuery) {
        if(query==null || query.isBlank())return List.of();
        String q=language.normalize(query);
        ChatSearchCriteriaDto hard=new ChatSearchCriteriaDto(criteria.getMinPrice(),criteria.getMaxPrice(),"",criteria.getAuthor(),criteria.getSort(),List.of());
        hard.setMinRating(criteria.getMinRating());
        List<Product> candidates=search("",hard,catalog).stream()
            .filter(p->p.getQuantity()>0 && !excluded.contains(p.getProductId())).collect(Collectors.toList());
        String domain=new ChatExplicitFilters(language,data).programmingRequest(query)
            || q.matches("(?s).*\\b(java|python|javascript|spring boot|sql)\\b.*")?"lập trình":"";
        if(ChatBookFacts.has(q,"tre em|thieu nhi|cho be"))domain+=" thiếu nhi";
        if(ChatBookFacts.has(q,"tieng anh"))domain+=" tiếng Anh";
        if(ChatBookFacts.has(q,"truyen|tieu thuyet"))domain+=" truyện";
        if(ChatBookFacts.has(q,"phieu luu|tham hiem|trinh tham|tham tu|lang man")
            || ChatBookFacts.has(q,"tinh yeu|tinh cam") && !ChatBookFacts.has(q,"tam ly|ky nang|giao tiep"))domain+=" truyện";
        ChatExplicitFilters mandatory=new ChatExplicitFilters(language,data);
        var grade=Pattern.compile("\\blop\\s*(\\d{1,2})\\b").matcher(q);
        if(grade.find())domain+=" lớp "+grade.group(1);
        if(ChatBookFacts.has(q,"nghi qua nhieu|lo ca ngay|lo au|lo lang|giao tiep|tam ly|cam xuc|phat trien ban than|ky nang song|quan ly tien ca nhan|tai chinh ca nhan"))domain+=" "+q;
        final String required=domain;
        candidates.removeIf(p->!mandatory.matches(required,p));
        Map<Long,String> linked=data.relatedEvidence(seeds);
        Map<Long,String> semantic=data.semanticEvidence(semanticQuery,candidates);
        List<Nearby> found=new ArrayList<>();
        for(Product book:candidates) {
            String reason=linked.get(book.getProductId()), content=language.normalize(data.descriptiveText(book));
            if(reason==null)for(List<String> edge:NEIGHBORS) {
                if(!ChatBookFacts.has(q,edge.get(0)))continue;
                String match=Arrays.stream(edge.get(1).split("\\|")).filter(term->word(content,language.normalize(term))).findFirst().orElse(null);
                if(match!=null) { reason="Nội dung nhắc đến “"+match+"”, gần với hướng đọc bạn đang tìm.";break; }
            }
            if(reason==null)reason=semantic.get(book.getProductId());
            if(reason!=null)found.add(new Nearby(book,reason));
        }
        List<Product> ranked=data.rankByMeaning(query,found.stream().map(Nearby::book).collect(Collectors.toList()));
        Map<Long,Integer> positions=new HashMap<>();for(int i=0;i<ranked.size();i++)positions.put(ranked.get(i).getProductId(),i);
        if(criteria.getSort().isEmpty())found.sort(Comparator.comparing((Nearby n)->!semantic.containsKey(n.book().getProductId()))
            .thenComparingInt(n->positions.get(n.book().getProductId())));
        return found;
    }
    static boolean word(String text, String phrase) { return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(text).find(); }
    private String category(Product p) { return p.getCategory() == null ? "" : language.normalize(p.getCategory().getCategoryName()); }
    private String text(Product p) { return language.normalize(p.getName() + " " + p.getDescription()) + " " + category(p); }
    private String content(Product p) {
        String content=language.normalize(data.searchableText(p));
        String broadCategory=category(p);
        return broadCategory.isBlank()?content:content.replace(broadCategory," ");
    }
    private static final List<List<String>> GROUPS = List.of(
            List.of("phieu luu", "tham hiem"),
            List.of("kinh doanh", "quan tri", "marketing", "dau tu", "doanh nghiep", "kinh te"),
            List.of("khoi nghiep", "startup", "kiem chung y tuong", "thu nghiem y tuong"),
            List.of("phap luat", "phap ly", "luat", "hien phap", "dan su", "lao dong"),
            List.of("giao tiep", "ung xu", "doi nhan xu the", "thuyet phuc"),
            List.of("nghi qua nhieu", "lo ca ngay", "lo au", "lo lang", "ganh lo", "binh an"),
            List.of("quan ly tien ca nhan", "tai chinh ca nhan", "quan ly chi tieu", "tiet kiem", "ngan sach ca nhan"),
            List.of("phat trien ban than", "ky nang song", "thoi quen", "self help", "lo au", "lo lang", "doi nhan xu the"),
            List.of("lich su", "chien tranh", "trieu dai", "su luoc"),
            List.of("tam ly", "cam xuc", "hanh vi", "noi tam"),
            List.of("trinh tham", "tham tu", "pha an"),
            List.of("truyen tranh", "manga", "comic"),
            List.of("tinh yeu", "lang man", "tinh cam"),
            List.of("tam linh", "ton giao", "thien dinh", "phat giao"));
    private static final List<List<String>> NEIGHBORS=List.of(
        List.of("phieu luu|tham hiem","lang thang|lưu lạc|chu du|khám phá thế giới|đi tìm kho báu|mạo hiểm|phép thuật|phù thủy|giả tưởng"),
        List.of("trinh tham|tham tu|pha an","bí ẩn|ly kỳ|suy luận|mật mã"),
        List.of("khoi nghiep|startup|kiem chung y tuong|thu nghiem y tuong","quản trị|marketing|chiến lược kinh doanh|quản lý doanh nghiệp"),
        List.of("kinh doanh|quan tri|marketing|kinh te","khởi nghiệp|startup|giao tiếp|đàm phán"),
        List.of("giao tiep|ung xu|thuyet phuc","tâm lý|hành vi|cảm xúc|đàm phán"),
        List.of("nghi qua nhieu|lo ca ngay|lo au|lo lang|ganh lo","tâm lý|cảm xúc|bình an|nội tâm"),
        List.of("quan ly tien ca nhan|tai chinh ca nhan|quan ly chi tieu|tiet kiem","tài chính|tiền bạc|đầu tư"),
        List.of("phat trien ban than|ky nang song|thoi quen|self help|lo au|lo lang","tâm lý|cảm xúc|truyền cảm hứng|vượt qua khó khăn"),
        List.of("tam ly|cam xuc|hanh vi|noi tam","lo âu|lo lắng|kỹ năng sống|thói quen|giao tiếp"),
        List.of("lich su|chien tranh|trieu dai","văn hóa|hồi ký|tiểu sử|phong tục"),
        List.of("tinh yeu|lang man|tinh cam","rung động đầu đời|tình thân|tình cảm gia đình|khát khao yêu thương|tình cha con|tình mẹ con|mối quan hệ"),
        List.of("tam linh|ton giao|thien dinh|phat giao","nội tâm|bình an|tâm lý|lo âu"),
        List.of("lap trinh|code|ma nguon|java|python|javascript|spring boot|sql","lập trình|mã nguồn|phần mềm|thuật toán|cơ sở dữ liệu"),
        List.of("truyen tranh|manga|comic","đồng thoại|cổ tích")
    );
}
