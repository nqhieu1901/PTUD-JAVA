package com.example.smarttask.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

@Service
public class GoogleAccountService {

	private final UserRepository userRepository;
	private final AuthService authService;

	public GoogleAccountService(
			UserRepository userRepository,
			AuthService authService) {
		this.userRepository = userRepository;
		this.authService = authService;
	}

	@Transactional
	public OidcUser authenticate(OidcUser googleUser) {
		var subject = googleUser.getSubject();
		var email = googleUser.getEmail();
		if (subject == null || subject.isBlank() || email == null || email.isBlank()
				|| !Boolean.TRUE.equals(googleUser.getEmailVerified())) {
			throw oauthError("GOOGLE_PROFILE_INVALID", "Google không cung cấp email đã xác minh");
		}

		var normalizedEmail = authService.normalizeEmail(email);
		var user = userRepository.findByGoogleSubject(subject)
				.orElseGet(() -> findOrCreateUser(googleUser, subject, normalizedEmail));
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw oauthError("ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
		}
		// A subject may retain access after the provider changes its email; only verify
		// the local address when it is the address Google actually verified.
		if (user.getEmail().equalsIgnoreCase(normalizedEmail)) {
			user.setEmailVerified(true);
		}

		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name()));
		return new SmartTaskOidcUser(googleUser, user.getEmail(), authorities, user.getCredentialsVersion());
	}

	private User findOrCreateUser(OidcUser googleUser, String subject, String email) {
		var existing = userRepository.findByEmailIgnoreCase(email);
		if (existing.isPresent()) {
			var user = existing.get();
			if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(subject)) {
				throw oauthError("GOOGLE_ACCOUNT_CONFLICT", "Email đã liên kết với tài khoản Google khác");
			}
			throw oauthError(
					"GOOGLE_ACCOUNT_LINK_REQUIRED",
					"Email đã có tài khoản SmartTask. Hãy đăng nhập bằng mật khẩu của tài khoản đó");
		}

		var user = new User(
				email,
				PasswordPolicy.GOOGLE_ONLY_PASSWORD_HASH,
				normalizeName(googleUser.getFullName(), email));
		user.setGoogleSubject(subject);
		return userRepository.saveAndFlush(user);
	}

	private String normalizeName(String fullName, String email) {
		var value = fullName == null ? "" : fullName.strip();
		if (value.isBlank()) value = email.substring(0, email.indexOf('@'));
		return value.length() <= 150 ? value : value.substring(0, 150);
	}

	private OAuth2AuthenticationException oauthError(String code, String message) {
		return new OAuth2AuthenticationException(new OAuth2Error(code), message);
	}
}
