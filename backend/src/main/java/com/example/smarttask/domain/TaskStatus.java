package com.example.smarttask.domain;

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
@Table(name = "task_statuses")
public class TaskStatus {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@Column(nullable = false, length = 32)
	private String code;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "display_order", nullable = false)
	private short displayOrder;

	@Column(name = "is_terminal", nullable = false)
	private boolean terminal;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected TaskStatus() {
	}

	public TaskStatus(Project project, String code, String displayName,
			short displayOrder, boolean terminal) {
		this.project = project;
		this.code = code;
		this.displayName = displayName;
		this.displayOrder = displayOrder;
		this.terminal = terminal;
		this.active = true;
	}

	public Long getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public String getCode() {
		return code;
	}

	public String getDisplayName() {
		return displayName;
	}

	public short getDisplayOrder() {
		return displayOrder;
	}

	public boolean isTerminal() {
		return terminal;
	}

	public boolean isActive() {
		return active;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void setDisplayOrder(short displayOrder) {
		this.displayOrder = displayOrder;
	}

	public void setTerminal(boolean terminal) {
		this.terminal = terminal;
	}
}
