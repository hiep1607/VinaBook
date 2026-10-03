package vn.fs.dto.chat;

import java.util.Collections;
import java.util.List;

public class ChatSearchCriteriaDto {
	private final Long minPrice;
	private final Long maxPrice;
	private final String category;
	private final String author;
	private final String sort;
	private final List<String> topics;
	private Double minRating;
	public Double getMinRating() { return minRating; }
	public void setMinRating(Double value) { minRating = value; }

	@com.fasterxml.jackson.annotation.JsonCreator
	public ChatSearchCriteriaDto(@com.fasterxml.jackson.annotation.JsonProperty("minPrice") Long minPrice,
			@com.fasterxml.jackson.annotation.JsonProperty("maxPrice") Long maxPrice,
			@com.fasterxml.jackson.annotation.JsonProperty("category") String category,
			@com.fasterxml.jackson.annotation.JsonProperty("author") String author,
			@com.fasterxml.jackson.annotation.JsonProperty("sort") String sort,
			@com.fasterxml.jackson.annotation.JsonProperty("topics") List<String> topics) {
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
		this.category = category == null ? "" : category;
		this.author = author == null ? "" : author;
		this.sort = sort == null ? "" : sort;
		this.topics = topics == null ? Collections.emptyList() : topics;
	}

	public static ChatSearchCriteriaDto empty() {
		return new ChatSearchCriteriaDto(null, null, "", "", "", Collections.emptyList());
	}

	public Long getMinPrice() { return minPrice; }
	public Long getMaxPrice() { return maxPrice; }
	public String getCategory() { return category; }
	public String getAuthor() { return author; }
	public String getSort() { return sort; }
	public List<String> getTopics() { return topics; }
}
