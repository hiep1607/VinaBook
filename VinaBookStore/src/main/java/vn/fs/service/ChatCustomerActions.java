package vn.fs.service;

/** Explicit customer commands, distinct from a reading preference such as “mình thích trinh thám”. */
final class ChatCustomerActions {
    static String tool(String q) {
        if (reorder(q)) return "prepareReorder";
        return wishlistTool(q);
    }
    static com.fasterxml.jackson.databind.node.ObjectNode arguments(String tool, String q) {
        var arguments = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        if ("prepareWishlistChange".equals(tool)) arguments.put("operation", wishlistOperation(q));
        if (java.util.Set.of("prepareReorder", "prepareReturnRequest", "prepareHumanSupport").contains(tool)) {
            var id = java.util.regex.Pattern.compile("(?:#|\\bdon(?: hang)?(?: so)?\\s*)([0-9]{1,15})\\b").matcher(q);
            if (id.find()) arguments.put("orderId", Long.parseLong(id.group(1)));
        }
        return arguments;
    }


    static String supportTool(String q) {
        if(ChatBookFacts.has(q,"yeu cau da gui|trang thai yeu cau|xem yeu cau|kiem tra yeu cau|phan hoi cua nhan vien|phan hoi tu nhan vien"))return "getSupportRequests";
        if(ChatBookFacts.has(q,"gui yeu cau ho tro|gap nhan vien|lien he nhan vien|noi chuyen voi nhan vien|gap tu van vien|gap nguoi that|chuyen.*nhan vien|nho nhan vien|can nhan vien"))return "prepareHumanSupport";
        return "";
    }
    static boolean reorder(String q) { return ChatBookFacts.has(q,"mua lai|dat lai") && ChatBookFacts.has(q,"don|lan truoc|da mua"); }
    static boolean returnRequest(String q) { return ChatBookFacts.has(q,"doi tra|tra sach|tra cuon|doi cuon|gui yeu cau doi") && ChatBookFacts.has(q,"don|yeu cau") && !ChatBookFacts.has(q,"chinh sach|dieu kien|lam sao|huong dan"); }
    static String wishlistTool(String q) {
        if (!ChatBookFacts.has(q,"yeu thich|wishlist")) return "";
        if (ChatBookFacts.has(q,"them|luu|bo|xoa") && !ChatBookFacts.has(q,"khong them|dung them|khong xoa|dung xoa|khong bo|dung bo")) return "prepareWishlistChange";
        if (ChatBookFacts.has(q,"xem|liet ke|danh sach|cua toi|cua minh|co nhung|co sach nao|co cuon nao")) return "getWishlist";
        return "";
    }
    static String wishlistOperation(String q) { return ChatBookFacts.has(q,"bo|xoa")?"remove":"add"; }
}
