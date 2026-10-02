package com.example.smarttask.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Added only to Spring Security's filter chain, after authentication and CSRF checks. */
public final class RequestRateLimitFilter extends OncePerRequestFilter {

	private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register");
	private static final Set<String> EMAIL_PATHS = Set.of(
			"/api/auth/forgot-password", "/api/auth/email-verification/request");
	private static final Set<String> TOKEN_PATHS = Set.of(
			"/api/auth/reset-password", "/api/auth/email-verification/confirm");
	private static final Set<String> REAUTH_POST_PATHS = Set.of(
			"/api/users/me/google-link", "/api/users/me/google-unlink");
	private static final String PASSWORD_PATH = "/api/users/me/password";
	private static final Pattern AI_PATH = Pattern.compile("/api/projects/[0-9]+/tasks/[0-9]+/ai/decompositions");
	private final SlidingWindowLimiter limiter;
	private final int authLimit;
	private final int emailLimit;
	private final int reauthLimit;
	private final int aiLimit;
	private final int aiConcurrentLimit;

	public RequestRateLimitFilter(int authLimit, int emailLimit, int aiLimit, int aiConcurrentLimit) {
		this(authLimit, emailLimit, emailLimit, aiLimit, aiConcurrentLimit);
	}

	public RequestRateLimitFilter(int authLimit, int emailLimit, int reauthLimit,
			int aiLimit, int aiConcurrentLimit) {
		this(new SlidingWindowLimiter(Clock.systemUTC(), 10_000),
				authLimit, emailLimit, reauthLimit, aiLimit, aiConcurrentLimit);
	}

	RequestRateLimitFilter(SlidingWindowLimiter limiter, int authLimit, int emailLimit,
			int aiLimit, int aiConcurrentLimit) {
		this(limiter, authLimit, emailLimit, emailLimit, aiLimit, aiConcurrentLimit);
	}

	RequestRateLimitFilter(SlidingWindowLimiter limiter, int authLimit, int emailLimit,
			int reauthLimit, int aiLimit, int aiConcurrentLimit) {
		if (authLimit < 1 || emailLimit < 1 || reauthLimit < 1 || aiLimit < 1 || aiConcurrentLimit < 1
				|| authLimit > 1000 || emailLimit > 1000 || reauthLimit > 1000
				|| aiLimit > 1000 || aiConcurrentLimit > 100) {
			throw new IllegalArgumentException("Rate limits are outside supported bounds");
		}
		this.limiter = limiter;
		this.authLimit = authLimit;
		this.emailLimit = emailLimit;
		this.reauthLimit = reauthLimit;
		this.aiLimit = aiLimit;
		this.aiConcurrentLimit = aiConcurrentLimit;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		var path = request.getServletPath();
		if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
		var post = "POST".equals(request.getMethod());
		var passwordChange = "PUT".equals(request.getMethod()) && PASSWORD_PATH.equals(path);
		if (!post && !passwordChange) {
			chain.doFilter(request, response);
			return;
		}
		// Never trust a client-supplied X-Forwarded-For header as a rate-limit identity.
		var address = request.getRemoteAddr();
		long retryAfter = 0;
		String aiIdentity = null;
		if (post && AUTH_PATHS.contains(path)) {
			retryAfter = limiter.acquire("auth:" + address, authLimit, Duration.ofMinutes(1));
		}
		else if (post && EMAIL_PATHS.contains(path)) {
			retryAfter = limiter.acquire("email:" + address, emailLimit, Duration.ofMinutes(15));
		}
		else if (post && TOKEN_PATHS.contains(path)) {
			retryAfter = limiter.acquire("token:" + address, authLimit, Duration.ofMinutes(1));
		}
		else if ((post && REAUTH_POST_PATHS.contains(path)) || passwordChange) {
			var authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.isAuthenticated()
					&& !(authentication instanceof AnonymousAuthenticationToken)) {
				retryAfter = limiter.acquire("reauth:" + authentication.getName(),
						reauthLimit, Duration.ofMinutes(15));
			}
		}
		else if (post && AI_PATH.matcher(path).matches()) {
			var authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.isAuthenticated()
					&& !(authentication instanceof AnonymousAuthenticationToken)) {
				aiIdentity = "ai:" + authentication.getName();
				retryAfter = limiter.acquire(aiIdentity, aiLimit, Duration.ofMinutes(1));
			}
		}
		if (retryAfter > 0) {
			tooManyRequests(response, retryAfter);
			return;
		}
		if (aiIdentity != null && !limiter.enter(aiIdentity, aiConcurrentLimit)) {
			tooManyRequests(response, 1);
			return;
		}
		try {
			chain.doFilter(request, response);
		}
		finally {
			if (aiIdentity != null) limiter.leave(aiIdentity);
		}
	}

	private static void tooManyRequests(HttpServletResponse response, long retryAfter) throws IOException {
		response.setStatus(429);
		response.setHeader("Retry-After", Long.toString(retryAfter));
		response.setHeader("Cache-Control", "no-store");
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Bạn thao tác quá nhanh. Vui lòng thử lại sau "
				+ retryAfter + " giây.\"}");
	}
}
