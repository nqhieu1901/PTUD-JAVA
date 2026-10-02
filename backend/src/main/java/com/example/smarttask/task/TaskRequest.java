package com.example.smarttask.task;

import java.time.LocalDateTime;

import com.example.smarttask.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TaskRequest(
		@NotBlank(message = "Tên công việc không được để trống")
		@Size(max = 255, message = "Tên công việc không được vượt quá 255 ký tự")
		String title,
		@Size(max = 16000, message = "Mô tả không được vượt quá 16000 ký tự")
		String description,
		TaskPriority priority,
		LocalDateTime startAt,
		@NotNull(message = "Deadline không được để trống")
		LocalDateTime dueAt,
		@Positive(message = "Thời lượng dự kiến phải lớn hơn 0")
		Integer estimatedMinutes,
		Long assigneeId,
		Long parentTaskId) {

	public TaskRequest {
		if (title != null) {
			title = title.strip();
		}
		if (description != null) {
			description = description.strip();
			if (description.isEmpty()) {
				description = null;
			}
		}
		if (priority == null) {
			priority = TaskPriority.MEDIUM;
		}
	}
}
