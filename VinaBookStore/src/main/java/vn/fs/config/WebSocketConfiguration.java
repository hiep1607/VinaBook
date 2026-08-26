/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import vn.fs.handler.NotificationWebSocketHandle;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

	private final static String NOTIFICATION_ENDPOINT = "/notification";
	private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

	@Value("${app.admin-origin:http://localhost:4300}")
	private String adminOrigin;

	public WebSocketConfiguration(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
		this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
		webSocketHandlerRegistry.addHandler(getNotificationWebSocketHandler(), NOTIFICATION_ENDPOINT)
				.addInterceptors(jwtHandshakeInterceptor)
				.setAllowedOrigins(adminOrigin);
	}

	@Bean
	public WebSocketHandler getNotificationWebSocketHandler() {
		return new NotificationWebSocketHandle();
	}

}
