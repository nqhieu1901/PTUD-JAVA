package com.example.smarttask.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
	private final GoogleAccountService googleAccountService;
	private final GoogleAccountLinkService googleAccountLinkService;
	private final GoogleLinkIntentStore linkIntents;
	private final ObjectProvider<HttpServletRequest> currentRequest;

	@Autowired
	public GoogleOidcUserService(GoogleAccountService googleAccountService,
			GoogleAccountLinkService googleAccountLinkService,
			GoogleLinkIntentStore linkIntents,
			ObjectProvider<HttpServletRequest> currentRequest) {
		this(new OidcUserService(), googleAccountService, googleAccountLinkService,
				linkIntents, currentRequest);
	}

	GoogleOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate,
			GoogleAccountService googleAccountService,
			GoogleAccountLinkService googleAccountLinkService,
			GoogleLinkIntentStore linkIntents,
			ObjectProvider<HttpServletRequest> currentRequest) {
		this.delegate = delegate;
		this.googleAccountService = googleAccountService;
		this.googleAccountLinkService = googleAccountLinkService;
		this.linkIntents = linkIntents;
		this.currentRequest = currentRequest;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) {
		if (!"google-link".equals(userRequest.getClientRegistration().getRegistrationId())) {
			return googleAccountService.authenticate(delegate.loadUser(userRequest));
		}
		var request = currentRequest.getIfAvailable();
		if (request == null) {
			throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
					new org.springframework.security.oauth2.core.OAuth2Error("GOOGLE_LINK_INTENT_MISSING"));
		}
		// Consume before contacting/parsing the provider; cancellation and every error are one-shot.
		var intent = linkIntents.consumeRequired(request);
		var principal = googleAccountLinkService.complete(intent, delegate.loadUser(userRequest));
		linkIntents.markSuccessful(request);
		return principal;
	}
}
