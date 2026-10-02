package com.example.smarttask.comment;

import java.time.LocalDateTime;

import com.example.smarttask.domain.TaskComment;

public record TaskCommentResponse(
		Long id,
		Long projectId,
		Long taskId,
		String content,
		TaskCommentAuthorResponse author,
		LocalDateTime createdAt) {

	static TaskCommentResponse from(TaskComment comment) {
		return new TaskCommentResponse(
				comment.getId(),
				comment.getProject().getId(),
				comment.getTask().getId(),
				comment.getContent(),
				TaskCommentAuthorResponse.from(comment.getAuthor()),
				comment.getCreatedAt());
	}
}
