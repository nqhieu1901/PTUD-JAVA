package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import com.example.smarttask.repository.TaskAttachmentRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskAttachmentControllerIntegrationTests {

	private static final Path TEST_STORAGE = Path.of("target/test-attachments");

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
	private TaskAttachmentRepository taskAttachmentRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@AfterEach
	void cleanStoredFiles() throws IOException {
		if (!Files.exists(TEST_STORAGE)) {
			return;
		}
		try (var paths = Files.walk(TEST_STORAGE)) {
			for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	@Test
	void ownerUploadsAndViewerCanListAndDownload() throws Exception {
		var owner = createUser("attachment-owner-read@example.com", "Attachment Owner");
		var viewer = createUser("attachment-viewer-read@example.com", "Attachment Viewer");
		var project = createProject(owner, "Attachment read");
		addMembership(project, viewer, ProjectRole.VIEWER);
		var task = createTask(project, owner, "Task with plan");
		var bytes = "Nội dung kế hoạch".getBytes();

		var upload = upload(project, task, owner,
				new MockMultipartFile("file", "ke-hoach.txt", "text/plain", bytes))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.projectId").value(project.getId()))
				.andExpect(jsonPath("$.taskId").value(task.getId()))
				.andExpect(jsonPath("$.originalName").value("ke-hoach.txt"))
				.andExpect(jsonPath("$.contentType").value("text/plain"))
				.andExpect(jsonPath("$.sizeBytes").value(bytes.length))
				.andExpect(jsonPath("$.uploadedBy.id").value(owner.getId()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn();
		var attachmentId = ((Number) JsonPath.read(
				upload.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get(attachmentPath(project, task)).with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(attachmentId));

		mockMvc.perform(get(attachmentPath(project, task) + "/{id}/download", attachmentId)
					.with(user(viewer.getEmail())))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						org.hamcrest.Matchers.containsString("attachment")))
				.andExpect(header().longValue("Content-Length", bytes.length))
				.andExpect(content().contentType("text/plain"))
				.andExpect(content().bytes(bytes));

		assertThat(taskAttachmentRepository.findAllForTask(project.getId(), task.getId()))
				.hasSize(1);
	}

	@Test
	void uploaderOrOwnerCanDeleteButAnotherMemberCannot() throws Exception {
		var owner = createUser("attachment-owner-delete@example.com", "Delete Owner");
		var uploader = createUser("attachment-uploader@example.com", "File Uploader");
		var otherMember = createUser("attachment-other@example.com", "Other Member");
		var project = createProject(owner, "Attachment delete");
		addMembership(project, uploader, ProjectRole.MEMBER);
		addMembership(project, otherMember, ProjectRole.MEMBER);
		var task = createTask(project, owner, "Delete attachment task");
		var attachmentId = uploadedAttachmentId(project, task, uploader, "member.csv");

		mockMvc.perform(delete(attachmentPath(project, task) + "/{id}", attachmentId)
					.with(user(otherMember.getEmail()))
					.with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ATTACHMENT_DELETE_FORBIDDEN"));
		assertThat(taskAttachmentRepository.findById(attachmentId)).isPresent();

		mockMvc.perform(delete(attachmentPath(project, task) + "/{id}", attachmentId)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());
		assertThat(taskAttachmentRepository.findById(attachmentId)).isEmpty();
	}

	@Test
	void uploadRequiresAuthenticationCsrfWriteRoleAndSupportedFile() throws Exception {
		var owner = createUser("attachment-owner-validation@example.com", "Validation Owner");
		var viewer = createUser("attachment-viewer-validation@example.com", "Validation Viewer");
		var project = createProject(owner, "Attachment validation");
		addMembership(project, viewer, ProjectRole.VIEWER);
		var task = createTask(project, owner, "Validation task");
		var validFile = new MockMultipartFile("file", "document.pdf", "application/pdf", "pdf".getBytes());

		mockMvc.perform(multipart(attachmentPath(project, task)).file(validFile).with(csrf()))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(multipart(attachmentPath(project, task))
					.file(validFile)
					.with(user(owner.getEmail())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		upload(project, task, viewer, validFile)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_VIEWER_READ_ONLY"));

		upload(project, task, owner,
				new MockMultipartFile("file", "run.exe", "application/octet-stream", "bad".getBytes()))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("ATTACHMENT_TYPE_NOT_ALLOWED"));

		upload(project, task, owner,
				new MockMultipartFile("file", "../secret.pdf", "application/pdf", "bad".getBytes()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ATTACHMENT_NAME_INVALID"));

		upload(project, task, owner,
				new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ATTACHMENT_EMPTY"));

		upload(project, task, owner,
				new MockMultipartFile("file", "too-large.pdf", "application/pdf",
						new byte[10 * 1024 * 1024 + 1]))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("ATTACHMENT_TOO_LARGE"));

		assertThat(taskAttachmentRepository.findAllForTask(project.getId(), task.getId())).isEmpty();
	}

	@Test
	void archivedProjectIsReadOnlyAndOutsidersCannotDiscoverFiles() throws Exception {
		var owner = createUser("attachment-owner-archive@example.com", "Archive Owner");
		var outsider = createUser("attachment-outsider@example.com", "Archive Outsider");
		var project = createProject(owner, "Attachment archive");
		var task = createTask(project, owner, "Archived attachment task");
		var attachmentId = uploadedAttachmentId(project, task, owner, "archive.txt");
		project.setStatus(ProjectStatus.ARCHIVED);
		project.setArchivedAt(LocalDateTime.now());
		projectRepository.saveAndFlush(project);

		mockMvc.perform(get(attachmentPath(project, task)).with(user(owner.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(attachmentId));

		upload(project, task, owner,
				new MockMultipartFile("file", "blocked.txt", "text/plain", "blocked".getBytes()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(delete(attachmentPath(project, task) + "/{id}", attachmentId)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));

		mockMvc.perform(get(attachmentPath(project, task)).with(user(outsider.getEmail())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
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

	private org.springframework.test.web.servlet.ResultActions upload(
			Project project,
			Task task,
			User actor,
			MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart(attachmentPath(project, task))
				.file(file)
				.with(user(actor.getEmail()))
				.with(csrf()));
	}

	private long uploadedAttachmentId(
			Project project,
			Task task,
			User actor,
			String fileName) throws Exception {
		var result = upload(project, task, actor,
				new MockMultipartFile("file", fileName, MediaType.TEXT_PLAIN_VALUE, "content".getBytes()))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private String attachmentPath(Project project, Task task) {
		return "/api/projects/%d/tasks/%d/attachments".formatted(project.getId(), task.getId());
	}
}
