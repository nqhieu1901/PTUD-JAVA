package com.example.smarttask.task;

import com.example.smarttask.domain.TaskStatus;
public record TaskStatusResponse(
		Long id,
		String code,
		String displayName,
		short displayOrder,
		boolean terminal) {

	public static TaskStatusResponse from(TaskStatus status) {
		return new TaskStatusResponse(
				status.getId(),
				status.getCode(),
				status.getDisplayName(),
				status.getDisplayOrder(),
				status.isTerminal());
	}
}
