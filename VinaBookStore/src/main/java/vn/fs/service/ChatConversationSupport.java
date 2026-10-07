package vn.fs.service;

/** References and missing reader preferences shared by planning and local recovery. */
final class ChatConversationSupport {
    private ChatConversationSupport() { }

    static boolean audienceOnly(String message, ChatIntentService language) {
        String q=language.normalize(message);
        boolean audience=ChatBookFacts.has(q,"ban nu|phu nu|nu gioi|con gai|ban gai|nam gioi|dan ong|ban trai");
        boolean request=ChatBookFacts.has(q,"sach|cuon|doc|goi y|thich|ua chuong|ua thich|tang|qua");
        return audience && request && language.criteria(message,java.util.List.of()).getTopics().isEmpty()
            && !ChatBookFacts.has(q,"ngon tinh|truyen|tieu thuyet|tho|nau an|am thuc|hoi hoa|thiet ke|ngoai ngu|toan|vat ly|sinh hoc|sql|java|javascript");
    }

    static boolean giftDiscovery(String message, ChatIntentService language) {
        String q=language.normalize(message);
        return ChatBookFacts.has(q,"(?:mua|tim|chon|goi y).*(?:sach|truyen).*(?:tang|lam qua)")
            && (language.criteria(message,java.util.List.of()).getTopics().stream().anyMatch(t->!language.normalize(t).equals("lam qua"))
                || ChatBookFacts.has(q,"truyen|tieu thuyet|tho|nhe nhang|de doc|thu gian|thich doc"))
            && !ChatBookFacts.has(q,"cuon nay|cuon do|cuon ay|quyen nay|sach nay|sach do|gio hang|vao gio|dat hang|dat don|chot don");
    }

    static boolean suitabilityQuestion(String message, ChatIntentService language) {
        return ChatBookFacts.has(language.normalize(message),"co phu hop|co hop|doc duoc khong|danh cho ai|chua biet lap trinh");
    }

    static boolean followupComparison(String message, ChatIntentService language) {
        String q=language.normalize(message);
        return (ChatBookFacts.has(q,"khac gi|khac nhau|so sanh")
            || ChatBookFacts.has(q,"(?:cuon|sach|quyen) (?:do|nay|ay).*(?:hay|so voi|voi).*(?:hon|chon|hop)"))
            && !ChatBookFacts.has(q,"gio hang|don hang|thanh toan|dat hang|mua ngay");
    }

    static boolean explanation(String message, ChatIntentService language) {
        String q=language.normalize(message);
        return (ChatBookFacts.has(q,"tai sao|vi sao|ly do|giai thich|noi ro hon|noi ky hon|sau hon|chi tiet hon|phan tich them")
                || ChatBookFacts.has(q,"cuon nao.*(?:hop|sat|tot|de doc|do tuoi).*nhat|nen chon cuon nao"))
            && !ChatBookFacts.has(q,"tra hang|doi tra|hoan tien|don hang|gio hang|thanh toan|giao hang");
    }

    static boolean pluralReference(String message, ChatIntentService language) {
        return ChatBookFacts.has(language.normalize(message),"nhung cuon|cac cuon|may cuon|nhung sach|cac sach|danh sach|goi y do|lua chon do|trong so do|ca [0-9]+ cuon");
    }

    static String readerQuestion(ChatDialogueMemory.State state, String message) {
        state.clarificationFacts.clear();
        state.clarificationFacts.put("recipient",ChatDialogueInterpreter.clean(message,180));
        return "Mình đã biết bạn đang chọn sách cho ai. Để gợi ý hợp người đọc hơn, bạn cho mình biết người ấy thích chủ đề gì — chẳng hạn tình cảm, khám phá hay học một kỹ năng nhé. Sở thích đọc khác nhau ở từng người nên mình chưa thể chọn chỉ từ giới tính.";
    }
}
