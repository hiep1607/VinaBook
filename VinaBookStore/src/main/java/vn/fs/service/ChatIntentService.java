package vn.fs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.entity.Product;

@Service
public class ChatIntentService {
	private static final List<String> TOPICS = Arrays.asList(
			"phiêu lưu", "giao tiếp", "phát triển bản thân", "thiếu nhi", "kinh doanh",
			"khởi nghiệp", "trinh thám", "tình yêu", "lãng mạn", "lịch sử", "tâm lý",
			"khoa học", "công nghệ", "kỹ năng", "làm quà", "pháp luật", "pháp lý",
			"chính trị", "giáo trình", "tâm linh", "tôn giáo", "văn học");

	public ChatIntent classify(String question) {
		String q = normalize(question);
		if (containsAny(q, "so sanh", "khac nhau", "nen chon cuon nao", "cuon nao tot hon")) {
			return ChatIntent.COMPARE_BOOKS;
		}
		if (containsAny(q, "don hang", "theo doi don", "ma don", "dang giao", "don cua toi")) {
			return ChatIntent.ORDER_SUPPORT;
		}
		if (containsAny(q, "phi ship", "phi giao", "van chuyen", "giao hang", "doi tra",
				"hoan tien", "thanh toan", "cod", "vnpay", "momo", "vietqr", "bao hanh",
				"lien he", "cua hang", "chinh sach")) {
			return ChatIntent.POLICY;
		}
		if (containsAny(q, "sach gia duoi", "sach gia tren", "tim sach gia duoi", "tim sach gia tren")) {
			return ChatIntent.DISCOVER_BOOKS;
		}
		if (containsAny(q, "gia ", "gia bao nhieu", "con hang", "ton kho", "bao nhieu ban", "bao nhieu cuon",
				"con may", "noi dung", "tac gia", "giam bao nhieu", "danh gia cuon", "cuon nay", "sach nay")) {
			return ChatIntent.PRODUCT_INFO;
		}
		if (containsAny(q, "tim sach", "tim cuon", "goi y", "tu van", "nen doc", "nen mua",
				"doc gi", "sach hay", "sach moi", "ban chay", "khuyen mai", "gia duoi",
				"gia tren", "danh cho", "phu hop", "lam qua", "the loai", "sach ve", "chu de",
				"phieu luu", "giao tiep", "phat trien ban than", "cuon khac", "sach khac", "re hon",
				"phap luat", "phap ly", "sach luat", "luat dan su", "luat doanh nghiep")) {
			return ChatIntent.DISCOVER_BOOKS;
		}
		if (q.length() <= 80 && (containsAny(q, "xin chao", "chao", "hello", "cam on",
				"tam biet", "ban la ai", "ban lam duoc gi") || containsWord(q, "hi"))) {
			return ChatIntent.SMALL_TALK;
		}
		return ChatIntent.UNKNOWN;
	}

	public ChatSearchCriteriaDto criteria(String question, List<Product> catalog) {
		String q = normalize(question);
		Long minPrice = null;
		Long maxPrice = null;
		List<Long> amounts = amounts(q);
		boolean range = amounts.size() >= 2 && containsAny(q, "tu ", " den ", "khoang", "giua");
		if (range) {
			minPrice = Math.min(amounts.get(0), amounts.get(1));
			maxPrice = Math.max(amounts.get(0), amounts.get(1));
		} else if (!amounts.isEmpty() && containsAny(q, "tren", "hon", "it nhat", "toi thieu")) {
			minPrice = amounts.get(0);
		} else if (!amounts.isEmpty()) {
			maxPrice = amounts.get(0);
		}

		String category = catalog.stream()
				.filter(product -> product.getCategory() != null && product.getCategory().getCategoryName() != null)
				.map(product -> product.getCategory().getCategoryName())
				.distinct()
				.filter(name -> q.contains(normalize(name)))
				.findFirst().orElse("");
		String author = catalog.stream().map(Product::getAuthor).filter(value -> value != null && value.length() > 3)
				.filter(value -> q.contains(normalize(value))).findFirst().orElse("");
		String sort = containsAny(q, "re nhat", "re hon", "gia tang", "gia thap", "tiet kiem") ? "price-asc"
				: containsAny(q, "dat nhat", "gia giam", "cao cap") ? "price-desc"
				: containsAny(q, "moi nhat", "sach moi", "vua ve") ? "new"
				: containsAny(q, "giam gia", "khuyen mai", "sale") ? "sale"
				: containsAny(q, "ban chay", "pho bien", "hot") ? "best" : "";
		List<String> topics = new ArrayList<>();
		for (String topic : TOPICS) {
			if (q.contains(normalize(topic))) topics.add(topic);
		}
		return new ChatSearchCriteriaDto(minPrice, maxPrice, category, author, sort, topics);
	}

	public String normalize(String value) {
		if (value == null) return "";
		return Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.replace('đ', 'd').replace('Đ', 'D')
				.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", " ").trim();
	}

	private List<Long> amounts(String q) {
		List<Long> values = new ArrayList<>();
		Matcher matcher = Pattern.compile("(\\d[\\d.,]*)\\s*(trieu|tr|k|nghin|ngan|d|dong|vnd)?").matcher(q);
		while (matcher.find()) {
			String raw = matcher.group(1).replaceAll("[.,]", "");
			try {
				long value = Long.parseLong(raw);
				String unit = matcher.group(2);
				String tail = q.substring(matcher.end()).trim();
				if (unit == null && value <= 12 && tail.matches("^(sach|cuon|quyen)\\b.*")) continue;
				if ("trieu".equals(unit) || "tr".equals(unit)) value *= 1_000_000;
				else if ("k".equals(unit) || "nghin".equals(unit) || "ngan".equals(unit) || value < 1000) value *= 1000;
				if (value > 0) values.add(value);
			} catch (NumberFormatException ignored) {
				// Ignore malformed amounts and keep classifying the rest of the sentence.
			}
		}
		return values;
	}

	private boolean containsAny(String value, String... options) {
		return Arrays.stream(options).anyMatch(value::contains);
	}

	private boolean containsWord(String value, String word) {
		return Pattern.compile("(^|\\s)" + Pattern.quote(word) + "($|\\s)").matcher(value).find();
	}
}
