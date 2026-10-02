package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.auth.AuthUserResponse;
import com.example.smarttask.auth.GoogleAccountLinkService;
import com.example.smarttask.auth.GoogleLinkIntentStore;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.auth.VersionedIdentity;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@SpringBootTest
@Transactional
class GoogleAccountLinkServiceTests {

	@Autowired private GoogleAccountLinkService links;
	@Autowired private UserRepository users;
	@Autowired private PasswordEncoder passwords;

	@Test
	void passwordConfirmedIntentLinksOnlyTheMatchingVerifiedGoogleAccount() {
		var user = localUser("link@example.com", "correct-password");
		var target = links.begin(user.getEmail(), "correct-password");
		var before = user.getCredentialsVersion();

		var principal = links.complete(intent(target),
				googleUser("google-link-subject", " LINK@example.com ", true));

		assertThat(principal.getName()).isEqualTo(user.getEmail());
		assertThat(((VersionedIdentity) principal).credentialsVersion()).isEqualTo(before + 1);
		assertThat(user.getGoogleSubject()).isEqualTo("google-link-subject");
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(AuthUserResponse.from(user).googleLinked()).isTrue();
	}

	@Test
	void callbackRejectsUnverifiedOrDifferentEmailWithoutChangingTheAccount() {
		var user = localUser("right@example.com", "correct-password");
		var intent = intent(links.begin(user.getEmail(), "correct-password"));

		assertOauthCode(() -> links.complete(intent,
				googleUser("unverified-subject", "right@example.com", false)), "GOOGLE_PROFILE_INVALID");
		assertThat(user.getGoogleSubject()).isNull();

		assertOauthCode(() -> links.complete(intent,
				googleUser("wrong-email-subject", "other@example.com", true)), "GOOGLE_LINK_EMAIL_MISMATCH");
		assertThat(user.getGoogleSubject()).isNull();
	}

	@Test
	void callbackRejectsStaleIntentLockedTargetAndSubjectOwnedByAnotherUser() {
		var stale = localUser("stale-link@example.com", "correct-password");
		var staleIntent = intent(links.begin(stale.getEmail(), "correct-password"));
		stale.revokeCredentials();
		users.saveAndFlush(stale);
		assertOauthCode(() -> links.complete(staleIntent,
				googleUser("stale-subject", stale.getEmail(), true)), "GOOGLE_LINK_INTENT_STALE");

		var locked = localUser("locked-link@example.com", "correct-password");
		var lockedIntent = intent(links.begin(locked.getEmail(), "correct-password"));
		locked.setAccountStatus(AccountStatus.LOCKED);
		users.saveAndFlush(locked);
		assertOauthCode(() -> links.complete(lockedIntent,
				googleUser("locked-subject", locked.getEmail(), true)), "ACCOUNT_LOCKED");

		var owner = localUser("subject-owner@example.com", "correct-password");
		owner.setGoogleSubject("owned-subject");
		users.saveAndFlush(owner);
		var target = localUser("subject-target@example.com", "correct-password");
		var targetIntent = intent(links.begin(target.getEmail(), "correct-password"));
		assertOauthCode(() -> links.complete(targetIntent,
				googleUser("owned-subject", target.getEmail(), true)), "GOOGLE_LINK_SUBJECT_CONFLICT");
		assertThat(target.getGoogleSubject()).isNull();
	}

	@Test
	void beginAndUnlinkRequireTheCurrentLocalPasswordAndAUserRole() {
		var user = localUser("unlink-service@example.com", "correct-password");
		user.setGoogleSubject("unlink-subject");
		users.saveAndFlush(user);

		assertThatThrownBy(() -> links.unlink(user.getEmail(), "wrong-password"))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo("CURRENT_PASSWORD_INVALID"));
		assertThat(user.getGoogleSubject()).isEqualTo("unlink-subject");
		var version = user.getCredentialsVersion();
		links.unlink(user.getEmail(), "correct-password");
		assertThat(user.getGoogleSubject()).isNull();
		assertThat(user.getCredentialsVersion()).isEqualTo(version + 1);

		var admin = localUser("admin-link@example.com", "correct-password");
		admin.setSystemRole(SystemRole.ADMIN);
		users.saveAndFlush(admin);
		assertThatThrownBy(() -> links.begin(admin.getEmail(), "correct-password"))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo("GOOGLE_LINK_NOT_ALLOWED"));

		var googleOnly = new User("google-only-link@example.com",
				PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH, "Google only");
		users.saveAndFlush(googleOnly);
		assertThatThrownBy(() -> links.begin(googleOnly.getEmail(), "anything"))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo("PASSWORD_LOGIN_DISABLED"));
	}

	private User localUser(String email, String password) {
		return users.saveAndFlush(new User(email, passwords.encode(password), "Local user"));
	}

	private GoogleLinkIntentStore.Intent intent(GoogleAccountLinkService.LinkTarget target) {
		return new GoogleLinkIntentStore.Intent(target.userId(), target.email(),
				target.credentialsVersion(), Instant.now().plusSeconds(300));
	}

	private OidcUser googleUser(String subject, String email, boolean verified) {
		var result = mock(OidcUser.class);
		when(result.getSubject()).thenReturn(subject);
		when(result.getEmail()).thenReturn(email);
		when(result.getEmailVerified()).thenReturn(verified);
		return result;
	}

	private void assertOauthCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
				error -> assertThat(error.getError().getErrorCode()).isEqualTo(code));
	}
}
