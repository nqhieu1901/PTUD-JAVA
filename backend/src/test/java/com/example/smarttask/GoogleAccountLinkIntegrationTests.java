package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest(properties = {
		"smarttask.oauth.google.enabled=true",
		"smarttask.oauth.google.client-id=test-client-id",
		"smarttask.oauth.google.client-secret=test-client-secret",
		"smarttask.rate-limit.enabled=true",
		"smarttask.rate-limit.reauth-per-15-minutes=2"
})
@AutoConfigureMockMvc
@Transactional
class GoogleAccountLinkIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository users;
	@Autowired private PasswordEncoder passwords;

	@Test
	void startRequiresCsrfPasswordAndRotatesSessionBeforeReturningGuardedFlow() throws Exception {
		var account = localUser("link-start@example.com", "correct-password");
		var session = login(account.getEmail(), "correct-password");
		var oldSessionId = session.getId();

		mockMvc.perform(post("/api/users/me/google-link").session(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/users/me/google-link").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"wrong-password\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

		var started = mockMvc.perform(post("/api/users/me/google-link").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authorizationUrl")
						.value("/oauth2/authorization/google-link"))
				.andReturn();
		assertThat(session.getId()).isNotEqualTo(oldSessionId);

		mockMvc.perform(get("/oauth2/authorization/google-link").session(session))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location",
						org.hamcrest.Matchers.containsString("accounts.google.com")));
		assertThat(started.getResponse().getContentAsString()).doesNotContain("correct-password");
	}

	@Test
	void dedicatedAuthorizationEntryNeverActsAsPublicOrIntentlessGoogleLogin() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/oauth2/authorization/google-link"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("Location"));

		var account = localUser("no-intent@example.com", "correct-password");
		var session = login(account.getEmail(), "correct-password");
		var result = mockMvc.perform(get("/oauth2/authorization/google-link").session(session)).andReturn();
		assertThat(result.getResponse().getStatus() >= 300
				&& result.getResponse().getStatus() < 400).isFalse();
		assertThat(result.getResponse().getHeader("Location")).isNull();
	}

	@Test
	void onlyRoleUserWithLocalPasswordMayStartLinking() throws Exception {
		mockMvc.perform(post("/api/users/me/google-link")
				.with(user("admin@example.com").roles("ADMIN")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isForbidden());

		var googleOnly = new User("google-only-start@example.com",
				com.example.smarttask.auth.PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH, "Google only");
		users.saveAndFlush(googleOnly);
		mockMvc.perform(post("/api/users/me/google-link")
				.with(user(googleOnly.getEmail()).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"anything\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_LOGIN_DISABLED"));
	}

	@Test
	void unlinkRequiresPasswordThenRevokesEveryExistingSession() throws Exception {
		var account = localUser("unlink@example.com", "correct-password");
		account.setGoogleSubject("linked-subject");
		users.saveAndFlush(account);
		var currentSession = login(account.getEmail(), "correct-password");
		var otherSession = login(account.getEmail(), "correct-password");

		mockMvc.perform(post("/api/users/me/google-unlink").session(currentSession).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"wrong-password\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
		assertThat(account.getGoogleSubject()).isEqualTo("linked-subject");

		mockMvc.perform(post("/api/users/me/google-unlink").session(currentSession).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isNoContent());
		assertThat(currentSession.isInvalid()).isTrue();
		assertThat(account.getGoogleSubject()).isNull();

		mockMvc.perform(get("/api/auth/me").session(otherSession))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
	}

	@Test
	void alreadyLinkedAndNotLinkedStatesAreExplicit() throws Exception {
		var linked = localUser("already-linked@example.com", "correct-password");
		linked.setGoogleSubject("already-subject");
		users.saveAndFlush(linked);
		var linkedSession = login(linked.getEmail(), "correct-password");
		mockMvc.perform(post("/api/users/me/google-link").session(linkedSession).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_ALREADY_LINKED"));

		var unlinked = localUser("not-linked@example.com", "correct-password");
		var unlinkedSession = login(unlinked.getEmail(), "correct-password");
		mockMvc.perform(post("/api/users/me/google-unlink").session(unlinkedSession).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_NOT_LINKED"));
	}

	@Test
	void sensitiveAccountChangesAreRateLimitedAfterCsrfAndAuthentication() throws Exception {
		var account = localUser("reauth-limit@example.com", "correct-password");
		var session = login(account.getEmail(), "correct-password");
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/users/me/google-link").session(session).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"currentPassword\":\"wrong-password\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
		}
		mockMvc.perform(post("/api/users/me/google-link").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"wrong-password\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void cancellingGoogleClearsOneShotIntentButKeepsTheExistingPasswordSession() throws Exception {
		var account = localUser("cancel-link@example.com", "correct-password");
		var session = login(account.getEmail(), "correct-password");
		mockMvc.perform(post("/api/users/me/google-link").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"correct-password\"}"))
				.andExpect(status().isOk());
		var authorization = mockMvc.perform(get("/oauth2/authorization/google-link").session(session))
				.andExpect(status().is3xxRedirection()).andReturn();
		var state = org.springframework.web.util.UriComponentsBuilder
				.fromUriString(authorization.getResponse().getRedirectedUrl())
				.build().getQueryParams().getFirst("state");
		assertThat(state).isNotBlank();

		mockMvc.perform(get("/login/oauth2/code/google").session(session)
					.param("error", "access_denied").param("state", state))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location",
						"http://localhost:5173/?authError=google&reason=failed"));
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(account.getEmail()));

		var replay = mockMvc.perform(get("/oauth2/authorization/google-link").session(session)).andReturn();
		assertThat(replay.getResponse().getStatus() >= 300
				&& replay.getResponse().getStatus() < 400).isFalse();
		assertThat(replay.getResponse().getHeader("Location")).isNull();
	}

	private User localUser(String email, String password) {
		return users.saveAndFlush(new User(email, passwords.encode(password), "Local user"));
	}

	private MockHttpSession login(String email, String password) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andExpect(status().isOk()).andReturn().getRequest().getSession(false);
	}
}
