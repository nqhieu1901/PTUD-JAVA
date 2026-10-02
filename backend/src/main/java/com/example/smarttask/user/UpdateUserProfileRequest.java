package com.example.smarttask.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
		@NotBlank(message = "Họ tên không được để trống")
		@Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
		String fullName) {

	public UpdateUserProfileRequest {
		if (fullName != null) {
			fullName = fullName.strip();
		}
	}
}
