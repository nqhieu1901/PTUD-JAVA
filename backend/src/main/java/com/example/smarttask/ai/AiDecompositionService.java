package com.example.smarttask.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.AiDecisionStatus;
import com.example.smarttask.domain.AiRequest;
import com.example.smarttask.domain.AiRequestStatus;
import com.example.smarttask.domain.AiRequestType;
import com.example.smarttask.domain.AiSuggestion;
import com.example.smarttask.domain.AiSuggestionType;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.User;
import com.example.smarttask.notification.NotificationService;
import com.example.smarttask.repository.AiRequestRepository;
import com.example.smarttask.repository.AiSuggestionRepository;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.task.TaskResponse;
import com.example.smarttask.web.ApiException;
import com.example.smarttask.workflow.WorkflowStatusService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiDecompositionService {

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskRepository taskRepository;
	private final WorkflowStatusService workflowStatusService;
	private final AiRequestRepository aiRequestRepository;
	private final AiSuggestionRepository aiSuggestionRepository;
	private final TaskDecompositionProvider provider;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactions;

	public AiDecompositionService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskRepository taskRepository,
			WorkflowStatusService workflowStatusService,
			AiRequestRepository aiRequestRepository,
			AiSuggestionRepository aiSuggestionRepository,
			TaskDecompositionProvider provider,
			NotificationService notificationService,
			ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskRepository = taskRepository;
		this.workflowStatusService = workflowStatusService;
		this.aiRequestRepository = aiRequestRepository;
		this.aiSuggestionRepository = aiSuggestionRepository;
		this.provider = provider;
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public AiDecompositionResponse generate(
			String currentUserEmail,
			Long projectId,
			Long taskId,
			GenerateTaskDecompositionRequest request) {
		var maxSuggestions = requireSuggestionCount(request.maxSuggestions());
		var snapshot = transactions.execute(ignored -> createGenerationRequest(
				currentUserEmail, projectId, taskId, maxSuggestions));
		try {
			var generation = provider.generate(snapshot.source(), maxSuggestions);
			var proposals = generation.proposals().stream()
					.map(this::validateProposal)
					.toList();
			if (proposals.size() != maxSuggestions) {
				throw invalidSuggestion();
			}
			return transactions.execute(ignored -> completeGeneration(
					currentUserEmail, projectId, taskId, snapshot.requestId(), generation, proposals));
		}
		catch (RuntimeException exception) {
			transactions.executeWithoutResult(ignored -> {
				var failed = aiRequestRepository.findById(snapshot.requestId()).orElseThrow();
				failed.setRequestStatus(AiRequestStatus.FAILED);
				failed.setErrorMessage("Không thể tạo gợi ý. Vui lòng kiểm tra công việc và thử lại.");
				failed.setFinishedAt(LocalDateTime.now());
				aiRequestRepository.saveAndFlush(failed);
			});
			throw exception;
		}
	}

	private GenerationSnapshot createGenerationRequest(
			String currentUserEmail, Long projectId, Long taskId, int maxSuggestions) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);
		var sourceTask = requireTask(projectId, taskId);

		var aiRequest = new AiRequest(
				access.user(),
				access.project(),
				AiRequestType.TASK_DECOMPOSITION,
				writeJson(Map.of("maxSuggestions", maxSuggestions)));
		aiRequest.setSourceTask(sourceTask);
		aiRequest.setModelName(provider.modelName());
		aiRequest.setInputTokens(0);
		aiRequest.setOutputTokens(0);
		aiRequest.setRawResponse(writeJson(new StoredGeneration(
				provider.generationMode(), provider.providerName(), provider.modelName(),
				provider.notice(), List.of())));
		aiRequestRepository.saveAndFlush(aiRequest);

		return new GenerationSnapshot(aiRequest.getId(), new TaskDecompositionSource(
				sourceTask.getTitle(),
				sourceTask.getDescription(),
				sourceTask.getEstimatedMinutes()));
	}

	private AiDecompositionResponse completeGeneration(
			String currentUserEmail, Long projectId, Long taskId, Long requestId,
			TaskDecompositionGeneration generation, List<TaskDecompositionProposal> proposals) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);
		var sourceTask = requireTask(projectId, taskId);
		var aiRequest = aiRequestRepository.findById(requestId).orElseThrow();

		var suggestions = proposals.stream()
				.map(proposal -> {
					var suggestion = new AiSuggestion(
							aiRequest,
							AiSuggestionType.SUBTASK,
							writeJson(proposal));
					suggestion.setTargetTask(sourceTask);
					return suggestion;
				})
				.toList();
		aiSuggestionRepository.saveAllAndFlush(suggestions);

		aiRequest.setInputTokens(generation.inputTokens());
		aiRequest.setOutputTokens(generation.outputTokens());
		aiRequest.setRawResponse(writeJson(new StoredGeneration(
				provider.generationMode(),
				provider.providerName(),
				provider.modelName(),
				provider.notice(),
				proposals)));
		aiRequest.setRequestStatus(AiRequestStatus.SUCCEEDED);
		aiRequest.setFinishedAt(LocalDateTime.now());
		aiRequestRepository.saveAndFlush(aiRequest);

		return toResponse(aiRequest, suggestions);
	}

	@Transactional(readOnly = true)
	public AiDecompositionResponse latest(
			String currentUserEmail,
			Long projectId,
			Long taskId) {
		requireReadAccess(currentUserEmail, projectId);
		requireTask(projectId, taskId);
		var request = aiRequestRepository
				.findFirstByProject_IdAndSourceTask_IdAndRequestTypeOrderByCreatedAtDescIdDesc(
						projectId,
						taskId,
						AiRequestType.TASK_DECOMPOSITION)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"AI_REQUEST_NOT_FOUND",
						"Chưa có lần phân rã công việc nào"));
		return toResponse(
				request,
				aiSuggestionRepository.findAllByRequest_IdOrderByIdAsc(request.getId()));
	}

	@Transactional
	public AiSuggestionResponse accept(
			String currentUserEmail,
			Long projectId,
			Long suggestionId) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);
		var suggestion = requireDecompositionSuggestion(projectId, suggestionId);
		requirePending(suggestion);
		var proposal = readAndValidateProposal(suggestion.getProposedData());
		var sourceTaskId = suggestion.getRequest().getSourceTask().getId();
		var sourceTask = requireTask(projectId, sourceTaskId);
		var assignee = requireAssignableUser(projectId, sourceTask.getAssignee());
		var initialStatus = workflowStatusService.initialStatus(access.project());

		var createdTask = new Task(
				access.project(),
				access.user(),
				initialStatus,
				proposal.title(),
				sourceTask.getDueAt());
		createdTask.setParentTask(sourceTask);
		createdTask.setDescription(proposal.description());
		createdTask.setPriority(sourceTask.getPriority());
		createdTask.setAssignee(assignee);
		createdTask.setEstimatedMinutes(proposal.estimatedMinutes());
		createdTask.setBoardOrder(taskRepository.findNextBoardOrder(projectId, initialStatus.getCode()));
		taskRepository.saveAndFlush(createdTask);
		if (assignee != null) {
			notificationService.notifyTaskAssigned(assignee, access.user(), access.project(), createdTask);
		}

		suggestion.accept(access.user(), LocalDateTime.now(), createdTask);
		aiSuggestionRepository.saveAndFlush(suggestion);
		return toResponse(suggestion, proposal);
	}

	@Transactional
	public AiSuggestionResponse reject(
			String currentUserEmail,
			Long projectId,
			Long suggestionId) {
		var access = requireMutationAccess(currentUserEmail, projectId);
		requireOwner(access);
		var suggestion = requireDecompositionSuggestion(projectId, suggestionId);
		requirePending(suggestion);
		var proposal = readAndValidateProposal(suggestion.getProposedData());

		suggestion.reject(access.user(), LocalDateTime.now());
		aiSuggestionRepository.saveAndFlush(suggestion);
		return toResponse(suggestion, proposal);
	}

	private AiDecompositionResponse toResponse(
			AiRequest request,
			List<AiSuggestion> suggestions) {
		var generation = readStoredGeneration(request);
		return new AiDecompositionResponse(
				request.getId(),
				request.getRequestType(),
				request.getRequestStatus(),
				request.getSourceTask().getId(),
				generation.generationMode(),
				generation.provider(),
				request.getModelName(),
				request.getRequestStatus() == AiRequestStatus.FAILED
						? "Lần tạo gợi ý này không thành công. Vui lòng thử lại."
						: request.getRequestStatus() == AiRequestStatus.PENDING
								? "Đang tạo gợi ý, vui lòng tải lại sau."
								: generation.notice(),
				suggestions.stream()
						.map(suggestion -> toResponse(
								suggestion,
								readAndValidateProposal(suggestion.getProposedData())))
						.toList(),
				request.getCreatedAt(),
				request.getFinishedAt());
	}

	private StoredGeneration readStoredGeneration(AiRequest request) {
		if (request.getRawResponse() == null && request.getRequestStatus() != AiRequestStatus.SUCCEEDED) {
			return new StoredGeneration(provider.generationMode(), provider.providerName(),
					request.getModelName(), provider.notice(), List.of());
		}
		try {
			return objectMapper.readValue(request.getRawResponse(), StoredGeneration.class);
		}
		catch (JacksonException | IllegalArgumentException exception) {
			throw invalidSuggestion();
		}
	}

	private AiSuggestionResponse toResponse(
			AiSuggestion suggestion,
			TaskDecompositionProposal proposal) {
		return new AiSuggestionResponse(
				suggestion.getId(),
				suggestion.getSuggestionType(),
				suggestion.getDecisionStatus(),
				proposal,
				suggestion.getCreatedTask() == null
						? null
						: TaskResponse.from(suggestion.getCreatedTask()));
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

	private User requireAssignableUser(Long projectId, User assignee) {
		if (assignee == null) {
			return null;
		}
		var membership = projectMemberRepository.findMembership(projectId, assignee.getId())
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
		if (assignee.getAccountStatus() != AccountStatus.ACTIVE || assignee.getSystemRole() != SystemRole.USER) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"TASK_ASSIGNEE_UNAVAILABLE",
					"Chỉ có thể giao công việc cho tài khoản người dùng đang hoạt động");
		}
		return assignee;
	}

	private AiSuggestion requireDecompositionSuggestion(Long projectId, Long suggestionId) {
		var suggestion = aiSuggestionRepository.findForUpdate(projectId, suggestionId)
				.orElseThrow(this::suggestionNotFound);
		if (suggestion.getSuggestionType() != AiSuggestionType.SUBTASK
				|| suggestion.getRequest().getRequestType() != AiRequestType.TASK_DECOMPOSITION
				|| suggestion.getRequest().getSourceTask() == null) {
			throw suggestionNotFound();
		}
		return suggestion;
	}

	private void requirePending(AiSuggestion suggestion) {
		if (suggestion.getDecisionStatus() != AiDecisionStatus.PENDING) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"AI_SUGGESTION_ALREADY_DECIDED",
					"Gợi ý này đã được chấp nhận hoặc từ chối");
		}
	}

	private int requireSuggestionCount(Integer maxSuggestions) {
		if (maxSuggestions == null || maxSuggestions < 2 || maxSuggestions > 6) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"AI_SUGGESTION_COUNT_INVALID",
					"Số lượng gợi ý phải từ 2 đến 6");
		}
		return maxSuggestions;
	}

	private TaskDecompositionProposal readAndValidateProposal(String json) {
		try {
			return validateProposal(objectMapper.readValue(json, TaskDecompositionProposal.class));
		}
		catch (JacksonException exception) {
			throw invalidSuggestion();
		}
	}

	private TaskDecompositionProposal validateProposal(TaskDecompositionProposal proposal) {
		if (proposal == null || proposal.title() == null) {
			throw invalidSuggestion();
		}
		var title = proposal.title().strip();
		var description = proposal.description() == null
				? null
				: proposal.description().strip();
		if (description != null && description.isEmpty()) {
			description = null;
		}
		if (title.isEmpty()
				|| title.length() > 255
				|| (description != null && description.length() > 16000)
				|| proposal.estimatedMinutes() == null
				|| proposal.estimatedMinutes() <= 0) {
			throw invalidSuggestion();
		}
		return new TaskDecompositionProposal(title, description, proposal.estimatedMinutes());
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Could not serialize AI decomposition data", exception);
		}
	}

	private ApiException invalidSuggestion() {
		return new ApiException(
				HttpStatus.CONFLICT,
				"AI_SUGGESTION_INVALID",
				"Dữ liệu gợi ý không hợp lệ và không thể áp dụng");
	}

	private ApiException suggestionNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"AI_SUGGESTION_NOT_FOUND",
				"Không tìm thấy gợi ý AI");
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

	private record GenerationSnapshot(Long requestId, TaskDecompositionSource source) {
	}

	private record ProjectAccess(User user, Project project, ProjectRole role) {
	}

	private record StoredGeneration(
			String generationMode,
			String provider,
			String modelName,
			String notice,
			List<TaskDecompositionProposal> suggestions) {
	}
}
