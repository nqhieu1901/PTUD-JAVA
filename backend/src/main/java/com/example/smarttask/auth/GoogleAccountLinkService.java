package com.example.smarttask.auth;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class GoogleAccountLinkService {

	private final UserRepository users;
	private final AccountTokenRepository accountTokens;
	private final PasswordEncoder passwords;

	public GoogleAccountLinkService(UserRepository users, AccountTokenRepository accountTokens,
			PasswordEncoder passwords) {
		this.users = users;
		this.accountTokens = accountTokens;
		this.passwords = passwords;
	}

	@Transactional
	public LinkTarget begin(String currentEmail, String currentPassword) {
		PasswordPolicy.requireSupportedLength(currentPassword);
		var user = users.findByEmailForUpdate(normalizeEmail(currentEmail))
				.orElseThrow(this::unauthorized);
		requireActivePasswordUser(user);
		if (!passwords.matches(currentPassword, user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID",
					"Mật khẩu hiện tại không đúng");
		}
		if (user.getGoogleSubject() != null) {
			throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_ALREADY_LINKED",
					"Tài khoản đã liên kết Google");
		}
		return new LinkTarget(user.getId(), user.getEmail(), user.getCredentialsVersion());
	}

	@Transactional
	public OidcUser complete(GoogleLinkIntentStore.Intent intent, OidcUser googleUser) {
		var subject = googleUser.getSubject();
		var googleEmail = googleUser.getEmail();
		if (subject == null || subject.isBlank() || googleEmail == null || googleEmail.isBlank()
				|| !Boolean.TRUE.equals(googleUser.getEmailVerified())) {
			throw oauthError("GOOGLE_PROFILE_INVALID", "Google không cung cấp email đã xác minh");
		}

		var user = users.findByIdForUpdate(intent.userId())
				.orElseThrow(() -> oauthError("GOOGLE_LINK_INTENT_INVALID", "Tài khoản liên kết không còn tồn tại"));
		if (user.getCredentialsVersion() != intent.credentialsVersion()
				|| !user.getEmail().equalsIgnoreCase(intent.email())) {
			throw oauthError("GOOGLE_LINK_INTENT_STALE", "Thông tin tài khoản đã thay đổi");
		}
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw oauthError("ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
		}
		if (user.getSystemRole() != SystemRole.USER || !PasswordPolicy.passwordLoginEnabled(user)) {
			throw oauthError("GOOGLE_LINK_NOT_ALLOWED", "Tài khoản không được phép liên kết Google");
		}
		if (!user.getEmail().equals(normalizeEmail(googleEmail))) {
			throw oauthError("GOOGLE_LINK_EMAIL_MISMATCH",
					"Hãy chọn đúng tài khoản Google có email trùng với SmartTask");
		}
		if (user.getGoogleSubject() != null) {
			throw oauthError("GOOGLE_ACCOUNT_ALREADY_LINKED", "Tài khoản đã liên kết Google");
		}
		var subjectOwner = users.findByGoogleSubject(subject);
		if (subjectOwner.isPresent() && !subjectOwner.get().getId().equals(user.getId())) {
			throw oauthError("GOOGLE_LINK_SUBJECT_CONFLICT", "Tài khoản Google đã được liên kết nơi khác");
		}

		try {
			user.setGoogleSubject(subject);
			user.setEmailVerified(true);
			user.revokeCredentials();
			accountTokens.deleteByUser_Id(user.getId());
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw oauthError("GOOGLE_LINK_SUBJECT_CONFLICT", "Tài khoản Google đã được liên kết nơi khác");
		}

		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name()));
		return new SmartTaskOidcUser(googleUser, user.getEmail(), authorities, user.getCredentialsVersion());
	}

	@Transactional
	public void unlink(String currentEmail, String currentPassword) {
		PasswordPolicy.requireSupportedLength(currentPassword);
		var user = users.findByEmailForUpdate(normalizeEmail(currentEmail))
				.orElseThrow(this::unauthorized);
		requireActivePasswordUser(user);
		if (!passwords.matches(currentPassword, user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID",
					"Mật khẩu hiện tại không đúng");
		}
		if (user.getGoogleSubject() == null) {
			throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_NOT_LINKED",
					"Tài khoản chưa liên kết Google");
		}
		user.setGoogleSubject(null);
		user.revokeCredentials();
		accountTokens.deleteByUser_Id(user.getId());
		users.saveAndFlush(user);
	}

	@Transactional(readOnly = true)
	public Optional<Authentication> restoreAuthentication(GoogleLinkIntentStore.Intent intent) {
		if (!intent.expiresAt().isAfter(Instant.now())) return Optional.empty();
		return users.findById(intent.userId())
				.filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
				.filter(user -> user.getSystemRole() == SystemRole.USER)
				.filter(PasswordPolicy::passwordLoginEnabled)
				.filter(user -> user.getCredentialsVersion() == intent.credentialsVersion())
				.filter(user -> user.getEmail().equalsIgnoreCase(intent.email()))
				.map(user -> {
					var details = new SmartTaskUserDetails(user);
					return UsernamePasswordAuthenticationToken.authenticated(
							details, null, details.getAuthorities());
				});
	}

	private void requireActivePasswordUser(com.example.smarttask.domain.User user) {
		if (user.getAccountStatus() != AccountStatus.ACTIVE) throw unauthorized();
		if (user.getSystemRole() != SystemRole.USER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GOOGLE_LINK_NOT_ALLOWED",
					"Chỉ tài khoản người dùng mới được thay đổi liên kết Google");
		}
		if (!PasswordPolicy.passwordLoginEnabled(user)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_LOGIN_DISABLED",
					"Tài khoản không có mật khẩu SmartTask để xác nhận");
		}
	}

	private String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Cần đăng nhập để tiếp tục");
	}

	private OAuth2AuthenticationException oauthError(String code, String message) {
		return new OAuth2AuthenticationException(new OAuth2Error(code), message);
	}

	public record LinkTarget(long userId, String email, long credentialsVersion) { }
}
