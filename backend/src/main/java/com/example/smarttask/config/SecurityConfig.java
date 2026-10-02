package com.example.smarttask.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.core.Authentication;

import com.example.smarttask.auth.GoogleOidcUserService;
import com.example.smarttask.auth.GoogleLinkIntentStore;
import com.example.smarttask.auth.GoogleAccountLinkService;
import com.example.smarttask.auth.CredentialsVersionFilter;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.security.RequestRateLimitFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository,
			ObjectProvider<ClientRegistrationRepository> clientRegistrations,
			GoogleOidcUserService googleOidcUserService,
			GoogleLinkIntentStore googleLinkIntents,
			GoogleAccountLinkService googleAccountLinks,
			UserRepository userRepository,
			@Value("${smarttask.rate-limit.enabled:true}") boolean rateLimitEnabled,
			@Value("${smarttask.rate-limit.auth-per-minute:10}") int authRateLimit,
			@Value("${smarttask.rate-limit.email-per-15-minutes:5}") int emailRateLimit,
			@Value("${smarttask.rate-limit.reauth-per-15-minutes:5}") int reauthRateLimit,
			@Value("${smarttask.rate-limit.ai-per-minute:5}") int aiRateLimit,
			@Value("${smarttask.rate-limit.ai-concurrent:2}") int aiConcurrentLimit,
			@Value("${smarttask.oauth.google.success-url:http://localhost:5173/}") String googleSuccessUrl,
			@Value("${smarttask.oauth.google.failure-url:http://localhost:5173/?authError=google}") String googleFailureUrl) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/providers").permitAll()
						.requestMatchers(HttpMethod.GET, "/oauth2/authorization/google-link").hasRole("USER")
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/forgot-password", "/api/auth/reset-password",
								"/api/auth/email-verification/confirm").permitAll()
						.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/users/me/google-link",
								"/api/users/me/google-unlink").hasRole("USER")
						.requestMatchers("/api/projects/**", "/api/notifications/**").hasRole("USER")
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
				.securityContext(context -> context
						.securityContextRepository(securityContextRepository)
						.requireExplicitSave(true))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessHandler(
								new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								writeSecurityError(response, HttpStatus.UNAUTHORIZED,
										"UNAUTHORIZED", "Cần đăng nhập để tiếp tục"))
						.accessDeniedHandler((request, response, exception) ->
								writeSecurityError(response, HttpStatus.FORBIDDEN,
										"ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này")));

		http.addFilterAfter(new CredentialsVersionFilter(userRepository), SecurityContextHolderFilter.class);
		if (rateLimitEnabled) {
			http.addFilterAfter(new RequestRateLimitFilter(authRateLimit, emailRateLimit, reauthRateLimit,
					aiRateLimit, aiConcurrentLimit), AnonymousAuthenticationFilter.class);
		}
		var clientRegistrationRepository = clientRegistrations.getIfAvailable();
		if (clientRegistrationRepository != null) {
			var authorizedClients = new HttpSessionOAuth2AuthorizedClientRepository();
			http.oauth2Login(oauth -> oauth
					.clientRegistrationRepository(clientRegistrationRepository)
					.authorizedClientRepository(authorizedClients)
					.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
							new GuardedGoogleAuthorizationRequestResolver(
									clientRegistrationRepository, googleLinkIntents)))
					.userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
					.successHandler((request, response, authentication) -> {
						discardAuthorizedClient(authorizedClients, authentication, request, response);
						var linked = googleLinkIntents.wasSuccessful(request);
						googleLinkIntents.clear(request.getSession(false));
						csrfTokenRepository.saveToken(null, request, response);
						var destination = linked
								? org.springframework.web.util.UriComponentsBuilder.fromUriString(googleSuccessUrl)
										.replaceQueryParam("googleLinked", "true").build().toUriString()
								: googleSuccessUrl;
						response.sendRedirect(destination);
					})
					.failureHandler(new GoogleLoginFailureHandler(googleFailureUrl, googleLinkIntents,
							googleAccountLinks, securityContextRepository)));
		}

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
	}

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new ChangeSessionIdAuthenticationStrategy();
	}

	static void discardAuthorizedClient(
			OAuth2AuthorizedClientRepository authorizedClients,
			Authentication authentication,
			HttpServletRequest request,
			HttpServletResponse response) {
		if (authentication instanceof OAuth2AuthenticationToken oauthAuthentication) {
			authorizedClients.removeAuthorizedClient(
					oauthAuthentication.getAuthorizedClientRegistrationId(),
					authentication, request, response);
		}
	}

	private static void writeSecurityError(
			HttpServletResponse response,
			HttpStatus status,
			String code,
			String message) throws IOException {
		response.setStatus(status.value());
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
