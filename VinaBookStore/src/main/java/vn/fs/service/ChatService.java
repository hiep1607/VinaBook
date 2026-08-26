package vn.fs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.fs.dto.chat.ChatProductDto;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatStatusDto;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

@Service
public class ChatService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);

	private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
			"toi", "minh", "ban", "sach", "cuon", "quyen", "cho", "voi", "mot", "nhung",
			"cua", "la", "co", "khong", "nao", "gi", "ve", "tim", "muon", "can", "hay",
			"giup", "vai", "chu", "de", "phu", "hop", "danh", "goi", "y"));

	private final ProductRepository products;
	private final AiGateway ai;

	public ChatService(ProductRepository products, AiGateway ai) {
		this.products = products;
		this.ai = ai;
	}

	@Transactional(readOnly = true)
	public ChatResponseDto reply(ChatRequestDto request) {
		String question = request.getMessage().trim();
		List<Product> catalog = products.findByStatusTrue();
		List<Product> ranked = rankProducts(question, catalog);
		List<Product> suggestedProducts = selectSuggestedProducts(question, catalog, ranked);
		List<ChatProductDto> suggestions = suggestedProducts.stream()
				.map(this::toDto)
				.collect(Collectors.toList());
		List<Product> promptCatalog = suggestions.isEmpty() ? List.of() : suggestedProducts;

		boolean fallback = !ai.isConfigured();
		String answer;
		if (ai.isConfigured()) {
			try {
				answer = ai.complete(systemPrompt(promptCatalog, catalog), request.getHistory(), question);
			} catch (RuntimeException ex) {
				LOGGER.warn("AI provider {} failed; using local fallback ({})", ai.provider(),
						ex.getClass().getSimpleName());
				fallback = true;
				answer = localAnswer(question, suggestions, ranked);
			}
		} else {
			answer = localAnswer(question, suggestions, ranked);
		}

		return new ChatResponseDto(answer, fallback ? "local" : ai.provider(),
				fallback ? "" : ai.model(), fallback, suggestions, chips(question, suggestions));
	}

	public ChatStatusDto status() {
		return new ChatStatusDto(ai.isConfigured() ? ai.provider() : "local",
				ai.isConfigured() ? ai.model() : "", ai.isConfigured());
	}

	private String systemPrompt(List<Product> context, List<Product> fullCatalog) {
		StringBuilder catalog = new StringBuilder();
		for (Product product : context) {
			catalog.append("- ID ").append(product.getProductId())
					.append(": ").append(product.getName())
					.append(" | tác giả: ").append(value(product.getAuthor(), "Đang cập nhật"))
					.append(" | thể loại: ").append(category(product))
					.append(" | giá bán: ").append(effectivePrice(product)).append("đ")
					.append(" | tồn kho: ").append(product.getQuantity())
					.append(" | mô tả: ").append(shortDescription(product.getDescription())).append("\n");
		}
		String categories = fullCatalog.stream()
				.map(this::category)
				.filter(name -> !"Khác".equalsIgnoreCase(name))
				.distinct()
				.collect(Collectors.joining(", "));
		return "Bạn là trợ lý mua sách của VinaBook. Trả lời bằng tiếng Việt tự nhiên, thân thiện, tối đa 160 từ. "
				+ "Chỉ trả lời đúng điều khách đang hỏi; không tự gợi ý sách hoặc nhắc thêm chính sách không liên quan. "
				+ "Dùng văn bản thuần, không dùng Markdown, tiêu đề hay dấu **. "
				+ "Ưu tiên giúp khách chọn sách, hiểu chính sách và hoàn tất mua hàng. Chỉ khẳng định thông tin sản phẩm có trong danh sách catalog bên dưới; "
				+ "nếu không đủ dữ liệu hãy nói rõ và đề nghị khách mở trang sản phẩm hoặc liên hệ hỗ trợ. "
				+ "Không yêu cầu mật khẩu, mã OTP, số thẻ đầy đủ hoặc thông tin nhạy cảm. "
				+ "Chính sách cố định: phí vận chuyển 20.000đ, miễn phí từ 300.000đ; giao dự kiến 2 đến 4 ngày làm việc; "
				+ "đổi trả trong 7 ngày với sách lỗi in, rách hoặc giao sai; thanh toán gồm COD và các cổng trực tuyến đang được cửa hàng cấu hình. "
				+ "Cách mua: mở trang sách, thêm vào giỏ hoặc mua ngay, kiểm tra giỏ, nhập địa chỉ, chọn thanh toán và xác nhận. "
				+ "Khách đã đăng nhập xem trạng thái tại mục Đơn hàng; thông tin cá nhân và địa chỉ nằm trong trang cá nhân; hỗ trợ qua trang Liên hệ. "
				+ "Catalog hiện có " + fullCatalog.size() + " đầu sách đang hoạt động. Các thể loại hiện có: "
				+ value(categories, "Đang cập nhật") + ". "
				+ "Không tự tạo mã giảm giá, trạng thái đơn hàng hay cam kết tồn kho. Khi gợi ý sách, chỉ chọn sách trong CATALOG LIÊN QUAN và nêu ngắn gọn lý do phù hợp. "
				+ "Không đọc ID sản phẩm cho khách. Sau khi trả lời xong thì dừng; không tự mời đặt hàng, nhắc phí giao hàng, thanh toán hay liên hệ hỗ trợ nếu khách không hỏi.\n\n"
				+ "CATALOG LIÊN QUAN:\n" + catalog;
	}

	private List<Product> rankProducts(String question, List<Product> catalog) {
		String normalized = normalize(question);
		Set<String> tokens = Arrays.stream(normalized.split("\\s+"))
				.filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
				.collect(Collectors.toSet());
		boolean sale = containsAny(normalized, "giam gia", "khuyen mai", "sale", "gia tot");
		boolean newBooks = containsAny(normalized, "sach moi", "moi nhat", "vua ve");
		boolean popular = containsAny(normalized, "ban chay", "pho bien", "sach hay", "goi y", "nen doc");

		List<ScoredProduct> scored = new ArrayList<>();
		for (Product product : catalog) {
			String name = normalize(product.getName());
			String author = normalize(product.getAuthor());
			String category = normalize(category(product));
			String description = normalize(product.getDescription());
			int score = 0;
			for (String token : tokens) {
				if (name.contains(token)) score += 8;
				if (author.contains(token)) score += 6;
				if (category.contains(token)) score += 5;
				if (description.contains(token)) score += 1;
			}
			if (sale) score += product.getDiscount() * 2;
			if (popular) score += Math.min(50, Math.max(0, product.getSold()) / 2);
			if (newBooks && product.getEnteredDate() != null) score += product.getEnteredDate().getYear() - 2000;
			scored.add(new ScoredProduct(product, score));
		}
		scored.sort(Comparator.comparingInt(ScoredProduct::getScore).reversed()
				.thenComparing(item -> item.product.getSold(), Comparator.reverseOrder()));
		return scored.stream().map(item -> item.product).collect(Collectors.toList());
	}

	private List<Product> selectSuggestedProducts(String question, List<Product> catalog, List<Product> ranked) {
		String q = normalize(question);
		List<Product> namedProducts = catalog.stream()
				.filter(product -> isNamedInQuestion(q, product))
				.limit(4)
				.collect(Collectors.toList());
		if (!namedProducts.isEmpty()) return namedProducts;

		List<Product> catalogMatches = catalog.stream()
				.filter(product -> matchesAuthorOrCategory(q, product))
				.sorted(Comparator.comparingInt((Product product) -> ranked.indexOf(product)))
				.limit(4)
				.collect(Collectors.toList());
		if (!catalogMatches.isEmpty()) return catalogMatches;

		if (!isProductDiscoveryIntent(q)) return List.of();
		if (isTopicDiscoveryIntent(q)) {
			return ranked.stream()
					.filter(product -> topicRelevance(q, product) >= 4)
					.limit(4)
					.collect(Collectors.toList());
		}
		return ranked.stream().limit(4).collect(Collectors.toList());
	}

	private boolean isNamedInQuestion(String normalizedQuestion, Product product) {
		String name = normalize(product.getName());
		return name.length() >= 3 && normalizedQuestion.contains(name);
	}

	private boolean matchesAuthorOrCategory(String normalizedQuestion, Product product) {
		String author = normalize(product.getAuthor());
		String productCategory = normalize(category(product));
		boolean authorMatch = author.length() >= 4 && normalizedQuestion.contains(author);
		boolean categoryMatch = productCategory.length() >= 4
				&& !"khac".equals(productCategory)
				&& normalizedQuestion.contains(productCategory);
		return authorMatch || categoryMatch;
	}

	private boolean isProductDiscoveryIntent(String q) {
		boolean mentionsBook = containsAny(q, "sach", "cuon", "quyen", "tua");
		boolean asksToFind = containsAny(q, "tim", "goi y", "tu van", "de xuat", "recommend");
		boolean describesNeed = containsAny(q, "chu de", "the loai", "noi dung", "phu hop", "danh cho");
		boolean asksWhatToRead = containsAny(q, "doc gi", "mua gi", "nen doc", "nen mua", "co gi doc");
		return asksWhatToRead || (mentionsBook && (asksToFind || describesNeed)) || containsAny(q,
				"goi y sach", "tu van sach", "sach nao", "cuon nao", "nen doc", "nen mua",
				"tim sach", "tim cuon", "co sach", "danh sach sach", "sach ban chay",
				"sach moi", "moi nhat", "sach hay", "sach giam gia", "dang giam gia",
				"khuyen mai", "sach gia re", "sach re", "gia duoi", "gia tren", "tam gia",
				"sach ve", "cuon khac", "sach khac", "con cuon nao", "con sach nao");
	}

	private boolean isTopicDiscoveryIntent(String q) {
		return containsAny(q, "chu de", "noi dung", "sach ve", "cuon ve", "phu hop", "danh cho",
				"phieu luu", "giao tiep", "phat trien ban than", "tre em", "tre nho", "thieu nhi",
				"kinh doanh", "khoi nghiep", "trinh tham", "pha an", "tinh yeu", "lang man");
	}

	private int topicRelevance(String q, Product product) {
		String searchable = normalize(value(product.getName(), "") + " "
				+ value(product.getAuthor(), "") + " " + category(product) + " "
				+ value(product.getDescription(), ""));
		int score = (int) Arrays.stream(q.split("\\s+"))
				.filter(token -> token.length() > 2 && !STOP_WORDS.contains(token))
				.filter(searchable::contains)
				.count();
		boolean hasMappedTopic = containsAny(q, "phieu luu", "tre em", "tre nho", "phat trien ban than",
				"giao tiep", "ung xu", "kinh doanh", "khoi nghiep", "trinh tham", "pha an",
				"tinh yeu", "lang man");
		int semanticScore = 0;
		if (q.contains("phieu luu") && containsAny(searchable, "phieu luu", "hanh trinh", "kham pha", "du ky")) {
			semanticScore += 4;
		}
		if (containsAny(q, "tre em", "tre nho") && searchable.contains("thieu nhi")) semanticScore += 4;
		if (q.contains("phat trien ban than") && containsAny(searchable, "ky nang", "tam ly", "thoi quen")) semanticScore += 4;
		if (containsAny(q, "giao tiep", "ung xu") && containsAny(searchable, "giao tiep", "ung xu", "doi nhan xu the")) semanticScore += 4;
		if (containsAny(q, "kinh doanh", "khoi nghiep") && containsAny(searchable, "kinh doanh", "khoi nghiep", "kinh te")) semanticScore += 4;
		if (containsAny(q, "trinh tham", "pha an") && containsAny(searchable, "trinh tham", "dieu tra", "pha an")) semanticScore += 4;
		if (containsAny(q, "tinh yeu", "lang man") && containsAny(searchable, "tinh yeu", "lang man")) semanticScore += 4;
		return hasMappedTopic ? semanticScore : score;
	}

	private String localAnswer(String question, List<ChatProductDto> suggestions, List<Product> ranked) {
		String q = normalize(question);
		Product mentioned = mentionedProduct(q, ranked);
		if (mentioned != null && containsAny(q, "con bao nhieu", "bao nhieu ban", "bao nhieu cuon",
				"con may", "ton kho", "so luong con", "con hang", "het hang")) {
			if (mentioned.getQuantity() <= 0) {
				return "Theo dữ liệu hiện tại, “" + mentioned.getName() + "” đang hết hàng. Bạn có thể kiểm tra lại sau vì tồn kho có thể thay đổi.";
			}
			return "Theo dữ liệu hiện tại, “" + mentioned.getName() + "” còn " + mentioned.getQuantity()
					+ " cuốn. Số lượng có thể thay đổi khi có đơn hàng mới.";
		}
		if (mentioned != null && containsAny(q, "gia bao nhieu", "bao nhieu tien", "gia cua", "gia sach")) {
			String discount = mentioned.getDiscount() > 0 ? " sau khi giảm " + mentioned.getDiscount() + "%" : "";
			return "“" + mentioned.getName() + "” hiện có giá " + money(effectivePrice(mentioned)) + discount
					+ ". Giá được lấy từ catalog tại thời điểm trả lời.";
		}
		if (mentioned != null && containsAny(q, "giam bao nhieu", "giam may", "khuyen mai bao nhieu")) {
			return mentioned.getDiscount() > 0
					? "“" + mentioned.getName() + "” đang giảm " + mentioned.getDiscount() + "%, còn " + money(effectivePrice(mentioned)) + "."
					: "“" + mentioned.getName() + "” hiện không có giảm giá trong catalog.";
		}
		if (containsAny(q, "phi ship", "phi giao", "van chuyen", "giao hang", "bao lau nhan", "freeship")) {
			return "Phí vận chuyển là 20.000đ và được miễn phí khi giá trị đơn từ 300.000đ. Thời gian giao dự kiến từ 2 đến 4 ngày làm việc, tùy khu vực.";
		}
		if (containsAny(q, "thanh toan", "cod", "vietqr", "vnpay", "momo", "chuyen khoan")) {
			return "VinaBook hỗ trợ COD. VietQR, VNPay Sandbox và MoMo Test sẽ xuất hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng. API thanh toán xác minh mã đơn và số tiền ở backend.";
		}
		if (containsAny(q, "doi tra", "tra hang", "hoan tien", "sach loi", "sach rach", "giao sai")) {
			return "Bạn có thể yêu cầu đổi trả trong 7 ngày nếu sách lỗi in, bị rách hoặc giao sai. Hãy giữ sách nguyên vẹn và liên hệ hỗ trợ để được hướng dẫn.";
		}
		if (containsAny(q, "don hang", "theo doi don", "trang thai don")) {
			return "Bạn đăng nhập rồi mở mục Đơn hàng để xem trạng thái. Mình không thể tự xác nhận trạng thái đơn nếu chưa có dữ liệu tài khoản của bạn.";
		}
		if (containsAny(q, "mat khau", "otp", "so the", "cvv")) {
			return "Bạn không nên gửi mật khẩu, mã OTP hoặc thông tin thẻ trong khung chat. Nếu cần hỗ trợ tài khoản, hãy dùng chức năng quên mật khẩu hoặc trang Liên hệ.";
		}
		if (!suggestions.isEmpty()) {
			return "Mình đã chọn một số đầu sách phù hợp nhất từ catalog hiện tại. Bạn có thể mở từng cuốn bên dưới để xem mô tả, giá và tình trạng còn hàng.";
		}
		return "Mình có thể giúp bạn tìm sách theo thể loại, tác giả, khoảng giá hoặc giải đáp về giao hàng, thanh toán và đổi trả. Bạn thử mô tả thể loại hoặc mức giá mong muốn nhé.";
	}

	private List<String> chips(String question, List<ChatProductDto> suggestions) {
		String q = normalize(question);
		if (!suggestions.isEmpty()) return List.of("Còn cuốn khác không?", "Sách rẻ hơn");
		if (containsAny(q, "phi ship", "phi giao", "van chuyen", "giao hang", "freeship")) {
			return List.of("Có những cách thanh toán nào?", "Chính sách đổi trả");
		}
		if (containsAny(q, "thanh toan", "cod", "vietqr", "vnpay", "momo", "chuyen khoan")) {
			return List.of("Phí giao hàng thế nào?", "Cách đặt hàng?");
		}
		if (containsAny(q, "doi tra", "tra hang", "hoan tien", "sach loi", "sach rach", "giao sai")) {
			return List.of("Phí giao hàng thế nào?", "Liên hệ hỗ trợ");
		}
		return List.of();
	}

	private ChatProductDto toDto(Product product) {
		return new ChatProductDto(product.getProductId(), product.getName(), value(product.getAuthor(), "Đang cập nhật"),
				category(product), effectivePrice(product), product.getPrice() == null ? 0 : product.getPrice(),
				product.getDiscount(), product.getQuantity(), product.getImage());
	}

	private Product mentionedProduct(String normalizedQuestion, List<Product> ranked) {
		return ranked.stream()
				.filter(product -> isNamedInQuestion(normalizedQuestion, product))
				.findFirst()
				.orElse(null);
	}

	private String money(long amount) {
		return String.format(Locale.forLanguageTag("vi-VN"), "%,dđ", amount);
	}

	private long effectivePrice(Product product) {
		double price = product.getPrice() == null ? 0 : product.getPrice();
		return Math.round(price * (100 - Math.max(0, product.getDiscount())) / 100);
	}

	private String category(Product product) {
		return product.getCategory() == null ? "Khác" : value(product.getCategory().getCategoryName(), "Khác").trim();
	}

	private boolean containsAny(String value, String... options) {
		return Arrays.stream(options).anyMatch(value::contains);
	}

	private String normalize(String value) {
		if (value == null) return "";
		return Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.replace('đ', 'd').replace('Đ', 'D')
				.toLowerCase(Locale.ROOT).trim();
	}

	private String value(String value, String fallback) {
		return value == null || value.trim().isEmpty() ? fallback : value;
	}

	private String shortDescription(String description) {
		String cleaned = value(description, "Chưa có mô tả").replaceAll("\\s+", " ").trim();
		return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 237) + "...";
	}

	private static final class ScoredProduct {
		private final Product product;
		private final int score;
		private ScoredProduct(Product product, int score) { this.product = product; this.score = score; }
		private int getScore() { return score; }
	}
}
