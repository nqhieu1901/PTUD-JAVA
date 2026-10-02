package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.User;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void updateProfileChangesOnlyTheNormalizedFullName() throws Exception {
		var account = createUser("settings-profile@example.com", "Tên ban đầu", "current-password");

		mockMvc.perform(put("/api/users/me")
					.with(user(account.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"fullName":" Nguyễn Văn Mới "}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(account.getId()))
				.andExpect(jsonPath("$.email").value("settings-profile@example.com"))
				.andExpect(jsonPath("$.fullName").value("Nguyễn Văn Mới"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		var saved = userRepository.findById(account.getId()).orElseThrow();
		assertThat(saved.getEmail()).isEqualTo("settings-profile@example.com");
		assertThat(saved.getFullName()).isEqualTo("Nguyễn Văn Mới");
		assertThat(passwordEncoder.matches("current-password", saved.getPasswordHash())).isTrue();
	}

	@Test
	void profileAndPasswordRequestsValidateTheirPayloads() throws Exception {
		var account = createUser("settings-validation@example.com", "Validation User", "current-password");

		mockMvc.perform(put("/api/users/me")
					.with(user(account.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.fullName").exists());

		mockMvc.perform(put("/api/users/me/password")
					.with(user(account.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "currentPassword": "",
							  "newPassword": "short"
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.currentPassword").exists())
				.andExpect(jsonPath("$.fieldErrors.newPassword").exists());
	}

	@Test
	void changePasswordInvalidatesTheOldPasswordAndSupportsTheNextLogin() throws Exception {
		var account = createUser("settings-password@example.com", "Password User", "current-password");

		mockMvc.perform(put("/api/users/me/password")
					.with(user(account.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "currentPassword": "current-password",
							  "newPassword": "replacement-password"
							}
							"""))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		var saved = userRepository.findById(account.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("current-password", saved.getPasswordHash())).isFalse();
		assertThat(passwordEncoder.matches("replacement-password", saved.getPasswordHash())).isTrue();

		login(account.getEmail(), "current-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login(account.getEmail(), "replacement-password")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(account.getEmail()));
	}

	@Test
	void wrongCurrentPasswordLeavesTheStoredPasswordUnchanged() throws Exception {
		var account = createUser("settings-wrong-password@example.com", "Wrong Password", "current-password");

		mockMvc.perform(put("/api/users/me/password")
					.with(user(account.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "currentPassword": "incorrect-password",
							  "newPassword": "replacement-password"
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

		var saved = userRepository.findById(account.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("current-password", saved.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("replacement-password", saved.getPasswordHash())).isFalse();
	}

	@Test
	void changingEitherUnicodePasswordBeyondBcryptLimitReturnsBadRequestAndKeepsHash() throws Exception {
		var account = createUser("unicode-change@example.com", "Unicode Password", "current-password");
		var previousHash = account.getPasswordHash();
		var tooLong = "ệ".repeat(25);
		for (var body : new String[] {
				"{\"currentPassword\":\"current-password\",\"newPassword\":\"%s\"}".formatted(tooLong),
				"{\"currentPassword\":\"%s\",\"newPassword\":\"new-password\"}".formatted(tooLong) }) {
			mockMvc.perform(put("/api/users/me/password")
						.with(user(account.getEmail()))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
		}
		assertThat(account.getPasswordHash()).isEqualTo(previousHash);
	}

	@Test
	void googleOnlyProfileReportsNoPasswordAndCannotUseChangePassword() throws Exception {
		var account = new User("google-settings@example.com", PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH, "Google settings");
		account.setGoogleSubject("google-settings-subject");
		userRepository.saveAndFlush(account);

		mockMvc.perform(get("/api/auth/me").with(user(account.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.passwordLoginEnabled").value(false));
		mockMvc.perform(put("/api/users/me/password")
					.with(user(account.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validPasswordBody()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_LOGIN_DISABLED"));
		assertThat(account.getPasswordHash()).isEqualTo(PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH);
	}

	@Test
	void settingsRequireAuthenticationAndCsrf() throws Exception {
		var account = createUser("settings-security@example.com", "Security User", "current-password");

		mockMvc.perform(put("/api/users/me")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"Tên mới\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(put("/api/users/me/password")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validPasswordBody()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(put("/api/users/me")
					.with(user(account.getEmail()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"Tên mới\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		mockMvc.perform(put("/api/users/me/password")
					.with(user(account.getEmail()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(validPasswordBody()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	private User createUser(String email, String fullName, String password) {
		return userRepository.saveAndFlush(new User(
				email,
				passwordEncoder.encode(password),
				fullName));
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

	private String validPasswordBody() {
		return """
				{
				  "currentPassword": "current-password",
				  "newPassword": "replacement-password"
				}
				""";
	}
}
