package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AiDecisionStatus;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.AiRequestStatus;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskPriority;
import com.example.smarttask.domain.TaskStatusCode;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.AiRequestRepository;
import com.example.smarttask.repository.AiSuggestionRepository;
import com.example.smarttask.repository.NotificationRepository;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiControllerIntegrationTests {

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
	private AiRequestRepository aiRequestRepository;

	@Autowired
	private AiSuggestionRepository aiSuggestionRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void ownerGeneratesAndAcceptsSubtaskWithInheritedFieldsAtEndOfTodoColumn() throws Exception {
		var owner = createUser("ai-owner-generate@example.com", "AI Owner");
		var member = createUser("ai-member-generate@example.com", "AI Member");
		var project = createProject(owner, "AI Generate", member);
		createTask(project, owner, null, "TODO one", TaskPriority.LOW,
				TaskStatusCode.TODO, LocalDateTime.of(2026, 9, 1, 17, 0), 0, null);
		createTask(project, owner, null, "TODO two", TaskPriority.LOW,
				TaskStatusCode.TODO, LocalDateTime.of(2026, 9, 2, 17, 0), 1, null);
		var source = createTask(project, owner, member, "Tích hợp thanh toán", TaskPriority.URGENT,
				TaskStatusCode.IN_PROGRESS, LocalDateTime.of(2026, 9, 10, 17, 0), 0, 240);

		var generated = generate(project, source, owner, 4)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.requestType").value("TASK_DECOMPOSITION"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.sourceTaskId").value(source.getId()))
				.andExpect(jsonPath("$.generationMode").value("DEMO"))
				.andExpect(jsonPath("$.provider").value("demo"))
				.andExpect(jsonPath("$.modelName").value("demo-rules-v1"))
				.andExpect(jsonPath("$.notice", containsString("không gọi hoặc sử dụng mô hình AI")))
				.andExpect(jsonPath("$.suggestions.length()").value(4))
				.andExpect(jsonPath("$.suggestions[0].type").value("SUBTASK"))
				.andExpect(jsonPath("$.suggestions[0].decisionStatus").value("PENDING"))
				.andExpect(jsonPath("$.suggestions[0].proposed.title").isNotEmpty())
				.andExpect(jsonPath("$.suggestions[0].proposed.estimatedMinutes").value(60))
				.andExpect(jsonPath("$.suggestions[0].createdTask").value(nullValue()))
				.andReturn();

		Number requestIdValue = JsonPath.read(generated.getResponse().getContentAsString(), "$.requestId");
		Number suggestionIdValue = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[0].id");
		var requestId = requestIdValue.longValue();
		var suggestionId = suggestionIdValue.longValue();

		var persistedRequest = aiRequestRepository.findById(requestId).orElseThrow();
		assertThat(persistedRequest.getRequestStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
		assertThat(persistedRequest.getRawResponse()).contains("DEMO", "demo-rules-v1");
		assertThat(aiSuggestionRepository.findAllByRequest_IdOrderByIdAsc(requestId)).hasSize(4);

		mockMvc.perform(get(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions/latest",
					project.getId(), source.getId())
				.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").value(requestId));

		mockMvc.perform(post(
					"/api/projects/{projectId}/ai/suggestions/{suggestionId}/accept",
					project.getId(), suggestionId)
				.with(user(owner.getEmail()))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decisionStatus").value("ACCEPTED"))
				.andExpect(jsonPath("$.createdTask.parentTaskId").value(source.getId()))
				.andExpect(jsonPath("$.createdTask.priority").value("URGENT"))
				.andExpect(jsonPath("$.createdTask.status.code").value("TODO"))
				.andExpect(jsonPath("$.createdTask.assignee.id").value(member.getId()))
				.andExpect(jsonPath("$.createdTask.boardOrder").value(2));

		var accepted = aiSuggestionRepository.findById(suggestionId).orElseThrow();
		assertThat(accepted.getDecisionStatus()).isEqualTo(AiDecisionStatus.ACCEPTED);
		var createdTask = taskRepository.findById(accepted.getCreatedTask().getId()).orElseThrow();
		assertThat(createdTask.getParentTask().getId()).isEqualTo(source.getId());
		assertThat(createdTask.getPriority()).isEqualTo(source.getPriority());
		assertThat(createdTask.getDueAt()).isEqualTo(source.getDueAt());
		assertThat(createdTask.getAssignee().getId()).isEqualTo(member.getId());
		assertThat(createdTask.getBoardOrder()).isEqualTo(2);
		assertThat(notificationRepository.findAllByRecipient_IdOrderByCreatedAtDescIdDesc(member.getId()))
				.singleElement().satisfies(notification -> {
					assertThat(notification.getType().name()).isEqualTo("TASK_ASSIGNED");
					assertThat(notification.getTask().getId()).isEqualTo(createdTask.getId());
				});

		mockMvc.perform(post(
					"/api/projects/{projectId}/ai/suggestions/{suggestionId}/accept",
					project.getId(), suggestionId)
				.with(user(owner.getEmail()))
				.with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_SUGGESTION_ALREADY_DECIDED"));
	}

	@Test
	void memberCanReadButOnlyOwnerCanGenerateOrDecideAndOutsiderGetsNotFound() throws Exception {
		var owner = createUser("ai-owner-access@example.com", "Access Owner");
		var member = createUser("ai-member-access@example.com", "Access Member");
		var outsider = createUser("ai-outsider-access@example.com", "Access Outsider");
		var project = createProject(owner, "AI Access", member);
		var source = createTask(project, owner, member, "Access source", TaskPriority.HIGH,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(3), 0, 120);

		mockMvc.perform(get(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions/latest",
					project.getId(), source.getId())
				.with(user(member.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AI_REQUEST_NOT_FOUND"));

		generate(project, source, member, 3)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));
		generate(project, source, outsider, 3)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		var generated = generate(project, source, owner, 3)
				.andExpect(status().isCreated())
				.andReturn();
		Number suggestionIdValue = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[0].id");
		var suggestionId = suggestionIdValue.longValue();

		mockMvc.perform(get(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions/latest",
					project.getId(), source.getId())
				.with(user(member.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions.length()").value(3));
		mockMvc.perform(get(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions/latest",
					project.getId(), source.getId())
				.with(user(outsider.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		decide(project, suggestionId, member, "accept")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_OWNER_REQUIRED"));
		decide(project, suggestionId, outsider, "accept")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

	@Test
	void rejectIsFinalAndDoesNotCreateTask() throws Exception {
		var owner = createUser("ai-owner-reject@example.com", "Reject Owner");
		var project = createProject(owner, "AI Reject");
		var source = createTask(project, owner, null, "Reject source", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, null);
		var initialTaskCount = taskRepository.count();
		var generated = generate(project, source, owner, 2)
				.andExpect(status().isCreated())
				.andReturn();
		Number suggestionIdValue = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[0].id");
		var suggestionId = suggestionIdValue.longValue();

		decide(project, suggestionId, owner, "reject")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decisionStatus").value("REJECTED"))
				.andExpect(jsonPath("$.createdTask").value(nullValue()));
		assertThat(taskRepository.count()).isEqualTo(initialTaskCount);

		decide(project, suggestionId, owner, "reject")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_SUGGESTION_ALREADY_DECIDED"));
		decide(project, suggestionId, owner, "accept")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_SUGGESTION_ALREADY_DECIDED"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"LOCKED", "VIEWER", "ADMIN"})
	void acceptingRevalidatesTheInheritedAssigneeBeforeCreatingATask(String change) throws Exception {
		var owner = createUser("ai-assignee-owner@example.com", "Chủ AI");
		var member = createUser("ai-assignee-member@example.com", "Người nhận AI");
		var project = createProject(owner, "Kiểm tra người nhận AI", member);
		var source = createTask(project, owner, member, "Nguồn gợi ý", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 60);
		var generated = generate(project, source, owner, 2).andExpect(status().isCreated()).andReturn();
		Number suggestionId = JsonPath.read(generated.getResponse().getContentAsString(), "$.suggestions[0].id");
		var membership = projectMemberRepository.findMembership(project.getId(), member.getId()).orElseThrow();
		String expectedCode;
		if (!change.equals("VIEWER")) {
			if (change.equals("ADMIN")) member.setSystemRole(SystemRole.ADMIN);
			else member.setAccountStatus(AccountStatus.LOCKED);
			userRepository.saveAndFlush(member);
			expectedCode = "TASK_ASSIGNEE_UNAVAILABLE";
		}
		else {
			membership.setProjectRole(ProjectRole.VIEWER);
			projectMemberRepository.saveAndFlush(membership);
			expectedCode = "TASK_ASSIGNEE_VIEWER_NOT_ALLOWED";
		}
		var taskCount = taskRepository.count();
		decide(project, suggestionId.longValue(), owner, "accept")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(expectedCode));
		assertThat(taskRepository.count()).isEqualTo(taskCount);
		assertThat(aiSuggestionRepository.findById(suggestionId.longValue()).orElseThrow().getDecisionStatus())
				.isEqualTo(AiDecisionStatus.PENDING);
		assertThat(notificationRepository.findAllByRecipient_IdOrderByCreatedAtDescIdDesc(member.getId())).isEmpty();
	}

	@Test
	void acceptingAfterUnassignmentAndMemberRemovalDoesNotRestoreTheOldAssignee() throws Exception {
		var owner = createUser("ai-removal-owner@example.com", "Chủ AI");
		var member = createUser("ai-removal-member@example.com", "Người nhận AI");
		var project = createProject(owner, "Gỡ người nhận AI", member);
		var source = createTask(project, owner, member, "Nguồn gợi ý", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 60);
		var generated = generate(project, source, owner, 2).andExpect(status().isCreated()).andReturn();
		Number suggestionId = JsonPath.read(generated.getResponse().getContentAsString(), "$.suggestions[0].id");
		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}", project.getId(), member.getId())
				.with(user(owner.getEmail())).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_HAS_TASKS"));
		source.setAssignee(null);
		taskRepository.saveAndFlush(source);
		mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}", project.getId(), member.getId())
				.with(user(owner.getEmail())).with(csrf()))
				.andExpect(status().isNoContent());
		decide(project, suggestionId.longValue(), owner, "accept")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decisionStatus").value("ACCEPTED"))
				.andExpect(jsonPath("$.createdTask.assignee").value(nullValue()));
		assertThat(notificationRepository.findAllByRecipient_IdOrderByCreatedAtDescIdDesc(member.getId())).isEmpty();
	}

	@Test
	void invalidStoredSuggestionCannotBeAccepted() throws Exception {
		var owner = createUser("ai-owner-invalid@example.com", "Invalid Owner");
		var project = createProject(owner, "AI Invalid");
		var source = createTask(project, owner, null, "Invalid source", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 60);
		var generated = generate(project, source, owner, 2)
				.andExpect(status().isCreated())
				.andReturn();
		Number suggestionIdValue = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[0].id");
		var suggestionId = suggestionIdValue.longValue();
		var suggestion = aiSuggestionRepository.findById(suggestionId).orElseThrow();
		suggestion.setProposedData("{\"title\":\"   \",\"estimatedMinutes\":0}");
		aiSuggestionRepository.saveAndFlush(suggestion);
		var taskCount = taskRepository.count();

		decide(project, suggestionId, owner, "accept")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_SUGGESTION_INVALID"));
		assertThat(taskRepository.count()).isEqualTo(taskCount);
		assertThat(aiSuggestionRepository.findById(suggestionId).orElseThrow().getDecisionStatus())
				.isEqualTo(AiDecisionStatus.PENDING);
	}

	@Test
	void archivedProjectKeepsLatestReadableButBlocksAllAiMutations() throws Exception {
		var owner = createUser("ai-owner-archive@example.com", "Archive Owner");
		var member = createUser("ai-member-archive@example.com", "Archive Member");
		var project = createProject(owner, "AI Archive", member);
		var source = createTask(project, owner, member, "Archive source", TaskPriority.HIGH,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 90);
		var generated = generate(project, source, owner, 2)
				.andExpect(status().isCreated())
				.andReturn();
		Number firstSuggestionId = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[0].id");
		Number secondSuggestionId = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[1].id");
		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		mockMvc.perform(get(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions/latest",
					project.getId(), source.getId())
				.with(user(member.getEmail())))
				.andExpect(status().isOk());
		generate(project, source, owner, 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		decide(project, firstSuggestionId.longValue(), owner, "accept")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		decide(project, secondSuggestionId.longValue(), owner, "reject")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void suggestionCountMustBeBetweenTwoAndSix() throws Exception {
		var owner = createUser("ai-owner-count@example.com", "Count Owner");
		var project = createProject(owner, "AI Count");
		var source = createTask(project, owner, null, "Count source", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 60);

		generate(project, source, owner, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.maxSuggestions").exists());
		generate(project, source, owner, 7)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions",
					project.getId(), source.getId())
				.with(user(owner.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.maxSuggestions").exists());
	}

	@Test
	void suggestionIdsAreScopedToTheirProject() throws Exception {
		var firstOwner = createUser("ai-owner-scope-one@example.com", "Scope One");
		var secondOwner = createUser("ai-owner-scope-two@example.com", "Scope Two");
		var firstProject = createProject(firstOwner, "First AI Scope");
		var secondProject = createProject(secondOwner, "Second AI Scope");
		var firstTask = createTask(firstProject, firstOwner, null, "First source", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 60);
		var secondTask = createTask(secondProject, secondOwner, null, "Second source", TaskPriority.MEDIUM,
				TaskStatusCode.TODO, LocalDateTime.now().plusDays(2), 0, 60);
		var generated = generate(secondProject, secondTask, secondOwner, 2)
				.andExpect(status().isCreated())
				.andReturn();
		Number suggestionIdValue = JsonPath.read(
				generated.getResponse().getContentAsString(),
				"$.suggestions[0].id");

		decide(firstProject, suggestionIdValue.longValue(), firstOwner, "accept")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AI_SUGGESTION_NOT_FOUND"));
		generate(firstProject, secondTask, firstOwner, 2)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		mockMvc.perform(get(
					"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions/latest",
					firstProject.getId(), firstTask.getId())
				.with(user(firstOwner.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AI_REQUEST_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions generate(
			Project project,
			Task source,
			User actor,
			int maxSuggestions) throws Exception {
		return mockMvc.perform(post(
				"/api/projects/{projectId}/tasks/{taskId}/ai/decompositions",
				project.getId(), source.getId())
				.with(user(actor.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"maxSuggestions\":" + maxSuggestions + "}"));
	}

	private org.springframework.test.web.servlet.ResultActions decide(
			Project project,
			long suggestionId,
			User actor,
			String decision) throws Exception {
		return mockMvc.perform(post(
				"/api/projects/{projectId}/ai/suggestions/{suggestionId}/{decision}",
				project.getId(), suggestionId, decision)
				.with(user(actor.getEmail()))
				.with(csrf()));
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
			TaskPriority priority,
			TaskStatusCode statusCode,
			LocalDateTime dueAt,
			long boardOrder,
			Integer estimatedMinutes) {
		var status = taskStatusRepository.findByCode(statusCode).orElseThrow();
		var task = new Task(project, creator, status, title, dueAt);
		task.setPriority(priority);
		task.setAssignee(assignee);
		task.setBoardOrder(boardOrder);
		task.setEstimatedMinutes(estimatedMinutes);
		return taskRepository.saveAndFlush(task);
	}
}
