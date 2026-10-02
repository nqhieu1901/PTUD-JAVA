package com.example.smarttask.auth;

import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;

public record AuthUserResponse(
		Long id,
		String email,
		String fullName,
		SystemRole role,
		boolean passwordLoginEnabled,
		boolean emailVerified,
		boolean googleLinked) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getSystemRole(),
				PasswordPolicy.passwordLoginEnabled(user),
				user.isEmailVerified(),
				user.getGoogleSubject() != null);
	}
}
