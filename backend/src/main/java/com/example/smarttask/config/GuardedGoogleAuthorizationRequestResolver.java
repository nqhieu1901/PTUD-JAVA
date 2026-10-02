package com.example.smarttask.config;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import com.example.smarttask.auth.GoogleLinkIntentStore;

import jakarta.servlet.http.HttpServletRequest;

/** Prevents the dedicated account-link registration from becoming a public OAuth login entrypoint. */
final class GuardedGoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	private final OAuth2AuthorizationRequestResolver delegate;
	private final GoogleLinkIntentStore intents;

	GuardedGoogleAuthorizationRequestResolver(ClientRegistrationRepository registrations,
			GoogleLinkIntentStore intents) {
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
				registrations, "/oauth2/authorization");
		this.intents = intents;
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return guard(request, delegate.resolve(request));
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return guard(request, delegate.resolve(request, clientRegistrationId));
	}

	private OAuth2AuthorizationRequest guard(HttpServletRequest request,
			OAuth2AuthorizationRequest authorizationRequest) {
		if (authorizationRequest == null) return null;
		var registrationId = authorizationRequest.<String>getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
		if (!"google-link".equals(registrationId)) return authorizationRequest;
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		return intents.hasUsableIntent(request.getSession(false), authentication)
				? authorizationRequest
				: null;
	}
}
