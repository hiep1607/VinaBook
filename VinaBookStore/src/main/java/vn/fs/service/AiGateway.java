package vn.fs.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.config.AiSettings;
import vn.fs.dto.chat.ChatTurnDto;

@Service
public class AiGateway {
	private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

	private static final String OPENAI = "openai";
	private static final String OPENAI_COMPATIBLE = "openai-compatible";
	private static final String GEMINI = "gemini";
	private static final String ANTHROPIC = "anthropic";

	private final AiSettings settings;
	private final ObjectMapper mapper;
	private final HttpClient client;
	private final Map<String, Long> geminiKeyCooldowns = new ConcurrentHashMap<>();
	private final Map<String, Long> geminiModelCooldowns = new ConcurrentHashMap<>();
	private final AtomicInteger nextGeminiKey = new AtomicInteger();
	private final ThreadLocal<String> modelUsed = new ThreadLocal<>();

	public AiGateway(AiSettings settings, ObjectMapper mapper) {
		this.settings = settings;
		this.mapper = mapper;
		this.client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(Math.max(3, settings.getTimeoutSeconds())))
				.build();
	}

	public boolean isConfigured() {
		String provider = provider();
		if (!(OPENAI.equals(provider) || OPENAI_COMPATIBLE.equals(provider)
				|| GEMINI.equals(provider) || ANTHROPIC.equals(provider))) {
			return false;
		}
		if (blank(settings.getModel()) || (GEMINI.equals(provider)
				? settings.getGeminiApiKeys().isEmpty() : blank(settings.getApiKey()))) {
			return false;
		}
		return !OPENAI_COMPATIBLE.equals(provider) || !blank(settings.getBaseUrl());
	}

	public String provider() {
		return value(settings.getProvider(), "local").trim().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	public String model() {
		return value(settings.getModel(), "").trim();
	}

	public String modelUsed() {
		String used = modelUsed.get();
		return used == null ? model() : used;
	}

	public void resetModelUsed() {
		modelUsed.remove();
	}

	public String complete(String systemPrompt, List<ChatTurnDto> history, String message) {
		modelUsed.set(model());
		if (!isConfigured()) {
			throw new IllegalStateException("Nhà cung cấp AI chưa được cấu hình đầy đủ");
		}
		switch (provider()) {
		case OPENAI:
			return callOpenAi(systemPrompt, history, message);
		case OPENAI_COMPATIBLE:
			return callOpenAiCompatible(systemPrompt, history, message);
		case GEMINI:
			return callGemini(systemPrompt, history, message);
		case ANTHROPIC:
			return callAnthropic(systemPrompt, history, message);
		default:
			throw new IllegalStateException("Nhà cung cấp AI không được hỗ trợ");
		}
	}

	private String callOpenAi(String systemPrompt, List<ChatTurnDto> history, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model());
		body.put("instructions", systemPrompt);
		body.put("input", conversation(history, message, false));
		body.put("max_output_tokens", safeMaxTokens());
		body.put("store", false);
		JsonNode json = post(join(defaultBase("https://api.openai.com/v1"), "responses"), body,
				Map.of("Authorization", "Bearer " + settings.getApiKey()));
		List<String> text = new ArrayList<>();
		for (JsonNode output : json.path("output")) {
			for (JsonNode content : output.path("content")) {
				if (content.path("text").isTextual()) text.add(content.path("text").asText());
			}
		}
		return requireText(text);
	}

	private String callOpenAiCompatible(String systemPrompt, List<ChatTurnDto> history, String message) {
		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(message("system", systemPrompt));
		messages.addAll(conversation(history, message, false));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model());
		body.put("messages", messages);
		body.put("max_tokens", safeMaxTokens());
		body.put("temperature", safeTemperature());
		body.put("stream", false);
		JsonNode json = post(join(settings.getBaseUrl(), "chat/completions"), body,
				Map.of("Authorization", "Bearer " + settings.getApiKey()));
		JsonNode content = json.path("choices").path(0).path("message").path("content");
		if (content.isTextual()) return requireText(List.of(content.asText()));
		List<String> text = new ArrayList<>();
		for (JsonNode part : content) {
			if (part.path("text").isTextual()) text.add(part.path("text").asText());
		}
		return requireText(text);
	}

	private String callGemini(String systemPrompt, List<ChatTurnDto> history, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
		List<Map<String, Object>> contents = new ArrayList<>();
		for (Map<String, String> turn : conversation(history, message, true)) {
			contents.add(Map.of("role", turn.get("role"), "parts", List.of(Map.of("text", turn.get("content")))));
		}
		body.put("contents", contents);
		// Các nơi gọi gateway đều mong đợi JSON; yêu cầu Gemini trả đúng định dạng
		// để tránh tiêu thêm một lượt API chỉ vì markdown hoặc JSON sai cú pháp.
		body.put("generationConfig", Map.of("maxOutputTokens", safeMaxTokens(),
				"responseMimeType", "application/json"));
		String primary = model();
		String backup = value(settings.getFallbackModel(), "").trim();
		if (backup.isEmpty() || backup.equalsIgnoreCase(primary)) {
			return callGeminiModel(primary, body, false);
		}
		if (geminiModelCooldowns.getOrDefault(primary, 0L) <= System.currentTimeMillis()) {
			try {
				return callGeminiModel(primary, body, true);
			} catch (AiHttpException ex) {
				if (!isTemporaryModelFailure(ex.status)) throw ex;
				if (ex.status == 503) geminiModelCooldowns.put(primary, System.currentTimeMillis() + 60_000L);
				log.warn("gemini_model_fallback primary={} backup={} status={}", primary, backup, ex.status);
			} catch (IllegalStateException ex) {
				log.warn("gemini_model_fallback primary={} backup={} reason={}", primary, backup,
						ex.getClass().getSimpleName());
			}
		}
		return callGeminiModel(backup, body, false);
	}

	private boolean isTemporaryModelFailure(int status) {
		return status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
	}

	private String callGeminiModel(String targetModel, Map<String, Object> body, boolean stopOn503) {
		String endpoint = "models/" + URLEncoder.encode(targetModel, StandardCharsets.UTF_8) + ":generateContent";
		String url = join(defaultBase("https://generativelanguage.googleapis.com/v1beta"), endpoint);
		IllegalStateException lastFailure = null;
		List<String> keys = settings.getGeminiApiKeys();
		int firstKey = Math.floorMod(nextGeminiKey.getAndIncrement(), keys.size());
		for (int offset = 0; offset < keys.size(); offset++) {
			int keyIndex = (firstKey + offset) % keys.size();
			String key = keys.get(keyIndex);
			String cooldownKey = targetModel + "\u0000" + key;
			if (geminiKeyCooldowns.getOrDefault(cooldownKey, 0L) > System.currentTimeMillis()) continue;
			try {
				JsonNode json = post(url, body, Map.of("x-goog-api-key", key), 3);
				if (json == null) throw new IllegalStateException("Gemini trả về JSON rỗng");
				List<String> text = new ArrayList<>();
				for (JsonNode candidate : json.path("candidates")) {
					for (JsonNode part : candidate.path("content").path("parts")) {
						if (!part.path("thought").asBoolean(false) && part.path("text").isTextual())
							text.add(part.path("text").asText());
					}
				}
				String answer = String.join("\n", text).trim();
				if (answer.isEmpty()) {
					lastFailure = new IllegalStateException("Gemini không trả về nội dung");
					geminiKeyCooldowns.put(cooldownKey, System.currentTimeMillis() + 15_000L);
					log.warn("gemini_key_failure slot={} reason=empty_response finishReason={}", keyIndex + 1,
							json.path("candidates").path(0).path("finishReason").asText("unknown"));
					continue;
				}
				geminiKeyCooldowns.remove(cooldownKey);
				modelUsed.set(targetModel);
				return answer;
			} catch (AiHttpException ex) {
				if (!canTryNextGeminiKey(ex)) throw ex;
				lastFailure = ex;
				log.warn("gemini_key_failure slot={} reason=http status={}", keyIndex + 1, ex.status);
				long now = System.currentTimeMillis();
				long cooldownUntil = ex.dailyQuota ? nextGeminiDailyReset()
						: now + (ex.status == 429 ? 60_000L
						: (ex.invalidKey || ex.status == 401 || ex.status == 402 || ex.status == 403 ? 600_000L : 15_000L));
				geminiKeyCooldowns.put(cooldownKey, cooldownUntil);
				if (stopOn503 && ex.status == 503) break;
			} catch (IllegalStateException ex) {
				lastFailure = ex;
				geminiKeyCooldowns.put(cooldownKey, System.currentTimeMillis() + 15_000L);
				log.warn("gemini_key_failure slot={} reason=invalid_response exceptionType={}",
						keyIndex + 1, ex.getClass().getSimpleName());
			}
		}
		if (lastFailure != null) throw lastFailure;
		throw new IllegalStateException("Các khóa Gemini đang tạm thời bị giới hạn; vui lòng thử lại sau");
	}

	private boolean canTryNextGeminiKey(AiHttpException ex) {
		int status = ex.status;
		return ex.invalidKey || status == 401 || status == 402 || status == 403 || status == 408 || status == 429
				|| status == 500 || status == 502 || status == 503 || status == 504;
	}

	private long nextGeminiDailyReset() {
		ZoneId pacific = ZoneId.of("America/Los_Angeles");
		return LocalDate.now(pacific).plusDays(1).atStartOfDay(pacific).toInstant().toEpochMilli();
	}

	private String callAnthropic(String systemPrompt, List<ChatTurnDto> history, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model());
		body.put("system", systemPrompt);
		body.put("messages", conversation(history, message, false));
		body.put("max_tokens", safeMaxTokens());
		body.put("temperature", safeTemperature());
		JsonNode json = post(join(defaultBase("https://api.anthropic.com/v1"), "messages"), body,
				Map.of("x-api-key", settings.getApiKey(), "anthropic-version", "2023-06-01"));
		List<String> text = new ArrayList<>();
		for (JsonNode part : json.path("content")) {
			if ("text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
				text.add(part.path("text").asText());
			}
		}
		return requireText(text);
	}

	private JsonNode post(String url, Object body, Map<String, String> headers) {
		return post(url, body, headers, 1);
	}

	private JsonNode post(String url, Object body, Map<String, String> headers, int maxAttempts) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(3, settings.getTimeoutSeconds())))
					.header("Content-Type", "application/json");
			headers.forEach(request::header);
			HttpRequest prepared = request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
			for (int attempt = 0; attempt < maxAttempts; attempt++) {
				HttpResponse<String> response = client.send(prepared,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				int status = response.statusCode();
				if (status >= 200 && status < 300) return mapper.readTree(response.body());
				if (attempt + 1 < maxAttempts && (status == 408 || status == 500 || status == 502
						|| status == 503 || status == 504)) {
					Thread.sleep((500L << attempt) + ThreadLocalRandom.current().nextLong(250L));
					continue;
				}
				String errorBody = response.body().toLowerCase(Locale.ROOT);
				throw new AiHttpException(status,
						status == 400 && errorBody.contains("api_key_invalid"),
						status == 429 && (errorBody.contains("perday") || errorBody.contains("per day")
								|| errorBody.contains("daily")));
			}
			throw new IllegalStateException("Nhà cung cấp AI không phản hồi sau khi thử lại");
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Yêu cầu AI đã bị gián đoạn", ex);
		} catch (Exception ex) {
			if (ex instanceof IllegalStateException) throw (IllegalStateException) ex;
			throw new IllegalStateException("Không thể kết nối nhà cung cấp AI", ex);
		}
	}

	private List<Map<String, String>> conversation(List<ChatTurnDto> history, String latest, boolean gemini) {
		List<Map<String, String>> messages = new ArrayList<>();
		int limit = Math.max(0, settings.getMaxHistoryMessages());
		int start = Math.max(0, history.size() - limit);
		for (int i = start; i < history.size(); i++) {
			ChatTurnDto turn = history.get(i);
			String role = "assistant".equals(turn.getRole()) ? (gemini ? "model" : "assistant") : "user";
			messages.add(message(role, turn.getContent()));
		}
		messages.add(message("user", latest));
		return messages;
	}

	private Map<String, String> message(String role, String content) {
		Map<String, String> item = new LinkedHashMap<>();
		item.put("role", role);
		item.put("content", content);
		return item;
	}

	private String defaultBase(String fallback) {
		return blank(settings.getBaseUrl()) ? fallback : settings.getBaseUrl();
	}

	private String join(String base, String path) {
		String left = value(base, "").replaceAll("/+$", "");
		String right = value(path, "").replaceAll("^/+", "");
		return left.endsWith("/" + right) ? left : left + "/" + right;
	}

	private int safeMaxTokens() { return Math.max(100, Math.min(8192, settings.getMaxOutputTokens())); }
	private double safeTemperature() { return Math.max(0, Math.min(1, settings.getTemperature())); }
	private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
	private String value(String value, String fallback) { return value == null ? fallback : value; }

	private String requireText(List<String> parts) {
		String result = String.join("\n", parts).trim();
		if (result.isEmpty()) throw new IllegalStateException("Nhà cung cấp AI không trả về nội dung");
		return result;
	}

	private static final class AiHttpException extends IllegalStateException {
		private final int status;
		private final boolean invalidKey;
		private final boolean dailyQuota;
		private AiHttpException(int status, boolean invalidKey, boolean dailyQuota) {
			super("Nhà cung cấp AI trả về mã " + status);
			this.status = status;
			this.invalidKey = invalidKey;
			this.dailyQuota = dailyQuota;
		}
	}
}
