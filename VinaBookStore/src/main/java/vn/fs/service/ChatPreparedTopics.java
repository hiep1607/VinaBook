package vn.fs.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.entity.Product;

/** Conversation-owned, bounded previews. Never serves saved price, stock or product DTOs. */
final class ChatPreparedTopics {
    private static final ObjectMapper mapper=new ObjectMapper();
    static final long TTL_MS=5*60*1000L;
    record Selection(long preparedAt,String constraints,List<Long> rankedIds,Map<Long,String> revisions) {}
    private ChatPreparedTopics() {}

    static String constraints(Map<String,Object> filters) {
        Map<String,Object> confirmed=new TreeMap<>();
        for(String key:ChatTurnContext.FIELDS)if(filters.containsKey(key))confirmed.put(key,filters.get(key));
        try {return mapper.writeValueAsString(confirmed);}
        catch(Exception ex){throw new IllegalStateException("Cannot encode prepared constraints",ex);}
    }
    static Selection prepare(Map<String,Object> filters,List<Product> ranked,ChatRetrievalService data) {
        Map<Long,String> revisions=new LinkedHashMap<>();
        Map<Long,Double> ratings=data.ratingSnapshot();
        Map<Long,List<String>> labels=data.topicLabels(ranked);
        ranked.forEach(book->revisions.put(book.getProductId(),revision(book,data,ratings,labels)));
        return new Selection(System.currentTimeMillis(),constraints(filters),
            ranked.stream().map(Product::getProductId).toList(),revisions);
    }
    static List<Product> take(ChatDialogueMemory.State state,List<Product> current,ChatRetrievalService data) {
        if(state.preparedTopicChoice.isBlank())return null;
        Selection saved=state.preparedTopics.get(state.preparedTopicChoice);
        state.preparedTopics.clear();
        state.preparedTopicStatus="miss";
        if(saved==null || saved.preparedAt()>System.currentTimeMillis()
            || System.currentTimeMillis()-saved.preparedAt()>TTL_MS
            || !saved.constraints().equals(constraints(state.toolSearch)))return null;
        Map<Long,Product> live=new HashMap<>();current.forEach(book->live.put(book.getProductId(),book));
        Map<Long,Double> ratings=data.ratingSnapshot();
        Map<Long,List<String>> labels=data.topicLabels(current);
        List<Product> ranked=new ArrayList<>();
        for(Long id:saved.rankedIds()) {
            Product book=live.get(id);
            if(book==null || !Boolean.TRUE.equals(book.getStatus()) || book.getQuantity()<1
                || !Objects.equals(saved.revisions().get(id),revision(book,data,ratings,labels)))return null;
            ranked.add(book);
        }
        if(ranked.isEmpty())return null;
        state.preparedTopicStatus="hit";
        return ranked;
    }
    private static String revision(Product book,ChatRetrievalService data,Map<Long,Double> ratings,Map<Long,List<String>> labels) {
        // Revisions invalidate previews when price/stock/content changes. These
        // hashes are not a second source of catalog facts for the response.
        Map<String,Object> facts=new TreeMap<>();
        facts.put("name",Objects.toString(book.getName(),""));facts.put("author",Objects.toString(book.getAuthor(),""));
        facts.put("description",Objects.toString(book.getDescription(),""));
        facts.put("category",book.getCategory()==null?"":Objects.toString(book.getCategory().getCategoryName(),""));
        facts.put("price",data.effectivePrice(book));facts.put("quantity",book.getQuantity());
        facts.put("sold",book.getSold());facts.put("date",Objects.toString(book.getEnteredDate(),""));
        facts.put("reading",new TreeMap<>(data.approvedReadingFacts(book)));
        facts.put("labels",labels.getOrDefault(book.getProductId(),List.of()).stream().sorted().toList());
        facts.put("search",data.searchableText(book));facts.put("rating",Objects.toString(ratings.get(book.getProductId()),""));
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(mapper.writeValueAsString(facts).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("Cannot verify prepared books",ex);}
    }
}
