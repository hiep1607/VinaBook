package vn.fs.service;
import com.fasterxml.jackson.databind.ObjectMapper;
/** Unambiguous cart commands shared by local intake and active chat tools. */
final class ChatCartCommands {
    static String cartTool(String message) {
        if(!ChatBookFacts.has(message,"gio|cart") || ChatBookFacts.has(message,"khong|chua|dung|lam sao|huong dan|cach"))return "";
        if(ChatBookFacts.has(message,"xoa|bo") && ChatBookFacts.has(message,"toan bo|tat ca"))return "prepareClearCart";
        if(message.matches("(?s).*\\b(?:thanh toan|dat hang|dat don|chot)\\s+(?:(?:toan bo|tat ca)\\s+)?gio(?: hang)?\\b.*")
                || message.matches("(?s).*\\bdat hang tu gio\\b.*"))return "prepareCheckout";
        if(ChatBookFacts.has(message,"xem gio|kiem tra gio|gio hang cua toi|gio.*bao nhieu|co gi trong gio")
                && !ChatBookFacts.has(message,"them|xoa|bo|doi|sua|mua|dat hang|thanh toan"))return "getCart";
        return "";
    }
    static com.fasterxml.jackson.databind.node.ObjectNode cartArguments(String tool) {
        var arguments=new ObjectMapper().createObjectNode();
        if("prepareCheckout".equals(tool))arguments.put("source","cart").put("reference","selection");
        return arguments;
    }
}
