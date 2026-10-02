package com.example.smarttask.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.smarttask.repository.UserRepository;

class AccountTokenServiceConfigurationTests {

	@ParameterizedTest
	@ValueSource(strings = {
			"http://example.com", "http://localhost.example.com", "ftp://localhost:5173",
			"https://user@example.com", "https://example.com/?next=bad", "https://example.com/#bad"
	})
	void rejectsUnsafeFrontendUrls(String frontendUrl) {
		assertThatThrownBy(() -> service(frontendUrl))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid account email configuration");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://localhost:5173", "http://127.0.0.1:5173/app", "https://smarttask.example.com"
	})
	void acceptsLocalDevelopmentHttpAndProductionHttps(String frontendUrl) {
		assertThatCode(() -> service(frontendUrl)).doesNotThrowAnyException();
	}

	private AccountTokenService service(String frontendUrl) {
		return new AccountTokenService(mock(UserRepository.class), mock(AccountTokenRepository.class),
				mock(AccountMailDelivery.class), mock(PasswordEncoder.class), frontendUrl, 30, 1_440, 60);
	}
}
