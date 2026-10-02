package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskStatusCode;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskCommentRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskCommentControllerIntegrationTests {

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
	private TaskCommentRepository taskCommentRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void ownerAndMemberCreateTrimmedCommentsAndParticipantsReadThemChronologically() throws Exception {
		var owner = createUser("comment-owner-order@example.com", "Comment Owner");
		var member = createUser("comment-member-order@example.com", "Comment Member");
		var viewer = createUser("comment-viewer-order@example.com", "Comment Viewer");
		var project = createProject(owner, "Comment order");
		addMembership(project, member, ProjectRole.MEMBER);
		addMembership(project, viewer, ProjectRole.VIEWER);
		var task = createTask(project, owner, "Commented task");

		var firstId = createComment(project, task, owner, "  Bình luận đầu tiên  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.projectId").value(project.getId()))
				.andExpect(jsonPath("$.taskId").value(task.getId()))
				.andExpect(jsonPath("$.content").value("Bình luận đầu tiên"))
				.andExpect(jsonPath("$.author.id").value(owner.getId()))
				.andExpect(jsonPath("$.author.email").value(owner.getEmail()))
				.andExpect(jsonPath("$.author.fullName").value(owner.getFullName()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn();
		var firstCommentId = ((Number) JsonPath.read(
				firstId.getResponse().getContentAsString(), "$.id")).longValue();

		var secondResult = createComment(project, task, member, "Bình luận thứ hai")
				.andExpect(status().isCreated())
				.andReturn();
		var secondCommentId = ((Number) JsonPath.read(
				secondResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get(commentPath(project, task)).with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(firstCommentId))
				.andExpect(jsonPath("$[1].id").value(secondCommentId));
	}

	@Test
	void commentsRequireAuthenticationCsrfAndValidContent() throws Exception {
		var owner = createUser("comment-owner-validation@example.com", "Validation Owner");
		var project = createProject(owner, "Comment validation");
		var task = createTask(project, owner, "Validation task");

		mockMvc.perform(get(commentPath(project, task)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(post(commentPath(project, task))
					.with(user(owner.getEmail()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"Không có CSRF\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		createComment(project, task, owner, "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.content").exists());

		createComment(project, task, owner, "a".repeat(4001))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.content").exists());

		assertThat(taskCommentRepository.findAllForTask(project.getId(), task.getId())).isEmpty();
	}

	@Test
	void viewerCannotCreateAndOutsiderCannotListComments() throws Exception {
		var owner = createUser("comment-owner-permission@example.com", "Permission Owner");
		var viewer = createUser("comment-viewer-permission@example.com", "Permission Viewer");
		var outsider = createUser("comment-outsider-permission@example.com", "Permission Outsider");
		var project = createProject(owner, "Comment permission");
		addMembership(project, viewer, ProjectRole.VIEWER);
		var task = createTask(project, owner, "Permission task");

		createComment(project, task, viewer, "Viewer comment")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));

		mockMvc.perform(get(commentPath(project, task)).with(user(outsider.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

	@Test
	void memberDemotedToViewerCannotDeleteAnEarlierComment() throws Exception {
		var owner = createUser("comment-owner-demote@example.com", "Demote Owner");
		var member = createUser("comment-member-demote@example.com", "Demote Member");
		var project = createProject(owner, "Comment demotion");
		addMembership(project, member, ProjectRole.MEMBER);
		var task = createTask(project, owner, "Demotion task");
		var commentId = createdCommentId(project, task, member, "Before becoming viewer");
		var membership = projectMemberRepository
				.findMembership(project.getId(), member.getId())
				.orElseThrow();
		membership.setProjectRole(ProjectRole.VIEWER);
		projectMemberRepository.saveAndFlush(membership);

		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", commentId)
					.with(user(member.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));
		assertThat(taskCommentRepository.findById(commentId)).isPresent();
	}

	@Test
	void onlyCommentAuthorOrProjectOwnerCanDelete() throws Exception {
		var owner = createUser("comment-owner-delete@example.com", "Delete Owner");
		var author = createUser("comment-author-delete@example.com", "Delete Author");
		var otherMember = createUser("comment-other-delete@example.com", "Delete Other");
		var project = createProject(owner, "Comment delete");
		addMembership(project, author, ProjectRole.MEMBER);
		addMembership(project, otherMember, ProjectRole.MEMBER);
		var task = createTask(project, owner, "Delete task");

		var firstCommentId = createdCommentId(project, task, author, "Author comment");
		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", firstCommentId)
					.with(user(otherMember.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("COMMENT_DELETE_FORBIDDEN"));

		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", firstCommentId)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());
		assertThat(taskCommentRepository.findById(firstCommentId)).isEmpty();

		var ownCommentId = createdCommentId(project, task, author, "Author removes this");
		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", ownCommentId)
					.with(user(author.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", Long.MAX_VALUE)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
	}

	@Test
	void archivedProjectKeepsCommentsReadableButBlocksCreateAndDelete() throws Exception {
		var owner = createUser("comment-owner-archive@example.com", "Archive Owner");
		var project = createProject(owner, "Comment archive");
		var task = createTask(project, owner, "Archive task");
		var commentId = createdCommentId(project, task, owner, "Comment before archive");
		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		mockMvc.perform(get(commentPath(project, task)).with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(commentId));

		createComment(project, task, owner, "Blocked after archive")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", commentId)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void softDeletedAndCrossProjectTasksAreHiddenFromCommentEndpoints() throws Exception {
		var owner = createUser("comment-owner-task-scope@example.com", "Scope Owner");
		var project = createProject(owner, "Comment task scope");
		var task = createTask(project, owner, "Deleted task");
		var commentId = createdCommentId(project, task, owner, "Hidden comment");
		task.setDeletedAt(LocalDateTime.now());
		taskRepository.saveAndFlush(task);

		mockMvc.perform(get(commentPath(project, task)).with(user(owner.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		createComment(project, task, owner, "Comment on deleted task")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		mockMvc.perform(delete(commentPath(project, task) + "/{commentId}", commentId)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		var otherProject = createProject(owner, "Other comment scope");
		mockMvc.perform(get(commentPath(otherProject, task)).with(user(owner.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
	}

	private User createUser(String email, String fullName) {
		return userRepository.saveAndFlush(new User(
				email,
				passwordEncoder.encode("test-password"),
				fullName));
	}

	private Project createProject(User owner, String name) {
		var project = projectRepository.saveAndFlush(new Project(owner, name));
		projectMemberRepository.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
		return project;
	}

	private void addMembership(Project project, User user, ProjectRole role) {
		projectMemberRepository.saveAndFlush(new ProjectMember(project, user, role));
	}

	private Task createTask(Project project, User creator, String title) {
		var todo = taskStatusRepository.findByCode(TaskStatusCode.TODO).orElseThrow();
		return taskRepository.saveAndFlush(new Task(
				project,
				creator,
				todo,
				title,
				LocalDateTime.now().plusDays(1)));
	}

	private org.springframework.test.web.servlet.ResultActions createComment(
			Project project,
			Task task,
			User actor,
			String content) throws Exception {
		return mockMvc.perform(post(commentPath(project, task))
				.with(user(actor.getEmail()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"" + content + "\"}"));
	}

	private long createdCommentId(Project project, Task task, User actor, String content) throws Exception {
		var result = createComment(project, task, actor, content)
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private String commentPath(Project project, Task task) {
		return "/api/projects/%d/tasks/%d/comments".formatted(project.getId(), task.getId());
	}
}
