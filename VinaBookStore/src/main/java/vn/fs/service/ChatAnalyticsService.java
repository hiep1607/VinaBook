package vn.fs.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import vn.fs.dto.chat.ChatFeedbackRequestDto;
import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatMetricsDto;
import vn.fs.entity.ChatEvent;
import vn.fs.repository.ChatEventRepository;

@Service
public class ChatAnalyticsService {
	private final ChatEventRepository events;

	public ChatAnalyticsService(ChatEventRepository events) {
		this.events = events;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public String record(String sessionId, ChatIntent intent, String provider, boolean fallback,
			int productCount, long latencyMs) {
		ChatEvent event = new ChatEvent();
		event.setMessageId(UUID.randomUUID().toString());
		event.setSessionId(safe(sessionId, 64));
		event.setIntent(intent.name());
		event.setProvider(safe(provider, 40));
		event.setFallback(fallback);
		event.setProductCount(productCount);
		event.setLatencyMs(Math.max(0, latencyMs));
		event.setCreatedAt(LocalDateTime.now());
		events.save(event);
		return event.getMessageId();
	}

	@Transactional
	public boolean feedback(ChatFeedbackRequestDto request) {
		return events.findById(request.getMessageId()).map(event -> {
			event.setFeedback(request.getValue());
			event.setFeedbackComment(safe(request.getComment(), 500));
			events.save(event);
			return true;
		}).orElse(false);
	}

    @Transactional(readOnly=true)
    public ChatMetricsDto metrics(){return metrics(30);}
    @Transactional(readOnly=true)
    public ChatMetricsDto metrics(int days) {
        LocalDateTime since=LocalDateTime.now().minusDays(Math.max(1,Math.min(365,days)));
        ChatEventRepository.Summary summary=events.summarize(since);
        long total=summary==null?0:summary.getTotal(),fallback=summary==null?0:summary.getFallback();
        Map<String,Long> intents=new LinkedHashMap<>();
        for(Object[] row:events.countIntents(since))intents.put(String.valueOf(row[0]),((Number)row[1]).longValue());
        return new ChatMetricsDto(total,fallback,total==0?0:fallback*100D/total,
            summary==null?0:summary.getPositive(),summary==null?0:summary.getNegative(),summary==null?0:summary.getLatency(),intents);
    }

	private String safe(String value, int max) {
		if (value == null) return "";
		String cleaned = value.trim();
		return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
	}
}
