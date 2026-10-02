package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskPriority;
import com.example.smarttask.domain.TaskStatusCode;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskControllerIntegrationTests {

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
	private PasswordEncoder passwordEncoder;

	@Test
	void ownerCreatesTaskWithDefaultsAndMemberCanReadIt() throws Exception {
		var owner = createUser("task-owner-create@example.com", "Task Owner");
		var member = createUser("task-member-create@example.com", "Task Member");
		var project = createProject(owner, "Task Create", member);

		var result = mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title": " Thiết kế API Task ",
							  "description": " Viết REST API ",
							  "dueAt": "2026-08-20T17:00:00",
							  "estimatedMinutes": 120,
							  "assigneeId": %d
							}
							""".formatted(member.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Thiết kế API Task"))
				.andExpect(jsonPath("$.description").value("Viết REST API"))
				.andExpect(jsonPath("$.priority").value("MEDIUM"))
				.andExpect(jsonPath("$.status.code").value("TODO"))
				.andExpect(jsonPath("$.assignee.id").value(member.getId()))
				.andExpect(jsonPath("$.createdBy.id").value(owner.getId()))
				.andExpect(jsonPath("$.boardOrder").value(0))
				.andExpect(jsonPath("$.completedAt").value(nullValue()))
				.andReturn();

		Number taskIdValue = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		var taskId = taskIdValue.longValue();
		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}", project.getId(), taskId)
					.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(taskId));

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(member.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validTaskBody("Member không được tạo", member.getId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));
	}

	@Test
	void ownerCreatesUpdatesAndClearsTaskParent() throws Exception {
		var owner = createUser("task-owner-parent@example.com", "Parent Owner");
		var project = createProject(owner, "Task Parent");
		var firstParent = createTask(
				project, owner, null, "Parent one", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0);

		var createResult = mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Child task", firstParent.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentTaskId").value(firstParent.getId()))
				.andReturn();
		Number childIdValue = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
		var childId = childIdValue.longValue();
		assertThat(taskRepository.findById(childId).orElseThrow().getParentTask().getId())
				.isEqualTo(firstParent.getId());

		var secondParent = createTask(
				project, owner, null, "Parent two", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 2);
		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), childId)
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Child moved", secondParent.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Child moved"))
				.andExpect(jsonPath("$.parentTaskId").value(secondParent.getId()));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), childId)
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Child without parent", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentTaskId").value(nullValue()));
	}

	@Test
	void parentMustBeAnActiveTaskInTheSameProject() throws Exception {
		var owner = createUser("task-owner-parent-scope@example.com", "Parent Scope Owner");
		var project = createProject(owner, "Parent Scope");
		var otherProject = createProject(owner, "Other Parent Scope");
		var crossProjectParent = createTask(
				otherProject, owner, null, "Other project parent", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0);
		var deletedParent = createTask(
				project, owner, null, "Deleted parent", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0);
		deletedParent.setDeletedAt(LocalDateTime.now());
		taskRepository.saveAndFlush(deletedParent);

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Cross project child", crossProjectParent.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_PARENT_NOT_FOUND"));

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Deleted parent child", deletedParent.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_PARENT_NOT_FOUND"));

		var task = createTask(
				project, owner, null, "Existing task", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 1);
		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Unknown parent", Long.MAX_VALUE)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_PARENT_NOT_FOUND"));
	}

	@Test
	void updateRejectsSelfParentAndAncestorCycles() throws Exception {
		var owner = createUser("task-owner-parent-cycle@example.com", "Parent Cycle Owner");
		var project = createProject(owner, "Parent Cycle");
		var root = createTask(
				project, owner, null, "Root", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0);
		var child = createTask(
				project, owner, null, "Child", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 1);
		child.setParentTask(root);
		var grandchild = createTask(
				project, owner, null, "Grandchild", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 2);
		grandchild.setParentTask(child);
		taskRepository.saveAllAndFlush(java.util.List.of(child, grandchild));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), child.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Self parent", child.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_PARENT_CYCLE"));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), root.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBodyWithParent("Cycle root", grandchild.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_PARENT_CYCLE"));

		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}", project.getId(), root.getId())
					.with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentTaskId").value(nullValue()));
	}

	@Test
	void listCombinesSearchStatusPriorityAssigneeAndWholeDayDeadline() throws Exception {
		var owner = createUser("task-owner-filter@example.com", "Filter Owner");
		var member = createUser("task-member-filter@example.com", "Filter Member");
		var otherOwner = createUser("task-other-filter@example.com", "Other Filter Owner");
		var project = createProject(owner, "Task Filter", member);
		var otherProject = createProject(otherOwner, "Other Task Filter");

		var target = createTask(
				project, owner, member, "Backend chính", "Triển khai Website API", TaskPriority.HIGH,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 23, 59, 59), 0);
		createTask(
				project, owner, member, "Website tài liệu", null, TaskPriority.LOW,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 12, 0), 1);
		createTask(
				project, owner, member, "API thanh toán", null, TaskPriority.HIGH,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 13, 0), 2);
		createTask(
				project, owner, member, "Website ngày sau", null, TaskPriority.HIGH,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 8, 16, 0, 0), 3);
		createTask(
				otherProject, otherOwner, otherOwner, "Website API", null, TaskPriority.HIGH,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 10, 0), 0);
		var deleted = createTask(
				project, owner, member, "Website đã xóa", null, TaskPriority.HIGH,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 11, 0), 4);
		deleted.setDeletedAt(LocalDateTime.now());
		deleted.setAssignee(null);
		taskRepository.saveAndFlush(deleted);

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(member.getEmail()))
					.param("q", "wEbSiTe")
					.param("status", "IN_PROGRESS")
					.param("priority", "HIGH")
					.param("assigneeId", member.getId().toString())
					.param("dueFrom", "2026-08-15")
					.param("dueTo", "2026-08-15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(target.getId()));
	}

	@Test
	void listUsesStableKanbanStatusOrder() throws Exception {
		var owner = createUser("task-owner-order@example.com", "Order Owner");
		var project = createProject(owner, "Task Order");
		createTask(project, owner, null, "Done", null, TaskPriority.MEDIUM,
				TaskStatusCode.DONE, LocalDateTime.now().plusDays(3), 0);
		createTask(project, owner, null, "Doing", null, TaskPriority.MEDIUM,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.now().plusDays(2), 0);
		createTask(project, owner, null, "Todo", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(1), 0);

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status.code").value("TODO"))
				.andExpect(jsonPath("$[1].status.code").value("IN_PROGRESS"))
				.andExpect(jsonPath("$[2].status.code").value("DONE"));
	}

	@Test
	void outsiderAndCrossProjectTaskAreHidden() throws Exception {
		var owner = createUser("task-owner-scope@example.com", "Scope Owner");
		var outsider = createUser("task-outsider-scope@example.com", "Scope Outsider");
		var project = createProject(owner, "Visible Scope");
		var outsiderProject = createProject(outsider, "Hidden Scope");
		var outsiderTask = createTask(
				outsiderProject, outsider, null, "Hidden task", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(1), 0);

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(outsider.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}",
					project.getId(), outsiderTask.getId())
					.with(user(owner.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
	}

	@Test
	void assigneeCanMoveStatusAndCompletedAtFollowsTheTerminalState() throws Exception {
		var owner = createUser("task-owner-status@example.com", "Status Owner");
		var assignee = createUser("task-assignee-status@example.com", "Status Assignee");
		var otherMember = createUser("task-other-status@example.com", "Status Other");
		var project = createProject(owner, "Task Status", assignee, otherMember);
		var task = createTask(
				project, owner, assignee, "Move status", null, TaskPriority.HIGH,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(1), 0);

		updateStatus(project, task, assignee, "IN_PROGRESS")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("IN_PROGRESS"));

		var doneResult = updateStatus(project, task, assignee, "DONE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("DONE"))
				.andExpect(jsonPath("$.completedAt").isNotEmpty())
				.andReturn();
		String completedAt = JsonPath.read(doneResult.getResponse().getContentAsString(), "$.completedAt");

		updateStatus(project, task, assignee, "DONE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").value(completedAt));

		updateStatus(project, task, assignee, "IN_PROGRESS")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.completedAt").value(nullValue()));

		updateStatus(project, task, otherMember, "DONE")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_REQUIRED"));
	}

	@Test
	void ownerUpdatesTaskAndInvalidAssigneeOrDatesAreRejected() throws Exception {
		var owner = createUser("task-owner-update@example.com", "Update Owner");
		var member = createUser("task-member-update@example.com", "Update Member");
		var outsider = createUser("task-outsider-update@example.com", "Update Outsider");
		var project = createProject(owner, "Task Update", member);
		var task = createTask(
				project, owner, member, "Before update", null, TaskPriority.LOW,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(1), 0);

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title": "After update",
							  "priority": "URGENT",
							  "startAt": "2026-08-20T08:00:00",
							  "dueAt": "2026-08-20T17:00:00",
							  "estimatedMinutes": 90,
							  "assigneeId": null
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("After update"))
				.andExpect(jsonPath("$.priority").value("URGENT"))
				.andExpect(jsonPath("$.assignee").value(nullValue()));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validTaskBody("Invalid assignee", outsider.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_NOT_MEMBER"));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title": "Invalid dates",
							  "startAt": "2026-08-21T17:00:00",
							  "dueAt": "2026-08-20T17:00:00"
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TASK_DATES"));
	}

	@Test
	void deleteIsOwnerOnlySoftAndReleasesTheAssigneeMembership() throws Exception {
		var owner = createUser("task-owner-delete@example.com", "Delete Owner");
		var member = createUser("task-member-delete@example.com", "Delete Member");
		var project = createProject(owner, "Task Delete", member);
		var task = createTask(
				project, owner, member, "Soft delete", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(1), 0);

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(member.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validTaskBody("Member cannot update", member.getId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));

		mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(member.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));

		mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());

		var deletedTask = taskRepository.findById(task.getId()).orElseThrow();
		assertThat(deletedTask.getDeletedAt()).isNotNull();
		assertThat(deletedTask.getAssignee()).isNull();

		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}", project.getId(), member.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());
	}

	@Test
	void archivedProjectIsReadableButAllTaskMutationsAreBlocked() throws Exception {
		var owner = createUser("task-owner-archive@example.com", "Archive Task Owner");
		var member = createUser("task-member-archive@example.com", "Archive Task Member");
		var project = createProject(owner, "Archived Tasks", member);
		var task = createTask(
				project, owner, member, "Archived task", null, TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(1), 0);
		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validTaskBody("Blocked create", member.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validTaskBody("Blocked update", member.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		updateStatus(project, task, member, "DONE")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void cannotAssignTasksToAnAdminEvenWithLegacyProjectMembership() throws Exception {
		var owner = createUser("owner-legacy-admin@example.com", "Chủ dự án");
		var admin = createUser("legacy-admin@example.com", "Admin");
		admin.setSystemRole(com.example.smarttask.domain.SystemRole.ADMIN);
		userRepository.saveAndFlush(admin);
		var project = createProject(owner, "Kiểm tra phân công Admin", admin);
		var initialCount = taskRepository.count();
		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
				.with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(validTaskBody("Không giao cho Admin", admin.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_UNAVAILABLE"));
		assertThat(taskRepository.count()).isEqualTo(initialCount);
	}

	@Test
	void taskValidationAndMalformedFiltersReturnStableErrors() throws Exception {
		var owner = createUser("task-owner-validation@example.com", "Validation Task Owner");
		var project = createProject(owner, "Task Validation");

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\":\"   \",\"estimatedMinutes\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.title").exists())
				.andExpect(jsonPath("$.fieldErrors.dueAt").exists())
				.andExpect(jsonPath("$.fieldErrors.estimatedMinutes").exists());

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.param("dueFrom", "2026-08-20")
					.param("dueTo", "2026-08-19"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TASK_FILTER"));

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.param("status", "BOGUS"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.param("dueFrom", "not-a-date"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

		var oversizedDescription = "x".repeat(16001);
		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title": "Oversized description",
							  "description": "%s",
							  "dueAt": "2026-08-20T17:00:00"
							}
							""".formatted(oversizedDescription)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.description").exists());
	}

	private User createUser(String email, String fullName) {
		return userRepository.saveAndFlush(new User(
				email,
				passwordEncoder.encode("test-password"),
				fullName));
	}

	private Project createProject(User owner, String name, User... members) {
		var project = new Project(owner, name);
		projectRepository.saveAndFlush(project);
		projectMemberRepository.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
		for (var member : members) {
			projectMemberRepository.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
		}
		return project;
	}

	private Task createTask(
			Project project,
			User creator,
			User assignee,
			String title,
			String description,
			TaskPriority priority,
			TaskStatusCode statusCode,
			LocalDateTime dueAt,
			long boardOrder) {
		var status = taskStatusRepository.findByCode(statusCode).orElseThrow();
		var task = new Task(project, creator, status, title, dueAt);
		task.setDescription(description);
		task.setPriority(priority);
		task.setAssignee(assignee);
		task.setBoardOrder(boardOrder);
		return taskRepository.saveAndFlush(task);
	}

	private org.springframework.test.web.servlet.ResultActions updateStatus(
			Project project,
			Task task,
			User actor,
			String statusCode) throws Exception {
		return mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status",
				project.getId(), task.getId())
				.with(user(actor.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + statusCode + "\"}"));
	}

	private String validTaskBody(String title, Long assigneeId) {
		return """
				{
				  "title": "%s",
				  "description": "Task hợp lệ",
				  "priority": "HIGH",
				  "startAt": "2026-08-20T08:00:00",
				  "dueAt": "2026-08-20T17:00:00",
				  "estimatedMinutes": 60,
				  "assigneeId": %d
				}
				""".formatted(title, assigneeId);
	}

	private String taskBodyWithParent(String title, Long parentTaskId) {
		var parentValue = parentTaskId == null ? "null" : parentTaskId.toString();
		return """
				{
				  "title": "%s",
				  "dueAt": "2026-08-20T17:00:00",
				  "parentTaskId": %s
				}
				""".formatted(title, parentValue);
	}
}
