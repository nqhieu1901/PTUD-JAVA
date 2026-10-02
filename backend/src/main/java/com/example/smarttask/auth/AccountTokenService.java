package com.example.smarttask.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class AccountTokenService {

	private final UserRepository users;
	private final AccountTokenRepository tokens;
	private final AccountMailDelivery delivery;
	private final PasswordEncoder passwords;
	private final String frontendUrl;
	private final long resetMinutes;
	private final long verificationMinutes;
	private final long resendCooldownSeconds;
	private final SecureRandom random = new SecureRandom();

	public AccountTokenService(UserRepository users, AccountTokenRepository tokens,
			AccountMailDelivery delivery, PasswordEncoder passwords,
			@Value("${smarttask.mail.frontend-url:http://localhost:5173/}") String frontendUrl,
			@Value("${smarttask.mail.reset-token-ttl-minutes:30}") long resetMinutes,
			@Value("${smarttask.mail.verification-token-ttl-minutes:1440}") long verificationMinutes,
			@Value("${smarttask.mail.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
		this.users = users;
		this.tokens = tokens;
		this.delivery = delivery;
		this.passwords = passwords;
		var uri = URI.create(frontendUrl.strip()).normalize();
		var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		var localHttp = "http".equals(scheme)
				&& ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
		if (!("https".equals(scheme) || localHttp)
				|| uri.getHost() == null || uri.getRawFragment() != null || uri.getRawQuery() != null
				|| uri.getUserInfo() != null || resetMinutes < 1 || verificationMinutes < 1
				|| resendCooldownSeconds < 1 || resendCooldownSeconds > 86_400) {
			throw new IllegalArgumentException("Invalid account email configuration");
		}
		var normalizedUrl = uri.toString();
		this.frontendUrl = normalizedUrl.endsWith("/") ? normalizedUrl : normalizedUrl + "/";
		this.resetMinutes = resetMinutes;
		this.verificationMinutes = verificationMinutes;
		this.resendCooldownSeconds = resendCooldownSeconds;
	}

	@Transactional
	public void requestPasswordReset(String email) {
		// Check configuration before looking up the account: no enumeration via 202/503.
		delivery.requireAvailable();
		var candidate = newToken();
		users.findByEmailForUpdate(normalizeEmail(email)).filter(this::canReset)
				.ifPresent(user -> issue(user, AccountTokenPurpose.PASSWORD_RESET, candidate, true));
	}

	@Transactional
	public void requestVerification(String email) {
		var user = users.findByEmailForUpdate(normalizeEmail(email)).orElseThrow(this::invalidToken);
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
		}
		if (user.isEmailVerified()) return;
		delivery.requireAvailable();
		issue(user, AccountTokenPurpose.EMAIL_VERIFICATION, newToken(), true);
	}

	void requestVerificationAfterRegistration(User user) {
		if (!delivery.isAvailable()) return;
		issue(user, AccountTokenPurpose.EMAIL_VERIFICATION, newToken(), false);
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		PasswordPolicy.requireSupportedLength(newPassword);
		var token = requireToken(rawToken, AccountTokenPurpose.PASSWORD_RESET);
		var user = token.getUser();
		if (!canReset(user)) throw invalidToken();
		user.setPasswordHash(passwords.encode(newPassword));
		user.revokeCredentials();
		token.consume(now());
		try {
			delivery.sendAfterCommitIfAvailable(user.getEmail(), "SmartTask - Mật khẩu đã thay đổi",
					"Mật khẩu SmartTask của bạn vừa được đặt lại."
					+ "\nNếu bạn không thực hiện thao tác này, hãy liên hệ quản trị viên ngay.");
		}
		catch (RuntimeException ignored) {
			// Password and session revocation must commit even if notification setup fails.
		}
	}

	@Transactional
	public void confirmVerification(String rawToken) {
		var token = requireToken(rawToken, AccountTokenPurpose.EMAIL_VERIFICATION);
		if (token.getUser().getAccountStatus() != AccountStatus.ACTIVE) throw invalidToken();
		token.getUser().setEmailVerified(true);
		token.consume(now());
	}

	private void issue(User user, AccountTokenPurpose purpose, TokenCandidate candidate,
			boolean deliveryRequired) {
		var now = now();
		var token = tokens.findByUser_IdAndPurpose(user.getId(), purpose)
				.orElseGet(() -> new AccountToken(user, purpose));
		// Rotation remains bounded to two rows/user and stops rapid email resends.
		if (token.getIssuedAt() != null
				&& token.getIssuedAt().plusSeconds(resendCooldownSeconds).isAfter(now)) return;
		var reset = purpose == AccountTokenPurpose.PASSWORD_RESET;
		var minutes = reset ? resetMinutes : verificationMinutes;
		token.rotate(candidate.hash(), now, minutes);
		tokens.saveAndFlush(token);
		var link = frontendUrl + (reset ? "#reset-password=" : "#verify-email=") + candidate.raw();
		var subject = reset ? "SmartTask - Đặt lại mật khẩu" : "SmartTask - Xác minh email";
		var body =
				(reset ? "Mở liên kết để đặt lại mật khẩu SmartTask:\n" : "Mở liên kết để xác minh email SmartTask:\n")
				+ link + "\n\nLiên kết có hiệu lực " + minutes + " phút và chỉ dùng một lần."
				+ "\nNếu bạn không yêu cầu thao tác này, hãy bỏ qua email.";
		if (deliveryRequired) delivery.sendAfterCommit(user.getEmail(), subject, body);
		else delivery.sendAfterCommitIfAvailable(user.getEmail(), subject, body);
	}

	private TokenCandidate newToken() {
		var bytes = new byte[32];
		random.nextBytes(bytes);
		var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new TokenCandidate(raw, hash(raw));
	}

	private AccountToken requireToken(String raw, AccountTokenPurpose purpose) {
		if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
		var hash = hash(raw);
		var userId = tokens.findUserId(hash, purpose).orElseThrow(this::invalidToken);
		users.findByIdForUpdate(userId).orElseThrow(this::invalidToken);
		// Re-read after locking: another consume/resend may have happened meanwhile.
		var token = tokens.findByTokenHashAndPurpose(hash, purpose).orElseThrow(this::invalidToken);
		if (!token.canUse(now())) throw invalidToken();
		return token;
	}

	private boolean canReset(User user) {
		return user.getAccountStatus() == AccountStatus.ACTIVE && PasswordPolicy.passwordLoginEnabled(user);
	}

	private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }

	private String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private String hash(String raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(raw.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
	}

	private ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_TOKEN_INVALID",
				"Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu liên kết mới");
	}

	private record TokenCandidate(String raw, String hash) { }
}
