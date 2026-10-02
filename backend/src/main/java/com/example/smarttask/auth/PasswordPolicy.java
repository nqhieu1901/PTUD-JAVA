package com.example.smarttask.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;

import com.example.smarttask.domain.User;
import com.example.smarttask.web.ApiException;

public final class PasswordPolicy {

	public static final String GOOGLE_ONLY_PASSWORD_HASH = "!GOOGLE_ONLY!";

	private PasswordPolicy() {
	}

	public static boolean exceedsBcryptLimit(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > 72;
	}

	public static void requireSupportedLength(String password) {
		if (exceedsBcryptLimit(password)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"PASSWORD_TOO_LONG",
					"Mật khẩu không được vượt quá 72 byte UTF-8; ký tự có dấu có thể chiếm nhiều byte");
		}
	}

	public static boolean passwordLoginEnabled(User user) {
		return !GOOGLE_ONLY_PASSWORD_HASH.equals(user.getPasswordHash());
	}
}
