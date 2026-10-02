package com.example.smarttask.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkflowStatusItemRequest(
		Long id,
		@NotBlank(message = "Tên trạng thái không được để trống")
		@Size(max = 100, message = "Tên trạng thái không được vượt quá 100 ký tự")
		String displayName,
		@NotNull(message = "Loại trạng thái không được để trống")
		Boolean terminal) {
}
