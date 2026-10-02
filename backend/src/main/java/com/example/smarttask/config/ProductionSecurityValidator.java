package com.example.smarttask.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("prod")
public class ProductionSecurityValidator {

	static final String SECURE_COOKIE_ERROR =
			"Production profile requires server.servlet.session.cookie.secure=true";

	private final boolean sessionCookieSecure;

	public ProductionSecurityValidator(
			@Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure) {
		this.sessionCookieSecure = sessionCookieSecure;
	}

	@PostConstruct
	void validate() {
		if (!sessionCookieSecure) {
			throw new IllegalStateException(SECURE_COOKIE_ERROR);
		}
	}
}
