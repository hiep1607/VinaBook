/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.config;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import vn.fs.service.implement.UserDetailsImpl;

@Component
public class JwtUtils {
	private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

	@Value("${bezkoder.app.jwtSecret}")
	private String jwtSecrect;

	@Value("${bezkoder.app.jwtExpirationMs}")
	private int jwtExpirationMs;

	@Value("${bezkoder.app.passwordResetExpirationMs:900000}")
	private int passwordResetExpirationMs;

	public String generateJwtToken(Authentication authentication) {

		UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

		Date dateJwtDate = new Date();
		dateJwtDate.getTime();

		return Jwts.builder().setSubject((userPrincipal.getEmail())).setIssuedAt(new Date())
				.setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
				.signWith(SignatureAlgorithm.HS512, jwtSecrect).compact();
	}

	public String doGenerateToken(String email) {
		return generatePasswordResetToken(email);
	}

	public String generatePasswordResetToken(String email) {
		long now = System.currentTimeMillis();
		return Jwts.builder()
				.setSubject(email)
				.setIssuer("vinabook-password-reset")
				.setIssuedAt(new Date(now))
				.setExpiration(new Date(now + passwordResetExpirationMs))
				.claim("purpose", "password-reset")
				.signWith(SignatureAlgorithm.HS512, jwtSecrect)
				.compact();
	}

	public boolean validatePasswordResetToken(String token, String email) {
		try {
			Claims claims = Jwts.parser().setSigningKey(jwtSecrect).parseClaimsJws(token).getBody();
			return email != null
					&& email.equalsIgnoreCase(claims.getSubject())
					&& "vinabook-password-reset".equals(claims.getIssuer())
					&& "password-reset".equals(claims.get("purpose", String.class));
		} catch (Exception ex) {
			return false;
		}
	}

	public String getEmailFromJwtToken(String token) {
		return Jwts.parser().setSigningKey(jwtSecrect).parseClaimsJws(token).getBody().getSubject();
	}

	public boolean validateJwtToken(String authToken) {
		try {
			Jwts.parser().setSigningKey(jwtSecrect).parseClaimsJws(authToken);
			return true;
		} catch (SignatureException e) {
			logger.error("Invalid JWT signature: {}", e.getMessage());
		} catch (MalformedJwtException e) {
			logger.error("Invalid JWT token: {}", e.getMessage());
		} catch (ExpiredJwtException e) {
			logger.error("JWT token is expired: {}", e.getMessage());
		} catch (UnsupportedJwtException e) {
			logger.error("JWT token is usnupported: {}", e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.error("JWT claims string is empty: {}", e.getMessage());
		}

		return false;
	}

}
