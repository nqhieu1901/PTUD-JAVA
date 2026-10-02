package com.example.smarttask.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.auth.AuthUserResponse;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AuthUserResponse updateProfile(
			String currentUserEmail,
			UpdateUserProfileRequest request) {
		var user = requireCurrentUser(currentUserEmail);
		user.setFullName(request.fullName());
		userRepository.saveAndFlush(user);
		return AuthUserResponse.from(user);
	}

	@Transactional
	public void changePassword(
			String currentUserEmail,
			ChangePasswordRequest request) {
		var user = userRepository.findByEmailForUpdate(currentUserEmail)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Cần đăng nhập để tiếp tục"));
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
		}
		if (!PasswordPolicy.passwordLoginEnabled(user)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"PASSWORD_LOGIN_DISABLED",
					"Tài khoản này đăng nhập bằng Google và chưa có mật khẩu SmartTask");
		}
		PasswordPolicy.requireSupportedLength(request.currentPassword());
		PasswordPolicy.requireSupportedLength(request.newPassword());
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"CURRENT_PASSWORD_INVALID",
					"Mật khẩu hiện tại không đúng");
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.revokeCredentials();
		userRepository.saveAndFlush(user);
	}

	private User requireCurrentUser(String email) {
		var user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						HttpStatus.UNAUTHORIZED,
						"UNAUTHORIZED",
						"Cần đăng nhập để tiếp tục"));
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"ACCOUNT_LOCKED",
					"Tài khoản đã bị khóa");
		}
		return user;
	}
}
