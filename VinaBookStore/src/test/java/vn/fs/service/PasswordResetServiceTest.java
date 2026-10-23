package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.fs.entity.*;
import vn.fs.repository.*;

class PasswordResetServiceTest {
    UserRepository users=mock(UserRepository.class);
    PasswordResetTokenRepository tokens=mock(PasswordResetTokenRepository.class);
    PasswordEncoder encoder=mock(PasswordEncoder.class);
    SendMailService mail=mock(SendMailService.class);
    EntityManager em=mock(EntityManager.class);
    PasswordResetService service=new PasswordResetService(users,tokens,encoder,mail,em,"https://books.example.invalid");
    User user;
    AtomicReference<PasswordResetToken> stored=new AtomicReference<>();
    @BeforeEach void setup() {
        user=new User();user.setUserId(7L);user.setStatus(true);user.setEmail("reader@example.invalid");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(users.findById(7L)).thenReturn(Optional.of(user));
        when(tokens.saveAndFlush(any())).thenAnswer(call->{stored.set(call.getArgument(0));return stored.get();});
        when(tokens.lockByHash(anyString())).thenAnswer(call->Optional.ofNullable(stored.get()).filter(t->t.getTokenHash().equals(call.getArgument(0))));
        when(tokens.findByTokenHash(anyString())).thenAnswer(call->Optional.ofNullable(stored.get()).filter(t->t.getTokenHash().equals(call.getArgument(0))));
        doAnswer(call->{stored.set(null);return null;}).when(tokens).delete(any());
        when(encoder.encode(anyString())).thenReturn("encoded-new-password");
    }
    private String issue() {
        service.request(user.getEmail());var body=ArgumentCaptor.forClass(String.class);
        verify(mail).queue(eq(user.getEmail()),anyString(),body.capture());
        var match=java.util.regex.Pattern.compile("forgot-password/([a-f0-9]{64})").matcher(body.getValue());
        assertThat(match.find()).isTrue();return match.group(1);
    }
    @Test void linkIsRandomHashedExpiringAndConsumedOnlyOnce() {
        String raw=issue();assertThat(stored.get().getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.get().getExpiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(901));
        assertThat(service.consume(raw,"new-password")).isTrue();assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(service.consume(raw,"second-password")).isFalse();verify(encoder,times(1)).encode(anyString());
    }
    @Test void expiredTokenCannotChangePassword() {
        String raw=issue();stored.get().setExpiresAt(Instant.now().minusSeconds(1));
        assertThat(service.consume(raw,"new-password")).isFalse();verifyNoInteractions(encoder);
    }
    @Test void resetCannotReactivateDisabledAccount() {
        String raw=issue();user.setStatus(false);
        assertThat(service.consume(raw,"new-password")).isFalse();assertThat(user.getStatus()).isFalse();verifyNoInteractions(encoder);
    }
    @Test void duplicateRequestIsThrottledAndUnknownAddressGetsNoEmail() {
        issue();service.request(user.getEmail());service.request("unknown@example.invalid");
        verify(mail,times(1)).queue(anyString(),anyString(),anyString());
    }
}
