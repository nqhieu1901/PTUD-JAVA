package com.example.smarttask.auth;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final CsrfTokenRepository csrfTokenRepository;
	private final boolean googleLoginEnabled;

	public AuthController(
			AuthService authService,
			AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			CsrfTokenRepository csrfTokenRepository,
			@Value("${smarttask.oauth.google.enabled:false}") boolean googleLoginEnabled) {
		this.authService = authService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.csrfTokenRepository = csrfTokenRepository;
		this.googleLoginEnabled = googleLoginEnabled;
	}

	@GetMapping("/providers")
	public AuthProvidersResponse providers() {
		return new AuthProvidersResponse(googleLoginEnabled);
	}

	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken csrfToken) {
		return CsrfResponse.from(csrfToken);
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthUserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public AuthUserResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		PasswordPolicy.requireSupportedLength(request.password());
		try {
			var authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(
					authService.normalizeEmail(request.email()),
					request.password());
			var authentication = authenticationManager.authenticate(authenticationRequest);

			sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
			var securityContext = SecurityContextHolder.createEmptyContext();
			securityContext.setAuthentication(authentication);
			SecurityContextHolder.setContext(securityContext);
			securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

			csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
			return authService.findByEmail(authentication.getName());
		}
		catch (AuthenticationException exception) {
			SecurityContextHolder.clearContext();
			securityContextRepository.saveContext(
					SecurityContextHolder.createEmptyContext(),
					httpRequest,
					httpResponse);
			throw new InvalidCredentialsException();
		}
	}

	@GetMapping("/me")
	public AuthUserResponse me(Authentication authentication) {
		return authService.findByEmail(authentication.getName());
	}
}
