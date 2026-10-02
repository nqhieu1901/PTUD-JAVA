package com.example.smarttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.ServletException;

class RequestRateLimitFilterTests {

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void loginAndRegisterShareAnIpLimitAndIgnoreForgedForwardedHeaders() throws Exception {
		var filter = filter(2, 5, 5, 2);
		var called = new AtomicInteger();
		for (int attempt = 0; attempt < 3; attempt++) {
			var request = request(attempt == 1 ? "/api/auth/register" : "/api/auth/login");
			request.addHeader("X-Forwarded-For", "198.51.100." + attempt);
			var response = new MockHttpServletResponse();
			filter.doFilter(request, response, (req, res) -> called.incrementAndGet());
			if (attempt == 2) {
				assertThat(response.getStatus()).isEqualTo(429);
				assertThat(Long.parseLong(response.getHeader("Retry-After"))).isBetween(1L, 60L);
				assertThat(response.getContentAsString()).contains("RATE_LIMITED");
				assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
			}
		}
		assertThat(called).hasValue(2);
	}

	@Test
	void emailAndTokenLimitsDoNotPreventReadingTheSiteOrLoggingOut() throws Exception {
		var filter = filter(1, 1, 5, 2);
		var first = new MockHttpServletResponse();
		filter.doFilter(request("/api/auth/forgot-password"), first, (req, res) -> {});
		var limited = new MockHttpServletResponse();
		filter.doFilter(request("/api/auth/email-verification/request"), limited, (req, res) -> {});
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, 900L);
		for (String path : List.of("/api/auth/csrf", "/api/projects", "/api/auth/logout")) {
			var request = request(path);
			if (!path.endsWith("logout")) request.setMethod("GET");
			var called = new AtomicInteger();
			filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.incrementAndGet());
			assertThat(called).hasValue(1);
		}
	}

	@Test
	void aiLimitFollowsTheAccountAcrossProjectsButNotOtherUsers() throws Exception {
		var filter = filter(10, 5, 1, 2);
		signIn("owner");
		filter.doFilter(request("/api/projects/1/tasks/2/ai/decompositions"),
				new MockHttpServletResponse(), (req, res) -> {});
		var response = new MockHttpServletResponse();
		filter.doFilter(request("/api/projects/3/tasks/4/ai/decompositions"), response, (req, res) -> {});
		assertThat(response.getStatus()).isEqualTo(429);
		signIn("other-owner");
		var other = new MockHttpServletResponse();
		filter.doFilter(request("/api/projects/3/tasks/4/ai/decompositions"), other, (req, res) -> {});
		assertThat(other.getStatus()).isEqualTo(200);
	}

	@Test
	void concurrentAiCallsAreRejectedAndAnExceptionStillReleasesTheSlot() throws Exception {
		var filter = filter(10, 5, 10, 1);
		signIn("owner");
		assertThatThrownBy(() -> filter.doFilter(request("/api/projects/1/tasks/2/ai/decompositions"),
				new MockHttpServletResponse(), (req, res) -> {
					var overlapping = new MockHttpServletResponse();
					filter.doFilter(request("/api/projects/1/tasks/2/ai/decompositions"), overlapping,
							(innerReq, innerRes) -> { throw new AssertionError("Must not call provider"); });
					assertThat(overlapping.getStatus()).isEqualTo(429);
					throw new ServletException("simulated provider failure");
				})).isInstanceOf(ServletException.class);
		var recovered = new MockHttpServletResponse();
		filter.doFilter(request("/api/projects/1/tasks/2/ai/decompositions"), recovered, (req, res) -> {});
		assertThat(recovered.getStatus()).isEqualTo(200);
	}

	@Test
	void passwordAndGoogleChangesShareAReauthenticationLimitPerAccount() throws Exception {
		var filter = new RequestRateLimitFilter(
				new SlidingWindowLimiter(Clock.systemUTC(), 100), 10, 10, 2, 10, 2);
		signIn("owner@example.com");
		filter.doFilter(request("/api/users/me/google-link"),
				new MockHttpServletResponse(), (req, res) -> {});
		filter.doFilter(request("/api/users/me/google-unlink"),
				new MockHttpServletResponse(), (req, res) -> {});
		var limited = new MockHttpServletResponse();
		var password = request("/api/users/me/password");
		password.setMethod("PUT");
		filter.doFilter(password, limited, (req, res) -> {});
		assertThat(limited.getStatus()).isEqualTo(429);

		signIn("other@example.com");
		var other = new MockHttpServletResponse();
		var otherPassword = request("/api/users/me/password");
		otherPassword.setMethod("PUT");
		filter.doFilter(otherPassword, other, (req, res) -> {});
		assertThat(other.getStatus()).isEqualTo(200);
	}

	@Test
	void invalidConfigurationIsRejected() {
		assertThatThrownBy(() -> filter(0, 5, 5, 2)).isInstanceOf(IllegalArgumentException.class);
	}

	private static RequestRateLimitFilter filter(int auth, int email, int ai, int concurrent) {
		return new RequestRateLimitFilter(new SlidingWindowLimiter(Clock.systemUTC(), 100), auth, email, ai, concurrent);
	}

	private static MockHttpServletRequest request(String path) {
		var request = new MockHttpServletRequest("POST", path);
		request.setRemoteAddr("192.0.2.1");
		return request;
	}

	private static void signIn(String name) {
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(name, null, List.of()));
	}
}
