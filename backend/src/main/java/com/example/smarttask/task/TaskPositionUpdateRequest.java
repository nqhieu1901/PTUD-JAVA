package com.example.smarttask.task;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskPositionUpdateRequest(
		@NotBlank(message = "Trạng thái không được để trống")
		@Size(max = 32, message = "Mã trạng thái không hợp lệ")
		String status,
		@NotNull(message = "Vị trí không được để trống")
		@Min(value = 0, message = "Vị trí không được nhỏ hơn 0")
		Long boardOrder) {
}
