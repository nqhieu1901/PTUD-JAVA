package com.example.smarttask.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.example.smarttask.auth.GoogleLinkIntentStore;

class GoogleLoginFailureHandlerTests {

	@Test
	void sendsOnlyAnAllowlistedReasonToFrontend() throws Exception {
		var response = new MockHttpServletResponse();
		new GoogleLoginFailureHandler("http://localhost:5173/?authError=google")
				.onAuthenticationFailure(new MockHttpServletRequest(), response,
						new OAuth2AuthenticationException(new OAuth2Error("GOOGLE_ACCOUNT_LINK_REQUIRED")));
		assertThat(response.getRedirectedUrl())
				.isEqualTo("http://localhost:5173/?authError=google&reason=link_required");
	}

	@Test
	void neverIncludesProviderErrorDetailsInRedirect() throws Exception {
		var response = new MockHttpServletResponse();
		new GoogleLoginFailureHandler("http://localhost:5173/?authError=google&reason=old")
				.onAuthenticationFailure(new MockHttpServletRequest(), response,
						new OAuth2AuthenticationException(new OAuth2Error("unknown_provider_failure"),
								"sensitive provider response"));
		assertThat(response.getRedirectedUrl())
				.isEqualTo("http://localhost:5173/?authError=google&reason=failed");
	}

	@Test
	void mapsLinkFailuresWithoutLeakingProviderDetailsAndClearsTheIntent() throws Exception {
		var intents = new GoogleLinkIntentStore(300);
		var request = new MockHttpServletRequest();
		var session = new org.springframework.mock.web.MockHttpSession();
		request.setSession(session);
		intents.save(session, 1, "person@example.com", 0);
		var response = new MockHttpServletResponse();
		new GoogleLoginFailureHandler("http://localhost:5173/", intents)
				.onAuthenticationFailure(request, response,
						new OAuth2AuthenticationException(new OAuth2Error("GOOGLE_LINK_SUBJECT_CONFLICT"),
								"secret subject"));

		assertThat(response.getRedirectedUrl())
				.isEqualTo("http://localhost:5173/?authError=google&reason=link_conflict");
		assertThatThrownBy(() -> intents.consumeRequired(request))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						error -> assertThat(error.getError().getErrorCode())
							.isEqualTo("GOOGLE_LINK_INTENT_MISSING"));
	}
}
