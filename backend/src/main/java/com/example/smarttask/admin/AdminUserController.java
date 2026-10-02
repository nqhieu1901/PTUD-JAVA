package com.example.smarttask.admin;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	public AdminUserPageResponse listUsers(
			Authentication authentication,
			@RequestParam(defaultValue = "") String query,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminUserService.listUsers(authentication.getName(), query, page, size);
	}

	@PutMapping("/{userId}/status")
	public AdminUserResponse updateStatus(
			Authentication authentication,
			@PathVariable Long userId,
			@Valid @RequestBody UpdateAccountStatusRequest request) {
		return adminUserService.updateStatus(authentication.getName(), userId, request);
	}
}
