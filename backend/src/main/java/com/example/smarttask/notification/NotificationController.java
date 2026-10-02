package com.example.smarttask.notification;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public List<NotificationResponse> findAll(
			Authentication authentication,
			@RequestParam(defaultValue = "false") boolean unreadOnly) {
		return notificationService.findAll(authentication.getName(), unreadOnly);
	}

	@PatchMapping("/{notificationId}/read")
	public NotificationResponse markRead(
			Authentication authentication,
			@PathVariable Long notificationId) {
		return notificationService.markRead(authentication.getName(), notificationId);
	}

	@PatchMapping("/read-all")
	public ReadAllNotificationsResponse markAllRead(Authentication authentication) {
		return notificationService.markAllRead(authentication.getName());
	}
}
