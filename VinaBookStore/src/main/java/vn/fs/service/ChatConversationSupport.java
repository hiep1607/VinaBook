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

    static boolean explanation(String message, ChatIntentService language) {
        String q=language.normalize(message);
        return ChatBookFacts.has(q,"tai sao|vi sao|ly do|giai thich|noi ro hon|noi ky hon|sau hon|chi tiet hon|phan tich them")
            && !ChatBookFacts.has(q,"tra hang|doi tra|hoan tien|don hang|gio hang|thanh toan|giao hang");
    }

    static boolean pluralReference(String message, ChatIntentService language) {
        return ChatBookFacts.has(language.normalize(message),"nhung cuon|cac cuon|may cuon|nhung sach|cac sach|danh sach|goi y do|lua chon do");
    }

    static String readerQuestion(ChatDialogueMemory.State state, String message) {
        state.clarificationFacts.clear();
        state.clarificationFacts.put("recipient",ChatDialogueInterpreter.clean(message,180));
        return "Sở thích đọc khác nhau ở từng người nên mình chưa thể chọn chỉ từ giới tính, và cửa hàng chưa có thống kê sách được yêu thích theo nhóm này. Bạn đang tìm cho người thích đọc chủ đề gì — chẳng hạn tình cảm, khám phá hay học một kỹ năng?";
    }
}
