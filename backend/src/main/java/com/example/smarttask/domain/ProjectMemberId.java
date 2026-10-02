package com.example.smarttask.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProjectMemberId implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "user_id")
	private Long userId;

	protected ProjectMemberId() {
	}

	public ProjectMemberId(Long projectId, Long userId) {
		this.projectId = projectId;
		this.userId = userId;
	}

	public Long getProjectId() {
		return projectId;
	}

	public Long getUserId() {
		return userId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ProjectMemberId that)) {
			return false;
		}
		return Objects.equals(projectId, that.projectId)
				&& Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(projectId, userId);
	}
}
