package vn.fs.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiSettings {

	private String provider = "local";
	private String model = "";
	private String fallbackModel = "";
	private String embeddingModel = "gemini-embedding-001";
	private String apiKey = "";
	private String geminiApiKey1 = "";
	private String geminiApiKey2 = "";
	private String geminiApiKey3 = "";
	private String geminiApiKey4 = "";
	private String baseUrl = "";
	private int timeoutSeconds = 25;
	private int geminiAttemptTimeoutMillis = 10000;
	private int maxOutputTokens = 4096;
	private double temperature = 0.35;
	private int rateLimitPerMinute = 20;
	private int maxHistoryMessages = 8;

	public String getProvider() { return provider; }
	public void setProvider(String provider) { this.provider = provider; }
	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }
	public String getFallbackModel() { return fallbackModel; }
	public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }
	public String getEmbeddingModel() { return embeddingModel; }
	public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
	public String getApiKey() { return apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getGeminiApiKey1() { return geminiApiKey1; }
	public void setGeminiApiKey1(String geminiApiKey1) { this.geminiApiKey1 = geminiApiKey1; }
	public String getGeminiApiKey2() { return geminiApiKey2; }
	public void setGeminiApiKey2(String geminiApiKey2) { this.geminiApiKey2 = geminiApiKey2; }
	public String getGeminiApiKey3() { return geminiApiKey3; }
	public void setGeminiApiKey3(String geminiApiKey3) { this.geminiApiKey3 = geminiApiKey3; }
	public String getGeminiApiKey4() { return geminiApiKey4; }
	public void setGeminiApiKey4(String geminiApiKey4) { this.geminiApiKey4 = geminiApiKey4; }
	public List<String> getGeminiApiKeys() {
		LinkedHashSet<String> keys = new LinkedHashSet<>();
		addGeminiKey(keys, geminiApiKey1);
		addGeminiKey(keys, geminiApiKey2);
		addGeminiKey(keys, geminiApiKey3);
		addGeminiKey(keys, geminiApiKey4);
		if (keys.isEmpty()) addGeminiKey(keys, apiKey);
		return new ArrayList<>(keys);
	}
	private void addGeminiKey(LinkedHashSet<String> keys, String candidate) {
		if (candidate == null) return;
		String key = candidate.trim();
		if (!key.isEmpty() && !key.equals("PASTE_GEMINI_API_KEY_HERE")) keys.add(key);
	}
	public String getBaseUrl() { return baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public int getTimeoutSeconds() { return timeoutSeconds; }
	public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
	public int getGeminiAttemptTimeoutMillis() { return geminiAttemptTimeoutMillis; }
	public void setGeminiAttemptTimeoutMillis(int value) { this.geminiAttemptTimeoutMillis = value; }
	public int getMaxOutputTokens() { return maxOutputTokens; }
	public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
	public double getTemperature() { return temperature; }
	public void setTemperature(double temperature) { this.temperature = temperature; }
	public int getRateLimitPerMinute() { return rateLimitPerMinute; }
	public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
	public int getMaxHistoryMessages() { return maxHistoryMessages; }
	public void setMaxHistoryMessages(int maxHistoryMessages) { this.maxHistoryMessages = maxHistoryMessages; }
}
