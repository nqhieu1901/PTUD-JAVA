package com.example.smarttask.notification;

import java.time.LocalDateTime;

import com.example.smarttask.domain.Notification;
import com.example.smarttask.domain.NotificationType;

public record NotificationResponse(
		Long id,
		NotificationType type,
		String title,
		String message,
		Long projectId,
		Long taskId,
		boolean read,
		LocalDateTime readAt,
		LocalDateTime createdAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getMessage(),
				notification.getProject().getId(),
				notification.getTask() == null ? null : notification.getTask().getId(),
				notification.getReadAt() != null,
				notification.getReadAt(),
				notification.getCreatedAt());
	}
}
