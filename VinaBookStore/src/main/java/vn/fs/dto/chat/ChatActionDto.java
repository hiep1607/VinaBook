package vn.fs.dto.chat;

public class ChatActionDto {
	private final String type;
	private final String label;
	private final Long productId;
	private final String route;
	private final boolean requiresConfirmation;

	public ChatActionDto(String type, String label, Long productId, String route,
			boolean requiresConfirmation) {
		this.type = type;
		this.label = label;
		this.productId = productId;
		this.route = route;
		this.requiresConfirmation = requiresConfirmation;
	}

	public String getType() { return type; }
	public String getLabel() { return label; }
	public Long getProductId() { return productId; }
	public String getRoute() { return route; }
	public boolean isRequiresConfirmation() { return requiresConfirmation; }
}
