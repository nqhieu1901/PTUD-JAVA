package com.example.smarttask.project;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
		@NotBlank(message = "Tên dự án không được để trống")
		@Size(max = 160, message = "Tên dự án không được vượt quá 160 ký tự")
		String name,
		String description,
		LocalDate startDate,
		LocalDate dueDate) {

	public ProjectRequest {
		if (name != null) {
			name = name.strip();
		}
		if (description != null) {
			description = description.strip();
			if (description.isEmpty()) {
				description = null;
			}
		}
	}
}
