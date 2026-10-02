package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.auth.GoogleAccountService;
import com.example.smarttask.auth.AuthUserResponse;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@Transactional
class GoogleAccountServiceTests {

	@Autowired
	private GoogleAccountService googleAccountService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void verifiedGoogleProfileCreatesReusableLocalAccount() {
		var googleUser = googleUser("google-subject-1", " Google.User@Example.com ", "Người dùng Google", true);

		var principal = googleAccountService.authenticate(googleUser);
		var saved = userRepository.findByGoogleSubject("google-subject-1").orElseThrow();

		assertThat(principal.getName()).isEqualTo("google.user@example.com");
		assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
		assertThat(saved.getEmail()).isEqualTo("google.user@example.com");
		assertThat(saved.getFullName()).isEqualTo("Người dùng Google");
		assertThat(saved.getPasswordHash()).isEqualTo(PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH);
		assertThat(saved.isEmailVerified()).isTrue();
		assertThat(AuthUserResponse.from(saved).passwordLoginEnabled()).isFalse();
		assertThat(googleAccountService.authenticate(googleUser).getName()).isEqualTo(saved.getEmail());
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void verifiedGoogleProfileCannotTakeOverAPreRegisteredLocalAccount() {
		userRepository.saveAndFlush(new User("linked@example.com", "{noop}attacker-password", "Tài khoản có sẵn"));

		assertThatThrownBy(() -> googleAccountService.authenticate(googleUser(
				"google-subject-linked", "linked@example.com", "Tên Google", true)))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("GOOGLE_ACCOUNT_LINK_REQUIRED"));

		var saved = userRepository.findByEmailIgnoreCase("linked@example.com").orElseThrow();
		assertThat(saved.getGoogleSubject()).isNull();
		assertThat(saved.getPasswordHash()).isEqualTo("{noop}attacker-password");
		assertThat(saved.getFullName()).isEqualTo("Tài khoản có sẵn");
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void anAlreadyLinkedSubjectKeepsItsLocalAccountAndPasswordWhenGoogleEmailChanges() {
		var linked = new User("original@example.com", "{noop}existing-password", "Tài khoản cũ");
		linked.setGoogleSubject("linked-stable-subject");
		linked.setSystemRole(SystemRole.ADMIN);
		userRepository.saveAndFlush(linked);

		var principal = googleAccountService.authenticate(googleUser(
				"linked-stable-subject", "new-address@example.com", "Tên Google", true));

		assertThat(principal.getName()).isEqualTo("original@example.com");
		assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
		assertThat(linked.getPasswordHash()).isEqualTo("{noop}existing-password");
		assertThat(AuthUserResponse.from(linked).passwordLoginEnabled()).isTrue();
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void anotherGoogleSubjectCannotReplaceAnExistingLink() {
		var linked = new User("collision@example.com", "{noop}existing-password", "Đã liên kết");
		linked.setGoogleSubject("original-google-subject");
		userRepository.saveAndFlush(linked);

		assertThatThrownBy(() -> googleAccountService.authenticate(googleUser(
				"different-google-subject", "collision@example.com", "Tên Google", true)))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("GOOGLE_ACCOUNT_CONFLICT"));
		assertThat(linked.getGoogleSubject()).isEqualTo("original-google-subject");
	}

	@Test
	void unverifiedOrLockedGoogleAccountCannotAuthenticate() {
		assertThatThrownBy(() -> googleAccountService.authenticate(
				googleUser("unverified", "unverified@example.com", "Chưa xác minh", false)))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessageContaining("email đã xác minh");

		var locked = new User("locked-google@example.com", "{noop}unused", "Đã khóa");
		locked.setGoogleSubject("locked-google-subject");
		locked.setAccountStatus(AccountStatus.LOCKED);
		userRepository.save(locked);

		assertThatThrownBy(() -> googleAccountService.authenticate(
				googleUser("locked-google-subject", "locked-google@example.com", "Đã khóa", true)))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessageContaining("đã bị khóa");
	}

	private OidcUser googleUser(
			String subject,
			String email,
			String fullName,
			boolean emailVerified) {
		var user = mock(OidcUser.class);
		when(user.getSubject()).thenReturn(subject);
		when(user.getEmail()).thenReturn(email);
		when(user.getFullName()).thenReturn(fullName);
		when(user.getEmailVerified()).thenReturn(emailVerified);
		return user;
	}
}
