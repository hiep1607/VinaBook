package vn.fs.config;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import vn.fs.service.implement.UserDetailsImpl;
import vn.fs.service.implement.UserDetailsServiceImpl;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtUtils jwtUtils;
	private final UserDetailsServiceImpl userDetailsService;

	public JwtHandshakeInterceptor(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
		this.jwtUtils = jwtUtils;
		this.userDetailsService = userDetailsService;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {
		try {
			String token = UriComponentsBuilder.fromUri(request.getURI()).build()
					.getQueryParams().getFirst("token");
			if (token == null || !jwtUtils.validateJwtToken(token)) {
				return false;
			}
			String email = jwtUtils.getEmailFromJwtToken(token);
			UserDetailsImpl user = (UserDetailsImpl) userDetailsService.loadUserByUsername(email);
			boolean admin = user.getAuthorities().stream()
					.anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()));
			if (admin) {
				attributes.put("email", email);
			}
			return admin;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
		// Nothing to clean up.
	}
}
