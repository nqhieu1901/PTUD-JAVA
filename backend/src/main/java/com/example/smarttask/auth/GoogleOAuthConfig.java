package com.example.smarttask.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "smarttask.oauth.google", name = "enabled", havingValue = "true")
public class GoogleOAuthConfig {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(
			@Value("${smarttask.oauth.google.client-id:}") String clientId,
			@Value("${smarttask.oauth.google.client-secret:}") String clientSecret,
			@Value("${smarttask.oauth.google.redirect-uri:http://localhost:8080/login/oauth2/code/google}") String redirectUri) {
		if (clientId.isBlank() || clientSecret.isBlank()) {
			throw new IllegalStateException(
					"GOOGLE_CLIENT_ID và GOOGLE_CLIENT_SECRET là bắt buộc khi bật đăng nhập Google");
		}

		var google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.redirectUri(redirectUri)
				.scope("openid", "profile", "email")
				.clientName("Google")
				.build();
		// A distinct registration id binds account-link callbacks to the explicit
		// password-confirmed flow, while retaining the one redirect URI registered at Google.
		var googleLink = CommonOAuth2Provider.GOOGLE.getBuilder("google-link")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.redirectUri(redirectUri)
				.scope("openid", "profile", "email")
				.clientName("Google account link")
				.build();
		return new InMemoryClientRegistrationRepository(google, googleLink);
	}
}
