package vn.fs.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import vn.fs.service.NotificationSocketTickets;
import vn.fs.service.implement.UserDetailsServiceImpl;

class NotificationHandshakeTest {
    @Test void anonymousAndLegacyJwtUrlsCannotUpgradeToAnAdminSocket() {
        var tickets=mock(NotificationSocketTickets.class);var users=mock(UserDetailsServiceImpl.class);
        var guard=new JwtHandshakeInterceptor(tickets,users);
        for(String url:new String[]{"http://localhost:8080/notification","http://localhost:8080/notification?token=old-jwt"}) {
            var request=mock(ServerHttpRequest.class);when(request.getURI()).thenReturn(URI.create(url));
            var response=mock(ServerHttpResponse.class);
            assertThat(guard.beforeHandshake(request,response,mock(WebSocketHandler.class),new HashMap<>())).isFalse();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
        verifyNoInteractions(users);
    }
}
