package com.example.smarttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.example.smarttask.domain.User;
import com.example.smarttask.domain.SystemRole;

class GoogleLinkIntentStoreTests {

	private static final Instant NOW = Instant.parse("2026-09-07T03:00:00Z");

	@Test
	void validIntentIsBoundToIdentityAndConsumedExactlyOnce() {
		var store = storeAt(NOW);
		var session = new MockHttpSession();
		var request = new MockHttpServletRequest();
		request.setSession(session);
		var principal = principal("person@example.com", 4);
		store.save(session, 42, "person@example.com", 4);

		assertThat(store.hasUsableIntent(session, principal)).isTrue();
		assertThat(store.consumeRequired(request).userId()).isEqualTo(42);
		assertThatThrownBy(() -> store.consumeRequired(request))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						error -> assertThat(error.getError().getErrorCode())
							.isEqualTo("GOOGLE_LINK_INTENT_MISSING"));
	}

	@Test
	void expiredIntentIsRemovedBeforeTheErrorAndCannotBeReplayed() {
		var session = new MockHttpSession();
		var request = new MockHttpServletRequest();
		request.setSession(session);
		storeAt(NOW).save(session, 7, "expired@example.com", 0);
		var later = storeAt(NOW.plusSeconds(31));

		assertThatThrownBy(() -> later.consumeRequired(request))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						error -> assertThat(error.getError().getErrorCode())
							.isEqualTo("GOOGLE_LINK_INTENT_EXPIRED"));
		assertThatThrownBy(() -> later.consumeRequired(request))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						error -> assertThat(error.getError().getErrorCode())
							.isEqualTo("GOOGLE_LINK_INTENT_MISSING"));
	}

	@Test
	void guardRejectsWrongUserVersionOrRoleWithoutConsumingTheIntent() {
		var store = storeAt(NOW);
		var session = new MockHttpSession();
		store.save(session, 9, "right@example.com", 2);

		assertThat(store.hasUsableIntent(session, principal("wrong@example.com", 2))).isFalse();
		assertThat(store.hasUsableIntent(session, principal("right@example.com", 3))).isFalse();
		assertThat(store.hasUsableIntent(session, principal("right@example.com", 2, SystemRole.ADMIN))).isFalse();
		assertThat(store.hasUsableIntent(session, principal("right@example.com", 2))).isTrue();
	}

	private GoogleLinkIntentStore storeAt(Instant instant) {
		return new GoogleLinkIntentStore(30, Clock.fixed(instant, ZoneOffset.UTC));
	}

	private UsernamePasswordAuthenticationToken principal(String email, long version) {
		return principal(email, version, SystemRole.USER);
	}

	private UsernamePasswordAuthenticationToken principal(String email, long version, SystemRole role) {
		var user = new User(email, "{noop}password", "Person");
		user.setSystemRole(role);
		for (long i = 0; i < version; i++) user.revokeCredentials();
		var details = new SmartTaskUserDetails(user);
		return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
	}
}
