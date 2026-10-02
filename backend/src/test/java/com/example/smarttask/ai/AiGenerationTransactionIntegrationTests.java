package com.example.smarttask.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.AiRequestStatus;
import com.example.smarttask.domain.AiRequestType;
import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;
import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.AiRequestRepository;
import com.example.smarttask.repository.AiSuggestionRepository;
import com.example.smarttask.repository.ProjectMemberRepository;
import com.example.smarttask.repository.ProjectRepository;
import com.example.smarttask.repository.TaskRepository;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;
import com.example.smarttask.workflow.WorkflowStatusService;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ai_generation_transactions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiGenerationTransactionIntegrationTests {

	@Autowired
	private AiDecompositionService service;
	@Autowired
	private UserRepository users;
	@Autowired
	private ProjectRepository projects;
	@Autowired
	private ProjectMemberRepository members;
	@Autowired
	private TaskRepository tasks;
	@Autowired
	private AiRequestRepository requests;
	@Autowired
	private AiSuggestionRepository suggestions;
	@Autowired
	private WorkflowStatusService workflow;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoBean
	private TaskDecompositionProvider provider;

	private TransactionTemplate transactions;
	private Fixture fixture;

	@BeforeEach
	void setUp() {
		transactions = new TransactionTemplate(transactionManager);
		when(provider.providerName()).thenReturn("test");
		when(provider.generationMode()).thenReturn("TEST");
		when(provider.modelName()).thenReturn("test-model");
		when(provider.notice()).thenReturn("Provider kiểm thử nội bộ");
		fixture = transactions.execute(ignored -> {
			var owner = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "unused", "Chủ AI"));
			var project = projects.saveAndFlush(new Project(owner, "Kiểm thử giao dịch AI"));
			members.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
			var task = tasks.saveAndFlush(new Task(project, owner, workflow.initialStatus(project),
					"Phân tích yêu cầu", LocalDateTime.now().plusDays(2)));
			return new Fixture(owner.getId(), owner.getEmail(), project.getId(), task.getId());
		});
	}

	@Test
	void providerRunsOutsideTheTransactionAndResultIsCommittedAfterward() {
		when(provider.generate(any(), anyInt())).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(latestStoredStatus()).isEqualTo(AiRequestStatus.PENDING);
			var pending = service.latest(fixture.email(), fixture.projectId(), fixture.taskId());
			assertThat(pending.status()).isEqualTo(AiRequestStatus.PENDING);
			assertThat(pending.suggestions()).isEmpty();
			return generation();
		});

		var result = generate();
		assertThat(result.status()).isEqualTo(AiRequestStatus.SUCCEEDED);
		assertThat(result.suggestions()).hasSize(2);
		assertThat(latestStoredStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
	}

	@Test
	void providerFailureIsPersistedAndLatestCanReadTheFailedRequest() {
		when(provider.generate(any(), anyInt())).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "Provider chưa sẵn sàng");
		});

		assertThatThrownBy(this::generate).isInstanceOf(ApiException.class);
		assertThat(latestStoredStatus()).isEqualTo(AiRequestStatus.FAILED);
		var latest = service.latest(fixture.email(), fixture.projectId(), fixture.taskId());
		assertThat(latest.status()).isEqualTo(AiRequestStatus.FAILED);
		assertThat(latest.suggestions()).isEmpty();
		assertThat(latest.finishedAt()).isNotNull();
		assertThat(latest.notice()).contains("không thành công");
		assertThat(suggestions.findAllByRequest_IdOrderByIdAsc(latest.requestId())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ARCHIVED", "DELETED", "LOCKED", "REMOVED", "DEMOTED"})
	void completedGenerationRevalidatesStateBeforePublishingSuggestions(String change) {
		when(provider.generate(any(), anyInt())).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			transactions.executeWithoutResult(ignored -> {
				switch (change) {
					case "ARCHIVED" -> projects.findById(fixture.projectId()).orElseThrow().setStatus(ProjectStatus.ARCHIVED);
					case "DELETED" -> tasks.findById(fixture.taskId()).orElseThrow().setDeletedAt(LocalDateTime.now());
					case "LOCKED" -> users.findById(fixture.userId()).orElseThrow().setAccountStatus(AccountStatus.LOCKED);
					case "REMOVED" -> members.delete(members.findMembership(fixture.projectId(), fixture.userId()).orElseThrow());
					case "DEMOTED" -> members.findMembership(fixture.projectId(), fixture.userId()).orElseThrow().setProjectRole(ProjectRole.MEMBER);
					default -> throw new IllegalArgumentException(change);
				}
			});
			return generation();
		});

		assertThatThrownBy(this::generate).isInstanceOf(ApiException.class);
		assertThat(latestStoredStatus()).isEqualTo(AiRequestStatus.FAILED);
		var request = requests.findFirstByProject_IdAndSourceTask_IdAndRequestTypeOrderByCreatedAtDescIdDesc(
				fixture.projectId(), fixture.taskId(), AiRequestType.TASK_DECOMPOSITION).orElseThrow();
		assertThat(suggestions.findAllByRequest_IdOrderByIdAsc(request.getId())).isEmpty();
	}

	private AiDecompositionResponse generate() {
		return service.generate(fixture.email(), fixture.projectId(), fixture.taskId(), new GenerateTaskDecompositionRequest(2));
	}

	private AiRequestStatus latestStoredStatus() {
		return requests.findFirstByProject_IdAndSourceTask_IdAndRequestTypeOrderByCreatedAtDescIdDesc(
				fixture.projectId(), fixture.taskId(), AiRequestType.TASK_DECOMPOSITION).orElseThrow().getRequestStatus();
	}

	private TaskDecompositionGeneration generation() {
		return new TaskDecompositionGeneration(List.of(
				new TaskDecompositionProposal("Phân tích", "Làm rõ yêu cầu", 30),
				new TaskDecompositionProposal("Triển khai", "Xây dựng chức năng", 60)), 10, 20);
	}

	private record Fixture(Long userId, String email, Long projectId, Long taskId) {
	}
}
