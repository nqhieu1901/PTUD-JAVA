package com.example.smarttask.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskStatusUpdateRequest(
		@NotBlank(message = "Trạng thái không được để trống")
		@Size(max = 32, message = "Mã trạng thái không hợp lệ")
		String status) {
}
