package com.example.smarttask;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"smarttask.rate-limit.enabled=true", "smarttask.rate-limit.auth-per-minute=2"})
@AutoConfigureMockMvc
class RateLimitIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void failedLoginsReachTheLimitAndReadRequestsRemainAvailable() throws Exception {
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/auth/login").with(csrf())
					.with(request -> { request.setRemoteAddr("192.0.2.31"); return request; })
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"unknown-limit@example.test\",\"password\":\"bad-password\"}"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/auth/login").with(csrf())
				.with(request -> { request.setRemoteAddr("192.0.2.31"); return request; })
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"unknown-limit@example.test\",\"password\":\"bad-password\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		mockMvc.perform(get("/api/auth/csrf")
				.with(request -> { request.setRemoteAddr("192.0.2.31"); return request; }))
				.andExpect(status().isOk());
	}

	@Test
	void csrfProtectionIsNotReplacedByTheRateLimiter() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/auth/login")
					.with(request -> { request.setRemoteAddr("192.0.2.32"); return request; })
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(post("/api/auth/login").with(csrf())
				.with(request -> { request.setRemoteAddr("192.0.2.32"); return request; })
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}
}
