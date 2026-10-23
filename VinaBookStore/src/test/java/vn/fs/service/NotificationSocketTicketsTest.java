package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import vn.fs.config.CurrentUserAccess;

class NotificationSocketTicketsTest {
    @Test void onlyAdminCanObtainSingleUseTicket() {
        var access=mock(CurrentUserAccess.class);var tickets=new NotificationSocketTickets(access);
        assertThatThrownBy(tickets::issue).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(access.isAdmin()).thenReturn(true);when(access.currentEmail()).thenReturn("admin@example.invalid");
        String ticket=tickets.issue().get("ticket");
        assertThat(tickets.consume(ticket)).isEqualTo("admin@example.invalid");assertThat(tickets.consume(ticket)).isNull();
        assertThat(tickets.consume("arbitrary-jwt")).isNull();
    }
}
