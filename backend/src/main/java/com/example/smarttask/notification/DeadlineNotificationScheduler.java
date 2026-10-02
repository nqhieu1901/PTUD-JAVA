package com.example.smarttask.notification;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DeadlineNotificationScheduler {

	private final NotificationService notificationService;

	DeadlineNotificationScheduler(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Scheduled(
			fixedDelayString = "${smarttask.notifications.deadline-scan-ms:60000}",
			initialDelayString = "${smarttask.notifications.deadline-initial-delay-ms:60000}")
	void createDeadlineNotifications() {
		notificationService.createDeadlineNotificationsForAll(LocalDateTime.now());
	}
}
