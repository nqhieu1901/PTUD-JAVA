package com.example.smarttask.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.smarttask.auth.GoogleLinkIntentStore;
import com.example.smarttask.auth.GoogleAccountLinkService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

	private final String failureUrl;
	private final GoogleLinkIntentStore linkIntents;
	private final GoogleAccountLinkService links;
	private final SecurityContextRepository securityContexts;

	GoogleLoginFailureHandler(String failureUrl) {
		this(failureUrl, null, null, null);
	}

	GoogleLoginFailureHandler(String failureUrl, GoogleLinkIntentStore linkIntents) {
		this(failureUrl, linkIntents, null, null);
	}

	GoogleLoginFailureHandler(String failureUrl, GoogleLinkIntentStore linkIntents,
			GoogleAccountLinkService links, SecurityContextRepository securityContexts) {
		this.failureUrl = failureUrl;
		this.linkIntents = linkIntents;
		this.links = links;
		this.securityContexts = securityContexts;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		if (linkIntents != null) {
			var restoration = linkIntents.takeRestoration(request.getSession(false));
			if (links != null && securityContexts != null) {
				restoration.flatMap(links::restoreAuthentication).ifPresent(authentication -> {
					var context = SecurityContextHolder.createEmptyContext();
					context.setAuthentication(authentication);
					SecurityContextHolder.setContext(context);
					securityContexts.saveContext(context, request, response);
				});
			}
		}
		String reason = "failed";
		if (exception instanceof OAuth2AuthenticationException oauthException) {
			reason = switch (oauthException.getError().getErrorCode()) {
				case "GOOGLE_ACCOUNT_LINK_REQUIRED", "GOOGLE_ACCOUNT_CONFLICT" -> "link_required";
				case "GOOGLE_LINK_EMAIL_MISMATCH" -> "link_email_mismatch";
				case "GOOGLE_LINK_SUBJECT_CONFLICT" -> "link_conflict";
				case "GOOGLE_LINK_INTENT_MISSING", "GOOGLE_LINK_INTENT_EXPIRED",
						"GOOGLE_LINK_INTENT_INVALID", "GOOGLE_LINK_INTENT_STALE" -> "link_expired";
				case "GOOGLE_ACCOUNT_ALREADY_LINKED" -> "already_linked";
				case "GOOGLE_LINK_NOT_ALLOWED" -> "link_not_allowed";
				case "ACCOUNT_LOCKED" -> "locked";
				default -> "failed";
			};
		}
		var redirect = UriComponentsBuilder.fromUriString(failureUrl)
				.replaceQueryParam("authError", "google")
				.replaceQueryParam("reason", reason)
				.build().toUriString();
		response.sendRedirect(redirect);
	}
}
