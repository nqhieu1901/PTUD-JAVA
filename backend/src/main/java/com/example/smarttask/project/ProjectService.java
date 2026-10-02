package com.example.smarttask.project;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectMemberId;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.User;
import com.example.smarttask.notification.NotificationService;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;
import com.example.smarttask.workflow.WorkflowStatusService;

@Service
public class ProjectService {

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskRepository taskRepository;
	private final NotificationService notificationService;
	private final WorkflowStatusService workflowStatusService;

	public ProjectService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskRepository taskRepository,
			NotificationService notificationService,
			WorkflowStatusService workflowStatusService) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskRepository = taskRepository;
		this.notificationService = notificationService;
		this.workflowStatusService = workflowStatusService;
	}

	@Transactional
	public ProjectResponse create(String currentUserEmail, ProjectRequest request) {
		var owner = requireCurrentUser(currentUserEmail);
		validateDates(request);

		var project = new Project(owner, request.name());
		applyRequest(project, request);
		projectRepository.saveAndFlush(project);

		var ownerMembership = new ProjectMember(project, owner, ProjectRole.OWNER);
		projectMemberRepository.saveAndFlush(ownerMembership);
		workflowStatusService.createDefaults(project);

		return ProjectResponse.from(project, ProjectRole.OWNER);
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> findAllForCurrentUser(String currentUserEmail) {
		var user = requireCurrentUser(currentUserEmail);
		return projectMemberRepository.findAllForUser(user.getId()).stream()
				.map(membership -> ProjectResponse.from(
						membership.getProject(),
						membership.getProjectRole()))
				.toList();
	}

	@Transactional(readOnly = true)
	public ProjectResponse findOne(String currentUserEmail, Long projectId) {
		var membership = requireMembership(currentUserEmail, projectId);
		return ProjectResponse.from(membership.getProject(), membership.getProjectRole());
	}

	@Transactional
	public ProjectResponse update(String currentUserEmail, Long projectId, ProjectRequest request) {
		var ownerMembership = requireOwner(currentUserEmail, projectId);
		var project = ownerMembership.getProject();
		requireActive(project);
		validateDates(request);

		applyRequest(project, request);
		projectRepository.saveAndFlush(project);
		return ProjectResponse.from(project, ProjectRole.OWNER);
	}

	@Transactional
	public ProjectResponse archive(String currentUserEmail, Long projectId) {
		var ownerMembership = requireOwner(currentUserEmail, projectId);
		var project = ownerMembership.getProject();
		if (project.getStatus() == ProjectStatus.ACTIVE) {
			project.setStatus(ProjectStatus.ARCHIVED);
			project.setArchivedAt(LocalDateTime.now());
			projectRepository.saveAndFlush(project);
		}
		return ProjectResponse.from(project, ProjectRole.OWNER);
	}

	@Transactional
	public ProjectResponse restore(String currentUserEmail, Long projectId) {
		var ownerMembership = requireOwner(currentUserEmail, projectId);
		var project = ownerMembership.getProject();
		if (project.getStatus() != ProjectStatus.ACTIVE || project.getArchivedAt() != null) {
			project.setStatus(ProjectStatus.ACTIVE);
			project.setArchivedAt(null);
			projectRepository.saveAndFlush(project);
		}
		return ProjectResponse.from(project, ProjectRole.OWNER);
	}

	@Transactional(readOnly = true)
	public List<ProjectMemberResponse> findMembers(String currentUserEmail, Long projectId) {
		requireMembership(currentUserEmail, projectId);
		return projectMemberRepository.findAllForProject(projectId).stream()
				.map(ProjectMemberResponse::from)
				.toList();
	}

	@Transactional
	public ProjectMemberResponse addMember(
			String currentUserEmail,
			Long projectId,
			AddProjectMemberRequest request) {
		var ownerMembership = requireOwner(currentUserEmail, projectId);
		var project = ownerMembership.getProject();
		requireActive(project);
		var role = requireAssignableRole(request.role());

		var user = userRepository.findByEmailIgnoreCase(request.email())
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"USER_NOT_FOUND",
						"Không tìm thấy tài khoản với email này"));
		if (user.getSystemRole() != SystemRole.USER || user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.CONFLICT, "PROJECT_MEMBER_UNAVAILABLE",
					"Chỉ có thể thêm tài khoản người dùng đang hoạt động vào dự án");
		}
		var memberId = new ProjectMemberId(projectId, user.getId());
		if (projectMemberRepository.existsById(memberId)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_MEMBER_ALREADY_EXISTS",
					"Tài khoản đã là thành viên của dự án");
		}

		var membership = new ProjectMember(project, user, role);
		try {
			projectMemberRepository.saveAndFlush(membership);
		}
		catch (DataIntegrityViolationException exception) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_MEMBER_ALREADY_EXISTS",
					"Tài khoản đã là thành viên của dự án");
		}
		notificationService.notifyProjectMemberAdded(
				user,
				ownerMembership.getUser(),
				project);
		return ProjectMemberResponse.from(membership);
	}

	@Transactional
	public ProjectMemberResponse updateMemberRole(
			String currentUserEmail,
			Long projectId,
			Long userId,
			UpdateProjectMemberRoleRequest request) {
		var ownerMembership = requireOwner(currentUserEmail, projectId);
		requireActive(ownerMembership.getProject());
		var role = requireAssignableRole(request.role());

		var membership = projectMemberRepository.findById(new ProjectMemberId(projectId, userId))
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"PROJECT_MEMBER_NOT_FOUND",
						"Không tìm thấy thành viên trong dự án"));
		if (membership.getProjectRole() == ProjectRole.OWNER) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_OWNER_ROLE_CANNOT_BE_CHANGED",
					"Không thể thay đổi vai trò của chủ sở hữu dự án");
		}
		if (role == ProjectRole.VIEWER
				&& membership.getProjectRole() != ProjectRole.VIEWER
				&& taskRepository.existsByProject_IdAndAssignee_Id(projectId, userId)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_VIEWER_HAS_ASSIGNED_TASKS",
					"Cần chuyển các công việc được giao trước khi đổi thành người xem");
		}

		membership.setProjectRole(role);
		projectMemberRepository.saveAndFlush(membership);
		return ProjectMemberResponse.from(membership);
	}

	@Transactional
	public void removeMember(String currentUserEmail, Long projectId, Long userId) {
		var ownerMembership = requireOwner(currentUserEmail, projectId);
		requireActive(ownerMembership.getProject());

		var membershipId = new ProjectMemberId(projectId, userId);
		var membership = projectMemberRepository.findById(membershipId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"PROJECT_MEMBER_NOT_FOUND",
						"Không tìm thấy thành viên trong dự án"));
		if (membership.getProjectRole() == ProjectRole.OWNER) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_OWNER_CANNOT_BE_REMOVED",
					"Không thể gỡ chủ sở hữu khỏi dự án");
		}
		if (taskRepository.existsByProject_IdAndAssignee_Id(projectId, userId)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_MEMBER_HAS_TASKS",
					"Cần chuyển các công việc được giao trước khi gỡ thành viên");
		}

		try {
			projectMemberRepository.delete(membership);
			projectMemberRepository.flush();
		}
		catch (DataIntegrityViolationException exception) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_MEMBER_HAS_TASKS",
					"Cần chuyển các công việc được giao trước khi gỡ thành viên");
		}
	}

	private ProjectMember requireMembership(String currentUserEmail, Long projectId) {
		var user = requireCurrentUser(currentUserEmail);
		return projectMemberRepository.findMembership(projectId, user.getId())
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"PROJECT_NOT_FOUND",
						"Không tìm thấy dự án"));
	}

	private ProjectMember requireOwner(String currentUserEmail, Long projectId) {
		var user = requireCurrentUser(currentUserEmail);
		projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"PROJECT_NOT_FOUND",
						"Không tìm thấy dự án"));
		var membership = projectMemberRepository.findMembership(projectId, user.getId())
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"PROJECT_NOT_FOUND",
						"Không tìm thấy dự án"));
		if (membership.getProjectRole() == ProjectRole.VIEWER) {
			throw viewerReadOnly();
		}
		if (membership.getProjectRole() != ProjectRole.OWNER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"PROJECT_OWNER_REQUIRED",
					"Chỉ chủ sở hữu mới có thể thực hiện thao tác này");
		}
		return membership;
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

	private void requireActive(Project project) {
		if (project.getStatus() == ProjectStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_ARCHIVED",
					"Dự án đã đóng và không thể chỉnh sửa");
		}
	}

	private ProjectRole requireAssignableRole(ProjectRole requestedRole) {
		var role = requestedRole == null ? ProjectRole.MEMBER : requestedRole;
		if (role != ProjectRole.MEMBER && role != ProjectRole.VIEWER) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"PROJECT_MEMBER_ROLE_INVALID",
					"Vai trò thành viên chỉ có thể là MEMBER hoặc VIEWER");
		}
		return role;
	}

	private ApiException viewerReadOnly() {
		return new ApiException(
				HttpStatus.FORBIDDEN,
				"PROJECT_VIEWER_READ_ONLY",
				"Người xem chỉ có quyền đọc dữ liệu dự án");
	}

	private void validateDates(ProjectRequest request) {
		if (request.startDate() != null
				&& request.dueDate() != null
				&& request.dueDate().isBefore(request.startDate())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_PROJECT_DATES",
					"Ngày kết thúc không được trước ngày bắt đầu");
		}
	}

	private void applyRequest(Project project, ProjectRequest request) {
		project.setName(request.name());
		project.setDescription(request.description());
		project.setStartDate(request.startDate());
		project.setDueDate(request.dueDate());
	}
}
