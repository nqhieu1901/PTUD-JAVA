package com.example.smarttask.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateTaskDecompositionRequest(
		@NotNull(message = "Số lượng gợi ý không được để trống")
		@Min(value = 2, message = "Cần tạo ít nhất 2 gợi ý")
		@Max(value = 6, message = "Chỉ được tạo tối đa 6 gợi ý")
		Integer maxSuggestions) {
}
