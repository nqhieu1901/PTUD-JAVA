package com.example.smarttask.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;

class SecurityConfigTests {

	@ParameterizedTest
	@ValueSource(strings = {"google", "google-link"})
	void discardsAuthorizedClientAfterGoogleAuthentication(String registrationId) {
		var authorizedClients = mock(OAuth2AuthorizedClientRepository.class);
		var authentication = new OAuth2AuthenticationToken(
				mock(OAuth2User.class), List.of(), registrationId);
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();

		SecurityConfig.discardAuthorizedClient(
				authorizedClients, authentication, request, response);

		verify(authorizedClients).removeAuthorizedClient(
				registrationId, authentication, request, response);
	}
}
