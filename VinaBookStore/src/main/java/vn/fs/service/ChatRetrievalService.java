package vn.fs.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.entity.Product;

/**
 * Retrieval layer của RAG. Tìm trên catalog thật bằng phrase, token, category,
 * author, mô tả và nhóm từ đồng nghĩa; không sinh sản phẩm mới.
 */
@Service
public class ChatRetrievalService {
	private static final int MIN_ANCHOR_SCORE = 16;


	private final ChatIntentService intents;
	private vn.fs.repository.RateRepository rates;
	private BookKnowledgeService knowledge;
	@org.springframework.beans.factory.annotation.Autowired(required=false)
	public void setKnowledge(BookKnowledgeService value) { this.knowledge = value; }
    private final ThreadLocal<Boolean> topicPreparation=new ThreadLocal<>();
    void beginTopicPreparation(){topicPreparation.set(true);}
    void endTopicPreparation(){topicPreparation.remove();}
    java.util.Map<Long,List<String>> topicLabels(List<Product> catalog){return knowledge==null?java.util.Map.of():knowledge.topicLabels(catalog);}
    List<String> topicSuggestionNames(List<Product> catalog){return knowledge==null?List.of():knowledge.topicSuggestionNames(catalog);}
    private final ThreadLocal<List<Product>> currentCatalog=new ThreadLocal<>();
    List<Product> catalog(java.util.function.Supplier<List<Product>> load){List<Product> current=currentCatalog.get();return current==null?load.get():current;}
    void beginTurnRead(List<Product> catalog) { currentCatalog.set(catalog);if(knowledge!=null)knowledge.beginChatRead(catalog); }
    void endTurnRead() { currentCatalog.remove();if(knowledge!=null)knowledge.endChatRead(); }

	@org.springframework.beans.factory.annotation.Autowired
	public void setRates(vn.fs.repository.RateRepository rates) { this.rates = rates; }

	public List<Product> rated(List<Product> products, ChatSearchCriteriaDto criteria) {
		if (criteria.getMinRating() == null && !"rating".equals(criteria.getSort())) return products;
		java.util.Map<Long, Double> averages = new java.util.HashMap<>();
		if (rates != null) for (Object[] row : rates.averageRatings())
			averages.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
		java.util.stream.Stream<Product> stream = products.stream().filter(p -> averages.containsKey(p.getProductId())
				&& averages.get(p.getProductId()) >= (criteria.getMinRating() == null ? 0 : criteria.getMinRating()));
		if ("rating".equals(criteria.getSort())) stream = stream.sorted(Comparator
				.comparingDouble((Product p) -> averages.get(p.getProductId())).reversed().thenComparing(Product::getProductId));
		return stream.collect(Collectors.toList());
	}

	public ChatRetrievalService(ChatIntentService intents) {
		this.intents = intents;
	}

	public Double averageRating(Product product) {
		if (rates != null) for (Object[] row : rates.averageRatings())
			if (((Number) row[0]).longValue() == product.getProductId()) return ((Number) row[1]).doubleValue();
		return null;
	}

	public java.util.Map<Long,Double> ratingSnapshot() {
		java.util.Map<Long,Double> result = new java.util.HashMap<>();
		if (rates != null) for (Object[] row : rates.averageRatings())
			result.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
		return result;
	}

    /** Conservative subset used by the local dialogue. */
	public List<Product> directMatches(String question, ChatSearchCriteriaDto criteria,
			List<Product> candidates) {
		if (intents.isBudgetOnlyDiscovery(question) && criteria.getTopics().isEmpty()
				&& criteria.getCategory().isEmpty() && criteria.getAuthor().isEmpty()) {
			return candidates.stream().filter(p ->
					(criteria.getMinPrice() == null || effectivePrice(p) >= criteria.getMinPrice())
					&& (criteria.getMaxPrice() == null || effectivePrice(p) <= criteria.getMaxPrice()))
					.collect(Collectors.toList());
		}
		LinkedHashSet<String> anchors = new LinkedHashSet<>();
		for (String topic : criteria.getTopics()) addTerm(anchors, topic);
		addTerm(anchors, criteria.getCategory());
		addTerm(anchors, criteria.getAuthor());
		for (String token : intents.normalize(question).split("\\s+")) {
			if (token.length() >= 3 && !STOP_WORDS.contains(token)) anchors.add(token);
		}
		List<String> explicit = new ArrayList<>(anchors);
		return candidates.stream().filter(product -> relevance(product, explicit) >= MIN_ANCHOR_SCORE)
				.collect(Collectors.toList());
	}

	private int relevance(Product product, List<String> terms) {
		String name = intents.normalize(product.getName());
		String author = intents.normalize(product.getAuthor());
		String category = category(product);
		String description = intents.normalize(searchableText(product));
		int score = 0;
		for (String term : terms) {
			if (term.length() < 2) continue;
			boolean phrase = term.contains(" ");
			if (ChatCatalogQuery.word(name,term)) score += phrase ? 70 : 28;
			if (ChatCatalogQuery.word(author,term)) score += phrase ? 45 : 20;
			if (ChatCatalogQuery.word(category,term)) score += phrase ? 65 : 25;
			if (ChatCatalogQuery.word(description,term)) score += phrase ? 30 : 8;
		}
		return score;
	}

	public String searchableText(Product product) {
		return knowledge == null ? product.getName() + " " + product.getDescription()
				: knowledge.searchableText(product);
	}
    /** Content evidence for nearby recommendations excludes author names and broad categories. */
    public String descriptiveText(Product product) {
        return java.util.Objects.toString(product.getName(),"")+" "+java.util.Objects.toString(knowledge==null?product.getDescription():knowledge.approvedDescription(product),"");
    }

    public java.util.Map<String,String> approvedReadingFacts(Product product) {
        java.util.Map<String,String> facts=knowledge==null?java.util.Map.of():knowledge.approvedReadingFacts(product);
        return facts==null || facts.isEmpty()
            ?java.util.Map.of("summary",java.util.Objects.toString(product.getDescription(),"")):facts;
    }

	public List<Product> rankByMeaning(String query, List<Product> candidates) {
		return knowledge == null ? candidates : Boolean.TRUE.equals(topicPreparation.get())
            ?knowledge.rankByMeaning(query,candidates,false):knowledge.rankByMeaning(query,candidates);
	}

    public java.util.Map<Long,String> relatedEvidence(List<Long> seeds) {
        return knowledge==null?java.util.Map.of():knowledge.relatedEvidence(seeds);
    }

    public java.util.Map<Long,String> semanticEvidence(String query,List<Product> candidates) {
        return knowledge==null?java.util.Map.of():knowledge.semanticEvidence(query,candidates);
    }
    public BookKnowledgeService.SemanticRecall semanticRecall(String query,List<Product> candidates) {
        BookKnowledgeService.SemanticRecall result=knowledge==null?null:knowledge.semanticRecall(query,candidates);
        return result==null?new BookKnowledgeService.SemanticRecall(false,java.util.Map.of()):result;
    }

	private void addTerm(Set<String> values, String value) {
		String normalized = intents.normalize(value);
		if (normalized.length() >= 2) values.add(normalized);
	}

	private String category(Product product) {
		return product.getCategory() == null ? "" : intents.normalize(product.getCategory().getCategoryName());
	}

	public long effectivePrice(Product product) {
		double price = product.getPrice() == null ? 0 : product.getPrice();
		return Math.round(price * (100 - Math.max(0, product.getDiscount())) / 100);
	}

	private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
			"toi", "minh", "ban", "sach", "cuon", "quyen", "cho", "voi", "mot", "nhung", "cac", "cua",
			"la", "co", "khong", "nao", "gi", "ve", "tim", "muon", "can", "hay", "giup", "goi", "y",
			"gioi", "thieu", "phu", "hop", "danh", "doc", "mua", "duoc", "nhe", "a", "it", "vai",
			"con", "khac", "re", "hon", "nguoi", "moi", "bat", "dau", "hieu"));

}
