package com.example.smarttask.task;

import java.time.LocalDate;

import com.example.smarttask.domain.TaskPriority;
public record TaskFilter(
		String query,
		String status,
		TaskPriority priority,
		LocalDate dueFrom,
		LocalDate dueTo,
		Long assigneeId) {

	public TaskFilter {
		if (query != null) {
			query = query.strip();
			if (query.isEmpty()) {
				query = null;
			}
		}
		if (status != null) {
			status = status.strip();
			if (status.isEmpty()) {
				status = null;
			}
		}
	}
}
