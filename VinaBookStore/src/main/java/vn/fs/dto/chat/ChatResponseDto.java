package vn.fs.dto.chat;

import java.util.List;

public class ChatResponseDto {

	private final String answer;
	private final String provider;
	private final String model;
	private final boolean fallback;
	private final List<ChatProductDto> products;
	private final List<String> chips;

	public ChatResponseDto(String answer, String provider, String model, boolean fallback,
			List<ChatProductDto> products, List<String> chips) {
		this.answer = answer;
		this.provider = provider;
		this.model = model;
		this.fallback = fallback;
		this.products = products;
		this.chips = chips;
	}

	public String getAnswer() { return answer; }
	public String getProvider() { return provider; }
	public String getModel() { return model; }
	public boolean isFallback() { return fallback; }
	public List<ChatProductDto> getProducts() { return products; }
	public List<String> getChips() { return chips; }
}
