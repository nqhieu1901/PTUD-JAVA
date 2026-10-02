package com.example.smarttask.attachment;

import com.example.smarttask.domain.User;

public record AttachmentAuthorResponse(Long id, String email, String fullName) {

	static AttachmentAuthorResponse from(User user) {
		return new AttachmentAuthorResponse(user.getId(), user.getEmail(), user.getFullName());
	}
}
