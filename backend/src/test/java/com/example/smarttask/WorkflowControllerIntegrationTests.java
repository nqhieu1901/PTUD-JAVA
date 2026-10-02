package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.workflow.WorkflowStatusService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Transactional
class WorkflowControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectMemberRepository projectMemberRepository;

	@Autowired
	private TaskStatusRepository taskStatusRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private WorkflowStatusService workflowStatusService;

	@Test
	void everyMemberCanReadTheDefaultProjectWorkflow() throws Exception {
		var owner = createUser("workflow-owner@example.com", "Chủ dự án");
		var member = createUser("workflow-member@example.com", "Thành viên");
		var viewer = createUser("workflow-viewer@example.com", "Người xem");
		var project = createProject(owner, member, viewer);

		for (var actor : new User[] { owner, member, viewer }) {
			mockMvc.perform(get("/api/projects/{projectId}/workflow", project.getId())
					.with(user(actor.getEmail())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(3))
					.andExpect(jsonPath("$[0].code").value("TODO"))
					.andExpect(jsonPath("$[2].terminal").value(true));
		}
	}

	@Test
	void ownerCanAddRenameAndReorderColumnsThenMoveATaskToTheNewColumn() throws Exception {
		var owner = createUser("workflow-edit@example.com", "Chủ chỉnh workflow");
		var project = createProject(owner);
		var statuses = taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(project.getId());
		var todo = statuses.get(0);
		var inProgress = statuses.get(1);
		var done = statuses.get(2);
		var task = taskRepository.saveAndFlush(new Task(
				project, owner, todo, "Kiểm thử quy trình", LocalDateTime.now().plusDays(1)));

		var response = mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"statuses":[
						  {"id":%d,"displayName":"Danh sách chờ","terminal":false},
						  {"id":null,"displayName":"Kiểm thử","terminal":false},
						  {"id":%d,"displayName":"Đang làm","terminal":false},
						  {"id":%d,"displayName":"Hoàn tất","terminal":true}
						]}
						""".formatted(todo.getId(), inProgress.getId(), done.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].displayName").value("Danh sách chờ"))
				.andExpect(jsonPath("$[1].displayName").value("Kiểm thử"))
				.andReturn();

		var workflow = objectMapper.readTree(response.getResponse().getContentAsString());
		var customCode = workflow.get(1).get("code").asText();
		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/position",
				project.getId(), task.getId())
				.with(user(owner.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + customCode + "\",\"boardOrder\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value(customCode))
				.andExpect(jsonPath("$.status.displayName").value("Kiểm thử"));
	}

	@Test
	void onlyTheOwnerCanReplaceAnActiveWorkflow() throws Exception {
		var owner = createUser("workflow-permission-owner@example.com", "Chủ quyền");
		var member = createUser("workflow-permission-member@example.com", "Thành viên quyền");
		var viewer = createUser("workflow-permission-viewer@example.com", "Người xem quyền");
		var project = createProject(owner, member, viewer);
		var body = currentWorkflowBody(project);

		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(member.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(viewer.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());

		project.setStatus(ProjectStatus.ARCHIVED);
		projectRepository.saveAndFlush(project);
		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void workflowRequiresUniqueNamesAndExactlyOneTerminalColumn() throws Exception {
		var owner = createUser("workflow-validation@example.com", "Chủ kiểm tra");
		var project = createProject(owner);

		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"statuses":[
						  {"displayName":"Trùng","terminal":false},
						  {"displayName":" trùng ","terminal":true}
						]}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WORKFLOW_STATUS_NAME_DUPLICATE"));

		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"statuses":[
						  {"displayName":"Một","terminal":false},
						  {"displayName":"Hai","terminal":false}
						]}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WORKFLOW_TERMINAL_STATUS_REQUIRED"));
	}

	@Test
	void aColumnWithTasksCannotBeRemoved() throws Exception {
		var owner = createUser("workflow-in-use@example.com", "Chủ có task");
		var project = createProject(owner);
		var statuses = taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(project.getId());
		var task = new Task(project, owner, statuses.get(0), "Không được mất cột", LocalDateTime.now().plusDays(1));
		taskRepository.saveAndFlush(task);

		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"statuses":[
						  {"id":%d,"displayName":"Đang làm","terminal":false},
						  {"id":%d,"displayName":"Xong","terminal":true}
						]}
						""".formatted(statuses.get(1).getId(), statuses.get(2).getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WORKFLOW_STATUS_IN_USE"));
	}

	@Test
	void removingColumnsWithOnlyDeletedTasksPreservesTheirHistoryAndForeignKeys() throws Exception {
		var owner = createUser("workflow-deleted@example.com", "Chủ cột đã xóa task");
		var project = createProject(owner);
		var statuses = taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(project.getId());
		var deletedAt = LocalDateTime.now().withNano(0);
		var completedAt = deletedAt.minusHours(1);
		var deletedTask = new Task(project, owner, statuses.get(2), "Lưu lịch sử", deletedAt.plusDays(1));
		deletedTask.setDeletedAt(deletedAt);
		deletedTask.setCompletedAt(completedAt);
		taskRepository.saveAndFlush(deletedTask);

		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"statuses":[
						  {"displayName":"Bắt đầu mới","terminal":false},
						  {"displayName":"Kết thúc mới","terminal":true}
						]}
						"""))
				.andExpect(status().isOk());

		taskRepository.flush();
		var history = taskRepository.findById(deletedTask.getId()).orElseThrow();
		assertThat(history.getTitle()).isEqualTo("Lưu lịch sử");
		assertThat(history.getDeletedAt()).isEqualTo(deletedAt);
		assertThat(history.getCompletedAt()).isEqualTo(completedAt);
		assertThat(history.getStatus().getDisplayName()).isEqualTo("Bắt đầu mới");
		assertThat(taskRepository.findActiveTask(project.getId(), history.getId())).isEmpty();
		for (var removedStatus : statuses) {
			assertThat(taskStatusRepository.existsById(removedStatus.getId())).isFalse();
		}
	}

	@Test
	void changingTheTerminalColumnUpdatesTaskCompletionState() throws Exception {
		var owner = createUser("workflow-terminal@example.com", "Chủ hoàn tất");
		var project = createProject(owner);
		var statuses = taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(project.getId());
		var task = taskRepository.saveAndFlush(new Task(
				project, owner, statuses.get(1), "Sẽ hoàn tất", LocalDateTime.now().plusDays(1)));

		mockMvc.perform(put("/api/projects/{projectId}/workflow", project.getId())
				.with(user(owner.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"statuses":[
						  {"id":%d,"displayName":"Cần làm","terminal":false},
						  {"id":%d,"displayName":"Hoàn tất mới","terminal":true},
						  {"id":%d,"displayName":"Đã duyệt","terminal":false}
						]}
						""".formatted(statuses.get(0).getId(), statuses.get(1).getId(), statuses.get(2).getId())))
				.andExpect(status().isOk());

		taskRepository.flush();
		assertThat(taskRepository.findById(task.getId()).orElseThrow().getCompletedAt()).isNotNull();
	}

	private String currentWorkflowBody(Project project) {
		var statuses = taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(project.getId());
		return """
				{"statuses":[
				  {"id":%d,"displayName":"Cần làm","terminal":false},
				  {"id":%d,"displayName":"Đang làm","terminal":false},
				  {"id":%d,"displayName":"Hoàn thành","terminal":true}
				]}
				""".formatted(statuses.get(0).getId(), statuses.get(1).getId(), statuses.get(2).getId());
	}

	private User createUser(String email, String name) {
		return userRepository.saveAndFlush(new User(email, passwordEncoder.encode("Password@123"), name));
	}

	private Project createProject(User owner, User... members) {
		var project = projectRepository.saveAndFlush(new Project(owner, "Workflow " + owner.getEmail()));
		projectMemberRepository.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
		for (var index = 0; index < members.length; index++) {
			var role = index == members.length - 1 && members.length > 1
					? ProjectRole.VIEWER
					: ProjectRole.MEMBER;
			projectMemberRepository.saveAndFlush(new ProjectMember(project, members[index], role));
		}
		workflowStatusService.createDefaults(project);
		return project;
	}
}
