package vn.fs.service;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.fs.dto.chat.ChatRequestDto;

/** Physical cover details are separate from work-level descriptions and cover images. */
final class ChatBookEditionQuestions {
    private static final ChatIntentService LANGUAGE=new ChatIntentService();
    private ChatBookEditionQuestions() {}

    static boolean active(String question) {
        return ChatBookFacts.has(LANGUAGE.normalize(question),"bia cung|bia mem|hardcover|hardback|paperback|loai bia|dang bia");
    }

    static JsonNode plan(ChatRequestDto request,ChatDialogueMemory.State state,
            List<Map<String,Object>> facts,ObjectMapper mapper) {
        String q=LANGUAGE.normalize(request.getMessage());
        if(facts.isEmpty() || !active(request.getMessage())
                || ChatBookFacts.has(q,"tim|goi y|liet ke|sach nao|cuon nao|sach khac|cuon khac|them sach"))return null;
        // A cover clarification is a read of the referenced book. It cannot
        // authorize a purchase or silently substitute a similarly named book.
        ObjectNode plan=mapper.createObjectNode();
        plan.put("contextVersion",state.contextVersion).put("intent","PRODUCT_INFO").put("contextMode","current");
        plan.set("set",mapper.createObjectNode());plan.set("clear",mapper.createArrayNode());
        plan.put("evidence",ChatTurnContext.CURRENT_MESSAGE);plan.putNull("memoryNote");
        plan.set("calls",mapper.valueToTree(List.of(Map.of("tool","getProducts",
            "arguments",Map.of("reference","focus","field","detail")))));
        return plan;
    }

    static List<String> answers(String question) {
        if(!active(question))return List.of();
        // Neither Product nor the approved BookProfile has an edition-level
        // binding field. A work summary, title match or image cannot fill it.
        return List.of("Về loại bìa: mình chưa có thông tin đã xác nhận về bìa cứng hay bìa mềm của ấn bản đang bán, nên chưa thể khẳng định có đúng bản bạn cần.");
    }
}
