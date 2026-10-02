package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.smarttask.attachment.TaskAttachmentLifecycleService;
import com.example.smarttask.attachment.TaskAttachmentService;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskAttachment;
import com.example.smarttask.domain.TaskStatusCode;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskAttachmentRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AttachmentLifecycleIntegrationTests {

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
	private TaskAttachmentService taskAttachmentService;

	@Autowired
	private TaskAttachmentLifecycleService lifecycleService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@AfterEach
	void cleanStoredFiles() throws IOException {
		taskAttachmentRepository.deleteAllInBatch();
		taskRepository.deleteAllInBatch();
		projectMemberRepository.deleteAllInBatch();
		projectRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();

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
	void rolledBackUploadRemovesTheStoredFileAndMetadata() {
		var owner = createUser("attachment-rollback-owner@example.com", "Rollback Owner");
		var project = createProject(owner, "Attachment rollback");
		var task = createTask(project, owner, "Rollback upload task");
		var transaction = new TransactionTemplate(transactionManager);

		transaction.executeWithoutResult(status -> {
			taskAttachmentService.upload(
					owner.getEmail(),
					project.getId(),
					task.getId(),
					new MockMultipartFile("file", "rollback.txt", "text/plain", "rollback".getBytes()));
			status.setRollbackOnly();
		});

		assertThat(taskAttachmentRepository.findAllByTask_Id(task.getId())).isEmpty();
		assertThat(storedFiles()).isEmpty();
	}

	@Test
	void committedAttachmentAndTaskDeletesRemoveMetadataAndFiles() throws Exception {
		var owner = createUser("attachment-commit-owner@example.com", "Commit Owner");
		var project = createProject(owner, "Attachment commit delete");
		var firstTask = createTask(project, owner, "Delete one attachment");
		var firstAttachment = upload(project, firstTask, owner, "one.txt");
		var firstStoredName = taskAttachmentRepository.findById(firstAttachment).orElseThrow().getStoredName();
		assertThat(TEST_STORAGE.resolve(firstStoredName)).exists();

		mockMvc.perform(delete(attachmentPath(project, firstTask) + "/{id}", firstAttachment)
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());
		assertThat(taskAttachmentRepository.findById(firstAttachment)).isEmpty();
		assertThat(TEST_STORAGE.resolve(firstStoredName)).doesNotExist();

		var secondTask = createTask(project, owner, "Delete task attachments");
		var secondAttachment = upload(project, secondTask, owner, "two.txt");
		var thirdAttachment = upload(project, secondTask, owner, "three.txt");
		var taskStoredNames = taskAttachmentRepository.findAllByTask_Id(secondTask.getId()).stream()
				.map(TaskAttachment::getStoredName)
				.toList();
		assertThat(taskStoredNames).hasSize(2);

		mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}", project.getId(), secondTask.getId())
					.with(user(owner.getEmail()))
					.with(csrf()))
				.andExpect(status().isNoContent());
		assertThat(taskAttachmentRepository.findById(secondAttachment)).isEmpty();
		assertThat(taskAttachmentRepository.findById(thirdAttachment)).isEmpty();
		assertThat(taskStoredNames).allSatisfy(storedName ->
				assertThat(TEST_STORAGE.resolve(storedName)).doesNotExist());
	}

	@Test
	void reconciliationRemovesMissingMetadataAndOnlyOldOrphanFiles() throws IOException {
		var owner = createUser("attachment-reconcile-owner@example.com", "Reconcile Owner");
		var project = createProject(owner, "Attachment reconciliation");
		var task = createTask(project, owner, "Reconcile task");
		var missingAttachment = taskAttachmentRepository.saveAndFlush(new TaskAttachment(
				project,
				task,
				owner,
				"missing.txt",
				"missing-content.txt",
				"text/plain",
				8));
		Files.createDirectories(TEST_STORAGE);
		var now = Instant.now();
		var oldOrphan = TEST_STORAGE.resolve("old-orphan.txt");
		var freshOrphan = TEST_STORAGE.resolve("fresh-orphan.txt");
		Files.writeString(oldOrphan, "old");
		Files.setLastModifiedTime(oldOrphan, FileTime.from(now.minusSeconds(7200)));
		Files.writeString(freshOrphan, "fresh");

		var result = lifecycleService.reconcile(now);

		assertThat(result.missingMetadataRemoved()).isEqualTo(1);
		assertThat(result.orphanFilesScheduled()).isEqualTo(1);
		assertThat(taskAttachmentRepository.findById(missingAttachment.getId())).isEmpty();
		assertThat(oldOrphan).doesNotExist();
		assertThat(freshOrphan).exists();
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

	private Task createTask(Project project, User creator, String title) {
		var todo = taskStatusRepository.findByCode(TaskStatusCode.TODO).orElseThrow();
		return taskRepository.saveAndFlush(new Task(
				project,
				creator,
				todo,
				title,
				LocalDateTime.now().plusDays(1)));
	}

	private long upload(Project project, Task task, User actor, String fileName) throws Exception {
		var result = mockMvc.perform(multipart(attachmentPath(project, task))
					.file(new MockMultipartFile(
							"file", fileName, MediaType.TEXT_PLAIN_VALUE, "content".getBytes()))
					.with(user(actor.getEmail()))
					.with(csrf()))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private String attachmentPath(Project project, Task task) {
		return "/api/projects/%d/tasks/%d/attachments".formatted(project.getId(), task.getId());
	}

	private java.util.List<Path> storedFiles() {
		if (!Files.isDirectory(TEST_STORAGE)) {
			return java.util.List.of();
		}
		try (var paths = Files.list(TEST_STORAGE)) {
			return paths.filter(Files::isRegularFile).toList();
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
