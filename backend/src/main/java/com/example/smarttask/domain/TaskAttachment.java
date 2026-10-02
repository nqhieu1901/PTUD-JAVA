package com.example.smarttask.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_attachments")
public class TaskAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private Task task;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by_id", nullable = false)
	private User uploadedBy;

	@Column(name = "original_name", nullable = false, length = 255)
	private String originalName;

	@Column(name = "stored_name", nullable = false, length = 100, unique = true)
	private String storedName;

	@Column(name = "content_type", nullable = false, length = 150)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected TaskAttachment() {
	}

	public TaskAttachment(Project project, Task task, User uploadedBy, String originalName,
			String storedName, String contentType, long sizeBytes) {
		this.project = project;
		this.task = task;
		this.uploadedBy = uploadedBy;
		this.originalName = originalName;
		this.storedName = storedName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
	}

	public Long getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public Task getTask() {
		return task;
	}

	public User getUploadedBy() {
		return uploadedBy;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getStoredName() {
		return storedName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
