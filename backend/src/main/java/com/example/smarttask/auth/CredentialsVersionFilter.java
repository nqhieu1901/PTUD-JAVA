package com.example.smarttask.auth;

import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.smarttask.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Registered only inside the security chain, after authentication is restored. */
public class CredentialsVersionFilter extends OncePerRequestFilter {
	private static final Set<String> PUBLIC_GETS = Set.of(
			"/api/auth/csrf", "/api/auth/providers");
	private static final Set<String> PUBLIC_POSTS = Set.of(
			"/api/auth/login", "/api/auth/register", "/api/auth/forgot-password",
			"/api/auth/reset-password", "/api/auth/email-verification/confirm");

	private final UserRepository users;

	public CredentialsVersionFilter(UserRepository users) {
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof VersionedIdentity identity) {
			var user = users.findByEmailIgnoreCase(authentication.getName());
			if (user.isEmpty() || user.get().getCredentialsVersion() != identity.credentialsVersion()) {
				if (isPublicAuthRequest(request)) {
					continueWithoutStaleIdentity(request, response, filterChain);
					return;
				}
				SecurityContextHolder.clearContext();
				var session = request.getSession(false);
				if (session != null) session.invalidate();
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setCharacterEncoding("UTF-8");
				response.setContentType("application/json");
				response.getWriter().write("{\"code\":\"SESSION_REVOKED\",\"message\":\"Phiên đăng nhập đã hết hiệu lực. Vui lòng đăng nhập lại\"}");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private boolean isPublicAuthRequest(HttpServletRequest request) {
		var path = request.getRequestURI().substring(request.getContextPath().length());
		return switch (request.getMethod()) {
			case "GET" -> PUBLIC_GETS.contains(path);
			case "POST" -> PUBLIC_POSTS.contains(path);
			default -> false;
		};
	}

	private void continueWithoutStaleIdentity(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
		SecurityContextHolder.clearContext();
		var staleSession = request.getSession(false);
		if (staleSession != null) {
			staleSession.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		}

		if ("GET".equals(request.getMethod())) {
			invalidate(staleSession);
			filterChain.doFilter(request, response);
			return;
		}

		try {
			// Preserve the old session just long enough for CsrfFilter to validate this POST.
			filterChain.doFilter(request, response);
		}
		finally {
			if (!hasFreshIdentity()) invalidate(staleSession);
		}
	}

	private boolean hasFreshIdentity() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof VersionedIdentity identity)) {
			return false;
		}
		return users.findByEmailIgnoreCase(authentication.getName())
				.map(user -> user.getCredentialsVersion() == identity.credentialsVersion())
				.orElse(false);
	}

	private void invalidate(jakarta.servlet.http.HttpSession session) {
		if (session == null) return;
		try {
			session.invalidate();
		}
		catch (IllegalStateException ignored) {
			// A downstream handler may already have invalidated the stale session.
		}
	}
}
