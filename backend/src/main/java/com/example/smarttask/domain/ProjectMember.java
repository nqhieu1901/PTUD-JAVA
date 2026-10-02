package com.example.smarttask.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_members")
public class ProjectMember {

	@EmbeddedId
	private ProjectMemberId id;

	@MapsId("projectId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "project_role", nullable = false, length = 16)
	private ProjectRole projectRole;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	protected ProjectMember() {
	}

	public ProjectMember(Project project, User user, ProjectRole projectRole) {
		this.project = Objects.requireNonNull(project);
		this.user = Objects.requireNonNull(user);
		this.projectRole = Objects.requireNonNull(projectRole);
		this.id = new ProjectMemberId(project.getId(), user.getId());
	}

	public ProjectMemberId getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public User getUser() {
		return user;
	}

	public ProjectRole getProjectRole() {
		return projectRole;
	}

	public void setProjectRole(ProjectRole projectRole) {
		this.projectRole = projectRole;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}
}
