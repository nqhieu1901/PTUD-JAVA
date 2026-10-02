package com.example.smarttask.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.smarttask.web.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/users/me")
public class GoogleAccountLinkController {

	private final GoogleAccountLinkService links;
	private final GoogleLinkIntentStore intents;
	private final boolean googleEnabled;

	public GoogleAccountLinkController(GoogleAccountLinkService links, GoogleLinkIntentStore intents,
			@Value("${smarttask.oauth.google.enabled:false}") boolean googleEnabled) {
		this.links = links;
		this.intents = intents;
		this.googleEnabled = googleEnabled;
	}

	@PostMapping("/google-link")
	public GoogleLinkStartResponse start(@Valid @RequestBody CurrentPasswordRequest body,
			Authentication authentication, HttpServletRequest request) {
		if (!googleEnabled) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_OAUTH_DISABLED",
					"Đăng nhập Google chưa được cấu hình");
		}
		var target = links.begin(authentication.getName(), body.currentPassword());
		request.changeSessionId();
		intents.save(request.getSession(false), target.userId(), target.email(), target.credentialsVersion());
		return new GoogleLinkStartResponse(request.getContextPath() + "/oauth2/authorization/google-link");
	}

	@PostMapping("/google-unlink")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlink(@Valid @RequestBody CurrentPasswordRequest body,
			Authentication authentication, HttpServletRequest request) {
		var session = request.getSession(false);
		intents.clear(session);
		links.unlink(authentication.getName(), body.currentPassword());
		SecurityContextHolder.clearContext();
		if (session != null) session.invalidate();
	}

	public record CurrentPasswordRequest(
			@NotBlank(message = "Mật khẩu hiện tại không được để trống")
			@Size(max = 72, message = "Mật khẩu hiện tại không được vượt quá 72 ký tự")
			String currentPassword) { }

	public record GoogleLinkStartResponse(String authorizationUrl) { }
}
