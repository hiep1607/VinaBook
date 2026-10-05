package vn.fs.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.entity.Product;

@Service
public class ChatProductSearchService {
	private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
			"toi", "minh", "ban", "sach", "cuon", "quyen", "cho", "voi", "mot", "nhung",
			"cua", "la", "co", "khong", "nao", "gi", "ve", "tim", "muon", "can", "hay",
			"giup", "vai", "chu", "de", "phu", "hop", "danh", "goi", "y", "so", "sanh"));

	private final ChatIntentService intents;

	public ChatProductSearchService(ChatIntentService intents) {
		this.intents = intents;
	}

	public List<Product> search(String question, ChatIntent intent, ChatSearchCriteriaDto criteria,
			List<Product> catalog) {
		String q = intents.normalize(question);
		List<Product> named = catalog.stream().filter(product -> {
			String name = intents.normalize(product.getName());
            return name.length() >= 3 && ChatCatalogQuery.word(q,name)
                    && ChatCatalogQuery.titleMention(question,product.getName(),intents);
		}).limit(intent == ChatIntent.COMPARE_BOOKS ? 3 : 1).collect(Collectors.toList());
		if (!named.isEmpty() && (intent == ChatIntent.COMPARE_BOOKS || intent == ChatIntent.PRODUCT_INFO)) {
			return named;
		}

		List<Product> filtered = new ArrayList<>(catalog);
		if (!criteria.getCategory().isEmpty()) {
			filtered = filtered.stream().filter(product -> product.getCategory() != null
					&& criteria.getCategory().equalsIgnoreCase(product.getCategory().getCategoryName()))
					.collect(Collectors.toList());
		}
		if (!criteria.getAuthor().isEmpty()) {
			filtered = filtered.stream().filter(product -> criteria.getAuthor().equalsIgnoreCase(product.getAuthor()))
					.collect(Collectors.toList());
		}
		if (criteria.getMinPrice() != null) {
			filtered = filtered.stream().filter(product -> effectivePrice(product) >= criteria.getMinPrice())
					.collect(Collectors.toList());
		}
		if (criteria.getMaxPrice() != null) {
			filtered = filtered.stream().filter(product -> effectivePrice(product) <= criteria.getMaxPrice())
					.collect(Collectors.toList());
		}

		List<String> tokens = Arrays.stream(q.split("\\s+"))
				.filter(token -> token.length() > 1 && !STOP_WORDS.contains(token)).collect(Collectors.toList());
		Comparator<Product> ranked = Comparator.comparingInt((Product product) -> score(product, tokens, criteria)).reversed()
				.thenComparing(Product::getSold, Comparator.reverseOrder());
		filtered.sort(ranked);
		applySort(filtered, criteria.getSort());

		if (intent == ChatIntent.DISCOVER_BOOKS) {
			boolean hasSemanticNeed = !criteria.getTopics().isEmpty() || !criteria.getCategory().isEmpty()
					|| !criteria.getAuthor().isEmpty() || criteria.getMinPrice() != null || criteria.getMaxPrice() != null;
			if (hasSemanticNeed) {
				filtered = filtered.stream().filter(product -> (!criteria.getTopics().isEmpty()
							? topicScore(product, criteria.getTopics()) > 0 : score(product, tokens, criteria) > 0)
						|| !criteria.getCategory().isEmpty() || !criteria.getAuthor().isEmpty())
						.collect(Collectors.toList());
			}
			return filtered.stream().limit(4).collect(Collectors.toList());
		}
		return named;
	}

	private int score(Product product, List<String> tokens, ChatSearchCriteriaDto criteria) {
		String name = intents.normalize(product.getName());
		String author = intents.normalize(product.getAuthor());
		String category = product.getCategory() == null ? "" : intents.normalize(product.getCategory().getCategoryName());
		String description = intents.normalize(product.getDescription());
		String searchable = name + " " + author + " " + category + " " + description;
		int score = 0;
		for (String token : tokens) {
			if (name.contains(token)) score += 8;
			if (author.contains(token)) score += 6;
			if (category.contains(token)) score += 5;
			if (description.contains(token)) score += 2;
		}
		score += topicScore(product, criteria.getTopics());
		return score;
	}

	private int topicScore(Product product, List<String> topics) {
		String category = product.getCategory() == null ? "" : intents.normalize(product.getCategory().getCategoryName());
		String searchable = intents.normalize(product.getName()) + " " + intents.normalize(product.getAuthor())
				+ " " + category + " " + intents.normalize(product.getDescription());
		int score = 0;
		for (String topic : topics) {
			String normalized = intents.normalize(topic);
			if (searchable.contains(normalized)) score += 14;
			if ("giao tiep".equals(normalized) && containsAny(searchable, "ung xu", "doi nhan xu the", "thuyet phuc", "ket noi con nguoi")) score += 10;
			if ("phieu luu".equals(normalized) && containsAny(searchable, "hanh trinh", "kham pha", "du ky", "tham hiem")) score += 10;
			if ("phat trien ban than".equals(normalized) && containsAny(searchable, "ky nang song", "tam ly", "thoi quen", "tu duy", "ban than")) score += 10;
			if ("thieu nhi".equals(normalized) && containsAny(searchable, "tre em", "cho be", "co tich", "dong thoai")) score += 10;
			if ("kinh doanh".equals(normalized) && containsAny(searchable, "doanh nghiep", "quan tri", "marketing", "tai chinh")) score += 10;
			if ("khoi nghiep".equals(normalized) && containsAny(searchable, "startup", "lap nghiep", "doanh nhan")) score += 10;
			if ("trinh tham".equals(normalized) && containsAny(searchable, "pha an", "tham tu", "toi pham", "bi an")) score += 10;
			if (("tinh yeu".equals(normalized) || "lang man".equals(normalized))
					&& containsAny(searchable, "tinh cam", "tinh yeu", "lang man")) score += 10;
			if ("lich su".equals(normalized) && containsAny(searchable, "chien tranh", "trieu dai", "viet nam xua")) score += 10;
			if ("tam ly".equals(normalized) && containsAny(searchable, "cam xuc", "hanh vi", "noi tam")) score += 10;
			if ("khoa hoc".equals(normalized) && containsAny(searchable, "vu tru", "sinh hoc", "vat ly", "hoa hoc")) score += 10;
			if ("cong nghe".equals(normalized) && containsAny(searchable, "lap trinh", "phan mem", "du lieu", "may tinh")) score += 10;
			if ("ky nang".equals(normalized) && containsAny(searchable, "ky nang", "thoi quen", "tu duy", "ung xu")) score += 10;
			if ("lam qua".equals(normalized) && product.getQuantity() > 0) score += Math.min(8, Math.max(1, product.getSold() / 10));
		}
		return score;
	}

	private void applySort(List<Product> products, String sort) {
		if ("price-asc".equals(sort)) products.sort(Comparator.comparingLong(this::effectivePrice));
		else if ("price-desc".equals(sort)) products.sort(Comparator.comparingLong(this::effectivePrice).reversed());
		else if ("new".equals(sort)) products.sort(Comparator.comparing(Product::getEnteredDate,
				Comparator.nullsLast(Comparator.reverseOrder())));
		else if ("sale".equals(sort)) products.sort(Comparator.comparingInt(Product::getDiscount).reversed());
		else if ("best".equals(sort)) products.sort(Comparator.comparingInt(Product::getSold).reversed());
	}

	public long effectivePrice(Product product) {
		double price = product.getPrice() == null ? 0 : product.getPrice();
		return Math.round(price * (100 - Math.max(0, product.getDiscount())) / 100);
	}

	private boolean containsAny(String value, String... options) {
		return Arrays.stream(options).anyMatch(value::contains);
	}
}
