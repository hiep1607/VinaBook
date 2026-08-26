package vn.fs.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ChatTurnDto {

	@Pattern(regexp = "user|assistant")
	private String role;

	@NotBlank
	@Size(max = 1200)
	private String content;

	public ChatTurnDto() { }

	public ChatTurnDto(String role, String content) {
		this.role = role;
		this.content = content;
	}

	public String getRole() { return role; }
	public void setRole(String role) { this.role = role; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
}
