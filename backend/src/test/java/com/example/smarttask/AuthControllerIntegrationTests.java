package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void csrfEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
				.andExpect(jsonPath("$.parameterName").value("_csrf"));
	}

	@Test
	void providersEndpointReportsGoogleDisabledByDefault() throws Exception {
		mockMvc.perform(get("/api/auth/providers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.google").value(false));
	}

	@Test
	void tokenReturnedByCsrfEndpointProtectsTheNextRequest() throws Exception {
		var csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		var responseBody = csrfResult.getResponse().getContentAsString();
		String token = JsonPath.read(responseBody, "$.token");
		String headerName = JsonPath.read(responseBody, "$.headerName");

		mockMvc.perform(post("/api/auth/register")
					.session(session)
					.header(headerName, token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "csrf@example.com",
							  "password": "correct-password",
							  "fullName": "CSRF Browser Flow"
							}
							"""))
				.andExpect(status().isCreated());
	}

	@Test
	void registerCreatesActiveUserWithoutExposingPassword() throws Exception {
		mockMvc.perform(post("/api/auth/register")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": " New.User@Example.com ",
							  "password": "correct-password",
							  "fullName": " Nguyễn Văn A "
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("new.user@example.com"))
				.andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.passwordLoginEnabled").value(true))
				.andExpect(jsonPath("$.googleLinked").value(false))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		var savedUser = userRepository.findByEmailIgnoreCase("new.user@example.com").orElseThrow();
		assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(savedUser.getPasswordHash()).isNotEqualTo("correct-password");
		assertThat(passwordEncoder.matches("correct-password", savedUser.getPasswordHash())).isTrue();
	}

	@Test
	void registerRejectsNormalizedDuplicateEmail() throws Exception {
		register("Duplicate@Example.com", "valid-password", "Người dùng đầu tiên")
				.andExpect(status().isCreated());

		register(" duplicate@example.com ", "another-password", "Người dùng thứ hai")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void registerRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/api/auth/register")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "not-an-email",
							  "password": "short",
							  "fullName": "   "
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists())
				.andExpect(jsonPath("$.fieldErrors.fullName").exists());

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void loginCreatesReusableSession() throws Exception {
		register("member@example.com", "correct-password", "Thành viên")
				.andExpect(status().isCreated());

		var result = login(" MEMBER@example.com ", "correct-password")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("member@example.com"))
				.andReturn();

		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("member@example.com"))
				.andExpect(jsonPath("$.fullName").value("Thành viên"));
	}

	@Test
	void loginRejectsBadCredentials() throws Exception {
		register("login@example.com", "correct-password", "Người đăng nhập")
				.andExpect(status().isCreated());

		login("login@example.com", "wrong-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void registerAndLoginRejectPasswordsBeyondBcryptUtf8Limit() throws Exception {
		var tooLongPassword = "ệ".repeat(25);
		register("unicode-password@example.com", tooLongPassword, "Mật khẩu Unicode")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
		assertThat(userRepository.existsByEmailIgnoreCase("unicode-password@example.com")).isFalse();

		register("unicode-password@example.com", "ệ".repeat(24), "Mật khẩu Unicode")
				.andExpect(status().isCreated());
		login("unicode-password@example.com", "ệ".repeat(24))
				.andExpect(status().isOk());
		login("unicode-password@example.com", tooLongPassword)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
	}

	@Test
	void googleOnlyAccountRejectsPasswordLoginWithoutEncoderErrors() throws Exception {
		var account = new User("google-only@example.com", PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH, "Google only");
		account.setGoogleSubject("google-only-subject");
		userRepository.saveAndFlush(account);

		login(account.getEmail(), PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void failedLoginClearsAnyPreviousAuthentication() throws Exception {
		register("reauth@example.com", "correct-password", "Người xác thực lại")
				.andExpect(status().isCreated());
		var firstLogin = login("reauth@example.com", "correct-password")
				.andExpect(status().isOk())
				.andReturn();
		var session = (MockHttpSession) firstLogin.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/login")
					.session(session)
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "reauth@example.com",
							  "password": "wrong-password"
							}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void lockedUserReceivesTheSameGenericLoginError() throws Exception {
		var user = new User(
				"locked@example.com",
				passwordEncoder.encode("correct-password"),
				"Tài khoản bị khóa");
		user.setAccountStatus(AccountStatus.LOCKED);
		user.setLockedAt(LocalDateTime.now());
		userRepository.save(user);

		login("locked@example.com", "correct-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void meRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void logoutRequiresCsrfAndInvalidatesAnAuthenticatedSession() throws Exception {
		register("logout@example.com", "correct-password", "Người đăng xuất")
				.andExpect(status().isCreated());
		var loginResult = login("logout@example.com", "correct-password")
				.andExpect(status().isOk())
				.andReturn();
		var session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		assertThat(session.isInvalid()).isTrue();
	}

	private org.springframework.test.web.servlet.ResultActions register(
			String email,
			String password,
			String fullName) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s",
						  "fullName": "%s"
						}
						""".formatted(email, password, fullName)));
	}

	private org.springframework.test.web.servlet.ResultActions login(
			String email,
			String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}
}
