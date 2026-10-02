package com.example.smarttask.project;

import com.example.smarttask.domain.ProjectRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddProjectMemberRequest(
		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không đúng định dạng")
		@Size(max = 254, message = "Email không được vượt quá 254 ký tự")
		String email,
		ProjectRole role) {

	public AddProjectMemberRequest {
		if (email != null) {
			email = email.strip();
		}
	}
}
