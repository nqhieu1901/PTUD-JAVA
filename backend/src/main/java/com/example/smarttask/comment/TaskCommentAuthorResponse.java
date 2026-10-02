package com.example.smarttask.comment;

import com.example.smarttask.domain.User;

public record TaskCommentAuthorResponse(Long id, String email, String fullName) {

	static TaskCommentAuthorResponse from(User user) {
		return new TaskCommentAuthorResponse(user.getId(), user.getEmail(), user.getFullName());
	}
}
