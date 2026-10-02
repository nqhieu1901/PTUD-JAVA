package com.example.smarttask.auth;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfResponse(
		String token,
		String headerName,
		String parameterName) {

	public static CsrfResponse from(CsrfToken csrfToken) {
		return new CsrfResponse(
				csrfToken.getToken(),
				csrfToken.getHeaderName(),
				csrfToken.getParameterName());
	}
}
