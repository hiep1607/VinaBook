package vn.fs.config;

import java.util.Map;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import vn.fs.service.NotificationSocketTickets;
import vn.fs.service.implement.UserDetailsServiceImpl;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final NotificationSocketTickets tickets;
    private final UserDetailsServiceImpl users;
    public JwtHandshakeInterceptor(NotificationSocketTickets tickets,UserDetailsServiceImpl users){this.tickets=tickets;this.users=users;}
    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,
            WebSocketHandler handler,Map<String,Object> attributes) {
        try {
            String ticket=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
            String email=tickets.consume(ticket);if(email==null){response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);return false;}
            var user=users.loadUserByUsername(email);
            if(!user.isEnabled() || user.getAuthorities().stream().noneMatch(r->"ROLE_ADMIN".equals(r.getAuthority()))){response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);return false;}
            attributes.put("email",email);return true;
        } catch(RuntimeException invalid){response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);return false;}
    }
    @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception error) {}
}
