package vn.fs.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.ChatActionDto;
import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatProductDto;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.dto.chat.ChatStatusDto;
import vn.fs.dto.chat.ChatTurnDto;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

@Service
public class ChatService {
	private final ProductRepository products;
	private final AiGateway ai;
	private final ChatIntentService intents;
	private final ChatQueryPlannerService planner;
	private final ChatRetrievalService retrieval;
	private final ChatGroundingService grounding;
	private final ChatPolicyService policies;
	private final ChatOrderSupportService orderSupport;
	private final ChatAnalyticsService analytics;
	private final CurrentUserAccess access;

	public ChatService(ProductRepository products, AiGateway ai, ChatIntentService intents,
			ChatQueryPlannerService planner, ChatRetrievalService retrieval,
			ChatGroundingService grounding, ChatPolicyService policies,
			ChatOrderSupportService orderSupport, ChatAnalyticsService analytics,
			CurrentUserAccess access) {
		this.products = products;
		this.ai = ai;
		this.intents = intents;
		this.planner = planner;
		this.retrieval = retrieval;
		this.grounding = grounding;
		this.policies = policies;
		this.orderSupport = orderSupport;
		this.analytics = analytics;
		this.access = access;
	}

	@Transactional(readOnly = true)
	public ChatResponseDto reply(ChatRequestDto request) {
		long started = System.currentTimeMillis();
		String question = request.getMessage().trim();
		List<Product> catalog = products.findByStatusTrue();
		ChatIntent baseIntent = intents.classify(question);
		ChatSearchCriteriaDto criteria = intents.criteria(question, catalog);
		String normalized = intents.normalize(question);
		if (isAlternativeRequest(normalized)) {
			criteria = followUpCriteria(criteria, request, catalog);
		}
		ChatSearchCriteriaDto explicitCriteria = criteria;
		ChatQueryPlan plan = planner.plan(question, request.getHistory(), catalog, baseIntent, criteria);
		ChatIntent intent = plan.getIntent();
		criteria = plan.getCriteria();
		List<Product> selected = retrieval.retrieve(question, plan, catalog,
				request.getContextProductIds(), isAlternativeRequest(normalized));
		if (intent == ChatIntent.UNKNOWN && !selected.isEmpty()) {
			List<Product> named = selected.stream().filter(product ->
					normalized.contains(intents.normalize(product.getName()))).collect(Collectors.toList());
			if (!named.isEmpty()) {
				intent = ChatIntent.PRODUCT_INFO;
				selected = named;
				plan = new ChatQueryPlan(intent, criteria, plan.getSearchTerms(),
						plan.getRequestedCount(), plan.isAiPlanned());
			}
		}
		if (isCategoryList(question)) selected = Collections.emptyList();

		boolean deterministic = intent == ChatIntent.POLICY || intent == ChatIntent.ORDER_SUPPORT
				|| intent == ChatIntent.SMALL_TALK || (intent == ChatIntent.UNKNOWN && selected.isEmpty())
				|| (intent == ChatIntent.DISCOVER_BOOKS && selected.isEmpty())
				|| isCategoryList(question) || isDeterministicProductInfo(question, intent, selected);
		boolean fallback = !ai.isConfigured();
		String provider = fallback || deterministic ? "local" : ai.provider();
		String model = fallback || deterministic ? "" : ai.model();
		String answer;

		if (intent == ChatIntent.ORDER_SUPPORT) {
			answer = orderSupport.latestOrders();
			fallback = false;
		} else if (intent == ChatIntent.POLICY) {
			answer = policies.answer(question);
			fallback = false;
		} else if (isCategoryList(question)) {
			answer = categoryAnswer(catalog);
			fallback = false;
		} else if (isDeterministicProductInfo(question, intent, selected)) {
			answer = productInfo(question, selected.get(0));
			fallback = false;
		} else if (intent == ChatIntent.SMALL_TALK
				|| ((intent == ChatIntent.UNKNOWN || intent == ChatIntent.DISCOVER_BOOKS) && selected.isEmpty())) {
			answer = localAnswer(question, intent, selected, criteria);
			fallback = false;
			fallback = false;
		} else if (ai.isConfigured()) {
			try {
				String raw = ai.complete(grounding.prompt(plan, selected, catalog.size()),
						request.getHistory(), question);
				ChatGroundingService.GroundedResult grounded = grounding.parse(raw, selected, intent);
				if (!grounded.isValid()) throw new IllegalStateException("AI không trả dữ liệu RAG hợp lệ");
				answer = grounded.getAnswer();
				selected = grounded.getProducts();
			} catch (RuntimeException ex) {
				fallback = true;
				provider = "local";
				model = "";
				if (intent == ChatIntent.DISCOVER_BOOKS) {
					selected = retrieval.directMatches(question, explicitCriteria, selected);
				}
				answer = localAnswer(question, intent, selected, criteria);
			}
		} else {
			answer = localAnswer(question, intent, selected, criteria);
		}

		List<ChatProductDto> productDtos = selected.stream().map(this::toDto).collect(Collectors.toList());
		List<String> chips = chips(intent, selected);
		List<ChatActionDto> actions = actions(intent, selected);
		String messageId = analytics.record(request.getSessionId(), intent, provider, fallback,
				selected.size(), System.currentTimeMillis() - started);
		return new ChatResponseDto(answer, provider, model, fallback, productDtos, chips,
				messageId, intent, criteria, actions);
	}

	public ChatStatusDto status() {
		return new ChatStatusDto(ai.isConfigured() ? ai.provider() : "local",
				ai.isConfigured() ? ai.model() : "", ai.isConfigured());
	}

	private String localAnswer(String question, ChatIntent intent, List<Product> selected,
			ChatSearchCriteriaDto criteria) {
		if (intent == ChatIntent.COMPARE_BOOKS) return comparison(selected);
		if (intent == ChatIntent.PRODUCT_INFO && !selected.isEmpty()) return productInfo(question, selected.get(0));
		if (intent == ChatIntent.DISCOVER_BOOKS) {
			if (selected.isEmpty()) return "Mình chưa tìm thấy sách khớp hoàn toàn. Bạn thử nới khoảng giá hoặc mô tả rõ người đọc và chủ đề nhé.";
			String need = criteria.getTopics().isEmpty() ? "nhu cầu của bạn" : String.join(", ", criteria.getTopics());
			StringBuilder answer = new StringBuilder("Mình đã kiểm tra catalog và chọn các sách phù hợp với ")
					.append(need).append(":");
			for (Product product : selected.stream().limit(5).collect(Collectors.toList())) {
				answer.append("\n\n• “").append(product.getName()).append("” — ")
						.append(money(retrieval.effectivePrice(product))).append(", ")
						.append(product.getQuantity() > 0 ? "còn " + product.getQuantity() + " cuốn" : "hiện hết hàng")
						.append(". ").append(shortDescription(product.getDescription()));
			}
			return answer.toString();
		}
		if (intent == ChatIntent.SMALL_TALK) {
			String q = intents.normalize(question);
			if (q.contains("cam on")) return "Rất vui được giúp bạn. Hãy mô tả người đọc, chủ đề và ngân sách để mình chọn sách chính xác hơn.";
			if (q.contains("ban la ai") || q.contains("lam duoc gi")) return "Mình là trợ lý mua sách VinaBook. Mình có thể tìm, so sánh sách, tư vấn theo ngân sách, giải đáp chính sách và tra cứu đơn hàng sau khi bạn đăng nhập.";
			return "Chào bạn! Bạn đang tìm sách cho bản thân hay làm quà? Hãy cho mình biết chủ đề và ngân sách nhé.";
		}
		return "Mình chưa hiểu rõ yêu cầu. Bạn có thể hỏi: “Tìm sách tâm lý dưới 150k”, “So sánh hai cuốn” hoặc “Đơn hàng của tôi đang ở đâu?”.";
	}

	private String productInfo(String question, Product product) {
		String q = intents.normalize(question);
		if (contains(q, "con hang", "ton kho", "bao nhieu ban", "bao nhieu cuon", "con may")) {
			return product.getQuantity() > 0
					? "Theo dữ liệu hiện tại, “" + product.getName() + "” còn " + product.getQuantity() + " cuốn. Số lượng có thể thay đổi khi có đơn hàng mới."
					: "“" + product.getName() + "” hiện đang hết hàng.";
		}
		if (contains(q, "gia", "bao nhieu tien", "giam")) {
			return "“" + product.getName() + "” hiện có giá " + money(retrieval.effectivePrice(product))
					+ (product.getDiscount() > 0 ? ", giảm " + product.getDiscount() + "% từ " + money(Math.round(product.getPrice())) : ".");
		}
		String description = product.getDescription() == null || product.getDescription().trim().isEmpty()
				? "Mô tả đang được cập nhật." : product.getDescription().trim();
		return "“" + product.getName() + "” của " + value(product.getAuthor(), "tác giả đang cập nhật") + ". " + description;
	}

	private String comparison(List<Product> selected) {
		if (selected.size() < 2) return "Bạn hãy nêu rõ ít nhất hai tên sách muốn so sánh, ví dụ: “So sánh Nhà Giả Kim và Đắc Nhân Tâm”.";
		StringBuilder answer = new StringBuilder("So sánh nhanh:\n");
		for (Product product : selected) {
			answer.append("• ").append(product.getName()).append(": ")
					.append(money(retrieval.effectivePrice(product))).append(", ")
					.append(category(product)).append(", ")
					.append(product.getQuantity() > 0 ? "còn hàng" : "hết hàng").append(". ")
					.append(shortDescription(product.getDescription())).append("\n");
		}
		answer.append("Nếu ưu tiên ngân sách, chọn cuốn có giá thấp hơn; nếu ưu tiên nội dung, mở từng trang sách để xem mô tả chi tiết.");
		return answer.toString();
	}

	private List<ChatActionDto> actions(ChatIntent intent, List<Product> selected) {
		List<ChatActionDto> result = new ArrayList<>();
		for (Product product : selected) {
			result.add(new ChatActionDto("VIEW_PRODUCT", "Xem " + product.getName(), product.getProductId(), "/product/" + product.getProductId(), false));
			if (product.getQuantity() > 0) result.add(new ChatActionDto("ADD_TO_CART", "Thêm vào giỏ", product.getProductId(), "", true));
		}
		if (intent == ChatIntent.ORDER_SUPPORT) {
			if (access.currentEmail() == null) result.add(new ChatActionDto("LOGIN", "Đăng nhập", null, "/login", false));
			else result.add(new ChatActionDto("VIEW_ORDERS", "Xem đơn hàng", null, "/orders", false));
		}
		return result;
	}

	private List<String> chips(ChatIntent intent, List<Product> selected) {
		if (!selected.isEmpty()) return List.of("Còn cuốn khác không?", "So sánh các cuốn này", "Sách rẻ hơn");
		if (intent == ChatIntent.POLICY) return List.of("Phí giao hàng thế nào?", "Có những cách thanh toán nào?", "Chính sách đổi trả");
		if (intent == ChatIntent.ORDER_SUPPORT) return List.of("Đơn hàng của tôi", "Cách đặt hàng?");
		return List.of("Gợi ý sách dưới 150k", "Tìm sách làm quà", "Có những thể loại nào?");
	}

	private ChatProductDto toDto(Product product) {
		return new ChatProductDto(product.getProductId(), product.getName(), value(product.getAuthor(), "Đang cập nhật"),
				category(product), retrieval.effectivePrice(product), product.getPrice() == null ? 0 : product.getPrice(),
				product.getDiscount(), product.getQuantity(), product.getImage());
	}

	private String categoryAnswer(List<Product> catalog) {
		String categories = catalog.stream().map(this::category).filter(value -> !"Khác".equals(value)).distinct().sorted().collect(Collectors.joining(", "));
		return "VinaBook hiện có các thể loại: " + categories + ". Bạn có thể chọn một thể loại và thêm ngân sách để mình gợi ý chính xác hơn.";
	}

	private boolean isCategoryList(String value) {
		String q = intents.normalize(value);
		return contains(q, "co nhung the loai", "the loai nao", "danh sach the loai");
	}

	private boolean isDeterministicProductInfo(String question, ChatIntent intent, List<Product> selected) {
		if (intent != ChatIntent.PRODUCT_INFO || selected.isEmpty()) return false;
		String q = intents.normalize(question);
		return contains(q, "con hang", "ton kho", "bao nhieu ban", "bao nhieu cuon", "con may",
				"gia", "bao nhieu tien", "giam bao nhieu", "khuyen mai");
	}

	private boolean isAlternativeRequest(String normalized) {
		return contains(normalized, "cuon khac", "sach khac", "con cuon", "re hon");
	}

	private ChatSearchCriteriaDto followUpCriteria(ChatSearchCriteriaDto current, ChatRequestDto request,
			List<Product> catalog) {
		ChatSearchCriteriaDto previous = request.getHistory().stream()
				.filter(turn -> "user".equals(turn.getRole()))
				.map(ChatTurnDto::getContent)
				.reduce((first, second) -> second)
				.map(message -> intents.criteria(message, catalog))
				.orElse(ChatSearchCriteriaDto.empty());
		return new ChatSearchCriteriaDto(
				current.getMinPrice() != null ? current.getMinPrice() : previous.getMinPrice(),
				current.getMaxPrice() != null ? current.getMaxPrice() : previous.getMaxPrice(),
				current.getCategory().isEmpty() ? previous.getCategory() : current.getCategory(),
				current.getAuthor().isEmpty() ? previous.getAuthor() : current.getAuthor(),
				current.getSort().isEmpty() ? previous.getSort() : current.getSort(),
				current.getTopics().isEmpty() ? previous.getTopics() : current.getTopics());
	}

	private boolean contains(String value, String... options) {
		for (String option : options) if (value.contains(option)) return true;
		return false;
	}

	private String category(Product product) {
		return product.getCategory() == null ? "Khác" : value(product.getCategory().getCategoryName(), "Khác");
	}

	private String value(String value, String fallback) {
		return value == null || value.trim().isEmpty() ? fallback : value.trim();
	}

	private String shortDescription(String value) {
		String description = value(value, "Chưa có mô tả").replaceAll("\\s+", " ");
		return description.length() <= 150 ? description : description.substring(0, 147) + "...";
	}

	private String money(long amount) {
		return String.format(Locale.forLanguageTag("vi-VN"), "%,dđ", amount);
	}
}
