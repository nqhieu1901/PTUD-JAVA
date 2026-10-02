package com.example.smarttask.attachment;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskAttachment;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskAttachmentRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class TaskAttachmentService {

	private static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
			Map.entry("pdf", "application/pdf"),
			Map.entry("doc", "application/msword"),
			Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			Map.entry("xls", "application/vnd.ms-excel"),
			Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
			Map.entry("ppt", "application/vnd.ms-powerpoint"),
			Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
			Map.entry("txt", "text/plain"),
			Map.entry("csv", "text/csv"),
			Map.entry("md", "text/markdown"),
			Map.entry("jpg", "image/jpeg"),
			Map.entry("jpeg", "image/jpeg"),
			Map.entry("png", "image/png"),
			Map.entry("gif", "image/gif"),
			Map.entry("webp", "image/webp"),
			Map.entry("zip", "application/zip"));

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskRepository taskRepository;
	private final TaskAttachmentRepository taskAttachmentRepository;
	private final AttachmentStorage attachmentStorage;
	private final TaskAttachmentLifecycleService lifecycleService;
	private final long maxSizeBytes;

	public TaskAttachmentService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskRepository taskRepository,
			TaskAttachmentRepository taskAttachmentRepository,
			AttachmentStorage attachmentStorage,
			TaskAttachmentLifecycleService lifecycleService,
			@Value("${smarttask.attachments.max-size-bytes}") long maxSizeBytes) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskRepository = taskRepository;
		this.taskAttachmentRepository = taskAttachmentRepository;
		this.attachmentStorage = attachmentStorage;
		this.lifecycleService = lifecycleService;
		this.maxSizeBytes = maxSizeBytes;
	}

	@Transactional(readOnly = true)
	public List<TaskAttachmentResponse> findAll(
			String currentUserEmail,
			Long projectId,
			Long taskId) {
		requireReadAccess(currentUserEmail, projectId);
		requireTask(projectId, taskId);
		return taskAttachmentRepository.findAllForTask(projectId, taskId).stream()
				.map(TaskAttachmentResponse::from)
				.toList();
	}

	@Transactional
	public TaskAttachmentResponse upload(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			MultipartFile file) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		var task = requireTask(projectId, taskId);
		var fileInfo = validate(file);
		var storedName = UUID.randomUUID() + "." + fileInfo.extension();

		attachmentStorage.store(file, storedName);
		try {
			lifecycleService.deleteUploadedFileAfterRollback(storedName);
		}
		catch (RuntimeException exception) {
			attachmentStorage.deleteQuietly(storedName);
			throw exception;
		}
		var attachment = new TaskAttachment(
				access.project(),
				task,
				access.user(),
				fileInfo.originalName(),
				storedName,
				fileInfo.contentType(),
				file.getSize());
		return TaskAttachmentResponse.from(taskAttachmentRepository.saveAndFlush(attachment));
	}

	@Transactional(readOnly = true)
	public AttachmentDownload download(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			Long attachmentId) {
		requireReadAccess(currentUserEmail, projectId);
		requireTask(projectId, taskId);
		var attachment = requireAttachment(projectId, taskId, attachmentId);
		return new AttachmentDownload(
				attachmentStorage.load(attachment.getStoredName()),
				attachment.getOriginalName(),
				attachment.getContentType(),
				attachment.getSizeBytes());
	}

	@Transactional
	public void delete(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			Long attachmentId) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireTask(projectId, taskId);
		var attachment = requireAttachment(projectId, taskId, attachmentId);
		if (access.role() != ProjectRole.OWNER
				&& !attachment.getUploadedBy().getId().equals(access.user().getId())) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"ATTACHMENT_DELETE_FORBIDDEN",
					"Chỉ người tải lên hoặc chủ dự án mới có thể xóa tệp");
		}
		lifecycleService.deleteAttachment(attachment);
	}

	private FileInfo validate(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() <= 0) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"ATTACHMENT_EMPTY",
					"Vui lòng chọn một tệp có nội dung");
		}
		if (file.getSize() > maxSizeBytes) {
			throw new ApiException(
					HttpStatus.PAYLOAD_TOO_LARGE,
					"ATTACHMENT_TOO_LARGE",
					"Tệp đính kèm không được vượt quá 10 MB");
		}

		var originalName = file.getOriginalFilename() == null
				? ""
				: file.getOriginalFilename().trim();
		if (originalName.isBlank() || originalName.length() > 255
				|| originalName.contains("/") || originalName.contains("\\")
				|| originalName.contains("..")
				|| originalName.chars().anyMatch(Character::isISOControl)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"ATTACHMENT_NAME_INVALID",
					"Tên tệp không hợp lệ");
		}

		var separator = originalName.lastIndexOf('.');
		var extension = separator < 1 || separator == originalName.length() - 1
				? ""
				: originalName.substring(separator + 1).toLowerCase(Locale.ROOT);
		var contentType = ALLOWED_TYPES.get(extension);
		if (contentType == null) {
			throw new ApiException(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"ATTACHMENT_TYPE_NOT_ALLOWED",
					"Định dạng tệp không được hỗ trợ");
		}
		return new FileInfo(originalName, extension, contentType);
	}

	private ProjectAccess requireReadAccess(String currentUserEmail, Long projectId) {
		var user = requireCurrentUser(currentUserEmail);
		var membership = projectMemberRepository.findMembership(projectId, user.getId())
				.orElseThrow(this::projectNotFound);
		return new ProjectAccess(user, membership.getProject(), membership.getProjectRole());
	}

	private ProjectAccess requireMutationAccess(String currentUserEmail, Long projectId) {
		var user = requireCurrentUser(currentUserEmail);
		var project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(this::projectNotFound);
		var membership = projectMemberRepository.findMembership(projectId, user.getId())
				.orElseThrow(this::projectNotFound);
		if (membership.getProjectRole() != ProjectRole.OWNER
				&& membership.getProjectRole() != ProjectRole.MEMBER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"PROJECT_VIEWER_READ_ONLY",
					"Vai trò hiện tại chỉ được xem dự án");
		}
		if (project.getStatus() == ProjectStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_ARCHIVED",
					"Dự án đã đóng và không thể chỉnh sửa");
		}
		return new ProjectAccess(user, project, membership.getProjectRole());
	}

	private User requireCurrentUser(String email) {
		var user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						HttpStatus.UNAUTHORIZED,
						"UNAUTHORIZED",
						"Cần đăng nhập để tiếp tục"));
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"ACCOUNT_LOCKED",
					"Tài khoản đã bị khóa");
		}
		return user;
	}

	private Task requireTask(Long projectId, Long taskId) {
		return taskRepository.findActiveTask(projectId, taskId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"TASK_NOT_FOUND",
						"Không tìm thấy công việc"));
	}

	private TaskAttachment requireAttachment(Long projectId, Long taskId, Long attachmentId) {
		return taskAttachmentRepository.findForTask(projectId, taskId, attachmentId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"ATTACHMENT_NOT_FOUND",
						"Không tìm thấy tệp đính kèm"));
	}

	private ApiException projectNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"PROJECT_NOT_FOUND",
				"Không tìm thấy dự án");
	}

	private record FileInfo(String originalName, String extension, String contentType) {
	}

	private record ProjectAccess(User user, Project project, ProjectRole role) {
	}
}
