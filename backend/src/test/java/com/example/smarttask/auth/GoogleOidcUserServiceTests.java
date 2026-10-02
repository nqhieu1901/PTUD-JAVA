package com.example.smarttask.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import jakarta.servlet.http.HttpServletRequest;

class GoogleOidcUserServiceTests {

	@Test
	void linkFlowConsumesIntentBeforeProviderCallThenMarksSuccess() {
		var fixture = fixture("google-link");
		var intent = new GoogleLinkIntentStore.Intent(1, "person@example.com", 0,
				Instant.now().plusSeconds(60));
		var google = mock(OidcUser.class);
		var linked = mock(OidcUser.class);
		when(fixture.intents.consumeRequired(fixture.request)).thenReturn(intent);
		when(fixture.delegate.loadUser(fixture.userRequest)).thenReturn(google);
		when(fixture.links.complete(intent, google)).thenReturn(linked);

		fixture.service.loadUser(fixture.userRequest);

		var ordered = inOrder(fixture.intents, fixture.delegate, fixture.links);
		ordered.verify(fixture.intents).consumeRequired(fixture.request);
		ordered.verify(fixture.delegate).loadUser(fixture.userRequest);
		ordered.verify(fixture.links).complete(intent, google);
		verify(fixture.intents).markSuccessful(fixture.request);
		verify(fixture.normal, never()).authenticate(google);
	}

	@Test
	void providerFailureStillLeavesTheOneShotIntentConsumed() {
		var fixture = fixture("google-link");
		var intent = new GoogleLinkIntentStore.Intent(1, "person@example.com", 0,
				Instant.now().plusSeconds(60));
		when(fixture.intents.consumeRequired(fixture.request)).thenReturn(intent);
		when(fixture.delegate.loadUser(fixture.userRequest)).thenThrow(new IllegalStateException("provider down"));

		assertThatThrownBy(() -> fixture.service.loadUser(fixture.userRequest))
				.isInstanceOf(IllegalStateException.class);
		var ordered = inOrder(fixture.intents, fixture.delegate);
		ordered.verify(fixture.intents).consumeRequired(fixture.request);
		ordered.verify(fixture.delegate).loadUser(fixture.userRequest);
		verify(fixture.intents, never()).markSuccessful(fixture.request);
		verify(fixture.links, never()).complete(intent, null);
	}

	@Test
	void normalGoogleLoginNeverReadsOrConsumesALinkIntent() {
		var fixture = fixture("google");
		var google = mock(OidcUser.class);
		var normalPrincipal = mock(OidcUser.class);
		when(fixture.delegate.loadUser(fixture.userRequest)).thenReturn(google);
		when(fixture.normal.authenticate(google)).thenReturn(normalPrincipal);

		fixture.service.loadUser(fixture.userRequest);

		verify(fixture.intents, never()).consumeRequired(fixture.request);
		verify(fixture.links, never()).complete(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
		verify(fixture.normal).authenticate(google);
	}

	@SuppressWarnings("unchecked")
	private Fixture fixture(String registrationId) {
		var delegate = (OAuth2UserService<OidcUserRequest, OidcUser>) mock(OAuth2UserService.class);
		var normal = mock(GoogleAccountService.class);
		var links = mock(GoogleAccountLinkService.class);
		var intents = mock(GoogleLinkIntentStore.class);
		var provider = (ObjectProvider<HttpServletRequest>) mock(ObjectProvider.class);
		var request = new MockHttpServletRequest();
		request.setSession(new MockHttpSession());
		when(provider.getIfAvailable()).thenReturn(request);
		var userRequest = mock(OidcUserRequest.class);
		var registration = mock(ClientRegistration.class);
		when(registration.getRegistrationId()).thenReturn(registrationId);
		when(userRequest.getClientRegistration()).thenReturn(registration);
		return new Fixture(new GoogleOidcUserService(delegate, normal, links, intents, provider),
				delegate, normal, links, intents, request, userRequest);
	}

	private record Fixture(GoogleOidcUserService service,
			OAuth2UserService<OidcUserRequest, OidcUser> delegate,
			GoogleAccountService normal,
			GoogleAccountLinkService links,
			GoogleLinkIntentStore intents,
			MockHttpServletRequest request,
			OidcUserRequest userRequest) { }
}
