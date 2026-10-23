package vn.fs.service;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fs.repository.*;

/** Retention is opt-in; enabling it never purges authenticated conversation history. */
@Service
public class ChatRetentionService {
    private final ChatConversationRepository conversations;
    private final ChatExchangeRepository exchanges;
    private final ChatEventRepository events;
    @Value("${chat.retention.enabled:false}") private boolean enabled;
    @Value("${chat.retention.guest-days:30}") private int guestDays;
    @Value("${chat.retention.telemetry-days:90}") private int telemetryDays;
    public ChatRetentionService(ChatConversationRepository conversations,ChatExchangeRepository exchanges,ChatEventRepository events){this.conversations=conversations;this.exchanges=exchanges;this.events=events;}
    @Scheduled(cron="${chat.retention.cron:0 30 3 * * *}",zone="Asia/Bangkok")
    @Transactional
    public void purge() {
        if(!enabled)return;
        LocalDateTime cutoff=LocalDateTime.now().minusDays(Math.max(1,guestDays));
        exchanges.deleteExpiredGuestExchanges(cutoff);conversations.deleteExpiredGuests(cutoff);
        events.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(Math.max(1,telemetryDays)));
    }
}
