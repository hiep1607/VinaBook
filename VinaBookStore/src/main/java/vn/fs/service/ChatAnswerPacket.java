package vn.fs.service;

import java.util.*;
import vn.fs.dto.chat.*;

/** Facts, evidence and limitations passed to the writer; product cards stay server-owned. */
final class ChatAnswerPacket {
    static Map<String,Object> build(ChatTool.Result result,String question,ChatDialogueMemory.State state,
            Map<Long,Map<String,String>> readingFacts,ChatIntentService language) {
        Map<String,Object> packet=new LinkedHashMap<>();
        packet.put("contextVersion",state.contextVersion);packet.put("intent",result.intent());
        Map<String,Object> conversation=new LinkedHashMap<>();
        conversation.put("latestMessage",question);conversation.put("semanticNotes",state.conversationNotes);
        conversation.put("focusedBookId",state.focus);
        conversation.put("recentTurns",state.recentDialogue.stream().skip(Math.max(0,state.recentDialogue.size()-6)).toList());
        packet.put("conversation",conversation);packet.put("confirmedNeed",state.toolSearch);
        List<Map<String,Object>> books=new ArrayList<>();
        ChatReadingFit fit=new ChatReadingFit(language);
        for(ChatProductDto book:result.products()) {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",book.getId());item.put("title",book.getName());item.put("author",book.getAuthor());
            item.put("salePriceVnd",book.getPrice());item.put("stockStatus",book.getQuantity()>0?"in_stock":"out_of_stock");
            Map<String,String> facts=readingFacts.getOrDefault(book.getId(),Map.of());
            List<Map<String,String>> evidence=new ArrayList<>();
            facts.forEach((field,text)->{if(!text.isBlank())evidence.add(Map.of("field",field,"source","approvedCatalog","text",ChatDialogueInterpreter.clean(text,2400)));});
            item.put("evidence",evidence);
            item.put("match",state.discoveryNearby.contains(book.getId())?"nearby":"selected");
            item.put("fitLimitations",fit.missing(state.toolSearch,facts));
            item.put("nearbyReason",state.discoveryReasons.getOrDefault(book.getId(),""));
            List<String> unknown=new ArrayList<>();
            for(String field:List.of("audience","readingLevel","readingStyle","learningGoal"))
                if(facts.getOrDefault(field,"").isBlank())unknown.add(field);
            item.put("unknown",unknown);books.add(item);
        }
        packet.put("result",Map.of("status",state.searchOutcome.isBlank()?"completed":state.searchOutcome,"books",books));
        packet.put("responseRequirements",Map.of("verifiedAnswer",result.answer(),
            "preserveServerCaveats",true,"mayInventFacts",false,"mayChangeSelection",false,
            "optionalRecipientAndBudget",true,"mayExecuteActions",false));
        return packet;
    }
}
