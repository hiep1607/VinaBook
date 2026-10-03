package vn.fs.dto.chat;

public class ChatProductDto {

	private final Long id;
	private final String name;
	private final String author;
	private final String category;
	private final long price;
	private final double oldPrice;
	private final int discount;
	private final int quantity;
	private final String image;

	@com.fasterxml.jackson.annotation.JsonCreator
	public ChatProductDto(@com.fasterxml.jackson.annotation.JsonProperty("id") Long id,
			@com.fasterxml.jackson.annotation.JsonProperty("name") String name,
			@com.fasterxml.jackson.annotation.JsonProperty("author") String author,
			@com.fasterxml.jackson.annotation.JsonProperty("category") String category,
			@com.fasterxml.jackson.annotation.JsonProperty("price") long price,
			@com.fasterxml.jackson.annotation.JsonProperty("oldPrice") double oldPrice,
			@com.fasterxml.jackson.annotation.JsonProperty("discount") int discount,
			@com.fasterxml.jackson.annotation.JsonProperty("quantity") int quantity,
			@com.fasterxml.jackson.annotation.JsonProperty("image") String image) {
		this.id = id;
		this.name = name;
		this.author = author;
		this.category = category;
		this.price = price;
		this.oldPrice = oldPrice;
		this.discount = discount;
		this.quantity = quantity;
		this.image = image;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getAuthor() { return author; }
	public String getCategory() { return category; }
	public long getPrice() { return price; }
	public double getOldPrice() { return oldPrice; }
	public int getDiscount() { return discount; }
	public int getQuantity() { return quantity; }
	public String getImage() { return image; }
}
