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

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
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
class ViewerRoleIntegrationTests {

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
	void addingMemberDefaultsToMemberAndOwnerCannotBeSupplied() throws Exception {
		var owner = createUser("viewer-default-owner@example.com", "Viewer Default Owner");
		var member = createUser("viewer-default-member@example.com", "Viewer Default Member");
		var rejectedOwner = createUser("viewer-rejected-owner@example.com", "Rejected Owner");
		var project = createProject(owner, "Viewer default role");

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("MEMBER"));

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"%s","role":"OWNER"}
							""".formatted(rejectedOwner.getEmail())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ROLE_INVALID"));

		assertThat(projectMemberRepository.findAllForProject(project.getId()))
				.extracting(ProjectMember::getProjectRole)
				.containsExactly(ProjectRole.OWNER, ProjectRole.MEMBER);
	}

	@Test
	void ownerCanAddViewerAndChangeOrdinaryMemberRole() throws Exception {
		var owner = createUser("viewer-role-owner@example.com", "Viewer Role Owner");
		var viewer = createUser("viewer-role-viewer@example.com", "Role Viewer");
		var member = createUser("viewer-role-member@example.com", "Role Member");
		var project = createProject(owner, "Viewer role management");
		addMembership(project, member, ProjectRole.MEMBER);

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"%s","role":"VIEWER"}
							""".formatted(viewer.getEmail())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(viewer.getId()))
				.andExpect(jsonPath("$.role").value("VIEWER"));

		mockMvc.perform(patch("/api/projects/{projectId}/members/{userId}/role",
					project.getId(), member.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"role\":\"VIEWER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("VIEWER"));

		mockMvc.perform(patch("/api/projects/{projectId}/members/{userId}/role",
					project.getId(), owner.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"role\":\"MEMBER\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_ROLE_CANNOT_BE_CHANGED"));

		mockMvc.perform(patch("/api/projects/{projectId}/members/{userId}/role",
					project.getId(), viewer.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"role\":\"OWNER\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ROLE_INVALID"));

		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		mockMvc.perform(patch("/api/projects/{projectId}/members/{userId}/role",
					project.getId(), viewer.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"role\":\"MEMBER\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void viewerCanReadProjectsTasksMembersAndNotifications() throws Exception {
		var owner = createUser("viewer-read-owner@example.com", "Viewer Read Owner");
		var viewer = createUser("viewer-read-user@example.com", "Viewer Read User");
		var project = createProject(owner, "Viewer read access");
		var task = createTask(project, owner, null, "Task visible to viewer");

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"%s","role":"VIEWER"}
							""".formatted(viewer.getEmail())))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/projects").with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(project.getId()))
				.andExpect(jsonPath("$[0].currentUserRole").value("VIEWER"));

		mockMvc.perform(get("/api/projects/{projectId}", project.getId())
					.with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentUserRole").value("VIEWER"));

		mockMvc.perform(get("/api/projects/{projectId}/members", project.getId())
					.with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.userId == %s)].role".formatted(viewer.getId()))
						.value("VIEWER"));

		mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId())
					.with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(task.getId()));

		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}",
					project.getId(), task.getId())
					.with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Task visible to viewer"));

		mockMvc.perform(get("/api/notifications").with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].type").value("PROJECT_MEMBER_ADDED"))
				.andExpect(jsonPath("$[0].projectId").value(project.getId()));
	}

	@Test
	void viewerCannotMutateProjectTasksMembersOrAiEvenWhenAssignedInLegacyData() throws Exception {
		var owner = createUser("viewer-deny-owner@example.com", "Viewer Deny Owner");
		var viewer = createUser("viewer-deny-user@example.com", "Viewer Deny User");
		var candidate = createUser("viewer-deny-candidate@example.com", "Viewer Deny Candidate");
		var project = createProject(owner, "Viewer mutation denial");
		addMembership(project, viewer, ProjectRole.VIEWER);
		var task = createTask(project, owner, viewer, "Legacy viewer assignment");

		mockMvc.perform(put("/api/projects/{projectId}", project.getId())
					.with(user(viewer.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validProjectBody()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(viewer.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + candidate.getEmail() + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status",
					project.getId(), task.getId())
					.with(user(viewer.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));

		mockMvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/ai/decompositions",
					project.getId(), task.getId())
					.with(user(viewer.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"maxSuggestions\":2}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));
	}

	@Test
	void viewerCannotBeAssignedAndAssignedMemberCannotBecomeViewer() throws Exception {
		var owner = createUser("viewer-assign-owner@example.com", "Viewer Assign Owner");
		var member = createUser("viewer-assign-member@example.com", "Viewer Assign Member");
		var viewer = createUser("viewer-assign-viewer@example.com", "Viewer Assign Viewer");
		var project = createProject(owner, "Viewer assignment rules");
		addMembership(project, member, ProjectRole.MEMBER);
		addMembership(project, viewer, ProjectRole.VIEWER);

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Cannot assign viewer", viewer.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_VIEWER_NOT_ALLOWED"));

		var createdTask = mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Member assignment", member.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		Number taskIdValue = JsonPath.read(
				createdTask.getResponse().getContentAsString(),
				"$.id");
		var taskId = taskIdValue.longValue();

		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}",
					project.getId(), taskId)
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(taskBody("Cannot reassign viewer", viewer.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_VIEWER_NOT_ALLOWED"));

		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status",
					project.getId(), taskId)
					.with(user(member.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("IN_PROGRESS"));

		mockMvc.perform(patch("/api/projects/{projectId}/members/{userId}/role",
					project.getId(), member.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"role\":\"VIEWER\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_HAS_ASSIGNED_TASKS"));

		assertThat(projectMemberRepository.findMembership(project.getId(), member.getId()))
				.get()
				.extracting(ProjectMember::getProjectRole)
				.isEqualTo(ProjectRole.MEMBER);
	}

	private User createUser(String email, String fullName) {
		return userRepository.saveAndFlush(new User(
				email,
				passwordEncoder.encode("test-password"),
				fullName));
	}

	private Project createProject(User owner, String name) {
		var project = projectRepository.saveAndFlush(new Project(owner, name));
		addMembership(project, owner, ProjectRole.OWNER);
		return project;
	}

	private void addMembership(Project project, User member, ProjectRole role) {
		projectMemberRepository.saveAndFlush(new ProjectMember(project, member, role));
	}

	private Task createTask(Project project, User creator, User assignee, String title) {
		var todo = taskStatusRepository.findByCode(TaskStatusCode.TODO).orElseThrow();
		var task = new Task(project, creator, todo, title, LocalDateTime.now().plusDays(2));
		task.setAssignee(assignee);
		return taskRepository.saveAndFlush(task);
	}

	private String taskBody(String title, Long assigneeId) {
		return """
				{
				  "title": "%s",
				  "dueAt": "2026-12-30T17:00:00",
				  "assigneeId": %d
				}
				""".formatted(title, assigneeId);
	}

	private String validProjectBody() {
		return """
				{
				  "name": "Viewer cannot update",
				  "startDate": "2026-08-15",
				  "dueDate": "2026-12-31"
				}
				""";
	}
}
