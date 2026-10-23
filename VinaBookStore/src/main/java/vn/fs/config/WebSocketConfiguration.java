package vn.fs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import vn.fs.handler.NotificationWebSocketHandle;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final NotificationWebSocketHandle handler;
    private final JwtHandshakeInterceptor authentication;
    private final String[] origins;
    public WebSocketConfiguration(NotificationWebSocketHandle handler,JwtHandshakeInterceptor authentication,
        @Value("${app.allowed-origins:http://localhost:4200,http://localhost:4300,http://127.0.0.1:4200,http://127.0.0.1:4300}") String[] origins) {
        this.handler=handler;this.authentication=authentication;this.origins=origins;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler,"/notification").addInterceptors(authentication).setAllowedOrigins(origins);
    }
}
