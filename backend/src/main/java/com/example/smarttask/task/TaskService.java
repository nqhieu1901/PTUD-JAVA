package com.example.smarttask.task;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.attachment.TaskAttachmentLifecycleService;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMemberId;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.User;
import com.example.smarttask.notification.NotificationService;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;
import com.example.smarttask.workflow.WorkflowStatusService;

@Service
public class TaskService {

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskRepository taskRepository;
	private final NotificationService notificationService;
	private final WorkflowStatusService workflowStatusService;
	private final TaskAttachmentLifecycleService attachmentLifecycleService;

	public TaskService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskRepository taskRepository,
			NotificationService notificationService,
			WorkflowStatusService workflowStatusService,
			TaskAttachmentLifecycleService attachmentLifecycleService) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskRepository = taskRepository;
		this.notificationService = notificationService;
		this.workflowStatusService = workflowStatusService;
		this.attachmentLifecycleService = attachmentLifecycleService;
	}

	@Transactional
	public TaskResponse create(String currentUserEmail, Long projectId, TaskRequest request) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);
		validateDates(request);

		var initialStatus = workflowStatusService.initialStatus(access.project());
		var task = new Task(
				access.project(),
				access.user(),
				initialStatus,
				request.title(),
				request.dueAt());
		applyRequest(task, request, access.project());
		task.setBoardOrder(taskRepository.findNextBoardOrder(projectId, initialStatus.getCode()));
		taskRepository.saveAndFlush(task);
		notifyAssigneeChange(task, null, access.user());

		return TaskResponse.from(task);
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> findAll(
			String currentUserEmail,
			Long projectId,
			TaskFilter filter) {
		var access = requireReadAccess(currentUserEmail, projectId);
		validateFilter(filter);

		var searchQuery = filter.query() == null
				? null
				: filter.query().toLowerCase(Locale.ROOT);
		var dueFrom = filter.dueFrom() == null
				? null
				: filter.dueFrom().atStartOfDay();
		var dueBefore = dueBefore(filter);

		var statusCode = filter.status() == null
				? null
				: workflowStatusService.requireStatus(access.project(), filter.status()).getCode();

		return taskRepository.findActiveTasks(
				projectId,
				searchQuery,
				statusCode,
				filter.priority(),
				dueFrom,
				dueBefore,
				filter.assigneeId()).stream()
				.map(TaskResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public TaskResponse findOne(String currentUserEmail, Long projectId, Long taskId) {
		requireReadAccess(currentUserEmail, projectId);
		return TaskResponse.from(requireTask(projectId, taskId));
	}

	@Transactional
	public TaskResponse update(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			TaskRequest request) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);
		validateDates(request);

		var task = requireTask(projectId, taskId);
		var previousAssigneeId = task.getAssignee() == null
				? null
				: task.getAssignee().getId();
		applyRequest(task, request, access.project());
		taskRepository.saveAndFlush(task);
		notifyAssigneeChange(task, previousAssigneeId, access.user());
		return TaskResponse.from(task);
	}

	@Transactional
	public TaskResponse updateStatus(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			TaskStatusUpdateRequest request) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		var task = requireTask(projectId, taskId);
		requireTaskMover(access, task);

		var requestedOrder = task.getStatus().getCode().equalsIgnoreCase(request.status())
				? task.getBoardOrder()
				: Long.MAX_VALUE;
		return moveTask(projectId, task, request.status(), requestedOrder, access.user());
	}

	@Transactional
	public TaskResponse updatePosition(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			TaskPositionUpdateRequest request) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		var task = requireTask(projectId, taskId);
		requireTaskMover(access, task);
		return moveTask(projectId, task, request.status(), request.boardOrder(), access.user());
	}

	@Transactional
	public void delete(String currentUserEmail, Long projectId, Long taskId) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);

		var task = requireTask(projectId, taskId);
		var remainingTasks = new ArrayList<>(findTasksInStatus(
				projectId,
				task.getStatus().getCode()));
		remainingTasks.removeIf(item -> item.getId().equals(task.getId()));
		attachmentLifecycleService.deleteAllForTask(task);
		task.setAssignee(null);
		task.setDeletedAt(LocalDateTime.now());
		normalizeBoardOrder(remainingTasks);
		taskRepository.flush();
	}

	private TaskResponse moveTask(
			Long projectId,
			Task task,
			String targetStatusCode,
			long requestedOrder,
			User actor) {
		var sourceStatusCode = task.getStatus().getCode();
		var normalizedTargetStatus = workflowStatusService
				.requireStatus(task.getProject(), targetStatusCode);
		targetStatusCode = normalizedTargetStatus.getCode();
		if (!sourceStatusCode.equals(targetStatusCode)
				&& Math.abs(task.getStatus().getDisplayOrder() - normalizedTargetStatus.getDisplayOrder()) != 1) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"TASK_STATUS_TRANSITION_NOT_ADJACENT",
					"Công việc chỉ có thể chuyển sang cột trạng thái liền trước hoặc liền sau");
		}
		var sourceTasks = new ArrayList<>(findTasksInStatus(projectId, sourceStatusCode));
		var targetTasks = sourceStatusCode.equals(targetStatusCode)
				? sourceTasks
				: new ArrayList<>(findTasksInStatus(projectId, targetStatusCode));

		sourceTasks.removeIf(item -> item.getId().equals(task.getId()));
		if (sourceTasks != targetTasks) {
			normalizeBoardOrder(sourceTasks);
		}

		task.setStatus(normalizedTargetStatus);
		updateCompletedAt(task, normalizedTargetStatus.isTerminal());
		var targetIndex = (int) Math.min(requestedOrder, targetTasks.size());
		targetTasks.add(targetIndex, task);
		normalizeBoardOrder(targetTasks);
		taskRepository.flush();
		if (!sourceStatusCode.equals(targetStatusCode)) {
			notificationService.notifyTaskStatusChanged(actor, task);
		}
		return TaskResponse.from(task);
	}

	private void requireTaskMover(ProjectAccess access, Task task) {
		if (access.role() == ProjectRole.VIEWER) {
			throw viewerReadOnly();
		}
		if (access.role() != ProjectRole.OWNER
				&& (task.getAssignee() == null
						|| !task.getAssignee().getId().equals(access.user().getId()))) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"TASK_ASSIGNEE_REQUIRED",
					"Chỉ chủ sở hữu hoặc người được giao mới có thể di chuyển công việc");
		}
	}

	private void notifyAssigneeChange(Task task, Long previousAssigneeId, User actor) {
		var assignee = task.getAssignee();
		if (assignee != null && !assignee.getId().equals(previousAssigneeId)) {
			notificationService.notifyTaskAssigned(
					assignee,
					actor,
					task.getProject(),
					task);
		}
	}

	private void updateCompletedAt(Task task, boolean terminal) {
		if (terminal) {
			if (task.getCompletedAt() == null) {
				task.setCompletedAt(LocalDateTime.now());
			}
		}
		else {
			task.setCompletedAt(null);
		}
	}

	private void normalizeBoardOrder(List<Task> tasks) {
		for (var index = 0; index < tasks.size(); index++) {
			tasks.get(index).setBoardOrder(index);
		}
	}

	private List<Task> findTasksInStatus(Long projectId, String statusCode) {
		return taskRepository.findActiveTasksInStatus(projectId, statusCode);
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
		if (membership.getProjectRole() == ProjectRole.VIEWER) {
			throw viewerReadOnly();
		}
		if (project.getStatus() == ProjectStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_ARCHIVED",
					"Dự án đã đóng và không thể chỉnh sửa");
		}
		return new ProjectAccess(user, project, membership.getProjectRole());
	}

	private void requireOwner(ProjectAccess access) {
		if (access.role() != ProjectRole.OWNER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"PROJECT_OWNER_REQUIRED",
					"Chỉ chủ sở hữu mới có thể thực hiện thao tác này");
		}
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

	private User resolveAssignee(Project project, Long assigneeId) {
		if (assigneeId == null) {
			return null;
		}
		var membershipId = new ProjectMemberId(project.getId(), assigneeId);
		var membership = projectMemberRepository.findById(membershipId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.BAD_REQUEST,
						"TASK_ASSIGNEE_NOT_MEMBER",
						"Người được giao phải là thành viên của dự án"));
		if (membership.getProjectRole() == ProjectRole.VIEWER) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"TASK_ASSIGNEE_VIEWER_NOT_ALLOWED",
					"Không thể giao công việc cho người chỉ có quyền xem");
		}
		var assignee = membership.getUser();
		if (assignee.getAccountStatus() != AccountStatus.ACTIVE || assignee.getSystemRole() != SystemRole.USER) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"TASK_ASSIGNEE_UNAVAILABLE",
					"Chỉ có thể giao công việc cho tài khoản người dùng đang hoạt động");
		}
		return assignee;
	}

	private void applyRequest(Task task, TaskRequest request, Project project) {
		var assignee = resolveAssignee(project, request.assigneeId());
		var parentTask = resolveParentTask(project.getId(), task, request.parentTaskId());
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setPriority(request.priority());
		task.setStartAt(request.startAt());
		task.setDueAt(request.dueAt());
		task.setEstimatedMinutes(request.estimatedMinutes());
		task.setAssignee(assignee);
		task.setParentTask(parentTask);
	}

	private Task resolveParentTask(Long projectId, Task task, Long parentTaskId) {
		if (parentTaskId == null) {
			return null;
		}
		if (task.getId() != null && task.getId().equals(parentTaskId)) {
			throw parentCycle();
		}

		var parentTask = taskRepository.findActiveTask(projectId, parentTaskId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.BAD_REQUEST,
						"TASK_PARENT_NOT_FOUND",
						"Công việc cha phải là công việc đang hoạt động trong cùng dự án"));
		if (task.getId() == null) {
			return parentTask;
		}

		var visitedTaskIds = new HashSet<Long>();
		for (var ancestor = parentTask; ancestor != null; ancestor = ancestor.getParentTask()) {
			if (task.getId().equals(ancestor.getId()) || !visitedTaskIds.add(ancestor.getId())) {
				throw parentCycle();
			}
		}
		return parentTask;
	}

	private ApiException parentCycle() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"TASK_PARENT_CYCLE",
				"Quan hệ công việc cha không được tạo chu trình");
	}

	private void validateDates(TaskRequest request) {
		if (request.startAt() != null && request.dueAt().isBefore(request.startAt())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_TASK_DATES",
					"Deadline không được trước thời điểm bắt đầu");
		}
	}

	private void validateFilter(TaskFilter filter) {
		if (filter.dueFrom() != null
				&& filter.dueTo() != null
				&& filter.dueTo().isBefore(filter.dueFrom())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_TASK_FILTER",
					"Ngày kết thúc bộ lọc không được trước ngày bắt đầu");
		}
	}

	private LocalDateTime dueBefore(TaskFilter filter) {
		if (filter.dueTo() == null) {
			return null;
		}
		try {
			return filter.dueTo().plusDays(1).atStartOfDay();
		}
		catch (DateTimeException exception) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_TASK_FILTER",
					"Ngày kết thúc bộ lọc nằm ngoài phạm vi hỗ trợ");
		}
	}

	private ApiException projectNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"PROJECT_NOT_FOUND",
				"Không tìm thấy dự án");
	}

	private ApiException viewerReadOnly() {
		return new ApiException(
				HttpStatus.FORBIDDEN,
				"PROJECT_VIEWER_READ_ONLY",
				"Người xem chỉ có quyền đọc dữ liệu dự án");
	}


	private record ProjectAccess(User user, Project project, ProjectRole role) {
	}
}
