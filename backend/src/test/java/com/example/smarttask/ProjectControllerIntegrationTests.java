package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
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
class ProjectControllerIntegrationTests {

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
	void createProjectAddsCreatorAsTheOnlyOwner() throws Exception {
		var owner = createUser("owner-create@example.com", "Chủ dự án");

		var result = mockMvc.perform(post("/api/projects")
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name": " Website quản lý công việc ",
							  "description": " Đồ án Java ",
							  "startDate": "2026-08-15",
							  "dueDate": "2026-12-31"
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Website quản lý công việc"))
				.andExpect(jsonPath("$.description").value("Đồ án Java"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.currentUserRole").value("OWNER"))
				.andExpect(jsonPath("$.createdBy.id").value(owner.getId()))
				.andReturn();

		Number projectIdValue = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		var projectId = projectIdValue.longValue();
		var memberships = projectMemberRepository.findAllForProject(projectId);
		assertThat(memberships).hasSize(1);
		assertThat(memberships.getFirst().getUser().getId()).isEqualTo(owner.getId());
		assertThat(memberships.getFirst().getProjectRole()).isEqualTo(ProjectRole.OWNER);
	}

	@Test
	void listAndReadExposeOnlyProjectsWhereTheUserIsAMember() throws Exception {
		var owner = createUser("owner-list@example.com", "Owner List");
		var member = createUser("member-list@example.com", "Member List");
		var outsider = createUser("outsider-list@example.com", "Outsider List");
		var visibleProject = createProject(owner, "Dự án được tham gia");
		addMembership(visibleProject, member);
		createProject(outsider, "Dự án riêng của người ngoài");

		mockMvc.perform(get("/api/projects").with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(visibleProject.getId()))
				.andExpect(jsonPath("$[0].currentUserRole").value("MEMBER"));

		mockMvc.perform(get("/api/projects/{projectId}", visibleProject.getId())
					.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentUserRole").value("MEMBER"));

		mockMvc.perform(get("/api/projects/{projectId}/members", visibleProject.getId())
					.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(get("/api/projects/{projectId}", visibleProject.getId())
					.with(user(outsider.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

	@Test
	void ownerCanAddAndRemoveAnOrdinaryMember() throws Exception {
		var owner = createUser("owner-members@example.com", "Owner Members");
		var member = createUser("target-member@example.com", "Target Member");
		var project = createProject(owner, "Quản lý thành viên");

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":" TARGET-MEMBER@EXAMPLE.COM "}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(member.getId()))
				.andExpect(jsonPath("$.role").value("MEMBER"));

		mockMvc.perform(get("/api/projects/{projectId}", project.getId())
					.with(user(member.getEmail())))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}",
					project.getId(), member.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/projects/{projectId}", project.getId())
					.with(user(member.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"ADMIN", "LOCKED"})
	void cannotAddAnAdminOrLockedAccountToAProject(String kind) throws Exception {
		var owner = createUser("owner-unavailable@example.com", "Chủ dự án");
		var candidate = createUser("candidate-unavailable@example.com", "Tài khoản không hợp lệ");
		if (kind.equals("ADMIN")) candidate.setSystemRole(SystemRole.ADMIN);
		else candidate.setAccountStatus(AccountStatus.LOCKED);
		userRepository.saveAndFlush(candidate);
		var project = createProject(owner, "Kiểm tra thành viên");
		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
				.with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + candidate.getEmail() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_UNAVAILABLE"));
		assertThat(projectMemberRepository.findMembership(project.getId(), candidate.getId())).isEmpty();
	}

	@Test
	void memberCannotPerformOwnerMutations() throws Exception {
		var owner = createUser("owner-permission@example.com", "Owner Permission");
		var member = createUser("member-permission@example.com", "Member Permission");
		var candidate = createUser("candidate-permission@example.com", "Candidate Permission");
		var project = createProject(owner, "Kiểm tra phân quyền");
		addMembership(project, member);

		mockMvc.perform(put("/api/projects/{projectId}", project.getId())
					.with(user(member.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validProjectBody("Tên bị sửa")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));

		mockMvc.perform(post("/api/projects/{projectId}/archive", project.getId())
					.with(user(member.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(member.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + candidate.getEmail() + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));

		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}",
					project.getId(), owner.getId())
					.with(user(member.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));
	}

	@Test
	void addingDuplicateOrUnknownMemberReturnsAStableError() throws Exception {
		var owner = createUser("owner-add-errors@example.com", "Owner Add Errors");
		var member = createUser("member-add-errors@example.com", "Member Add Errors");
		var project = createProject(owner, "Lỗi thành viên");
		addMembership(project, member);

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ALREADY_EXISTS"));

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"unknown@example.com\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

		assertThat(projectMemberRepository.findAllForProject(project.getId())).hasSize(2);
	}

	@Test
	void ownerAndAssignedMemberCannotBeRemoved() throws Exception {
		var owner = createUser("owner-remove@example.com", "Owner Remove");
		var member = createUser("assigned-remove@example.com", "Assigned Remove");
		var project = createProject(owner, "Ràng buộc gỡ thành viên");
		addMembership(project, member);

		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}",
					project.getId(), owner.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_CANNOT_BE_REMOVED"));

		var todo = taskStatusRepository.findByCode(TaskStatusCode.TODO).orElseThrow();
		var task = new Task(
				project,
				owner,
				todo,
				"Công việc đang được giao",
				LocalDateTime.now().plusDays(1));
		task.setAssignee(member);
		taskRepository.saveAndFlush(task);

		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}",
					project.getId(), member.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_HAS_TASKS"));
	}

	@Test
	void ownerCanArchiveAndRestoreProjectIdempotently() throws Exception {
		var owner = createUser("owner-archive@example.com", "Owner Archive");
		var candidate = createUser("candidate-archive@example.com", "Candidate Archive");
		var project = createProject(owner, "Tên ban đầu");

		mockMvc.perform(put("/api/projects/{projectId}", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validProjectBody("Tên đã cập nhật")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Tên đã cập nhật"));

		var firstArchive = mockMvc.perform(post("/api/projects/{projectId}/archive", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ARCHIVED"))
				.andExpect(jsonPath("$.archivedAt").isNotEmpty())
				.andReturn();
		String firstArchivedAt = JsonPath.read(
				firstArchive.getResponse().getContentAsString(),
				"$.archivedAt");

		mockMvc.perform(post("/api/projects/{projectId}/archive", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.archivedAt").value(firstArchivedAt));

		mockMvc.perform(put("/api/projects/{projectId}", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validProjectBody("Không được cập nhật")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + candidate.getEmail() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(post("/api/projects/{projectId}/restore", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.archivedAt").value(nullValue()));

		mockMvc.perform(post("/api/projects/{projectId}/restore", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.archivedAt").value(nullValue()));

		mockMvc.perform(put("/api/projects/{projectId}", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(validProjectBody("Tên sau khi khôi phục")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Tên sau khi khôi phục"));

		mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title": "Task sau khi khôi phục",
							  "dueAt": "2026-12-30T17:00:00"
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.projectId").value(project.getId()))
				.andExpect(jsonPath("$.status.code").value("TODO"));
	}

	@Test
	void restoreRequiresOwnerMembershipAndCsrf() throws Exception {
		var owner = createUser("owner-restore-access@example.com", "Owner Restore Access");
		var member = createUser("member-restore-access@example.com", "Member Restore Access");
		var outsider = createUser("outsider-restore-access@example.com", "Outsider Restore Access");
		var project = createProject(owner, "Restore Access");
		addMembership(project, member);
		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		mockMvc.perform(post("/api/projects/{projectId}/restore", project.getId())
					.with(user(owner.getEmail())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		mockMvc.perform(post("/api/projects/{projectId}/restore", project.getId())
					.with(user(member.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));

		mockMvc.perform(post("/api/projects/{projectId}/restore", project.getId())
					.with(user(outsider.getEmail()))
					.with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		assertThat(projectRepository.findById(project.getId()).orElseThrow().getStatus())
				.isEqualTo(ProjectStatus.ARCHIVED);
	}

	@Test
	void projectRequestsRequireAuthenticationAndValidInput() throws Exception {
		var owner = createUser("owner-validation@example.com", "Owner Validation");

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(post("/api/projects")
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.name").exists());

		mockMvc.perform(post("/api/projects")
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name": "Sai ngày",
							  "startDate": "2026-12-31",
							  "dueDate": "2026-01-01"
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PROJECT_DATES"));

		mockMvc.perform(post("/api/projects")
					.with(user(owner.getEmail()))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Sai định dạng ngày\",\"dueDate\":\"not-a-date\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void lockedAccountCannotKeepUsingAnExistingSession() throws Exception {
		var account = createUser("locked-session@example.com", "Locked Session");
		var loginResult = mockMvc.perform(post("/api/auth/login")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "locked-session@example.com",
							  "password": "test-password"
							}
							"""))
				.andExpect(status().isOk())
				.andReturn();
		var session = (MockHttpSession) loginResult.getRequest().getSession(false);

		account.setAccountStatus(AccountStatus.LOCKED);
		account.setLockedAt(LocalDateTime.now());
		userRepository.saveAndFlush(account);

		mockMvc.perform(get("/api/projects").session(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	private User createUser(String email, String fullName) {
		return userRepository.saveAndFlush(new User(
				email,
				passwordEncoder.encode("test-password"),
				fullName));
	}

	private Project createProject(User owner, String name) {
		var project = new Project(owner, name);
		projectRepository.saveAndFlush(project);
		projectMemberRepository.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
		return project;
	}

	private void addMembership(Project project, User member) {
		projectMemberRepository.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
	}

	private String validProjectBody(String name) {
		return """
				{
				  "name": "%s",
				  "description": "Mô tả cập nhật",
				  "startDate": "2026-08-15",
				  "dueDate": "2026-12-31"
				}
				""".formatted(name);
	}
}
