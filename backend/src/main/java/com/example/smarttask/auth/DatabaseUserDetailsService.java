package com.example.smarttask.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.smarttask.repository.UserRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;
	private final AuthService authService;

	public DatabaseUserDetailsService(UserRepository userRepository, AuthService authService) {
		this.userRepository = userRepository;
		this.authService = authService;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		var user = userRepository.findByEmailIgnoreCase(authService.normalizeEmail(email))
				.orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
		if (!PasswordPolicy.passwordLoginEnabled(user)) {
			throw new UsernameNotFoundException("Không tìm thấy tài khoản đăng nhập bằng mật khẩu");
		}

		return new SmartTaskUserDetails(user);
	}
}
