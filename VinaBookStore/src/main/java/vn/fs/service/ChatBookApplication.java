package vn.fs.service;

/** Recognizes an application question without turning it into a new catalog search. */
final class ChatBookApplication {
    static boolean requested(String question, ChatIntentService language) {
        String q=language.normalize(question);
        return ChatBookFacts.has(q,"vi du|minh hoa|ap dung|ung dung|nen noi (?:cau )?gi|noi cau gi|xu ly tinh huong")
            && !language.asksForBookSuggestions(question)
            && !ChatBookFacts.has(q,"goi y|tim sach|tim cuon|gioi thieu sach")
            && !ChatBookFacts.has(q,"dat hang|mua ngay|gio hang|huy don|doi tra|thanh toan|phi giao|giao hang");
    }
}
