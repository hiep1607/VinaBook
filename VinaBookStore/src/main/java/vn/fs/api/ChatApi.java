package vn.fs.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatStatusDto;
import vn.fs.dto.chat.ChatFeedbackRequestDto;
import vn.fs.dto.chat.ChatMetricsDto;
import vn.fs.service.ChatAnalyticsService;
import vn.fs.service.ChatRateLimiter;
import vn.fs.service.ChatService;

@CrossOrigin("*")
@RestController
@RequestMapping("api/chat")
public class ChatApi {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatApi.class);

	private final ChatService chat;
	private final ChatRateLimiter rateLimiter;
	private final ChatAnalyticsService analytics;
    private final vn.fs.service.ChatTaskExecutor executor;

	public ChatApi(ChatService chat, ChatRateLimiter rateLimiter, ChatAnalyticsService analytics, vn.fs.service.ChatTaskExecutor executor) {
        this.executor=executor;
		this.chat = chat;
		this.rateLimiter = rateLimiter;
		this.analytics = analytics;
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
					.body(Map.of("message", "Mình đang nhận nhiều câu hỏi liên tiếp. Bạn chờ khoảng một phút rồi gửi lại nhé, để mình xử lý đầy đủ từng yêu cầu."));
		}
		ChatResponseDto response = chat.reply(request);
		return ResponseEntity.ok(response);
	}

	@GetMapping("conversation")
	public ResponseEntity<?> conversation(@RequestParam String sessionId) {
		if (!validSession(sessionId)) return ResponseEntity.badRequest().body(Map.of("message", "Mã cuộc trò chuyện không hợp lệ."));
		return ResponseEntity.ok(chat.history(sessionId));
	}

    @GetMapping("conversation/page")
    public ResponseEntity<?> historyPage(@RequestParam String sessionId,@RequestParam(required=false) Long before,@RequestParam(defaultValue="25") int limit) {
        if(!validSession(sessionId) || before!=null && before<1)return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(chat.historyPage(sessionId,before,limit));
    }
	@DeleteMapping("conversation")
	public ResponseEntity<?> clearConversation(@RequestParam String sessionId) {
		if (!validSession(sessionId)) return ResponseEntity.badRequest().body(Map.of("message", "Mã cuộc trò chuyện không hợp lệ."));
		chat.clearConversation(sessionId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("account-memory")
	public ResponseEntity<?> accountMemory() {
		return ResponseEntity.ok(chat.accountContext());
	}

	@PostMapping("activity")
	public ResponseEntity<?> activity(@RequestBody Map<String, Long> body) {
		Long productId = body.get("productId");
		if (productId == null || productId <= 0) return ResponseEntity.badRequest().build();
		chat.observeProductView(productId);
		return ResponseEntity.noContent().build();
	}

	private boolean validSession(String sessionId) {
		return sessionId != null && sessionId.matches("[A-Za-z0-9_-]{16,64}");
	}

	@PostMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@Valid @RequestBody ChatRequestDto request, HttpServletRequest servletRequest) {
		SseEmitter emitter = new SseEmitter(60_000L);
		String clientId = clientIp(servletRequest);
		if (!rateLimiter.allow(clientId)) {
			try {
				emitter.send(SseEmitter.event().name("error").data(Map.of("message",
						"Mình đang nhận nhiều câu hỏi liên tiếp. Bạn chờ khoảng một phút rồi gửi lại nhé, để mình xử lý đầy đủ từng yêu cầu.")));
				emitter.complete();
			} catch (Exception ex) {
				emitter.completeWithError(ex);
			}
			return emitter;
		}
		SecurityContext callerContext = SecurityContextHolder.getContext();
		// Devtools loads entities in the restart loader. The dedicated worker must
		// resolve JPA parameter types with the caller's loader.
		ClassLoader callerLoader = Thread.currentThread().getContextClassLoader();
		java.util.concurrent.FutureTask<Void> task=new java.util.concurrent.FutureTask<>(() -> {
			ClassLoader workerLoader = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(callerLoader);
			SecurityContextHolder.setContext(callerContext);
            vn.fs.service.ChatTurnProgress.listen(stage -> {
                try {emitter.send(SseEmitter.event().name("progress").data(Map.of("stage",stage)));}
                catch(Exception disconnected){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();}
            });
			try {
				emitter.send(SseEmitter.event().name("ready").data(Map.of("status", "thinking")));
				ChatResponseDto response = chat.reply(request);
				emitter.send(SseEmitter.event().name("done").data(response));
				emitter.complete();
			} catch (Exception ex) {
				// Entity exceptions may contain account fields; expose only a generic error.
				log.warn("Chat stream failed ({})", ex.getClass().getSimpleName());
				try {
					emitter.send(SseEmitter.event().name("error").data(Map.of("message",
							"Mình chưa hoàn tất câu trả lời do lỗi kết nối hoặc xử lý. Bạn thử gửi lại câu hỏi nhé để mình kiểm tra tiếp.")));
					emitter.complete();
				} catch (Exception disconnected) {
					emitter.complete();
				}
			} finally {
				vn.fs.service.ChatTurnProgress.clear();
				SecurityContextHolder.clearContext();
				Thread.currentThread().setContextClassLoader(workerLoader);
			}
            return null;
        });
        emitter.onTimeout(() -> { task.cancel(true);emitter.complete(); });
        emitter.onError(error -> task.cancel(true));
        emitter.onCompletion(() -> task.cancel(true));
        try {executor.execute(task);}catch(java.util.concurrent.RejectedExecutionException busy) {
            try {emitter.send(SseEmitter.event().name("error").data(Map.of("message","Mình đang xử lý nhiều yêu cầu. Bạn thử lại sau một chút nhé.")));emitter.complete();}
            catch(Exception disconnected){emitter.complete();}
        }
		return emitter;
	}

	@PostMapping("feedback")
	public ResponseEntity<Void> feedback(@Valid @RequestBody ChatFeedbackRequestDto request) {
		return analytics.feedback(request) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@GetMapping("admin/metrics")
	public ResponseEntity<ChatMetricsDto> metrics(@RequestParam(defaultValue="30") int days) {
		return ResponseEntity.ok(analytics.metrics(days));
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
