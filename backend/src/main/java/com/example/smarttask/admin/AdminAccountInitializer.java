package com.example.smarttask.admin;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.auth.PasswordPolicy;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccountInitializer.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;
	private final String fullName;

	public AdminAccountInitializer(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${smarttask.bootstrap-admin.email:}") String email,
			@Value("${smarttask.bootstrap-admin.password:}") String password,
			@Value("${smarttask.bootstrap-admin.full-name:Quản trị viên SmartTask}") String fullName) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
		this.fullName = fullName;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		if (email.isBlank() && password.isBlank()) {
			return;
		}
		if (email.isBlank() || password.length() < 8) {
			throw new IllegalStateException(
					"SMARTTASK_ADMIN_EMAIL và SMARTTASK_ADMIN_PASSWORD tối thiểu 8 ký tự phải được cấu hình cùng nhau");
		}
		if (PasswordPolicy.exceedsBcryptLimit(password)) {
			throw new IllegalStateException("SMARTTASK_ADMIN_PASSWORD không được vượt quá 72 byte UTF-8");
		}

		var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
		var existing = userRepository.findByEmailIgnoreCase(normalizedEmail);
		if (existing.isPresent()) {
			var admin = existing.orElseThrow();
			if (admin.getSystemRole() != SystemRole.ADMIN) {
				throw new IllegalStateException(
						"SMARTTASK_ADMIN_EMAIL trùng với tài khoản không có quyền ADMIN; từ chối tự nâng quyền");
			}
			return;
		}

		var admin = new User(
				normalizedEmail,
				passwordEncoder.encode(password),
				fullName.isBlank() ? "Quản trị viên SmartTask" : fullName.strip());
		admin.setSystemRole(SystemRole.ADMIN);
		userRepository.saveAndFlush(admin);
		LOGGER.info("Created configured ADMIN account {}", normalizedEmail);
	}
}
