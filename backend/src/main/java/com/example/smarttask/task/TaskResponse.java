package com.example.smarttask.task;

import java.time.LocalDateTime;

import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskPriority;

public record TaskResponse(
		Long id,
		Long projectId,
		Long parentTaskId,
		String title,
		String description,
		TaskPriority priority,
		TaskStatusResponse status,
		TaskUserResponse assignee,
		TaskUserResponse createdBy,
		LocalDateTime startAt,
		LocalDateTime dueAt,
		Integer estimatedMinutes,
		long boardOrder,
		LocalDateTime completedAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static TaskResponse from(Task task) {
		return new TaskResponse(
				task.getId(),
				task.getProject().getId(),
				task.getParentTask() == null ? null : task.getParentTask().getId(),
				task.getTitle(),
				task.getDescription(),
				task.getPriority(),
				TaskStatusResponse.from(task.getStatus()),
				task.getAssignee() == null ? null : TaskUserResponse.from(task.getAssignee()),
				TaskUserResponse.from(task.getCreatedBy()),
				task.getStartAt(),
				task.getDueAt(),
				task.getEstimatedMinutes(),
				task.getBoardOrder(),
				task.getCompletedAt(),
				task.getCreatedAt(),
				task.getUpdatedAt());
	}
}
