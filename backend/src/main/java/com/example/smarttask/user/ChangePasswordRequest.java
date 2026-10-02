package com.example.smarttask.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "Mật khẩu hiện tại không được để trống")
		@Size(max = 72, message = "Mật khẩu hiện tại không được vượt quá 72 ký tự")
		String currentPassword,

		@NotBlank(message = "Mật khẩu mới không được để trống")
		@Size(min = 8, max = 72, message = "Mật khẩu mới phải có từ 8 đến 72 ký tự")
		String newPassword) {
}
