package com.example.smarttask.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.smarttask.auth.AuthUserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PutMapping
	public AuthUserResponse updateProfile(
			Authentication authentication,
			@Valid @RequestBody UpdateUserProfileRequest request) {
		return userService.updateProfile(authentication.getName(), request);
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(
			Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(authentication.getName(), request);
	}
}
