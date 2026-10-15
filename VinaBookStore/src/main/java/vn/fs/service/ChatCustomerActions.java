package vn.fs.service;

/** Explicit customer commands, distinct from a reading preference such as “mình thích trinh thám”. */
final class ChatCustomerActions {
    /** A single explicit refusal is safe to acknowledge without assuming any cart contents. */
    static boolean keepCartUnchanged(String q) {
        if(ChatBookFacts.has(q,"[?;,\\n]|\\b(?:neu|gia su|khi|va|nhung|roi|hay|them|mua|dat|sua|doi|cap nhat|thanh toan|xem|kiem tra|luu|chuyen|tai sao|vi sao)\\b"))return false;
        return q.matches("(?:ban )?(?:dung|khong)(?: can| duoc)? (?:xoa|bo) .{1,180} (?:khoi|trong) gio(?: hang)?(?: (?:nhe|nha|giup minh|giup toi))?[.!]*");
    }
    /** Only concrete commands bypass interpretation; general shopping advice still uses AI. */
    static String coreTool(String q) {
        String candidate=coreCandidate(q);
        if(java.util.Set.of("prepareCheckout","prepareCartChange","prepareClearCart").contains(candidate)
                && ChatBookFacts.has(q,"\\b(?:neu|gia su|co the|duoc khong|duoc ko|chua|khong|dung|khoan|khi nao|cach|huong dan|nut|o dau|la gi|the nao|chuc nang)\\b"))return "";
        if("getPolicy".equals(candidate) && changesReadingContext(q))return "";
        return candidate;
    }
    static boolean changesReadingContext(String q) {
        return ChatBookFacts.has(q,"(?:bo|xoa|doi|sua|thay|giu|van|khong gioi han).*(?:gioi han|gia|ngan sach|chu de|the loai|tac gia|muc doc|do tuoi)|gia.*khong (?:con )?quan trong|khong can.*(?:gia|ngan sach)");
    }
    private static String coreCandidate(String q) {
        if (ChatBookFacts.has(q,"khong mua|chua mua|chua can mua|dung mua|khong them|dung them|khong xoa|dung xoa|huong dan|lam sao|cach nao|cach mua|nen mua|goi y|tu van|tim sach")) return "";
        if (ChatBookFacts.has(q,"gio hang|(?:vao|trong|khoi|ca|toan bo|het|xem|kiem tra|thanh toan) gio|gio cua (?:minh|toi|ban)|(?:xoa|bo)(?: het| tat ca)? gio")) {
            if (ChatBookFacts.has(q,"xoa (?:het|tat ca|toan bo)|bo (?:het|tat ca|toan bo)")) return "prepareClearCart";
            if (ChatBookFacts.has(q,"them|bo vao|cho vao|xoa|bo khoi|sua|doi (?:so luong|thanh)|cap nhat")) return "prepareCartChange";
            if (ChatBookFacts.has(q,"dat hang|thanh toan|mua ngay")) return "prepareCheckout";
            if (ChatBookFacts.has(q,"xem|kiem tra|co gi|bao nhieu")) return "getCart";
        }
        if (ChatBookFacts.has(q,"don hang|don cua (?:toi|minh)|don (?:gan nhat|moi nhat)|don #?[0-9]")) {
            if (ChatBookFacts.has(q,"huy|doi tra|tra sach|mua lai|dat lai|nhan vien")) return "";
            if (ChatBookFacts.has(q,"den dau|trang thai|tra cuu|kiem tra|xem|cua toi|cua minh|giao chua|khi nao")) return "getOrderDetail";
        }
        if (!returnRequest(q) && ChatBookFacts.has(q,"phi ship|ca ship|bao nhieu.*ship|phi giao hang|phi van chuyen|mien phi.*(?:ship|van chuyen|giao hang)|\\bcod\\b|chinh sach (?:doi tra|van chuyen|thanh toan)|(?:doi tra|tra sach).*(?:bao nhieu ngay|duoc khong)|(?:giao hang|van chuyen).*(?:may ngay|bao lau)|(?:ho tro|co|nhan).*(?:vnpay|momo)|phuong thuc thanh toan")) return "getPolicy";
        if (ChatBookFacts.has(q,"mua ngay|dat ngay|(?:mua|dat) (?:[0-9][0-9.,]*|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi) (?:cuon|quyen|ban)|(?:mua|dat) (?:cuon|quyen|sach) (?:nay|do|thu|vua chon)")) return "prepareCheckout";
        return "";
    }
    static com.fasterxml.jackson.databind.node.ObjectNode coreArguments(String tool, String q) {
        var args = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        if ("prepareCartChange".equals(tool)) args.put("operation",ChatBookFacts.has(q,"xoa|bo khoi")?"remove":ChatBookFacts.has(q,"sua|doi (?:so luong|thanh)|cap nhat")?"update":"add");
        if ("prepareCheckout".equals(tool) && ChatBookFacts.has(q,"gi[o] hang|trong gio|ca gio|toan bo gio")) args.put("source","cart");
        if ("getOrderDetail".equals(tool)) {
            var id=java.util.regex.Pattern.compile("(?:#|\\b(?:ma )?don(?: hang)?(?: (?:cua (?:toi|minh)|so))?(?: la)?\\s*)([0-9]{1,15})\\b").matcher(q);
            if(id.find()) args.put("orderId",Long.parseLong(id.group(1)));
        }
        return args;
    }
    record Quantity(boolean explicit, Long value, String error) {}
    /** Read a single explicit book count without turning invalid/large input into one. */
    static Quantity quantity(String q) {
        String word="(?:mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin|muoi|tram|nghin|ngan|linh|le)";
        String tokenPattern="(?:-?[0-9]+(?:[.,][0-9]+)*|"+word+"(?: "+word+")*)";
        var pattern=java.util.regex.Pattern.compile("(?<![a-z0-9])("+tokenPattern+")\\s+(?:cuon|quyen|ban)(?=\\s|$|[.,!?])|\\bso luong\\s*(?:la|thanh|:)?\\s*("+tokenPattern+")|(?:\\bx\\s*|×\\s*)(-?[0-9]+(?:[.,][0-9]+)*)\\b");
        java.util.Set<Long> counts=new java.util.LinkedHashSet<>();
        var update=java.util.regex.Pattern.compile("\\b(?:doi|sua|cap nhat)\\b.*?\\b(?:thanh|con)\\s+("+tokenPattern+")\\b").matcher(q);
        if(update.find())q=q.substring(update.start(1))+" so luong "+update.group(1);
        var matcher=pattern.matcher(q);
        while(matcher.find()) {
            String token=matcher.group(1)!=null?matcher.group(1):matcher.group(2)!=null?matcher.group(2):matcher.group(3);
            if(Character.isLetter(token.charAt(0))) {
                long count=wordQuantity(token);
                if(count<1)return new Quantity(true,null,"Mình chưa xác định chắc số lượng viết bằng chữ. Bạn gửi lại số cuốn bằng chữ số nhé; giỏ hàng chưa được thay đổi.");
                counts.add(count);continue;
            }
            if(token.matches("-?[0-9]{1,3}(?:[.,][0-9]{3})+")) token=token.replace(".","").replace(",","");
            else if(token.contains(".") || token.contains(",")) return new Quantity(true,null,"Số lượng sách phải là số nguyên dương. Bạn cho mình biết lại số cuốn muốn chọn nhé.");
            try { long count=Long.parseLong(token); if(count<1) return new Quantity(true,null,"Số lượng sách phải từ 1 cuốn trở lên. Bạn cho mình biết lại số cuốn muốn chọn nhé."); counts.add(count); }
            catch(NumberFormatException ex) { return new Quantity(true,null,"Số lượng bạn nêu quá lớn để xử lý. Bạn cho mình biết lại số cuốn muốn chọn nhé."); }
        }
        if(counts.size()>1) return new Quantity(true,null,"Bạn nêu nhiều số lượng khác nhau. Bạn xác nhận một số lượng cuối cùng cho cuốn sách này nhé; mình chưa chuẩn bị thao tác với giỏ hàng.");
        if(counts.isEmpty() && ChatBookFacts.has(q,"so luong|(?:doi|sua|cap nhat).*thanh"))
            return new Quantity(true,null,"Mình chưa đọc chắc số lượng bạn nêu. Bạn gửi lại số cuốn bằng chữ số nhé; giỏ hàng chưa được thay đổi.");
        return counts.isEmpty()?new Quantity(false,null,""):new Quantity(true,counts.iterator().next(),"");
    }
    private static long wordQuantity(String token) {
        String unit="(?:mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin)";
        String underHundred="(?:(?:hai|ba|bon|nam|sau|bay|tam|chin) muoi(?: "+unit+")?|muoi(?: "+unit+")?|"+unit+")";
        String underThousand="(?:"+unit+" tram(?: (?:linh|le))?(?: "+underHundred+")?|"+underHundred+")";
        if(!token.matches("(?:"+underThousand+" (?:nghin|ngan)(?: "+underThousand+")?|"+underThousand+")"))return -1;
        java.util.Map<String,Integer> digits=java.util.Map.ofEntries(java.util.Map.entry("mot",1),java.util.Map.entry("hai",2),java.util.Map.entry("ba",3),java.util.Map.entry("bon",4),java.util.Map.entry("tu",4),java.util.Map.entry("nam",5),java.util.Map.entry("lam",5),java.util.Map.entry("sau",6),java.util.Map.entry("bay",7),java.util.Map.entry("tam",8),java.util.Map.entry("chin",9));
        long total=0,current=0,pending=0;
        for(String word:token.split(" ")) {
            if(digits.containsKey(word)) { current+=pending;pending=digits.get(word); }
            else if("tram".equals(word)) { current+=pending*100;pending=0; }
            else if("muoi".equals(word)) { current+=(pending==0?1:pending)*10;pending=0; }
            else if(java.util.Set.of("nghin","ngan").contains(word)) { total+=(current+pending)*1000;current=0;pending=0; }
        }
        return total+current+pending;
    }
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
        if (ChatBookFacts.has(q,"tim|goi y|tu van|nen doc|liet ke|muon sach|can sach|chon sach")
                && ChatBookFacts.has(q,"danh gia cao|danh gia tot|rating|(?:tren|tu|it nhat) [1-5](?:[.,][0-9]+)? sao")) return "";
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
