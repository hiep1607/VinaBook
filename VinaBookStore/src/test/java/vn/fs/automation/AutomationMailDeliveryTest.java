package vn.fs.automation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.entity.MailOutbox;
import vn.fs.repository.MailOutboxRepository;
import vn.fs.service.MailOutboxStore;

class AutomationMailDeliveryTest {
    @Test void actualSmtpOutcomeUpdatesLinkedAutomationStatusAndDiscardClearsPayload(){
        var rows=mock(MailOutboxRepository.class);var notices=mock(AutomationNoticeRepository.class);var row=new MailOutbox();row.setId("job");row.setState("SENDING");row.setClaimToken("claim");row.setAttempts(1);row.setPayload("encrypted");var notice=new AutomationNotice();notice.setEmailState("QUEUED");
        when(rows.locked("job")).thenReturn(Optional.of(row));when(notices.findByMailJobId("job")).thenReturn(Optional.of(notice));
        var store=new MailOutboxStore(rows,new ObjectMapper(),"test-only-key-with-at-least-32-characters");ReflectionTestUtils.setField(store,"automationNotices",notices);var delivery=new MailOutboxStore.Delivery("job","claim",null);
        store.finish(delivery,false);assertThat(notice.getEmailState()).isEqualTo("QUEUED");
        row.setState("SENDING");row.setClaimToken("claim");row.setAttempts(3);store.finish(delivery,false);assertThat(notice.getEmailState()).isEqualTo("FAILED");assertThat(row.getPayload()).isEmpty();
        row.setState("SENDING");row.setClaimToken("claim");store.finish(delivery,true);assertThat(notice.getEmailState()).isEqualTo("SENT");
        row.setState("SENDING");row.setClaimToken("claim");row.setPayload("encrypted");store.discard(delivery);assertThat(row.getState()).isEqualTo("CANCELLED");assertThat(row.getPayload()).isEmpty();
    }
}
