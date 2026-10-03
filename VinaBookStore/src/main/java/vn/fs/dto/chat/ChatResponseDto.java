package vn.fs.dto.chat;

import java.util.List;

public class ChatResponseDto {
    private java.util.Map<String, String> execution = java.util.Map.of();
    public java.util.Map<String, String> getExecution() { return execution; }
    public void setExecution(java.util.Map<String, String> value) { execution = value; }

	private final String answer;
	private final String provider;
	private final String model;
	private final boolean fallback;
	private final List<ChatProductDto> products;
	private final List<String> chips;
	private final String messageId;
	private final ChatIntent intent;
	private final ChatSearchCriteriaDto criteria;
	private final List<ChatActionDto> actions;

	public ChatResponseDto(String answer, String provider, String model, boolean fallback,
			List<ChatProductDto> products, List<String> chips) {
		this.answer = answer;
		this.provider = provider;
		this.model = model;
		this.fallback = fallback;
		this.products = products;
		this.chips = chips;
		this.messageId = "";
		this.intent = ChatIntent.UNKNOWN;
		this.criteria = ChatSearchCriteriaDto.empty();
		this.actions = java.util.Collections.emptyList();
	}

	@com.fasterxml.jackson.annotation.JsonCreator
	public ChatResponseDto(@com.fasterxml.jackson.annotation.JsonProperty("answer") String answer,
			@com.fasterxml.jackson.annotation.JsonProperty("provider") String provider,
			@com.fasterxml.jackson.annotation.JsonProperty("model") String model,
			@com.fasterxml.jackson.annotation.JsonProperty("fallback") boolean fallback,
			@com.fasterxml.jackson.annotation.JsonProperty("products") List<ChatProductDto> products,
			@com.fasterxml.jackson.annotation.JsonProperty("chips") List<String> chips,
			@com.fasterxml.jackson.annotation.JsonProperty("messageId") String messageId,
			@com.fasterxml.jackson.annotation.JsonProperty("intent") ChatIntent intent,
			@com.fasterxml.jackson.annotation.JsonProperty("criteria") ChatSearchCriteriaDto criteria,
			@com.fasterxml.jackson.annotation.JsonProperty("actions") List<ChatActionDto> actions) {
		this.answer = answer;
		this.provider = provider;
		this.model = model;
		this.fallback = fallback;
		this.products = products;
		this.chips = chips;
		this.messageId = messageId;
		this.intent = intent;
		this.criteria = criteria;
		this.actions = actions;
	}

	public String getAnswer() { return answer; }
	public String getProvider() { return provider; }
	public String getModel() { return model; }
	public boolean isFallback() { return fallback; }
	public List<ChatProductDto> getProducts() { return products; }
	public List<String> getChips() { return chips; }
	public String getMessageId() { return messageId; }
	public ChatIntent getIntent() { return intent; }
	public ChatSearchCriteriaDto getCriteria() { return criteria; }
	public List<ChatActionDto> getActions() { return actions; }
}
