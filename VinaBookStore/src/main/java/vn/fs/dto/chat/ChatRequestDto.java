package vn.fs.dto.chat;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ChatRequestDto {

	@NotBlank(message = "Vui lòng nhập câu hỏi")
	@Size(max = 1000, message = "Câu hỏi tối đa 1000 ký tự")
	private String message;

	@Valid
	@Size(max = 10, message = "Lịch sử hội thoại quá dài")
	private List<ChatTurnDto> history = new ArrayList<>();

	@Size(max = 64, message = "Mã phiên quá dài")
	private String sessionId;

	@Size(max = 8, message = "Ngữ cảnh sản phẩm quá dài")
	private List<Long> contextProductIds = new ArrayList<>();

	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }
	public List<ChatTurnDto> getHistory() { return history; }
	public void setHistory(List<ChatTurnDto> history) { this.history = history == null ? new ArrayList<>() : history; }
	public String getSessionId() { return sessionId; }
	public void setSessionId(String sessionId) { this.sessionId = sessionId; }
	public List<Long> getContextProductIds() { return contextProductIds; }
	public void setContextProductIds(List<Long> contextProductIds) {
		this.contextProductIds = contextProductIds == null ? new ArrayList<>() : contextProductIds;
	}
}
