package com.example.smarttask.admin;

import java.time.LocalDateTime;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;

public record AdminUserResponse(
		Long id,
		String email,
		String fullName,
		SystemRole role,
		AccountStatus status,
		LocalDateTime lockedAt,
		LocalDateTime createdAt) {

	public static AdminUserResponse from(User user) {
		return new AdminUserResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getSystemRole(),
				user.getAccountStatus(),
				user.getLockedAt(),
				user.getCreatedAt());
	}
}
