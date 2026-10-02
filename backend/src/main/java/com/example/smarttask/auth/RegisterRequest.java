package com.example.smarttask.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không đúng định dạng")
		@Size(max = 254, message = "Email không được vượt quá 254 ký tự")
		String email,

		@NotBlank(message = "Mật khẩu không được để trống")
		@Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
		String password,

		@NotBlank(message = "Họ tên không được để trống")
		@Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
		String fullName) {

	public RegisterRequest {
		if (email != null) {
			email = email.strip();
		}
		if (fullName != null) {
			fullName = fullName.strip();
		}
	}
}
