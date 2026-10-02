package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void adminCanListSearchAndPageUsers() throws Exception {
		var admin = createUser("admin-list@example.com", "Quản trị viên", SystemRole.ADMIN);
		createUser("an@example.com", "Nguyễn An", SystemRole.USER);
		createUser("binh@example.com", "Trần Bình", SystemRole.USER);

		mockMvc.perform(get("/api/admin/users")
					.with(user(admin.getEmail()).roles("ADMIN"))
					.param("query", "Nguyễn")
					.param("page", "0")
					.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.users[0].email").value("an@example.com"))
				.andExpect(jsonPath("$.users[0].role").value("USER"))
				.andExpect(jsonPath("$.users[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.page").value(0));
	}

	@Test
	void adminEndpointsRequireAuthenticationAndAdminRole() throws Exception {
		var ordinaryUser = createUser("ordinary@example.com", "Người dùng", SystemRole.USER);

		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/users")
					.with(user(ordinaryUser.getEmail()).roles("USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void adminCannotAccessProjectAndNotificationApis() throws Exception {
		var admin = createUser("admin-boundary@example.com", "Quản trị viên", SystemRole.ADMIN);

		mockMvc.perform(get("/api/projects")
					.with(user(admin.getEmail()).roles("ADMIN")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		mockMvc.perform(get("/api/notifications")
					.with(user(admin.getEmail()).roles("ADMIN")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void adminCanLockAndUnlockAnAccount() throws Exception {
		var admin = createUser("admin-status@example.com", "Quản trị viên", SystemRole.ADMIN);
		var target = createUser("target@example.com", "Tài khoản mục tiêu", SystemRole.USER);
		var activeSession = login(target.getEmail(), "test-password")
				.andExpect(status().isOk())
				.andReturn()
				.getRequest()
				.getSession(false);

		mockMvc.perform(put("/api/admin/users/{userId}/status", target.getId())
					.with(user(admin.getEmail()).roles("ADMIN"))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"LOCKED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LOCKED"))
				.andExpect(jsonPath("$.lockedAt").isNotEmpty());

		var locked = userRepository.findById(target.getId()).orElseThrow();
		assertThat(locked.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
		assertThat(locked.getLockedAt()).isNotNull();
		assertThat(locked.getCredentialsVersion()).isEqualTo(1);

		mockMvc.perform(get("/api/auth/me").session((MockHttpSession) activeSession))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
		login(target.getEmail(), "test-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		mockMvc.perform(put("/api/admin/users/{userId}/status", target.getId())
					.with(user(admin.getEmail()).roles("ADMIN"))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.lockedAt").doesNotExist());

		login(target.getEmail(), "test-password")
				.andExpect(status().isOk());
		assertThat(userRepository.findById(target.getId()).orElseThrow().getCredentialsVersion()).isEqualTo(2);
	}

	@Test
	void repeatingTheSameStatusDoesNotRevokeCredentialsAgain() throws Exception {
		var admin = createUser("admin-repeat-status@example.com", "Quản trị viên", SystemRole.ADMIN);
		var target = createUser("repeat-status@example.com", "Tài khoản mục tiêu", SystemRole.USER);

		for (var ignored = 0; ignored < 2; ignored++) {
			mockMvc.perform(put("/api/admin/users/{userId}/status", target.getId())
					.with(user(admin.getEmail()).roles("ADMIN"))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"LOCKED\"}"))
					.andExpect(status().isOk());
		}

		assertThat(userRepository.findById(target.getId()).orElseThrow().getCredentialsVersion()).isEqualTo(1);
	}

	@Test
	void adminCannotLockTheCurrentAccount() throws Exception {
		var admin = createUser("admin-self@example.com", "Quản trị viên", SystemRole.ADMIN);

		mockMvc.perform(put("/api/admin/users/{userId}/status", admin.getId())
					.with(user(admin.getEmail()).roles("ADMIN"))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"LOCKED\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANNOT_LOCK_SELF"));

		assertThat(userRepository.findById(admin.getId()).orElseThrow().getAccountStatus())
				.isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void statusUpdateRequiresCsrf() throws Exception {
		var admin = createUser("admin-csrf@example.com", "Quản trị viên", SystemRole.ADMIN);
		var target = createUser("target-csrf@example.com", "Người dùng", SystemRole.USER);

		mockMvc.perform(put("/api/admin/users/{userId}/status", target.getId())
					.with(user(admin.getEmail()).roles("ADMIN"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"LOCKED\"}"))
				.andExpect(status().isForbidden());
	}

	private User createUser(String email, String fullName, SystemRole role) {
		var user = new User(email, passwordEncoder.encode("test-password"), fullName);
		user.setSystemRole(role);
		return userRepository.saveAndFlush(user);
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
