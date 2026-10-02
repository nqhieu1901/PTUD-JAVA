package com.example.smarttask.auth;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountTokenService accountTokens;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AccountTokenService accountTokens) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.accountTokens = accountTokens;
	}

	@Transactional
	public AuthUserResponse register(RegisterRequest request) {
		PasswordPolicy.requireSupportedLength(request.password());
		var normalizedEmail = normalizeEmail(request.email());
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new DuplicateEmailException();
		}

		var user = new User(
				normalizedEmail,
				passwordEncoder.encode(request.password()),
				request.fullName().strip());

		User saved;
		try {
			saved = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
		accountTokens.requestVerificationAfterRegistration(saved);
		return AuthUserResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public AuthUserResponse findByEmail(String email) {
		var user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
				.orElseThrow(InvalidCredentialsException::new);
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"ACCOUNT_LOCKED",
					"Tài khoản đã bị khóa");
		}
		return AuthUserResponse.from(user);
	}

	public String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}
}
