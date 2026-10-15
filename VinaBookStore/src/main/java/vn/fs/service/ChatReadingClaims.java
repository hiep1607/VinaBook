package vn.fs.service;

import java.util.*;

/** Checks claim families against the corresponding approved reading fields, including synonyms. */
final class ChatReadingClaims {
    private final ChatIntentService language;
    ChatReadingClaims(ChatIntentService language) { this.language=language; }
    boolean unsupported(String text,Map<String,String> facts) {
        String source=language.normalize(String.join(" ",facts.values()));
        String level=language.normalize(facts.getOrDefault("summary","")+" "+facts.getOrDefault("readingLevel","")+" "+facts.getOrDefault("readingStyle",""));
        String audience=language.normalize(facts.getOrDefault("audience",""));
        for(String sentence:text.split("[.!?;\\n]+")) {
            String q=language.normalize(sentence).trim().replace("muc tieu hoc","muc dich hoc");
            // Explicit uncertainty is useful; it is not a positive reading claim.
            if(q.matches("^(?:phan mo ta |mo ta |du lieu |cua hang |minh )?(?:hien )?(?:chua|khong du|khong the|khong co du).*"))continue;
            // Editorial praise is not evidence of content or reader fit, even
            // when copied from promotional text. Keep the concrete description.
            if(ChatBookFacts.has(q,"gia tri cao|rat co gia tri|vo gia|dang doc|rat hay|tuyet voi|xuat sac|ly tuong|toi uu|khong the bo qua|lua chon hoan hao|phu hop nhat"))return true;
            if(ChatBookFacts.has(q,"(?:co ban|can ban|nen tang|nhap mon)(?: de| cho| khi)? (?:ban )?bat dau|nguon tu lieu co ban|tai lieu nen tang")
                    && List.of("người mới","mới bắt đầu","nhập môn","cơ bản","căn bản","nền tảng")
                        .stream().noneMatch(term->new ChatExplicitFilters(language).affirmed(level,language.normalize(term))))return true;
            if(!q.contains("chua")) {
                if(ChatCatalogQuery.word(q,"truyen chu") && !new ChatReadingRequest(language).proseStory(source))return true;
                ChatExplicitFilters formEvidence=new ChatExplicitFilters(language);
                for(String form:List.of("tieu thuyet","truyen dai","truyen ngan","truyen tranh"))
                    if(formEvidence.affirmed(q,form) && !formEvidence.affirmed(source,form))return true;
            }
            if(ChatBookFacts.has(q,"phu nu.*(?:thich|hop)|ban nu.*(?:thich|hop)|nu gioi.*(?:thich|hop)|nam gioi.*(?:thich|hop)|duoc.*ua thich|ban chay"))return true;
            if(ChatBookFacts.has(q,"(?:hop|phu hop|danh cho|giup|ho tro).*(?:nguoi moi|moi bat dau|bat dau)|de bat dau.*(?:hoc|nghien cuu)")
                    && List.of("người mới","mới bắt đầu","nhập môn","cơ bản","căn bản")
                        .stream().noneMatch(term->new ChatExplicitFilters(language).affirmed(level,language.normalize(term))))return true;
            for(String basic:List.of("khái niệm nền tảng","kiến thức nền tảng","kiến thức cơ bản"))
                if(ChatCatalogQuery.word(q,language.normalize(basic))
                        && !new ChatExplicitFilters(language).affirmed(level,language.normalize(basic)))return true;
            if(ChatBookFacts.has(q,"de doc|de hieu|khong.*ap luc khi doc|theo doi.*thuan tien|de dang (?:doc|theo doi|tiep can|lam quen|ap dung|dong cam)|khong.*choang ngop|de tiep can|de hinh dung|de tiep nhan|khong.*nang ne|thay vi.*phuc tap|doc.*khong.*mat.*thoi gian|phu hop.*(?:lam quen|tap doc|it doc|moi doc|bat dau doc)|(?:lam quen|tap doc|bat dau).*thoi quen doc|giup.*(?:lam quen|tap doc)")
                    && !ChatBookFacts.has(level,"de doc|de hieu|ngon ngu don gian|ngon ngu gian di|de tiep can|de dang ap dung"))return true;
            if(ChatBookFacts.has(q,"khong.*(?:giao dieu|huong dan ky nang|giao trinh)|khong mang mau sac")
                    && !ChatBookFacts.has(source,"khong.*(?:giao dieu|huong dan ky nang|giao trinh)|khong mang mau sac"))return true;
            if(ChatBookFacts.has(q,"khong (?:de cap|ban ve|chua dung)|khong.*lien quan|hoan toan.*(?:tach biet|khac biet)|giai tri (?:thuan tuy|don thuan)|thuan tuy.*(?:giai tri|ve giai tri)|tranh xa.*(?:chu de|nguyen tac)|tranh.*(?:cac|nhung) (?:noi dung|chu de|nguyen tac)")
                    && !ChatBookFacts.has(source,"khong (?:de cap|ban ve|chua dung)|hoan toan tach biet|giai tri thuan tuy"))return true;
            if(ChatBookFacts.has(q,"cong cu cu the|co che thuc hanh|tinh huong cu the|vi du cu the|phuong phap thuc hanh")
                    && !new ChatReadingFit(language).confirmed("practical",facts))return true;
            if(ChatBookFacts.has(q,"giup (?:be|tre)|ren luyen.*(?:be|tre)|phong phu.*(?:tu duy|ngon ngu)|phat trien.*(?:tu duy|ngon ngu|tri tuong tuong)|kich thich tu duy")
                    && !ChatBookFacts.has(source,"phat trien.*(?:tu duy|ngon ngu|tri tuong tuong)|ren luyen|nuoi duong"))return true;
            if(ChatBookFacts.has(q,"(?:hop|phu hop|danh cho|ly tuong cho|doi tuong).*(?:be|tre|tuoi)|do tuoi.*(?:hop|phu hop)|doc gia nho tuoi")
                    && audience.isBlank())return true;
            if(ChatBookFacts.has(q,"lap ngan sach|chi tieu hang thang")
                    && !ChatBookFacts.has(source,"lap ngan sach|chi tieu hang thang"))return true;
            if(ChatBookFacts.has(q,"quan ly cam xuc|kiem soat cam xuc") && !ChatBookFacts.has(source,"quan ly cam xuc|kiem soat cam xuc"))return true;
            if(ChatBookFacts.has(q,"nhe nhang") && !new ChatReadingFit(language).confirmed("gentle",facts))return true;
            if(ChatBookFacts.has(q,"nhip ke|tiet tau ke") && !ChatBookFacts.has(source,"nhip ke|tiet tau ke"))return true;
            for(String fact:List.of("chuyen sau","nang cao","ngan gon","hoc sinh","tieu hoc","trung hoc","tre em","thieu nhi","co dien","van phong","java","python","javascript","spring boot","bat ky","bat buoc","loi ke","ke chuyen nhanh","phong cach","kich tinh","truong thanh","chuong trinh hoc","hoc phan"))
                if(ChatCatalogQuery.word(q,fact) && !ChatCatalogQuery.word(source,fact))return true;
            for(String fact:List.of("bai tap","cac buoc","ky thuat","thuc hanh"))
                if(ChatCatalogQuery.word(q,fact) && !ChatCatalogQuery.word(source,fact)
                        && !(fact.equals("bai tap") && ChatBookFacts.has(q,"truoc khi chuyen sang bai tap")))return true;
        }
        return false;
    }
}
