package com.example.smarttask.attachment;

import java.time.LocalDateTime;

import com.example.smarttask.domain.TaskAttachment;

public record TaskAttachmentResponse(
		Long id,
		Long projectId,
		Long taskId,
		String originalName,
		String contentType,
		long sizeBytes,
		AttachmentAuthorResponse uploadedBy,
		LocalDateTime createdAt) {

	static TaskAttachmentResponse from(TaskAttachment attachment) {
		return new TaskAttachmentResponse(
				attachment.getId(),
				attachment.getProject().getId(),
				attachment.getTask().getId(),
				attachment.getOriginalName(),
				attachment.getContentType(),
				attachment.getSizeBytes(),
				AttachmentAuthorResponse.from(attachment.getUploadedBy()),
				attachment.getCreatedAt());
	}
}
