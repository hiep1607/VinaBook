package vn.fs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;

@SpringBootTest(properties =
        "bezkoder.app.jwtSecret=test-only-secret-that-must-never-be-used-in-production-123456789")
@AutoConfigureMockMvc
class VinaBookApplicationTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void catalogIsPublic() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk());
	}

	@Test
	void customerListRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void legacyOtpMailRelayIsNotPublic() throws Exception {
		mockMvc.perform(post("/api/send-mail/otp")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isUnauthorized());
	}

}
