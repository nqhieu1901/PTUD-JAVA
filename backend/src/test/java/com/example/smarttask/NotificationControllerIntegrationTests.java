package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;

import java.time.LocalDateTime;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskStatusCode;
import com.example.smarttask.domain.User;
import com.example.smarttask.notification.NotificationService;
import com.example.smarttask.repository.NotificationRepository;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectMemberRepository projectMemberRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private TaskStatusRepository taskStatusRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationService notificationService;

	@Test
	void addingMemberCreatesAnUnreadNotificationOnlyForTheRecipient() throws Exception {
		var owner = createUser("notification-owner@example.com", "Chủ dự án");
		var member = createUser("notification-member@example.com", "Thành viên");
		var project = createProject(owner, "Website thông minh");

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/notifications")
					.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("PROJECT_MEMBER_ADDED"))
				.andExpect(jsonPath("$[0].title").value("Bạn đã được thêm vào dự án"))
				.andExpect(jsonPath("$[0].projectId").value(project.getId()))
				.andExpect(jsonPath("$[0].taskId").doesNotExist())
				.andExpect(jsonPath("$[0].read").value(false))
				.andExpect(jsonPath("$[0].readAt").doesNotExist())
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty());

		mockMvc.perform(get("/api/notifications")
					.param("unreadOnly", "true")
					.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/api/notifications")
					.with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void assigningAndReassigningATaskNotifiesOnlyTheNewAssignee() throws Exception {
		var owner = createUser("task-notification-owner@example.com", "Người giao việc");
		var firstAssignee = createUser("first-assignee@example.com", "Người thứ nhất");
		var secondAssignee = createUser("second-assignee@example.com", "Người thứ hai");
		var project = createProject(owner, "Dự án giao việc");
		addMembership(project, firstAssignee);
		addMembership(project, secondAssignee);

		var createResult = mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Thiết kế Kanban", firstAssignee.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		Number taskIdValue = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
		var taskId = taskIdValue.longValue();

		mockMvc.perform(get("/api/notifications").with(user(firstAssignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("TASK_ASSIGNED"))
				.andExpect(jsonPath("$[0].taskId").value(taskId));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), taskId)
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Thiết kế Kanban đã sửa", firstAssignee.getId())))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications").with(user(firstAssignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), taskId)
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Thiết kế Kanban đã sửa", secondAssignee.getId())))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications").with(user(secondAssignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].taskId").value(taskId));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), taskId)
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Thiết kế Kanban đã sửa", owner.getId())))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications").with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void onlyTheRecipientCanReadNotificationsAndReadAllReturnsTheUpdatedCount() throws Exception {
		var owner = createUser("read-owner@example.com", "Chủ dự án");
		var recipient = createUser("read-recipient@example.com", "Người nhận");
		var firstProject = createProject(owner, "Dự án thứ nhất");
		var secondProject = createProject(owner, "Dự án thứ hai");

		addMemberThroughApi(owner, recipient, firstProject);
		addMemberThroughApi(owner, recipient, secondProject);

		var listResult = mockMvc.perform(get("/api/notifications")
					.with(user(recipient.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].projectId").value(secondProject.getId()))
				.andReturn();
		Number notificationIdValue = JsonPath.read(
				listResult.getResponse().getContentAsString(),
				"$[0].id");
		var notificationId = notificationIdValue.longValue();

		mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

		mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
					.with(user(recipient.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(notificationId))
				.andExpect(jsonPath("$.read").value(true))
				.andExpect(jsonPath("$.readAt").isNotEmpty());

		mockMvc.perform(get("/api/notifications")
					.param("unreadOnly", "true")
					.with(user(recipient.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(patch("/api/notifications/read-all")
					.with(user(recipient.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedCount").value(1));

		mockMvc.perform(patch("/api/notifications/read-all")
					.with(user(recipient.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedCount").value(0));

		mockMvc.perform(get("/api/notifications")
					.param("unreadOnly", "true")
					.with(user(recipient.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void loadingNotificationsCreatesDueSoonAndOverdueAlertsIdempotently() throws Exception {
		var owner = createUser("deadline-owner@example.com", "Chủ dự án deadline");
		var assignee = createUser("deadline-assignee@example.com", "Người nhận deadline");
		var project = createProject(owner, "Dự án có deadline");
		addMembership(project, assignee);
		var now = LocalDateTime.now();
		var dueSoon = createTask(project, owner, assignee, TaskStatusCode.TODO,
				"Công việc sắp hạn", now.plusHours(12));
		var overdue = createTask(project, owner, assignee, TaskStatusCode.TODO,
				"Công việc quá hạn", now.minusHours(2));
		createTask(project, owner, assignee, TaskStatusCode.TODO,
				"Công việc còn xa", now.plusHours(25));
		createTask(project, owner, assignee, TaskStatusCode.DONE,
				"Công việc đã xong", now.plusHours(3));
		var deletedTask = createTask(project, owner, assignee, TaskStatusCode.TODO,
				"Công việc đã xóa", now.plusHours(5));
		deletedTask.setDeletedAt(now.minusMinutes(1));
		taskRepository.saveAndFlush(deletedTask);
		createTask(project, owner, null, TaskStatusCode.TODO,
				"Công việc chưa phân công", now.plusHours(6));

		var archivedProject = createProject(owner, "Dự án đã đóng");
		addMembership(archivedProject, assignee);
		archivedProject.setStatus(ProjectStatus.ARCHIVED);
		archivedProject.setArchivedAt(now.minusDays(1));
		projectRepository.saveAndFlush(archivedProject);
		createTask(archivedProject, owner, assignee, TaskStatusCode.TODO,
				"Deadline thuộc dự án đã đóng", now.plusHours(4));

		mockMvc.perform(get("/api/notifications")
					.param("unreadOnly", "true")
					.with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.type == 'TASK_DUE_SOON')].taskId")
						.value(hasItem(dueSoon.getId().intValue())))
				.andExpect(jsonPath("$[?(@.type == 'TASK_DUE_SOON')].projectId")
						.value(hasItem(project.getId().intValue())))
				.andExpect(jsonPath("$[?(@.type == 'TASK_OVERDUE')].taskId")
						.value(hasItem(overdue.getId().intValue())))
				.andExpect(jsonPath("$[?(@.type == 'TASK_OVERDUE')].projectId")
						.value(hasItem(project.getId().intValue())));

		mockMvc.perform(get("/api/notifications").with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].type").value(containsInAnyOrder(
						"TASK_DUE_SOON",
						"TASK_OVERDUE")));
	}

	@Test
	void backgroundDeadlineScanCreatesNotificationsForAllAssigneesIdempotently() {
		var owner = createUser("scan-owner@example.com", "Chủ dự án quét nền");
		var dueSoonAssignee = createUser("scan-due-soon@example.com", "Người sắp đến hạn");
		var overdueAssignee = createUser("scan-overdue@example.com", "Người quá hạn");
		var project = createProject(owner, "Dự án quét deadline");
		addMembership(project, dueSoonAssignee);
		addMembership(project, overdueAssignee);
		var now = LocalDateTime.now();
		createTask(project, owner, dueSoonAssignee, TaskStatusCode.TODO,
				"Task sắp hạn được quét nền", now.plusHours(6));
		createTask(project, owner, overdueAssignee, TaskStatusCode.TODO,
				"Task quá hạn được quét nền", now.minusMinutes(30));

		assertThat(notificationService.createDeadlineNotificationsForAll(now)).isEqualTo(2);
		assertThat(notificationRepository
				.findAllByRecipient_IdOrderByCreatedAtDescIdDesc(dueSoonAssignee.getId()))
				.extracting(notification -> notification.getType().name())
				.containsExactly("TASK_DUE_SOON");
		assertThat(notificationRepository
				.findAllByRecipient_IdOrderByCreatedAtDescIdDesc(overdueAssignee.getId()))
				.extracting(notification -> notification.getType().name())
				.containsExactly("TASK_OVERDUE");
		assertThat(notificationService.createDeadlineNotificationsForAll(now)).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = {6, -2})
	void rescheduledDeadlineCanNotifyAgainWithoutDuplicatingTheSameOccurrence(int dueInHours) throws Exception {
		var owner = createUser("reschedule-owner@example.com", "Chủ dự án");
		var assignee = createUser("reschedule-assignee@example.com", "Người nhận");
		var project = createProject(owner, "Đổi deadline");
		addMembership(project, assignee);
		var now = LocalDateTime.now().withNano(0);
		var task = createTask(project, owner, assignee, TaskStatusCode.TODO,
				"Deadline thay đổi", now.plusHours(dueInHours));

		mockMvc.perform(get("/api/notifications").with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		assertThat(notificationService.createDeadlineNotificationsForAll(now)).isZero();
		mockMvc.perform(patch("/api/notifications/read-all")
				.with(user(assignee.getEmail())).with(csrf()))
				.andExpect(status().isOk());

		task.setDueAt(task.getDueAt().plusHours(1));
		taskRepository.saveAndFlush(task);
		assertThat(notificationService.createDeadlineNotificationsForAll(now)).isEqualTo(1);
		assertThat(notificationService.createDeadlineNotificationsForAll(now)).isZero();
		mockMvc.perform(get("/api/notifications").with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(get("/api/notifications").param("unreadOnly", "true")
				.with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void statusChangeNotifiesAssigneeOnlyWhenAnotherUserChangesTheStatus() throws Exception {
		var owner = createUser("status-owner@example.com", "Người đổi trạng thái");
		var assignee = createUser("status-assignee@example.com", "Người được giao");
		var project = createProject(owner, "Dự án đổi trạng thái");
		addMembership(project, assignee);
		var task = createTask(project, owner, assignee, TaskStatusCode.TODO,
				"Kiểm thử thông báo trạng thái", LocalDateTime.now().plusDays(2));

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/position",
					project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"IN_PROGRESS\",\"boardOrder\":0}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications").with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("TASK_STATUS_CHANGED"))
				.andExpect(jsonPath("$[0].projectId").value(project.getId()))
				.andExpect(jsonPath("$[0].taskId").value(task.getId()));

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/position",
					project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"IN_PROGRESS\",\"boardOrder\":0}"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status",
					project.getId(), task.getId())
					.with(user(assignee.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"DONE\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications").with(user(assignee.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/api/notifications").with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	private User createUser(String email, String fullName) {
		return userRepository.saveAndFlush(new User(email, "hashed-password", fullName));
	}

	private Project createProject(User owner, String name) {
		var project = projectRepository.saveAndFlush(new Project(owner, name));
		projectMemberRepository.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
		return project;
	}

	private void addMembership(Project project, User member) {
		projectMemberRepository.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
	}

	private void addMemberThroughApi(User owner, User member, Project project) throws Exception {
		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private Task createTask(
			Project project,
			User creator,
			User assignee,
			TaskStatusCode statusCode,
			String title,
			LocalDateTime dueAt) {
		var status = taskStatusRepository.findByCode(statusCode).orElseThrow();
		var task = new Task(project, creator, status, title, dueAt);
		task.setAssignee(assignee);
		task.setBoardOrder(taskRepository.findNextBoardOrder(project.getId(), statusCode));
		return taskRepository.saveAndFlush(task);
	}

	private String taskBody(String title, Long assigneeId) {
		return """
				{
				  "title": "%s",
				  "priority": "HIGH",
				  "dueAt": "%s",
				  "assigneeId": %d
				}
				""".formatted(title, LocalDateTime.now().plusDays(30), assigneeId);
	}
}
