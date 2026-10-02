package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import com.example.smarttask.admin.AdminUserService;
import com.example.smarttask.admin.UpdateAccountStatusRequest;
import com.example.smarttask.auth.AccountMailDelivery;
import com.example.smarttask.auth.AccountTokenPurpose;
import com.example.smarttask.auth.AccountTokenRepository;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;
import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountRecoveryIntegrationTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired AccountTokenRepository tokens;
	@Autowired PasswordEncoder passwords;
	@Autowired EntityManager entityManager;
	@Autowired AdminUserService adminUsers;
	@MockitoBean AccountMailDelivery delivery;
	private final List<String> deliveredBodies = new ArrayList<>();

	@BeforeEach
	void captureMailWithoutLoggingTokens() {
		deliveredBodies.clear();
		doAnswer(invocation -> {
			deliveredBodies.add(invocation.getArgument(2));
			return null;
		}).when(delivery).sendAfterCommit(anyString(), anyString(), anyString());
		doAnswer(invocation -> {
			deliveredBodies.add(invocation.getArgument(2));
			return null;
		}).when(delivery).sendAfterCommitIfAvailable(anyString(), anyString(), anyString());
	}

	@Test
	void unknownLockedAndGoogleOnlyEmailsHaveTheSameResponseAndNoTokens() throws Exception {
		var local = createUser("recover-local@example.com");
		var locked = createUser("recover-locked@example.com");
		locked.setAccountStatus(AccountStatus.LOCKED);
		var google = users.saveAndFlush(new User("recover-google@example.com", PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH, "Google"));
		var expected = forgot(local.getEmail()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		for (var email : List.of("missing@example.com", locked.getEmail(), google.getEmail())) {
			forgot(email).andExpect(status().isAccepted()).andExpect(content().string(expected))
					.andExpect(jsonPath("$.token").doesNotExist());
		}
		assertThat(tokens.count()).isEqualTo(1);
		assertThat(deliveredBodies).hasSize(1);
	}

	@Test
	void disabledDeliveryIsTheSameServiceUnavailableForEveryEmail() throws Exception {
		var local = createUser("disabled-delivery@example.com");
		doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Email unavailable"))
				.when(delivery).requireAvailable();
		for (var email : List.of(local.getEmail(), "missing@example.com")) {
			forgot(email).andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.code").value("MAIL_UNAVAILABLE"));
		}
		assertThat(tokens.count()).isZero();
		assertThat(deliveredBodies).isEmpty();
	}

	@Test
	void forgotDoesNotRevealTransportFailureThroughResponseStatus() throws Exception {
		var local = createUser("transport-failure@example.com");
		doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Email unavailable"))
				.when(delivery).sendAfterCommit(anyString(), anyString(), anyString());
		var known = forgot(local.getEmail()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		forgot("missing@example.com").andExpect(status().isAccepted()).andExpect(content().string(known));
	}

	@Test
	void resetTokenIsHashedSingleUseAndRevokesEveryExistingSession() throws Exception {
		var local = createUser("reset-sessions@example.com");
		var first = login(local.getEmail(), "original-password");
		var second = login(local.getEmail(), "original-password");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var raw = lastToken("reset-password");
		var stored = tokens.findByUser_IdAndPurpose(local.getId(), AccountTokenPurpose.PASSWORD_RESET).orElseThrow();
		assertThat(raw).hasSize(43);
		assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(raw);
		assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC));

		reset(raw, "replacement-password").andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());
		assertThat(deliveredBodies.getLast())
				.contains("vừa được đặt lại")
				.doesNotContain(raw, "replacement-password");
		assertThat(stored.getConsumedAt()).isNotNull();
		assertThat(users.findById(local.getId()).orElseThrow().getCredentialsVersion()).isEqualTo(1);
		for (var session : List.of(first, second)) {
			mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
			assertThat(session.isInvalid()).isTrue();
		}
		reset(raw, "another-password").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ACCOUNT_TOKEN_INVALID"));
		loginRequest(local.getEmail(), "original-password").andExpect(status().isUnauthorized());
		var next = login(local.getEmail(), "replacement-password");
		mvc.perform(get("/api/auth/me").session(next)).andExpect(status().isOk());
	}

	@Test
	void notificationFailureDoesNotRollBackACompletedPasswordReset() throws Exception {
		var local = createUser("reset-notification-failure@example.com");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var raw = lastToken("reset-password");
		doThrow(new IllegalStateException("test-only delivery failure"))
				.when(delivery).sendAfterCommitIfAvailable(anyString(), anyString(), anyString());

		reset(raw, "replacement-password").andExpect(status().isOk());
		assertThat(passwords.matches("replacement-password",
				users.findById(local.getId()).orElseThrow().getPasswordHash())).isTrue();
		assertThat(tokens.findByUser_IdAndPurpose(local.getId(), AccountTokenPurpose.PASSWORD_RESET)
				.orElseThrow().getConsumedAt()).isNotNull();
	}

	@Test
	void verificationRequiresExplicitPostAndDoesNotSignOutCurrentSession() throws Exception {
		var local = createUser("verify-session@example.com");
		var session = login(local.getEmail(), "original-password");
		mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.emailVerified").value(false));
		requestVerification(local).andExpect(status().isAccepted());
		var raw = lastToken("verify-email");
		mvc.perform(get("/api/auth/email-verification/confirm").param("token", raw))
				.andExpect(status().isUnauthorized());
		assertThat(local.isEmailVerified()).isFalse();
		confirm(raw).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.emailVerified").value(true));
		confirm(raw).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACCOUNT_TOKEN_INVALID"));
		requestVerification(local).andExpect(status().isAccepted());
		assertThat(deliveredBodies).hasSize(1);
	}

	@Test
	void tokensCannotBeUsedAcrossPurposes() throws Exception {
		var local = createUser("purpose@example.com");
		requestVerification(local).andExpect(status().isAccepted());
		var verification = lastToken("verify-email");
		reset(verification, "replacement-password").andExpect(status().isBadRequest());
		forgot(local.getEmail()).andExpect(status().isAccepted());
		confirm(lastToken("reset-password")).andExpect(status().isBadRequest());
		assertThat(local.isEmailVerified()).isFalse();
		assertThat(passwords.matches("original-password", local.getPasswordHash())).isTrue();
	}

	@Test
	void cooldownSuppressesResendThenRotationInvalidatesOldLink() throws Exception {
		var local = createUser("resend@example.com");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var original = lastToken("reset-password");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		assertThat(deliveredBodies).hasSize(1);
		var current = tokens.findByUser_IdAndPurpose(local.getId(), AccountTokenPurpose.PASSWORD_RESET).orElseThrow();
		ReflectionTestUtils.setField(current, "issuedAt", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2));
		tokens.saveAndFlush(current);
		entityManager.clear();
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var replacement = lastToken("reset-password");
		assertThat(replacement).isNotEqualTo(original);
		assertThat(tokens.count()).isEqualTo(1);
		reset(original, "replacement-password").andExpect(status().isBadRequest());
		reset(replacement, "replacement-password").andExpect(status().isOk());
	}

	@Test
	void expiredTokenAndUnicodePasswordLimitLeaveCredentialsUnchanged() throws Exception {
		var local = createUser("expiry@example.com");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var raw = lastToken("reset-password");
		reset(raw, "ệ".repeat(25)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
		assertThat(tokens.findByUser_IdAndPurpose(local.getId(), AccountTokenPurpose.PASSWORD_RESET).orElseThrow().getConsumedAt()).isNull();
		var current = tokens.findByUser_IdAndPurpose(local.getId(), AccountTokenPurpose.PASSWORD_RESET).orElseThrow();
		ReflectionTestUtils.setField(current, "expiresAt", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
		tokens.saveAndFlush(current);
		entityManager.clear();
		reset(raw, "replacement-password").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACCOUNT_TOKEN_INVALID"));
		assertThat(passwords.matches("original-password", users.findById(local.getId()).orElseThrow().getPasswordHash())).isTrue();
	}

	@Test
	void lockingAccountAfterIssuancePreventsRecovery() throws Exception {
		var local = createUser("lock-after@example.com");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var raw = lastToken("reset-password");
		local.setAccountStatus(AccountStatus.LOCKED);
		users.saveAndFlush(local);
		reset(raw, "replacement-password").andExpect(status().isBadRequest());
		assertThat(passwords.matches("original-password", local.getPasswordHash())).isTrue();
	}

	@Test
	void tokenIssuedBeforeAnAdminLockCannotBeUsedAfterUnlock() throws Exception {
		var admin = createUser("recovery-admin@example.com");
		admin.setSystemRole(SystemRole.ADMIN);
		users.saveAndFlush(admin);
		var local = createUser("lock-unlock-token@example.com");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var raw = lastToken("reset-password");

		adminUsers.updateStatus(admin.getEmail(), local.getId(), new UpdateAccountStatusRequest(AccountStatus.LOCKED));
		adminUsers.updateStatus(admin.getEmail(), local.getId(), new UpdateAccountStatusRequest(AccountStatus.ACTIVE));

		assertThat(local.getCredentialsVersion()).isEqualTo(2);
		reset(raw, "replacement-password").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ACCOUNT_TOKEN_INVALID"));
		assertThat(passwords.matches("original-password", local.getPasswordHash())).isTrue();
	}

	@Test
	void normalPasswordChangeAlsoRevokesSessionAndPendingRecoveryTokens() throws Exception {
		var local = createUser("password-change-revoke@example.com");
		var session = login(local.getEmail(), "original-password");
		forgot(local.getEmail()).andExpect(status().isAccepted());
		var raw = lastToken("reset-password");
		mvc.perform(put("/api/users/me/password").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"original-password\",\"newPassword\":\"replacement-password\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
		reset(raw, "another-password").andExpect(status().isBadRequest());
	}

	@Test
	void staleSessionsDoNotBlockPublicCsrfResetOrVerificationConfirmation() throws Exception {
		var local = createUser("stale-public@example.com");
		var staleCsrf = login(local.getEmail(), "original-password");
		var staleReset = login(local.getEmail(), "original-password");
		local.setPasswordHash(passwords.encode("intermediate-password"));
		local.revokeCredentials();
		users.saveAndFlush(local);

		forgot(local.getEmail()).andExpect(status().isAccepted());
		var resetToken = lastToken("reset-password");
		mvc.perform(get("/api/auth/csrf").session(staleCsrf))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
		assertThat(staleCsrf.isInvalid()).isTrue();

		mvc.perform(post("/api/auth/reset-password").session(staleReset).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"newPassword\":\"final-password\"}".formatted(resetToken)))
				.andExpect(status().isOk());
		assertThat(staleReset.isInvalid()).isTrue();
		loginRequest(local.getEmail(), "final-password").andExpect(status().isOk());

		var verificationUser = createUser("stale-confirm@example.com");
		var staleConfirmation = login(verificationUser.getEmail(), "original-password");
		verificationUser.revokeCredentials();
		users.saveAndFlush(verificationUser);
		requestVerification(verificationUser).andExpect(status().isAccepted());
		var verificationToken = lastToken("verify-email");
		mvc.perform(post("/api/auth/email-verification/confirm").session(staleConfirmation).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(verificationToken)))
				.andExpect(status().isOk());
		assertThat(staleConfirmation.isInvalid()).isTrue();
		assertThat(verificationUser.isEmailVerified()).isTrue();
	}

	@Test
	void staleSessionCanBeReauthenticatedWithoutDiscardingTheFreshLogin() throws Exception {
		var local = createUser("stale-login@example.com");
		var stale = login(local.getEmail(), "original-password");
		local.setPasswordHash(passwords.encode("replacement-password"));
		local.revokeCredentials();
		users.saveAndFlush(local);

		mvc.perform(post("/api/auth/login").session(stale).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"stale-login@example.com\",\"password\":\"replacement-password\"}"))
				.andExpect(status().isOk());
		assertThat(stale.isInvalid()).isFalse();
		mvc.perform(get("/api/auth/me").session(stale)).andExpect(status().isOk());
	}

	@Test
	void recoveryEndpointsRequireCsrfAndVerificationRequestRequiresAuthentication() throws Exception {
		for (var route : List.of("forgot-password", "reset-password", "email-verification/confirm")) {
			mvc.perform(post("/api/auth/" + route).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isForbidden());
		}
		mvc.perform(post("/api/auth/email-verification/request").with(csrf())).andExpect(status().isUnauthorized());
		forgot("invalid-email").andExpect(status().isBadRequest());
		reset("bad-token", "short").andExpect(status().isBadRequest());
	}

	@Test
	void forgotNormalizesEmailWithoutChangingItsGenericResponse() throws Exception {
		var local = createUser("normalized-recovery@example.com");
		var expected = forgot("  NORMALIZED-RECOVERY@EXAMPLE.COM  ")
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		forgot("missing-normalized@example.com").andExpect(status().isAccepted())
				.andExpect(content().string(expected));
		assertThat(tokens.findByUser_IdAndPurpose(local.getId(), AccountTokenPurpose.PASSWORD_RESET)).isPresent();
	}

	@Test
	void registrationSucceedsWhenMailIsDisabled() throws Exception {
		when(delivery.isAvailable()).thenReturn(false);
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"registration-without-mail@example.com",
						 "password":"registration-password","fullName":"Registration user"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.token").doesNotExist());
		var saved = users.findByEmailIgnoreCase("registration-without-mail@example.com").orElseThrow();
		assertThat(tokens.findByUser_IdAndPurpose(saved.getId(), AccountTokenPurpose.EMAIL_VERIFICATION)).isEmpty();
		verify(delivery, never()).sendAfterCommit(anyString(), anyString(), anyString());
	}

	@Test
	void registrationQueuesVerificationWithoutReturningItsToken() throws Exception {
		when(delivery.isAvailable()).thenReturn(true);
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"registration-verification@example.com",
						 "password":"registration-password","fullName":"Registration user"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.token").doesNotExist());

		var saved = users.findByEmailIgnoreCase("registration-verification@example.com").orElseThrow();
		assertThat(tokens.findByUser_IdAndPurpose(saved.getId(), AccountTokenPurpose.EMAIL_VERIFICATION)).isPresent();
		assertThat(lastToken("verify-email")).hasSize(43);
	}

	private User createUser(String email) {
		return users.saveAndFlush(new User(email, passwords.encode("original-password"), "Recovery user"));
	}

	private ResultActions forgot(String email) throws Exception {
		return mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\"}".formatted(email)));
	}

	private ResultActions reset(String token, String password) throws Exception {
		return mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password)));
	}

	private ResultActions confirm(String token) throws Exception {
		return mvc.perform(post("/api/auth/email-verification/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(token)));
	}

	private ResultActions requestVerification(User account) throws Exception {
		return mvc.perform(post("/api/auth/email-verification/request").with(user(account.getEmail())).with(csrf()));
	}

	private ResultActions loginRequest(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private MockHttpSession login(String email, String password) throws Exception {
		return (MockHttpSession) loginRequest(email, password).andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
	}

	private String lastToken(String fragment) {
		var matcher = Pattern.compile("#" + fragment + "=([A-Za-z0-9_-]{43})").matcher(deliveredBodies.getLast());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
