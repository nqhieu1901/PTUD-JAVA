package com.example.smarttask.auth;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Server-side, one-shot proof that a password-authenticated user requested a Google link. */
@Component
public final class GoogleLinkIntentStore {

	private static final String SESSION_ATTRIBUTE =
			GoogleLinkIntentStore.class.getName() + ".INTENT";
	private static final String SUCCESS_ATTRIBUTE =
			GoogleLinkIntentStore.class.getName() + ".SUCCESS";
	private static final String RESTORATION_ATTRIBUTE =
			GoogleLinkIntentStore.class.getName() + ".RESTORATION";

	private final long ttlSeconds;
	private final Clock clock;

	@Autowired
	public GoogleLinkIntentStore(
			@Value("${smarttask.oauth.google.link-intent-ttl-seconds:300}") long ttlSeconds) {
		this(ttlSeconds, Clock.systemUTC());
	}

	GoogleLinkIntentStore(long ttlSeconds, Clock clock) {
		if (ttlSeconds < 30 || ttlSeconds > 900) {
			throw new IllegalArgumentException("Google link intent TTL must be between 30 and 900 seconds");
		}
		this.ttlSeconds = ttlSeconds;
		this.clock = clock;
	}

	public void save(HttpSession session, long userId, String email, long credentialsVersion) {
		if (session == null) {
			throw new IllegalStateException("An authenticated session is required to link Google");
		}
		var intent = new Intent(
				userId,
				email,
				credentialsVersion,
				clock.instant().plusSeconds(ttlSeconds));
		session.setAttribute(SESSION_ATTRIBUTE, intent);
		// OAuth failure handling clears Spring Security's context before invoking its
		// handler. Keep only the minimum identity needed to validate and restore the
		// pre-existing local session; never store the submitted password.
		session.setAttribute(RESTORATION_ATTRIBUTE, intent);
	}

	/** Removes the intent before validating it, so every callback outcome is one-shot. */
	public Intent consumeRequired(HttpServletRequest request) {
		var session = request.getSession(false);
		if (session == null) throw oauthError("GOOGLE_LINK_INTENT_MISSING", "Yêu cầu liên kết không tồn tại");
		var value = session.getAttribute(SESSION_ATTRIBUTE);
		session.removeAttribute(SESSION_ATTRIBUTE);
		if (!(value instanceof Intent intent)) {
			throw oauthError("GOOGLE_LINK_INTENT_MISSING", "Yêu cầu liên kết không tồn tại");
		}
		if (!intent.expiresAt().isAfter(clock.instant())) {
			throw oauthError("GOOGLE_LINK_INTENT_EXPIRED", "Yêu cầu liên kết đã hết hạn");
		}
		return intent;
	}

	public void clear(HttpSession session) {
		if (session != null) {
			session.removeAttribute(SESSION_ATTRIBUTE);
			session.removeAttribute(RESTORATION_ATTRIBUTE);
		}
	}

	/** Removes every flow marker and returns the pre-OAuth identity, when present. */
	public Optional<Intent> takeRestoration(HttpSession session) {
		if (session == null) return Optional.empty();
		var value = session.getAttribute(RESTORATION_ATTRIBUTE);
		clear(session);
		return value instanceof Intent intent ? Optional.of(intent) : Optional.empty();
	}

	public boolean hasUsableIntent(HttpSession session, Authentication authentication) {
		if (session == null || authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken
				|| authentication.getAuthorities().stream().noneMatch(a -> "ROLE_USER".equals(a.getAuthority()))) {
			return false;
		}
		var value = session.getAttribute(SESSION_ATTRIBUTE);
		if (!(value instanceof Intent intent) || !intent.expiresAt().isAfter(clock.instant())) return false;
		if (!(authentication.getPrincipal() instanceof VersionedIdentity identity)) return false;
		return authentication.getName().equalsIgnoreCase(intent.email())
				&& identity.credentialsVersion() == intent.credentialsVersion();
	}

	public void markSuccessful(HttpServletRequest request) {
		request.setAttribute(SUCCESS_ATTRIBUTE, Boolean.TRUE);
	}

	public boolean wasSuccessful(HttpServletRequest request) {
		return Boolean.TRUE.equals(request.getAttribute(SUCCESS_ATTRIBUTE));
	}

	private OAuth2AuthenticationException oauthError(String code, String message) {
		return new OAuth2AuthenticationException(new OAuth2Error(code), message);
	}

	public record Intent(
			long userId,
			String email,
			long credentialsVersion,
			Instant expiresAt) implements Serializable {
		private static final long serialVersionUID = 1L;
	}
}
