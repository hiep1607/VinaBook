package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.dto.MailInfo;
import vn.fs.entity.MailOutbox;
import vn.fs.repository.MailOutboxRepository;

class MailOutboxStoreTest {
    @Test void restartRestoresEncryptedMailAndRetriesThenClearsSensitivePayload() {
        var rows=mock(MailOutboxRepository.class);var saved=new AtomicReference<MailOutbox>();
        when(rows.save(any())).thenAnswer(call->{saved.set(call.getArgument(0));return saved.get();});
        when(rows.ready(anyLong(),anyLong(),any())).thenAnswer(call->List.of(saved.get()));
        when(rows.locked(anyString())).thenAnswer(call->Optional.of(saved.get()));
        String secret="test-only-key-with-at-least-32-characters";
        new MailOutboxStore(rows,new ObjectMapper(),"",secret).enqueue(new MailInfo("reader@example.invalid","Reset","private-reset-link"));
        assertThat(saved.get().getPayload()).doesNotContain("reader","private-reset-link");
        var restarted=new MailOutboxStore(rows,new ObjectMapper(),secret);var first=restarted.claim().orElseThrow();
        assertThat(first.mail().getBody()).isEqualTo("private-reset-link");
        restarted.finish(first,false);assertThat(saved.get().getState()).isEqualTo("PENDING");
        assertThat(saved.get().getAttempts()).isEqualTo(1);assertThat(saved.get().getPayload()).isNotEmpty();
        var second=restarted.claim().orElseThrow();restarted.finish(first,true);
        assertThat(saved.get().getState()).isEqualTo("SENDING"); // stale worker cannot finish a newer claim
        restarted.finish(second,true);assertThat(saved.get().getState()).isEqualTo("SENT");assertThat(saved.get().getPayload()).isEmpty();
    }
    @Test void abandonedThirdAttemptIsRetiredWithoutSendingForever() {
        var rows=mock(MailOutboxRepository.class);var row=new MailOutbox();row.setAttempts(3);row.setState("SENDING");row.setPayload("ciphertext");
        when(rows.ready(anyLong(),anyLong(),any())).thenReturn(List.of(row));
        assertThat(new MailOutboxStore(rows,new ObjectMapper(),"test-only-key-with-at-least-32-characters").claim()).isEmpty();
        assertThat(row.getState()).isEqualTo("FAILED");assertThat(row.getPayload()).isEmpty();
    }
}
