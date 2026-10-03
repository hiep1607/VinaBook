package vn.fs.dto.chat;

public class ChatActionDto {
    private Long orderId;
    public Long getOrderId(){return orderId;}
    public void setOrderId(Long value){orderId=value;}
    private int quantity = 1;
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
	private final String type;
	private final String label;
	private final Long productId;
	private final String route;
	private final boolean requiresConfirmation;

	@com.fasterxml.jackson.annotation.JsonCreator
	public ChatActionDto(@com.fasterxml.jackson.annotation.JsonProperty("type") String type,
			@com.fasterxml.jackson.annotation.JsonProperty("label") String label,
			@com.fasterxml.jackson.annotation.JsonProperty("productId") Long productId,
			@com.fasterxml.jackson.annotation.JsonProperty("route") String route,
			@com.fasterxml.jackson.annotation.JsonProperty("requiresConfirmation") boolean requiresConfirmation) {
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
