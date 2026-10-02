package com.example.smarttask.project;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.smarttask.domain.Project;
import com.example.smarttask.domain.ProjectRole;
import com.example.smarttask.domain.ProjectStatus;

public record ProjectResponse(
		Long id,
		String name,
		String description,
		LocalDate startDate,
		LocalDate dueDate,
		ProjectStatus status,
		ProjectRole currentUserRole,
		ProjectUserResponse createdBy,
		LocalDateTime archivedAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static ProjectResponse from(Project project, ProjectRole currentUserRole) {
		return new ProjectResponse(
				project.getId(),
				project.getName(),
				project.getDescription(),
				project.getStartDate(),
				project.getDueDate(),
				project.getStatus(),
				currentUserRole,
				ProjectUserResponse.from(project.getCreatedBy()),
				project.getArchivedAt(),
				project.getCreatedAt(),
				project.getUpdatedAt());
	}
}
