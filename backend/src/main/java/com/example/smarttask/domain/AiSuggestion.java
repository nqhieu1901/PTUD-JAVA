package com.example.smarttask.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ai_suggestions")
public class AiSuggestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private AiRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_task_id")
	private Task targetTask;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_task_id", unique = true)
	private Task createdTask;

	@Enumerated(EnumType.STRING)
	@Column(name = "suggestion_type", nullable = false, length = 32)
	private AiSuggestionType suggestionType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "proposed_data", nullable = false, columnDefinition = "json")
	private String proposedData;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision_status", nullable = false, length = 16)
	private AiDecisionStatus decisionStatus = AiDecisionStatus.PENDING;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by_id")
	private User decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected AiSuggestion() {
	}

	public AiSuggestion(AiRequest request, AiSuggestionType suggestionType,
			String proposedData) {
		this.request = request;
		this.suggestionType = suggestionType;
		this.proposedData = proposedData;
	}

	public Long getId() {
		return id;
	}

	public AiRequest getRequest() {
		return request;
	}

	public Task getTargetTask() {
		return targetTask;
	}

	public void setTargetTask(Task targetTask) {
		this.targetTask = targetTask;
	}

	public Task getCreatedTask() {
		return createdTask;
	}

	public AiSuggestionType getSuggestionType() {
		return suggestionType;
	}

	public String getProposedData() {
		return proposedData;
	}

	public void setProposedData(String proposedData) {
		this.proposedData = proposedData;
	}

	public AiDecisionStatus getDecisionStatus() {
		return decisionStatus;
	}

	public User getDecidedBy() {
		return decidedBy;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public void accept(User user, LocalDateTime decidedAt, Task createdTask) {
		ensurePending();
		this.decisionStatus = AiDecisionStatus.ACCEPTED;
		this.decidedBy = user;
		this.decidedAt = decidedAt;
		this.createdTask = createdTask;
	}

	public void reject(User user, LocalDateTime decidedAt) {
		ensurePending();
		this.decisionStatus = AiDecisionStatus.REJECTED;
		this.decidedBy = user;
		this.decidedAt = decidedAt;
	}

	private void ensurePending() {
		if (decisionStatus != AiDecisionStatus.PENDING) {
			throw new IllegalStateException("AI suggestion has already been decided");
		}
	}

	public long getVersion() {
		return version;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
