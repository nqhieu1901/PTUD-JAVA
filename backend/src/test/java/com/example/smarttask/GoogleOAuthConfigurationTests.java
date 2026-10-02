package com.example.smarttask;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"smarttask.oauth.google.enabled=true",
		"smarttask.oauth.google.client-id=test-client-id",
		"smarttask.oauth.google.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
class GoogleOAuthConfigurationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void enabledGoogleLoginExposesAuthorizationRedirect() throws Exception {
		mockMvc.perform(get("/api/auth/providers"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.google").value(true));

		mockMvc.perform(get("/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", containsString("accounts.google.com")));
	}
}
