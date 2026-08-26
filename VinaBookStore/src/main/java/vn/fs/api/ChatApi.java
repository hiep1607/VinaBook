package vn.fs.api;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatStatusDto;
import vn.fs.service.ChatRateLimiter;
import vn.fs.service.ChatService;

@CrossOrigin("*")
@RestController
@RequestMapping("api/chat")
public class ChatApi {

	private final ChatService chat;
	private final ChatRateLimiter rateLimiter;

	public ChatApi(ChatService chat, ChatRateLimiter rateLimiter) {
		this.chat = chat;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("status")
	public ResponseEntity<ChatStatusDto> status() {
		return ResponseEntity.ok(chat.status());
	}

	@PostMapping
	public ResponseEntity<?> reply(@Valid @RequestBody ChatRequestDto request, HttpServletRequest servletRequest) {
		String clientId = clientIp(servletRequest);
		if (!rateLimiter.allow(clientId)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.body(Map.of("message", "Bạn gửi câu hỏi quá nhanh. Vui lòng thử lại sau một phút."));
		}
		ChatResponseDto response = chat.reply(request);
		return ResponseEntity.ok(response);
	}

	private String clientIp(HttpServletRequest request) {
		String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
		String forwarded = request.getHeader("X-Forwarded-For");
		if (isTrustedProxy(remote) && forwarded != null && !forwarded.trim().isEmpty()) {
			return forwarded.split(",")[0].trim();
		}
		return remote;
	}

	private boolean isTrustedProxy(String address) {
		return "127.0.0.1".equals(address) || "::1".equals(address)
				|| address.startsWith("10.") || address.startsWith("192.168.")
				|| address.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
	}
}
