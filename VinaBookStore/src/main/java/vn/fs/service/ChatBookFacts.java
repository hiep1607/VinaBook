package vn.fs.service;

import java.util.*;
import vn.fs.entity.Product;

/** Requested facts are rendered from store data, independently of the planner's field choice. */
final class ChatBookFacts {
    private ChatBookFacts() {}

    static Set<String> requested(String question, List<Product> books, ChatIntentService language) {
        String q = language.normalize(question);
        for (Product book : books) {
            String title = language.normalize(book.getName()).replaceAll("\\s*\\([^)]*\\)", "").trim();
            q = q.replace(title, " ");
            String shortTitle = title.split("\\s+[-–:]\\s+", 2)[0];
            if (shortTitle.length() >= 4) q = q.replace(shortTitle, " ");
        }
        Set<String> fields = new LinkedHashSet<>();
        if (has(q, "tac gia|ai viet|cua ai|ai chu bien|chu bien.*ai|co phai.*(?:sach cua|cua|la sach)")) fields.add("author");
        String priceQuestion=q.replaceAll("\\b(?:tac gia|danh gia)\\b"," ");
        if (has(priceQuestion, "gia|bao nhieu tien|re hon|re nhat|dat hon|dat nhat")) fields.add("price");
        if (has(q, "giam|chiet khau|khuyen mai|phan tram") || q.contains("%")) fields.add("discount");
        if (has(q, "con hang|con ban|dang ban|co ban|ton kho|con may|bao nhieu ban|bao nhieu cuon")) fields.add("inventory");
        if (has(q, "rating|danh gia|[0-5](?:[.,]\\d)? sao")) fields.add("rating");
        if (contentQuestion(q)) fields.add("description");
        if (ChatBookDetailQuestions.active(question,language,books.stream().map(Product::getName).toList())) fields.add("contentDetails");
        if (formatQuestion(q)) fields.add("format");
        if (communicationInstructionQuestion(q)) fields.add("communicationInstruction");
        if (has(q,"danh cho ai|phu hop.*ai|doi tuong|do tuoi|bao nhieu tuoi")) fields.add("audience");
        if (has(q, "nha xuat ban|nxb")) fields.add("publisher");
        if (has(q, "nam xuat ban")) fields.add("publicationYear");
        if (has(q, "so trang|bao nhieu trang")) fields.add("pageCount");
        if (has(q, "isbn")) fields.add("isbn");
        if (has(q, "bai tap|bai thuc hanh")) fields.add("exercises");
        if (has(q, "dap an|loi giai|huong dan giai")) fields.add("answerKey");
        return fields;
    }

    static boolean asksDirectFacts(String question,ChatIntentService language) {
        String q=language.normalize(question);
        return asksStudyFeatures(question,language) || formatQuestion(q) || communicationInstructionQuestion(q)
            || ChatBookDetailQuestions.active(question,language);
    }

    private static boolean formatQuestion(String q) {
        return has(q,"(?:la|phai|thuoc|dang|loai).*(?:truyen chu|truyen tranh)|truyen chu.*(?:hay|hoac).*truyen tranh|truyen tranh.*(?:hay|hoac).*truyen chu|dang sach|the loai gi|thuoc the loai|la the loai|la truyen gi");
    }

    private static boolean communicationInstructionQuestion(String q) {
        return has(q,"(?:day|hoc|huong dan|luyen|ky nang|thuc hanh).*giao tiep|giao tiep.*(?:ky nang|huong dan|thuc hanh)");
    }

    static List<String> directFacts(Set<String> requested,Map<String,String> facts,ChatIntentService language) {
        List<String> answers=new ArrayList<>();
        String source=facts.getOrDefault("summary","");
        String normalized=language.normalize(source);
        ChatExplicitFilters confirmed=new ChatExplicitFilters(language);
        if(requested.contains("format")) {
            boolean comic=confirmed.affirmed(normalized,"truyen tranh");
            String prose=List.of("tiểu thuyết","truyện dài","truyện ngắn")
                .stream().filter(term->confirmed.affirmed(normalized,language.normalize(term))).findFirst().orElse("");
            if(comic && prose.isBlank())answers.add("Đây là truyện tranh theo phần giới thiệu đã được xác nhận.");
            else if(!comic && !prose.isBlank())answers.add("Về dạng tác phẩm, đây là truyện chữ ("+prose+"). "
                +"Riêng ấn bản đang bán có minh họa hay là bản chuyển thể hay không thì mình chưa có thông tin để xác nhận.");
            else answers.add("Mình chưa có thông tin xác nhận rõ đây là truyện chữ hay truyện tranh của đúng ấn bản đang bán, nên chưa muốn trả lời chắc rồi khiến bạn chọn nhầm.");
        }
        if(requested.contains("communicationInstruction")) {
            boolean topic=List.of("giao tiếp","ứng xử","đối nhân xử thế","thuyết phục")
                .stream().anyMatch(term->confirmed.affirmed(normalized,language.normalize(term)));
            boolean instruction=List.of("kỹ năng giao tiếp","nguyên tắc","phương pháp","hướng dẫn","luyện tập","thực hành")
                .stream().anyMatch(term->confirmed.affirmed(normalized,language.normalize(term)));
            if(topic && instruction)answers.add("Có, phần giới thiệu xác nhận nội dung hướng dẫn về giao tiếp hoặc ứng xử: "+ChatDialogueInterpreter.clean(source,500));
            else answers.add("Mình chưa có căn cứ xếp cuốn này vào sách dạy kỹ năng giao tiếp. "
                +(!source.isBlank()?"Phần giới thiệu nói về: "+ChatDialogueInterpreter.clean(source,400)+" ":"")
                +"Nội dung để suy ngẫm về cách ứng xử chưa đủ để khẳng định sách hướng dẫn hay có bài tập giao tiếp. "
                +"Nếu bạn muốn học kỹ năng này, mình sẽ ưu tiên sách có nội dung hướng dẫn được xác nhận rõ hơn.");
        }
        return answers;
    }

    static boolean asksStudyFeatures(String question,ChatIntentService language) {
        return has(language.normalize(question),"bai tap|bai thuc hanh|dap an|loi giai|huong dan giai");
    }

    static List<String> studyFeatures(Set<String> requested,Map<String,String> facts,ChatIntentService language) {
        List<String> answers=new ArrayList<>();
        String source=facts.getOrDefault("summary","")+"\n"+facts.getOrDefault("readingStyle","");
        ChatExplicitFilters confirmed=new ChatExplicitFilters(language);
        for(String field:List.of("exercises","answerKey")) {
            if(!requested.contains(field))continue;
            String evidence="";
            for(String sentence:source.split("[.!?;\\n]+")) {
                String normalized=language.normalize(sentence);
                boolean exercises=confirmed.affirmed(normalized,"bai tap") || confirmed.affirmed(normalized,"bai thuc hanh");
                boolean solution=List.of("dap an","loi giai","huong dan giai").stream().anyMatch(term->confirmed.affirmed(normalized,term));
                if(exercises && (field.equals("exercises") || solution)) {
                    evidence=ChatDialogueInterpreter.clean(sentence.trim(),350);break;
                }
            }
            String label=field.equals("exercises")?"bài tập thực hành":"đáp án hoặc lời giải cho bài tập";
            answers.add(evidence.isBlank()?"Về "+label+": mình chưa có thông tin đã xác nhận cho cuốn này, nên chưa thể trả lời là có hay không."
                :"Về "+label+": thông tin đã xác nhận có nêu “"+evidence+"”.");
        }
        return answers;
    }

    static boolean contentQuestion(String normalized) {
        return has(normalized,"truyen chu|truyen tranh|dang sach|the loai gi|thuoc the loai|la the loai|la truyen gi|noi dung|tom tat|ke ve|viet ve|noi ve|de cap|chu de|hoc nhung|day|huong dan|phan tich tinh huong|van ban luat|co chuong|co phai|co chac|can.*(?:toan|lap trinh)|gom|bao quat|giai doan|trinh bay|kieu gi|moi nhat|hien hanh|con dung.*phap luat|tap nao|tron bo|mot tap|tap [123]|rut gon|minh hoa|nghien cuu khoa hoc|chung minh");
    }

    static boolean focusedContentQuestion(String normalized) {
        return contentQuestion(normalized) && has(normalized,
            "(?:cuon|sach|quyen|ban|tap|bo) (?:do|nay|ay)|^sach|^(?:co|ban xac nhan|san pham o day).*(?:khong|tap|tron bo)")
            && !has(normalized,"tim|goi y|liet ke|co cuon|cuon nao khac|sach nao khac|gio hang|don hang|ma don|huy|doi tra|thanh toan|dat hang|dang nhap|phi giao|phi ship");
    }

    static boolean has(String normalized, String alternatives) {
        return normalized.matches("(?s).*\\b(?:" + alternatives + ")\\b.*");
    }

    static final Set<String> BIBLIOGRAPHY = Set.of("publisher", "publicationYear", "pageCount", "isbn");

    static String missingBibliography(Set<String> fields) {
        List<String> missing = new ArrayList<>();
        if (fields.contains("publisher")) missing.add("nhà xuất bản");
        if (fields.contains("publicationYear")) missing.add("năm xuất bản");
        if (fields.contains("pageCount")) missing.add("số trang");
        if (fields.contains("isbn")) missing.add("ISBN");
        return "Chưa có dữ liệu đã duyệt về " + String.join(", ", missing) + ".";
    }
}
