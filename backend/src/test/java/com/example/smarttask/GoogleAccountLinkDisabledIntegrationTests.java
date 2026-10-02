package com.example.smarttask;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GoogleAccountLinkDisabledIntegrationTests {
	@Autowired private MockMvc mockMvc;

	@Test
	void startReportsUnavailableWhenGoogleIsDisabled() throws Exception {
		mockMvc.perform(post("/api/users/me/google-link")
				.with(user("person@example.com").roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("GOOGLE_OAUTH_DISABLED"));
	}
}
