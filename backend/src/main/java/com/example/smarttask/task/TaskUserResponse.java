package com.example.smarttask.task;

import com.example.smarttask.domain.User;

public record TaskUserResponse(
		Long id,
		String email,
		String fullName) {

	public static TaskUserResponse from(User user) {
		return new TaskUserResponse(user.getId(), user.getEmail(), user.getFullName());
	}
}
