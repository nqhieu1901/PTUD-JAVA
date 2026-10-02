package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AiRequest;
import com.example.smarttask.domain.AiRequestType;
import com.example.smarttask.domain.AiSuggestion;
import com.example.smarttask.domain.AiSuggestionType;
import com.example.smarttask.domain.Notification;
import com.example.smarttask.domain.NotificationType;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskStatus;
import com.example.smarttask.domain.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class SchemaMappingTests {

	@Autowired
	private EntityManager entityManager;

	@Test
	void seedsTheThreeTaskStatuses() {
		var statuses = entityManager
				.createQuery("select status from TaskStatus status order by status.displayOrder",
						TaskStatus.class)
				.getResultList();

		assertThat(statuses)
				.extracting(TaskStatus::getCode)
				.containsExactly(
						"TODO",
						"IN_PROGRESS",
						"DONE");
	}

	@Test
	void persistsTheCoreEntityGraphAndJsonSuggestions() {
		var owner = new User("owner@example.com", "hashed-password", "Project Owner");
		entityManager.persist(owner);

		var project = new Project(owner, "Smart Task");
		entityManager.persist(project);
		entityManager.flush();

		var membership = new ProjectMember(project, owner, ProjectRole.OWNER);
		entityManager.persist(membership);

		var todo = entityManager.find(TaskStatus.class, 1L);
		var task = new Task(
				project,
				owner,
				todo,
				"Thiết kế cơ sở dữ liệu",
				LocalDateTime.now().plusDays(3));
		task.setAssignee(owner);
		entityManager.persist(task);

		var notification = new Notification(
				owner,
				project,
				task,
				NotificationType.TASK_ASSIGNED,
				"Bạn được giao công việc",
				"Bạn được giao công việc thiết kế cơ sở dữ liệu.");
		entityManager.persist(notification);

		var request = new AiRequest(
				owner,
				project,
				AiRequestType.TASK_DECOMPOSITION,
				"{\"goal\":\"Xây dựng website quản lý công việc\"}");
		request.setSourceTask(task);
		entityManager.persist(request);

		var suggestion = new AiSuggestion(
				request,
				AiSuggestionType.SUBTASK,
				"{\"title\":\"Tạo schema MySQL\"}");
		suggestion.setTargetTask(task);
		entityManager.persist(suggestion);
		entityManager.flush();

		assertThat(task.getId()).isNotNull();
		assertThat(notification.getId()).isNotNull();
		assertThat(request.getId()).isNotNull();
		assertThat(suggestion.getId()).isNotNull();
		assertThat(suggestion.getProposedData()).contains("Tạo schema MySQL");
	}
}
