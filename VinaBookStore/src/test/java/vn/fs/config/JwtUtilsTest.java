package vn.fs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import vn.fs.service.implement.UserDetailsImpl;

// JwtUtils handles login JWTs. Password reset currently uses a database token,
// not the removed generatePasswordResetToken/validatePasswordResetToken API.
class JwtUtilsTest {

	private JwtUtils jwtUtils;

	@BeforeEach
	void setUp() {
		jwtUtils = new JwtUtils();
		ReflectionTestUtils.setField(jwtUtils, "jwtSecrect",
				"test-secret-that-is-long-enough-for-token-signing-123456789");
		ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 900000);
	}

	private String generateToken(String email) {
		UserDetailsImpl principal = new UserDetailsImpl();
		principal.setEmail(email);
		return jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(principal, null));
	}

	@Test
	void loginTokenContainsTheAuthenticatedEmail() {
		String token = generateToken("customer@example.com");
		assertTrue(jwtUtils.validateJwtToken(token));
		assertEquals("customer@example.com", jwtUtils.getEmailFromJwtToken(token));
	}

	@Test
	void expiredLoginTokenIsRejected() {
		ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -60000);
		assertFalse(jwtUtils.validateJwtToken(generateToken("customer@example.com")));
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		String token = generateToken("customer@example.com");
		ReflectionTestUtils.setField(jwtUtils, "jwtSecrect",
				"another-test-secret-for-token-signing-987654321");
		assertFalse(jwtUtils.validateJwtToken(token));
	}

	@Test
	void malformedOrEmptyLoginTokenIsRejected() {
		assertFalse(jwtUtils.validateJwtToken("not-a-token"));
		assertFalse(jwtUtils.validateJwtToken(""));
		assertFalse(jwtUtils.validateJwtToken(null));
	}
}

