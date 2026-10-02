package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class KanbanControllerIntegrationTests {

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
	void ownerReordersWithinOneColumnAndKeepsDenseZeroBasedOrders() throws Exception {
		var owner = createUser("kanban-owner-same@example.com", "Kanban Owner");
		var project = createProject(owner, "Same column");
		createTask(project, owner, null, "A", TaskStatusCode.TODO, 0);
		createTask(project, owner, null, "B", TaskStatusCode.TODO, 1);
		var taskC = createTask(project, owner, null, "C", TaskStatusCode.TODO, 2);

		move(project, taskC, owner, "TODO", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("TODO"))
				.andExpect(jsonPath("$.boardOrder").value(0));

		assertColumn(project, TaskStatusCode.TODO, "C", "A", "B");
	}

	@Test
	void ownerMovesAcrossColumnsAndCompactsBothColumns() throws Exception {
		var owner = createUser("kanban-owner-cross@example.com", "Kanban Owner");
		var project = createProject(owner, "Cross columns");
		createTask(project, owner, null, "A", TaskStatusCode.TODO, 0);
		var taskB = createTask(project, owner, null, "B", TaskStatusCode.TODO, 1);
		createTask(project, owner, null, "C", TaskStatusCode.TODO, 2);
		createTask(project, owner, null, "D", TaskStatusCode.IN_PROGRESS, 0);
		createTask(project, owner, null, "E", TaskStatusCode.IN_PROGRESS, 1);

		move(project, taskB, owner, "IN_PROGRESS", 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.boardOrder").value(1));

		assertColumn(project, TaskStatusCode.TODO, "A", "C");
		assertColumn(project, TaskStatusCode.IN_PROGRESS, "D", "B", "E");
	}

	@Test
	void workflowTransitionsMustFollowAdjacentColumnOrder() throws Exception {
		var owner = createUser("kanban-owner-sequential@example.com", "Kanban Sequential Owner");
		var project = createProject(owner, "Sequential workflow");
		var task = createTask(project, owner, null, "Tuần tự", TaskStatusCode.TODO, 0);

		move(project, task, owner, "DONE", 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_STATUS_TRANSITION_NOT_ADJACENT"));
		assertColumn(project, TaskStatusCode.TODO, "Tuần tự");

		move(project, task, owner, "IN_PROGRESS", 0)
				.andExpect(status().isOk());
		move(project, task, owner, "DONE", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").isNotEmpty());
		assertColumn(project, TaskStatusCode.DONE, "Tuần tự");
	}

	@Test
	void oversizedPositionAppendsAndInvalidPositionIsRejected() throws Exception {
		var owner = createUser("kanban-owner-position@example.com", "Kanban Owner");
		var project = createProject(owner, "Position rules");
		var taskA = createTask(project, owner, null, "A", TaskStatusCode.TODO, 0);
		createTask(project, owner, null, "B", TaskStatusCode.TODO, 1);

		move(project, taskA, owner, "TODO", 999)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.boardOrder").value(1));
		assertColumn(project, TaskStatusCode.TODO, "B", "A");

		move(project, taskA, owner, "TODO", -1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.boardOrder").exists());

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/position",
					project.getId(), taskA.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.status").exists())
				.andExpect(jsonPath("$.fieldErrors.boardOrder").exists());

		assertColumn(project, TaskStatusCode.TODO, "B", "A");
	}

	@Test
	void assigneeCanMoveOwnTaskAndCompletedAtTracksDoneState() throws Exception {
		var owner = createUser("kanban-owner-done@example.com", "Kanban Owner");
		var assignee = createUser("kanban-assignee-done@example.com", "Kanban Assignee");
		var otherMember = createUser("kanban-other-done@example.com", "Kanban Other");
		var project = createProject(owner, "Done lifecycle", assignee, otherMember);
		var assignedTask = createTask(project, owner, assignee, "Assigned", TaskStatusCode.TODO, 0);
		createTask(project, owner, null, "Already done", TaskStatusCode.DONE, 0);

		move(project, assignedTask, assignee, "IN_PROGRESS", 0)
				.andExpect(status().isOk());

		var doneResult = move(project, assignedTask, assignee, "DONE", 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").isNotEmpty())
				.andReturn();
		String completedAt = JsonPath.read(doneResult.getResponse().getContentAsString(), "$.completedAt");

		move(project, assignedTask, assignee, "DONE", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").value(completedAt));

		move(project, assignedTask, assignee, "IN_PROGRESS", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").value(nullValue()));

		move(project, assignedTask, otherMember, "DONE", 0)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_REQUIRED"));

		assertColumn(project, TaskStatusCode.IN_PROGRESS, "Assigned");
	}

	@Test
	void legacyStatusMoveAppendsAndDeleteCompactsTheSourceColumn() throws Exception {
		var owner = createUser("kanban-owner-legacy@example.com", "Kanban Owner");
		var project = createProject(owner, "Legacy status");
		var taskA = createTask(project, owner, null, "A", TaskStatusCode.TODO, 0);
		var taskB = createTask(project, owner, null, "B", TaskStatusCode.TODO, 1);
		createTask(project, owner, null, "C", TaskStatusCode.TODO, 2);
		createTask(project, owner, null, "D", TaskStatusCode.IN_PROGRESS, 0);

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status",
					project.getId(), taskB.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.boardOrder").value(1));

		assertColumn(project, TaskStatusCode.TODO, "A", "C");
		assertColumn(project, TaskStatusCode.IN_PROGRESS, "D", "B");

		mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}",
					project.getId(), taskA.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());

		assertColumn(project, TaskStatusCode.TODO, "C");
	}

	@Test
	void moveProtectsProjectScopeArchiveStateAndCsrf() throws Exception {
		var owner = createUser("kanban-owner-guard@example.com", "Kanban Owner");
		var outsider = createUser("kanban-outsider-guard@example.com", "Kanban Outsider");
		var project = createProject(owner, "Protected board");
		var otherProject = createProject(outsider, "Other board");
		var task = createTask(project, owner, null, "Protected", TaskStatusCode.TODO, 0);
		var otherTask = createTask(otherProject, outsider, null, "Other", TaskStatusCode.TODO, 0);

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/position",
					project.getId(), task.getId())
					.with(user(owner.getEmail()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"DONE\",\"boardOrder\":0}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		move(project, task, outsider, "DONE", 0)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		move(project, otherTask, owner, "DONE", 0)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		move(project, task, owner, "DONE", 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		assertColumn(project, TaskStatusCode.TODO, "Protected");
	}

	private User createUser(String email, String fullName) {
		return userRepository.saveAndFlush(new User(
				email,
				passwordEncoder.encode("test-password"),
				fullName));
	}

	private Project createProject(User owner, String name, User... members) {
		var project = projectRepository.saveAndFlush(new Project(owner, name));
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
			TaskStatusCode statusCode,
			long boardOrder) {
		var status = taskStatusRepository.findByCode(statusCode).orElseThrow();
		var task = new Task(project, creator, status, title, LocalDateTime.now().plusDays(1));
		task.setPriority(TaskPriority.MEDIUM);
		task.setAssignee(assignee);
		task.setBoardOrder(boardOrder);
		if (statusCode == TaskStatusCode.DONE) {
			task.setCompletedAt(LocalDateTime.now().minusMinutes(5));
		}
		return taskRepository.saveAndFlush(task);
	}

	private org.springframework.test.web.servlet.ResultActions move(
			Project project,
			Task task,
			User actor,
			String statusCode,
			long boardOrder) throws Exception {
		return mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/position",
				project.getId(), task.getId())
				.with(user(actor.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + statusCode + "\",\"boardOrder\":" + boardOrder + "}"));
	}

	private void assertColumn(Project project, TaskStatusCode statusCode, String... titles) {
		var tasks = taskRepository.findActiveTasksInStatus(project.getId(), statusCode);
		assertThat(tasks)
				.extracting(Task::getTitle)
				.containsExactly(titles);
		assertThat(tasks)
				.extracting(Task::getBoardOrder)
				.containsExactly(java.util.stream.LongStream.range(0, titles.length)
						.boxed()
						.toArray(Long[]::new));
	}
}
