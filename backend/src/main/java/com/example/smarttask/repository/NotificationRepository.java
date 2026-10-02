package com.example.smarttask.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findAllByRecipient_IdOrderByCreatedAtDescIdDesc(Long recipientId);

	List<Notification> findAllByRecipient_IdAndReadAtIsNullOrderByCreatedAtDescIdDesc(Long recipientId);

	Optional<Notification> findByIdAndRecipient_Id(Long id, Long recipientId);

	@Modifying
	@Query(value = """
			insert ignore into notifications (
				recipient_id, project_id, task_id, type, title, message, dedupe_key
			)
			select :recipientId, task.project_id, task.id, :type, :title,
				concat(concat(:messagePrefix, task.title), :messageSuffix),
				concat(:type, ':', task.id, ':', task.due_at)
			from tasks task
			join projects project on project.id = task.project_id
			join task_statuses status on status.id = task.status_id
			where task.assignee_id = :recipientId
			  and project.status = 'ACTIVE'
			  and task.deleted_at is null
			  and status.is_terminal = false
			  and status.is_active = true
			  and task.due_at >= :dueFrom
			  and task.due_at < :dueBefore
			""", nativeQuery = true)
	int insertDeadlineNotifications(
			@Param("recipientId") Long recipientId,
			@Param("type") String type,
			@Param("title") String title,
			@Param("messagePrefix") String messagePrefix,
			@Param("messageSuffix") String messageSuffix,
			@Param("dueFrom") LocalDateTime dueFrom,
			@Param("dueBefore") LocalDateTime dueBefore);

	@Modifying
	@Query(value = """
			insert ignore into notifications (
				recipient_id, project_id, task_id, type, title, message, dedupe_key
			)
			select task.assignee_id, task.project_id, task.id, :type, :title,
				concat(concat(:messagePrefix, task.title), :messageSuffix),
				concat(:type, ':', task.id, ':', task.due_at)
			from tasks task
			join users assignee on assignee.id = task.assignee_id
			join projects project on project.id = task.project_id
			join task_statuses status on status.id = task.status_id
			where assignee.account_status = 'ACTIVE'
			  and project.status = 'ACTIVE'
			  and task.deleted_at is null
			  and status.is_terminal = false
			  and status.is_active = true
			  and task.due_at >= :dueFrom
			  and task.due_at < :dueBefore
			""", nativeQuery = true)
	int insertDeadlineNotificationsForAllAssignees(
			@Param("type") String type,
			@Param("title") String title,
			@Param("messagePrefix") String messagePrefix,
			@Param("messageSuffix") String messageSuffix,
			@Param("dueFrom") LocalDateTime dueFrom,
			@Param("dueBefore") LocalDateTime dueBefore);

	@Modifying
	@Query("""
			update Notification notification
			set notification.readAt = :readAt
			where notification.recipient.id = :recipientId
			  and notification.readAt is null
			""")
	int markAllRead(
			@Param("recipientId") Long recipientId,
			@Param("readAt") LocalDateTime readAt);
}
