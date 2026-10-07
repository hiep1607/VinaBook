package vn.fs.service;

/** Explicit customer commands, distinct from a reading preference such as “mình thích trinh thám”. */
final class ChatCustomerActions {
    static String tool(String q) {
        String support = supportTool(q);
        if (!support.isEmpty()) return support;
        String reviewMode=reviewMode(q);
        if (!reviewMode.isEmpty()) return "bookReviews";
        if (reorder(q)) return "prepareReorder";
        if (returnRequest(q)) return "prepareReturnRequest";
        return wishlistTool(q);
    }
    static com.fasterxml.jackson.databind.node.ObjectNode arguments(String tool, String q) {
        var arguments = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        if ("bookReviews".equals(tool)) arguments.put("mode", reviewMode(q));
        if ("prepareWishlistChange".equals(tool)) arguments.put("operation", wishlistOperation(q));
        if (java.util.Set.of("prepareReorder", "prepareReturnRequest", "prepareHumanSupport").contains(tool)) {
            var id = java.util.regex.Pattern.compile("(?:#|\\bdon(?: hang)?(?: so)?\\s*)([0-9]{1,15})\\b").matcher(q);
            if (id.find()) arguments.put("orderId", Long.parseLong(id.group(1)));
        }
        return arguments;
    }
    static String tool(String q, ChatDialogueMemory.State state, boolean namedBook) {
        String explicit = tool(q);
        if (!explicit.isEmpty()) {
            if (!"bookReviews".equals(explicit)) clearReview(state);
            return explicit;
        }
        boolean titleReply = namedBook && !ChatBookFacts.has(q,"tim|goi y|tu van|mua|dat|them|xoa|so sanh|chinh sach|don hang|cam on");
        if (!state.pendingReviewMode.isBlank() && (titleReply || !shortReviewMode(q).isEmpty()
                || q.matches("(?:chon )?(?:(?:cuon|sach|quyen) )?(?:(?:thu|so) )?(?:[1-8]|dau tien|dau|nhat|hai|ba|cuoi)(?: nhe| nha)?")))
            return "bookReviews";
        clearReview(state);
        return "";
    }
    static com.fasterxml.jackson.databind.node.ObjectNode arguments(String tool, String q, ChatDialogueMemory.State state) {
        var args = arguments(tool, q);
        if (!"bookReviews".equals(tool)) return args;
        if (reviewMode(q).isEmpty()) {
            String replyMode = shortReviewMode(q);
            args.put("mode", replyMode.isEmpty() ? state.pendingReviewMode : replyMode);
            if (state.pendingReviewProductId != null) args.put("productId", state.pendingReviewProductId);
            var ordinal = java.util.regex.Pattern.compile("^(?:chon )?(?:(?:cuon|sach|quyen) )?(?:(?:thu|so) )?([1-8]|dau tien|dau|nhat|hai|ba|cuoi)(?: nhe| nha)?$").matcher(q);
            if (ordinal.matches()) {
                String value = ordinal.group(1);
                int index = "cuoi".equals(value) ? state.displayed.size()-1
                    : java.util.Set.of("dau tien","dau","nhat").contains(value) ? 0
                    : "hai".equals(value) ? 1 : "ba".equals(value) ? 2 : Integer.parseInt(value)-1;
                args.remove("productId");
                args.put("index", Math.max(1,index+1));
            }
        }
        return args;
    }
    static String shortReviewMode(String q) {
        String answer = q.replaceFirst("^(?:minh |toi |cho minh |cho toi |muon |chi )+", "").replaceFirst("(?: thoi| nhe| nha| a)$", "");
        if (answer.matches("(?:xem|doc)(?: (?:nhan xet|danh gia|review))?")) return "read";
        if (answer.matches("(?:viet|gui|sua|cap nhat)(?: (?:nhan xet|danh gia|review))?")) return "write";
        return "";
    }
    static void clearReview(ChatDialogueMemory.State state) {
        state.pendingReviewMode = "";
        state.pendingReviewProductId = null;
    }
    static String reviewMode(String q) {
        if (!ChatBookFacts.has(q,"danh gia|nhan xet|review|rating|bao nhieu sao|may sao|diem trung binh|doc gia noi|[1-5] sao")) return "";
        if (ChatBookFacts.has(q,"tim|goi y|tu van|nen doc|liet ke")
                && ChatBookFacts.has(q,"danh gia cao|danh gia tot|rating|(?:tren|tu|it nhat) [1-5] sao")) return "";
        if (!ChatBookFacts.has(q,"sach|cuon|quyen|tac pham|dau sach|doc gia")
                && ChatBookFacts.has(q,"don hang|yeu cau ho tro|nhan vien|cau tra loi")) return "";
        boolean reading=ChatBookFacts.has(q,"(?:xem|doc|co nhung|co bao nhieu|tong quan)(?: cac| nhung| phan)? (?:danh gia|nhan xet|review)|nhan xet.*the nao|danh gia.*the nao|doc gia.*noi gi|bao nhieu sao|may sao|sao.*khong|duoc danh gia|diem trung binh");
        boolean writing=ChatBookFacts.has(q,"(?:viet|gui|dang|cap nhat|chinh sua|sua|chia se|de lai)(?: mot| phan| bai)? (?:nhan xet|danh gia|review)|(?:muon|de toi|de minh) danh gia|(?:minh|toi) (?:danh gia|cham|cho).*sao");
        if (reading && writing) return "clarify";
        if (writing) return "write";
        if (reading) return "read";
        return "clarify";
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
