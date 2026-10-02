package com.example.smarttask.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_requests")
public class AiRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requested_by_id", nullable = false)
	private User requestedBy;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_task_id")
	private Task sourceTask;

	@Enumerated(EnumType.STRING)
	@Column(name = "request_type", nullable = false, length = 40)
	private AiRequestType requestType;

	@Enumerated(EnumType.STRING)
	@Column(name = "request_status", nullable = false, length = 16)
	private AiRequestStatus requestStatus = AiRequestStatus.PENDING;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_data", nullable = false, columnDefinition = "json")
	private String inputData;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_response", columnDefinition = "json")
	private String rawResponse;

	@Column(name = "model_name", length = 100)
	private String modelName;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected AiRequest() {
	}

	public AiRequest(User requestedBy, Project project, AiRequestType requestType,
			String inputData) {
		this.requestedBy = requestedBy;
		this.project = project;
		this.requestType = requestType;
		this.inputData = inputData;
	}

	public Long getId() {
		return id;
	}

	public User getRequestedBy() {
		return requestedBy;
	}

	public Project getProject() {
		return project;
	}

	public Task getSourceTask() {
		return sourceTask;
	}

	public void setSourceTask(Task sourceTask) {
		this.sourceTask = sourceTask;
	}

	public AiRequestType getRequestType() {
		return requestType;
	}

	public AiRequestStatus getRequestStatus() {
		return requestStatus;
	}

	public void setRequestStatus(AiRequestStatus requestStatus) {
		this.requestStatus = requestStatus;
	}

	public String getInputData() {
		return inputData;
	}

	public String getRawResponse() {
		return rawResponse;
	}

	public void setRawResponse(String rawResponse) {
		this.rawResponse = rawResponse;
	}

	public String getModelName() {
		return modelName;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public Integer getInputTokens() {
		return inputTokens;
	}

	public void setInputTokens(Integer inputTokens) {
		this.inputTokens = inputTokens;
	}

	public Integer getOutputTokens() {
		return outputTokens;
	}

	public void setOutputTokens(Integer outputTokens) {
		this.outputTokens = outputTokens;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
