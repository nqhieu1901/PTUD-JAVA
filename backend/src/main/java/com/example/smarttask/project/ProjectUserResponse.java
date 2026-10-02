package com.example.smarttask.project;

import com.example.smarttask.domain.User;

public record ProjectUserResponse(
		Long id,
		String email,
		String fullName) {

	public static ProjectUserResponse from(User user) {
		return new ProjectUserResponse(user.getId(), user.getEmail(), user.getFullName());
	}
}
