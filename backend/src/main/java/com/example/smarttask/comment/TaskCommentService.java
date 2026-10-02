package com.example.smarttask.comment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskComment;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskCommentRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class TaskCommentService {

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskRepository taskRepository;
	private final TaskCommentRepository taskCommentRepository;

	public TaskCommentService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskRepository taskRepository,
			TaskCommentRepository taskCommentRepository) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskRepository = taskRepository;
		this.taskCommentRepository = taskCommentRepository;
	}

	@Transactional(readOnly = true)
	public List<TaskCommentResponse> findAll(
			String currentUserEmail,
			Long projectId,
			Long taskId) {
		requireReadAccess(currentUserEmail, projectId);
		requireTask(projectId, taskId);
		return taskCommentRepository.findAllForTask(projectId, taskId).stream()
				.map(TaskCommentResponse::from)
				.toList();
	}

	@Transactional
	public TaskCommentResponse create(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			TaskCommentRequest request) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		var task = requireTask(projectId, taskId);
		var comment = new TaskComment(access.project(), task, access.user(), request.content());
		return TaskCommentResponse.from(taskCommentRepository.save(comment));
	}

	@Transactional
	public void delete(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			Long commentId) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireTask(projectId, taskId);
		var comment = taskCommentRepository.findForTask(projectId, taskId, commentId)
				.orElseThrow(this::commentNotFound);
		if (access.role() != ProjectRole.OWNER
				&& !comment.getAuthor().getId().equals(access.user().getId())) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"COMMENT_DELETE_FORBIDDEN",
					"Chỉ tác giả hoặc chủ dự án mới có thể xóa bình luận");
		}
		taskCommentRepository.delete(comment);
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
		requireWriteRole(membership.getProjectRole());
		requireActive(project);
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

	private void requireActive(Project project) {
		if (project.getStatus() == ProjectStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"PROJECT_ARCHIVED",
					"Dự án đã đóng và không thể chỉnh sửa");
		}
	}

	private void requireWriteRole(ProjectRole role) {
		if (role != ProjectRole.OWNER && role != ProjectRole.MEMBER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"PROJECT_VIEWER_READ_ONLY",
					"Vai trò hiện tại chỉ được xem dự án");
		}
	}

	private ApiException projectNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"PROJECT_NOT_FOUND",
				"Không tìm thấy dự án");
	}

	private ApiException commentNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"COMMENT_NOT_FOUND",
				"Không tìm thấy bình luận");
	}

	private record ProjectAccess(User user, Project project, ProjectRole role) {
	}
}
