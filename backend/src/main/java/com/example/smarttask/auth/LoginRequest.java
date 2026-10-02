package com.example.smarttask.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không đúng định dạng")
		@Size(max = 254, message = "Email không được vượt quá 254 ký tự")
		String email,

		@NotBlank(message = "Mật khẩu không được để trống")
		@Size(max = 72, message = "Mật khẩu không được vượt quá 72 ký tự")
		String password) {

	public LoginRequest {
		if (email != null) {
			email = email.strip();
		}
	}
}
