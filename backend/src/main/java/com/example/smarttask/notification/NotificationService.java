package com.example.smarttask.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.Notification;
import com.example.smarttask.domain.NotificationType;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.NotificationRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class NotificationService {

	private final UserRepository userRepository;
	private final NotificationRepository notificationRepository;

	public NotificationService(
			UserRepository userRepository,
			NotificationRepository notificationRepository) {
		this.userRepository = userRepository;
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public void notifyProjectMemberAdded(User recipient, User actor, Project project) {
		if (recipient.getId().equals(actor.getId())) {
			return;
		}
		notificationRepository.save(new Notification(
				recipient,
				project,
				null,
				NotificationType.PROJECT_MEMBER_ADDED,
				"Bạn đã được thêm vào dự án",
				actor.getFullName() + " đã thêm bạn vào dự án \"" + project.getName() + "\"."));
	}

	@Transactional
	public void notifyTaskAssigned(User recipient, User actor, Project project, Task task) {
		if (recipient.getId().equals(actor.getId())) {
			return;
		}
		notificationRepository.save(new Notification(
				recipient,
				project,
				task,
				NotificationType.TASK_ASSIGNED,
				"Bạn được giao công việc",
				actor.getFullName() + " đã giao cho bạn công việc \"" + task.getTitle() + "\"."));
	}

	@Transactional
	public void notifyTaskStatusChanged(User actor, Task task) {
		var recipient = task.getAssignee();
		if (recipient == null || recipient.getId().equals(actor.getId())) {
			return;
		}
		notificationRepository.save(new Notification(
				recipient,
				task.getProject(),
				task,
				NotificationType.TASK_STATUS_CHANGED,
				"Trạng thái công việc đã thay đổi",
				actor.getFullName() + " đã chuyển công việc này sang trạng thái \""
						+ task.getStatus().getDisplayName() + "\"."));
	}

	@Transactional
	public List<NotificationResponse> findAll(String currentUserEmail, boolean unreadOnly) {
		var user = requireCurrentUser(currentUserEmail);
		createDeadlineNotifications(user, LocalDateTime.now());
		var notifications = unreadOnly
				? notificationRepository
						.findAllByRecipient_IdAndReadAtIsNullOrderByCreatedAtDescIdDesc(user.getId())
				: notificationRepository.findAllByRecipient_IdOrderByCreatedAtDescIdDesc(user.getId());
		return notifications.stream()
				.map(NotificationResponse::from)
				.toList();
	}

	@Transactional
	public NotificationResponse markRead(String currentUserEmail, Long notificationId) {
		var user = requireCurrentUser(currentUserEmail);
		var notification = notificationRepository.findByIdAndRecipient_Id(
				notificationId,
				user.getId())
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"NOTIFICATION_NOT_FOUND",
						"Không tìm thấy thông báo"));
		if (notification.getReadAt() == null) {
			notification.setReadAt(LocalDateTime.now());
			notificationRepository.saveAndFlush(notification);
		}
		return NotificationResponse.from(notification);
	}

	@Transactional
	public ReadAllNotificationsResponse markAllRead(String currentUserEmail) {
		var user = requireCurrentUser(currentUserEmail);
		var updatedCount = notificationRepository.markAllRead(user.getId(), LocalDateTime.now());
		return new ReadAllNotificationsResponse(updatedCount);
	}

	@Transactional
	public int createDeadlineNotificationsForAll(LocalDateTime now) {
		var earliestSupportedDueAt = LocalDateTime.of(1970, 1, 1, 0, 0);
		var overdueCount = notificationRepository.insertDeadlineNotificationsForAllAssignees(
				NotificationType.TASK_OVERDUE.name(),
				"Công việc đã quá hạn",
				"Công việc \"",
				"\" đã quá hạn.",
				earliestSupportedDueAt,
				now);
		var dueSoonCount = notificationRepository.insertDeadlineNotificationsForAllAssignees(
				NotificationType.TASK_DUE_SOON.name(),
				"Công việc sắp đến hạn",
				"Công việc \"",
				"\" sẽ đến hạn trong vòng 24 giờ.",
				now,
				now.plusHours(24));
		return overdueCount + dueSoonCount;
	}

	private void createDeadlineNotifications(User recipient, LocalDateTime now) {
		var earliestSupportedDueAt = LocalDateTime.of(1970, 1, 1, 0, 0);
		notificationRepository.insertDeadlineNotifications(
				recipient.getId(),
				NotificationType.TASK_OVERDUE.name(),
				"Công việc đã quá hạn",
				"Công việc \"",
				"\" đã quá hạn.",
				earliestSupportedDueAt,
				now);
		notificationRepository.insertDeadlineNotifications(
				recipient.getId(),
				NotificationType.TASK_DUE_SOON.name(),
				"Công việc sắp đến hạn",
				"Công việc \"",
				"\" sẽ đến hạn trong vòng 24 giờ.",
				now,
				now.plusHours(24));
	}

	private User requireCurrentUser(String email) {
		var user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						HttpStatus.UNAUTHORIZED,
						"UNAUTHORIZED",
						"Cần đăng nhập để tiếp tục"));
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"ACCOUNT_LOCKED",
					"Tài khoản đã bị khóa");
		}
		return user;
	}

}
