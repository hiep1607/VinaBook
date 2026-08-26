package vn.fs.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilsTest {

	private JwtUtils jwtUtils;

	@BeforeEach
	void setUp() {
		jwtUtils = new JwtUtils();
		ReflectionTestUtils.setField(jwtUtils, "jwtSecrect",
				"test-secret-that-is-long-enough-for-reset-token-signing-123456789");
		ReflectionTestUtils.setField(jwtUtils, "passwordResetExpirationMs", 900000);
	}

	@Test
	void resetTokenIsValidOnlyForTheRequestedEmail() {
		String token = jwtUtils.generatePasswordResetToken("customer@example.com");

		assertTrue(jwtUtils.validatePasswordResetToken(token, "customer@example.com"));
		assertFalse(jwtUtils.validatePasswordResetToken(token, "other@example.com"));
	}

	@Test
	void expiredResetTokenIsRejected() {
		ReflectionTestUtils.setField(jwtUtils, "passwordResetExpirationMs", -1000);
		String token = jwtUtils.generatePasswordResetToken("customer@example.com");

		assertFalse(jwtUtils.validatePasswordResetToken(token, "customer@example.com"));
	}

	@Test
	void malformedResetTokenIsRejected() {
		assertFalse(jwtUtils.validatePasswordResetToken("not-a-token", "customer@example.com"));
	}
}
