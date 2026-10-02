package com.example.smarttask.workflow;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.TaskStatus;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.TaskStatusRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class WorkflowStatusService {

	private static final List<DefaultStatus> DEFAULT_STATUSES = List.of(
			new DefaultStatus("TODO", "Cần làm", false),
			new DefaultStatus("IN_PROGRESS", "Đang thực hiện", false),
			new DefaultStatus("DONE", "Hoàn thành", true));

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskStatusRepository taskStatusRepository;
	private final TaskRepository taskRepository;

	public WorkflowStatusService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskStatusRepository taskStatusRepository,
			TaskRepository taskRepository) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskStatusRepository = taskStatusRepository;
		this.taskRepository = taskRepository;
	}

	@Transactional(readOnly = true)
	public List<WorkflowStatusResponse> findAll(String currentUserEmail, Long projectId) {
		requireMembership(currentUserEmail, projectId);
		return responses(projectId);
	}

	@Transactional
	public List<WorkflowStatusResponse> replace(
			String currentUserEmail,
			Long projectId,
			ReplaceWorkflowRequest request) {
		var access = requireOwnerForUpdate(currentUserEmail, projectId);
		validateWorkflow(request.statuses());
		createDefaults(access.project());

		var existing = taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(projectId);
		var existingById = new HashMap<Long, TaskStatus>();
		existing.forEach(status -> existingById.put(status.getId(), status));
		var retainedIds = new HashSet<Long>();
		var replacement = new java.util.ArrayList<TaskStatus>();

		for (var index = 0; index < request.statuses().size(); index++) {
			var item = request.statuses().get(index);
			TaskStatus status;
			if (item.id() == null) {
				status = new TaskStatus(
						access.project(),
						newStatusCode(),
						item.displayName().strip(),
						(short) (index + 1),
						item.terminal());
			}
			else {
				status = existingById.get(item.id());
				if (status == null || !retainedIds.add(item.id())) {
					throw invalidStatus();
				}
				if (status.isTerminal() != item.terminal()) {
					updateCompletionState(status, item.terminal());
				}
				status.setDisplayName(item.displayName().strip());
				status.setDisplayOrder((short) (index + 1));
				status.setTerminal(item.terminal());
			}
			replacement.add(status);
		}

		var removed = existing.stream()
				.filter(status -> !retainedIds.contains(status.getId()))
				.toList();
		for (var status : removed) {
			if (taskRepository.existsByStatus_IdAndDeletedAtIsNull(status.getId())) {
				throw new ApiException(
						HttpStatus.CONFLICT,
						"WORKFLOW_STATUS_IN_USE",
						"Không thể xóa trạng thái đang có công việc");
			}
		}

		taskStatusRepository.saveAllAndFlush(replacement);
		var initialStatus = replacement.stream().filter(status -> !status.isTerminal())
				.findFirst().orElseThrow(() -> new IllegalStateException("Project workflow has no active status"));
		for (var status : removed) {
			for (var deletedTask : taskRepository.findAllByStatus_IdAndDeletedAtIsNotNull(status.getId())) {
				deletedTask.setStatus(initialStatus);
			}
		}
		taskRepository.flush();
		taskStatusRepository.deleteAll(removed);
		taskStatusRepository.flush();
		return responses(projectId);
	}

	@Transactional
	public void createDefaults(Project project) {
		if (taskStatusRepository.existsByProject_Id(project.getId())) {
			return;
		}
		var statuses = new java.util.ArrayList<TaskStatus>();
		for (var index = 0; index < DEFAULT_STATUSES.size(); index++) {
			var item = DEFAULT_STATUSES.get(index);
			statuses.add(new TaskStatus(
					project,
					item.code(),
					item.displayName(),
					(short) (index + 1),
					item.terminal()));
		}
		taskStatusRepository.saveAllAndFlush(statuses);
	}

	@Transactional
	public TaskStatus initialStatus(Project project) {
		createDefaults(project);
		return taskStatusRepository
				.findFirstByProject_IdAndTerminalFalseOrderByDisplayOrderAsc(project.getId())
				.orElseThrow(() -> new IllegalStateException("Project workflow has no active status"));
	}

	@Transactional
	public TaskStatus requireStatus(Project project, String code) {
		var normalizedCode = normalizeCode(code);
		var projectStatus = taskStatusRepository.findByProject_IdAndCode(project.getId(), normalizedCode);
		if (projectStatus.isPresent()) {
			return projectStatus.get();
		}
		if (!taskStatusRepository.existsByProject_Id(project.getId())) {
			return taskStatusRepository.findTemplateByCode(normalizedCode)
					.orElseThrow(this::malformedTaskStatus);
		}
		throw malformedTaskStatus();
	}

	private List<WorkflowStatusResponse> responses(Long projectId) {
		return taskStatusRepository.findAllByProject_IdOrderByDisplayOrderAsc(projectId).stream()
				.map(WorkflowStatusResponse::from)
				.toList();
	}

	private void validateWorkflow(List<WorkflowStatusItemRequest> statuses) {
		var names = new HashSet<String>();
		var terminalCount = 0;
		for (var status : statuses) {
			var normalizedName = status.displayName().strip().toLowerCase(Locale.ROOT);
			if (!names.add(normalizedName)) {
				throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"WORKFLOW_STATUS_NAME_DUPLICATE",
						"Tên trạng thái trong quy trình không được trùng nhau");
			}
			if (Boolean.TRUE.equals(status.terminal())) {
				terminalCount++;
			}
		}
		if (terminalCount != 1) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"WORKFLOW_TERMINAL_STATUS_REQUIRED",
					"Quy trình phải có đúng một trạng thái hoàn tất");
		}
	}

	private void updateCompletionState(TaskStatus status, boolean terminal) {
		var now = LocalDateTime.now();
		for (var task : taskRepository.findAllByStatus_Id(status.getId())) {
			if (terminal && task.getCompletedAt() == null) {
				task.setCompletedAt(now);
			}
			else if (!terminal) {
				task.setCompletedAt(null);
			}
		}
	}

	private ProjectAccess requireOwnerForUpdate(String email, Long projectId) {
		var user = requireCurrentUser(email);
		var project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(this::projectNotFound);
		var membership = projectMemberRepository.findMembership(projectId, user.getId())
				.orElseThrow(this::projectNotFound);
		if (membership.getProjectRole() != ProjectRole.OWNER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"PROJECT_OWNER_REQUIRED",
					"Chỉ chủ sở hữu mới có thể quản lý quy trình");
		}
		if (project.getStatus() == ProjectStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_ARCHIVED",
					"Dự án đã đóng và không thể chỉnh sửa");
		}
		return new ProjectAccess(project);
	}

	private void requireMembership(String email, Long projectId) {
		var user = requireCurrentUser(email);
		projectMemberRepository.findMembership(projectId, user.getId())
				.orElseThrow(this::projectNotFound);
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

	private String normalizeCode(String code) {
		return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
	}

	private String newStatusCode() {
		return "CUSTOM_" + UUID.randomUUID().toString().replace("-", "")
				.substring(0, 16).toUpperCase(Locale.ROOT);
	}

	private ApiException invalidStatus() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"WORKFLOW_STATUS_INVALID",
				"Trạng thái không thuộc quy trình của dự án");
	}

	private ApiException malformedTaskStatus() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"Trạng thái không thuộc quy trình của dự án");
	}

	private ApiException projectNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"PROJECT_NOT_FOUND",
				"Không tìm thấy dự án");
	}

	private record ProjectAccess(Project project) {
	}

	private record DefaultStatus(String code, String displayName, boolean terminal) {
	}
}
